package xyz.duncanruns.jingle;

import org.apache.logging.log4j.Level;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Compile-time stub of Jingle's main class (matches the real signatures used by this plugin).
 * NOT included in the final plugin jar - Jingle provides the real classes at runtime.
 */
public final class Jingle {

    public static final Path FOLDER = Paths.get(System.getProperty("user.home")).resolve(".config").resolve("Jingle").toAbsolutePath();

    public static void log(Level level, String message) {
    }

    public static void logError(String failMessage, Throwable t) {
    }

    private Jingle() {
    }
}
