package org.apache.tika;

import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SemgrepSecurityDemo {

    private SemgrepSecurityDemo() {
    }

    public static Socket createUnencryptedSocket() throws IOException {
        // Tâche 3 : vulnérabilité volontaire à des fins pédagogiques.
        Socket socket = new Socket();
        return socket;
    }

    public static byte[] generateWeakHash(byte[] data)
        throws NoSuchAlgorithmException {

    // Tâche 3 : utilisation volontaire d'un algorithme faible.
    MessageDigest digest = MessageDigest.getInstance("MD5");
    return digest.digest(data);
}
}


