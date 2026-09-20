package com.example.bai1__ss14.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockInventoryClient implements InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(MockInventoryClient.class);

    private final ConcurrentHashMap<String, Integer> stockMap = new ConcurrentHashMap<>();
    private volatile boolean simulateIncreaseStockFailure = false;

    public MockInventoryClient() {
        // Khởi tạo một số sản phẩm mẫu với số lượng tồn kho ban đầu
        stockMap.put("PROD-001", 100);
        stockMap.put("PROD-002", 10);
        stockMap.put("PROD-003", 0);
    }

    @Override
    public boolean decreaseStock(String productId, int quantity) {
        log.info("[InventoryClient] Yêu cầu TRỪ kho: productId={}, quantity={}", productId, quantity);
        Integer current = stockMap.getOrDefault(productId, 0);

        if (current < quantity) {
            log.warn("[InventoryClient] Hết hàng! Tồn kho hiện tại ({}) < Yêu cầu ({})", current, quantity);
            return false;
        }

        // Trừ kho an toàn trong môi trường đồng thời
        stockMap.compute(productId, (k, v) -> (v == null ? 0 : v) - quantity);
        log.info("[InventoryClient] Trừ kho THÀNH CÔNG: productId={}, tồn kho còn lại={}", 
                productId, stockMap.get(productId));
        return true;
    }

    @Override
    public boolean increaseStock(String productId, int quantity) {
        log.info("[InventoryClient] Yêu cầu BÙ TRỪ (CỘNG TRẢ) kho: productId={}, quantity={}", productId, quantity);

        if (simulateIncreaseStockFailure) {
            log.error("[InventoryClient] GIẢ LẬP LỖI MẠNG / TIMEOUT khi gọi bù trừ kho cho productId={}", productId);
            throw new RuntimeException("Inventory Service Timeout: Không thể kết nối tới Inventory Service để bù trừ!");
        }

        stockMap.compute(productId, (k, v) -> (v == null ? 0 : v) + quantity);
        log.info("[InventoryClient] Bù trừ kho THÀNH CÔNG: productId={}, tồn kho hiện tại={}", 
                productId, stockMap.get(productId));
        return true;
    }

    @Override
    public int getStock(String productId) {
        return stockMap.getOrDefault(productId, 0);
    }

    @Override
    public void setStock(String productId, int quantity) {
        stockMap.put(productId, quantity);
    }

    @Override
    public void setSimulateIncreaseStockFailure(boolean simulateFailure) {
        this.simulateIncreaseStockFailure = simulateFailure;
    }

    @Override
    public boolean isSimulateIncreaseStockFailure() {
        return simulateIncreaseStockFailure;
    }
}
