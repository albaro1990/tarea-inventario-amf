package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Reproduces the "hundreds of customers buying the same products at once" scenario from the README. */
class InventoryConcurrencyTest {

    @Test
    void concurrentReservationsNeverOversell() throws InterruptedException {
        InventoryService service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 50);

        int attempts = 200;
        // Pool size must match attempts: every task blocks on start.await() before finishing,
        // so a smaller pool would starve queued tasks and deadlock the ready.await() below.
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            int orderNumber = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.reserve("ORDER-" + orderNumber, "SKU-1", 1);
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException expectedOnceSoldOut) {
                    // InsufficientStockException once the 50 units run out.
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(50, succeeded.get());
        assertEquals(0, service.available("SKU-1"));
    }
}
