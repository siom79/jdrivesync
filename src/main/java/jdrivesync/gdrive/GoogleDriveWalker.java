package jdrivesync.gdrive;

import com.google.api.services.drive.model.File;
import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.logging.LoggerFactory;
import jdrivesync.model.SyncDirectory;
import jdrivesync.model.SyncFile;
import jdrivesync.model.SyncItem;
import jdrivesync.report.ReportEntry;
import jdrivesync.report.ReportFactory;
import jdrivesync.walker.Walker;
import jdrivesync.walker.WalkerVisitor;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GoogleDriveWalker implements Walker {
	private static final Logger LOGGER = LoggerFactory.getLogger();
	private final Options options;
	private final GoogleDriveAdapter googleDriveAdapter;

	public GoogleDriveWalker(Options options, GoogleDriveAdapter googleDriveAdapter) {
		this.options = options;
		this.googleDriveAdapter = googleDriveAdapter;
	}

	@Override
	public void walk(Options options, WalkerVisitor walkerVisitor) {
		File remoteRootFile = googleDriveAdapter.getFile("root");
		remoteRootFile = getRemoteRootDir(remoteRootFile);
		java.io.File localRootFile = options.getLocalRootDir().get();
		SyncDirectory rootDirectory = new SyncDirectory(Optional.of(localRootFile), Optional.of(remoteRootFile), "/", Optional.empty());
		walkInternal(rootDirectory, googleDriveAdapter, walkerVisitor, options);
	}

	private File getRemoteRootDir(File remoteRootFile) {
		File currentRemoteDir = remoteRootFile;
		if (options.getRemoteRootDir().isPresent()) {
			String remoteRootDir = options.getRemoteRootDir().get();
			String[] pathParts = remoteRootDir.split("/");
			for (String pathPart : pathParts) {
				if (pathPart.length() == 0) {
					continue;
				}
				File foundRemoteDir = null;
				List<File> remoteChildren = googleDriveAdapter.listChildren(currentRemoteDir.getId());
				for (File remoteChild : remoteChildren) {
					if (remoteChild.getTitle().equals(pathPart)) {
						if (googleDriveAdapter.isDirectory(remoteChild)) {
							foundRemoteDir = remoteChild;
						} else {
							throw new JDriveSyncException(JDriveSyncException.Reason.InvalidRemoteRootDirectory, "The remote root directory path '" + remoteRootDir + "' contains a file: '" + pathPart + "'.");
						}
					}
				}
				if (foundRemoteDir == null) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidRemoteRootDirectory, "The remote path '" + remoteRootDir + "' does not exist.");
				} else {
					currentRemoteDir = foundRemoteDir;
				}
			}
		}
		return currentRemoteDir;
	}

	private void walkInternal(SyncDirectory syncDirectory, GoogleDriveAdapter googleDriveAdapter, WalkerVisitor visitor, Options options) {
		if (syncDirectory.getRemoteFile().isPresent()) {
			File remoteFile = syncDirectory.getRemoteFile().get();
			List<File> remoteChildren = googleDriveAdapter.listChildren(remoteFile.getId());
			for (File file : remoteChildren) {
				if (googleDriveAdapter.isGoogleAppsDocument(file)) {
					String msg = "Skipped file '" + file.getTitle() + "' because it is a Google Apps document.";
					ReportFactory.getInstance(this.options).log(new ReportEntry(file.getTitle(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_GoogleApps, ReportEntry.getDirection(options), msg));
					continue;
				}
				if (googleDriveAdapter.fileNameValid(file)) {
					String msg = "Skipped file '" + file.getTitle() + "' because it has an invalid file name.";
					ReportFactory.getInstance(this.options).log(new ReportEntry(file.getTitle(), ReportEntry.Status.Skipped, ReportEntry.Action.Skipped_Error, ReportEntry.getDirection(options), msg));
					continue;
				}
				String relativePath = toRelativePath(file, syncDirectory);
				if (googleDriveAdapter.isDirectory(file)) {
					if (!fileShouldBeIgnored(relativePath, true, file)) {
						SyncDirectory subSyncDirectory = new SyncDirectory(Optional.empty(), Optional.of(file), relativePath, Optional.of(syncDirectory));
						syncDirectory.addChild(subSyncDirectory);
					}
				} else {
					if (!fileShouldBeIgnored(relativePath, false, file)) {
						SyncFile syncFile = new SyncFile(Optional.empty(), Optional.of(file), relativePath, Optional.of(syncDirectory));
						syncDirectory.addChild(syncFile);
					}
				}
			}
			WalkerVisitor.WalkerVisitorResult result = visitor.visitDirectory(syncDirectory);
			if (result == WalkerVisitor.WalkerVisitorResult.Continue) {
				Iterator<SyncItem> childrenIterator = syncDirectory.getChildrenIterator();
				while (childrenIterator.hasNext()) {
					SyncItem syncItem = childrenIterator.next();
					if (syncItem instanceof SyncDirectory) {
						SyncDirectory subSyncDir = (SyncDirectory) syncItem;
						walkInternal(subSyncDir, googleDriveAdapter, visitor, options);
					}
					childrenIterator.remove(); //free memory
				}
			}
		} else {
			LOGGER.log(Level.FINE, "Skipping directory '" + syncDirectory.getPath() + "' because remote file is not set.");
		}
	}

	private String toRelativePath(File file, SyncDirectory syncDirectory) {
		if (syncDirectory.isRootDirectory()) {
			return "/" + file.getTitle();
		} else {
			String parentPath = syncDirectory.getPath();
			return parentPath + "/" + file.getTitle();
		}
	}

	private boolean fileShouldBeIgnored(String name, boolean isDirectory, File file) {
		boolean matches = options.getIgnoreFiles().matches(name, isDirectory);
		if (matches && LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, "Ignoring file '" + name + "' because the name matches the ignore list.");
		}
		if (!matches && options.getMaxFileSize().isPresent()) {
			if (!googleDriveAdapter.isDirectory(file)) {
				Long fileSize = file.getFileSize();
				Long maxFileSize = options.getMaxFileSize().get();
				if (maxFileSize.compareTo(fileSize) < 0) {
					matches = true;
					LOGGER.log(Level.FINE, "Ignoring '" + name + "' because file is bigger than maxFileSize (file: " + fileSize + ", maxFileSize: " + maxFileSize + ").");
				}
			}
		}
		return matches;
	}
}
