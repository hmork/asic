package no.difi.asic;

import org.apache.commons.io.output.NullOutputStream;

import java.io.IOException;
import java.io.InputStream;

public class AsicContainerVerifier extends AsicAbstractContainerReader {

    AsicContainerVerifier(MessageDigestAlgorithm messageDigestAlgorithm, InputStream inputStream) throws IOException {
        super(messageDigestAlgorithm, inputStream);

        while (getNextFile() != null)
            writeFile(new NullOutputStream());

        close();
    }
}
