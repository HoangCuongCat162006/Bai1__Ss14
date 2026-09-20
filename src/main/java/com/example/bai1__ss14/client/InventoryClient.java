package com.example.bai1__ss14.client;

public interface InventoryClient {
    /**
     * Giảm tồn kho cho sản phẩm
     * @param productId Mã sản phẩm
     * @param quantity Số lượng cần giảm
     * @return true nếu giảm thành công, false nếu hết hàng hoặc lỗi nghiệp vụ
     */
    boolean decreaseStock(String productId, int quantity);

    /**
     * Bù trừ (hoàn lại) tồn kho khi luồng tạo đơn thất bại
     * @param productId Mã sản phẩm
     * @param quantity Số lượng cần cộng trả
     * @return true nếu cộng trả thành công, false nếu thất bại
     */
    boolean increaseStock(String productId, int quantity);

    /**
     * Lấy số lượng tồn kho hiện tại (dùng để kiểm tra và kiểm thử)
     */
    int getStock(String productId);

    /**
     * Cập nhật số lượng tồn ban đầu cho kiểm thử
     */
    void setStock(String productId, int quantity);

    /**
     * Bật/tắt cờ giả lập lỗi mạng/timeout khi gọi increaseStock
     */
    void setSimulateIncreaseStockFailure(boolean simulateFailure);

    /**
     * Kiểm tra trạng thái cờ lỗi
     */
    boolean isSimulateIncreaseStockFailure();
}
