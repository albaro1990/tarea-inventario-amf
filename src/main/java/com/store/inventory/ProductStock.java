package com.store.inventory;

import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable state for a single SKU. Every read/write goes through the monitor of this
 * object (see InventoryServiceImpl), so one instance per SKU is also the concurrency unit:
 * two SKUs never block each other, but operations on the same SKU are serialized.
 */
final class ProductStock {

    final ProductCategory category;
    final Map<String, Reservation> activeReservations = new LinkedHashMap<>();
    int totalUnits;
    int soldUnits;
    boolean lowStockAlerted;

    ProductStock(ProductCategory category) {
        this.category = category;
    }

    int reservedUnits() {
        int sum = 0;
        for (Reservation reservation : activeReservations.values()) {
            sum += reservation.quantity();
        }
        return sum;
    }

    int available() {
        return totalUnits - soldUnits - reservedUnits();
    }
}
