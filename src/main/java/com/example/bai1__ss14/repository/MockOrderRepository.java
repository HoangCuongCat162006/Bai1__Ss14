package com.example.bai1__ss14.repository;

import com.example.bai1__ss14.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MockOrderRepository implements OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(MockOrderRepository.class);

    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1000);
    private volatile boolean simulateSaveFailure = false;

    @Override
    public Order save(Order order) {
        if (simulateSaveFailure) {
            log.error("[OrderRepository] GIẢ LẬP LỖI LƯU CƠ SỞ DỮ LIỆU (DB Timeout / Connection Error)!");
            throw new RuntimeException("Database Timeout: Không thể kết nối cơ sở dữ liệu để lưu đơn hàng!");
        }

        if (order.getId() == null) {
            order.setId(idGenerator.incrementAndGet());
        }
        orders.put(order.getId(), order);
        log.info("[OrderRepository] Lưu đơn hàng THÀNH CÔNG: orderId={}, status={}", order.getId(), order.getStatus());
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }

    @Override
    public void setSimulateSaveFailure(boolean simulateFailure) {
        this.simulateSaveFailure = simulateFailure;
    }

    @Override
    public boolean isSimulateSaveFailure() {
        return simulateSaveFailure;
    }
}
