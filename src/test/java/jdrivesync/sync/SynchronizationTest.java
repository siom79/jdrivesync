package jdrivesync.sync;

import jdrivesync.App;
import jdrivesync.cli.Options;
import jdrivesync.fs.FileSystemAdapter;
import jdrivesync.gdrive.GoogleDriveAdapter;
import jdrivesync.model.SyncFile;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.io.File;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SynchronizationTest {

    @BeforeClass
    public static void beforeClass() {
        App.initLogging();
    }

    public class FileMatcher extends ArgumentMatcher<File> {
        private String name;

        public FileMatcher(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(Object item) {
            if(item instanceof File) {
                File file = (File)item;
                return file.getName().equals(name);
            }
            return false;
        }
    }

    @Test
    public void testSyncUpBasic() {
        String baseDir = "/home/user";
        GoogleDriveAdapter googleDriveAdapter = mock(GoogleDriveAdapter.class);
        com.google.api.services.drive.model.File remoteRootFile = new com.google.api.services.drive.model.File();
        when(googleDriveAdapter.getFile("root")).thenReturn(remoteRootFile);
        FileSystemAdapter fileSystemAdapter = mock(FileSystemAdapter.class);
        fileExistsAndCanBeRead(fileSystemAdapter, "user");
        File testTxtFile = createFileMock(baseDir, "test.txt");
        when(fileSystemAdapter.listFiles(argThat(new FileMatcher("user")))).thenReturn(new File[]{testTxtFile});
        Options options = new Options();
        Synchronization synchronization = new Synchronization(googleDriveAdapter, fileSystemAdapter, options);
        File localRootDir = mock(File.class);
        when(localRootDir.getAbsolutePath()).thenReturn(baseDir);
        when(localRootDir.getName()).thenReturn("user");
        options.setLocalRootDir(Optional.of(localRootDir));
        synchronization.syncUp(options);
        verify(googleDriveAdapter).store(any(SyncFile.class));
    }

    private File createFileMock(String baseDir, String filename) {
        File testTxtFile = mock(File.class);
        when(testTxtFile.getAbsolutePath()).thenReturn(baseDir + "/" + filename);
        when(testTxtFile.getName()).thenReturn("test.txt");
        return testTxtFile;
    }

    private void fileExistsAndCanBeRead(FileSystemAdapter fileSystemAdapter, String filename) {
        when(fileSystemAdapter.exists(argThat(new FileMatcher(filename)))).thenReturn(true);
        when(fileSystemAdapter.canRead(argThat(new FileMatcher(filename)))).thenReturn(true);
    }
}