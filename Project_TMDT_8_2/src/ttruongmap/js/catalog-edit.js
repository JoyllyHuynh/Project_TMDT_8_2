import { apiRequest } from "../../shared/api-client.js";
import { escapeHtml, friendlyApiError } from "../../tin/js/catalog-ui.js";

let products = [];
let catalogs = { categories: [], authors: [], publishers: [] };
let saving = false;

function normalize(value) {
    return String(value || "").normalize("NFD").replace(/\p{Diacritic}/gu, "").toLocaleLowerCase("vi");
}

function showMessage(message, isError = false) {
    let status = document.getElementById("catalogEditStatus");
    if (!status) {
        status = document.createElement("p");
        status.id = "catalogEditStatus";
        status.setAttribute("role", "status");
        document.querySelector(".content-body")?.prepend(status);
    }
    status.textContent = message;
    status.style.color = isError ? "#b91c1c" : "#166534";
}

function populateSelect(id, list, selectedId, selectedName) {
    const select = document.getElementById(id);
    if (!select) return;
    select.innerHTML = `<option value="">Chọn mục</option>${list.map((item) =>
        `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`
    ).join("")}`;
    const match = list.find((item) => String(item.id) === String(selectedId))
        || list.find((item) => normalize(item.name) === normalize(selectedName));
    if (match) select.value = String(match.id);
}

function showProduct(product) {
    if (!product) return;
    document.getElementById("editPostId").value = String(product.id);
    document.getElementById("editTitle").value = product.title || "";
    populateSelect("editAuthor", catalogs.authors, product.authorId, product.authorName);
    populateSelect("editCategory", catalogs.categories, product.categoryId, product.categoryName);
    document.getElementById("editVolume").value = product.volumeNumbers || "";
    populateSelect("editPublisher", catalogs.publishers, product.publisherId, product.publisherName);
    document.getElementById("editYear").value = product.publicationYear || "";
    const condition = Number(product.conditionPercent);
    document.getElementById("editCondition").value = condition >= 100
        ? "Mới 100% Nguyên Seal"
        : condition >= 98 ? "Like New 99% (Đã đọc 1 lần)"
            : condition >= 95 ? "95% Giữ gìn cẩn thận"
                : condition >= 90 ? "90% Giấy ngả vàng nhẹ thời gian"
                    : "Truyện cũ sưu tầm 80%";
    document.getElementById("editPrice").value = product.price ?? "";
    document.getElementById("editStock").value = product.stockQuantity ?? 0;
    const imageUrl = product.images?.[0]?.imageUrl || product.imageUrls?.[0] || "";
    document.getElementById("editImageUrl").value = imageUrl;
    document.getElementById("editImagePreview").src = imageUrl;
    document.getElementById("editStatus").value = !product.active ? "hidden"
        : product.moderationStatus === "PENDING" ? "pending" : "active";
    document.getElementById("editCreatedAt").value = product.createdAt
        ? new Date(product.createdAt).toLocaleDateString("vi-VN") : "";
    document.getElementById("editDescription").value = product.description || "";
    document.getElementById("editIsTradeable").checked = product.listingType === "TRADE";
    document.getElementById("editTradeWishNote").value = product.tradeWishNote || "";
    window.updateStatusBadge(document.getElementById("editStatus").value);
}

window.loadSelectedPost = (id) => {
    const product = products.find((item) => String(item.id) === String(id));
    if (!product) {
        showMessage("Không tìm thấy sản phẩm trong tài khoản của bạn.", true);
        return;
    }
    showProduct(product);
};

window.updateStatusBadge = (status) => {
    const badge = document.getElementById("postStatusDisplayBadge");
    if (!badge) return;
    const labels = { active: "Đang hiển thị", pending: "Chờ kiểm duyệt", hidden: "Không hoạt động" };
    badge.className = status === "active" ? "badge badge-success" : "badge badge-warning";
    badge.textContent = labels[status] || "Chờ kiểm duyệt";
};

