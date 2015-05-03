package jdrivesync.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;

public class DriveFactory {
    private static Drive drive;
    private static JsonFactory jsonFactory;

    public Drive getDrive(Credential credential) {
        if(drive == null) {
            HttpTransport httpTransport = new NetHttpTransport();
            drive = new Drive.Builder(httpTransport, getJsonFactory(), credential).setApplicationName("JDriveSync").build();
        }
        return drive;
    }

    public static JsonFactory getJsonFactory() {
        if(jsonFactory == null) {
            jsonFactory = new JacksonFactory();
        }
        return jsonFactory;
    }
}
