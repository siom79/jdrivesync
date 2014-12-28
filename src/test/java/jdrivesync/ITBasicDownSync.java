package jdrivesync;

import com.google.api.services.drive.model.File;
import jdrivesync.cli.SyncDirection;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ITBasicDownSync extends BaseClass {
    private static final String TEST_DATA_UP = ITBasicDownSync.class.getSimpleName() + "_up";
    private static final String TEST_DATA_DOWN = ITBasicDownSync.class.getSimpleName() + "_down";

    @BeforeClass
    public static void beforeClass() {
        BaseClass.beforeClass();
    }

    @Before
    public void before() throws IOException {
        super.beforeEachTest(TEST_DATA_UP);
        createTestData(TEST_DATA_UP);
    }

    private void createTestData(String name) throws IOException {
        deleteDirectorySubtree(Paths.get(basePathTestData(), name));
        Files.createDirectory(Paths.get(basePathTestData(), name));
        Files.write(Paths.get(basePathTestData(), name, "test1.txt"), Arrays.asList("test1"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.createDirectory(Paths.get(basePathTestData(), name, "folder"));
        Files.write(Paths.get(basePathTestData(), name, "folder", "test2.txt"), Arrays.asList("test2"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Test
    public void testSimpleSyncToNewDir() throws IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        options.setSyncDirection(SyncDirection.Down);
        Path testDataDown = Paths.get(basePathTestData(), TEST_DATA_DOWN);
        if(!Files.exists(testDataDown)) {
            Files.createDirectory(testDataDown);
        }
        options.setLocalRootDir(Optional.of(testDataDown.toFile()));
        app.sync(options);
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_DOWN, "test1.txt")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_DOWN, "folder")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_DOWN, "folder", "test2.txt")), is(true));
    }

    @Test
    public void testSimpleSyncToExistingDir() throws IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        options.setSyncDirection(SyncDirection.Down);
        app.sync(options);
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "test1.txt")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder", "test2.txt")), is(true));
    }

    @Test
    public void testEmptyGoogleDrive() throws IOException {
        Path emptyDirPath = Paths.get(basePathTestData(), "emptyDir");
        if(Files.exists(emptyDirPath)) {
            deleteDirectorySubtree(emptyDirPath);
        }
        Files.createDirectory(emptyDirPath);
        options.setSyncDirection(SyncDirection.Down);
        options.setLocalRootDir(Optional.of(emptyDirPath.toFile()));
        App app = new App();
        app.sync(options);
        assertThat(emptyDirPath.toFile().listFiles().length, is(0));
    }

    @Test
    public void testUpDownUp() throws IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        options.setSyncDirection(SyncDirection.Down);
        app.sync(options);
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "test1.txt")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder", "test2.txt")), is(true));
        options.setSyncDirection(SyncDirection.Up);
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
    }

    @Test
    public void testLocalFileDeleted() throws IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        Files.delete(Paths.get(basePathTestData(), TEST_DATA_UP, "test1.txt"));
        options.setSyncDirection(SyncDirection.Down);
        app.sync(options);
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "test1.txt")), is(true));
    }

    @Test
    public void testRemoteFileDeleted() {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        List<File> searchResult = googleDriveAdapter.search(Optional.of("test1.txt"));
        assertThat(searchResult.size(), is(1));
        googleDriveAdapter.deleteFile(searchResult.get(0));
        options.setSyncDirection(SyncDirection.Down);
        app.sync(options);
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "test1.txt")), is(false));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder", "test2.txt")), is(true));
    }

    @Test
    public void testRemoteRootDir() {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        options.setRemoteRootDir(Optional.of("/folder"));
        options.setLocalRootDir(Optional.of(Paths.get(basePathTestData(), TEST_DATA_UP, "folder").toFile()));
        options.setSyncDirection(SyncDirection.Down);
        app.sync(options);
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "test1.txt")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder")), is(true));
        assertThat(Files.exists(Paths.get(basePathTestData(), TEST_DATA_UP, "folder", "test2.txt")), is(true));
    }
}
