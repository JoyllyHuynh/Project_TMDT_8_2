import { apiRequest, uploadFile } from "../../shared/api-client.js";

const STAR_LABELS = {
    1: "Rất tệ",
    2: "Không hài lòng",
    3: "Bình thường",
    4: "Tốt",
    5: "Tuyệt vời"
};

const MAX_IMAGES = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

const els = {
    reviewableList: document.getElementById("reviewableList"),
    reviewableCount: document.getElementById("reviewableCount"),
    formPanel: document.getElementById("reviewFormPanel"),
    itemTitle: document.getElementById("reviewItemTitle"),
    itemMeta: document.getElementById("reviewItemMeta"),
    starInput: document.getElementById("starInput"),
    starLabel: document.getElementById("starLabel"),
    content: document.getElementById("reviewContent"),
    counter: document.getElementById("reviewCounter"),
    error: document.getElementById("reviewError"),
    submit: document.getElementById("submitReview"),
    cancel: document.getElementById("cancelReview"),
    myReviewList: document.getElementById("myReviewList"),
    imageInput: document.getElementById("reviewImages"),
    imageDrop: document.getElementById("imageDrop"),
    imagePreviews: document.getElementById("imagePreviews"),
    imageHint: document.getElementById("imageHint"),
    imageCounter: document.getElementById("imageCounter")
};

let reviewableItems = [];
let selectedItem = null;
let rating = 0;
let selectedFiles = [];
let previewUrls = [];

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    })[character]);
}

function formatVnd(value) {
    return `${new Intl.NumberFormat("vi-VN").format(Number(value) || 0)} ₫`;
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "—";
    return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
}

function showToast(message, type = "info") {
    const toast = document.getElementById("orderToast");
    if (!toast) return;
    toast.textContent = message;
    toast.className = `order-toast show ${type}`;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
        toast.className = "order-toast";
    }, 4000);
}

function galleryMarkup(images) {
    const urls = images || [];
    if (!urls.length) return "";
    return `<div class="gallery">${urls.map((url) => `
        <a href="${escapeHtml(url)}" target="_blank" rel="noopener">
            <img src="${escapeHtml(url)}" alt="Ảnh đánh giá" loading="lazy">
        </a>`).join("")}</div>`;
}

function renderReviewable() {
    els.reviewableCount.textContent = `(${reviewableItems.length})`;
    els.reviewableList.innerHTML = reviewableItems.length
        ? reviewableItems.map((item) => `
            <article class="reviewable">
                <div class="reviewable-main">
                    ${item.imageUrl
                        ? `<img src="${escapeHtml(item.imageUrl)}" alt="${escapeHtml(item.productTitle)}">`
                        : `<span class="cover-fallback">📚</span>`}
                    <div>
                        <strong>${escapeHtml(item.productTitle)}</strong>
                        <p>Đơn #${escapeHtml(item.orderCode)} · Người bán: ${escapeHtml(item.sellerName)}</p>
                        <p>Tình trạng niêm yết ${item.conditionPercent}% · ${formatVnd(item.unitPrice)} × ${item.quantity} · Giao ngày ${escapeHtml(formatDate(item.deliveredAt))}</p>
                    </div>
                </div>
                <button type="button" class="btn btn-primary" data-review="${item.id}">Đánh giá ngay</button>
            </article>`).join("")
        : `<p class="empty">Không có sản phẩm nào chờ đánh giá. Đơn hàng cần ở trạng thái đã giao hoặc hoàn tất.</p>`;
}

function renderMyReviews(reviews) {
    els.myReviewList.innerHTML = reviews.length
        ? reviews.map((review) => `
            <article class="review-row">
                <div class="review-row-head">
                    <strong>${escapeHtml(review.productTitle)}</strong>
                    <span class="stars-static">${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}</span>
                </div>
                <p>${escapeHtml(review.content)}</p>
                ${galleryMarkup(review.images)}
                <p class="muted">Đơn #${escapeHtml(review.orderCode)} · ${escapeHtml(formatDate(review.createdAt))}</p>
                ${review.reply
                    ? `<div class="reply"><strong>${escapeHtml(review.reply.sellerName)} phản hồi:</strong> ${escapeHtml(review.reply.content)}</div>`
                    : `<p class="muted">Người bán chưa phản hồi.</p>`}
            </article>`).join("")
        : `<p class="empty">Bạn chưa gửi đánh giá nào.</p>`;
}

function setRating(value) {
    rating = value;
    els.starInput.querySelectorAll("button").forEach((button) => {
        button.classList.toggle("active", Number(button.dataset.star) <= value);
        button.setAttribute("aria-pressed", String(Number(button.dataset.star) === value));
    });
    els.starLabel.textContent = value ? `${value}/5 — ${STAR_LABELS[value]}` : "Chọn số sao";
}

function renderPreviews() {
    previewUrls.forEach((url) => URL.revokeObjectURL(url));
    previewUrls = selectedFiles.map((file) => URL.createObjectURL(file));
    els.imagePreviews.innerHTML = previewUrls.map((url, index) => `
        <div class="preview">
            <img src="${url}" alt="Ảnh ${index + 1}">
            <button type="button" data-remove-image="${index}" aria-label="Xóa ảnh ${index + 1}">×</button>
        </div>`).join("");
    els.imageCounter.textContent = `${selectedFiles.length}/${MAX_IMAGES}`;
}

function clearImages() {
    selectedFiles = [];
    previewUrls.forEach((url) => URL.revokeObjectURL(url));
    previewUrls = [];
    els.imagePreviews.replaceChildren();
    els.imageInput.value = "";
    els.imageHint.textContent = "";
    els.imageCounter.textContent = `0/${MAX_IMAGES}`;
}

