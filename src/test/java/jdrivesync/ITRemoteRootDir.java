package jdrivesync;

import com.google.api.services.drive.model.File;
import jdrivesync.exception.JDriveSyncException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ITRemoteRootDir extends BaseClass {
    private static final String TESTDATA = ITRemoteRootDir.class.getSimpleName();

    @BeforeClass
    public static void beforeClass() {
        BaseClass.beforeClass();
    }

    @Before
    public void before() throws IOException {
        super.beforeEachTest(TESTDATA, driveFactory);
        createTestData(TESTDATA);
    }

    private void createTestData(String name) throws IOException {
        deleteDirectorySubtree(Paths.get(basePathTestData(), name));
        Files.createDirectory(Paths.get(basePathTestData(), name));
        Files.write(Paths.get(basePathTestData(), name, "test1.txt"), Arrays.asList("test1"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.createDirectory(Paths.get(basePathTestData(), name, "folder"));
        Files.write(Paths.get(basePathTestData(), name, "folder", "test2.txt"), Arrays.asList("test2"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Test
    public void testRemoteDirDoesNotExist() {
        options.setRemoteRootDir(Optional.of("DoesNotExist"));
        App app = new App();
        boolean exceptionThrown = false;
        try {
            app.sync(options);
        } catch (Exception e) {
            exceptionThrown = true;
            assertThat(e instanceof JDriveSyncException, is(true));
            assertThat(((JDriveSyncException) e).getReason(), is(JDriveSyncException.Reason.InvalidRemoteRootDirectory));
        }
        assertThat(exceptionThrown, is(true));
    }

    @Test
    public void testRemoteDirExists() {
        File rootRemoteDir = googleDriveAdapter.getFile("root");
        googleDriveAdapter.createDirectory(rootRemoteDir, "backup");
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(1));
        options.setRemoteRootDir(Optional.of("backup"));
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(4));
    }

    @Test
    public void testRemoteDirWithSubDirs() {
        File rootRemoteDir = googleDriveAdapter.getFile("root");
        File backupDir = googleDriveAdapter.createDirectory(rootRemoteDir, "backup");
        googleDriveAdapter.createDirectory(backupDir, "2014-10-05");
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(2));
        options.setRemoteRootDir(Optional.of("/backup/2014-10-05"));
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(5));
    }

    @Test
    public void testRemoteDirAfterBasicSync() {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        options.setRemoteRootDir(Optional.of("/folder"));
        options.setLocalRootDir(Optional.of(Paths.get(options.getLocalRootDir().get().getAbsolutePath(), "folder").toFile()));
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
    }
}
