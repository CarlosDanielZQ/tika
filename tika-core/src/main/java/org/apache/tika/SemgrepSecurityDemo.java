package org.apache.tika;

import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import java.io.InputStream;
import java.io.ObjectInputStream;

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

    public static Cipher createWeakCipher() throws Exception {
    // Tâche 3 : utilisation volontaire d'un chiffrement faible.
    return Cipher.getInstance("DES/ECB/PKCS5Padding");
    }

    public static Object deserializeUntrustedData(InputStream input)
        throws Exception {

    // Tâche 3 : désérialisation volontairement non sécurisée.
    ObjectInputStream objectInput = new ObjectInputStream(input);
    return objectInput.readObject();
    }
}


