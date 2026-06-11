package com.chaosLab.orders;

import com.chaosLab.Chaosify;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    @Chaosify(scenario = "orders-create")
    public Order createOrder(CreateOrderRequest request) {
        BigDecimal total = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        Order order = new Order(
                UUID.randomUUID().toString(),
                request.customerId(),
                request.sku(),
                request.quantity(),
                request.unitPrice(),
                total,
                OrderStatus.CREATED,
                Instant.now(),
                null
        );
        orders.put(order.id(), order);
        return order;
    }

    @Chaosify(scenario = "orders-read")
    public Order getOrder(String orderId) {
        return requireOrder(orderId);
    }

    public List<Order> listOrders() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
    }

    @Chaosify(scenario = "orders-payment")
    public Order payOrder(String orderId) {
        return orders.compute(orderId, (id, existing) -> {
            if (existing == null) {
                throw new OrderNotFoundException(id);
            }
            if (existing.status() == OrderStatus.PAID) {
                return existing;
            }
            return existing.markPaid(Instant.now());
        });
    }

    private Order requireOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }
}
