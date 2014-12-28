package jdrivesync.model;

import java.io.File;
import java.util.Optional;

public class SyncItem {
    protected Optional<File> localFile = Optional.empty();
    protected Optional<com.google.api.services.drive.model.File> remoteFile = Optional.empty();
    protected String path;
    protected Optional<SyncDirectory> parent;

    public SyncItem(Optional<File> localFile, Optional<com.google.api.services.drive.model.File> remoteFile, String path, Optional<SyncDirectory> parent) {
        this.localFile = localFile;
        this.remoteFile = remoteFile;
        this.path = path;
        this.parent = parent;
    }

    public Optional<File> getLocalFile() {
        return localFile;
    }

    public Optional<com.google.api.services.drive.model.File> getRemoteFile() {
        return remoteFile;
    }

    public String getPath() {
        return path;
    }

    public Optional<SyncDirectory> getParent() {
        return parent;
    }

    public void setRemoteFile(Optional<com.google.api.services.drive.model.File> remoteFile) {
        this.remoteFile = remoteFile;
    }

    public void setLocalFile(Optional<File> localFile) {
        this.localFile = localFile;
    }
}
