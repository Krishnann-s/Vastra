package com.vastra.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes passwords and PINs with PBKDF2-HMAC-SHA256 (built into the JDK -
 * no extra dependency needed).
 */
public class PasswordUtil {
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    /** Returns {hashBase64, saltBase64} for a brand-new password/PIN. */
    public static String[] hash(String plainText) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = pbkdf2(plainText, salt);
        return new String[]{hash, Base64.getEncoder().encodeToString(salt)};
    }

    public static boolean verify(String plainText, String storedHashBase64, String storedSaltBase64) {
        if (plainText == null || storedHashBase64 == null || storedSaltBase64 == null) return false;
        byte[] salt = Base64.getDecoder().decode(storedSaltBase64);
        String hash = pbkdf2(plainText, salt);
        return hash.equals(storedHashBase64);
    }

    private static String pbkdf2(String plainText, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(plainText.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }
}