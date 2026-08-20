package com.speedrunigtcleaner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

        public CleanResult(int filesDeleted, long bytesFreed, int failures) {
            this.filesDeleted = filesDeleted;
            this.bytesFreed = bytesFreed;
            this.failures = failures;
        }
    }

    private RecordsCleaner() {
    }

    /** Total size in bytes of all regular files under the given directory (0 if it does not exist). */
    public static long getFolderSize(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        final long[] total = {0};
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    total[0] += Files.size(p);
                } catch (IOException ignored) {
                    // File may be locked/in use; skip it.
                }
            });
        } catch (IOException ignored) {
            // Directory vanished mid-walk; return what we have.
        }
        return total[0];
    }

    /** Number of regular files under the given directory (0 if it does not exist). */
    public static int countFiles(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile).count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    /**
     * Deletes every file and sub-folder inside the given directory, but KEEPS the directory itself.
     * Symlinks are not followed, so nothing outside the folder can be affected.
     */
    public static CleanResult clean(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return new CleanResult(0, 0, 0);
        }
        int deleted = 0;
        int failures = 0;
        long freed = 0;

        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(paths::add);
        } catch (IOException ignored) {
            // Directory vanished mid-walk.
        }

        for (Path p : paths) {
            if (p.equals(dir)) {
                continue; // keep the root folder
            }
            try {
                if (Files.isDirectory(p)) {
                    Files.delete(p);
                } else {
                    long size = Files.size(p);
                    Files.delete(p);
                    freed += size;
                    deleted++;
                }
            } catch (IOException ignored) {
                // File is locked or in use; count it and continue with the rest.
                failures++;
            }
        }
        return new CleanResult(deleted, freed, failures);
    }
}
