package com.speedrunigtcleaner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Core logic for cleaning the SpeedrunIGT records folder.
 * This class intentionally has NO dependency on Jingle, so it can be
 * unit-tested standalone and reused by the plugin.
 */
public final class RecordsCleaner {

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

    private RecordsCleaner() {
    }

    /** Filename pattern for SpeedrunIGT record files: <UUID>.json */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.json$");

    /**
     * Quick check: does the filename look like a SpeedrunIGT record?
     * (UUID.json pattern, e.g. 66451998-3b06-46dc-8546-23d418a98b6e.json)
     * Does NOT read file content — safe to call on many files.
     */
    public static boolean isRecordFileName(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        return UUID_PATTERN.matcher(name.toString()).matches();
    }

    /**
     * Thorough check: is this file actually a SpeedrunIGT record?
     * Verifies both the UUID filename pattern AND that the file content
     * contains the "final_igt" field (a SpeedrunIGT-specific JSON key).
     * If the file cannot be read, returns false (conservative — don't delete).
     */
    public static boolean isRecordFile(Path file) {
        if (!isRecordFileName(file)) {
            return false;
        }
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return content.contains("\"final_igt\"");
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Total size in bytes of all SpeedrunIGT record files under the given directory
     * (0 if it does not exist). Verifies both filename and content.
     */
    public static long getFolderSize(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        final long[] total = {0};
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .filter(RecordsCleaner::isRecordFile)
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
     * Number of SpeedrunIGT record files under the given directory
     * (0 if it does not exist). Verifies both filename and content.
     */
    public static int countFiles(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile)
                             .filter(RecordsCleaner::isRecordFile)
                             .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    /**
     * Deletes every file and sub-folder inside the given directory, but KEEPS the directory itself.
     * Symlinks are not followed, so nothing outside the folder can be affected.
     */
    public static CleanResult clean(Path dir) {
        return clean(dir, 0);
    }

    /**
     * Deletes every SpeedrunIGT record file inside the given directory, but KEEPS the
     * directory itself and any non-record files the user may have placed there.
     * If {@code keepRecent > 0}, the most recently modified {@code keepRecent} record
     * files are preserved. Empty sub-folders are removed; non-empty ones are left alone.
     */
    public static CleanResult clean(Path dir, int keepRecent) {
        if (dir == null || !Files.exists(dir)) {
            return new CleanResult(0, 0, 0, 0);
        }

        // Determine which record files to keep (most recently modified first).
        Set<Path> keepSet = new HashSet<>();
        if (keepRecent > 0) {
            List<Path> recordFiles = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(RecordsCleaner::isRecordFile)
                    .forEach(recordFiles::add);
            } catch (IOException ignored) {
            }
            recordFiles.sort((a, b) -> {
                try {
                    FileTime ta = Files.getLastModifiedTime(a);
                    FileTime tb = Files.getLastModifiedTime(b);
                    return tb.compareTo(ta); // newest first
                } catch (IOException e) {
                    return 0;
                }
            });
            int limit = Math.min(keepRecent, recordFiles.size());
            for (int i = 0; i < limit; i++) {
                keepSet.add(recordFiles.get(i));
            }
        }

        int deleted = 0;
        int failures = 0;
        long freed = 0;
        int skipped = 0;

        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(paths::add);
        } catch (IOException ignored) {
        }

        for (Path p : paths) {
            if (p.equals(dir)) {
                continue; // keep the root folder
            }
            if (Files.isDirectory(p)) {
                // Try to delete empty sub-folders; silently leave non-empty ones.
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
                continue;
            }
            // Regular file: only delete if it's a verified SpeedrunIGT record.
            if (!isRecordFile(p)) {
                skipped++;
                continue;
            }
            if (keepSet.contains(p)) {
                continue; // preserve this record file
            }
            try {
                long size = Files.size(p);
                Files.delete(p);
                freed += size;
                deleted++;
            } catch (IOException ignored) {
                failures++;
            }
        }
        return new CleanResult(deleted, freed, failures, skipped);
    }
}
