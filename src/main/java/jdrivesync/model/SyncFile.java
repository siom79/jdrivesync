package jdrivesync.model;

import java.io.File;
import java.util.Optional;

public class SyncFile extends SyncItem {

    public SyncFile(Optional<File> localFile, Optional<com.google.api.services.drive.model.File> remoteFile, String path, Optional<SyncDirectory> parent) {
        super(localFile, remoteFile, path, parent);
    }
}
