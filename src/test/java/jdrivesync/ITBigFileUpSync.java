package jdrivesync;

import jdrivesync.constants.Constants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ITBigFileUpSync extends BaseClass {
	private static final String TEST_DATA_UP = ITBigFileUpSync.class.getSimpleName() + "_up";

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
		byte[] bytes = new byte[3 * Constants.MB];
		Random random = new Random();
		random.nextBytes(bytes);
		Files.write(Paths.get(basePathTestData(), name, "bigFile.bin"), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	@Test
	public void testSimpleSync() {
		options.setHttpChunkSizeInBytes(1 * Constants.MB);
		App app = new App();
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("bigFile.bin")).size(), is(1));
	}
}
