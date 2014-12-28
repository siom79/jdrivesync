package jdrivesync.exception;

public class JDriveSyncException extends RuntimeException {
    private final Reason reason;

    public enum Reason {NormalTermination, IOException, AuthorizationFailed, IllegalStateException, NoSuchAlgorithmException, InvalidRemoteRootDirectory, InvalidCliParameter, Encryption;}

    public JDriveSyncException(Reason reason) {
        this.reason = reason;
    }

    public JDriveSyncException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public JDriveSyncException(Reason reason, String message, Throwable throwable) {
        super(message, throwable);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
