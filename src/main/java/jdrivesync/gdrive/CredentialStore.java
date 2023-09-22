package jdrivesync.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.gdrive.oauth.OAuth2ClientCredentials;
import jdrivesync.logging.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CredentialStore {
    public static final String FILE_NAME = ".jdrivesync";
    public static final String PROP_ACCESS_TOKEN = "accessToken";
    public static final String PROP_REFRESH_TOKEN = "refreshToken";
    private static final Logger LOGGER = LoggerFactory.getLogger();
    private final Options options;
    private Optional<Credential> credential = Optional.empty();

    public CredentialStore(Options options) {
        this.options = options;
    }

    public void store(Credential credential) {
        this.credential = Optional.of(credential);
        Properties properties = new Properties();
        properties.setProperty(PROP_ACCESS_TOKEN, credential.getAccessToken());
        properties.setProperty(PROP_REFRESH_TOKEN, credential.getRefreshToken());
        try {
			File file = getAuthenticationFile(options);
            properties.store(new FileWriter(file), "Properties of jdrivesync.");
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to store properties file: " + e.getMessage(), e);
        }
    }

    public Optional<Credential> getCredential() {
        if(!credential.isPresent()) {
            credential = load();
        }
        return credential;
    }

    public Optional<Credential> load() {
        Properties properties = new Properties();
        try {
			File file = getAuthenticationFile(options);
            if(!file.exists() || !file.canRead()) {
                LOGGER.log(Level.FINE, "Cannot find or read properties file. Returning empty credentials.");
                return Optional.empty();
            }
            properties.load(new FileReader(file));
            HttpTransport httpTransport = new NetHttpTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            GoogleCredential credential = new GoogleCredential.Builder().setJsonFactory(jsonFactory)
                    .setTransport(httpTransport).setClientSecrets(OAuth2ClientCredentials.CLIENT_ID, OAuth2ClientCredentials.CLIENT_SECRET).build();
            credential.setAccessToken(properties.getProperty(PROP_ACCESS_TOKEN));
            credential.setRefreshToken(properties.getProperty(PROP_REFRESH_TOKEN));
            return Optional.of(credential);
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to load properties file: " + e.getMessage(), e);
        }
    }

    private File getAuthenticationFile(Options options) {
		Path path = Paths.get(System.getProperty("user.home"), FILE_NAME);
		if (options.getAuthenticationFile().isPresent()) {
			path = Paths.get(options.getAuthenticationFile().get());
		}
		return path.toFile();
	}
}
