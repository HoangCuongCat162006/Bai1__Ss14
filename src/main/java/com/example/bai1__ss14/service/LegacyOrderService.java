package com.example.bai1__ss14.service;

import com.example.bai1__ss14.client.InventoryClient;
import com.example.bai1__ss14.model.Order;
import com.example.bai1__ss14.model.OrderRequest;
import com.example.bai1__ss14.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Mã nguồn kế thừa (Legacy Code) chứa lỗi logic "Kho treo" (Phantom stock).
 * Luồng xử lý: Trừ kho trước -> Lưu đơn sau.
 * Khi lưu đơn thất bại, KHÔNG có cơ chế bù trừ (compensating transaction)
 * dẫn tới tồn kho bị mất mà không có đơn hàng nào được tạo.
 */
@Service
public class LegacyOrderService {

    private static final Logger log = LoggerFactory.getLogger(LegacyOrderService.class);

    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;

    public LegacyOrderService(InventoryClient inventoryClient, OrderRepository orderRepository) {
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
    }

    public ResponseEntity<Order> createOrder(OrderRequest request) {
        log.warn("[LegacyOrderService] Bắt đầu tạo đơn theo luồng cũ (LEGACY)...");

        // 1. Gọi Inventory Service để trừ kho
        boolean stockDecreased = inventoryClient.decreaseStock(request.getProductId(), request.getQuantity());
        if (!stockDecreased) {
            log.warn("[LegacyOrderService] Hết hàng hoặc không thể trừ kho cho productId={}", request.getProductId());
            return ResponseEntity.badRequest().body(null); // Hết hàng
        }

        // 2. Tạo đơn hàng
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        // Giả sử có lỗi ở bước lưu (ví dụ DB timeout)
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            // LỖI: Không hoàn lại kho khi tạo đơn thất bại!
            log.error("[LegacyOrderService] LỖI LƯU ĐƠN HÀNG: {}. " +
                    "NGUY HIỂM: Kho đã bị trừ nhưng không được hoàn tác -> Gây ra 'Hàng ảo'!", e.getMessage());
            return ResponseEntity.status(500).build();
        }

        return ResponseEntity.ok(order);
    }
}
