package jdrivesync;

import com.google.api.services.drive.model.File;
import jdrivesync.gdrive.GoogleDriveAdapter;
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
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class ITBasicUpSync extends BaseClass {
    private static final String TESTDATA = ITBasicUpSync.class.getSimpleName();

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
    public void testSimpleSync() {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        assertThat(googleDriveAdapter.search(Optional.of("test1.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("test2.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
    }

    @Test
    public void testRemoteFolderDeletion() throws InterruptedException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        googleDriveAdapter.deleteDirectory(googleDriveAdapter.search(Optional.of("folder")).get(0));
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(1));
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
    }

    @Test
    public void testLocalFolderDeletion() throws InterruptedException, IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        deleteDirectorySubtree(Paths.get(basePathTestData(), TESTDATA, "folder"));
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(1));
    }

    @Test
    public void testLocalFileDeletion() throws InterruptedException, IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        Files.delete(Paths.get(basePathTestData(), TESTDATA, "test1.txt"));
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(2));
    }

    @Test
    public void testRemoteFileDeletion() throws InterruptedException, IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        googleDriveAdapter.deleteFile(googleDriveAdapter.search(Optional.of("test1.txt")).get(0));
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
    }

    @Test
    public void testLocalFileIsRemoteDir() {
        File rootRemoteDir = googleDriveAdapter.getFile("root");
        googleDriveAdapter.createDirectory(rootRemoteDir, "test1.txt");
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        assertThat(googleDriveAdapter.search(Optional.of("test1.txt")).get(0).getMimeType(), is(not(GoogleDriveAdapter.MIME_TYPE_FOLDER)));
    }

    @Test
    public void testLocalDirIsRemoteFile() throws IOException {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        Files.delete(Paths.get(basePathTestData(), TESTDATA, "test1.txt"));
        Files.createDirectory(Paths.get(basePathTestData(), TESTDATA, "test1.txt"));
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        assertThat(googleDriveAdapter.search(Optional.of("test1.txt")).get(0).getMimeType(), is(GoogleDriveAdapter.MIME_TYPE_FOLDER));
    }

    @Test
    public void testUmlaut() throws IOException {
        Files.write(Paths.get(basePathTestData(), TESTDATA, "äöüßÄÖÜ.txt"), Arrays.asList("äöüßÄÖÜ?"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(4));
        assertThat(googleDriveAdapter.search(Optional.of("äöüßÄÖÜ.txt")).size(), is(1));
    }

    @Test
    public void testEmptyDir() throws IOException {
        Path emptyDirPath = Paths.get(basePathTestData(), "emptyDir");
        options.setLocalRootDir(Optional.of(emptyDirPath.toFile()));
        if(Files.exists(emptyDirPath)) {
            deleteDirectorySubtree(emptyDirPath);
        }
        Files.createDirectory(emptyDirPath);
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(0));
    }
}
