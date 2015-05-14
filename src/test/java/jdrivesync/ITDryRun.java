package jdrivesync;

import jdrivesync.cli.SyncDirection;
import jdrivesync.constants.Constants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITDryRun extends BaseClass {
	private static final String TEST_DATA_UP = ITDryRun.class.getSimpleName() + "_up";

	@BeforeClass
	public static void beforeClass() {
		BaseClass.beforeClass();
	}

	@Before
	public void before() throws IOException {
		super.beforeEachTest(TEST_DATA_UP, driveFactory);
		createTestData(TEST_DATA_UP);
	}

	private void createTestData(String name) throws IOException {
		deleteDirectorySubtree(Paths.get(basePathTestData(), name));
		Files.createDirectory(Paths.get(basePathTestData(), name));
		Files.write(Paths.get(basePathTestData(), name, "test1.txt"), Collections.singletonList("test1"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		Files.createDirectory(Paths.get(basePathTestData(), name, "folder"));
		Files.write(Paths.get(basePathTestData(), name, "folder", "test2.txt"), Collections.singletonList("test2"), Charset.defaultCharset(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	@Test
	public void testAUpload() {
		options.setSyncDirection(SyncDirection.Up);
		options.setDryRun(true);
		App app = new App();
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(0));
	}

	@Test
	public void testBDownload() throws IOException {
		options.setSyncDirection(SyncDirection.Up);
		options.setDryRun(false);
		App app = new App();
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(3));
		Path path = Paths.get(basePathTestData(), TEST_DATA_UP);
		deleteDirectorySubtree(path);
		Files.createDirectory(path);
		options.setSyncDirection(SyncDirection.Down);
		options.setDryRun(true);
		app = new App();
		app.sync(options);
		sleep();
		assertThat(Files.list(path).count(), is(0L));
	}
}
