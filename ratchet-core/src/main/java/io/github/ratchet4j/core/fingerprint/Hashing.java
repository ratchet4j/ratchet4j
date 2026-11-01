package io.github.ratchet4j.core.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 based hashing used for fingerprints (first 64 bits, hex). */
final class Hashing {

  private Hashing() {}

  static String hash(String... parts) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (int i = 0; i < parts.length; i++) {
        if (i > 0) {
          md.update((byte) 0);
        }
        md.update(parts[i].getBytes(StandardCharsets.UTF_8));
      }
      byte[] d = md.digest();
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        sb.append(String.format("%02x", d[i]));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
