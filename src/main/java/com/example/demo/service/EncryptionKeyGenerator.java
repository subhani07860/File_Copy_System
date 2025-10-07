package com.example.demo.service;

import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionKeyGenerator {
	private static final int KEY_SIZE_BYTES = 16; // 128 bits

	public static String generateKey() {
		byte[] keyBytes = new byte[KEY_SIZE_BYTES];
		new SecureRandom().nextBytes(keyBytes);
		return Base64.getEncoder().encodeToString(keyBytes);
	}

}
