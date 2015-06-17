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
		fileSystemWalker.walk(options, new WalkerVisitor() {
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
								String title = remoteChild.getTitle();
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
								String msg = "Skipping file/directory '" + syncDirectory.getPath() + "' because an exception occurred: " + e.getMessage();
								LOGGER.log(Level.WARNING, msg, e);
								ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
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
					String msg = "Skipping directory '" + syncDirectory.getPath() + "' because an exception occurred: " + e.getMessage();
					LOGGER.log(Level.WARNING, msg, e);
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
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
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
							} else if (syncItem instanceof SyncDirectory) {
								LOGGER.log(Level.FINE, "Storing new directory '" + syncItem.getPath() + "'.");
								googleDriveAdapter.store((SyncDirectory) syncItem);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
							} else {
								LOGGER.log(Level.FINE, "Type of syncItem is not supported: " + syncItem.getClass().getName());
							}
						}
					} catch (Exception e) {
						String msg = "Skipping file/directory '" + syncItem.getPath() + "' because an exception occurred: " + e.getMessage();
						LOGGER.log(Level.WARNING, msg, e);
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
					}
				}
			}

			private void processRemoteChildNotFound(com.google.api.services.drive.model.File remoteChild, SyncDirectory syncDirectory) {
				LOGGER.log(Level.FINE, "Deleting remote file/directory '" + remoteChild.getTitle() + "' because locally it does not exist any more.");
				if (googleDriveAdapter.isDirectory(remoteChild)) {
					SyncAction syncAction = googleDriveAdapter.deleteDirectory(remoteChild);
					if (syncAction == SyncAction.Successful) {
						ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath() + "/" + remoteChild.getTitle(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted, ReportEntry.getDirection(options)));
					} else {
						ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath() + "/" + remoteChild.getTitle(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
					}
				} else {
					if (!googleDriveAdapter.isGoogleAppsDocument(remoteChild)) {
						SyncAction syncAction = googleDriveAdapter.deleteFile(remoteChild);
						if (syncAction == SyncAction.Successful) {
							ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath() + "/" + remoteChild.getTitle(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted, ReportEntry.getDirection(options)));
						} else {
							ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath() + "/" + remoteChild.getTitle(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
						}
					}
				}
			}

			private SyncItem processRemoteChildFound(com.google.api.services.drive.model.File remoteChild, SyncItem syncItem) throws IOException {
				SyncItem syncItemFound = null;
				if (googleDriveAdapter.isDirectory(remoteChild)) {
					if (!(syncItem instanceof SyncDirectory)) {
						LOGGER.log(Level.FINE, "Deleting remote directory '" + remoteChild.getTitle() + "' because locally it is a file (" + syncItem.getPath() + ").");
						SyncAction syncAction = googleDriveAdapter.deleteDirectory(remoteChild);
						if (syncAction == SyncAction.Successful) {
							if (syncItem instanceof SyncFile) {
								SyncFile syncFile = (SyncFile) syncItem;
								googleDriveAdapter.store(syncFile);
								syncItem.setRemoteFile(Optional.of(remoteChild));
								syncItemFound = syncItem;
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
							}
						} else {
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
						}
					} else {
						syncItem.setRemoteFile(Optional.of(remoteChild));
						syncItemFound = syncItem;
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
					}
				} else {
					if (syncItem instanceof SyncDirectory) {
						if (!googleDriveAdapter.isGoogleAppsDocument(remoteChild)) {
							LOGGER.log(Level.FINE, "Deleting remote file '" + remoteChild.getTitle() + "' because locally it is a directory (" + syncItem.getPath() + ").");
							SyncAction deleteAction = googleDriveAdapter.deleteFile(remoteChild);
							if (deleteAction == SyncAction.Successful) {
								ReportFactory.getInstance(options).log(new ReportEntry(remoteChild.getTitle(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted, ReportEntry.getDirection(options)));
								googleDriveAdapter.store((SyncDirectory) syncItem);
								syncItem.setRemoteFile(Optional.of(remoteChild));
								syncItemFound = syncItem;
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
							} else {
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
							}
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
							DateTime modifiedDateRemote = remoteChild.getModifiedDate();
							long sizeLocal = attr.size();
							Long sizeRemote = remoteChild.getFileSize() == null ? 0L : remoteChild.getFileSize();
							if (!datesAreEqual(modifiedDateLocal.toMillis(), modifiedDateRemote.getValue(), syncItem)) {
								LOGGER.log(Level.FINE, "Last modification dates are not equal for file '" + syncItemFound.getPath() + "' (local: " + DATE_FORMAT.format(new Date(modifiedDateLocal.toMillis())) + "; remote: " + DATE_FORMAT.format(new Date(modifiedDateRemote.getValue())) + "). Checking MD5 checksums.");
								performChecksumCheck(syncItemFound, localFile, true);
							} else if(sizeLocal != sizeRemote) {
								LOGGER.log(Level.FINE, "File sizes are not equal for file '" + syncItemFound.getPath() + "' (local: " + sizeLocal + "; remote: " + sizeRemote + "). Checking MD5 checksums.");
								performChecksumCheck(syncItemFound, localFile, true);
							} else {
								LOGGER.log(Level.FINE, "Last modification dates and sizes are equal for file '" + syncItemFound.getPath() + "' (local: " + DATE_FORMAT.format(new Date(modifiedDateLocal.toMillis())) + ", " + sizeLocal + " bytes; remote: " + DATE_FORMAT.format(new Date(modifiedDateRemote.getValue())) + ", " + sizeRemote + " bytes). Not updating file.");
								ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
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
						ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
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
						if (remoteDirectory.equals(remoteChild.getTitle())) {
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
						ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Updated, ReportEntry.getDirection(options)));
					}
				} else {
					if (!updateMetadata) {
						LOGGER.log(Level.FINE, "MD5 checksums are equal for file '" + syncItemFound.getPath() + "' (local: " + md5ChecksumLocal + "; remote: " + md5ChecksumRemote + "). Not updating file.");
						ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
					} else {
						if (!googleDriveAdapter.isGoogleAppsDocument(remoteFile)) {
							LOGGER.log(Level.FINE, "MD5 checksums are equal for file '" + syncItemFound.getPath() + "' (local: " + md5ChecksumLocal + "; remote: " + md5ChecksumRemote + "). Updating metadata of remote file.");
							googleDriveAdapter.updateMetadata(syncItemFound);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItemFound.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.UpdatedMetadata, ReportEntry.getDirection(options)));
						}
					}
				}
			}
		});
	}

	private String computeMd5Checksum(File file) {
		try {
			MessageDigest m = MessageDigest.getInstance("MD5");
			m.update(Files.readAllBytes(file.toPath()));
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1, digest);
			String md5String = bigInt.toString(16);
			while (md5String.length() < 32) {
				md5String = "0" + md5String;
			}
			return md5String;
		} catch (NoSuchAlgorithmException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.NoSuchAlgorithmException, "Could not load MD5 implementation: " + e.getMessage(), e);
		} catch (IOException e) {
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
		googleDriveWalker.walk(options, new WalkerVisitor() {
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
								if (syncItem.getRemoteFile().isPresent() && syncItem.getRemoteFile().get().getTitle().equals(file.getName())) {
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
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
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
								File newDirectory = new File(fileDirectory, remoteFile.getTitle());
								try {
									createLocalDir(newDirectory, syncItem, remoteFile);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
								} catch (IOException e) {
									String msg = "Could not create local directory '" + newDirectory.getAbsolutePath() + "': " + e.getMessage();
									LOGGER.log(Level.WARNING, msg, e);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
								}
							} else {
								LOGGER.log(Level.FINE, "Downloading file '" + syncItem.getPath() + "'.");
								try {
									InputStream stream = googleDriveAdapter.downloadFile(syncItem);
									fileSystemAdapter.storeFile(stream, syncItem);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
								} catch (Exception e) {
									String msg = "Failed to store file '" + syncItem.getPath() + "': " + e.getMessage();
									LOGGER.log(Level.SEVERE, msg);
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
								}
							}
						}
					}
				} else {
					String msg = "Cannot process missing local files because local directory '" + syncDirectory.getPath() + "' is missing.";
					LOGGER.log(Level.FINE, msg);
					ReportFactory.getInstance(options).log(new ReportEntry(syncDirectory.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
				}
			}

			private void processRemoteChildNotFound(File file) {
				if (fileSystemAdapter.isDirectory(file)) {
					LOGGER.log(Level.FINE, "Deleting local directory '" + file.getAbsolutePath() + "' because it does not exist remote.");
					try {
						fileSystemAdapter.deleteDirectorySubtree(file.toPath());
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted, ReportEntry.getDirection(options)));
					} catch (IOException e) {
						String msg = "Could not delete directory '" + file.getAbsolutePath() + "': " + e.getMessage();
						LOGGER.log(Level.WARNING, msg, e);
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
					}
				} else {
					LOGGER.log(Level.FINE, "Deleting local file '" + file.getAbsolutePath() + "' because it does not exist remote.");
					SyncAction syncAction = fileSystemAdapter.delete(file);
					if (syncAction == SyncAction.Error) {
						String msg = "Could not delete file '" + file.getAbsolutePath() + "'.";
						LOGGER.log(Level.WARNING, msg);
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
					} else if (syncAction == SyncAction.Successful) {
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Deleted, ReportEntry.getDirection(options)));
					} else {
						ReportFactory.getInstance(options).log(new ReportEntry(file.getAbsolutePath(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
					}
				}
			}

			private WalkerVisitor.WalkerVisitorResult processRemoteChildFound(File file, SyncItem syncItem) {
				com.google.api.services.drive.model.File remoteFile = syncItem.getRemoteFile().get();
				if (googleDriveAdapter.isDirectory(remoteFile)) {
					if (fileSystemAdapter.isDirectory(file)) {
						syncItem.setLocalFile(Optional.of(file));
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
					} else {
						LOGGER.log(Level.FINE, "Deleting local file '" + file.getAbsolutePath() + " because remote it is a directory.");
						SyncAction syncAction = fileSystemAdapter.delete(file);
						if (syncAction == SyncAction.Error) {
							String msg = "Could not delete local directory '" + file.getAbsolutePath() + "'.";
							LOGGER.log(Level.SEVERE, msg);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
						} else if (syncAction == SyncAction.Successful) {
							try {
								createLocalDir(file, syncItem, remoteFile);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
							} catch (IOException e) {
								String msg = "Skipping directory '" + syncItem.getPath() + "' because creation of local directory failed: " + e.getMessage();
								LOGGER.log(Level.WARNING, msg, e);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
								return WalkerVisitorResult.SkipSubtree;
							}
						} else {
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
						}
					}
				} else {
					if (fileSystemAdapter.isDirectory(file)) {
						LOGGER.log(Level.FINE, "Deleting local directory '" + syncItem.getPath() + "' because remote it is a file.");
						SyncAction syncAction = fileSystemAdapter.delete(file);
						if (syncAction == SyncAction.Error) {
							String msg = "Could not delete local directory '" + file.getAbsolutePath() + "'.";
							LOGGER.log(Level.SEVERE, msg);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
						} else if (syncAction == SyncAction.Successful) {
							LOGGER.log(Level.FINE, "Downloading file '" + syncItem.getPath() + "'.");
							try {
								InputStream stream = googleDriveAdapter.downloadFile(syncItem);
								fileSystemAdapter.storeFile(stream, syncItem);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Created, ReportEntry.getDirection(options)));
							} catch (Exception e) {
								String msg = "Failed to store file '" + syncItem.getPath() + "': " + e.getMessage();
								LOGGER.log(Level.SEVERE, msg);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
							}
						} else {
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Deletion, ReportEntry.getDirection(options)));
						}
					} else {
						if (options.isUseChecksum()) {
							performChecksumCheck(file, syncItem, remoteFile, false);
						} else {
							DateTime remoteFileModifiedDate = remoteFile.getModifiedDate();
							try {
								BasicFileAttributes attr = fileSystemAdapter.readAttributes(file);
								FileTime localLastModifiedTime = attr.lastModifiedTime();
								long sizeLocal = attr.size();
								long sizeRemote = remoteFile.getFileSize() == null ? 0L : remoteFile.getFileSize();
								if (!datesAreEqual(localLastModifiedTime.toMillis(), remoteFileModifiedDate.getValue(), syncItem)) {
									LOGGER.log(Level.FINE, "Last modification dates are not equal for file '" + syncItem.getPath() + "' (local: " + DATE_FORMAT.format(new Date(localLastModifiedTime.toMillis())) + "; remote: " + DATE_FORMAT.format(new Date(remoteFileModifiedDate.getValue())) + "). Checking MD5 checksums.");
									performChecksumCheck(file, syncItem, remoteFile, true);
								} else if(sizeLocal != sizeRemote) {
									LOGGER.log(Level.FINE, "File sizes are not equal for file '" + syncItem.getPath() + "' (local: " + sizeLocal + "; remote: " + sizeRemote + "). Checking MD5 checksums.");
									performChecksumCheck(file, syncItem, remoteFile, true);
								} else {
									syncItem.setLocalFile(Optional.of(file));
									LOGGER.log(Level.FINE, "Last modification dates and sizes are equal for file '" + syncItem.getPath() + "' (local: " + DATE_FORMAT.format(new Date(localLastModifiedTime.toMillis())) + ", " + sizeLocal + " bytes; remote: " + DATE_FORMAT.format(new Date(remoteFileModifiedDate.getValue())) + ", " + sizeRemote + " bytes). Not updating file.");
									ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
								}
							} catch (IOException e) {
								String msg = "Skipping file '" + syncItem.getPath() + " because reading local file attributes failed: " + e.getMessage();
								LOGGER.log(Level.FINE, msg, e);
								ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
							}
						}
					}
				}
				return WalkerVisitorResult.Continue;
			}

			private void createLocalDir(File newDirectory, SyncItem syncItem, com.google.api.services.drive.model.File remoteFile) throws IOException {
				LOGGER.log(Level.FINE, "Creating local directory '" + syncItem.getPath() + "'.");
				Path newLocalDir = fileSystemAdapter.createDirectory(newDirectory);
				DateTime lastModifiedDateTime = remoteFile.getModifiedDate();
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
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Unchanged, ReportEntry.getDirection(options)));
					} else {
						try {
							fileSystemAdapter.setLastModifiedTime(file, remoteFile.getModifiedDate().getValue());
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.UpdatedMetadata, ReportEntry.getDirection(options)));
						} catch (IOException e) {
							String msg = "Could not update last modification date of local file '" + file.getAbsolutePath() + "':" + e.getMessage();
							LOGGER.log(Level.WARNING, msg, e);
							ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
						}
					}
				} else {
					LOGGER.log(Level.FINE, "Downloading file '" + syncItem.getPath() + "' because MD5 checksums are not equal (local: " + localFileMd5Checksum + ", remote: " + remoteFileMd5Checksum + ").");
					try {
						InputStream stream = googleDriveAdapter.downloadFile(syncItem);
						fileSystemAdapter.storeFile(stream, syncItem);
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Synchronized, ReportEntry.Action.Updated, ReportEntry.getDirection(options)));
					} catch (Exception e) {
						String msg = "Failed to store file '" + syncItem.getPath() + "': " + e.getMessage();
						LOGGER.log(Level.SEVERE, msg);
						ReportFactory.getInstance(options).log(new ReportEntry(syncItem.getPath(), ReportEntry.Status.Error, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
					}
				}
			}
		});
	}
}
