package com.speedrunigtcleaner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Core logic for cleaning Minecraft log files in .minecraft/logs.
 * This class has NO dependency on Jingle, so it can be unit-tested standalone.
 *
 * Minecraft-generated log files (log4j rolling appenders):
 *  - latest.log              → active log, NEVER deleted
 *  - debug.log               → active debug log, NEVER deleted
 *  - yyyy-MM-dd-N.log.gz     → rolled main log archives (e.g. 2026-08-21-1.log.gz)
 *  - debug-N.log(.gz)        → rolled debug log archives (e.g. debug-1.log.gz)
 *
 * Only files matching the Minecraft naming pattern above are ever deleted.
 * Anything else — including user-placed files that merely LOOK like logs
 * (e.g. "notes.log", "backup.log.gz") — is skipped, and directories are
 * never touched.
 */
public final class LogsCleaner {

    /** Filenames that are "active" logs and must never be deleted. */
    private static final Set<String> ACTIVE_LOGS = new HashSet<>(Arrays.asList(
            "latest.log", "debug.log"
    ));

    /**
     * Matches Minecraft-generated rolling log files:
     *  - yyyy-MM-dd-N.log / yyyy-MM-dd-N.log.gz   (main log archives)
     *  - debug-N.log / debug-N.log.gz             (debug log archives)
     *  - debug-yyyy-MM-dd-N.log(.gz)              (some versions' debug archives)
     * Deliberately does NOT match user-placed files like "notes.log" or "backup.log.gz".
     */
    private static final Pattern MC_LOG_PATTERN = Pattern.compile(
            "^(?:\\d{4}-\\d{2}-\\d{2}-\\d+|debug-\\d+|debug-\\d{4}-\\d{2}-\\d{2}-\\d+)\\.log(?:\\.gz)?$",
            Pattern.CASE_INSENSITIVE);

    /** Result of a cleaning operation. */
    public static final class CleanResult {
        public final int filesDeleted;
        public final long bytesFreed;
        public final int failures;
        public final int filesSkipped;

        public CleanResult(int filesDeleted, long bytesFreed, int failures, int filesSkipped) {
            this.filesDeleted = filesDeleted;
            this.bytesFreed = bytesFreed;
            this.failures = failures;
            this.filesSkipped = filesSkipped;
        }
    }

    private LogsCleaner() {
    }

    /** Returns true if the file is an active log (latest.log or debug.log) — must NEVER be deleted. */
    public static boolean isActiveLog(Path file) {
        if (file == null) {
            return false;
        }
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        return ACTIVE_LOGS.contains(name.toString().toLowerCase());
    }

    /** Returns true if the file is a log archive (*.log.gz) — safe to delete. */
    public static boolean isLogArchive(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        return name.toString().toLowerCase().endsWith(".log.gz");
    }

    /**
     * Returns true if the file matches Minecraft's rolling-log naming pattern
     * (yyyy-MM-dd-N.log(.gz), debug-N.log(.gz), debug-yyyy-MM-dd-N.log(.gz)).
     * User-placed files that merely end in .log/.log.gz do NOT match.
     */
    public static boolean isMinecraftLogFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        return MC_LOG_PATTERN.matcher(name.toString()).matches();
    }

    /**
     * Returns true if the file is a deletable log file — i.e. a Minecraft-generated
     * rolling log that is NOT an active log. User-placed files (even ones named
     * like "notes.log" or "backup.log.gz") are NOT deletable.
     */
    public static boolean isDeletableLog(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        if (isActiveLog(file)) {
            return false;
        }
        return isMinecraftLogFile(file);
    }

    /**
     * Total size in bytes of all deletable log files under the given directory
     * (0 if it does not exist). Does NOT count active logs (latest.log, debug.log).
     */
    public static long getLogsSize(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        final long[] total = {0};
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .filter(LogsCleaner::isDeletableLog)
                .forEach(p -> {
                    try {
                        total[0] += Files.size(p);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
        return total[0];
    }

    /**
     * Number of deletable log files under the given directory
     * (0 if it does not exist). Does NOT count active logs.
     */
    public static int countLogFiles(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile)
                             .filter(LogsCleaner::isDeletableLog)
                             .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    /**
     * Deletes deletable log files inside the given directory.
     * Active logs (latest.log, debug.log) are NEVER deleted.
     * Only files matching Minecraft's rolling-log naming pattern are deleted;
     * user-placed files and directories are never touched (non-matching files
     * are counted in filesSkipped).
     * If {@code keepRecent > 0}, the most recently modified {@code keepRecent}
     * deletable log files are preserved.
     *
     * @param dir        the logs directory (e.g. .minecraft/logs)
     * @param keepRecent number of newest log files to keep (0 = delete all deletable logs)
     * @return CleanResult with deletion statistics
     */
    public static CleanResult cleanLogs(Path dir, int keepRecent) {
        if (dir == null || !Files.exists(dir)) {
            return new CleanResult(0, 0, 0, 0);
        }

        // Determine which log files to keep (most recently modified first).
        Set<Path> keepSet = new HashSet<>();
        if (keepRecent > 0) {
            List<Path> logFiles = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(LogsCleaner::isDeletableLog)
                    .forEach(logFiles::add);
            } catch (IOException ignored) {
            }
            logFiles.sort((a, b) -> {
                try {
                    FileTime ta = Files.getLastModifiedTime(a);
                    FileTime tb = Files.getLastModifiedTime(b);
                    return tb.compareTo(ta); // newest first
                } catch (IOException e) {
                    return 0;
                }
            });
            int limit = Math.min(keepRecent, logFiles.size());
            for (int i = 0; i < limit; i++) {
                keepSet.add(logFiles.get(i));
            }
        }

        int deleted = 0;
        int failures = 0;
        long freed = 0;
        int skipped = 0;

        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.forEach(paths::add);
        } catch (IOException ignored) {
        }

        for (Path p : paths) {
            if (p.equals(dir)) {
                continue; // keep the root folder
            }
            if (Files.isDirectory(p)) {
                continue; // never touch directories (user may have placed them)
            }
            // Regular file: only delete if it's a Minecraft-generated rolling log.
            if (!isDeletableLog(p)) {
                // Active logs (latest.log, debug.log) and user-placed files are skipped.
                if (!isActiveLog(p)) {
                    skipped++;
                }
                continue;
            }
            if (keepSet.contains(p)) {
                continue; // preserve this log file
            }
            try {
                long size = Files.size(p);
                Files.delete(p);
                freed += size;
                deleted++;
            } catch (IOException ignored) {
                // File is locked or in use (e.g. game is running and writing to it).
                failures++;
            }
        }
        return new CleanResult(deleted, freed, failures, skipped);
    }

    /** Convenience: delete ALL deletable log files (keepRecent = 0). */
    public static CleanResult cleanAllLogs(Path dir) {
        return cleanLogs(dir, 0);
    }
}
