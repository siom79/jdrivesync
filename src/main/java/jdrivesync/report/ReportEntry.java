package jdrivesync.report;

import java.util.Optional;

public class ReportEntry {
    private final String relativePath;
    private final Status status;
    private final Action action;
    private Optional<String> errorMessage = Optional.empty();

    public enum Status {
        Synchronized, Error
    }

    public enum Action {
        Created, Updated, UpdatedMetadata, Unchanged, Skipped, Deleted
    }

    public ReportEntry(String relativePath, Status status, Action action) {
        this.relativePath = relativePath;
        this.status = status;
        this.action = action;
    }

    public ReportEntry(String relativePath, Status status, Action action, String errorMessage) {
        this(relativePath, status, action);
        this.errorMessage = Optional.ofNullable(errorMessage);
    }

    public String getRelativePath() {
        return relativePath;
    }

    public Status getStatus() {
        return status;
    }

    public Action getAction() {
        return action;
    }

    public Optional<String> getErrorMessage() {
        return errorMessage;
    }
}
