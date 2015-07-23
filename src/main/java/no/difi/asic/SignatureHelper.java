package no.difi.asic;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.Arrays;


/**
 * Helper class to assist when creating a signature.
 *
 * Not thread safe
 *
 * @author steinar
 *         Date: 11.07.15
 *         Time: 22.53
 */
public class SignatureHelper {

    private static final Logger log = LoggerFactory.getLogger(SignatureHelper.class);

    private static JcaSimpleSignerInfoVerifierBuilder jcaSimpleSignerInfoVerifierBuilder =
            new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME);
    private static JcaDigestCalculatorProviderBuilder jcaDigestCalculatorProviderBuilder =
            new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private X509Certificate x509Certificate;
    private java.security.cert.Certificate[] certificateChain;
    private KeyPair keyPair;

    private JcaContentSignerBuilder jcaContentSignerBuilder;

    /** Loads the keystore and obtains the private key, the public key and the associated certificate */
    public SignatureHelper(File keyStoreFile, String keyStorePassword, String keyPassword) throws IOException {
        this(keyStoreFile, keyStorePassword, null, keyPassword);
    }

    /** Loads the keystore and obtains the private key, the public key and the associated certificate referenced by the alias.
     * @param keyStoreFile file holding the JKS keystore.
     * @param keyStorePassword password of the key store itself
     * @param keyAlias the alias referencing the private and public key pair.
     * @param keyPassword password protecting the private key
     * @throws IOException
     */
    public SignatureHelper(File keyStoreFile, String keyStorePassword, String keyAlias, String keyPassword) throws IOException {
        this(Files.newInputStream(keyStoreFile.toPath()), keyStorePassword, keyAlias, keyPassword);
    }

    /**
     * Loading keystore and fetching key
     * @param keyStoreStream Stream for keystore
     * @param keyStorePassword Password to open keystore
     * @param keyAlias Key alias, uses first key if set to null
     * @param keyPassword Key password
     */
    public SignatureHelper(InputStream keyStoreStream, String keyStorePassword, String keyAlias, String keyPassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(keyStoreStream, keyStorePassword.toCharArray()); // TODO: find password of keystore

            keyStoreStream.close();

            if (keyAlias == null)
                keyAlias = keyStore.aliases().nextElement();
            x509Certificate = (X509Certificate) keyStore.getCertificate(keyAlias);

            certificateChain = keyStore.getCertificateChain(keyAlias);

            Key key = keyStore.getKey(keyAlias, keyPassword.toCharArray());
            PrivateKey privateKey = (PrivateKey) key;

            keyPair = new KeyPair(x509Certificate.getPublicKey(), privateKey);

            jcaContentSignerBuilder = new JcaContentSignerBuilder(String.format("SHA1with%s", privateKey.getAlgorithm()))
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME);
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Unable to retrieve next element from keystore " + e.getMessage(), e);
        } catch (UnrecoverableKeyException e) {
            throw new IllegalStateException("Unable to get the private key from keystore " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to retrieve private key from keystore " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load keystore: " + e.getMessage(), e);
        }
    }

    /**
     * Sign content
     * @param data Content to be signed
     * @return Signature
     */
    public byte[] signData(byte[] data) {
        try {
            DigestCalculatorProvider digestCalculatorProvider = jcaDigestCalculatorProviderBuilder.build();
            ContentSigner contentSigner = jcaContentSignerBuilder.build(keyPair.getPrivate());
            SignerInfoGenerator signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider).build(contentSigner, x509Certificate);

            CMSSignedDataGenerator cmsSignedDataGenerator = new CMSSignedDataGenerator();
            cmsSignedDataGenerator.addSignerInfoGenerator(signerInfoGenerator);
            cmsSignedDataGenerator.addCertificates(new JcaCertStore(Arrays.asList(x509Certificate)));
            CMSSignedData cmsSignedData = cmsSignedDataGenerator.generate(new CMSProcessableByteArray(data), false);

            log.debug(new String(Base64.encodeBase64(cmsSignedData.getEncoded())));
            return cmsSignedData.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void validate(byte[] data, byte[] signature) {
        boolean result = false;

        try {
            CMSSignedData cmsSignedData = new CMSSignedData(new CMSProcessableByteArray(data), signature);
            Store store = cmsSignedData.getCertificates();
            SignerInformationStore signerInformationStore = cmsSignedData.getSignerInfos();

            for (SignerInformation signerInformation : signerInformationStore.getSigners()) {
                X509CertificateHolder x509Certificate = (X509CertificateHolder) store.getMatches(signerInformation.getSID()).iterator().next();
                log.info(x509Certificate.getSubject().toString());

                result = signerInformation.verify(jcaSimpleSignerInfoVerifierBuilder.build(x509Certificate));
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            result = false;
        }

        if (!result)
            throw new IllegalStateException("Unable to verify signature.");
    }

    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }

    public Certificate[] getCertificateChain() {
        return certificateChain;
    }
}
