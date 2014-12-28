package jdrivesync.encryption;

import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

public class Encryption {
	private final Options options;
	private final SecureRandom secureRandom;

	public Encryption(Options options) {
		this.options = options;
		secureRandom = new SecureRandom();
		secureRandom.setSeed(System.currentTimeMillis());
	}

	public InputStream encrypt(byte[] bytes) {
		try {
			char[] password = options.getEncryptPassword().toCharArray();
			byte[] salt = generateSalt();
			KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
			SecretKey secret = new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(), "AES");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secret);
			byte[] encryptedBytes = cipher.doFinal(bytes);
			byte[] iv = cipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
			return new ByteArrayInputStream(concat(salt, concat(iv, encryptedBytes)));
		} catch (Exception e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.Encryption, "Failed to encrypt: " + e.getMessage(), e);
		}
	}

	public InputStream decrypt(InputStream encryptedStream) {
		try {
			char[] password = options.getEncryptPassword().toCharArray();
			byte[] salt = readSalt(encryptedStream);
			byte[] iv = readIv(encryptedStream);
			KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
			SecretKey secret = new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(), "AES");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
			byte[] bytes = toByteArray(encryptedStream);
			byte[] decryptedBytes = cipher.doFinal(bytes);
			return new ByteArrayInputStream(decryptedBytes);
		} catch (Exception e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.Encryption, "Failed to decrypt: " + e.getMessage(), e);
		}
	}

	private byte[] generateSalt() {
		byte[] salt = new byte[8];
		secureRandom.nextBytes(salt);
		return salt;
	}

	private byte[] concat(byte[] first, byte[] second) {
		byte[] result = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	private byte[] readSalt(InputStream encryptedStream) throws IOException {
		byte[] salt = new byte[8];
		int bytesRead = encryptedStream.read(salt);
		if (bytesRead < salt.length) {
			throw new JDriveSyncException(JDriveSyncException.Reason.Encryption, "Failed to read salt from InputStream.");
		}
		return salt;
	}

	private byte[] readIv(InputStream encryptedStream) throws IOException {
		byte[] iv = new byte[16];
		int bytesRead = encryptedStream.read(iv);
		if (bytesRead < iv.length) {
			throw new JDriveSyncException(JDriveSyncException.Reason.Encryption, "Failed to read IV from InputStream.");
		}
		return iv;
	}

	byte[] toByteArray(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int bytesRead = inputStream.read(buffer);
		while(bytesRead != -1) {
			byteArrayOutputStream.write(buffer, 0, bytesRead);
			bytesRead = inputStream.read(buffer);
		}
		return byteArrayOutputStream.toByteArray();
	}
}
