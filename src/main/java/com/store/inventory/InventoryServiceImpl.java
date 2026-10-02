package com.store.inventory;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.api.StockAlertListener;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link InventoryService}. See DECISIONS.md for the
 * assumptions behind expiration, idempotency and low-stock alerting.
 */
final class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final Clock clock;
    private final StockAlertListener alertListener;
    private final Map<String, ProductStock> stocksBySku = new ConcurrentHashMap<>();
    private final Map<String, String> skuByOrderId = new ConcurrentHashMap<>();

    InventoryServiceImpl(Clock clock, StockAlertListener alertListener) {
        this.clock = clock;
        this.alertListener = alertListener;
    }

    @Override
    public void registerProduct(String sku, ProductCategory category) {
        if (stocksBySku.putIfAbsent(sku, new ProductStock(category)) != null) {
            throw new IllegalArgumentException("Product already registered: " + sku);
        }
    }

    @Override
    public void addStock(String sku, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        ProductStock stock = stocksBySku.get(sku);

        if (stock == null) {
            throw new IllegalArgumentException("Unknown producto: " + sku);
        }

        synchronized (stock) {
            stock.totalUnits += quantity;
            purgeExpired(stock);
            if (stock.available() > LOW_STOCK_THRESHOLD) {
                stock.lowStockAlerted = false;
            }
        }
    }

    @Override
    public Reservation reserve(String orderId, String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        ProductStock stock = stocksBySku.get(sku);
        if (stock == null) {
            throw new InsufficientStockException(sku, quantity, 0);
        }
        synchronized (stock) {
            purgeExpired(stock);

            Reservation existing = stock.activeReservations.get(orderId);
            if (existing != null) {
                return existing;
            }
            if (skuByOrderId.containsKey(orderId)) {
                throw new IllegalStateException(
                        "Order " + orderId + " already has a reservation for a different product");
            }

            OptionalInt limit = CategoryPolicy.orderLimit(stock.category);
            if (limit.isPresent() && quantity > limit.getAsInt()) {
                throw new OrderLimitExceededException(sku, quantity, limit.getAsInt());
            }

            int available = stock.available();
                if (quantity > available) {
                    throw new InsufficientStockException(sku, quantity, available);
                }

            Instant expiresAt = clock.instant().plus(CategoryPolicy.reservationTtl(stock.category));
            Reservation reservation = new Reservation(orderId, sku, quantity, expiresAt);
            stock.activeReservations.put(orderId, reservation);
            skuByOrderId.put(orderId, sku);

            if (stock.available() <= LOW_STOCK_THRESHOLD && !stock.lowStockAlerted) {
                stock.lowStockAlerted = true;
                alertListener.onLowStock(sku, stock.available());
            }

            return reservation;
        }
    }

    @Override
    public void confirm(String orderId) {
        String sku = skuByOrderId.get(orderId);
        if (sku == null) {
            throw new IllegalStateException("No active reservation to order " + orderId);
        }


        ProductStock stock = stocksBySku.get(sku);


        synchronized (stock) {
            purgeExpired(stock);
            Reservation reservation = stock.activeReservations.remove(orderId);
            skuByOrderId.remove(orderId);
            if (reservation == null) {
                throw new IllegalStateException("No active reservation to order " + orderId);
            }
            stock.soldUnits += reservation.quantity();
        }
    }

    @Override
    public int available(String sku) {
        ProductStock stock = stocksBySku.get(sku);
        if (stock == null) {
            return 0;
        }
        synchronized (stock) {
            purgeExpired(stock);
            return stock.available();
        }
    }


    private void purgeExpired(ProductStock stock) {

        Instant now = clock.instant();
        Iterator<Map.Entry<String, Reservation>> iterator = stock.activeReservations.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Reservation> entry = iterator.next();
            if (!entry.getValue().expiresAt().isAfter(now)) {
                iterator.remove();
                skuByOrderId.remove(entry.getKey());
            }
        }
    }
}
