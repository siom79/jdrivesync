package jdrivesync.logging;

import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.*;

public class LoggerFactory {
	private static final Logger LOGGER = Logger.getLogger("jdrivesync");

	public static void configure(Options options) {
		LOGGER.setLevel(options.isVerbose() ? Level.FINE : Level.INFO);
		Logger googleLogger = Logger.getLogger("com.google.api.client.http");
		googleLogger.setLevel(options.isVerbose() ? Level.FINE : Level.INFO);
		if (options.getLogFile().isPresent()) {
			Path path = options.getLogFile().get();
			try {
				Handler fh = new FileHandler(path.toString());
				SimpleFormatter simpleFormatter = new SimpleFormatter();
				fh.setFormatter(simpleFormatter);
				fh.setLevel(options.isVerbose() ? Level.FINE : Level.INFO);
				LOGGER.addHandler(fh);
				googleLogger.addHandler(fh);
			} catch (IOException e) {
				throw new JDriveSyncException(JDriveSyncException.Reason.IOException, String.format("Failed to create log file '%s': %s", path, e.getMessage()), e);
			}
		}
	}

	public static Logger getLogger() {
		return LOGGER;
	}
}
