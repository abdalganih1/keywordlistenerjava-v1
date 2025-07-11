package com.example.keywordlistenerjava.util;

// !!! هام جداً: هذا الكلاس يستخدم تجزئة بسيطة جداً (MD5) وغير آمنة لإنتاج حقيقي.
// في تطبيق حقيقي، يجب استخدام مكتبات تجزئة قوية ومصممة لتجزئة كلمات المرور
// مثل BCrypt أو PBKDF2 أو Argon2. هذه الخوارزميات تتضمن "salt" وتكرارات لزيادة الأمان.
// هذا الكلاس هو فقط لأغراض العرض التوضيحي للربط مع قاعدة البيانات.

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordHasher {

    // تجزئة كلمة المرور باستخدام MD5 (غير آمن للتطبيقات الحقيقية)
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(password.getBytes());
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null; // Should not happen with MD5
        }
    }

    // التحقق من كلمة المرور (باستخدام نفس خوارزمية التجزئة غير الآمنة)
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        String newHash = hashPassword(plainPassword);
        return newHash != null && newHash.equals(hashedPassword);
    }
}