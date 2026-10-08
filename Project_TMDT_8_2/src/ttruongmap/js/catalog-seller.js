import { apiRequest } from "../../shared/api-client.js";
import { escapeHtml, friendlyApiError } from "../../tin/js/catalog-ui.js";

let submitting = false;
let previewUrl = "";
let categories = [];
let authors = [];
let publishers = [];

function normalize(value) {
    return String(value || "").normalize("NFD").replace(/\p{Diacritic}/gu, "").toLocaleLowerCase("vi");
}

function showMessage(message, isError = false) {
    let status = document.getElementById("catalogSellerStatus");
    if (!status) {
        status = document.createElement("p");
        status.id = "catalogSellerStatus";
        status.setAttribute("role", "status");
        status.style.cssText = "margin: 12px 0; padding: 12px; border-radius: 8px;";
        document.querySelector(".content-body")?.prepend(status);
    }
    status.textContent = message;
    status.style.background = isError ? "#fef2f2" : "#f0fdf4";
    status.style.color = isError ? "#b91c1c" : "#166534";
}

function populateSelect(id, items, placeholder, currentText = "") {
    const select = document.getElementById(id);
    if (!select) return;
    select.innerHTML = `<option value="">${escapeHtml(placeholder)}</option>${items.map((item) =>
        `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`
    ).join("")}`;
    const match = items.find((item) => normalize(item.name) === normalize(currentText));
    if (match) select.value = String(match.id);
}

async function loadCatalogOptions() {
    try {
        const [categoryResponse, authorResponse, publisherResponse] = await Promise.all([
            apiRequest("/api/categories"),
            apiRequest("/api/authors"),
            apiRequest("/api/publishers")
        ]);
        categories = categoryResponse.data || [];
        authors = authorResponse.data || [];
        publishers = publisherResponse.data || [];
        populateSelect("postCategory", categories, "Chọn thể loại", document.getElementById("postCategory")?.value);
        populateSelect("postAuthor", authors, "Chọn tác giả", document.getElementById("postAuthor")?.value);
        populateSelect("postPublisher", publishers, "Chọn nhà xuất bản", document.getElementById("postPublisher")?.value);
        if (!categories.length || !authors.length || !publishers.length) {
            showMessage("Danh mục/tác giả/nhà xuất bản chưa đủ dữ liệu. Hãy bổ sung dữ liệu catalog trước khi đăng.", true);
        }
    } catch (error) {
        showMessage(friendlyApiError(error), true);
        document.querySelectorAll("#createPostForm button[type='submit'], .topbar-right button[onclick]").forEach((button) => button.disabled = true);
    }
}

function conditionPercent() {
    const value = document.getElementById("postCondition").value;
    if (value.startsWith("Mới")) return 100;
    const match = value.match(/(\d{2})/);
    return match ? Number(match[1]) : 100;
}

window.handleImageSelect = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith("image/") || file.size > 5 * 1024 * 1024) {
        showMessage("Chỉ chọn ảnh JPG, PNG hoặc WEBP có dung lượng tối đa 5MB để xem trước.", true);
        event.target.value = "";
        return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = URL.createObjectURL(file);
    const preview = document.getElementById("mainPreviewImg");
    if (preview) preview.src = previewUrl;
    showMessage("Ảnh chỉ được xem trước trên thiết bị; để lưu ảnh, hãy nhập URL ảnh công khai.");
};

document.getElementById("postImageUrl")?.addEventListener("input", (event) => {
    const value = event.target.value.trim();
    const preview = document.getElementById("mainPreviewImg");
    if (preview && value) preview.src = value;
});

window.submitPostForm = async () => {
    const form = document.getElementById("createPostForm");
    if (!form || submitting || !form.reportValidity()) return;
    const title = document.getElementById("postTitle").value.trim();
    const price = Number(document.getElementById("postPrice").value);
    const stock = Number(document.getElementById("postStock").value);
    const imageUrl = document.getElementById("postImageUrl").value.trim();
    if (!categories.length || !authors.length || !publishers.length) {
        showMessage("Không thể đăng sản phẩm khi catalog chưa có đủ thể loại, tác giả và nhà xuất bản.", true);
        return;
    }

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

    const listingType = document.querySelector('input[name="postType"]:checked')?.value === "trade" ? "TRADE" : "SELL";
    const request = {
        title,
        description: document.getElementById("postDescription").value.trim() || `Bài đăng ${title}`,
        categoryId: Number(document.getElementById("postCategory").value),
        authorId: Number(document.getElementById("postAuthor").value),
        publisherId: Number(document.getElementById("postPublisher").value),
        volumeNumbers: document.getElementById("postVolume").value.trim(),
        publicationYear: Number(document.getElementById("postYear").value) || null,
        conditionPercent: conditionPercent(),
        price,
        stockQuantity: stock,
        listingType,
        tradeWishNote: listingType === "TRADE" ? document.getElementById("tradeTarget").value.trim() : null,
        imageUrls
    };
    if (request.publicationYear === null) delete request.publicationYear;

    submitting = true;
    const buttons = [...form.querySelectorAll("button[type='submit']"), ...document.querySelectorAll(".topbar-right button[onclick]")];
    buttons.forEach((button) => button.disabled = true);
    try {
        const response = await apiRequest("/api/products", { method: "POST", body: request });
        showMessage(response.message || "Đã gửi sản phẩm để kiểm duyệt.");
        window.setTimeout(() => {
            window.location.href = "../quan-ly-bai-dang/index.html";
        }, 900);
    } catch (error) {
        showMessage(friendlyApiError(error), true);
        submitting = false;
        buttons.forEach((button) => button.disabled = false);
    }
};

document.addEventListener("DOMContentLoaded", loadCatalogOptions);
