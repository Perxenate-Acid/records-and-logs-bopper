import com.speedrunigtcleaner.RecordsCleaner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone test for RecordsCleaner (no Jingle dependency).
 * Verifies: size/count calculation, recursive deletion, folder preservation,
 * no side effects on files outside the folder, and no-op on missing folders.
 */
public class TestMain {

    public static void main(String[] args) throws IOException {
        Path temp = Files.createTempDirectory("records-cleaner-test");
        Path records = temp.resolve("records");
        Files.createDirectories(records);
        Files.write(records.resolve("a.txt"), new byte[1000]);
        Files.write(records.resolve("b.txt"), new byte[2000]);
        Path sub = records.resolve("sub");
        Files.createDirectories(sub);
        Files.write(sub.resolve("c.txt"), new byte[3000]);
        Path keep = temp.resolve("keep.txt");
        Files.write(keep, new byte[999]);

        long size = RecordsCleaner.getFolderSize(records);
        int count = RecordsCleaner.countFiles(records);
        System.out.println("[test] size=" + size + " count=" + count);
        check(size == 6000, "size should be 6000");
        check(count == 3, "count should be 3");

        RecordsCleaner.CleanResult result = RecordsCleaner.clean(records);
        System.out.println("[test] deleted=" + result.filesDeleted + " freed=" + result.bytesFreed + " failures=" + result.failures);
        check(result.filesDeleted == 3, "should delete 3 files");
        check(result.bytesFreed == 6000, "should free 6000 bytes");
        check(result.failures == 0, "should have 0 failures");
        check(Files.exists(records), "records dir must be KEPT");
        check(!Files.exists(sub), "subdir should be deleted");
        check(Files.exists(keep), "file outside folder must be untouched");
        check(RecordsCleaner.getFolderSize(records) == 0, "folder should be empty after clean");

        RecordsCleaner.CleanResult r2 = RecordsCleaner.clean(temp.resolve("nope"));
        check(r2.filesDeleted == 0, "missing folder should be a no-op");

        System.out.println("ALL TESTS PASSED");

        Files.deleteIfExists(keep);
        Files.deleteIfExists(records);
        Files.deleteIfExists(temp);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }
}
