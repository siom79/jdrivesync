package jdrivesync.cli;

import jdrivesync.exception.JDriveSyncException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileNamePatterns {
    private static final Logger LOGGER = Logger.getLogger(FileNamePatterns.class.getName());
    private List<FileNamePattern> fileNamePatterns;

    private enum PatternType {
        Path, Filename, Foldername
    }

    private static class FileNamePattern {
        private final PatternType patternType;
        private final Pattern pattern;

        private FileNamePattern(PatternType patternType, Pattern pattern) {
            this.patternType = patternType;
            this.pattern = pattern;
        }

        public static FileNamePattern create(String pattern) {
            if (pattern.contains("**")) {
                if (count(pattern, "**") > 1) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern, because it contains the double asterisks more than once: '" + pattern + "'.");
                }
                if (pattern.startsWith("**/")) {
                    if(pattern.length() == 3) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern, because it contains only the double asterisks: '" + pattern + "'.");
                    }
                    Pattern regPattern;
                    try {
                        regPattern = Pattern.compile(escapeForRegEx(pattern.substring(3)));
                    } catch (Exception e) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "':" + e.getMessage(), e);
                    }
                    return new FileNamePattern(PatternType.Filename, regPattern);
                } else if (pattern.endsWith("/**")) {
                    if(pattern.length() == 3) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern, because it contains only the double asterisks: '" + pattern + "'.");
                    }
                    Pattern regPattern;
                    try {
                        regPattern = Pattern.compile(escapeForRegEx(pattern.substring(0, pattern.length() - 2)) + ".*");
                    } catch (Exception e) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "':" + e.getMessage(), e);
                    }
                    return new FileNamePattern(PatternType.Path, regPattern);
                } else if (pattern.contains("/**/")) {
                    if(pattern.length() == 4) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern, because it contains only the double asterisks: '" + pattern + "'.");
                    }
                    int indexOf = pattern.indexOf("/**/");
                    Pattern regPattern;
                    try {
                        regPattern = Pattern.compile(escapeForRegEx(pattern.substring(0, indexOf)) + ".*" + escapeForRegEx(pattern.substring(indexOf + 3, pattern.length())));
                    } catch (Exception e) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "':" + e.getMessage(), e);
                    }
                    return new FileNamePattern(PatternType.Path, regPattern);
                } else {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "', because it does not start with **/ or end with **/ or contain /**/.");
                }
            } else if (pattern.contains("/")) {
                if (pattern.endsWith("/")) {
                    Pattern regPattern;
                    try {
                        if(pattern.length() > 1) {
                            pattern = pattern.substring(0, pattern.length()-1);
                        }
                        regPattern = Pattern.compile(escapeForRegEx(pattern));
                    } catch (Exception e) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "':" + e.getMessage(), e);
                    }
                    return new FileNamePattern(PatternType.Foldername, regPattern);
                } else {
                    Pattern regPattern;
                    try {
                        regPattern = Pattern.compile(escapeForRegEx(pattern));
                    } catch (Exception e) {
                        throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "':" + e.getMessage(), e);
                    }
                    return new FileNamePattern(PatternType.Path, regPattern);
                }
            } else {
                Pattern regPattern;
                try {
                    regPattern = Pattern.compile(escapeForRegEx(pattern));
                } catch (Exception e) {
                    throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Invalid ignore pattern '" + pattern + "':" + e.getMessage(), e);
                }
                return new FileNamePattern(PatternType.Filename, regPattern);
            }
        }

        private static int count(String pattern, String part) {
            int count = 0;
            int indexOf = pattern.indexOf(part);
            while (indexOf != -1) {
                count++;
                int newStartIndex = indexOf + part.length();
                if (newStartIndex < pattern.length()) {
                    indexOf = pattern.indexOf(part, newStartIndex);
                } else {
                    indexOf = -1;
                }
            }
            return count;
        }

        private static String escapeForRegEx(String pattern) {
            pattern = pattern.toLowerCase();
            pattern = pattern.replace(".", "\\.");
            pattern = pattern.replace("&", "\\&");
            pattern = pattern.replace("+", "\\+");
            pattern = pattern.replace("?", "\\?");
            pattern = pattern.replace("{", "\\{");
            pattern = pattern.replace("}", "\\}");
            pattern = pattern.replace("[", "\\[");
            pattern = pattern.replace("]", "\\]");
            pattern = pattern.replace("*", "[^/]*");
            return pattern;
        }

        public boolean matches(String path, boolean isDirectory) {
            if (patternType == PatternType.Path) {
                Matcher matcher = pattern.matcher(path);
                return matcher.matches();
            } else if (patternType == PatternType.Filename) {
                Matcher matcher = pattern.matcher(getFilename(path));
                return matcher.matches();
            } else if (patternType == PatternType.Foldername) {
                if(isDirectory) {
                    Matcher matcher = pattern.matcher(path);
                    return matcher.matches();
                }
            }
            return false;
        }

        private String getFilename(String path) {
            int lastIndexOf = path.lastIndexOf("/");
            if (lastIndexOf != -1 && lastIndexOf + 1 < path.length()) {
                return path.substring(lastIndexOf + 1, path.length());
            }
            return path;
        }
    }

    private FileNamePatterns(List<FileNamePattern> fileNamePatterns) {
        this.fileNamePatterns = fileNamePatterns;
    }

    public static FileNamePatterns create(List<String> lines) {
        List<FileNamePattern> fileNamePatternList = new ArrayList<>(lines.size());
        int lineCount = 0;
        for (String line : lines) {
            lineCount++;
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("#")) {
                LOGGER.log(Level.FINE, "Skipping commented line " + lineCount + ".");
                continue;
            }
            line = line.replace("\\#", "#");
            FileNamePattern fileNamePattern = FileNamePattern.create(line);
            fileNamePatternList.add(fileNamePattern);
        }
        return new FileNamePatterns(fileNamePatternList);
    }

    public boolean matches(String path, boolean isDirectory) {
        path = path.toLowerCase();
        if (path.startsWith("/")) {
            if (path.length() > 1) {
                path = path.substring(1);
            } else {
                return true;
            }
        }
        for (FileNamePattern fileNamePattern : fileNamePatterns) {
            if(fileNamePattern.matches(path, isDirectory)) {
                return true;
            }
        }
        return false;
    }
}
