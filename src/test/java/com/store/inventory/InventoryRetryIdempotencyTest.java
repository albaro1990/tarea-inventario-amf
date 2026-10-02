package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The mobile app resends a cart's order automatically on a slow connection, so {@code reserve}
 * must tolerate a duplicate call for the same order without double-reserving units.
 */
class InventoryRetryIdempotencyTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
    }

    @Test
    void retryingTheSameOrderDoesNotDoubleReserve() {
        Reservation first = service.reserve("ORDER-1", "SKU-1", 3);
        Reservation retry = service.reserve("ORDER-1", "SKU-1", 3);

        assertEquals(first, retry);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void reusingAnOrderIdForADifferentProductFails() {
        service.registerProduct("SKU-2", ProductCategory.STANDARD);
        service.addStock("SKU-2", 10);

        service.reserve("ORDER-1", "SKU-1", 1);

        assertThrows(IllegalStateException.class, () -> service.reserve("ORDER-1", "SKU-2", 1));
    }
}
