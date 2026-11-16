/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika;

import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.crypto.Cipher;


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

    public static Cipher createWeakRC4Cipher() throws Exception {
        // Tâche 3 : utilisation volontaire d'un algorithme
        // de chiffrement obsolète.
        return Cipher.getInstance("RC4");
    }

    public static Cipher createWeakBlowfishCipher() throws Exception {
    // Tâche 3 : utilisation volontaire d'un algorithme
    // de chiffrement ancien.
    return Cipher.getInstance("Blowfish");
    }
}
