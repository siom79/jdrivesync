package jdrivesync.walker;

import jdrivesync.model.SyncDirectory;

public interface WalkerVisitor {

    public enum WalkerVisitorResult {
        Continue, SkipSubtree
    }

    WalkerVisitorResult visitDirectory(SyncDirectory syncDirectory);
}
