package io.github.sweetzonzi.machine_max.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class QueuedCsvWriterTest {
    @TempDir Path directory;

    @Test void namesAreUniqueAndOverflowIsExplicit() throws Exception {
        try (var first = QueuedCsvWriter.open(directory, "../../same:name", "value", 2);
             var second = QueuedCsvWriter.open(directory, "../../same:name", "value", 2)) {
            assertNotEquals(first.path(), second.path());
            assertEquals(directory, first.path().getParent());
            assertTrue(first.offer("one"));
            assertTrue(first.offer("two"));
            assertFalse(first.offer("lost"));
            assertEquals(1, first.dropped());
            first.flush();
            assertTrue(first.offer("three"));
            first.close();
            first.close();
            assertFalse(first.offer("after_close"));
            assertEquals(3, first.samples());
            assertEquals(java.util.List.of("value", "one", "two", "three"), Files.readAllLines(first.path()));
        }
    }

    @Test void closeDrainsEveryAcceptedRowWhileProducersAreRacing() throws Exception {
        var csv = QueuedCsvWriter.open(directory, "race", "value", 65536);
        var firstRows = new CountDownLatch(4);
        var run = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (int producer = 0; producer < 4; producer++) {
                int id = producer;
                pool.submit(() -> {
                    csv.offer(id + ":first");
                    firstRows.countDown();
                    run.await();
                    for (int i = 0; i < 10000; i++) csv.offer(id + ":" + i);
                    return null;
                });
            }
            assertTrue(firstRows.await(5, java.util.concurrent.TimeUnit.SECONDS));
            run.countDown();
            csv.close();
        }
        var rows = Files.readAllLines(csv.path());
        assertEquals(csv.samples() + 1, rows.size());
        assertEquals(rows.size(), new java.util.HashSet<>(rows).size());
        assertEquals(0, csv.dropped());
        assertFalse(csv.failed());
    }

    @Test void csvQuotingPreservesEmptyAndSpecialFields() {
        assertEquals(",\"a,b\",\"say \"\"yes\"\"\",\"line\nnext\",", QueuedCsvWriter.row("", "a,b", "say \"yes\"", "line\nnext", null));
    }
}
