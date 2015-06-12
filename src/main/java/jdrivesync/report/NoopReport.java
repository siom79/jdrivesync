package jdrivesync.report;

import jdrivesync.logging.LoggerFactory;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NoopReport implements Report {
	private static final Logger LOGGER = LoggerFactory.getLogger();

	@Override
	public void log(ReportEntry reportEntry) {
		LOGGER.log(Level.INFO, HtmlReport.statusEntry(reportEntry) + " " + reportEntry.getAction() + " " + reportEntry.getRelativePath());
	}
}
