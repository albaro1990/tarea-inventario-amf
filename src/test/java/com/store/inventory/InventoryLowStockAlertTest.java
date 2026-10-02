package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.StockAlertListener;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryLowStockAlertTest {

    private record Alert(String sku, int available) {
    }

    private final List<Alert> alerts = new ArrayList<>();
    private InventoryService service;

    @BeforeEach
    void setUp() {
        StockAlertListener listener = (sku, available) -> alerts.add(new Alert(sku, available));
        service = Inventory.create(Clock.systemUTC(), listener);
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
    }

    @Test
    void alertsOnceWhenAvailableDropsToThreshold() {
        service.reserve("ORDER-1", "SKU-1", 4);
        assertEquals(0, alerts.size());

        service.reserve("ORDER-2", "SKU-1", 1);
        assertEquals(1, alerts.size());
        assertEquals(new Alert("SKU-1", 5), alerts.get(0));

        service.reserve("ORDER-3", "SKU-1", 1);
        assertEquals(1, alerts.size());
    }

    @Test
    void alertsAgainAfterRestockingAboveThreshold() {
        service.reserve("ORDER-1", "SKU-1", 6);
        assertEquals(1, alerts.size());

        service.addStock("SKU-1", 10);
        service.reserve("ORDER-2", "SKU-1", 10);
        assertEquals(2, alerts.size());
    }

    @Test
    void partialRestockThatStaysAtOrBelowThresholdDoesNotReAlert() {
        service.reserve("ORDER-1", "SKU-1", 6);
        assertEquals(1, alerts.size());

        service.addStock("SKU-1", 1);
        assertEquals(1, alerts.size());
    }
}
