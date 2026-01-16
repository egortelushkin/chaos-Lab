package com.chaosLab.withSpringStarter;


import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public Order create(@RequestParam double price) {
        return service.createOrder(price);
    }

    @PostMapping("/{id}/pay")
    public Order pay(@PathVariable String id) {
        return service.pay(id);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        return service.get(id);
    }
}