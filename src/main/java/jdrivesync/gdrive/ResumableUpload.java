package jdrivesync.gdrive;

import com.google.api.client.http.*;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.services.drive.Drive;
import jdrivesync.cli.Options;
import jdrivesync.constants.Constants;
import jdrivesync.exception.JDriveSyncException;
import jdrivesync.logging.LoggerFactory;

import java.io.*;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import static jdrivesync.gdrive.RetryOperation.executeWithRetry;

public class ResumableUpload {
    private static final Logger LOGGER = LoggerFactory.getLogger();
    private static final int DEFAULT_CHUNK_SIZE = Constants.MB;
    private Options options;

    private static class Range {
        private final long start;
        private final long end;

        private Range(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public Range(long start, long end, long fileLength) {
            this.start = start;
            if (end < fileLength) {
                this.end = end;
            } else {
                this.end = fileLength - 1;
            }
        }

        public static Range valueOf(String value) {
            if (value == null) {
                throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Invalid Range Header: " + value);
            }
            if (value.startsWith("bytes=")) {
                if (value.length() > "bytes=".length()) {
                    value = value.substring("bytes=".length());
                } else {
                    throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Invalid Range Header: " + value);
                }
            }
            String[] parts = value.split("-");
            if (parts.length != 2) {
                throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Invalid Range Header: " + value);
            }
            long start = 0;
            try {
                start = Long.valueOf(parts[0]);
            } catch (NumberFormatException e) {
                throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not convert the start of the range into a long number (" + parts[0] + "): " + e.getMessage(), e);
            }
            long end = 0;
            try {
                end = Long.valueOf(parts[1]);
            } catch (NumberFormatException e) {
                throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not convert the end of the range into a long number (" + parts[1] + "): " + e.getMessage(), e);
            }
            return new Range(start, end);
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public String toContentRange(long totalSize) {
            return "bytes " + start + "-" + end + "/" + totalSize;
        }

        public long getLength() {
            return (end - start) + 1;
        }
    }

    public ResumableUpload(Options options) {
        this.options = options;
    }

    public void upload(Drive drive, final File fileToUpload, String mimeType, com.google.api.services.drive.model.File remoteFile) throws IOException {
        HttpRequestFactory requestFactory = drive.getRequestFactory();
        String uploadLocation = executeWithRetry(options, () -> requestUploadLocation(fileToUpload, mimeType, requestFactory, remoteFile));
        GenericUrl uploadUrl = new GenericUrl(uploadLocation);
        HttpRequest httpRequest = createHttpRequest(requestFactory, HttpMethods.PUT, uploadUrl, new EmptyContent());
        long fileLength = fileToUpload.length();
        if (fileLength < DEFAULT_CHUNK_SIZE) {
            LOGGER.log(Level.FINE, "File is smaller than chunk size (file: " + fileLength + ", chunk-size: " + DEFAULT_CHUNK_SIZE+ "). Using direct upload.");
            HttpContent fileContent = new HttpContent() {
                @Override
                public long getLength() throws IOException {
                    return fileLength;
                }

                @Override
                public String getType() {
                    return mimeType;
                }

                @Override
                public boolean retrySupported() {
                    return true;
                }

                @Override
                public void writeTo(OutputStream out) throws IOException {
                    try (InputStream fis = new BufferedInputStream(new FileInputStream(fileToUpload))) {
                        byte[] buffer = new byte[1024];
                        int read = fis.read(buffer, 0, buffer.length);
                        while (read > 0) {
                            out.write(buffer, 0, read);
                            read = fis.read(buffer, 0, buffer.length);
                        }
                    }
                }
            };
            httpRequest.setContent(fileContent);
            HttpResponse httpResponse = null;
            try {
                httpResponse = executeHttpRequest(httpRequest);
                if (!httpResponse.isSuccessStatusCode()) {
                    Range rangeResponse = executeWithRetry(options, () -> requestStatus(requestFactory, uploadUrl, fileToUpload));
                    Range range = new Range(rangeResponse.getEnd() + 1, rangeResponse.getEnd() + DEFAULT_CHUNK_SIZE, fileLength);
                    uploadChunks(fileToUpload, mimeType, requestFactory, uploadUrl, httpRequest, fileLength, range);
                }
            } catch (IOException e) {
                Range rangeResponse = executeWithRetry(options, () -> requestStatus(requestFactory, uploadUrl, fileToUpload));
                Range range = new Range(rangeResponse.getEnd() + 1, rangeResponse.getEnd() + DEFAULT_CHUNK_SIZE, fileLength);
                uploadChunks(fileToUpload, mimeType, requestFactory, uploadUrl, httpRequest, fileLength, range);
            }
        } else {
            LOGGER.log(Level.FINE, "File is not smaller than chunk size (file: " + fileLength + ", chunk-size: " + DEFAULT_CHUNK_SIZE+ "). Using chunked upload.");
            Range range = new Range(0, DEFAULT_CHUNK_SIZE - 1, fileLength);
            uploadChunks(fileToUpload, mimeType, requestFactory, uploadUrl, httpRequest, fileLength, range);
        }
    }

    private void uploadChunks(File fileToUpload, final String mimeType, HttpRequestFactory requestFactory, GenericUrl uploadUrl, HttpRequest httpRequest, long fileLength, Range range) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(fileToUpload, "r")) {
            boolean fileUploaded = false;
            int numberOfRetries = 0;
            while (!fileUploaded) {
                httpRequest.getHeaders().setContentRange(range.toContentRange(fileLength));
                final Range finalRange = range;
                HttpContent fileContent = new HttpContent() {
                    @Override
                    public long getLength() throws IOException {
                        return finalRange.getLength();
                    }

                    @Override
                    public String getType() {
                        return mimeType;
                    }

                    @Override
                    public boolean retrySupported() {
                        return true;
                    }

                    @Override
                    public void writeTo(OutputStream out) throws IOException {
                        byte[] buffer = new byte[1024];
                        long bytesWritten = 0L;
                        int bytesToReadTotal = (int) finalRange.getLength();
                        if (bytesToReadTotal <= 0) {
                            LOGGER.log(Level.FINE, "bytesToReadTotal is <= 0.");
                            return;
                        }
                        int bytesToReadNext = buffer.length;
                        if (bytesToReadTotal < buffer.length) {
                            bytesToReadNext = bytesToReadTotal;
                        }
                        randomAccessFile.seek(finalRange.getStart());
                        int read = randomAccessFile.read(buffer, 0, bytesToReadNext);
                        while (read > 0) {
                            out.write(buffer, 0, read);
                            bytesWritten += read;
                            bytesToReadTotal -= read;
                            if (bytesToReadTotal <= 0) {
                                break;
                            }
                            if (bytesToReadTotal < buffer.length) {
                                bytesToReadNext = bytesToReadTotal;
                            }
                            read = randomAccessFile.read(buffer, 0, bytesToReadNext);
                        }
                        LOGGER.log(Level.FINE, "Bytes written in this chunk: " + bytesWritten + "; chunk: " + finalRange.toContentRange(fileLength));
                    }
                };
                httpRequest.setContent(fileContent);
                HttpResponse httpResponse;
                try {
                    LOGGER.log(Level.FINE, "Executing chunk upload for Content-Range: " + range.toContentRange(fileLength) + "(" + (int)(((double)finalRange.getEnd() / (double)fileLength)*100) + "%)");
                    httpResponse = executeHttpRequest(httpRequest);
                    int statusCode = httpResponse.getStatusCode();
                    LOGGER.log(Level.FINE, "Chunk upload finished with status code " + statusCode + ": " + httpResponse.getStatusMessage());
                    if (statusCode == 308) {
                        String rangeResponseHeader = httpResponse.getHeaders().getRange();
                        Range rangeResponse = Range.valueOf(rangeResponseHeader);
                        range = new Range(rangeResponse.getEnd() + 1, rangeResponse.getEnd() + DEFAULT_CHUNK_SIZE, fileLength);
                    } else if (statusCode == 200 || statusCode == 201) {
                        LOGGER.log(Level.FINE, "Upload completed with status code " + statusCode + ".");
                        fileUploaded = true;
                    } else {
                        Range rangeResponse = executeWithRetry(options, () -> requestStatus(requestFactory, uploadUrl, fileToUpload));
                        range = new Range(rangeResponse.getEnd() + 1, rangeResponse.getEnd() + DEFAULT_CHUNK_SIZE, fileLength);
                        numberOfRetries++;
                        if(numberOfRetries > options.getNetworkNumberOfRetries()) {
                            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Skipping file because number of retries has reached maximum of " + options.getNetworkNumberOfRetries() + ".");
                        }
                    }
                } catch (IOException e) {
                    Range rangeResponse = executeWithRetry(options, () -> requestStatus(requestFactory, uploadUrl, fileToUpload));
                    range = new Range(rangeResponse.getEnd() + 1, rangeResponse.getEnd() + DEFAULT_CHUNK_SIZE, fileLength);
                    numberOfRetries++;
                    if(numberOfRetries > options.getNetworkNumberOfRetries()) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Skipping file because number of retries has reached maximum of " + options.getNetworkNumberOfRetries() + ".");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "File not found (" + fileToUpload.getAbsolutePath() + "): " + e.getMessage(), e);
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not read file (" + fileToUpload.getAbsolutePath() + "): " + e.getMessage(), e);
        }
    }

    private Range requestStatus(HttpRequestFactory requestFactory, GenericUrl uploadUri, File fileToUpload) throws IOException {
        HttpRequest httpRequest = createHttpRequest(requestFactory, HttpMethods.PUT, uploadUri, new EmptyContent());
        httpRequest.getHeaders().setContentRange("*/" + fileToUpload.length());
        LOGGER.log(Level.FINE, "Executing status request.");
        HttpResponse httpResponse = executeHttpRequest(httpRequest);
        if(!httpResponse.isSuccessStatusCode()) {
            throw new IOException("Status request was not successful. Status-Code: " + httpResponse.getStatusCode());
        }
        String range = httpResponse.getHeaders().getRange();
        return Range.valueOf(range);
    }

    private String requestUploadLocation(java.io.File fileToUpload, String mimeType, HttpRequestFactory requestFactory, com.google.api.services.drive.model.File remoteFile) throws IOException {
        GenericUrl initializationUrl = new GenericUrl("https://www.googleapis.com/upload/drive/v2/files");
        initializationUrl.put("uploadType", "resumable");
        HttpRequest httpRequest = createHttpRequest(requestFactory, HttpMethods.POST, initializationUrl, new JsonHttpContent(DriveFactory.getJsonFactory(), remoteFile));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put("X-Upload-Content-Type", mimeType);
        httpHeaders.put("X-Upload-Content-Length", fileToUpload.length());
        httpRequest.getHeaders().putAll(httpHeaders);
        LOGGER.log(Level.FINE, "Executing initial upload location request.");
        HttpResponse httpResponse = executeHttpRequest(httpRequest);
        if(!httpResponse.isSuccessStatusCode()) {
            throw new IOException("Request for upload location was not successful. Status-Code: " + httpResponse.getStatusCode());
        }
        String location = httpResponse.getHeaders().getLocation();
        LOGGER.log(Level.FINE, "URL for resumable upload: " + location);
        return location;
    }

    private HttpRequest createHttpRequest(HttpRequestFactory httpRequestFactory, String httpMethod, GenericUrl url, HttpContent httpContent) {
        try {
            return httpRequestFactory.buildRequest(httpMethod, url, httpContent);
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to build HTTP request: " + e.getMessage(), e);
        }
    }

    private HttpResponse executeHttpRequest(HttpRequest httpRequest) throws IOException {
        HttpResponse httpResponse = null;
        try {
            httpRequest.setThrowExceptionOnExecuteError(false);
            return httpResponse = httpRequest.execute();
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.disconnect();
                } catch (IOException e) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to disconnect HTTP request: " + e.getMessage(), e);
                }
            }
        }
    }
}
