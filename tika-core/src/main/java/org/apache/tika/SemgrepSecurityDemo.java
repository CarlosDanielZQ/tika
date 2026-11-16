package org.apache.tika;

import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;


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


    public static byte[] generateWeakSHA1Hash(byte[] data)
            throws NoSuchAlgorithmException {

        // Tâche 3 : utilisation volontaire de SHA-1.
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(data);
    }


    public static ResultSet executeUnsafeQuery(
            Connection connection, String userInput)
            throws Exception {

        // Tâche 3 : construction volontairement vulnérable.
        Statement statement = connection.createStatement();

        String query = "SELECT * FROM users WHERE name = '"
                + userInput + "'";

        return statement.executeQuery(query);
    }


    public static X509TrustManager createUnsafeTrustManager() {
        // Tâche 3 : désactivation volontaire
        // de la validation des certificats TLS.
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(
                    X509Certificate[] chain, String authType) {
                // Aucune vérification
            }

            @Override
            public void checkServerTrusted(
                    X509Certificate[] chain, String authType) {
                // Accepte tous les certificats
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

}


