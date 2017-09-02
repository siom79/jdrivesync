package jdrivesync.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.services.drive.Drive;
import jdrivesync.cli.Options;
import jdrivesync.model.SyncDirectory;
import jdrivesync.model.SyncFile;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyObject;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@Ignore("compile error: no such class: Object")
@RunWith(PowerMockRunner.class)
@PrepareForTest( { Drive.class, HttpRequestFactory.class })
public class GoogleDriveAdapterTest {

	@Test
	public void testChunkedUpload() {
		Credential credentials = mock(Credential.class);
		Options options = new Options();
		DriveFactory driveFactory = mock(DriveFactory.class);
		Drive drive = mock(Drive.class);
		when(driveFactory.getDrive(anyObject())).thenReturn(drive);
		HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
		when(drive.getRequestFactory()).thenReturn(requestFactory);
		GoogleDriveAdapter googleDriveAdapter = new GoogleDriveAdapter(credentials, options, driveFactory);
	}
}
