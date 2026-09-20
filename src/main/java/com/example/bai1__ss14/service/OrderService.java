package com.example.bai1__ss14.service;

import com.example.bai1__ss14.client.InventoryClient;
import com.example.bai1__ss14.model.Order;
import com.example.bai1__ss14.model.OrderRequest;
import com.example.bai1__ss14.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Phiên bản đã sửa (Requirement b):
 * Bổ sung cơ chế bù trừ (Compensating Transaction) trực tiếp:
 * Khi lưu đơn hàng thất bại, gọi lại inventoryClient.increaseStock để cộng trả lại kho.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;

    public OrderService(InventoryClient inventoryClient, OrderRepository orderRepository) {
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
    }

    public ResponseEntity<Order> createOrder(OrderRequest request) {
        log.info("[OrderService] Bắt đầu tạo đơn hàng với cơ chế bù trừ trực tiếp: productId={}, quantity={}", 
                request.getProductId(), request.getQuantity());

        // 1. Gọi Inventory Service để trừ kho
        boolean stockDecreased = inventoryClient.decreaseStock(request.getProductId(), request.getQuantity());
        if (!stockDecreased) {
            log.warn("[OrderService] Không thể trừ kho (Hết hàng hoặc lỗi tồn kho)");
            return ResponseEntity.badRequest().body(null); // Hết hàng
        }

        // 2. Tạo đơn hàng
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        // Giả sử có lỗi ở bước lưu (ví dụ DB timeout)
        try {
            Order savedOrder = orderRepository.save(order);
            log.info("[OrderService] Đặt hàng thành công, mã đơn: {}", savedOrder.getId());
            return ResponseEntity.ok(savedOrder);
        } catch (Exception e) {
            log.error("[OrderService] Lỗi khi lưu đơn hàng vào DB: {}. Bắt đầu kích hoạt hành động bù trừ (Compensate)...", e.getMessage());

            // 3. Cơ chế bù trừ: Hoàn lại số lượng tồn kho đã trừ
            try {
                boolean compensated = inventoryClient.increaseStock(request.getProductId(), request.getQuantity());
                if (compensated) {
                    log.info("[OrderService] BÙ TRỪ THÀNH CÔNG: Đã hoàn trả {} sản phẩm cho productId={}", 
                            request.getQuantity(), request.getProductId());
                } else {
                    log.error("[OrderService] Bù trừ thất bại từ phía Inventory Service!");
                }
            } catch (Exception compensationEx) {
                // Trường hợp gọi bù trừ cũng gặp lỗi (lỗi mạng, timeout) - sẽ được xử lý tối ưu ở SagaOrderService
                log.error("[OrderService] NGUY HIỂM: Gọi bù trừ kho trực tiếp thất bại! Lỗi: {}", compensationEx.getMessage());
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
