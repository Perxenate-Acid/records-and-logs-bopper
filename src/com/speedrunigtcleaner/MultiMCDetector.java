package com.speedrunigtcleaner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Detects MultiMC / Prism Launcher installations and enumerates instances
 * with .minecraft/logs folders.
 *
 * This class has NO dependency on Jingle, so it can be unit-tested standalone.
 */
public final class MultiMCDetector {

    /** Common exe names for MultiMC and Prism Launcher. */
    private static final String[] EXE_NAMES = {
            "MultiMC.exe", "multimc.exe",
            "prismlauncher.exe", "PrismLauncher.exe"
    };

    /** Information about a detected instance. */
    public static final class InstanceInfo {
        public final String name;
        public final Path logsDir;
        public final long logSize;
        public final int logCount;

        public InstanceInfo(String name, Path logsDir, long logSize, int logCount) {
            this.name = name;
            this.logsDir = logsDir;
            this.logSize = logSize;
            this.logCount = logCount;
        }
    }

    private MultiMCDetector() {
    }

    /**
     * Attempts to auto-detect the MultiMC / Prism Launcher installation directory.
     * Strategy:
     *   1. Check common installation paths for the exe or an "instances" folder.
     *   2. Query running processes via wmic (Windows) for the exe path.
     *   3. Return null if not found.
     *
     * @return Path to the installation directory, or null if not detected.
     */
    public static Path autoDetect() {
        // 1. Check common paths
        String home = System.getProperty("user.home");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) {
            localAppData = home + "\\AppData\\Local";
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) {
            programFiles = "C:\\Program Files";
        }
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 == null) {
            programFilesX86 = "C:\\Program Files (x86)";
        }

        List<String> searchDirs = new ArrayList<>();
        searchDirs.add("C:\\MultiMC");
        searchDirs.add("C:\\Games\\MultiMC");
        searchDirs.add(programFiles + "\\MultiMC");
        searchDirs.add(programFilesX86 + "\\MultiMC");
        searchDirs.add(home + "\\Desktop\\MultiMC");
        searchDirs.add(home + "\\Downloads\\MultiMC");
        searchDirs.add(home + "\\Documents\\MultiMC");
        searchDirs.add(home + "\\Desktop");
        searchDirs.add(home + "\\Downloads");
        searchDirs.add(localAppData + "\\Programs\\Prism Launcher");
        searchDirs.add(localAppData + "\\PrismLauncher");
        searchDirs.add(programFiles + "\\Prism Launcher");
        searchDirs.add(programFilesX86 + "\\Prism Launcher");
        searchDirs.add("C:\\Games\\Prism Launcher");

        for (String dir : searchDirs) {
            Path dirPath = Paths.get(dir);
            if (!Files.isDirectory(dirPath)) {
                continue;
            }
            // Check for exe in this directory
            for (String exe : EXE_NAMES) {
                if (Files.isRegularFile(dirPath.resolve(exe))) {
                    return dirPath;
                }
            }
            // Check for instances folder (MultiMC may be here without exe visible)
            if (Files.isDirectory(dirPath.resolve("instances"))) {
                return dirPath;
            }
        }

        // 2. Try wmic for running processes
        Path wmicResult = detectViaWmic();
        if (wmicResult != null) {
            return wmicResult;
        }

        return null;
    }

    /** Uses wmic to find a running MultiMC / Prism process and returns its directory. */
    private static Path detectViaWmic() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "cmd", "/c", "wmic", "process", "get", "ExecutablePath"
            });
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String lower = line.toLowerCase();
                    if ((lower.contains("multimc") || lower.contains("prism")) && lower.endsWith(".exe")) {
                        Path exePath = Paths.get(line);
                        if (Files.isRegularFile(exePath)) {
                            return exePath.getParent();
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException ignored) {
        }
        return null;
    }

    /**
     * Checks if a directory is a valid MultiMC / Prism Launcher installation
     * (must contain an "instances" folder).
     */
    public static boolean isValidMultiMCDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return Files.isDirectory(dir.resolve("instances"));
    }

    /**
     * Lists all instances that have a .minecraft/logs (or minecraft/logs) folder.
     *
     * @param multimcDir the MultiMC / Prism installation directory
     * @return list of InstanceInfo, sorted by name; empty if none found or dir invalid
     */
    public static List<InstanceInfo> getInstances(Path multimcDir) {
        List<InstanceInfo> instances = new ArrayList<>();
        if (multimcDir == null) {
            return instances;
        }
        Path instancesDir = multimcDir.resolve("instances");
        if (!Files.isDirectory(instancesDir)) {
            return instances;
        }

        try (Stream<Path> dirs = Files.list(instancesDir)) {
            dirs.filter(Files::isDirectory).forEach(instanceDir -> {
                Path logsDir = findLogsDir(instanceDir);
                if (logsDir != null) {
                    String name = instanceDir.getFileName().toString();
                    long size = LogsCleaner.getLogsSize(logsDir);
                    int count = LogsCleaner.countLogFiles(logsDir);
                    instances.add(new InstanceInfo(name, logsDir, size, count));
                }
            });
        } catch (IOException ignored) {
        }

        instances.sort(Comparator.comparing(i -> i.name));
        return instances;
    }

    /**
     * Finds the logs directory in an instance folder.
     * Checks .minecraft/logs first, then minecraft/logs.
     *
     * @return the logs directory Path, or null if not found
     */
    private static Path findLogsDir(Path instanceDir) {
        Path logsDir = instanceDir.resolve(".minecraft").resolve("logs");
        if (Files.isDirectory(logsDir)) {
            return logsDir;
        }
        logsDir = instanceDir.resolve("minecraft").resolve("logs");
        if (Files.isDirectory(logsDir)) {
            return logsDir;
        }
        return null;
    }
}
