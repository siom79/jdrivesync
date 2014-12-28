package jdrivesync.report;

import jdrivesync.exception.JDriveSyncException;

import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

public class HtmlReport implements Report {
    private Date reportDate = new Date();
    private File file = null;
    private RandomAccessFile randomAccessFile = null;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss");

    @Override
    public void log(ReportEntry reportEntry) {
        if (file == null) {
            createFile();
            writeHeader();
        }
        logReportEntry(reportEntry);
    }

    private void writeHeader() {
        String headerPath = "/report/header.tpl";
        InputStream resourceAsStream = HtmlReport.class.getResourceAsStream(headerPath);
        if(resourceAsStream == null) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Could not load HTML header '" + headerPath + "' from classpath.");
        }
        String header = streamToString(resourceAsStream);
        try {
            String headPlusFooter = header + createFooter();
            randomAccessFile.write(headPlusFooter.getBytes("UTF-8"));
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to write header to file '" + file.getAbsolutePath() + "'.", e);
        }
    }

    private String streamToString(InputStream is) {
        try {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[1024];
            InputStreamReader isr = new InputStreamReader(is, "UTF-8");
            int read = isr.read(buffer, 0, buffer.length);
            while(read >= 0) {
                out.append(buffer, 0, read);
                read = isr.read(buffer, 0, buffer.length);
            }
            return out.toString();
        } catch (UnsupportedEncodingException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Unsupported encoding: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to convert input stream to String: " + e.getMessage(), e);
        }
    }

    private void logReportEntry(ReportEntry reportEntry) {
        String footer = createFooter();
        int footerLength = footer.getBytes(Charset.forName("UTF-8")).length;
        try {
            randomAccessFile.seek(randomAccessFile.length() - footerLength);
            String entryPlusFooter = reportEntryToString(reportEntry) + "\n" + footer;
            randomAccessFile.write(entryPlusFooter.getBytes("UTF-8"));
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to write entry to file '" + file.getAbsolutePath() + "'.", e);
        }
    }

    private String reportEntryToString(ReportEntry reportEntry) {
        StringBuilder sb = new StringBuilder();
        sb.append("<tr>\n");
        sb.append("<td>" + encodeHTML(reportEntry.getRelativePath()) + "</td>\n");
        sb.append("<td>" + encodeHTML(statusEntry(reportEntry)) + "</td>\n");
        sb.append("<td>" + reportEntry.getAction() + "</td>\n");
        sb.append("</tr>\n");
        return sb.toString();
    }

    private String statusEntry(ReportEntry reportEntry) {
        StringBuilder sb = new StringBuilder();
        if(reportEntry.getStatus() == ReportEntry.Status.Error) {
            sb.append(reportEntry.getStatus());
            sb.append("(");
            if(reportEntry.getErrorMessage().isPresent()) {
                sb.append(reportEntry.getErrorMessage().get());
            } else {
                sb.append("n.a.");
            }
            sb.append(")");
        } else {
            sb.append(reportEntry.getStatus());
        }
        return sb.toString();
    }

    private String createFooter() {
        StringBuilder sb = new StringBuilder();
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private void createFile() {
        String filename = "jdrivesyncReport_" + sdf.format(reportDate) + ".html";
        this.file = new File(filename);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                throw new JDriveSyncException(JDriveSyncException.Reason.IllegalStateException, "Unable to delete file '" + file.getAbsolutePath() + "'.");
            }
        }
        try {
            this.randomAccessFile = new RandomAccessFile(file, "rws");
        } catch (FileNotFoundException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to create file '" + file.getAbsolutePath() + "'.", e);
        }
    }

    private String encodeHTML(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>') {
                out.append("&#" + (int) c + ";");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
