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

    const buyNowData = {
        productId: productId,
        quantity: quantity
    };
    sessionStorage.setItem("buyNowItem", JSON.stringify(buyNowData));

    // Điều hướng tới payment.html cùng cấp
    window.location.href = "payment.html";
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
    } catch (error) {
        if (main) main.innerHTML = `<p role="alert">${escapeHtml(friendlyApiError(error))}</p>`;
    }
}

document.addEventListener("DOMContentLoaded", loadProduct);