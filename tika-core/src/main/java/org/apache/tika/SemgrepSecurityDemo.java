package org.apache.tika;

public final class SemgrepSecurityDemo {

    private SemgrepSecurityDemo() {
    }

    public static String getDatabasePassword() {
        // Tâche 3 : vulnérabilité volontaire à des fins pédagogiques.
        String password = "admin123";
        return password;
    }
}

