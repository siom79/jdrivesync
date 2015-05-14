package jdrivesync.fs;

import jdrivesync.cli.Options;
import jdrivesync.model.SyncDirectory;
import jdrivesync.model.SyncFile;
import jdrivesync.model.SyncItem;
import jdrivesync.report.ReportEntry;
import jdrivesync.report.ReportFactory;
import jdrivesync.util.FileUtil;
import jdrivesync.walker.Walker;
import jdrivesync.walker.WalkerVisitor;

import java.io.File;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileSystemWalker implements Walker {
    private static final Logger LOGGER = Logger.getLogger(FileSystemWalker.class.getName());
    private final File startDirectory;
    private final Options options;
    private FileSystemAdapter fileSystemAdapter;

    public FileSystemWalker(Options options, FileSystemAdapter fileSystemAdapter) {
        this.startDirectory = options.getLocalRootDir().get();
        this.options = options;
        this.fileSystemAdapter = fileSystemAdapter;
    }

    public void walk(WalkerVisitor fileSystemVisitor) {
        SyncDirectory rootDirectory = new SyncDirectory(Optional.of(this.startDirectory), Optional.empty(), "/", Optional.empty());
        walkInternal(rootDirectory, fileSystemVisitor, fileSystemAdapter);
    }

    private void walkInternal(SyncDirectory syncDirectory, WalkerVisitor fileSystemVisitor, FileSystemAdapter fileSystemAdapter) {
        if (syncDirectory.getLocalFile().isPresent()) {
            File directory = syncDirectory.getLocalFile().get();
            if (fileSystemAdapter.exists(directory)) {
                if (fileSystemAdapter.canRead(directory)) {
                    File[] files = fileSystemAdapter.listFiles(directory);
                    if (files != null) {
                        for (File file : files) {
                            String relativePath = FileUtil.toRelativePath(file, options);
                            if (fileSystemAdapter.isDirectory(file)) {
                                if (!fileShouldBeIgnored(relativePath, true, file)) {
                                    SyncDirectory subSyncDirectory = new SyncDirectory(Optional.of(file), Optional.empty(), relativePath, Optional.of(syncDirectory));
                                    syncDirectory.addChild(subSyncDirectory);
                                }
                            } else {
                                if (!fileShouldBeIgnored(relativePath, false, file)) {
                                    SyncFile syncFile = new SyncFile(Optional.of(file), Optional.empty(), relativePath, Optional.of(syncDirectory));
                                    syncDirectory.addChild(syncFile);
                                }
                            }
                        }
                        WalkerVisitor.WalkerVisitorResult result = fileSystemVisitor.visitDirectory(syncDirectory);
                        if (result == WalkerVisitor.WalkerVisitorResult.Continue) {
                            Iterator<SyncItem> childrenIterator = syncDirectory.getChildrenIterator();
                            while (childrenIterator.hasNext()) {
                                SyncItem syncItem = childrenIterator.next();
                                if (syncItem instanceof SyncDirectory) {
                                    SyncDirectory subSyncDir = (SyncDirectory) syncItem;
                                    walkInternal(subSyncDir, fileSystemVisitor, fileSystemAdapter);
                                }
                                childrenIterator.remove(); //free memory
                            }
                        }
                    } else {
                        LOGGER.log(Level.FINE, "Skipping directory '" + directory.getAbsolutePath() + "' because list of files is null/zero.");
                        ReportFactory.getInstance(options).log(new ReportEntry(FileUtil.toRelativePath(directory, options), ReportEntry.Status.Synchronized, ReportEntry.Action.Skipped));
                    }
                } else {
                    LOGGER.log(Level.FINE, "Skipping directory '" + directory.getAbsolutePath() + "' because read permission is missing.");
                    ReportFactory.getInstance(options).log(new ReportEntry(FileUtil.toRelativePath(directory, options), ReportEntry.Status.Error, ReportEntry.Action.Skipped, "Missing read permission."));
                }
            } else {
                LOGGER.log(Level.FINE, "Skipping directory '" + directory.getAbsolutePath() + "' because it does not exist.");
                ReportFactory.getInstance(options).log(new ReportEntry(FileUtil.toRelativePath(directory, options), ReportEntry.Status.Error, ReportEntry.Action.Skipped, "Does not exist."));
            }
        } else {
            LOGGER.log(Level.FINE, "Skipping directory '" + syncDirectory.getPath() + "' because no local directory is set.");
        }
    }

    private boolean fileShouldBeIgnored(String path, boolean isDirectory, File file) {
        boolean matches = options.getIgnoreFiles().matches(path, isDirectory);
        if (matches && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Ignoring '" + path + "' because the name matches the ignore list.");
        }
        if (!matches && options.getMaxFileSize().isPresent()) {
            if (file.isFile()) {
                long maxFileSize = options.getMaxFileSize().get();
                long fileSize = file.length();
                if (fileSize > maxFileSize) {
                    matches = true;
                    LOGGER.log(Level.FINE, "Ignoring '" + path + "' because file is bigger than maxFileSize (file: " + fileSize + ", maxFileSize: " + maxFileSize + ").");
                }
            }
        }
        return matches;
    }
}
