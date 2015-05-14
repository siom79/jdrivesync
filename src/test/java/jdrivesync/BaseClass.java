package jdrivesync;

import com.google.api.client.auth.oauth2.Credential;
import jdrivesync.cli.Options;
import jdrivesync.gdrive.CredentialStore;
import jdrivesync.gdrive.DriveFactory;
import jdrivesync.gdrive.GoogleDriveAdapter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BaseClass {
    private static final Logger LOGGER = Logger.getLogger(ITBasicUpSync.class.getName());
    protected Options options;
    protected GoogleDriveAdapter googleDriveAdapter;
    protected DriveFactory driveFactory = new DriveFactory();

    protected static void beforeClass() {
        App.initLogging();
    }

    protected void beforeEachTest(String testDirName, DriveFactory driveFactory) {
        options = createOptions(testDirName);
        googleDriveAdapter = createGoogleDriveAdapter(options, driveFactory);
        googleDriveAdapter.deleteAll();
        assertThat(googleDriveAdapter.listAll().size(), is(0));
    }

    protected Options createOptions(String testDirName) {
        Options options = new Options();
        options.setAuthenticationFile(Optional.of(Paths.get(System.getProperty("user.dir"), "src", "test", "resources", ".jdrivesync").toString()));
        options.setLocalRootDir(Optional.of(Paths.get(basePathTestData(), testDirName).toFile()));
        options.setDeleteFiles(true);
        return options;
    }

    protected GoogleDriveAdapter createGoogleDriveAdapter(Options options, DriveFactory driveFactory) {
        CredentialStore credentialStore = new CredentialStore(options);
        Optional<Credential> credentialOptional = credentialStore.load();
        assertThat(credentialOptional.isPresent(), is(true));
        return new GoogleDriveAdapter(credentialOptional.get(), options, driveFactory);
    }

    protected void deleteDirectorySubtree(Path path) throws IOException {
        if(Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    protected String basePathTestData() {
        return Paths.get(System.getProperty("user.dir"), "target").toString();
    }

    protected void sleep() {
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Sleeping was interrupted: " + e.getMessage(), e);
        }
    }
}
