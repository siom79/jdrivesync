package jdrivesync;

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
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ITNoDelete extends BaseClass {
	private static final String TESTDATA = ITNoDelete.class.getSimpleName();

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
	public void testNoDeleteUpSync() throws IOException {
		App app = new App();
		options.setSyncDirection(SyncDirection.Up);
		options.setNoDelete(true);
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(3));
		assertThat(googleDriveAdapter.search(Optional.of("test1.txt")).size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("test2.txt")).size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
		Files.delete(Paths.get(basePathTestData(), TESTDATA, "test1.txt"));
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(3));
		assertThat(googleDriveAdapter.search(Optional.of("test1.txt")).size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("test2.txt")).size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
	}

	@Test
	public void testNoDeleteDownSync() throws IOException {
		App app = new App();
		options.setSyncDirection(SyncDirection.Up);
		options.setNoDelete(true);
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(3));
		assertThat(googleDriveAdapter.search(Optional.of("test1.txt")).size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("test2.txt")).size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("folder")).size(), is(1));
		options.setSyncDirection(SyncDirection.Down);
		Path testDataDown = Paths.get(basePathTestData(), TESTDATA);
		if(!Files.exists(testDataDown)) {
			Files.createDirectory(testDataDown);
		}
		options.setLocalRootDir(Optional.of(testDataDown.toFile()));
		app.sync(options);
		assertThat(Files.exists(Paths.get(basePathTestData(), TESTDATA, "test1.txt")), is(true));
		assertThat(Files.exists(Paths.get(basePathTestData(), TESTDATA, "folder")), is(true));
		assertThat(Files.exists(Paths.get(basePathTestData(), TESTDATA, "folder", "test2.txt")), is(true));
		googleDriveAdapter.deleteFile(googleDriveAdapter.search(Optional.of("test2.txt")).get(0));
		app.sync(options);
		assertThat(Files.exists(Paths.get(basePathTestData(), TESTDATA, "test1.txt")), is(true));
		assertThat(Files.exists(Paths.get(basePathTestData(), TESTDATA, "folder")), is(true));
		assertThat(Files.exists(Paths.get(basePathTestData(), TESTDATA, "folder", "test2.txt")), is(true));
	}
}
