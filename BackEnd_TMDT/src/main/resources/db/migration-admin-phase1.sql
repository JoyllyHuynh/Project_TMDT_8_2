-- Migration cho các cột mới của phân hệ Admin (người dùng / sản phẩm / đơn hàng).
-- Chạy thủ công trên database comicworm (ddl-auto = none nên Spring không tự thêm cột).
-- Script chỉ thêm cột, không xoá hay cập nhật dữ liệu hiện có.

USE comicworm;

-- 1) Người dùng: lý do khoá tài khoản + yêu cầu trở thành người bán đang chờ admin duyệt.
ALTER TABLE users
    ADD COLUMN account_status_reason TEXT NULL;

ALTER TABLE users
    ADD COLUMN account_status_changed_at DATETIME NULL;

ALTER TABLE users
    ADD COLUMN seller_requested_at DATETIME NULL;

ALTER TABLE users
    ADD COLUMN seller_request_status VARCHAR(20) NULL;

ALTER TABLE users
    ADD COLUMN seller_request_note TEXT NULL;

ALTER TABLE users
    ADD COLUMN seller_request_resolved_at DATETIME NULL;

-- 2) Đơn hàng: mở / xử lý tranh chấp từ Admin Console
ALTER TABLE orders
    ADD COLUMN dispute_reason TEXT NULL;

ALTER TABLE orders
    ADD COLUMN disputed_at DATETIME NULL;

ALTER TABLE orders
    ADD COLUMN dispute_favor VARCHAR(10) NULL;

ALTER TABLE orders
    ADD COLUMN dispute_note TEXT NULL;

ALTER TABLE orders
    ADD COLUMN dispute_resolved_at DATETIME NULL;

ALTER TABLE orders
    ADD CONSTRAINT ck_order_dispute_favor
        CHECK (dispute_favor IS NULL OR dispute_favor IN ('BUYER', 'SELLER'));

-- 3) Sửa lỗi chặn tạo đơn: entity Order cho phép checkout_id NULL (luồng Mua ngay),
--    nhưng cột trong DB đang NOT NULL nên mọi lệnh đặt hàng đều bị từ chối.
ALTER TABLE orders
    MODIFY COLUMN checkout_id BIGINT NULL;
