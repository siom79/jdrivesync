package jdrivesync.report;

import jdrivesync.cli.Options;
import jdrivesync.stats.StatisticsDelegate;

import java.util.logging.Level;

public class ReportFactory {
    private final static Report HTML_REPORT = new StatisticsDelegate(new HtmlReport());
    private final static Report NOOP_REPORT = new StatisticsDelegate(new NoopReport());

    public static Report getInstance(Options options) {
        if (options.isHtmlReport()) {
            return HTML_REPORT;
        } else {
            return NOOP_REPORT;
        }
    }
}
