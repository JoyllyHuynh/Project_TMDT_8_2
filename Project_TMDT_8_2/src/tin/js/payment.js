import { apiRequest } from "../../shared/api-client.js";

document.addEventListener("DOMContentLoaded", async () => {
    // 1. Đọc dữ liệu buyNowItem từ sessionStorage
    const rawData = sessionStorage.getItem("buyNowItem");
    if (!rawData) {
        console.warn("Không tìm thấy thông tin sản phẩm đặt hàng trong sessionStorage.");
        return;
    }

    const buyNowItem = JSON.parse(rawData);
    const productId = Number(buyNowItem.productId);
    const quantity = Number(buyNowItem.quantity) || 1;

    // 2. Gọi API lấy dữ liệu thực tế từ DB
    try {
        const response = await apiRequest(`/api/products/${productId}`);
        const product = response.data;

        // Render dữ liệu lên trang payment
        renderPaymentSummary(product, quantity);

        // 3. Đăng ký sự kiện submit form đặt hàng
        setupCheckoutSubmit(productId, quantity);
    } catch (error) {
        console.error("Lỗi khi tải thông tin sản phẩm:", error);
        alert("Không thể tải thông tin sản phẩm: " + (error.message || "Vui lòng thử lại."));
    }
});

function renderPaymentSummary(product, quantity) {
    const imageUrl = (product.images && product.images.length > 0)
        ? product.images[0].imageUrl
        : (product.imageUrls && product.imageUrls.length > 0 ? product.imageUrls[0] : "https://via.placeholder.com/150");

    const unitPrice = product.price || 0;
    const subtotal = unitPrice * quantity;

    // Thay thế khối Kiện hàng thanh toán ở cột trái
    const packageCard = document.querySelector(".package-summary-card");
    if (packageCard) {
        packageCard.innerHTML = `
            <div class="card-header-bar">
                <div class="card-header-title">
                    <i class="fa-solid fa-boxes-packing"></i>
                    <h2>Kiện Hàng Thanh Toán</h2>
                </div>
                <span class="package-count-tag">SL: ${quantity} sản phẩm</span>
            </div>
            <div class="package-group-card">
                <div class="package-shop-header">
                    <div class="shop-title-wrap">
                        <i class="fa-solid fa-store"></i>
                        <span>Người bán: <strong>${escapeHtml(product.sellerName || "ComicHub Seller")}</strong></span>
                    </div>
                    <span class="trade-type-pill">Mua Trực Tiếp</span>
                </div>
                <ul class="package-items-list">
                    <li class="package-item-row">
                        <div class="package-thumb">
                            <img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(product.title)}">
                        </div>
                        <div class="package-item-details">
                            <h3 class="package-item-name">${escapeHtml(product.title)}</h3>
                            <p class="package-item-meta">${escapeHtml(product.publisherName || "NXB Kim Đồng")} • Tình trạng: ${product.conditionPercent || 100}% Like New</p>
                            <div class="package-item-tags">
                                <span class="tag-limited">Đã kiểm định</span>
                                <span class="tag-qty">SL: 0${quantity}</span>
                            </div>
                        </div>
                        <div class="package-item-price">
                            <span class="price-value">${formatVnd(subtotal)}</span>
                        </div>
                    </li>
                </ul>
            </div>
        `;
    }

    // Cập nhật Bảng tính chi phí bên cột phải cho đúng 1 sản phẩm Mua Ngay
    const summaryList = document.querySelector(".summary-breakdown-list");
    if (summaryList) {
        summaryList.innerHTML = `
            <div class="breakdown-item">
                <span class="item-label">Tạm tính tiền sách (1 món):</span>
                <span class="item-value">${formatVnd(subtotal)}</span>
            </div>
            <div class="breakdown-item">
                <span class="item-label">Đóng gói chống sốc 3 lớp:</span>
                <span class="item-value text-free">Miễn phí</span>
            </div>
            <div class="breakdown-item">
                <span class="item-label">Phí vận chuyển tiêu chuẩn:</span>
                <span class="item-value">0₫</span>
            </div>
            <div class="summary-separator-line"></div>
            <div class="total-payable-row">
                <div class="total-text-group">
                    <span class="total-title">Tổng Thanh Toán:</span>
                    <span class="total-note">Đã bao gồm VAT & Phí bảo hiểm truyện hiếm 100%</span>
                </div>
                <div class="total-price-display">
                    <span class="grand-total-amount">${formatVnd(subtotal)}</span>
                </div>
            </div>
        `;
    }

    const qrAmountEl = document.querySelector(".info-row .highlight-amount");
    if (qrAmountEl) qrAmountEl.textContent = formatVnd(subtotal);
}

function setupCheckoutSubmit(productId, quantity) {
    const submitBtn = document.querySelector(".btn-submit-checkout");
    if (!submitBtn) return;

    submitBtn.addEventListener("click", async (e) => {
        e.preventDefault();

        const selectedPayment = document.querySelector('input[name="payment_method"]:checked')?.value || "vnpay";

        // Lấy tên, SĐT, địa chỉ và đảm bảo không bao giờ bị rỗng
        let rawPhone = document.querySelector(".recipient-phone")?.textContent.trim() || "0988123456";
        let cleanPhone = rawPhone.replace(/\D/g, "");
        if (!cleanPhone) cleanPhone = "0988123456"; // Fallback nếu lọc xong bị rỗng

        let recipientName = document.querySelector(".recipient-name")?.textContent.trim() || "Nguyễn An Vinh";
        let shippingAddress = document.querySelector(".recipient-address")?.textContent.trim() || "124 Hoàng Hoa Thám, Phường Liễu Giai, Quận Ba Đình, Hà Nội";

        const checkoutPayload = {
            productId: Number(productId),
            quantity: Number(quantity),
            recipientName: recipientName,
            recipientPhone: cleanPhone,
            shippingAddress: shippingAddress,
            buyerNote: "Hàng truyện tranh sưu tầm dễ móp gáy, vui lòng nhẹ tay.",
            shippingMethod: "STANDARD",
            shippingFee: 0,
            paymentMethod: selectedPayment.toUpperCase() === "COD" ? "COD" : "VNPAY",
            idempotencyKey: window.crypto?.randomUUID ? window.crypto.randomUUID() : String(Date.now())
        };

        console.log("Payload gửi sang Backend:", checkoutPayload);

        try {
            submitBtn.disabled = true;
            submitBtn.querySelector("span").textContent = "Đang xử lý...";

            const res = await apiRequest("/api/orders/buy-now", {
                method: "POST",
                body: checkoutPayload
            });

            alert("Đặt hàng Mua Ngay thành công! Mã đơn: " + (res.data?.orderCode || ""));
            sessionStorage.removeItem("buyNowItem");
            window.location.href = "../../thuc/html/page3.html";
        } catch (err) {
            console.error("Lỗi đặt hàng chi tiết:", err);
            alert("Đặt hàng thất bại: " + (err.message || "Vui lòng kiểm tra lại trạng thái đăng nhập."));
            submitBtn.disabled = false;
            submitBtn.querySelector("span").textContent = "Xác Nhận Thanh Toán";
        }
    });
}

function formatVnd(amount) {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount).replace("₫", "") + "₫";
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}