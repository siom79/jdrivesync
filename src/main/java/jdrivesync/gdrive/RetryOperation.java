package jdrivesync.gdrive;

import jdrivesync.cli.Options;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.logging.LoggerFactory;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RetryOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger();

    public interface RetryCallback<T> {
        T execute() throws IOException;
    }

    public static <T> T executeWithRetry(Options options, RetryCallback<T> retryCallback) throws IOException {
        boolean successful = false;
        int numberOfAttempts = 0;
        T returnValue = null;
        while (!successful && numberOfAttempts < options.getNetworkNumberOfRetries()) {
            try {
                numberOfAttempts++;
                if (numberOfAttempts > 1) {
                    LOGGER.log(Level.FINE, "Retrying network operation for the " + numberOfAttempts + ". time.");
                }
                returnValue = retryCallback.execute();
                successful = true;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Network operation failed for the " + numberOfAttempts + ". time.", e);
            }
			if (!successful) {
				try {
					long sleepMillis = (numberOfAttempts * numberOfAttempts) * options.getNetworkSleepBetweenAttempts();
					LOGGER.log(Level.FINE, "Sleeping for " + sleepMillis + ".");
					Thread.sleep(sleepMillis);
				} catch (InterruptedException ignored) {
				}
			}
        }
        if (!successful) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to execute network operation for " + numberOfAttempts + ". time(s).");
        } else {
            if (numberOfAttempts > 1) {
                LOGGER.log(Level.FINE, "Successfully completed network operation after " + numberOfAttempts + " attempt(s).");
            }
        }
        return returnValue;
    }
}
