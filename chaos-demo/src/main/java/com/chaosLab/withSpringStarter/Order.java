package com.chaosLab.withSpringStarter;

public class Order {
    private String id;
    private double price;
    private OrderStatus orderStatus;

    public Order(String id, double price, OrderStatus orderStatus) {
        this.id = id;
        this.price = price;
        this.orderStatus = orderStatus;
    }

    public Order() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }
}
