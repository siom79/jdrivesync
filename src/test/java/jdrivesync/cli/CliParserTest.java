package jdrivesync.cli;

import jdrivesync.exception.JDriveSyncException;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class CliParserTest {

    @Test
    public void testLocalDirPresent() {
        CliParser cliParser = new CliParser();
        Options options = cliParser.parse(new String[]{"-l", System.getProperty("user.dir")});
        assertThat(options.getLocalRootDir().isPresent(), is(true));
        assertThat(options.getLocalRootDir().get().getAbsolutePath(), is(System.getProperty("user.dir")));
    }

    @Test
    public void testLocalDirMissing() {
        CliParser cliParser = new CliParser();
        boolean exceptionThrown = false;
        try {
            cliParser.parse(new String[]{"-l"});
        } catch (JDriveSyncException e) {
            exceptionThrown = true;
            assertThat(e.getReason(), is(JDriveSyncException.Reason.InvalidCliParameter));
        }
        assertThat(exceptionThrown, is(true));
    }

    @Test
    public void testLocalDirMissingButNewOptionInstead() {
        CliParser cliParser = new CliParser();
        boolean exceptionThrown = false;
        try {
            cliParser.parse(new String[]{"-l", "-r"});
        } catch (JDriveSyncException e) {
            exceptionThrown = true;
            assertThat(e.getReason(), is(JDriveSyncException.Reason.InvalidCliParameter));
        }
        assertThat(exceptionThrown, is(true));
    }

    @Test
    public void testInvalidParameter() {
        CliParser cliParser = new CliParser();
        boolean exceptionThrown = false;
        try {
            cliParser.parse(new String[]{"-&"});
        } catch (JDriveSyncException e) {
            exceptionThrown = true;
            assertThat(e.getReason(), is(JDriveSyncException.Reason.InvalidCliParameter));
        }
        assertThat(exceptionThrown, is(true));
    }
}