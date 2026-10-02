package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryValidationTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
    }

    @Test
    void unknownProductHasNoAvailableUnits() {
        assertEquals(0, service.available("GHOST-SKU"));
    }

    @Test
    void reservingAnUnknownProductIsInsufficientStock() {
        assertThrows(InsufficientStockException.class, () -> service.reserve("ORDER-1", "GHOST-SKU", 1));
    }

    @Test
    void addingStockToAnUnregisteredProductFails() {
        assertThrows(IllegalArgumentException.class, () -> service.addStock("GHOST-SKU", 10));
    }

    @Test
    void addStockRejectsZeroOrNegativeQuantities() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        assertThrows(IllegalArgumentException.class, () -> service.addStock("SKU-1", 0));
        assertThrows(IllegalArgumentException.class, () -> service.addStock("SKU-1", -5));
    }

    @Test
    void reserveRejectsZeroOrNegativeQuantities() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        assertThrows(IllegalArgumentException.class, () -> service.reserve("ORDER-1", "SKU-1", 0));
        assertThrows(IllegalArgumentException.class, () -> service.reserve("ORDER-1", "SKU-1", -1));
    }

    @Test
    void confirmingWithoutAReservationFails() {
        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
    }

    @Test
    void confirmingTwiceFailsTheSecondTime() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 2);
        service.confirm("ORDER-1");
        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
    }

    @Test
    void registeringTheSameProductTwiceFails() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        assertThrows(IllegalArgumentException.class,
                () -> service.registerProduct("SKU-1", ProductCategory.FLASH_SALE));
    }
}
