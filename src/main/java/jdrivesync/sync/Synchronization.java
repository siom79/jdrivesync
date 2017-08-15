package jdrivesync.sync;

import com.google.api.client.util.DateTime;
import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.fs.FileSystemAdapter;
import jdrivesync.fs.FileSystemWalker;
import jdrivesync.gdrive.GoogleDriveAdapter;
import jdrivesync.gdrive.GoogleDriveWalker;
import jdrivesync.logging.LoggerFactory;
import jdrivesync.model.SyncDirectory;
import jdrivesync.model.SyncFile;
import jdrivesync.model.SyncItem;
import jdrivesync.report.ReportEntry;
import jdrivesync.report.ReportFactory;
import jdrivesync.walker.WalkerVisitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Synchronization {
	private static final Logger LOGGER = LoggerFactory.getLogger();
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private GoogleDriveAdapter googleDriveAdapter;
	private FileSystemAdapter fileSystemAdapter;
	private final Options options;

	public Synchronization(GoogleDriveAdapter googleDriveAdapter, FileSystemAdapter fileSystemAdapter, Options options) {
		this.googleDriveAdapter = googleDriveAdapter;
		this.fileSystemAdapter = fileSystemAdapter;
		this.options = options;
	}

	public void syncUp(final Options options) {
		FileSystemWalker fileSystemWalker = new FileSystemWalker(options, fileSystemAdapter);
		fileSystemWalker.walk(new WalkerVisitor() {
			@Override
			public WalkerVisitorResult visitDirectory(SyncDirectory syncDirectory) {
				try {
					WalkerVisitorResult result = WalkerVisitorResult.Continue;
					LOGGER.log(Level.FINE, "visitDirectory (absolute path: '" + syncDirectory.getLocalFile().get().getAbsolutePath() + "';relative path: '" + syncDirectory.getPath() + "')");
					String parentId = determineParentId(syncDirectory);
					if (parentId != null) {
						List<com.google.api.services.drive.model.File> children = googleDriveAdapter.listChildren(parentId);
						for (com.google.api.services.drive.model.File remoteChild : children) {
							try {
								String title = remoteChild.getName();
								SyncItem syncItemFound = null;
								Iterator<SyncItem> childrenIterator = syncDirectory.getChildrenIterator();
								while (childrenIterator.hasNext()) {
									SyncItem syncItem = childrenIterator.next();
									String name = syncItem.getLocalFile().get().getName();
									if (title.equals(name)) {
										syncItemFound = processRemoteChildFound(remoteChild, syncItem);
										break;
									}
								}
								if (syncItemFound == null) {
									processRemoteChildNotFound(remoteChild, syncDirectory);
								}
							} catch (Exception e) {
								LOGGER.log(Level.WARNING, "Skipping file/directory '" + syncDirectory.getPath() + "' because an exception occurred: " + e.getMessage(), e);
								ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
							}
						}
						processLocalFilesWithoutRemoteFile(syncDirectory);
					} else {
						result = WalkerVisitorResult.SkipSubtree;
					}
					return result;
				} catch (Exception e) {
					if (e instanceof JDriveSyncException) {
						JDriveSyncException jDriveSyncException = (JDriveSyncException) e;
						if (jDriveSyncException.getReason() == JDriveSyncException.Reason.InvalidRemoteRootDirectory) {
							throw jDriveSyncException;
						}
					}
					LOGGER.log(Level.WARNING, "Skipping directory '" + syncDirectory.getPath() + "' because an exception occurred: " + e.getMessage(), e);
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
					return WalkerVisitorResult.SkipSubtree;
				}
			}

			private void processLocalFilesWithoutRemoteFile(SyncDirectory syncDirectory) {
				Iterator<SyncItem> childrenIterator = syncDirectory.getChildrenIterator();
				while (childrenIterator.hasNext()) {
					SyncItem syncItem = childrenIterator.next();
					try {
						if (!syncItem.getRemoteFile().isPresent()) {
							if (syncItem instanceof SyncFile) {
								LOGGER.log(Level.FINE, "Storing new file '" + syncItem.getPath() + "'.");
								googleDriveAdapter.store((SyncFile) syncItem);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
							} else if (syncItem instanceof SyncDirectory) {
								LOGGER.log(Level.FINE, "Storing new directory '" + syncItem.getPath() + "'.");
								googleDriveAdapter.store((SyncDirectory) syncItem);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
							} else {
								LOGGER.log(Level.FINE, "Type of syncItem is not supported: " + syncItem.getClass().getName());
							}
						}
					} catch (Exception e) {
						LOGGER.log(Level.WARNING, "Skipping file/directory '" + syncItem.getPath() + "' because an exception occurred: " + e.getMessage(), e);
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
					}
				}
			}

			private void processRemoteChildNotFound(com.google.api.services.drive.model.File remoteChild, SyncDirectory syncDirectory) {
				LOGGER.log(Level.FINE, "Deleting remote file/directory '" + remoteChild.getName() + "' because locally it does not exist any more.");
				if (googleDriveAdapter.isDirectory(remoteChild)) {
					googleDriveAdapter.deleteDirectory(remoteChild);
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath() + "/" + remoteChild.getName(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted));
				} else {
					if (!googleDriveAdapter.isGoogleAppsDocument(remoteChild)) {
						googleDriveAdapter.deleteFile(remoteChild);
						ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath() + "/" + remoteChild.getName(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted));
					}
				}
			}

			private SyncItem processRemoteChildFound(com.google.api.services.drive.model.File remoteChild, SyncItem syncItem) throws IOException {
				SyncItem syncItemFound = null;
				if (googleDriveAdapter.isDirectory(remoteChild)) {
					if (!(syncItem instanceof SyncDirectory)) {
						LOGGER.log(Level.FINE, "Deleting remote directory '" + remoteChild.getName() + "' because locally it is a file (" + syncItem.getPath() + ").");
						googleDriveAdapter.deleteDirectory(remoteChild);
						if (syncItem instanceof SyncFile) {
							SyncFile syncFile = (SyncFile) syncItem;
							googleDriveAdapter.store(syncFile);
							syncItem.setRemoteFile(Optional.of(remoteChild));
							syncItemFound = syncItem;
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
						}
					} else {
						syncItem.setRemoteFile(Optional.of(remoteChild));
						syncItemFound = syncItem;
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
					}
				} else {
					if (syncItem instanceof SyncDirectory) {
						if (!googleDriveAdapter.isGoogleAppsDocument(remoteChild)) {
							LOGGER.log(Level.FINE, "Deleting remote file '" + remoteChild.getName() + "' because locally it is a directory (" + syncItem.getPath() + ").");
							googleDriveAdapter.deleteFile(remoteChild);
							googleDriveAdapter.store((SyncDirectory) syncItem);
							syncItem.setRemoteFile(Optional.of(remoteChild));
							syncItemFound = syncItem;
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
						}
					} else {
						syncItem.setRemoteFile(Optional.of(remoteChild));
						syncItemFound = syncItem;
						File localFile = syncItemFound.getLocalFile().get();
						if (options.isUseChecksum()) {
							performChecksumCheck(syncItemFound, localFile, false);
						} else {
							BasicFileAttributes attr = Files.readAttributes(localFile.toPath(), BasicFileAttributes.class);
							FileTime modifiedDateLocal = attr.lastModifiedTime();
							DateTime modifiedDateRemote = remoteChild.getModifiedTime();
							long sizeLocal = attr.size();
							Long sizeRemote = remoteChild.getSize() == null ? 0L : remoteChild.getSize();
							if (!datesAreEqual(modifiedDateLocal.toMillis(), modifiedDateRemote.getValue(), syncItem)) {
								LOGGER.log(Level.FINE, "Last modification dates are not equal for file '" + syncItemFound.getPath() + "' (local: " + DATE_FORMAT.format(new Date(modifiedDateLocal.toMillis())) + "; remote: " + DATE_FORMAT.format(new Date(modifiedDateRemote.getValue())) + "). Checking MD5 checksums.");
								performChecksumCheck(syncItemFound, localFile, true);
							} else if(sizeLocal != sizeRemote) {
								LOGGER.log(Level.FINE, "File sizes are not equal for file '" + syncItemFound.getPath() + "' (local: " + sizeLocal + "; remote: " + sizeRemote + "). Checking MD5 checksums.");
								performChecksumCheck(syncItemFound, localFile, true);
							} else {
								LOGGER.log(Level.FINE, "Last modification dates and sizes are equal for file '" + syncItemFound.getPath() + "' (local: " + DATE_FORMAT.format(new Date(modifiedDateLocal.toMillis())) + ", " + sizeLocal + " bytes; remote: " + DATE_FORMAT.format(new Date(modifiedDateRemote.getValue())) + ", " + sizeRemote + " bytes). Not updating file.");
								ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
							}
						}
					}
				}
				return syncItemFound;
			}

			private String determineParentId(SyncDirectory syncDirectory) {
				String parentId = "root";
				if (syncDirectory.isRootDirectory()) {
					LOGGER.log(Level.FINE, "Getting remote file for root directory.");
					com.google.api.services.drive.model.File rootFile = googleDriveAdapter.getFile(parentId);
					if (options.getRemoteRootDir().isPresent()) {
						rootFile = getRootFileForRemotePath(rootFile, options.getRemoteRootDir().get());
						parentId = rootFile.getId();
					}
					syncDirectory.setRemoteFile(Optional.of(rootFile));
				} else {
					Optional<com.google.api.services.drive.model.File> remoteFileOptional = syncDirectory.getRemoteFile();
					if (remoteFileOptional.isPresent()) {
						parentId = remoteFileOptional.get().getId();
					} else {
						googleDriveAdapter.store(syncDirectory);
						ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
						remoteFileOptional = syncDirectory.getRemoteFile();
						if (remoteFileOptional.isPresent()) {
							parentId = remoteFileOptional.get().getId();
						} else {
							LOGGER.log(Level.FINE, "Skipping directory '" + syncDirectory.getPath() + "' because remoteFile is not set.");
							return null;
						}
					}
				}
				return parentId;
			}

			private com.google.api.services.drive.model.File getRootFileForRemotePath(com.google.api.services.drive.model.File rootFile, String remoteRootDir) {
				remoteRootDir = remoteRootDir.trim();
				remoteRootDir = remoteRootDir.replace("\\", "/");
				if (remoteRootDir.startsWith("/")) {
					remoteRootDir = remoteRootDir.substring(1, remoteRootDir.length());
				}
				LOGGER.log(Level.FINE, "Trying to find remote folder '" + remoteRootDir + "'.");
				String[] remoteDirectories = remoteRootDir.split("/");
				com.google.api.services.drive.model.File currentRemoteDir = rootFile;
				for (String remoteDirectory : remoteDirectories) {
					if (remoteDirectory.length() == 0) {
						continue;
					}
					com.google.api.services.drive.model.File foundRemoteDir = null;
					List<com.google.api.services.drive.model.File> remoteChildren = googleDriveAdapter.listChildren(currentRemoteDir.getId());
					for (com.google.api.services.drive.model.File remoteChild : remoteChildren) {
						if (remoteDirectory.equals(remoteChild.getName())) {
							if (googleDriveAdapter.isDirectory(remoteChild)) {
								foundRemoteDir = remoteChild;
							} else {
								throw new JDriveSyncException(JDriveSyncException.Reason.InvalidRemoteRootDirectory, "The remote path '" + remoteRootDir + "' does point to a file but not to a directory.");
							}
						}
					}
					if (foundRemoteDir == null) {
						throw new JDriveSyncException(JDriveSyncException.Reason.InvalidRemoteRootDirectory, "The remote path '" + remoteRootDir + "' does not exist.");
					} else {
						currentRemoteDir = foundRemoteDir;
						LOGGER.log(Level.FINE, "Found remote folder '" + remoteDirectory + "'.");
					}
				}
				return currentRemoteDir;
			}

			private void performChecksumCheck(SyncItem syncItemFound, File localFile, boolean updateMetadata) {
				com.google.api.services.drive.model.File remoteFile = syncItemFound.getRemoteFile().get();
				String md5ChecksumLocal = computeMd5Checksum(localFile);
				String md5ChecksumRemote = remoteFile.getMd5Checksum();
				if (!md5ChecksumLocal.equals(md5ChecksumRemote)) {
					if (!googleDriveAdapter.isGoogleAppsDocument(remoteFile)) {
						LOGGER.log(Level.FINE, "MD5 checksums are not equal for file '" + syncItemFound.getPath() + "' (local: " + md5ChecksumLocal + "; remote: " + md5ChecksumRemote + "). Updating file.");
						googleDriveAdapter.updateFile(syncItemFound);
						ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Updated));
					}
				} else {
					if (!updateMetadata) {
						LOGGER.log(Level.FINE, "MD5 checksums are equal for file '" + syncItemFound.getPath() + "' (local: " + md5ChecksumLocal + "; remote: " + md5ChecksumRemote + "). Not updating file.");
						ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
					} else {
						if (!googleDriveAdapter.isGoogleAppsDocument(remoteFile)) {
							LOGGER.log(Level.FINE, "MD5 checksums are equal for file '" + syncItemFound.getPath() + "' (local: " + md5ChecksumLocal + "; remote: " + md5ChecksumRemote + "). Updating metadata of remote file.");
							googleDriveAdapter.updateMetadata(syncItemFound);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.UpdatedMetadata));
						}
					}
				}
			}
		});
	}

	private String computeMd5Checksum(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			MessageDigest m = MessageDigest.getInstance("MD5");
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				m.update(buffer, 0, bytesRead);
			}
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1, digest);
			String md5String = bigInt.toString(16);
			while (md5String.length() < 32) {
				md5String = "0" + md5String;
			}
			return md5String;
		} catch (NoSuchAlgorithmException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.NoSuchAlgorithmException, "Could not load MD5 implementation: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not compute MD5 hash for file '" + file.getAbsolutePath() + "': " + e.getMessage(), e);
		}
	}

	private boolean datesAreEqual(long localMillis, long remoteMillis, SyncItem syncItem) {
		boolean equals = Math.abs(localMillis - remoteMillis) <= options.getLastModificationDateThreshold();
		if (!equals) {
			if(LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Last modification dates for file '" + syncItem.getPath() + "' are not equal (local: " + DATE_FORMAT.format(new Date(localMillis)) + ", remote: " + DATE_FORMAT.format(new Date(remoteMillis)) + ").");
			}
		} else {
			if(LOGGER.isLoggable(Level.FINER)) {
				LOGGER.log(Level.FINER, "Last modification dates for file '" + syncItem.getPath() + "' are equal (local: " + DATE_FORMAT.format(new Date(localMillis)) + ", remote: " + DATE_FORMAT.format(new Date(remoteMillis)) + ").");
			}
		}
		return equals;
	}

	public void syncDown(Options options) {
		GoogleDriveWalker googleDriveWalker = new GoogleDriveWalker(options, googleDriveAdapter);
		googleDriveWalker.walk(new WalkerVisitor() {
			@Override
			public WalkerVisitorResult visitDirectory(SyncDirectory syncDirectory) {
				WalkerVisitor.WalkerVisitorResult result = WalkerVisitor.WalkerVisitorResult.Continue;
				LOGGER.log(Level.FINE, "visitDirectory() " + syncDirectory.getPath() + ".");
				if (syncDirectory.getLocalFile().isPresent()) {
					File localFile = syncDirectory.getLocalFile().get();
					File[] files = fileSystemAdapter.listFiles(localFile);
					if (files != null) {
						for (File file : files) {
							SyncItem syncItemFound = null;
							Iterator<SyncItem> childrenIterator = syncDirectory.getChildrenIterator();
							while (childrenIterator.hasNext()) {
								SyncItem syncItem = childrenIterator.next();
								if (syncItem.getRemoteFile().isPresent() && syncItem.getRemoteFile().get().getName().equals(file.getName())) {
									syncItemFound = syncItem;
									result = processRemoteChildFound(file, syncItem);
									break;
								}
							}
							if (syncItemFound == null) {
								processRemoteChildNotFound(file);
							}
						}
					}
					processRemoteFilesWithoutLocalFile(syncDirectory);
				} else {
					LOGGER.log(Level.FINE, "Skipping directory " + syncDirectory.getPath() + " because local file is not present.");
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
				}
				return result;
			}

			private void processRemoteFilesWithoutLocalFile(SyncDirectory syncDirectory) {
				if (syncDirectory.getLocalFile().isPresent()) {
					File fileDirectory = syncDirectory.getLocalFile().get();
					Iterator<SyncItem> childrenIterator = syncDirectory.getChildrenIterator();
					while (childrenIterator.hasNext()) {
						SyncItem syncItem = childrenIterator.next();
						if (!syncItem.getLocalFile().isPresent()) {
							com.google.api.services.drive.model.File remoteFile = syncItem.getRemoteFile().get();
							if (googleDriveAdapter.isDirectory(remoteFile)) {
								File newDirectory = new File(fileDirectory, remoteFile.getName());
								try {
									createLocalDir(newDirectory, syncItem, remoteFile);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
								} catch (Exception e) {
									LOGGER.log(Level.WARNING, "Could not create local directory '" + newDirectory.getAbsolutePath() + "': " + e.getMessage(), e);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
								}
							} else {
								LOGGER.log(Level.FINE, "Downloading file '" + syncItem.getPath() + "'.");
								try {
									InputStream stream = googleDriveAdapter.downloadFile(syncItem);
									fileSystemAdapter.storeFile(stream, syncItem);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
								} catch (Exception e) {
									LOGGER.log(Level.SEVERE, "Failed to store file '" + syncItem.getPath() + "': " + e.getMessage());
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped));
								}
							}
						}
					}
				} else {
					LOGGER.log(Level.FINE, "Cannot process missing local files because local directory '" + syncDirectory.getPath() + "' is missing.");
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped));
				}
			}

			private void processRemoteChildNotFound(File file) {
				if (fileSystemAdapter.isDirectory(file)) {
					LOGGER.log(Level.FINE, "Deleting local directory '" + file.getAbsolutePath() + "' because it does not exist remote.");
					try {
						fileSystemAdapter.deleteDirectorySubtree(file.toPath());
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted));
					} catch (Exception e) {
						LOGGER.log(Level.WARNING, "Could not delete directory '" + file.getAbsolutePath() + "': " + e.getMessage(), e);
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
					}
				} else {
					LOGGER.log(Level.FINE, "Deleting local file '" + file.getAbsolutePath() + "' because it does not exist remote.");
					boolean deleted = fileSystemAdapter.delete(file);
					if (!deleted) {
						LOGGER.log(Level.WARNING, "Could not delete file '" + file.getAbsolutePath() + "':.");
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, "Could not delete file."));
					} else {
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted));
					}
				}
			}

			private WalkerVisitor.WalkerVisitorResult processRemoteChildFound(File file, SyncItem syncItem) {
				com.google.api.services.drive.model.File remoteFile = syncItem.getRemoteFile().get();
				if (googleDriveAdapter.isDirectory(remoteFile)) {
					if (fileSystemAdapter.isDirectory(file)) {
						syncItem.setLocalFile(Optional.of(file));
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
					} else {
						LOGGER.log(Level.FINE, "Deleting local file '" + file.getAbsolutePath() + " because remote it is a directory.");
						boolean deleted = fileSystemAdapter.delete(file);
						if (!deleted) {
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, "Deleting local file failed."));
							throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not delete local directory '" + file.getAbsolutePath() + "'.");
						}
						try {
							createLocalDir(file, syncItem, remoteFile);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
						} catch (Exception e) {
							LOGGER.log(Level.WARNING, "Skipping directory '" + syncItem.getPath() + "' because creation of local directory failed: " + e.getMessage(), e);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
							return WalkerVisitorResult.SkipSubtree;
						}
					}
				} else {
					if (fileSystemAdapter.isDirectory(file)) {
						LOGGER.log(Level.FINE, "Deleting local directory '" + syncItem.getPath() + "' because remote it is a file.");
						boolean deleted = fileSystemAdapter.delete(file);
						if (!deleted) {
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, "Deleting local file failed."));
							throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not delete local directory '" + file.getAbsolutePath() + "'.");
						}
						LOGGER.log(Level.FINE, "Downloading file '" + syncItem.getPath() + "'.");
						try {
							InputStream stream = googleDriveAdapter.downloadFile(syncItem);
							fileSystemAdapter.storeFile(stream, syncItem);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created));
						} catch (Exception e) {
							LOGGER.log(Level.SEVERE, "Failed to store file '" + syncItem.getPath() + "': " + e.getMessage());
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped));
						}
					} else {
						if (options.isUseChecksum()) {
							performChecksumCheck(file, syncItem, remoteFile, false);
						} else {
							DateTime remoteFileModifiedDate = remoteFile.getModifiedTime();
							try {
								BasicFileAttributes attr = fileSystemAdapter.readAttributes(file);
								FileTime localLastModifiedTime = attr.lastModifiedTime();
								long sizeLocal = attr.size();
								long sizeRemote = remoteFile.getSize() == null ? 0L : remoteFile.getSize();
								if (!datesAreEqual(localLastModifiedTime.toMillis(), remoteFileModifiedDate.getValue(), syncItem)) {
									LOGGER.log(Level.FINE, "Last modification dates are not equal for file '" + syncItem.getPath() + "' (local: " + DATE_FORMAT.format(new Date(localLastModifiedTime.toMillis())) + "; remote: " + DATE_FORMAT.format(new Date(remoteFileModifiedDate.getValue())) + "). Checking MD5 checksums.");
									performChecksumCheck(file, syncItem, remoteFile, true);
								} else if(sizeLocal != sizeRemote) {
									LOGGER.log(Level.FINE, "File sizes are not equal for file '" + syncItem.getPath() + "' (local: " + sizeLocal + "; remote: " + sizeRemote + "). Checking MD5 checksums.");
									performChecksumCheck(file, syncItem, remoteFile, true);
								} else {
									syncItem.setLocalFile(Optional.of(file));
									LOGGER.log(Level.FINE, "Last modification dates and sizes are equal for file '" + syncItem.getPath() + "' (local: " + DATE_FORMAT.format(new Date(localLastModifiedTime.toMillis())) + ", " + sizeLocal + " bytes; remote: " + DATE_FORMAT.format(new Date(remoteFileModifiedDate.getValue())) + ", " + sizeRemote + " bytes). Not updating file.");
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
								}
							} catch (Exception e) {
								LOGGER.log(Level.FINE, "Skipping file '" + syncItem.getPath() + " because reading local file attributes failed: " + e.getMessage(), e);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
							}
						}
					}
				}
				return WalkerVisitorResult.Continue;
			}

			private void createLocalDir(File newDirectory, SyncItem syncItem, com.google.api.services.drive.model.File remoteFile) throws IOException {
				LOGGER.log(Level.FINE, "Creating local directory '" + syncItem.getPath() + "'.");
				Path newLocalDir = fileSystemAdapter.createDirectory(newDirectory);
				DateTime lastModifiedDateTime = remoteFile.getModifiedTime();
				fileSystemAdapter.setLastModifiedTime(newLocalDir.toFile(), lastModifiedDateTime.getValue());
				syncItem.setLocalFile(Optional.of(newLocalDir.toFile()));
			}

			private void performChecksumCheck(File file, SyncItem syncItem, com.google.api.services.drive.model.File remoteFile, boolean updateMetadata) {
				String remoteFileMd5Checksum = remoteFile.getMd5Checksum();
				String localFileMd5Checksum = computeMd5Checksum(file);
				if (remoteFileMd5Checksum.equals(localFileMd5Checksum)) {
					syncItem.setLocalFile(Optional.of(file));
					if (!updateMetadata) {
						LOGGER.log(Level.FINE, "Not downloading file '" + syncItem.getPath() + "' because MD5 checksums are equal (local: " + localFileMd5Checksum + ", remote: " + remoteFileMd5Checksum + ").");
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged));
					} else {
						try {
							fileSystemAdapter.setLastModifiedTime(file, remoteFile.getModifiedTime().getValue());
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.UpdatedMetadata));
						} catch (Exception e) {
							LOGGER.log(Level.WARNING, "Could not update last modification date of local file '" + file.getAbsolutePath() + "':" + e.getMessage(), e);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped, e.getMessage()));
						}
					}
				} else {
					LOGGER.log(Level.FINE, "Downloading file '" + syncItem.getPath() + "' because MD5 checksums are not equal (local: " + localFileMd5Checksum + ", remote: " + remoteFileMd5Checksum + ").");
					try {
						InputStream stream = googleDriveAdapter.downloadFile(syncItem);
						fileSystemAdapter.storeFile(stream, syncItem);
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Updated));
					} catch (Exception e) {
						LOGGER.log(Level.SEVERE, "Failed to store file '" + syncItem.getPath() + "': " + e.getMessage());
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped));
					}
				}
			}
		});
	}
}
