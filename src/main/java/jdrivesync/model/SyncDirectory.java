package jdrivesync.model;

import java.io.File;
import java.util.*;

public class SyncDirectory extends SyncItem {
    private List<SyncItem> children = new LinkedList<>();

    public SyncDirectory(Optional<File> localFile, Optional<com.google.api.services.drive.model.File> remoteFile, String path, Optional<SyncDirectory> parent) {
        super(localFile, remoteFile, path, parent);
    }

    public void addChild(SyncItem child) {
        children.add(child);
    }

    public Iterator<SyncItem> getChildrenIterator() {
        Collections.sort(children, (o1, o2) -> {
            if(o1.getLocalFile().isPresent() && o2.getLocalFile().isPresent()) {
                return o1.getLocalFile().get().getName().compareTo(o2.getLocalFile().get().getName());
            } else if(o1.getRemoteFile().isPresent() && o2.getRemoteFile().isPresent()) {
                return o1.getRemoteFile().get().getTitle().compareTo(o2.getRemoteFile().get().getTitle());
            } else if(o1.getLocalFile().isPresent() && o2.getRemoteFile().isPresent()) {
                return o1.getLocalFile().get().getName().compareTo(o2.getRemoteFile().get().getTitle());
            } else if(o2.getLocalFile().isPresent() && o1.getRemoteFile().isPresent()) {
                return o2.getLocalFile().get().getName().compareTo(o1.getRemoteFile().get().getTitle());
            }
            return 0;
        });
        return children.iterator();
    }

    public boolean isRootDirectory() {
        boolean isRoot = false;
        if("/".equals(path)) {
            isRoot = true;
        }
        return isRoot;
    }
}
