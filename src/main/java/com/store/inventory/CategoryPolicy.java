package com.store.inventory;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.OptionalInt;

/**
 * Business rules per category, described in the README. Kept separate from
 * {@link ProductCategory} because that enum belongs to the contract and can't be touched.
 */
final class CategoryPolicy {

    private static final int FLASH_SALE_ORDER_LIMIT = 2;

    private CategoryPolicy() {
    }

    static Duration reservationTtl(ProductCategory category) {
        return switch (category) {
            case STANDARD -> Duration.ofMinutes(15);
            case PRE_ORDER -> Duration.ofHours(24);
            case FLASH_SALE -> Duration.ofMinutes(5);
        };
    }

    static OptionalInt orderLimit(ProductCategory category) {
        return switch (category) {
            case FLASH_SALE -> OptionalInt.of(FLASH_SALE_ORDER_LIMIT);
            case STANDARD, PRE_ORDER -> OptionalInt.empty();
        };
    }
}
