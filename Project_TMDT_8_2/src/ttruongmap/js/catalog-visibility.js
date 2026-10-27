import { apiRequest } from "../../shared/api-client.js";
import { escapeHtml, formatVnd, friendlyApiError } from "../../tin/js/catalog-ui.js";

let products = [];

function isPublic(product) {
    return product.active === true && product.moderationStatus === "APPROVED";
}

function productImage(product) {
    return product.imageUrls?.[0] || product.images?.[0]?.imageUrl || "";
}

function productStatus(product) {
    if (product.moderationStatus === "PENDING") return "Chờ kiểm duyệt";
    if (product.moderationStatus === "REJECTED") return "Bị từ chối";
    return product.active ? "Đang hiển thị" : "Đã ẩn";
}

function renderHiddenProduct(product) {
    const image = productImage(product);
    const canRestore = product.active !== true && product.moderationStatus === "APPROVED";
    const visibilityAction = product.active === true
        ? `<span class="badge">${escapeHtml(productStatus(product))}</span>`
        : `<button class="btn btn-success btn-sm" type="button" data-visibility-id="${escapeHtml(product.id)}" data-next-active="true"
            ${canRestore ? "" : "disabled title=\"Chỉ hiện sản phẩm đã được duyệt\""}>
            <span class="material-symbols-outlined" style="font-size:16px">visibility</span> Hiển thị lại
        </button>`;

    return `
        <tr>
            <td>${image ? `<img src="${escapeHtml(image)}" alt="" class="table-thumb">` : "—"}</td>
            <td>
                <div class="manga-title-link">${escapeHtml(product.title)}</div>
                <div class="manga-sub">Mã: <strong>${escapeHtml(product.id)}</strong> · ${escapeHtml(product.authorName || "Chưa có tác giả")}</div>
            </td>
            <td><span class="badge">${escapeHtml(productStatus(product))}</span></td>
            <td><strong style="color:var(--primary)">${escapeHtml(formatVnd(product.price))}</strong></td>
            <td>${Number(product.stockQuantity) || 0}</td>
            <td><span style="font-size:.8rem;color:var(--text-muted)">${escapeHtml(product.createdAt || "—")}</span></td>
            <td style="text-align:right">
                ${visibilityAction}
                <button class="btn btn-outline-danger btn-sm" type="button" data-delete-id="${escapeHtml(product.id)}">
                    <span class="material-symbols-outlined" style="font-size:16px">delete</span> Gỡ
                </button>
            </td>
        </tr>`;
}

function renderActiveProduct(product) {
    const image = productImage(product);
    return `
        <tr>
            <td>${image ? `<img src="${escapeHtml(image)}" alt="" class="table-thumb">` : "—"}</td>
            <td>
                <div class="manga-title-link">${escapeHtml(product.title)}</div>
                <div class="manga-sub">Mã: <strong>${escapeHtml(product.id)}</strong></div>
            </td>
            <td><strong>${escapeHtml(formatVnd(product.price))}</strong></td>
            <td>${Number(product.stockQuantity) || 0}</td>
            <td><span class="badge badge-success">Đang bán</span></td>
            <td style="text-align:right">
                <button class="btn btn-secondary btn-sm" type="button" data-visibility-id="${escapeHtml(product.id)}" data-next-active="false">
                    <span class="material-symbols-outlined" style="font-size:16px">visibility_off</span> Ẩn bài này
                </button>
            </td>
        </tr>`;
}

function emptyRow(message) {
    return `<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted)">${escapeHtml(message)}</td></tr>`;
}

function render() {
    const hidden = products.filter((product) => !isPublic(product));
    const visible = products.filter(isPublic);

    document.getElementById("statHiddenCount").textContent = String(hidden.length);
    document.getElementById("countHiddenTab").textContent = String(hidden.length);
    document.getElementById("statPendingCount").textContent = String(products.filter((product) => product.moderationStatus === "PENDING").length);
    document.getElementById("statOutOfStock").textContent = String(products.filter((product) => product.stockQuantity === 0).length);

    document.getElementById("hiddenTableBody").innerHTML = hidden.length
        ? hidden.map(renderHiddenProduct).join("")
        : emptyRow("Không có bài nào đang bị ẩn hoặc chờ kiểm duyệt.");
    document.getElementById("activeToHideTableBody").innerHTML = visible.length
        ? visible.map(renderActiveProduct).join("")
        : emptyRow("Bạn chưa có sản phẩm nào đang hiển thị công khai.");
}

function showMessage(message, isError = false) {
    let status = document.getElementById("catalogVisibilityStatus");
    if (!status) {
        status = document.createElement("p");
        status.id = "catalogVisibilityStatus";
        status.setAttribute("role", "status");
        document.querySelector(".content-body").prepend(status);
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
        document.getElementById("hiddenTableBody").innerHTML = emptyRow(friendlyApiError(error));
        document.getElementById("activeToHideTableBody").innerHTML = emptyRow(friendlyApiError(error));
    }
}

function switchView(view) {
    const showHidden = view === "hidden";
    document.getElementById("hiddenPostsSection").style.display = showHidden ? "block" : "none";
    document.getElementById("allToHideSection").style.display = showHidden ? "none" : "block";
    document.getElementById("tabHiddenBtn").classList.toggle("active", showHidden);
    document.getElementById("tabAllToHideBtn").classList.toggle("active", !showHidden);
}

function openDeleteModal(id) {
    const product = products.find((item) => String(item.id) === id);
    if (!product) return;
    document.getElementById("deleteTargetId").value = id;
    document.getElementById("deleteTargetTitle").textContent = product.title;
    document.getElementById("deleteConfirmModal").classList.add("show");
}

document.querySelectorAll("[data-view]").forEach((button) => {
    button.addEventListener("click", () => switchView(button.dataset.view));
});

document.querySelectorAll("[data-close-modal]").forEach((button) => {
    button.addEventListener("click", () => document.getElementById("deleteConfirmModal").classList.remove("show"));
});

document.addEventListener("click", async (event) => {
    const visibilityButton = event.target.closest("[data-visibility-id]");
    if (visibilityButton) {
        const id = visibilityButton.dataset.visibilityId;
        const active = visibilityButton.dataset.nextActive === "true";
        visibilityButton.disabled = true;
        try {
            const response = await apiRequest(`/api/products/${encodeURIComponent(id)}/visibility`, {
                method: "PATCH",
                body: { active }
            });
            products = products.map((product) => String(product.id) === id ? response.data : product);
            showMessage(response.message || "Đã cập nhật trạng thái hiển thị.");
            render();
        } catch (error) {
            showMessage(friendlyApiError(error), true);
            visibilityButton.disabled = false;
        }
        return;
    }

    const deleteButton = event.target.closest("[data-delete-id]");
    if (deleteButton) openDeleteModal(deleteButton.dataset.deleteId);
});

document.getElementById("confirmDeleteButton").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    const id = document.getElementById("deleteTargetId").value;
    if (!id) return;

    button.disabled = true;
    try {
        const response = await apiRequest(`/api/products/${encodeURIComponent(id)}`, { method: "DELETE" });
        products = products.filter((product) => String(product.id) !== id);
        document.getElementById("deleteConfirmModal").classList.remove("show");
        showMessage(response.message || "Đã gỡ sản phẩm khỏi gian hàng.");
        render();
    } catch (error) {
        showMessage(friendlyApiError(error), true);
    } finally {
        button.disabled = false;
    }
});

refresh();
