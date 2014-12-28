package jdrivesync.cli;

import jdrivesync.App;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class FileNamePatternsTest {

    @BeforeClass
    public static void beforeClass() {
        App.initLogging();
    }

    @Test
    public void testFileExtension() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("*.mp3", "*.avi", "*.mov"));
        assertThat(fileNamePatterns.matches("test.mp3", false), is(true));
        assertThat(fileNamePatterns.matches("test.mp4", false), is(false));
        assertThat(fileNamePatterns.matches("test.avi", false), is(true));
        assertThat(fileNamePatterns.matches("test.mov", false), is(true));
        assertThat(fileNamePatterns.matches("mov.txt", false), is(false));
    }

    @Test
    public void testCommentedLines() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("*.mp3", " #A comment", "\\#.txt"));
        assertThat(fileNamePatterns.matches("#.txt", false), is(true));
        assertThat(fileNamePatterns.matches("#.mp3", false), is(true));
    }

    @Test
    public void testBlankLine() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("*.mp3", "  ", "*.txt"));
        assertThat(fileNamePatterns.matches("#.txt", false), is(true));
    }

    @Test
    public void testPatternEndsWithSlash() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("folder/"));
        assertThat(fileNamePatterns.matches("folder", true), is(true));
        assertThat(fileNamePatterns.matches("folder", false), is(false));
    }

    @Test
    public void testPathPatternWithAsterisk() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("doc/*.html"));
        assertThat(fileNamePatterns.matches("doc/git.html", false), is(true));
        assertThat(fileNamePatterns.matches("doc/svn.html", false), is(true));
        assertThat(fileNamePatterns.matches("doc/folder/t1.html", false), is(false));
        assertThat(fileNamePatterns.matches("doc/folder/folder/t1.html", false), is(false));
        assertThat(fileNamePatterns.matches("t1.html", false), is(false));
    }

    @Test
    public void testPathPatternWithLeadingDoubleAsterisk() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("**/foo"));
        assertThat(fileNamePatterns.matches("doc/foo", false), is(true));
        assertThat(fileNamePatterns.matches("doc/html/foo", false), is(true));
        assertThat(fileNamePatterns.matches("foo", false), is(true));
    }

    @Test
    public void testPathPatternWithTrailingDoubleAsterisk() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("foo/**"));
        assertThat(fileNamePatterns.matches("foo/abc", false), is(true));
        assertThat(fileNamePatterns.matches("foo/abc", true), is(true));
        assertThat(fileNamePatterns.matches("foo/a.html", false), is(true));
        assertThat(fileNamePatterns.matches("foo/folder/abc.html", false), is(true));
        assertThat(fileNamePatterns.matches("fooo", false), is(false));
        assertThat(fileNamePatterns.matches("fo", false), is(false));
        assertThat(fileNamePatterns.matches("abc", false), is(false));
    }

    @Test
    public void testPathPatternWithIncludingDoubleAsterisk() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("foo/**/folder"));
        assertThat(fileNamePatterns.matches("foo/abc/folder", false), is(true));
        assertThat(fileNamePatterns.matches("foo/folder", false), is(true));
        assertThat(fileNamePatterns.matches("foo/x/y/folder", false), is(true));
        assertThat(fileNamePatterns.matches("foo/x/y/folde", false), is(false));
        assertThat(fileNamePatterns.matches("fo/folder", false), is(false));
    }

    @Test
    public void testPathPatternWithFileExtension() throws IOException {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("**/*.html"));
        assertThat(fileNamePatterns.matches("test.html", false), is(true));
        assertThat(fileNamePatterns.matches("foo/test.html", false), is(true));
        assertThat(fileNamePatterns.matches("foo/folder/xy.html", false), is(true));
        assertThat(fileNamePatterns.matches("test.mp3", false), is(false));
        assertThat(fileNamePatterns.matches("foo/test.mp3", false), is(false));
    }

    @Test
    public void testCompletePath() {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("doc/git.html"));
        assertThat(fileNamePatterns.matches("git.html", false), is(false));
        assertThat(fileNamePatterns.matches("doc/git.html", false), is(true));
        assertThat(fileNamePatterns.matches("doc/svn.html", false), is(false));
    }

    @Test
    public void testCatchAllPath() {
        FileNamePatterns fileNamePatterns = FileNamePatterns.create(Arrays.asList("**/*"));
        assertThat(fileNamePatterns.matches("git.html", false), is(true));
        assertThat(fileNamePatterns.matches("doc/git.html", false), is(true));
        assertThat(fileNamePatterns.matches("doc/svn.html", false), is(true));
        assertThat(fileNamePatterns.matches("doc/foo/xyz.txt", false), is(true));
        assertThat(fileNamePatterns.matches(".ssh", false), is(true));
    }
}