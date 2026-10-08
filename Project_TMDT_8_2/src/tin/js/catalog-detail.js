import { apiRequest } from "../../shared/api-client.js";
import {
    conditionText,
    escapeHtml,
    formatVnd,
    friendlyApiError
} from "./catalog-ui.js";

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
        const imageContainer = document.querySelector(".aspect-container");
        const thumbnails = document.querySelector(".thumbnail-strip");

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
        document.querySelector(".ai-inspection-card")?.remove();
        document.querySelector(".tag-live-viewers")?.remove();
        document.querySelector(".seller-stats-row")?.remove();
        document.querySelector(".seller-location")?.remove();
        document.querySelector(".original-price")?.remove();
        document.querySelector(".discount-badge")?.remove();

        const tradeSection = document.querySelector(".barter-offer-card");
        const exchangeButton = document.querySelector(".btn-propose-exchange");
        const isTrade = product.listingType === "TRADE";
        if (tradeSection) tradeSection.hidden = !isTrade;
        if (exchangeButton) exchangeButton.hidden = !isTrade;
        const barterDescription = document.querySelector(".barter-description");
        if (barterDescription && product.tradeWishNote) barterDescription.textContent = product.tradeWishNote;

        if (mainImage) {
            if (images.length) {
                mainImage.src = images[0];
                mainImage.alt = product.title;
                mainImage.hidden = false;
                imageContainer?.querySelector(".catalog-empty-image")?.remove();
            } else {
                mainImage.removeAttribute("src");
                mainImage.hidden = true;
                if (imageContainer) {
                    const placeholder = document.createElement("p");
                    placeholder.className = "catalog-empty-image";
                    placeholder.textContent = "Người bán chưa cung cấp ảnh sản phẩm.";
                    imageContainer.prepend(placeholder);
                }
            }
        }

        if (thumbnails) {
            thumbnails.replaceChildren(...images.map((url, index) => {
                const button = document.createElement("button");
                const image = document.createElement("img");
                button.type = "button";
                button.className = `thumb-item${index === 0 ? " active" : ""}`;
                image.src = url;
                image.alt = `${product.title} - ảnh ${index + 1}`;
                button.append(image);
                button.addEventListener("click", () => {
                    if (mainImage) mainImage.src = url;
                    thumbnails.querySelectorAll(".thumb-item").forEach((item) => item.classList.toggle("active", item === button));
                });
                return button;
            }));
            thumbnails.hidden = !images.length;
        }

        const sellerShelf = document.querySelector(".btn-view-shelf");
        if (sellerShelf) sellerShelf.href = `category.html?seller=${encodeURIComponent(product.sellerName || "")}`;
        const detailSection = document.querySelector(".comic-detail-section");
        detailSection?.setAttribute("data-product-id", String(product.id));
    } catch (error) {
        if (main) main.innerHTML = `<p role="alert">${escapeHtml(friendlyApiError(error))}</p>`;
    }
}

document.addEventListener("DOMContentLoaded", loadProduct);
