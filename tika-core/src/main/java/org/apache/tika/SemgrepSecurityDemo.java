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
import java.io.InputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.crypto.Cipher;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

/**
 * IFT3913 - Tache 3 : classe volontairement vulnerable.
 * <p>
 * Chaque methode contient UNE vulnerabilite introduite volontairement pour
 * verifier que le controle Semgrep du CI fait echouer le build. Cette classe
 * n'est appelee nulle part dans Tika. NE PAS UTILISER EN PRODUCTION.
 */
public final class SemgrepSecurityDemo {

    private SemgrepSecurityDemo() {
    }

    // V1 - CWE-319 : transmission en clair.
    // Une socket TCP ordinaire n'offre ni chiffrement ni authentification du serveur.
    public static Socket createUnencryptedSocket() throws IOException {
        Socket socket = new Socket();
        return socket;
    }

    // V2 - CWE-326 : chiffrement trop faible.
    // DES n'a qu'une cle effective de 56 bits (cassable par force brute) et ECB
    // chiffre deux blocs identiques de la meme facon.
    public static Cipher createWeakCipher() throws Exception {
        return Cipher.getInstance("DES/ECB/PKCS5Padding");
    }

    // V3 - CWE-89 : injection SQL.
    // L'entree est concatenee dans la requete : userInput = "' OR '1'='1" renvoie toute la table.
    public static ResultSet executeUnsafeQuery(Connection connection, String userInput) throws Exception {
        Statement statement = connection.createStatement();
        String query = "SELECT * FROM users WHERE name = '" + userInput + "'";
        return statement.executeQuery(query);
    }

    // V4 - CWE-611 : XML External Entity (XXE).
    // La fabrique est utilisee avec sa configuration par defaut : les DOCTYPE et les
    // entites externes sont acceptes, donc un document piege peut lire des fichiers
    // locaux (<!ENTITY x SYSTEM "file:///etc/passwd">) ou declencher des requetes (SSRF).
    // Tika fournit XMLReaderUtils pour eviter exactement ce probleme ; on le contourne ici.
    public static Document parseUntrustedXml(InputStream xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(xml);
    }
}
