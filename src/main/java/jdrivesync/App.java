package jdrivesync;

import jdrivesync.cli.CliParser;
import jdrivesync.cli.Options;
import jdrivesync.cli.SyncDirection;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.fs.FileSystemAdapter;
import jdrivesync.gdrive.GoogleDriveAdapter;
import jdrivesync.stats.Statistics;
import jdrivesync.sync.Synchronization;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        try {
            App app = new App();
            app.run(args);
        } catch (Exception e) {
            if (e instanceof JDriveSyncException) {
                JDriveSyncException jdriveSyncException = (JDriveSyncException) e;
                JDriveSyncException.Reason reason = jdriveSyncException.getReason();
                String message = jdriveSyncException.getMessage();
                switch (reason) {
                    case NormalTermination:
                        if (message != null && message.length() > 0) {
                            LOGGER.log(Level.INFO, message);
                        }
                        System.exit(0);
                    default:
                        LOGGER.log(Level.SEVERE, message, e);
                        System.exit(-1);
                }
            } else {
                LOGGER.log(Level.SEVERE, "The following error occurred: " + e.getMessage(), e);
                System.exit(-1);
            }
        }
    }

    private void run(String[] args) {
        initLogging();
        Options options = parseCli(args);
        sync(options);
        printStatistics();
    }

    private void printStatistics() {
        Statistics statistics = Statistics.getInstance();
        LOGGER.log(Level.INFO, String.format("Statistics:"));
        LOGGER.log(Level.INFO, String.format("NEW:       %s %s", statistics.getCreated(), statistics.getCreated() != 1 ? "files" : "file"));
        LOGGER.log(Level.INFO, String.format("DELETED:   %s %s", statistics.getDeleted(), statistics.getDeleted() != 1 ? "files" : "file"));
        LOGGER.log(Level.INFO, String.format("UPDATED:   %s %s", statistics.getUpdated(), statistics.getUpdated() != 1 ? "files" : "file"));
        LOGGER.log(Level.INFO, String.format("UNCHANGED: %s %s", statistics.getUnchanged(), statistics.getUnchanged() != 1 ? "files" : "file"));
    }

    public static void initLogging() {
        String filename = "logging.properties";
        System.setProperty("java.util.logging.config.file", filename);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s(): %5$s%6$s%n");
        try {
            InputStream inputStream = App.class.getResourceAsStream("/" + filename);
            if (inputStream == null) {
                throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not find logging configuration file: " + filename);
            }
            LogManager.getLogManager().readConfiguration(inputStream);
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to read logging configuration: " + e.getMessage(), e);
        }
    }

    void sync(Options options) {
        final GoogleDriveAdapter googleDriveAdapter = GoogleDriveAdapter.initGoogleDriveAdapter(options);
        final FileSystemAdapter fileSystemAdapter = new FileSystemAdapter(options);
        Synchronization synchronization = new Synchronization(googleDriveAdapter, fileSystemAdapter, options);
        if (options.getSyncDirection() == SyncDirection.Up) {
            synchronization.syncUp(options);
        } else {
            synchronization.syncDown(options);
        }
    }

    private Options parseCli(String[] args) {
        try {
            CliParser cliParser = new CliParser();
            Options options = cliParser.parse(args);
            LOGGER.log(Level.INFO, "Using options: " + options);
            return options;
        } catch (JDriveSyncException e) {
            if (e.getReason() == JDriveSyncException.Reason.InvalidCliParameter) {
                System.err.println(e.getMessage());
                CliParser.printHelp();
                throw new JDriveSyncException(JDriveSyncException.Reason.NormalTermination);
            } else {
                throw e;
            }
        }
    }
}
