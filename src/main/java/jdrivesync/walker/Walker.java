package jdrivesync.walker;

public interface Walker {
    void walk(WalkerVisitor fileSystemVisitor);
}
