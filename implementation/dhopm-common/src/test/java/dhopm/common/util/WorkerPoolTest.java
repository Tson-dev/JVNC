package dhopm.common.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerPoolTest {

    @Test
    void runsTasksAndAwaitsAll() {
        AtomicInteger counter = new AtomicInteger();
        try (WorkerPool pool = new WorkerPool(4)) {
            for (int i = 0; i < 100; i++) {
                pool.submit(counter::incrementAndGet);
            }
            pool.awaitAll();
        }
        assertEquals(100, counter.get());
    }

    @Test
    void rejectsZeroWorkers() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerPool(0));
    }
}