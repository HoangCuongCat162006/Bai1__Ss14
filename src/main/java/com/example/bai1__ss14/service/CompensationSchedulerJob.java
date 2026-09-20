package com.example.bai1__ss14.service;

import com.example.bai1__ss14.client.InventoryClient;
import com.example.bai1__ss14.model.CompensationLog;
import com.example.bai1__ss14.model.CompensationStatus;
import com.example.bai1__ss14.repository.CompensationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background Worker / Scheduler định kỳ quét các Compensation Log chưa hoàn thành (PENDING)
 * và thực hiện retry bù trừ kho, đảm bảo tính nhất quán cuối cùng (Eventual Consistency)
 * ngay cả khi xảy ra lỗi mạng kéo dài hoặc Inventory Service tạm thời ngừng hoạt động.
 */
@Service
public class CompensationSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(CompensationSchedulerJob.class);

    private final CompensationLogRepository compensationLogRepository;
    private final InventoryClient inventoryClient;

    public CompensationSchedulerJob(CompensationLogRepository compensationLogRepository, 
                                  InventoryClient inventoryClient) {
        this.compensationLogRepository = compensationLogRepository;
        this.inventoryClient = inventoryClient;
    }

    /**
     * Chạy định kỳ mỗi 5 giây (fixedDelay = 5000ms)
     */
    @Scheduled(fixedDelay = 5000)
    public void runCompensationJob() {
        processPendingCompensations();
    }

    /**
     * Phương thức xử lý chính, có thể gọi thủ công qua Controller hoặc Unit Test
     * @return số lượng bản ghi đã được xử lý thành công
     */
    public int processPendingCompensations() {
        List<CompensationLog> pendingLogs = compensationLogRepository.findPendingLogs();
        if (pendingLogs.isEmpty()) {
            return 0;
        }

        log.info("[CompensationWorker] Tìm thấy {} bản ghi bù trừ đang ở trạng thái PENDING. Bắt đầu xử lý...", 
                pendingLogs.size());

        int successCount = 0;

        for (CompensationLog compLog : pendingLogs) {
            log.info("[CompensationWorker] Đang retry bù trừ cho LogId={}, SagaId={}, ProductId={}, Qty={}, Lần thử={}/{}", 
                    compLog.getId(), compLog.getSagaId(), compLog.getProductId(), 
                    compLog.getQuantity(), compLog.getRetryCount() + 1, compLog.getMaxRetries());

            if (compLog.getRetryCount() >= compLog.getMaxRetries()) {
                compLog.setStatus(CompensationStatus.FAILED_PERMANENT);
                compLog.setLastErrorMessage("Vượt quá số lần thử tối đa (" + compLog.getMaxRetries() + "). Chuyển sang hàng đợi cảnh báo (DLQ/Alert)");
                compLog.setUpdatedAt(LocalDateTime.now());
                compensationLogRepository.save(compLog);

                log.error("[CompensationWorker] [CẢNH BÁO KHẨN CẤP] Bù trừ thất bại vĩnh viễn cho LogId={}. " +
                        "Cần can thiệp thủ công từ quản trị viên (Dead Letter Queue)!", compLog.getId());
                continue;
            }

            try {
                boolean success = inventoryClient.increaseStock(compLog.getProductId(), compLog.getQuantity());
                if (success) {
                    compLog.setStatus(CompensationStatus.COMPLETED);
                    compLog.setUpdatedAt(LocalDateTime.now());
                    compensationLogRepository.save(compLog);
                    successCount++;
                    log.info("[CompensationWorker] RETRY BÙ TRỪ THÀNH CÔNG cho LogId={}, SagaId={}! Đã hoàn trả kho.", 
                            compLog.getId(), compLog.getSagaId());
                } else {
                    compLog.setRetryCount(compLog.getRetryCount() + 1);
                    compLog.setLastErrorMessage("Inventory Client từ chối nhận lại hàng");
                    compLog.setUpdatedAt(LocalDateTime.now());
                    compensationLogRepository.save(compLog);
                }
            } catch (Exception ex) {
                compLog.setRetryCount(compLog.getRetryCount() + 1);
                compLog.setLastErrorMessage("Lỗi kết nối retry: " + ex.getMessage());
                compLog.setUpdatedAt(LocalDateTime.now());
                compensationLogRepository.save(compLog);

                log.warn("[CompensationWorker] Retry bù trừ cho LogId={} thất bại do: {}. Sẽ thử lại ở chu kỳ tiếp theo.", 
                        compLog.getId(), ex.getMessage());
            }
        }

        return successCount;
    }
}
