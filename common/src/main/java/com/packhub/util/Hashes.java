package com.packhub.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 校验工具：Modrinth 提供 sha1 / sha512。 */
public final class Hashes {
    private Hashes() {
    }

    public static String sha1(Path file) throws IOException {
        return digest("SHA-1", file);
    }

    public static String sha512(Path file) throws IOException {
        return digest("SHA-512", file);
    }

    public static String sha1(byte[] data) {
        return digest("SHA-1", data);
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String digest(String algorithm, Path file) throws IOException {
        MessageDigest digest = newDigest(algorithm);
        byte[] buffer = new byte[16384];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static String digest(String algorithm, byte[] data) {
        MessageDigest digest = newDigest(algorithm);
        digest.update(data);
        return hex(digest.digest());
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺少摘要算法: " + algorithm, e);
        }
    }
}
