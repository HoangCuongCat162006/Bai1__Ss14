package com.example.bai1__ss14.repository;

import com.example.bai1__ss14.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findAll();
    void setSimulateSaveFailure(boolean simulateFailure);
    boolean isSimulateSaveFailure();
}
