package jdrivesync.cli;

import jdrivesync.constants.Constants;
import jdrivesync.exception.JDriveSyncException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class CliParser {

    private enum Argument {
        Help("-h", "--help", "Prints this help."),
        LocalRootDir("-l", "--local-dir", "Provides the local directory that should be synchronized.", "<local-dir>"),
        RemoteRootDir("-r", "--remote-dir", "Provides the remote directory that should be synchronized.", "<remote-dir>"),
        AuthenticationFile("-a", "--authentication-file", "Use given authentication file instead of default one (.jdrivesync).", "<auth-file>"),
        DryRun(null, "--dry-run", "Simulates all data manipulating operations (dry run)."),
        Delete(null, "--delete", "Deletes all files instead of moving them to trash."),
        Checksum("-c", "--checksum", "Use MD5 checksum instead of last modification timestamp of file."),
        IgnoreFile("-i", "--ignore-file", "Provides a file with newline separated file and/or path name patterns that should be ignored.", "<ignore-file>"),
        SyncUp("-u", "--up", "Synchronization is performed from the local to the remote site (default)."),
        SyncDown("-d", "--down", "Synchronization is performed from the remote to the local site."),
        HtmlReport(null, "--html-report", "Creates an HTML report of the synchronization."),
        MaxFileSize("-m", "--max-file-size", "Provides the maximum file size in MB.", "<maxFileSize>"),
        HttpChunkSize(null, "--http-chunk-size", "The size of a chunk in MB used for chunked uploads (default: 10MB)."),
        NetworkNumberOfReries(null, "--network-number-of-retries", "The number of times how often a request is retried (default: 3)."),
        NetworkSleepBetweenRetries(null, "--network-sleep-between-retries", "The number of seconds to sleep between retries (default: 10).");
        //Password("-p", "--password", "The password used to encrypt/decrypt the files.", "<password>"),
        //EncryptFile("-e", "--encrypt-files", "Provides a file with newline separated file and/or path name patterns that should be encrypted.", "<encrypt-file>");

        private final String shortOption;
        private final String longOption;
        private final String description;
        private final Optional<String> argument;

        Argument(String shortOption, String longOption, String description) {
            this.shortOption = shortOption;
            this.longOption = longOption;
            this.description = description;
            this.argument = Optional.empty();
        }

        Argument(String shortOption, String longOption, String description, String argument) {
            this.shortOption = shortOption;
            this.longOption = longOption;
            this.description = description;
            this.argument = Optional.of(argument);
        }

        public boolean matches(String arg) {
            boolean matches = false;
            if (shortOption != null && shortOption.equals(arg)) {
                matches = true;
            }
            if (longOption != null && longOption.equals(arg)) {
                matches = true;
            }
            return matches;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (shortOption != null) {
                sb.append(shortOption);
            }
            if (longOption != null) {
                if (shortOption != null) {
                    sb.append(",");
                }
                sb.append(longOption);
            }
            if(argument.isPresent()) {
                sb.append(" ");
                sb.append(argument.get());
            }
            sb.append("\n");
            sb.append("\t");
            sb.append(description);
            return sb.toString();
        }
    }

    public Options parse(String[] args) throws IllegalArgumentException {
        Options options = new Options();
        StringArrayEnumeration sae = new StringArrayEnumeration(args);
        while (sae.hasMoreElements()) {
            String arg = sae.nextElement();
            Argument argument = toArgument(arg);
            if (argument == Argument.Help) {
                printHelp();
            } else if (argument == Argument.LocalRootDir) {
                String localRootDir = getOptionWithArgument(arg, sae);
                File file = validateLocalRootDirArg(localRootDir);
                options.setLocalRootDir(Optional.of(file));
            } else if (argument == Argument.RemoteRootDir) {
                String remoteRootDir = getOptionWithArgument(arg, sae);
                options.setRemoteRootDir(Optional.of(remoteRootDir));
            } else if (argument == Argument.AuthenticationFile) {
                String authenticationFile = getOptionWithArgument(arg, sae);
                options.setAuthenticationFile(Optional.of(authenticationFile));
            } else if (argument == Argument.DryRun) {
                options.setDryRun(true);
            } else if (argument == Argument.Delete) {
                options.setDeleteFiles(true);
            } else if (argument == Argument.Checksum) {
                options.setUseChecksum(true);
            } else if (argument == Argument.IgnoreFile) {
                String patternArg = getOptionWithArgument(arg, sae);
                List<String> lines = readFile(patternArg);
                FileNamePatterns ignoreFiles = FileNamePatterns.create(lines);
                options.setIgnoreFiles(ignoreFiles);
            } else if (argument == Argument.HtmlReport) {
                options.setHtmlReport(true);
            } else if (argument == Argument.SyncUp) {
                options.setSyncDirection(SyncDirection.Up);
            } else if (argument == Argument.SyncDown) {
                options.setSyncDirection(SyncDirection.Down);
            } else if (argument == Argument.MaxFileSize) {
                String option = getOptionWithArgument(arg, sae);
                Long maxFileSizeInteger;
                try {
                    maxFileSizeInteger = Long.valueOf(option);
                } catch (NumberFormatException e) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is not an integer.");
                }
                options.setMaxFileSize(Optional.of(maxFileSizeInteger * Constants.MB));
            } else if (argument == Argument.HttpChunkSize) {
                String option = getOptionWithArgument(arg, sae);
                long httpChunkSizeMB;
                try {
                    httpChunkSizeMB = Long.valueOf(option);
                } catch (NumberFormatException e) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is not an integer.");
                }
                long httpChunkSizeBytes = httpChunkSizeMB * Constants.MB;
                httpChunkSizeBytes = (httpChunkSizeBytes / 256) * 256; // chunk size must be multiple of 256
                if (httpChunkSizeMB <= 0) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is a negative integer or zero.");
                }
                options.setHttpChunkSizeInBytes(httpChunkSizeBytes);
            } else if (argument == Argument.NetworkNumberOfReries) {
                String option = getOptionWithArgument(arg, sae);
                int networkNumberOfRetries;
                try {
                    networkNumberOfRetries = Integer.valueOf(option);
                } catch (NumberFormatException e) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is not an integer.");
                }
				if (networkNumberOfRetries < 0) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is a negative integer.");
				}
                options.setNetworkNumberOfAttempts(networkNumberOfRetries);
            } else if (argument == Argument.NetworkSleepBetweenRetries) {
                String option = getOptionWithArgument(arg, sae);
                int optionAsInteger;
                try {
                    optionAsInteger = Integer.valueOf(option);
                } catch (NumberFormatException e) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is not an integer.");
                }
				if (optionAsInteger <= 0) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' is a negative integer or zero.");
				}
                options.setNetworkSleepBetweenAttempts(optionAsInteger * 1000);
            } else {
                throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "The parameter '" + arg + "' is not valid.");
            }
        }
        checkForMandatoryOptions(options);
        normalizeRemoteRootDir(options);
        return options;
    }

    private Argument toArgument(String arg) {
        for(Argument currentArgument : Argument.values()) {
            if(currentArgument.matches(arg)) {
                return currentArgument;
            }
        }
        return null;
    }

    private File validateLocalRootDirArg(String localRootDir) {
        File file = new File(localRootDir);
        if (!file.exists()) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("'%s' does not exist.", localRootDir));
        }
        if (!file.canRead()) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("Directory '%s' is not readable.", localRootDir));
        }
        if (!file.isDirectory()) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("'%s' is not a directory.", localRootDir));
        }
        return file;
    }

    public void normalizeRemoteRootDir(Options options) {
        if (options.getRemoteRootDir().isPresent()) {
            String remoteRootDir = options.getRemoteRootDir().get();
            remoteRootDir = remoteRootDir.trim();
            remoteRootDir = remoteRootDir.replace("\\", "/");
            if (remoteRootDir.startsWith("/")) {
                remoteRootDir = remoteRootDir.substring(1, remoteRootDir.length());
            }
            options.setRemoteRootDir(Optional.of(remoteRootDir));
        }
    }

    public static void printHelp() {
        System.out.println("Available parameters:");
        for(Argument currentArg : Argument.values()) {
            System.out.println(currentArg.toString());
        }
        throw new JDriveSyncException(JDriveSyncException.Reason.NormalTermination);
    }

    private void checkForMandatoryOptions(Options options) {
        boolean valid = true;
        String message = null;
        if (!options.getLocalRootDir().isPresent()) {
            message = "Please specify a local directory that should be synchronized.";
            valid = false;
        }
        if (!valid) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, message);
        }
    }

    private String getOptionWithArgument(String option, StringArrayEnumeration sae) {
        if (sae.hasMoreElements()) {
            String value = sae.nextElement();
            if (toArgument(value) != null) {
                throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("Missing argument for option %s.", option));
            }
            return value;
        } else {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("Missing argument for option %s.", option));
        }
    }

    private List<String> readFile(String filename) {
        Path path;
        try {
            path = Paths.get(filename);
        } catch (Exception e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "'" + filename + "' does not denote a valid path: " + e.getMessage(), e);
        }
        if (!Files.exists(path)) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "'" + filename + "' does not exist.");
        }
        try {
            return Files.readAllLines(path, Charset.defaultCharset());
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Could not read file '" + path + "':" + e.getMessage(), e);
        }
    }
}
