package jdrivesync.report;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NoopReport implements Report {
	private static final Logger LOGGER = Logger.getLogger(NoopReport.class.getName());

	@Override
	public void log(ReportEntry reportEntry) {
		LOGGER.log(Level.INFO, HtmlReport.statusEntry(reportEntry) + " " + reportEntry.getAction() + " " + reportEntry.getRelativePath());
	}
}
