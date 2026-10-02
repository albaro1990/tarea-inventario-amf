package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryOrderLimitTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
    }

    @Test
    void flashSaleRejectsOrdersAboveTwoUnits() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 10);
        assertThrows(OrderLimitExceededException.class, () -> service.reserve("ORDER-1", "SKU-1", 3));
    }

    @Test
    void flashSaleAllowsUpToTwoUnits() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 10);
        assertDoesNotThrow(() -> service.reserve("ORDER-1", "SKU-1", 2));
    }

    @Test
    void standardAndPreOrderHaveNoUnitLimit() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.registerProduct("SKU-2", ProductCategory.PRE_ORDER);
        service.addStock("SKU-1", 100);
        service.addStock("SKU-2", 100);

        assertDoesNotThrow(() -> service.reserve("ORDER-1", "SKU-1", 50));
        assertDoesNotThrow(() -> service.reserve("ORDER-2", "SKU-2", 50));
    }
}
