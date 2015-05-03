package jdrivesync;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ITUpdateMetadata extends BaseClass {
	private static final String TEST_DATA_UP = ITUpdateMetadata.class.getSimpleName() + "_up";

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
		byte[] bytes = new byte[1024];
		Random random = new Random();
		random.nextBytes(bytes);
		Files.write(Paths.get(basePathTestData(), name, "file.bin"), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	@Test
	public void testSimpleSync() throws IOException {
		App app = new App();
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(1));
		assertThat(googleDriveAdapter.search(Optional.of("file.bin")).size(), is(1));
		long newMillis = System.currentTimeMillis() + 1000;
		FileTime newTimestamp = FileTime.fromMillis(newMillis);
		Files.setLastModifiedTime(Paths.get(basePathTestData(), TEST_DATA_UP, "file.bin"), newTimestamp);
		app.sync(options);
		assertThat(googleDriveAdapter.search(Optional.of("file.bin")).get(0).getModifiedDate().getValue(), is(newMillis));
	}
}
