package jdrivesync.walker;

import jdrivesync.cli.Options;

public interface Walker {
    void walk(Options options, WalkerVisitor fileSystemVisitor);
}
