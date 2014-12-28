package jdrivesync.stats;

import jdrivesync.report.Report;
import jdrivesync.report.ReportEntry;

public class StatisticsDelegate implements Report {
    private final Report delegate;

    public StatisticsDelegate(Report delegate) {
        this.delegate = delegate;
    }

    @Override
    public void log(ReportEntry reportEntry) {
        ReportEntry.Action action = reportEntry.getAction();
        switch (action) {
            case Created:
                Statistics.getInstance().created();
                break;
            case Deleted:
                Statistics.getInstance().deleted();
                break;
            case Unchanged:
                Statistics.getInstance().unchanged();
                break;
            case Updated:
            case UpdatedMetadata:
                Statistics.getInstance().updated();
                break;
        }
        delegate.log(reportEntry);
    }
}
