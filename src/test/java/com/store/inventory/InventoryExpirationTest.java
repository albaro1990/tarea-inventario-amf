package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryExpirationTest {

    private MutableClock clock;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        clock = MutableClock.startingAt(Instant.parse("2024-01-01T00:00:00Z"));
        service = Inventory.create(clock, (sku, available) -> { });
    }

    @Test
    void expiredReservationReleasesUnitsBackToStock() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);
        assertEquals(6, service.available("SKU-1"));

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void confirmingAnExpiredReservationFails() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 2);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
    }

    @Test
    void preOrderReservationLastsTwentyFourHours() {
        service.registerProduct("SKU-1", ProductCategory.PRE_ORDER);
        service.addStock("SKU-1", 5);
        service.reserve("ORDER-1", "SKU-1", 5);

        clock.advance(Duration.ofHours(23).plusMinutes(59));
        assertEquals(0, service.available("SKU-1"));

        clock.advance(Duration.ofMinutes(2));
        assertEquals(5, service.available("SKU-1"));
    }
}
