package com.example.bai1__ss14.service;

import com.example.bai1__ss14.client.InventoryClient;
import com.example.bai1__ss14.model.CompensationLog;
import com.example.bai1__ss14.model.CompensationStatus;
import com.example.bai1__ss14.model.Order;
import com.example.bai1__ss14.model.OrderRequest;
import com.example.bai1__ss14.repository.CompensationLogRepository;
import com.example.bai1__ss14.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phiên bản nâng cao với Saga Pattern, State Log & Compensatory Action (Requirement c):
 * Xử lý triệt để khi việc hoàn trả kho trực tiếp cũng thất bại (lỗi mạng, timeout).
 * Áp dụng Transactional Outbox Pattern / Saga Log:
 * 1. Ghi nhận sự kiện bù trừ (Compensation Log) với trạng thái PENDING.
 * 2. Cố gắng bù trừ ngay lập tức.
 * 3. Nếu bù trừ ngay thất bại do sự cố mạng/timeout, sự kiện vẫn được lưu trong DB/Log.
 * 4. Background Job / Worker định kỳ quét các sự kiện PENDING để retry bù trừ đảm bảo Eventual Consistency.
 */
@Service
public class SagaOrderService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrderService.class);

    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final CompensationLogRepository compensationLogRepository;

    public SagaOrderService(InventoryClient inventoryClient, 
                            OrderRepository orderRepository, 
                            CompensationLogRepository compensationLogRepository) {
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
        this.compensationLogRepository = compensationLogRepository;
    }

    public ResponseEntity<Order> createOrderWithSaga(OrderRequest request) {
        String sagaId = UUID.randomUUID().toString();
        log.info("[SagaOrderService] [SagaId={}] Khởi tạo Saga đặt hàng: productId={}, quantity={}", 
                sagaId, request.getProductId(), request.getQuantity());

        // BƯỚC 1: Trừ kho tại Inventory Service
        boolean stockDecreased;
        try {
            stockDecreased = inventoryClient.decreaseStock(request.getProductId(), request.getQuantity());
        } catch (Exception e) {
            log.error("[SagaOrderService] [SagaId={}] Gọi trừ kho thất bại do lỗi kỹ thuật: {}", sagaId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (!stockDecreased) {
            log.warn("[SagaOrderService] [SagaId={}] Không thể trừ kho (Hết hàng)", sagaId);
            return ResponseEntity.badRequest().body(null);
        }

        // BƯỚC 2: Tạo và lưu đơn hàng vào DB cục bộ
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        try {
            Order savedOrder = orderRepository.save(order);
            log.info("[SagaOrderService] [SagaId={}] Lưu đơn hàng thành công, orderId={}", sagaId, savedOrder.getId());
            return ResponseEntity.ok(savedOrder);
        } catch (Exception dbException) {
            log.error("[SagaOrderService] [SagaId={}] Lỗi lưu đơn hàng vào DB: {}. Kích hoạt Saga Compensating Flow...", 
                    sagaId, dbException.getMessage());

            // BƯỚC 3: Xử lý bù trừ an toàn (Saga Compensatory Action với Log State)
            handleCompensationWithLog(sagaId, null, request.getProductId(), request.getQuantity(), dbException.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Ghi log sự kiện bù trừ và thực hiện bù trừ an toàn
     */
    private void handleCompensationWithLog(String sagaId, Long orderId, String productId, int quantity, String reason) {
        // 1. Tạo bản ghi Compensation Log trạng thái PENDING
        String logId = UUID.randomUUID().toString();
        CompensationLog compLog = new CompensationLog(logId, sagaId, orderId, productId, quantity);
        compLog.setLastErrorMessage("Đơn hàng thất bại: " + reason);
        compensationLogRepository.save(compLog);

        log.info("[SagaOrderService] [SagaId={}] Đã lưu CompensationLog [id={}] với trạng thái PENDING", sagaId, logId);

        // 2. Thử thực hiện bù trừ trực tiếp ngay lập tức
        try {
            boolean compensated = inventoryClient.increaseStock(productId, quantity);
            if (compensated) {
                compLog.setStatus(CompensationStatus.COMPLETED);
                compLog.setUpdatedAt(LocalDateTime.now());
                compensationLogRepository.save(compLog);
                log.info("[SagaOrderService] [SagaId={}] Bù trừ kho thành công ngay lập tức! Đã cập nhật Log COMPLETED", sagaId);
            } else {
                compLog.setRetryCount(compLog.getRetryCount() + 1);
                compLog.setLastErrorMessage("Inventory Client trả về false khi cộng trả kho");
                compLog.setUpdatedAt(LocalDateTime.now());
                compensationLogRepository.save(compLog);
                log.warn("[SagaOrderService] [SagaId={}] Bù trừ trả về false. Để lại cho Background Job xử lý.", sagaId);
            }
        } catch (Exception ex) {
            // Lỗi mạng hoặc Timeout khi gọi bù trừ kho
            compLog.setRetryCount(compLog.getRetryCount() + 1);
            compLog.setLastErrorMessage("Lỗi mạng/timeout khi bù trừ: " + ex.getMessage());
            compLog.setUpdatedAt(LocalDateTime.now());
            compensationLogRepository.save(compLog);

            log.error("[SagaOrderService] [SagaId={}] Bù trừ trực tiếp thất bại do lỗi mạng/timeout! " +
                    "CompensationLog [id={}] vẫn ở trạng thái PENDING. " +
                    "Background Job sẽ tự động retry để đảm bảo Eventual Consistency.", sagaId, logId);
        }
    }
}
