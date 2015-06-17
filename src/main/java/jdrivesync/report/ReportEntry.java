package jdrivesync.report;

import jdrivesync.cli.Options;
import jdrivesync.cli.SyncDirection;

import java.util.Optional;

public class ReportEntry {
    private final String relativePath;
    private final Status status;
    private final Action action;
	private final Direction direction;
    private Optional<String> errorMessage = Optional.empty();

    public enum Status {
        Synchronized, Skipped, Error
    }

    public enum Action {
        Created, Updated, UpdatedMetadata, Unchanged, Deleted, Skipped_Error, Skipped_GoogleApps, Skipped_Deletion
    }

    public enum Direction {
        Down, Up
    }

    public ReportEntry(String relativePath, Status status, Action action, Direction direction) {
        this.relativePath = relativePath;
        this.status = status;
        this.action = action;
		this.direction = direction;
    }

    public ReportEntry(String relativePath, Status status, Action action, Direction direction, String errorMessage) {
        this(relativePath, status, action, direction);
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

	public Direction getDirection() {
		return direction;
	}

	public static ReportEntry.Direction getDirection(Options options) {
		if (options.getSyncDirection() == SyncDirection.Up) {
			return ReportEntry.Direction.Up;
		} else if (options.getSyncDirection() == SyncDirection.Down) {
			return ReportEntry.Direction.Down;
		} else {
			throw new IllegalStateException("Unsupported sync direction: " + options.getSyncDirection());
		}
	}
}