function addFiles(fileList) {
    const rejected = [];
    for (const file of Array.from(fileList)) {
        if (selectedFiles.length >= MAX_IMAGES) {
            rejected.push(`Chỉ chọn tối đa ${MAX_IMAGES} ảnh.`);
            break;
        }
        if (!file.type.startsWith("image/")) {
            rejected.push(`${file.name} không phải tệp ảnh.`);
            continue;
        }
        if (file.size > MAX_IMAGE_BYTES) {
            rejected.push(`${file.name} vượt quá 5MB.`);
            continue;
        }
        selectedFiles.push(file);
    }
    renderPreviews();
    els.imageHint.textContent = rejected.length ? rejected[0] : "";
}

function openForm(itemId) {
    const item = reviewableItems.find((entry) => Number(entry.id) === Number(itemId));
    if (!item) return;
    selectedItem = item;
    rating = 0;
    setRating(0);
    els.itemTitle.textContent = item.productTitle;
    els.itemMeta.textContent = `Đơn #${item.orderCode} · ${item.sellerName} · Tình trạng niêm yết ${item.conditionPercent}%`;
    els.content.value = "";
    els.counter.textContent = "0/1000";
    els.error.hidden = true;
    clearImages();
    els.formPanel.hidden = false;
    els.formPanel.scrollIntoView({ behavior: "smooth", block: "start" });
}

function closeForm() {
    selectedItem = null;
    els.formPanel.hidden = true;
    els.content.value = "";
    els.counter.textContent = "0/1000";
    els.error.hidden = true;
    clearImages();
    setRating(0);
}

async function load() {
    try {
        const [reviewableResponse, mineResponse] = await Promise.all([
            apiRequest("/api/reviews/reviewable"),
            apiRequest("/api/reviews/mine")
        ]);
        reviewableItems = reviewableResponse.data || [];
        renderReviewable();
        renderMyReviews(mineResponse.data || []);

        const preselect = Number(new URLSearchParams(window.location.search).get("orderItemId"));
        if (preselect) openForm(preselect);
    } catch (error) {
        if (error.status === 401) {
            window.location.href = "../../vu/auth/login/login.html";
            return;
        }
        els.reviewableList.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
        els.myReviewList.innerHTML = "";
        showToast(error.message, "error");
    }
}

els.reviewableList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-review]");
    if (button) openForm(button.dataset.review);
});

els.starInput.addEventListener("click", (event) => {
    const button = event.target.closest("[data-star]");
    if (button) setRating(Number(button.dataset.star));
});

els.starInput.addEventListener("mouseover", (event) => {
    const button = event.target.closest("[data-star]");
    if (!button) return;
    const preview = Number(button.dataset.star);
    els.starInput.querySelectorAll("button").forEach((item) => {
        item.classList.toggle("active", Number(item.dataset.star) <= preview);
    });
});

els.starInput.addEventListener("mouseleave", () => setRating(rating));

els.content.addEventListener("input", () => {
    els.counter.textContent = `${els.content.value.length}/1000`;
});

els.imageDrop.addEventListener("click", () => els.imageInput.click());
els.imageDrop.addEventListener("keydown", (event) => {
    if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        els.imageInput.click();
    }
});
els.imageDrop.addEventListener("dragover", (event) => {
    event.preventDefault();
    els.imageDrop.classList.add("dragging");
});
els.imageDrop.addEventListener("dragleave", () => els.imageDrop.classList.remove("dragging"));
els.imageDrop.addEventListener("drop", (event) => {
    event.preventDefault();
    els.imageDrop.classList.remove("dragging");
    addFiles(event.dataTransfer.files);
});
els.imageInput.addEventListener("change", () => {
    addFiles(els.imageInput.files);
    els.imageInput.value = "";
});
els.imagePreviews.addEventListener("click", (event) => {
    const button = event.target.closest("[data-remove-image]");
    if (!button) return;
    selectedFiles.splice(Number(button.dataset.removeImage), 1);
    renderPreviews();
});

els.cancel.addEventListener("click", closeForm);

els.submit.addEventListener("click", async () => {
    if (!selectedItem) return;
    if (!rating) {
        els.error.textContent = "Vui lòng chọn số sao đánh giá.";
        els.error.hidden = false;
        return;
    }
    if (!els.content.value.trim()) {
        els.error.textContent = "Vui lòng viết nhận xét về tình trạng thực tế của truyện.";
        els.error.hidden = false;
        return;
    }

    const orderItemId = selectedItem.id;
    const content = els.content.value.trim();
    els.submit.disabled = true;
    els.submit.textContent = selectedFiles.length ? "Đang tải ảnh..." : "Đang gửi...";
    try {
        const imageUrls = [];
        for (const file of selectedFiles) {
            const uploaded = await uploadFile("/api/reviews/images", file);
            if (uploaded.data) imageUrls.push(uploaded.data);
        }
        await apiRequest("/api/reviews", {
            method: "POST",
            body: { orderItemId, rating, content, imageUrls }
        });
        closeForm();
        showToast("Đã gửi đánh giá. Cảm ơn bạn đã chia sẻ trải nghiệm.", "success");
        await load();
    } catch (error) {
        if (error.status === 401) {
            window.location.href = "../../vu/auth/login/login.html";
            return;
        }
        els.error.textContent = error.message;
        els.error.hidden = false;
    } finally {
        els.submit.disabled = false;
        els.submit.textContent = "Gửi đánh giá";
    }
});

load();
