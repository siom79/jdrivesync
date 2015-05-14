package jdrivesync.fs;

import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.model.SyncDirectory;
import jdrivesync.model.SyncItem;
import jdrivesync.report.ReportEntry;
import jdrivesync.report.ReportFactory;
import jdrivesync.sync.Synchronization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static jdrivesync.util.FileUtil.toRelativePath;

public class FileSystemAdapter {
	private static final Logger LOGGER = Logger.getLogger(FileSystemAdapter.class.getName());
	public static final String TRASH = ".trash";
	private final Options options;

	public FileSystemAdapter(Options options) {
		this.options = options;
	}

	public File[] listFiles(File directory) {
		File[] files = directory.listFiles();
		if (files == null) {
			files = new File[0];
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			if (files.length > 0) {
				for (File file : files) {
					LOGGER.log(Level.FINE, "Directory '" + directory.getAbsolutePath() + "' contains file '" + file.getAbsolutePath() + "'.");
				}
			} else {
				LOGGER.log(Level.FINE, "Directory '" + directory.getAbsolutePath() + "' does not contain any files.");
			}
		}
		Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName()));
		return files;
	}

	public boolean isDirectory(File directory) {
		return directory.isDirectory();
	}

	public boolean exists(File file) {
		return file.exists();
	}

	public boolean canRead(File file) {
		return file.canRead();
	}

	public void deleteDirectorySubtree(Path path) throws IOException {
		if (Files.exists(path)) {
			LOGGER.log(Level.FINE, "Deleting subtree '" + path + "'.");
			if (!options.isDryRun()) {
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						LOGGER.log(Level.FINE, "Deleting file '" + file + "'.");
						Files.delete(file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						LOGGER.log(Level.FINE, "Deleting directory '" + dir + "'.");
						Files.delete(dir);
						return FileVisitResult.CONTINUE;
					}
				});
			}
		}
	}

	public boolean delete(File file) {
		boolean deleted = false;
		if (TRASH.equals(file.getName()) && file.getParentFile() != null && file.getParentFile().equals(options.getLocalRootDir().get())) {
			LOGGER.log(Level.FINE, "Not deleting file '" + file.getAbsolutePath() + "' because it is our trash bin.");
		} else {
			if (options.isDeleteFiles()) {
				LOGGER.log(Level.FINE, "Deleting file '" + file.getAbsolutePath() + "'.");
				if (!options.isDryRun()) {
					deleted = file.delete();
				}
			} else {
				try {
					if (!options.isDryRun()) {
						Path trashDir = createTrashDir();
						Path relativePath = options.getLocalRootDir().get().toPath().relativize(file.toPath());
						Path target = Paths.get(trashDir.toString(), relativePath.toString());
						Path targetParent = target.getParent();
						if (targetParent != null) {
							Files.createDirectories(targetParent);
						}
						LOGGER.log(Level.FINE, "Moving file '" + file.getAbsolutePath() + "' to trash bin ('" + target + "').");
						Files.move(file.toPath(), target);
						deleted = true;
					}
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Could not move file to .trash directory: " + e.getMessage(), e);
				}
			}
		}
		return deleted;
	}

	private Path createTrashDir() throws IOException {
		File localRootDir = options.getLocalRootDir().get();
		Path trashDirPath = Paths.get(localRootDir.getAbsolutePath(), TRASH);
		if (!Files.exists(trashDirPath)) {
			trashDirPath = Files.createDirectory(trashDirPath);
		}
		return trashDirPath;
	}

	public BasicFileAttributes readAttributes(File file) throws IOException {
		return Files.readAttributes(file.toPath(), BasicFileAttributes.class);
	}

	public Path createDirectory(File file) throws IOException {
		LOGGER.log(Level.FINE, "Creating directory '" + file.getAbsolutePath() + "'.");
		if (!options.isDryRun()) {
			return Files.createDirectory(file.toPath());
		}
		return file.toPath();
	}

	public void setLastModifiedTime(File file, long millis) throws IOException {
		Date date = new Date(millis);
		String dateFormat = Synchronization.DATE_FORMAT.format(date);
		LOGGER.log(Level.FINE, "Setting last modified time on file '" + file.getAbsolutePath() + "' to " + dateFormat + ".");
		if (!options.isDryRun()) {
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(millis));
		}
	}

	public void storeFile(InputStream inputStream, SyncItem syncItem) {
		try {
			Optional<com.google.api.services.drive.model.File> remoteFileOptional = syncItem.getRemoteFile();
			if (remoteFileOptional.isPresent()) {
				com.google.api.services.drive.model.File remoteFile = remoteFileOptional.get();
				if (syncItem.getParent().isPresent()) {
					SyncDirectory parentSyncDir = syncItem.getParent().get();
					if (parentSyncDir.getLocalFile().isPresent()) {
						java.io.File parentFile = parentSyncDir.getLocalFile().get();
						java.io.File newFile = new java.io.File(parentFile, remoteFileOptional.get().getTitle());
						copyStreamToFile(inputStream, newFile);
						syncItem.setLocalFile(Optional.of(newFile));
						setLastModifiedTime(newFile, remoteFile.getModifiedDate().getValue());
					} else {
						LOGGER.log(Level.WARNING, "Cannot create file '" + syncItem.getPath() + "' because local file is not present.");
					}
				} else {
					java.io.File parentFile = options.getLocalRootDir().get();
					java.io.File newFile = new java.io.File(parentFile, remoteFileOptional.get().getTitle());
					copyStreamToFile(inputStream, newFile);
					syncItem.setLocalFile(Optional.of(newFile));
					setLastModifiedTime(newFile, remoteFile.getModifiedDate().getValue());
				}
			} else {
				LOGGER.log(Level.WARNING, "Cannot create file '" + syncItem.getPath() + "' because remote file is not present.");
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to store local file: " + e.getMessage(), e);
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void copyStreamToFile(InputStream inputStream, java.io.File file) throws IOException {
		if (!options.isDryRun()) {
			byte[] buffer = new byte[1024];
			try (FileOutputStream fos = new FileOutputStream(file)) {
				int read = inputStream.read(buffer, 0, buffer.length);
				while (read >= 0) {
					fos.write(buffer, 0, read);
					read = inputStream.read(buffer, 0, buffer.length);
				}
			}
		}
	}
}
