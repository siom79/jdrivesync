package jdrivesync.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.*;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Lists;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import jdrivesync.cli.Options;
import jdrivesync.constants.Constants;
import jdrivesync.encryption.Encryption;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.gdrive.oauth.Authorize;
import jdrivesync.model.SyncDirectory;
import jdrivesync.model.SyncFile;
import jdrivesync.model.SyncItem;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static jdrivesync.gdrive.RetryOperation.executeWithRetry;

public class GoogleDriveAdapter {
	public static final String MIME_TYPE_FOLDER = "application/vnd.google-apps.folder";
	public static final String MIME_TYPE_UNKNOWN = "application/octet-stream";
	private static final Logger LOGGER = Logger.getLogger(GoogleDriveAdapter.class.getName());
	private final Credential credential;
	private final Options options;
	private final Encryption encryption;

	public GoogleDriveAdapter(Credential credential, Options options) {
		this.credential = credential;
		this.options = options;
		this.encryption = new Encryption(options);
	}

	public static Credential authorize() {
		Authorize authorize = new Authorize();
		try {
			return authorize.authorize();
		} catch (Exception e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.AuthorizationFailed, "Authorization failed: " + e.getMessage(), e);
		}
	}

	public File getFile(String id) {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			File file = executeWithRetry(options, () -> drive.files().get(id).execute());
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Got file : " + file.getId() + ":" + file.getTitle());
			}
			return file;
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to execute get file request: " + e.getMessage(), e);
		}
	}

	public List<File> listChildren(String parentId) {
		List<File> resultList = new LinkedList<File>();
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			Drive.Files.List request = drive.files().list();
			request.setQ("trashed = false and '" + parentId + "' in parents");
			request.setMaxResults(1000);
			LOGGER.log(Level.FINE, "Listing children of folder " + parentId + ".");
			do {
				FileList fileList = executeWithRetry(options, () -> request.execute());
				List<File> items = fileList.getItems();
				resultList.addAll(items);
				request.setPageToken(fileList.getNextPageToken());
			} while (request.getPageToken() != null && request.getPageToken().length() > 0);
			if (LOGGER.isLoggable(Level.FINE)) {
				for (File file : resultList) {
					LOGGER.log(Level.FINE, "Child of " + parentId + ": " + file.getId() + ";" + file.getTitle() + ";" + file.getMimeType());
				}
			}
			removeDuplicates(resultList);
			return resultList;
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to execute list request: " + e.getMessage(), e);
		}
	}

	private void removeDuplicates(List<File> resultList) {
		Map<String, File> fileNameMap = new HashMap<>();
		for (File file : resultList) {
			String title = file.getTitle();
			File titleFound = fileNameMap.get(title);
			if (titleFound == null) {
				fileNameMap.put(title, file);
			} else {
				LOGGER.log(Level.WARNING, "Ignoring remote file '" + title + "' (id: '" + file.getId() + "') because its title/name appears more than once in this folder.");
				resultList.remove(file);
			}
		}
	}

	public void deleteFile(File id) {
		delete(id);
	}

	public void deleteDirectory(File id) {
		delete(id);
	}

	private void delete(File file) {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			String id = file.getId();
			if (isGoogleAppsDocument(file)) {
				return;
			}
			if (options.isDeleteFiles()) {
				LOGGER.log(Level.FINE, "Deleting file " + id + ".");
				if (!options.isDryRun()) {
					executeWithRetry(options, () -> drive.files().delete(id).execute());
				}
			} else {
				LOGGER.log(Level.FINE, "Trashing file " + id + ".");
				if (!options.isDryRun()) {
					executeWithRetry(options, () -> drive.files().trash(id).execute());
				}
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to delete file: " + e.getMessage(), e);
		}
	}

	public boolean isGoogleAppsDocument(File file) {
		String mimeType = file.getMimeType();
		if (mimeType != null && mimeType.startsWith("application/vnd.google-apps") && !mimeType.equals("application/vnd.google-apps.folder")) {
			LOGGER.log(Level.FINE, "Not touching file " + file.getId() + " because it is a Google Apps document.");
			return true;
		}
		return false;
	}

	public boolean isDirectory(File file) {
		if (MIME_TYPE_FOLDER.equals(file.getMimeType())) {
			return true;
		}
		return false;
	}

	public InputStream downloadFile(SyncItem syncItem) {
		Drive drive = DriveFactory.getDrive(this.credential);
		InputStream inputStream = null;
		try {
			File remoteFile = syncItem.getRemoteFile().get();
			HttpRequest httpRequest = drive.getRequestFactory().buildGetRequest(new GenericUrl(remoteFile.getDownloadUrl()));
			LOGGER.log(Level.FINE, "Downloading file " + remoteFile.getId() + ".");
			if (!options.isDryRun()) {
				HttpResponse httpResponse = executeWithRetry(options, () -> httpRequest.execute());
				return httpResponse.getContent();
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to delete file: " + e.getMessage(), e);
		}
		return inputStream;
	}

	public void updateFile(SyncItem syncItem) {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			java.io.File localFile = syncItem.getLocalFile().get();
			File remoteFile = syncItem.getRemoteFile().get();
			BasicFileAttributes attr = Files.readAttributes(localFile.toPath(), BasicFileAttributes.class);
			remoteFile.setModifiedDate(new DateTime(attr.lastModifiedTime().toMillis()));
			if (isGoogleAppsDocument(remoteFile)) {
				return;
			}
			LOGGER.log(Level.FINE, "Updating file " + remoteFile.getId() + " (" + syncItem.getPath() + ").");
			if (!options.isDryRun()) {
				Drive.Files.Update updateRequest = drive.files().update(remoteFile.getId(), remoteFile, new FileContent(determineMimeType(localFile), localFile));
				updateRequest.setSetModifiedDate(true);
				File updatedFile = executeWithRetry(options, () -> updateRequest.execute());
				syncItem.setRemoteFile(Optional.of(updatedFile));
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to update file: " + e.getMessage(), e);
		}
	}

	public void updateMetadata(SyncItem syncItem) {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			java.io.File localFile = syncItem.getLocalFile().get();
			File remoteFile = syncItem.getRemoteFile().get();
			BasicFileAttributes attr = Files.readAttributes(localFile.toPath(), BasicFileAttributes.class);
			remoteFile.setModifiedDate(new DateTime(attr.lastModifiedTime().toMillis()));
			if (isGoogleAppsDocument(remoteFile)) {
				return;
			}
			LOGGER.log(Level.FINE, "Updating metadata of remote file " + remoteFile.getId() + " (" + syncItem.getPath() + ").");
			if (!options.isDryRun()) {
				Drive.Files.Update updateRequest = drive.files().update(remoteFile.getId(), remoteFile);
				updateRequest.setSetModifiedDate(true);
				File updatedFile = executeWithRetry(options, () -> updateRequest.execute());
				syncItem.setRemoteFile(Optional.of(updatedFile));
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to update file: " + e.getMessage(), e);
		}
	}

	private String determineMimeType(java.io.File file) {
		String mimeType = GoogleDriveAdapter.MIME_TYPE_UNKNOWN;
		try {
			String contentType = Files.probeContentType(file.toPath());
			if (contentType != null) {
				mimeType = contentType;
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to probe MIME type for file ('" + file.getAbsolutePath() + "'): " + e.getMessage() + ". Using '" + mimeType + "' instead.", e);
		}
		return mimeType;
	}

	public void store(SyncFile syncFile) {
		String mimeType = determineMimeType(syncFile.getLocalFile().get());
		Drive drive = DriveFactory.getDrive(this.credential);
		InputStream inputStream = null;
		try {
			java.io.File localFile = syncFile.getLocalFile().get();
			inputStream = new FileInputStream(localFile);
			if (options.getEncryptFiles().matches(syncFile.getPath(), false)) {
				inputStream = encryption.encrypt(Files.readAllBytes(localFile.toPath()));
			}
			File remoteFile = new File();
			remoteFile.setTitle(localFile.getName());
			remoteFile.setMimeType(mimeType);
			remoteFile.setParents(createParentReferenceList(syncFile));
			BasicFileAttributes attr = Files.readAttributes(localFile.toPath(), BasicFileAttributes.class);
			remoteFile.setModifiedDate(new DateTime(attr.lastModifiedTime().toMillis()));
			LOGGER.log(Level.FINE, "Inserting new file '" + syncFile.getPath() + "' (" + bytesWithUnit(attr.size()) + ").");
			if (!options.isDryRun()) {
				final InputStream finalInputStream = inputStream;
				File insertedFile = executeWithRetry(options, () -> {
					Drive.Files.Insert insert = drive.files().insert(remoteFile, new InputStreamContent(remoteFile.getMimeType(), finalInputStream));
					MediaHttpUploader mediaHttpUploader = insert.getMediaHttpUploader();
					LOGGER.log(Level.FINE, "Chunk size: " + mediaHttpUploader.getChunkSize());
					mediaHttpUploader.setProgressListener(uploader -> LOGGER.log(Level.FINE, "Uploaded " + bytesWithUnit(uploader.getNumBytesUploaded()) + ", " + uploader.getUploadState()));
					return insert.execute();
				});
				syncFile.setRemoteFile(Optional.of(insertedFile));
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to update file: " + e.getMessage(), e);
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private String bytesWithUnit(long fileSize) {
		StringBuilder sb = new StringBuilder();
		if (fileSize < Constants.KB) {
			sb.append(fileSize);
			sb.append(" Byte");
		} else if (fileSize >= Constants.KB && fileSize < Constants.MB) {
			sb.append(fileSize / Constants.KB);
			sb.append(" KByte");
		} else if (fileSize >= Constants.MB && fileSize < Constants.GB) {
			sb.append(fileSize / Constants.MB);
			sb.append(" MByte");
		} else {
			sb.append(fileSize / Constants.GB);
			sb.append(" GByte");
		}
		return sb.toString();
	}

	private List<ParentReference> createParentReferenceList(SyncItem syncItem) {
		if (syncItem.getParent().isPresent()) {
			SyncDirectory syncItemParent = syncItem.getParent().get();
			Optional<File> remoteFileOptional = syncItemParent.getRemoteFile();
			if (remoteFileOptional.isPresent()) {
				return Arrays.asList(new ParentReference().setId(remoteFileOptional.get().getId()));
			}
		}
		return Lists.newArrayList();
	}

	public void store(SyncDirectory syncDirectory) {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			java.io.File localFile = syncDirectory.getLocalFile().get();
			File remoteFile = new File();
			remoteFile.setTitle(localFile.getName());
			remoteFile.setMimeType(MIME_TYPE_FOLDER);
			remoteFile.setParents(createParentReferenceList(syncDirectory));
			BasicFileAttributes attr = Files.readAttributes(localFile.toPath(), BasicFileAttributes.class);
			remoteFile.setModifiedDate(new DateTime(attr.lastModifiedTime().toMillis()));
			LOGGER.log(Level.FINE, "Inserting new directory '" + syncDirectory.getPath() + "'.");
			if (!options.isDryRun()) {
				File insertedFile = executeWithRetry(options, () -> drive.files().insert(remoteFile).execute());
				syncDirectory.setRemoteFile(Optional.of(insertedFile));
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to update file: " + e.getMessage(), e);
		}
	}

	public File createDirectory(File parentDirectory, String title) {
		File returnValue = null;
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			File remoteFile = new File();
			remoteFile.setTitle(title);
			remoteFile.setMimeType(MIME_TYPE_FOLDER);
			remoteFile.setParents(Arrays.asList(new ParentReference().setId(parentDirectory.getId())));
			LOGGER.log(Level.FINE, "Creating new directory '" + title + "'.");
			if (!options.isDryRun()) {
				returnValue = executeWithRetry(options, () -> drive.files().insert(remoteFile).execute());
			}
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to create directory: " + e.getMessage(), e);
		}
		return returnValue;
	}

	public void deleteAll() {
		List<File> result = listAll();
		for (File file : result) {
			delete(file);
		}
	}

	public List<File> listAll() {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			List<File> result = new ArrayList<>();
			Drive.Files.List request = drive.files().list();
			request.setMaxResults(1000);
			do {
				FileList files = executeWithRetry(options, () -> request.execute());
				result.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());
			} while (request.getPageToken() != null && request.getPageToken().length() > 0);
			return result;
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to list all files: " + e.getMessage(), e);
		}
	}

	public List<File> search(Optional<String> title) {
		Drive drive = DriveFactory.getDrive(this.credential);
		try {
			List<File> result = new ArrayList<File>();
			Drive.Files.List request = drive.files().list();
			request.setMaxResults(1000);
			String query = "";
			if (title.isPresent()) {
				query += " title = '" + title.get() + "'";
			}
			if (query.length() > 0) {
				request.setQ(query);
			}
			do {
				FileList files = executeWithRetry(options, () -> request.execute());
				result.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());
			} while (request.getPageToken() != null && request.getPageToken().length() > 0);
			return result;
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to list all files: " + e.getMessage(), e);
		}
	}

	public static GoogleDriveAdapter initGoogleDriveAdapter(Options options) {
		CredentialStore credentialStore = new CredentialStore(options);
		Optional<Credential> credentialOptional = credentialStore.load();
		if (!credentialOptional.isPresent()) {
			Credential credential = GoogleDriveAdapter.authorize();
			credentialStore.store(credential);
		}
		return new GoogleDriveAdapter(credentialStore.getCredential().get(), options);
	}
}
