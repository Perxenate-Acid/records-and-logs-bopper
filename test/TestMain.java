import com.speedrunigtcleaner.RecordsCleaner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Standalone test for RecordsCleaner (no Jingle dependency).
 * Verifies: file filtering (UUID name + content), size/count, recursive deletion,
 * folder preservation, non-record file protection, keepRecent, and no-op on
 * missing folders.
 */
public class TestMain {

    public static void main(String[] args) throws IOException {
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
        // UUID-named but wrong content (no "final_igt") — should NOT be deleted
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

        // --- Test size/count (only counts record files by name pattern) ---
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
        // Create 5 record files with distinct modification times (oldest first).
        Path[] files = new Path[5];
        for (int i = 0; i < 5; i++) {
            files[i] = writeRecord(records2, 100 * (i + 1));
            Files.setLastModifiedTime(files[i], FileTime.from(1000L + i, TimeUnit.SECONDS));
        }
        // Also add a non-record file to verify it's not counted.
        Path userFile2 = records2.resolve("user.txt");
        Files.write(userFile2, new byte[500]);

        // Keep the 2 most recent (files[3], files[4]).
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

        // keepRecent larger than file count → keep everything.
        RecordsCleaner.CleanResult r4 = RecordsCleaner.clean(records2, 10);
        check(r4.filesDeleted == 0, "keepRecent=10 on 2 files should delete 0");
        check(RecordsCleaner.countFiles(records2) == 2, "both files should still exist");

        // keepRecent=0 → delete all records.
        RecordsCleaner.CleanResult r5 = RecordsCleaner.clean(records2, 0);
        check(r5.filesDeleted == 2, "keepRecent=0 should delete all 2 record files");
        check(Files.exists(userFile2), "non-record file should still exist after keepRecent=0 clean");
        check(RecordsCleaner.countFiles(records2) == 0, "should have 0 record files");

        // keepRecent on non-existent folder → no-op.
        RecordsCleaner.CleanResult r6 = RecordsCleaner.clean(temp.resolve("nope2"), 5);
        check(r6.filesDeleted == 0, "keepRecent on missing folder should be a no-op");

        System.out.println("ALL TESTS PASSED");

        // Cleanup
        Files.deleteIfExists(userTxt);
        Files.deleteIfExists(userJson);
        Files.deleteIfExists(fakeRecord);
        Files.deleteIfExists(records);
        Files.deleteIfExists(userFile2);
        Files.deleteIfExists(records2);
        Files.deleteIfExists(outsideFile);
        Files.deleteIfExists(temp);
    }

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
