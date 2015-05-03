package jdrivesync;

import jdrivesync.cli.FileNamePatterns;
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

public class ITIgnoreFiles extends BaseClass {
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
        Files.write(Paths.get(basePathTestData(), name, "movie.mov"), Arrays.asList("movie"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.write(Paths.get(basePathTestData(), name, "text.txt"), Arrays.asList("text"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.write(Paths.get(basePathTestData(), name, ".hidden.txt"), Arrays.asList(".hidden"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.createDirectory(Paths.get(basePathTestData(), name, "folder"));
        Files.write(Paths.get(basePathTestData(), name, "folder", "image1.jpg"), Arrays.asList("image1"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.write(Paths.get(basePathTestData(), name, "folder", "image2.tiff"), Arrays.asList("image2"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Test
    public void testSimpleSync() {
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(6));
        assertThat(googleDriveAdapter.search(Optional.of("movie.mov")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("text.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of(".hidden.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image1.jpg")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image2.tiff")).size(), is(1));
    }

    @Test
    public void testNoImagesAndNoMovies() {
        options.setIgnoreFiles(FileNamePatterns.create(Arrays.asList("*.jpg", "*.tiff", "*.mov")));
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(3));
        assertThat(googleDriveAdapter.search(Optional.of("movie.mov")).size(), is(0));
        assertThat(googleDriveAdapter.search(Optional.of("text.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of(".hidden.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image1.jpg")).size(), is(0));
        assertThat(googleDriveAdapter.search(Optional.of("image2.tiff")).size(), is(0));
    }

    @Test
    public void testNoHiddenFiles() {
        options.setIgnoreFiles(FileNamePatterns.create(Arrays.asList(".*")));
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(5));
        assertThat(googleDriveAdapter.search(Optional.of("movie.mov")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("text.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of(".hidden.txt")).size(), is(0));
        assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image1.jpg")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image2.tiff")).size(), is(1));
    }

    @Test
    public void testNoFilesUnderFolder() {
        options.setIgnoreFiles(FileNamePatterns.create(Arrays.asList("folder/**")));
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(4));
        assertThat(googleDriveAdapter.search(Optional.of("movie.mov")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("text.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of(".hidden.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image1.jpg")).size(), is(0));
        assertThat(googleDriveAdapter.search(Optional.of("image2.tiff")).size(), is(0));
    }

    @Test
    public void testNoJpgFiles() {
        options.setIgnoreFiles(FileNamePatterns.create(Arrays.asList("**/*.jpg")));
        App app = new App();
        app.sync(options);
        sleep();
        assertThat(googleDriveAdapter.listAll().size(), is(5));
        assertThat(googleDriveAdapter.search(Optional.of("movie.mov")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("text.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of(".hidden.txt")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
        assertThat(googleDriveAdapter.search(Optional.of("image1.jpg")).size(), is(0));
        assertThat(googleDriveAdapter.search(Optional.of("image2.tiff")).size(), is(1));
    }
}
