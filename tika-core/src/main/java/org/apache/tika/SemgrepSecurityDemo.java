package org.apache.tika;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

public final class SemgrepSecurityDemo {

    private SemgrepSecurityDemo() {
    }

    public static Socket createUnencryptedSocket() throws IOException {
        // Tâche 3 : vulnérabilité volontaire à des fins pédagogiques.
        Socket socket = new Socket();
        return socket;
    }

    public static long generateInsecureToken() {
        // Tâche 3 : génération volontairement non sécurisée.
        Random random = new Random();
        return random.nextLong();
    }
}


