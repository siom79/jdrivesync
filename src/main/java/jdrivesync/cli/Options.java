package jdrivesync.cli;

import jdrivesync.constants.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

public class Options {
    private Optional<File> localRootDir = Optional.empty();
    private Optional<String> remoteRootDir = Optional.empty();
    private boolean dryRun = false;
    private boolean deleteFiles = false;
    private boolean useChecksum = false;
    private Optional<String> authenticationFile = Optional.empty();
    private int networkNumberOfAttempts = 3;
    private long networkSleepBetweenAttempts = 10 * 1000;
    private FileNamePatterns ignoreFiles = FileNamePatterns.create(Arrays.asList(""));
    private boolean htmlReport = false;
    private SyncDirection syncDirection = SyncDirection.Up;
    private Optional<Long> maxFileSize = Optional.empty();
    private FileNamePatterns encryptFiles = FileNamePatterns.create(Arrays.asList(""));
    private String encryptPassword = "";
    private long lastModificationDateThreshold = 1500;
    private long httpChunkSizeInBytes = 10 * Constants.MB;

    public long getNetworkSleepBetweenAttempts() {
        return networkSleepBetweenAttempts;
    }

    public void setNetworkSleepBetweenAttempts(long networkSleepBetweenAttempts) {
        this.networkSleepBetweenAttempts = networkSleepBetweenAttempts;
    }

    public void setLocalRootDir(Optional<File> localRootDir) {
        this.localRootDir = localRootDir;
    }

    public Optional<File> getLocalRootDir() {
        return localRootDir;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setDeleteFiles(boolean deleteFiles) {
        this.deleteFiles = deleteFiles;
    }

    public boolean isDeleteFiles() {
        return deleteFiles;
    }

    public boolean isUseChecksum() {
        return useChecksum;
    }

    public void setUseChecksum(boolean useChecksum) {
        this.useChecksum = useChecksum;
    }

    @Override
    public String toString() {
        return "Options{" +
                "localRootDir=" + localRootDir +
                ", remoteRootDir=" + remoteRootDir +
                ", dryRun=" + dryRun +
                ", deleteFiles=" + deleteFiles +
                ", useChecksum=" + useChecksum +
                ", authenticationFile=" + authenticationFile +
                ", networkNumberOfAttempts=" + networkNumberOfAttempts +
                ", ignoreFiles=" + ignoreFiles +
                ", htmlReport=" + htmlReport +
                ", syncDirection=" + syncDirection +
                ", maxFileSize=" + maxFileSize +
                ", encryptFiles=" + encryptFiles +
                ", encryptPassword='" + encryptPassword + '\'' +
                ", lastModificationDateThreshold=" + lastModificationDateThreshold +
                ", networkSleepBetweenAttempts=" + networkSleepBetweenAttempts +
                '}';
    }

    public void setAuthenticationFile(Optional<String> authenticationFile) {
        this.authenticationFile = authenticationFile;
    }

    public Optional<String> getAuthenticationFile() {
        return authenticationFile;
    }

    public int getNetworkNumberOfRetries() {
        return networkNumberOfAttempts;
    }

    public void setNetworkNumberOfAttempts(int networkNumberOfAttempts) {
        this.networkNumberOfAttempts = networkNumberOfAttempts;
    }

    public void setRemoteRootDir(Optional<String> remoteRootDir) {
        this.remoteRootDir = remoteRootDir;
    }

    public Optional<String> getRemoteRootDir() {
        return remoteRootDir;
    }

    public void setIgnoreFiles(FileNamePatterns ignoreFiles) {
        this.ignoreFiles = ignoreFiles;
    }

    public FileNamePatterns getIgnoreFiles() {
        return ignoreFiles;
    }

    public void setHtmlReport(boolean htmlReport) {
        this.htmlReport = htmlReport;
    }

    public boolean isHtmlReport() {
        return htmlReport;
    }

    public void setSyncDirection(SyncDirection syncDirection) {
        this.syncDirection = syncDirection;
    }

    public SyncDirection getSyncDirection() {
        return syncDirection;
    }

    public void setMaxFileSize(Optional<Long> maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Optional<Long> getMaxFileSize() {
        return maxFileSize;
    }

    public void setEncryptFiles(FileNamePatterns encryptFiles) {
        this.encryptFiles = encryptFiles;
    }

    public FileNamePatterns getEncryptFiles() {
        return encryptFiles;
    }

    public void setEncryptPassword(String encryptPassword) {
        this.encryptPassword = encryptPassword;
    }

    public String getEncryptPassword() {
        return encryptPassword;
    }

    public long getLastModificationDateThreshold() {
        return lastModificationDateThreshold;
    }

    public void setHttpChunkSizeInBytes(long httpChunkSize) {
        this.httpChunkSizeInBytes = httpChunkSize;
    }

    public long getHttpChunkSizeInBytes() {
        return httpChunkSizeInBytes;
    }
}
