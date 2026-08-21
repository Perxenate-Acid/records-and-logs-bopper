import com.speedrunigtcleaner.RecordsCleaner;
import com.speedrunigtcleaner.LogsCleaner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Standalone test for RecordsCleaner and LogsCleaner (no Jingle dependency).
 */
public class TestMain {

    public static void main(String[] args) throws IOException {
        testRecordsCleaner();
        testLogsCleaner();
        System.out.println("\nALL TESTS PASSED");
    }

    // ==================================================================
    // RecordsCleaner tests
    // ==================================================================

    private static void testRecordsCleaner() throws IOException {
        System.out.println("=== RecordsCleaner tests ===");
        Path temp = Files.createTempDirectory("records-cleaner-test");
        Path records = temp.resolve("records");
        Files.createDirectories(records);

        // --- Create record files (UUID-named, with "final_igt" in content) ---
        Path recA = writeRecord(records, 1000);
        Path recB = writeRecord(records, 2000);
        Path sub = records.resolve("sub");
        Files.createDirectories(sub);
        Path recC = writeRecord(sub, 3000);

        // --- Create non-record files (should NOT be deleted) ---
        Path userTxt = records.resolve("my-notes.txt");
        Files.write(userTxt, new byte[999]);
        Path userJson = records.resolve("manual.json");
        Files.write(userJson, "{\"hello\":1}".getBytes(StandardCharsets.UTF_8));
        Path fakeRecord = records.resolve(UUID.randomUUID() + ".json");
        Files.write(fakeRecord, "{\"not_a_record\":true}".getBytes(StandardCharsets.UTF_8));

        Path outsideFile = temp.resolve("keep.txt");
        Files.write(outsideFile, new byte[999]);

        // --- Test isRecordFileName / isRecordFile ---
        System.out.println("[test] --- isRecordFile tests ---");
        check(RecordsCleaner.isRecordFileName(recA), "record file should match name pattern");
        check(RecordsCleaner.isRecordFile(recA), "record file should pass full check");
        check(!RecordsCleaner.isRecordFileName(userTxt), "txt file should not match name pattern");
        check(!RecordsCleaner.isRecordFileName(userJson), "manual.json should not match name pattern");
        check(RecordsCleaner.isRecordFileName(fakeRecord), "fake record should match name pattern (UUID)");
        check(!RecordsCleaner.isRecordFile(fakeRecord), "fake record should fail content check");
        check(!RecordsCleaner.isRecordFile(temp.resolve("nope")), "non-existent file should be false");

        // --- Test size/count ---
        System.out.println("[test] --- size/count tests ---");
        long size = RecordsCleaner.getFolderSize(records);
        int count = RecordsCleaner.countFiles(records);
        System.out.println("[test] size=" + size + " count=" + count);
        check(size == 6000, "size should be 6000 (only record files)");
        check(count == 3, "count should be 3 (only record files, not user files)");

        // --- Test clean (no keep) ---
        System.out.println("[test] --- clean test ---");
        RecordsCleaner.CleanResult result = RecordsCleaner.clean(records);
        System.out.println("[test] deleted=" + result.filesDeleted + " freed=" + result.bytesFreed
                + " failures=" + result.failures + " skipped=" + result.filesSkipped);
        check(result.filesDeleted == 3, "should delete 3 record files");
        check(result.bytesFreed == 6000, "should free 6000 bytes");
        check(result.failures == 0, "should have 0 failures");
        check(result.filesSkipped == 3, "should skip 3 non-record files (txt, manual.json, fake)");
        check(Files.exists(records), "records dir must be KEPT");
        check(!Files.exists(sub), "empty subdir should be deleted");
        check(Files.exists(userTxt), "user txt file must NOT be deleted");
        check(Files.exists(userJson), "user json file must NOT be deleted");
        check(Files.exists(fakeRecord), "fake record (wrong content) must NOT be deleted");
        check(Files.exists(outsideFile), "file outside folder must be untouched");
        check(RecordsCleaner.countFiles(records) == 0, "should have 0 record files after clean");

        // --- Test missing folder ---
        RecordsCleaner.CleanResult r2 = RecordsCleaner.clean(temp.resolve("nope"));
        check(r2.filesDeleted == 0, "missing folder should be a no-op");

        // --- keepRecent tests ---
        System.out.println("[test] --- keepRecent tests ---");
        Path records2 = temp.resolve("records2");
        Files.createDirectories(records2);
        Path[] files = new Path[5];
        for (int i = 0; i < 5; i++) {
            files[i] = writeRecord(records2, 100 * (i + 1));
            Files.setLastModifiedTime(files[i], FileTime.from(1000L + i, TimeUnit.SECONDS));
        }
        Path userFile2 = records2.resolve("user.txt");
        Files.write(userFile2, new byte[500]);

        RecordsCleaner.CleanResult r3 = RecordsCleaner.clean(records2, 2);
        System.out.println("[test] keepRecent=2: deleted=" + r3.filesDeleted + " freed=" + r3.bytesFreed
                + " skipped=" + r3.filesSkipped);
        check(r3.filesDeleted == 3, "keepRecent=2 should delete 3 record files");
        check(r3.bytesFreed == 100 + 200 + 300, "should free 600 bytes (file0+file1+file2)");
        check(r3.filesSkipped == 1, "should skip 1 non-record file (user.txt)");
        check(Files.exists(files[3]), "file3 (4th newest) should be KEPT");
        check(Files.exists(files[4]), "file4 (newest) should be KEPT");
        check(!Files.exists(files[0]), "file0 (oldest) should be deleted");
        check(!Files.exists(files[1]), "file1 should be deleted");
        check(!Files.exists(files[2]), "file2 should be deleted");
        check(Files.exists(userFile2), "non-record file should NOT be deleted");
        check(RecordsCleaner.countFiles(records2) == 2, "should have 2 record files remaining");

        RecordsCleaner.CleanResult r4 = RecordsCleaner.clean(records2, 10);
        check(r4.filesDeleted == 0, "keepRecent=10 on 2 files should delete 0");
        check(RecordsCleaner.countFiles(records2) == 2, "both files should still exist");

        RecordsCleaner.CleanResult r5 = RecordsCleaner.clean(records2, 0);
        check(r5.filesDeleted == 2, "keepRecent=0 should delete all 2 record files");
        check(Files.exists(userFile2), "non-record file should still exist after keepRecent=0 clean");
        check(RecordsCleaner.countFiles(records2) == 0, "should have 0 record files");

        RecordsCleaner.CleanResult r6 = RecordsCleaner.clean(temp.resolve("nope2"), 5);
        check(r6.filesDeleted == 0, "keepRecent on missing folder should be a no-op");

        // Cleanup
        Files.deleteIfExists(userTxt);
        Files.deleteIfExists(userJson);
        Files.deleteIfExists(fakeRecord);
        Files.deleteIfExists(records);
        Files.deleteIfExists(userFile2);
        Files.deleteIfExists(records2);
        Files.deleteIfExists(outsideFile);
        Files.deleteIfExists(temp);
        System.out.println("[test] RecordsCleaner tests passed.\n");
    }

