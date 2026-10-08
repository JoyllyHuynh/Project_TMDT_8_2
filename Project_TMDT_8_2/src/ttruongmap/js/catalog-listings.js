import { apiRequest } from "../../shared/api-client.js";
import { conditionText, escapeHtml, formatVnd, friendlyApiError } from "../../tin/js/catalog-ui.js";

let products = [];
let activeTab = "all";

function statusLabel(product) {
    if (!product.active) return "Đã ẩn";
    if (product.moderationStatus === "PENDING") return "Chờ duyệt";
    if (product.moderationStatus === "REJECTED") return "Bị từ chối";
    if (product.stockQuantity === 0) return "Hết hàng";
    return "Đang bán";
}

function isVisibleOnTab(product) {
    if (activeTab === "active") return product.active && product.moderationStatus === "APPROVED" && product.stockQuantity > 0;
    if (activeTab === "pending") return product.moderationStatus === "PENDING";
    if (activeTab === "hidden") return !product.active;
    if (activeTab === "out_of_stock") return product.stockQuantity === 0;
    return true;
}

function renderRow(product) {
    const image = product.images?.[0]?.imageUrl || product.imageUrls?.[0] || "";
    return `
        <tr>
            <td><input type="checkbox" class="listing-select" value="${escapeHtml(product.id)}" aria-label="Chọn ${escapeHtml(product.title)}"></td>
            <td>${image
                ? `<img src="${escapeHtml(image)}" alt="" width="48" height="64" style="object-fit:cover">`
                : `<span aria-label="Chưa có ảnh">—</span>`}</td>
            <td><strong>${escapeHtml(product.title)}</strong><br><small>${escapeHtml(product.authorName || "Chưa có tác giả")} · ${escapeHtml(product.categoryName || "")}</small></td>
            <td>${escapeHtml(conditionText(product.conditionPercent))}</td>
            <td>${escapeHtml(formatVnd(product.price))}</td>
            <td>${Number(product.stockQuantity) || 0}</td>
            <td>—</td>
            <td>${escapeHtml(statusLabel(product))}</td>
            <td style="text-align:right">
                <a class="btn btn-secondary btn-sm" href="../chinh-sua-bai-dang/index.html?id=${encodeURIComponent(product.id)}">Sửa</a>
                <button type="button" class="btn btn-danger btn-sm" data-delete-id="${escapeHtml(product.id)}">Xóa</button>
            </td>
        </tr>`;
}

function render() {
    const search = document.getElementById("searchInput")?.value.trim().toLocaleLowerCase("vi") || "";
    const category = document.getElementById("categoryFilter")?.value || "all";
    const sort = document.getElementById("sortFilter")?.value || "newest";
    let filtered = products.filter((product) =>
        isVisibleOnTab(product)
        && (!search || `${product.title} ${product.authorName || ""} ${product.id}`.toLocaleLowerCase("vi").includes(search))
        && (category === "all" || String(product.categoryId) === category)
    );
    filtered = [...filtered].sort((left, right) => {
        if (sort === "price_asc") return Number(left.price) - Number(right.price);
        if (sort === "price_desc") return Number(right.price) - Number(left.price);
        return new Date(right.createdAt || 0) - new Date(left.createdAt || 0);
    });
    const body = document.getElementById("postsTableBody");
    if (body) body.innerHTML = filtered.map(renderRow).join("")
        || `<tr><td colspan="9">Không tìm thấy sản phẩm trong bộ lọc này.</td></tr>`;
    document.getElementById("statTotalPosts").textContent = String(products.length);
    document.getElementById("statActivePosts").textContent = String(products.filter((item) =>
        item.active && item.moderationStatus === "APPROVED" && item.stockQuantity > 0
    ).length);
    document.getElementById("statTotalViews").textContent = "—";
    document.getElementById("statPendingHidden").textContent = String(products.filter((item) =>
        item.moderationStatus === "PENDING"
    ).length);
    const counts = {
        tabCountAll: products.length,
        tabCountActive: products.filter((item) => item.active && item.moderationStatus === "APPROVED" && item.stockQuantity > 0).length,
        tabCountPending: products.filter((item) => item.moderationStatus === "PENDING").length,
        tabCountHidden: products.filter((item) => !item.active).length,
        tabCountOutOfStock: products.filter((item) => item.stockQuantity === 0).length,
        hiddenCountHeader: products.filter((item) => !item.active).length
    };
    Object.entries(counts).forEach(([id, count]) => {
        const element = document.getElementById(id);
        if (element) element.textContent = String(count);
    });
}

