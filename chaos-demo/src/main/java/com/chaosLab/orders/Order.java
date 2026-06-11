package com.chaosLab.orders;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        String id,
        String customerId,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total,
        OrderStatus status,
        Instant createdAt,
        Instant paidAt
) {
    public Order markPaid(Instant paidAt) {
        return new Order(id, customerId, sku, quantity, unitPrice, total, OrderStatus.PAID, createdAt, paidAt);
    }
}
