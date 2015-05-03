package jdrivesync;

import com.google.api.services.drive.model.File;
import jdrivesync.constants.Constants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ITChunkedUpload extends BaseClass {
	private static final String TEST_DATA_UP = ITChunkedUpload.class.getSimpleName() + "_up";
	public static final int TWO_MB_PLUS_1 = 2 * Constants.MB + 1;
	public static final int TWO_MB = 2 * Constants.MB;
	public static final int ONE_MB = 1 * Constants.MB;

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
		byte[] bytes = new byte[TWO_MB_PLUS_1];
		Random random = new Random();
		random.nextBytes(bytes);
		Files.write(Paths.get(basePathTestData(), name, TWO_MB_PLUS_1 + ".bin"), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		bytes = new byte[TWO_MB];
		random.nextBytes(bytes);
		Files.write(Paths.get(basePathTestData(), name, TWO_MB + ".bin"), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		bytes = new byte[ONE_MB];
		random.nextBytes(bytes);
		Files.write(Paths.get(basePathTestData(), name, ONE_MB + ".bin"), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	@Test
	public void testChunkedUploadWithSmallChunkSize() {
		options.setHttpChunkSizeInBytes(ONE_MB);
		App app = new App();
		app.sync(options);
		sleep();
		assertThat(googleDriveAdapter.listAll().size(), is(3));
		List<File> resultList = googleDriveAdapter.search(Optional.of(TWO_MB_PLUS_1 + ".bin"));
		assertThat(resultList.size(), is(1));
		assertThat(resultList.get(0).getFileSize().intValue(), is(TWO_MB_PLUS_1));
		resultList = googleDriveAdapter.search(Optional.of(TWO_MB + ".bin"));
		assertThat(resultList.size(), is(1));
		assertThat(resultList.get(0).getFileSize().intValue(), is(TWO_MB));
		resultList = googleDriveAdapter.search(Optional.of(ONE_MB + ".bin"));
		assertThat(resultList.size(), is(1));
		assertThat(resultList.get(0).getFileSize().intValue(), is(ONE_MB));
	}
}
