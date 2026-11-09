import { apiRequest } from "../../shared/api-client.js";
import {
    conditionText,
    escapeHtml,
    formatVnd,
    friendlyApiError
} from "./catalog-ui.js";

// Trang đăng nhập: src/vu/auth/login/login.html (tính từ src/tin/html/)
const LOGIN_URL = "../../vu/auth/login/login.html";

// api-client.js lưu token trong object "bookmooch_session" (localStorage hoặc sessionStorage)
function hasLoginSession() {
    for (const storage of [localStorage, sessionStorage]) {
        try {
            const session = JSON.parse(storage.getItem("bookmooch_session") || "null");
            if (typeof session?.token === "string") return true;
        } catch {
            // bỏ qua dữ liệu session bị hỏng
        }
    }
    return false;
}

function handleBuyNow(productId) {
    if (!hasLoginSession()) {
        alert("Bạn cần đăng nhập để thực hiện mua ngay!");
        window.location.href = LOGIN_URL;
        return;
    }

    const quantityInput = document.querySelector("#productQuantity");
    const quantity = quantityInput ? parseInt(quantityInput.value, 10) || 1 : 1;

    let checkoutSessionId;
    try {
        checkoutSessionId = createCheckoutSessionId();
        const buyNowData = {
            productId,
            quantity,
            checkoutSessionId
        };
        sessionStorage.setItem("buyNowItem", JSON.stringify(buyNowData));
        sessionStorage.removeItem("comichub_pending_payment");
    } catch (error) {
        console.error("Không thể khởi tạo phiên Mua Ngay:", error);
        alert("Không thể bắt đầu phiên mua hàng mới. Vui lòng kiểm tra bộ nhớ trình duyệt rồi thử lại.");
        return;
    }

    // Điều hướng tới payment.html cùng cấp
    window.location.href = "payment.html";
}

