package org.apache.logging.log4j;

/**
 * Compile-time stub of log4j Level (Jingle ships the real log4j-api at runtime).
 * NOT included in the final plugin jar.
 */
public final class Level {

    public static final Level ALL = new Level();
    public static final Level DEBUG = new Level();
    public static final Level INFO = new Level();
    public static final Level WARN = new Level();
    public static final Level ERROR = new Level();
    public static final Level FATAL = new Level();
    public static final Level OFF = new Level();

    private Level() {
    }
}
