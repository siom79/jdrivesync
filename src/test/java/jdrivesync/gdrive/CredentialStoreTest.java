package jdrivesync.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import jdrivesync.cli.Options;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Credential.class)
public class CredentialStoreTest {

	@Test
	public void testAuthFileCreatedAtGivenLocation() {
		Options options = new Options();
		Path path = Paths.get(System.getProperty("user.dir"), "target", "credential-store-test.properties");
		options.setAuthenticationFile(Optional.of(path.toString()));
		CredentialStore credentialStore = new CredentialStore(options);
		Credential credential = mock(Credential.class);
		when(credential.getAccessToken()).thenReturn("");
		when(credential.getRefreshToken()).thenReturn("");
		credentialStore.store(credential);
		assertThat(Files.exists(path), is(true));
	}

	@Test
	public void testAuthFileLoadedFromGivenLocation() throws IOException {
		Path path = Paths.get(System.getProperty("user.dir"), "target", "credentialOptional-store-test.properties");
		List<String> lines = Arrays.asList("accessToken=at", "refreshToken=rf");
		Files.write(path, lines, Charset.defaultCharset());
		Options options = new Options();
		options.setAuthenticationFile(Optional.of(path.toString()));
		CredentialStore credentialStore = new CredentialStore(options);
		Optional<Credential> credentialOptional = credentialStore.load();
		assertThat(credentialOptional.isPresent(), is(true));
		Credential credential = credentialOptional.get();
		assertThat(credential.getAccessToken(), is("at"));
		assertThat(credential.getRefreshToken(), is("rf"));
	}
}