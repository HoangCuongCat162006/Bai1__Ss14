package com.example.bai1__ss14.model;

import java.time.LocalDateTime;

public class CompensationLog {
    private String id;
    private String sagaId;
    private Long orderId;
    private String productId;
    private int quantity;
    private int retryCount;
    private int maxRetries;
    private CompensationStatus status;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CompensationLog() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = CompensationStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    public CompensationLog(String id, String sagaId, Long orderId, String productId, int quantity) {
        this();
        this.id = id;
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public CompensationStatus getStatus() {
        return status;
    }

    public void setStatus(CompensationStatus status) {
        this.status = status;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "CompensationLog{" +
                "id='" + id + '\'' +
                ", sagaId='" + sagaId + '\'' +
                ", orderId=" + orderId +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", retryCount=" + retryCount +
                ", status=" + status +
                ", lastErrorMessage='" + lastErrorMessage + '\'' +
                '}';
    }
}
