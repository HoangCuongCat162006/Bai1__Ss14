package com.example.bai1__ss14.model;

import java.time.LocalDateTime;

public class Order {
    private Long id;
    private String productId;
    private int quantity;
    private String status; // PENDING, CREATED, FAILED, CANCELLED
    private LocalDateTime createdAt;
    private String failureReason;

    public Order() {
        this.createdAt = LocalDateTime.now();
    }

    public Order(Long id, String productId, int quantity, String status) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", failureReason='" + failureReason + '\'' +
                '}';
    }
}
