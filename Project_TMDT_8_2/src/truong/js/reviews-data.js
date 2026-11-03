import { apiRequest } from "../../shared/api-client.js";

const container = document.getElementById("reviewsListContainer");

let reviews = [];

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    })[character]);
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "—";
    return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
}

function dateKey(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${date.getFullYear()}-${month}-${day}`;
}

function starsHtml(rating) {
    return Array.from({ length: 5 }, (_, index) => index < rating
        ? `<span class="material-symbols-outlined fill">star</span>`
        : `<span class="material-symbols-outlined" style="color:#cbd5e1;">star</span>`).join("");
}

function notify(message, type = "info") {
    if (typeof window.showToast === "function") {
        window.showToast(message, type);
        return;
    }
    const toast = document.getElementById("toastContainer");
    if (toast) toast.textContent = message;
}

function gallery(review) {
    const images = review.images || [];
    if (!images.length) return "";
    return `
        <div class="review-photo-gallery">
            ${images.map((url) => `<img src="${escapeHtml(url)}" alt="Ảnh đánh giá" class="review-photo-thumb" loading="lazy">`).join("")}
        </div>`;
}

function replyBox(review) {
    if (!review.reply) return "";
    return `
        <div class="shop-reply-box">
            <div class="shop-reply-header">
                <div class="shop-reply-author">
                    <span class="material-symbols-outlined" style="font-size:16px;">storefront</span>
                    Phản hồi từ Shop: ${escapeHtml(review.reply.sellerName)}
                </div>
                <span class="shop-reply-time">${escapeHtml(formatDate(review.reply.createdAt))}</span>
            </div>
            <p class="shop-reply-text">${escapeHtml(review.reply.content)}</p>
        </div>`;
}

function keepReplyButton(review) {
    if (review.reply) return "";
    return `
        <div style="display:flex;align-items:center;justify-content:space-between;margin-top:14px;gap:10px;flex-wrap:wrap;">
            <button class="btn btn-outline btn-sm" data-reply-review="${review.id}">
                <span class="material-symbols-outlined" style="font-size:16px;">reply</span> Phản hồi đánh giá
            </button>
            <span class="badge badge-danger" style="font-size:.75rem;"><span class="badge-dot"></span> Chưa phản hồi</span>
        </div>`;
}

function card(review) {
    return `
        <div class="review-card" data-stars="${review.rating}" data-book="${escapeHtml(review.productTitle)}" data-date="${dateKey(review.createdAt)}" data-has-photo="${(review.images || []).length > 0}" data-replied="${Boolean(review.reply)}">
            <div class="review-card-header">
                <div class="reviewer-profile">
                    <div class="reviewer-avatar" style="display:flex;align-items:center;justify-content:center;background:#ffedd5;color:#c2410c;font-weight:800;">${escapeHtml((review.buyerName || "?").slice(0, 1).toUpperCase())}</div>
                    <div class="reviewer-info">
                        <h4>${escapeHtml(review.buyerName)} <span class="badge badge-success" style="font-size:.72rem;padding:2px 7px;">Đã mua hàng</span></h4>
                        <div class="meta-time">${escapeHtml(formatDate(review.createdAt))} • Đơn hàng #${escapeHtml(review.orderCode)}</div>
                    </div>
                </div>
            </div>
            <div class="review-stars">${starsHtml(review.rating)}</div>
            <div class="review-product-attached">
                <span class="material-symbols-outlined" style="font-size:16px;">menu_book</span>
                ${escapeHtml(review.productTitle)} &nbsp;|&nbsp; Tình trạng niêm yết: ${review.conditionPercent}%
            </div>
            <p class="review-content-text">${escapeHtml(review.content)}</p>
            ${gallery(review)}
            ${replyBox(review)}
            ${keepReplyButton(review)}
        </div>`;
}

function updateSummary() {
    const total = reviews.length;
    const withPhoto = reviews.filter((review) => (review.images || []).length > 0).length;
    const average = total ? reviews.reduce((sum, review) => sum + review.rating, 0) / total : 0;
    const score = document.querySelector(".overall-score .score-num");
    if (score) {
        score.innerHTML = `${average.toFixed(1)}<span style="font-size:1.8rem;color:#94a3b8;font-weight:500;">/5.0</span>`;
    }
    const count = document.querySelector(".overall-score .score-count");
    if (count) {
        count.innerHTML = `Dựa trên <strong>${total}</strong> lượt đánh giá từ khách mua truyện`;
    }

    document.querySelectorAll(".rating-bars .rating-bar-row").forEach((row, index) => {
        const star = 5 - index;
        const starCount = reviews.filter((review) => review.rating === star).length;
        const percent = total ? Math.round((starCount * 100) / total) : 0;
        const fill = row.querySelector(".progress-fill");
        const label = row.querySelector(".percent-label");
        if (fill) fill.style.width = `${percent}%`;
        if (label) label.textContent = `${percent}%`;
    });

    const unreplied = reviews.filter((review) => !review.reply).length;
    document.querySelectorAll("#starFilterTabs [data-filter-star]").forEach((button) => {
        const key = button.dataset.filterStar;
        if (key === "with_photo") {
            button.style.display = "";
            button.textContent = `Có ảnh (${withPhoto})`;
            return;
        }
        if (key === "unreplied") {
            button.textContent = `Chưa phản hồi (${unreplied})`;
            return;
        }
        const starCount = key === "all" ? total : reviews.filter((review) => review.rating === Number(key)).length;
        button.textContent = `${key === "all" ? "Tất cả" : `${key} Sao ⭐`} (${starCount})`;
    });

    const badge = document.getElementById("reviewMatchCountBadge");
    if (badge) badge.textContent = `Hiển thị: ${total} / ${total} đánh giá`;

    const bookSelect = document.getElementById("reviewBookSelect");
    if (bookSelect) {
        const titles = [...new Set(reviews.map((review) => review.productTitle))];
        bookSelect.innerHTML = `<option value="all">Tất cả các tựa sách truyện</option>`
            + titles.map((title) => `<option value="${escapeHtml(title)}">${escapeHtml(title)}</option>`).join("");
    }

    const loadMore = document.getElementById("loadMoreReviewsBtn");
    if (loadMore && loadMore.parentElement) loadMore.parentElement.style.display = "none";
    document.querySelectorAll(".pagination-wrapper").forEach((element) => {
        element.style.display = "none";
    });
}

function openReplyForm(reviewId) {
    const review = reviews.find((entry) => Number(entry.id) === Number(reviewId));
    if (!review) return;
    const cardElement = container.querySelector(`[data-reply-review="${reviewId}"]`)?.closest(".review-card");
    if (!cardElement || cardElement.querySelector(".inline-reply-container")) return;

    const form = document.createElement("div");
    form.className = "inline-reply-container open";
    form.innerHTML = `
        <div style="font-weight:700;font-size:.85rem;margin-bottom:6px;">Phản hồi công khai tới người mua:</div>
        <textarea class="inline-reply-textarea" maxlength="1000" placeholder="Cảm ơn hoặc hướng giải quyết cho người mua..."></textarea>
        <p class="reply-error" hidden style="color:#b91c1c;font-size:.8rem;margin:6px 0 0;"></p>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px;">
            <button type="button" class="btn btn-outline btn-sm" data-cancel-reply>Hủy</button>
            <button type="button" class="btn btn-primary btn-sm" data-send-reply="${review.id}">Gửi phản hồi</button>
        </div>`;
    cardElement.appendChild(form);
    form.querySelector("textarea").focus();
}

async function sendReply(button) {
    const form = button.closest(".inline-reply-container");
    const textarea = form.querySelector("textarea");
    const error = form.querySelector(".reply-error");
    const content = textarea.value.trim();

    if (!content) {
        error.textContent = "Vui lòng nhập nội dung phản hồi.";
        error.hidden = false;
        return;
    }

    button.disabled = true;
    try {
        await apiRequest(`/api/reviews/${encodeURIComponent(button.dataset.sendReply)}/reply`, {
            method: "POST",
            body: { content }
        });
        notify("Đã gửi phản hồi tới người mua.", "success");
        await load();
    } catch (requestError) {
        error.textContent = requestError.message;
        error.hidden = false;
        button.disabled = false;
    }
}

function render() {
    if (!container) return;
    if (!reviews.length) {
        container.innerHTML = "";
        const empty = document.getElementById("noReviewsFound");
        if (empty) empty.style.display = "block";
        updateSummary();
        return;
    }
    container.innerHTML = reviews.map(card).join("");
    updateSummary();
    if (typeof window.filterReviewsCombined === "function") window.filterReviewsCombined();
}

async function load() {
    try {
        const response = await apiRequest("/api/reviews/seller");
        reviews = response.data || [];
        render();
    } catch (error) {
        if (container) {
            container.innerHTML = `<div class="review-card"><p class="review-content-text">${escapeHtml(error.message)}</p></div>`;
        }
        notify(error.message, "danger");
    }
}

container?.addEventListener("click", (event) => {
    const replyButton = event.target.closest("[data-reply-review]");
    if (replyButton) {
        openReplyForm(replyButton.dataset.replyReview);
        return;
    }
    const cancelButton = event.target.closest("[data-cancel-reply]");
    if (cancelButton) {
        cancelButton.closest(".inline-reply-container")?.remove();
        return;
    }
    const sendButton = event.target.closest("[data-send-reply]");
    if (sendButton) sendReply(sendButton);
});

load();
