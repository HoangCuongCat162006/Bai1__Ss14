package com.example.bai1__ss14;

import com.example.bai1__ss14.client.InventoryClient;
import com.example.bai1__ss14.model.CompensationLog;
import com.example.bai1__ss14.model.CompensationStatus;
import com.example.bai1__ss14.model.Order;
import com.example.bai1__ss14.model.OrderRequest;
import com.example.bai1__ss14.repository.CompensationLogRepository;
import com.example.bai1__ss14.repository.OrderRepository;
import com.example.bai1__ss14.service.CompensationSchedulerJob;
import com.example.bai1__ss14.service.LegacyOrderService;
import com.example.bai1__ss14.service.OrderService;
import com.example.bai1__ss14.service.SagaOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private LegacyOrderService legacyOrderService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SagaOrderService sagaOrderService;

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CompensationLogRepository compensationLogRepository;

    @Autowired
    private CompensationSchedulerJob compensationSchedulerJob;

    private static final String TEST_PRODUCT = "PROD-TEST";

    @BeforeEach
    void setUp() {
        inventoryClient.setStock(TEST_PRODUCT, 100);
        inventoryClient.setSimulateIncreaseStockFailure(false);
        orderRepository.setSimulateSaveFailure(false);
        compensationLogRepository.clear();
    }

    @Test
    @DisplayName("1. [Legacy Code] Tái hiện lỗi Kho treo (Hàng ảo): Trừ kho thành công nhưng DB lỗi lưu đơn -> Kho bị trừ oan, đơn không tạo")
    void testLegacyOrderService_ReproducesPhantomStockIssue() {
        // Giả lập DB timeout khi lưu đơn hàng
        orderRepository.setSimulateSaveFailure(true);

        OrderRequest request = new OrderRequest(TEST_PRODUCT, 10);
        ResponseEntity<Order> response = legacyOrderService.createOrder(request);

        // Kiểm tra: Đơn hàng trả về lỗi 500
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // LỖI NGHIỆM TRỌNG Ở ĐÂY:
        // Đơn hàng không tạo được, nhưng tồn kho vẫn bị giảm từ 100 xuống 90 (Kho treo / Hàng ảo)
        assertEquals(90, inventoryClient.getStock(TEST_PRODUCT), 
                "Tồn kho bị giảm dù đơn không được tạo -> Tái hiện thành công lỗi Hàng ảo!");
    }

    @Test
    @DisplayName("2. [Fixed OrderService - Yêu cầu b] Khắc phục bằng bù trừ trực tiếp: DB lỗi lưu đơn -> Gọi rollback cộng trả lại kho")
    void testOrderService_CompensatingTransactionSuccess() {
        // Giả lập DB timeout khi lưu đơn hàng
        orderRepository.setSimulateSaveFailure(true);

        OrderRequest request = new OrderRequest(TEST_PRODUCT, 10);
        ResponseEntity<Order> response = orderService.createOrder(request);

        // Đơn hàng trả về lỗi 500 do DB lỗi
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // ĐÃ SỬA THÀNH CÔNG:
        // Sau khi DB lỗi, cơ chế bù trừ đã gọi increaseStock(10) -> Tồn kho trở lại đúng 100!
        assertEquals(100, inventoryClient.getStock(TEST_PRODUCT), 
                "Tồn kho phải được hoàn trả về 100 sau khi lưu đơn thất bại!");
    }

    @Test
    @DisplayName("3. [Fixed OrderService] Luồng đặt hàng bình thường thành công khi không có lỗi")
    void testOrderService_SuccessFlow() {
        orderRepository.setSimulateSaveFailure(false);

        OrderRequest request = new OrderRequest(TEST_PRODUCT, 10);
        ResponseEntity<Order> response = orderService.createOrder(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PENDING", response.getBody().getStatus());

        // Kho bị trừ 10 còn 90 và đơn hàng tồn tại trong hệ thống
        assertEquals(90, inventoryClient.getStock(TEST_PRODUCT));
        assertTrue(orderRepository.findById(response.getBody().getId()).isPresent());
    }

    @Test
    @DisplayName("4. [Saga OrderService - Yêu cầu c] Bù trừ trực tiếp bị lỗi mạng/timeout -> Ghi Log State -> Job retry thành công")
    void testSagaOrderService_CompensationNetworkFailure_RecoveredByBackgroundJob() {
        // Giả lập: Lưu DB lỗi VÀ bước gọi bù trừ cũng bị Timeout mạng
        orderRepository.setSimulateSaveFailure(true);
        inventoryClient.setSimulateIncreaseStockFailure(true);

        OrderRequest request = new OrderRequest(TEST_PRODUCT, 15);
        ResponseEntity<Order> response = sagaOrderService.createOrderWithSaga(request);

        // Đơn hàng lỗi 500
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // Do bù trừ trực tiếp bị mạng timeout, kho tạm thời vẫn ở 85 (chưa hoàn trả ngay được)
        assertEquals(85, inventoryClient.getStock(TEST_PRODUCT));

        // Kiểm tra Saga Outbox / Compensation Log: Đã lưu bản ghi PENDING với retryCount = 1
        List<CompensationLog> pendingLogs = compensationLogRepository.findPendingLogs();
        assertEquals(1, pendingLogs.size(), "Phải có 1 bản ghi bù trừ PENDING trong log!");
        CompensationLog log = pendingLogs.get(0);
        assertEquals(TEST_PRODUCT, log.getProductId());
        assertEquals(15, log.getQuantity());
        assertEquals(CompensationStatus.PENDING, log.getStatus());
        assertEquals(1, log.getRetryCount());

        // GIẢ LẬP: Mạng đã phục hồi bình thường
        inventoryClient.setSimulateIncreaseStockFailure(false);

        // Kích hoạt Background Worker quét và retry
        int processedCount = compensationSchedulerJob.processPendingCompensations();
        assertEquals(1, processedCount, "Background job phải xử lý thành công 1 bản ghi bù trừ!");

        // Kiểm tra sau khi Job chạy:
        // 1. Không còn bản ghi nào ở trạng thái PENDING
        assertTrue(compensationLogRepository.findPendingLogs().isEmpty());

        // 2. Bản ghi log đã được cập nhật thành COMPLETED
        CompensationLog updatedLog = compensationLogRepository.findById(log.getId()).orElseThrow();
        assertEquals(CompensationStatus.COMPLETED, updatedLog.getStatus());

        // 3. Tồn kho đã được hoàn lại đầy đủ về 100!
        assertEquals(100, inventoryClient.getStock(TEST_PRODUCT), 
                "Tồn kho phải được hoàn trả về 100 sau khi Background Job retry bù trừ thành công!");
    }
}