window.savePostChanges = async () => {
    const form = document.getElementById("editPostForm");
    if (!form || saving || !form.reportValidity()) return;
    const id = document.getElementById("editPostId").value;
    const imageUrl = document.getElementById("editImageUrl").value.trim();
    let imageUrls = [];
    if (imageUrl) {
        try {
            const parsed = new URL(imageUrl);
            if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("invalid protocol");
            imageUrls = [parsed.href];
        } catch {
            showMessage("URL ảnh phải là địa chỉ HTTP hoặc HTTPS hợp lệ.", true);
            return;
        }
    }

    const conditionValue = document.getElementById("editCondition").value;
    const condition = conditionValue.startsWith("Mới")
        ? 100
        : Number(conditionValue.match(/(\d{2})/)?.[1] || 100);
    const listingType = document.getElementById("editIsTradeable").checked ? "TRADE" : "SELL";
    const request = {
        title: document.getElementById("editTitle").value.trim(),
        description: document.getElementById("editDescription").value.trim(),
        categoryId: Number(document.getElementById("editCategory").value),
        authorId: Number(document.getElementById("editAuthor").value),
        publisherId: Number(document.getElementById("editPublisher").value) || null,
        volumeNumbers: document.getElementById("editVolume").value.trim(),
        publicationYear: Number(document.getElementById("editYear").value) || null,
        conditionPercent: condition,
        price: Number(document.getElementById("editPrice").value),
        stockQuantity: Number(document.getElementById("editStock").value),
        listingType,
        tradeWishNote: listingType === "TRADE" ? document.getElementById("editTradeWishNote").value.trim() : null,
        imageUrls
    };
    if (!request.publicationYear) delete request.publicationYear;
    if (!request.authorId) delete request.authorId;
    if (!request.publisherId) delete request.publisherId;

    saving = true;
    const button = form.querySelector("button[type='submit']");
    if (button) button.disabled = true;
    try {
        const response = await apiRequest(`/api/products/${encodeURIComponent(id)}`, { method: "PUT", body: request });
        products = products.map((item) => String(item.id) === String(id) ? response.data : item);
        showProduct(response.data);
        showMessage(response.message || "Sản phẩm đã được cập nhật và gửi kiểm duyệt.");
    } catch (error) {
        showMessage(friendlyApiError(error), true);
    } finally {
        saving = false;
        if (button) button.disabled = false;
    }
};

document.addEventListener("DOMContentLoaded", async () => {
    document.querySelector("#editOriginalPrice")?.closest(".form-group")?.remove();
    document.querySelector("#editViews")?.closest(".form-group")?.remove();
    try {
        const [productResponse, categoryResponse, authorResponse, publisherResponse] = await Promise.all([
            apiRequest("/api/products/mine"),
            apiRequest("/api/categories"),
            apiRequest("/api/authors"),
            apiRequest("/api/publishers")
        ]);
        products = productResponse.data || [];
        catalogs = {
            categories: categoryResponse.data || [],
            authors: authorResponse.data || [],
            publishers: publisherResponse.data || []
        };
        const selector = document.getElementById("postSelector");
        selector.innerHTML = products.map((product) =>
            `<option value="${escapeHtml(product.id)}">${escapeHtml(product.title)}</option>`
        ).join("");
        if (!products.length) {
            showMessage("Tài khoản của bạn chưa có sản phẩm để chỉnh sửa.");
            document.getElementById("editPostForm").hidden = true;
            return;
        }
        const requestedId = new URLSearchParams(window.location.search).get("id");
        const initialProduct = products.find((product) => String(product.id) === requestedId) || products[0];
        selector.value = String(initialProduct.id);
        showProduct(initialProduct);
    } catch (error) {
        showMessage(friendlyApiError(error), true);
        document.getElementById("editPostForm").hidden = true;
    }
});
