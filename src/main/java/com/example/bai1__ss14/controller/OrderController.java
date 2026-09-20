package com.example.bai1__ss14.controller;

import com.example.bai1__ss14.client.InventoryClient;
import com.example.bai1__ss14.model.CompensationLog;
import com.example.bai1__ss14.model.Order;
import com.example.bai1__ss14.model.OrderRequest;
import com.example.bai1__ss14.repository.CompensationLogRepository;
import com.example.bai1__ss14.repository.OrderRepository;
import com.example.bai1__ss14.service.CompensationSchedulerJob;
import com.example.bai1__ss14.service.LegacyOrderService;
import com.example.bai1__ss14.service.OrderService;
import com.example.bai1__ss14.service.SagaOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final LegacyOrderService legacyOrderService;
    private final OrderService orderService;
    private final SagaOrderService sagaOrderService;
    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final CompensationLogRepository compensationLogRepository;
    private final CompensationSchedulerJob compensationSchedulerJob;

    public OrderController(LegacyOrderService legacyOrderService,
                           OrderService orderService,
                           SagaOrderService sagaOrderService,
                           InventoryClient inventoryClient,
                           OrderRepository orderRepository,
                           CompensationLogRepository compensationLogRepository,
                           CompensationSchedulerJob compensationSchedulerJob) {
        this.legacyOrderService = legacyOrderService;
        this.orderService = orderService;
        this.sagaOrderService = sagaOrderService;
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
        this.compensationLogRepository = compensationLogRepository;
        this.compensationSchedulerJob = compensationSchedulerJob;
    }

    /**
     * 1. Luồng cũ (Legacy): Trừ kho nhưng không rollback khi lưu đơn lỗi -> Gây ra lỗi "Hàng ảo"
     */
    @PostMapping("/orders/legacy")
    public ResponseEntity<Order> createOrderLegacy(@RequestBody OrderRequest request) {
        return legacyOrderService.createOrder(request);
    }

    /**
     * 2. Luồng đã sửa theo Yêu cầu (b): Bù trừ trực tiếp (Compensating Transaction)
     */
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrderFixed(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }

    /**
     * 3. Luồng nâng cao theo Yêu cầu (c): Saga Pattern + State Log + Background Retry Job
     */
    @PostMapping("/orders/saga")
    public ResponseEntity<Order> createOrderSaga(@RequestBody OrderRequest request) {
        return sagaOrderService.createOrderWithSaga(request);
    }

    /**
     * Lấy thông tin tồn kho của sản phẩm
     */
    @GetMapping("/inventory/{productId}")
    public ResponseEntity<Map<String, Object>> getInventory(@PathVariable String productId) {
        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("stock", inventoryClient.getStock(productId));
        return ResponseEntity.ok(result);
    }

    /**
     * Đặt lại số lượng tồn kho
     */
    @PostMapping("/inventory/{productId}")
    public ResponseEntity<Map<String, Object>> setInventory(@PathVariable String productId, @RequestParam int stock) {
        inventoryClient.setStock(productId, stock);
        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("stock", inventoryClient.getStock(productId));
        result.put("message", "Đã cập nhật tồn kho thành công");
        return ResponseEntity.ok(result);
    }

    /**
     * Lấy danh sách tất cả đơn hàng đã tạo thành công trong DB
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    /**
     * Lấy danh sách các bản ghi bù trừ (Compensation Logs / Saga State)
     */
    @GetMapping("/compensation-logs")
    public ResponseEntity<List<CompensationLog>> getCompensationLogs() {
        return ResponseEntity.ok(compensationLogRepository.findAll());
    }

    /**
     * Kích hoạt thủ công Background Compensation Job
     */
    @PostMapping("/compensation/trigger-job")
    public ResponseEntity<Map<String, Object>> triggerCompensationJob() {
        int processed = compensationSchedulerJob.processPendingCompensations();
        Map<String, Object> res = new HashMap<>();
        res.put("message", "Job đã chạy xong");
        res.put("compensatedCount", processed);
        return ResponseEntity.ok(res);
    }

    /**
     * Cấu hình giả lập sự cố (Database Timeout hoặc Network Timeout)
     */
    @PostMapping("/config/simulate")
    public ResponseEntity<Map<String, Object>> configureSimulation(
            @RequestParam(required = false) Boolean failSaveOrder,
            @RequestParam(required = false) Boolean failCompensate) {

        if (failSaveOrder != null) {
            orderRepository.setSimulateSaveFailure(failSaveOrder);
        }
        if (failCompensate != null) {
            inventoryClient.setSimulateIncreaseStockFailure(failCompensate);
        }

        Map<String, Object> status = new HashMap<>();
        status.put("simulateSaveOrderFailure", orderRepository.isSimulateSaveFailure());
        status.put("simulateIncreaseStockFailure", inventoryClient.isSimulateIncreaseStockFailure());
        return ResponseEntity.ok(status);
    }
}
