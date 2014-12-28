package jdrivesync.encryption;

import jdrivesync.cli.Options;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EncryptionTest {
	private Options options;
	private Encryption encryption;

	@Before
	public void before() {
		options = new Options();
		encryption = new Encryption(options);
	}

	@Test
	public void encryptDecyrptString() throws IOException {
		options.setEncryptPassword("password");
		String encryptMe = "EncryptMeNow";
		InputStream decrypted = encryption.decrypt(encryption.encrypt(encryptMe.getBytes("UTF-8")));
		String decryptedString = new String(encryption.toByteArray(decrypted), "UTF-8");
		assertThat(decryptedString, is(encryptMe));
	}

	@Test
	public void encryptDecyrptShortString() throws IOException {
		options.setEncryptPassword("password");
		String encryptMe = "1";
		InputStream decrypted = encryption.decrypt(encryption.encrypt(encryptMe.getBytes("UTF-8")));
		String decryptedString = new String(encryption.toByteArray(decrypted), "UTF-8");
		assertThat(decryptedString, is(encryptMe));
	}
}