function showMessage(message, isError = false) {
    let status = document.getElementById("catalogListingsStatus");
    if (!status) {
        status = document.createElement("p");
        status.id = "catalogListingsStatus";
        status.setAttribute("role", "status");
        document.querySelector(".content-body")?.prepend(status);
    }
    status.textContent = message;
    status.style.color = isError ? "#b91c1c" : "#166534";
}

async function refresh() {
    try {
        const response = await apiRequest("/api/products/mine");
        products = response.data || [];
        render();
    } catch (error) {
        showMessage(friendlyApiError(error), true);
        document.getElementById("postsTableBody").innerHTML =
            `<tr><td colspan="9">${escapeHtml(friendlyApiError(error))}</td></tr>`;
    }
}

window.setTabFilter = (tab, button) => {
    activeTab = tab;
    document.querySelectorAll("#statusTabs .filter-tab").forEach((item) => item.classList.toggle("active", item === button));
    render();
};
window.applyFilters = render;
window.toggleSelectAll = (master) => {
    document.querySelectorAll(".listing-select").forEach((input) => input.checked = master.checked);
};
window.bulkHideSelected = async () => {
    const ids = [...document.querySelectorAll(".listing-select:checked")].map((input) => input.value);
    if (!ids.length) {
        showMessage("Chọn ít nhất một sản phẩm.");
        return;
    }
    if (!window.confirm(`Xóa mềm ${ids.length} sản phẩm đã chọn?`)) return;
    try {
        for (const id of ids) await apiRequest(`/api/products/${encodeURIComponent(id)}`, { method: "DELETE" });
        showMessage("Đã xóa các sản phẩm đã chọn.");
        await refresh();
    } catch (error) {
        showMessage(friendlyApiError(error), true);
        await refresh();
    }
};
window.resetSampleData = () => showMessage("Dữ liệu mẫu không được nạp. Danh sách lấy trực tiếp từ tài khoản của bạn.");

document.getElementById("postsTableBody")?.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-delete-id]");
    if (!button) return;
    const product = products.find((item) => String(item.id) === button.dataset.deleteId);
    if (!product || !window.confirm(`Xóa mềm sản phẩm "${product.title}"?`)) return;
    button.disabled = true;
    try {
        await apiRequest(`/api/products/${encodeURIComponent(product.id)}`, { method: "DELETE" });
        showMessage("Đã xóa sản phẩm.");
        await refresh();
    } catch (error) {
        showMessage(friendlyApiError(error), true);
        button.disabled = false;
    }
});

document.addEventListener("DOMContentLoaded", async () => {
    const sortFilter = document.getElementById("sortFilter");
    if (sortFilter) sortFilter.innerHTML = `
        <option value="newest">Mới nhất</option>
        <option value="price_asc">Giá tăng dần</option>
        <option value="price_desc">Giá giảm dần</option>`;
    try {
        const response = await apiRequest("/api/categories");
        const categoryFilter = document.getElementById("categoryFilter");
        if (categoryFilter) categoryFilter.innerHTML = `<option value="all">Tất cả thể loại</option>${(response.data || []).map((item) =>
            `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`
        ).join("")}`;
    } catch (error) {
        showMessage(friendlyApiError(error), true);
    }
    document.querySelectorAll("#statusTabs .filter-tab").forEach((button) => {
        const tab = button.getAttribute("onclick")?.match(/'([^']+)'/)?.[1] || "all";
        button.removeAttribute("onclick");
        button.addEventListener("click", () => window.setTabFilter(tab, button));
    });
    document.querySelectorAll(".search-filter-bar button").forEach((button) => {
        if (button.textContent.includes("Ẩn đã chọn")) button.textContent = "Xóa đã chọn";
        if (button.textContent.includes("Nạp lại mẫu")) button.remove();
    });
    await refresh();
});