    // ==================================================================
    // LogsCleaner tests
    // ==================================================================

    private static void testLogsCleaner() throws IOException {
        System.out.println("=== LogsCleaner tests ===");
        Path temp = Files.createTempDirectory("logs-cleaner-test");
        Path logsDir = temp.resolve("logs");
        Files.createDirectories(logsDir);

        // --- Create active logs (should NEVER be deleted) ---
        Path latestLog = logsDir.resolve("latest.log");
        Files.write(latestLog, "current log content".getBytes(StandardCharsets.UTF_8));
        Path debugLog = logsDir.resolve("debug.log");
        Files.write(debugLog, "current debug content".getBytes(StandardCharsets.UTF_8));

        // --- Create log archives (*.log.gz, should be deleted) ---
        Path[] archives = new Path[6];
        for (int i = 0; i < 6; i++) {
            archives[i] = logsDir.resolve("2024-01-" + String.format("%02d", i + 1) + "-1.log.gz");
            Files.write(archives[i], ("compressed log " + i).getBytes(StandardCharsets.UTF_8));
            // Set distinct modification times: archives[0] oldest, archives[5] newest
            Files.setLastModifiedTime(archives[i], FileTime.from(1000L + i, TimeUnit.SECONDS));
        }

        // --- Create debug log archives ---
        Path debugArchive = logsDir.resolve("debug-2024-01-01-1.log.gz");
        Files.write(debugArchive, "compressed debug log".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(debugArchive, FileTime.from(500L, TimeUnit.SECONDS)); // oldest

        // --- Create non-log files (should be skipped) ---
        Path lockFile = logsDir.resolve("latest.log.lck");
        Files.write(lockFile, new byte[0]);
        Path readmeFile = logsDir.resolve("README.txt");
        Files.write(readmeFile, "not a log".getBytes(StandardCharsets.UTF_8));

        // --- Create user-placed files that LOOK like logs (must be skipped) ---
        Path userLog = logsDir.resolve("mynotes.log");
        Files.write(userLog, "user's own log".getBytes(StandardCharsets.UTF_8));
        Path userGz = logsDir.resolve("backup.log.gz");
        Files.write(userGz, "user's backup".getBytes(StandardCharsets.UTF_8));
        // User-placed folder must survive cleanup too
        Path userDir = logsDir.resolve("user-data");
        Files.createDirectories(userDir);
        Path userFileInDir = userDir.resolve("keepme.txt");
        Files.write(userFileInDir, "keep".getBytes(StandardCharsets.UTF_8));

        // --- Test isActiveLog / isLogArchive / isDeletableLog ---
        System.out.println("[test] --- file type tests ---");
        check(LogsCleaner.isActiveLog(latestLog), "latest.log should be active");
        check(LogsCleaner.isActiveLog(debugLog), "debug.log should be active");
        check(!LogsCleaner.isActiveLog(archives[0]), "archive should not be active");
        check(LogsCleaner.isLogArchive(archives[0]), "archive should be log archive");
        check(LogsCleaner.isLogArchive(debugArchive), "debug archive should be log archive");
        check(!LogsCleaner.isLogArchive(latestLog), "latest.log should not be log archive");
        check(LogsCleaner.isDeletableLog(archives[0]), "archive should be deletable");
        check(LogsCleaner.isDeletableLog(debugArchive), "debug archive should be deletable");
        check(!LogsCleaner.isDeletableLog(latestLog), "latest.log should NOT be deletable");
        check(!LogsCleaner.isDeletableLog(debugLog), "debug.log should NOT be deletable");
        check(!LogsCleaner.isDeletableLog(lockFile), ".lck file should NOT be deletable");
        check(!LogsCleaner.isDeletableLog(readmeFile), "README.txt should NOT be deletable");
        check(!LogsCleaner.isDeletableLog(userLog), "user .log file should NOT be deletable");
        check(!LogsCleaner.isDeletableLog(userGz), "user .log.gz file should NOT be deletable");
        check(!LogsCleaner.isDeletableLog(temp.resolve("nope")), "non-existent should not be deletable");

        // --- Test size/count ---
        System.out.println("[test] --- size/count tests ---");
        long logSize = LogsCleaner.getLogsSize(logsDir);
        int logCount = LogsCleaner.countLogFiles(logsDir);
        System.out.println("[test] logSize=" + logSize + " logCount=" + logCount);
        check(logCount == 7, "should count 7 deletable log files (6 archives + 1 debug archive)");
        check(logSize > 0, "log size should be > 0");

        // --- Test cleanLogs with keepRecent=5 ---
        System.out.println("[test] --- cleanLogs keepRecent=5 test ---");
        // Recreate archives (previous test didn't delete them, but let's verify state)
        check(logCount == 7, "should still have 7 archives before clean");
        LogsCleaner.CleanResult r = LogsCleaner.cleanLogs(logsDir, 5);
        System.out.println("[test] keepRecent=5: deleted=" + r.filesDeleted + " freed=" + r.bytesFreed
                + " failures=" + r.failures + " skipped=" + r.filesSkipped);
        // 7 deletable logs total, keep 5 newest → delete 2 oldest
        // Oldest is debugArchive (mtime=500), then archives[0] (mtime=1000)
        check(r.filesDeleted == 2, "should delete 2 oldest logs (keep 5 of 7)");
        check(r.failures == 0, "should have 0 failures");
        check(r.filesSkipped == 5, "should skip 5 non-log files (.lck + README + user .log + user .log.gz + file in user dir)");
        // Active logs must still exist
        check(Files.exists(latestLog), "latest.log must NOT be deleted");
        check(Files.exists(debugLog), "debug.log must NOT be deleted");
        // Non-log files must still exist
        check(Files.exists(lockFile), ".lck file must NOT be deleted");
        check(Files.exists(readmeFile), "README.txt must NOT be deleted");
        // User-placed log-lookalike files and folders must survive
        check(Files.exists(userLog), "user .log file must NOT be deleted");
        check(Files.exists(userGz), "user .log.gz file must NOT be deleted");
        check(Files.isDirectory(userDir), "user folder must NOT be deleted");
        check(Files.exists(userFileInDir), "file inside user folder must NOT be deleted");
        // Newest 5 archives should be kept (archives[1] through archives[5])
        // debugArchive (mtime=500) and archives[0] (mtime=1000) should be deleted
        check(!Files.exists(debugArchive), "oldest debug archive should be deleted");
        check(!Files.exists(archives[0]), "oldest archive should be deleted");
        check(Files.exists(archives[1]), "archive[1] should be KEPT (2nd newest..6th newest kept)");
        check(Files.exists(archives[5]), "newest archive should be KEPT");
        // Verify remaining count
        check(LogsCleaner.countLogFiles(logsDir) == 5, "should have 5 deletable logs remaining");

        // --- Test cleanAllLogs (keepRecent=0) ---
        System.out.println("[test] --- cleanAllLogs test ---");
        LogsCleaner.CleanResult r2 = LogsCleaner.cleanAllLogs(logsDir);
        System.out.println("[test] cleanAll: deleted=" + r2.filesDeleted + " freed=" + r2.bytesFreed
                + " skipped=" + r2.filesSkipped);
        check(r2.filesDeleted == 5, "should delete all 5 remaining archives");
        check(r2.filesSkipped == 5, "should skip 5 non-log files");
        check(Files.exists(latestLog), "latest.log must NOT be deleted");
        check(Files.exists(debugLog), "debug.log must NOT be deleted");
        check(Files.exists(lockFile), ".lck file must NOT be deleted");
        check(Files.exists(readmeFile), "README.txt must NOT be deleted");
        check(Files.exists(userLog), "user .log file must NOT be deleted");
        check(Files.exists(userGz), "user .log.gz file must NOT be deleted");
        check(Files.isDirectory(userDir), "user folder must NOT be deleted");
        check(Files.exists(userFileInDir), "file inside user folder must NOT be deleted");
        check(LogsCleaner.countLogFiles(logsDir) == 0, "should have 0 deletable logs after cleanAll");

        // --- Test missing folder ---
        System.out.println("[test] --- missing folder test ---");
        LogsCleaner.CleanResult r3 = LogsCleaner.cleanLogs(temp.resolve("nope"), 5);
        check(r3.filesDeleted == 0, "missing folder should be a no-op");
        LogsCleaner.CleanResult r4 = LogsCleaner.cleanAllLogs(temp.resolve("nope"));
        check(r4.filesDeleted == 0, "missing folder cleanAll should be a no-op");

        // --- Test keepRecent=0 on empty folder ---
        System.out.println("[test] --- empty folder test ---");
        Path emptyDir = temp.resolve("empty-logs");
        Files.createDirectories(emptyDir);
        LogsCleaner.CleanResult r5 = LogsCleaner.cleanLogs(emptyDir, 5);
        check(r5.filesDeleted == 0, "empty folder should delete 0");

        // --- Test keepRecent > file count ---
        System.out.println("[test] --- keepRecent > count test ---");
        Path logs2 = temp.resolve("logs2");
        Files.createDirectories(logs2);
        Path a1 = logs2.resolve("2024-02-01-1.log.gz");
        Files.write(a1, "log".getBytes(StandardCharsets.UTF_8));
        Path a2 = logs2.resolve("2024-02-02-1.log.gz");
        Files.write(a2, "log".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(a1, FileTime.from(1000L, TimeUnit.SECONDS));
        Files.setLastModifiedTime(a2, FileTime.from(2000L, TimeUnit.SECONDS));
        LogsCleaner.CleanResult r6 = LogsCleaner.cleanLogs(logs2, 10);
        check(r6.filesDeleted == 0, "keepRecent=10 on 2 files should delete 0");
        check(Files.exists(a1), "a1 should be KEPT");
        check(Files.exists(a2), "a2 should be KEPT");

        // Cleanup
        Files.deleteIfExists(latestLog);
        Files.deleteIfExists(debugLog);
        Files.deleteIfExists(lockFile);
        Files.deleteIfExists(readmeFile);
        Files.deleteIfExists(userLog);
        Files.deleteIfExists(userGz);
        Files.deleteIfExists(userFileInDir);
        Files.deleteIfExists(userDir);
        for (int i = 1; i < 6; i++) Files.deleteIfExists(archives[i]);
        Files.deleteIfExists(logsDir);
        Files.deleteIfExists(a1);
        Files.deleteIfExists(a2);
        Files.deleteIfExists(logs2);
        Files.deleteIfExists(emptyDir);
        Files.deleteIfExists(temp);
        System.out.println("[test] LogsCleaner tests passed.");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Creates a fake SpeedrunIGT record file with a random UUID name and the given size. */
    private static Path writeRecord(Path dir, int size) throws IOException {
        byte[] data = new byte[size];
        String marker = "{\"final_igt\":0,\"final_rta\":0}";
        byte[] mb = marker.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(mb.length, size);
        System.arraycopy(mb, 0, data, 0, len);
        for (int i = len; i < size; i++) {
            data[i] = ' ';
        }
        Path file = dir.resolve(UUID.randomUUID() + ".json");
        Files.write(file, data);
        return file;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }
}
