package com.chaosLab.withSpringStarter;
import com.chaosLab.Chaosify;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private final Map<String, Order> storage = new ConcurrentHashMap<>();

    public Order createOrder(double price) {
        String id = UUID.randomUUID().toString();
        Order order = new Order(id, price, OrderStatus.CREATED);
        storage.put(id, order);
        return order;
    }

    @Chaosify(scenario = "stress")
    public Order pay(String orderId) {
        Order order = get(orderId);

        // имитация бизнес-логики
        order.setOrderStatus(OrderStatus.PAID);
        return order;
    }

    @Chaosify(scenario = "default")
    public Order get(String orderId) {
        Order order = storage.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found");
        }
        return order;
    }
}