function createCheckoutSessionId() {
    if (!window.crypto?.getRandomValues) {
        throw new Error("Trình duyệt không hỗ trợ tạo mã phiên an toàn.");
    }
    if (window.crypto.randomUUID) {
        return window.crypto.randomUUID();
    }

    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

// Gắn sự kiện Mua Ngay ngay khi trang load, không phụ thuộc vào kết quả gọi API
function setupBuyNow(productId) {
    const buyNowBtn = document.querySelector(".btn-buy-now, .btn-primary-action, .btn-buy");
    if (!buyNowBtn) return;

    buyNowBtn.addEventListener("click", (e) => {
        // Luôn chặn thẻ <a href="payment.html"> đi thẳng, tránh sang payment khi chưa lưu dữ liệu
        e.preventDefault();

        if (!productId || !/^\d+$/.test(productId)) {
            alert("Không xác định được sản phẩm để mua. Vui lòng mở lại từ trang danh sách.");
            return;
        }
        handleBuyNow(Number(productId));
    });
}

function setText(selector, value) {
    const element = document.querySelector(selector);
    if (element) element.textContent = value || "Chưa cập nhật";
}

function setSpec(index, value) {
    const element = document.querySelectorAll(".specs-grid .spec-value")[index];
    if (element) element.textContent = value || "Chưa cập nhật";
}

async function loadProduct() {
    const id = new URLSearchParams(window.location.search).get("id");
    const main = document.querySelector(".comic-detail-section");

    setupBuyNow(id);

    if (!id || !/^\d+$/.test(id)) {
        if (main) main.innerHTML = `<p role="alert">Đường dẫn sản phẩm không hợp lệ.</p>`;
        return;
    }

    try {
        const result = await apiRequest(`/api/products/${encodeURIComponent(id)}`);
        const product = result.data;
        const images = product.images?.length
            ? product.images.map((image) => image.imageUrl)
            : product.imageUrls || [];
        const mainImage = document.querySelector(".primary-preview-img");

        document.title = `${product.title} | ComicHub`;
        setText(".comic-main-title", product.title);
        setText(".current-price", formatVnd(product.price));
        setText(".listing-id strong", `#${product.id}`);
        setText(".seller-name", product.sellerName);
        setText(".breadcrumb-item.active", product.title);
        setText(".edition-badge", product.volumeNumbers || product.editionType || "Sản phẩm truyện");
        setText(".posted-time", product.createdAt ? new Date(product.createdAt).toLocaleDateString("vi-VN") : "");
        setSpec(0, [product.publisherName, product.publicationYear].filter(Boolean).join(" • "));
        setSpec(1, product.categoryName);
        setSpec(2, product.editionType);
        setSpec(3, product.authorName);
        setSpec(4, product.volumeNumbers);
        setSpec(5, conditionText(product.conditionPercent));

        const description = document.querySelector(".synopsis-paragraph");
        if (description) description.textContent = product.description || "Người bán chưa cung cấp mô tả chi tiết.";

        if (mainImage && images.length) {
            mainImage.src = images[0];
            mainImage.alt = product.title;
            mainImage.hidden = false;
        }

        const detailSection = document.querySelector(".comic-detail-section");
        detailSection?.setAttribute("data-product-id", String(product.id));
        // Tải sản phẩm gợi ý dựa trên sản phẩm hiện tại
        await loadRecommendations(product.id);
    } catch (error) {
        if (main) main.innerHTML = `<p role="alert">${escapeHtml(friendlyApiError(error))}</p>`;
    }
}
// Tải danh sách sản phẩm gợi ý
async function loadRecommendations(productId) {
    const container = document.querySelector("#relatedProducts");

    if (!container) return;

    container.innerHTML = "<p>Đang tải sản phẩm gợi ý...</p>";

    try {
        const result = await apiRequest(
            `/api/products/${encodeURIComponent(productId)}/recommendations`
        );

        const products = result.data;

        if (!Array.isArray(products) || products.length === 0) {
            container.innerHTML = `
                <p>Hiện chưa có sản phẩm gợi ý.</p>
            `;
            return;
        }

        container.innerHTML = products.map(product => {
            const imageUrl = product.images?.length
                ? product.images[0].imageUrl
                : product.imageUrls?.[0];

            const safeImageUrl = imageUrl &&
                /^https?:\/\//i.test(imageUrl)
                ? escapeHtml(imageUrl)
                : "https://placehold.co/600x900/png?text=No+Image";

            const productUrl = new URL(window.location.href);
            productUrl.searchParams.set("id", product.id);

            let listingBadge = "";

            if (product.listingType === "SELL_AND_TRADE") {
                listingBadge = `
                    <span class="barter-badge">Bán & Đổi</span>
                `;
            } else if (product.listingType === "TRADE") {
                listingBadge = `
                    <span class="barter-badge">Trao đổi</span>
                `;
            } else {
                listingBadge = `
                    <span class="direct-sale-badge">Bán trực tiếp</span>
                `;
            }

            return `
                <article class="comic-card">
                    <a href="${escapeHtml(productUrl.href)}">
                        <div class="card-cover-wrap">
                            <img
                                src="${safeImageUrl}"
                                alt="${escapeHtml(product.title)}"
                                loading="lazy"
                            >

                            <span class="condition-tag">
                                ${escapeHtml(conditionText(product.conditionPercent))}
                            </span>
                        </div>
                    </a>

                    <div class="card-body">
                        <span class="card-category">
                            ${escapeHtml(product.categoryName || "Chưa phân loại")}
                        </span>

                        <a href="${escapeHtml(productUrl.href)}">
                            <h3 class="card-title">
                                ${escapeHtml(product.title)}
                            </h3>
                        </a>

                        <div class="card-footer">
                            <span class="card-price">
                                ${formatVnd(product.price)}
                            </span>

                            <span class="card-meta">
                                ${listingBadge}
                            </span>
                        </div>
                    </div>
                </article>
            `;
        }).join("");

    } catch (error) {
        console.error("Lỗi tải sản phẩm gợi ý:", error);

        container.innerHTML = `
            <p>Không thể tải sản phẩm gợi ý. Vui lòng thử lại.</p>
        `;
    }
}
document.addEventListener("DOMContentLoaded", loadProduct);