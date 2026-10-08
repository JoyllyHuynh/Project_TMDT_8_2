import { apiRequest } from "../../shared/api-client.js";
import {
    conditionText,
    escapeHtml,
    formatVnd,
    friendlyApiError,
    productDetailUrl
} from "./catalog-ui.js";

const params = new URLSearchParams(window.location.search);
const pageSize = 12;
const state = {
    categories: [],
    categoryId: params.get("categoryId") || "",
    keyword: params.get("q") || "",
    seller: params.get("seller") || "",
    listingType: params.get("listingType") || "",
    minCondition: params.get("minCondition") || "",
    maxCondition: params.get("maxCondition") || "",
    minPrice: "",
    maxPrice: "",
    sort: "newest",
    direction: "desc",
    page: 0,
    totalPages: 0
};

function normalize(value) {
    return String(value || "").normalize("NFD").replace(/\p{Diacritic}/gu, "").toLocaleLowerCase("vi");
}

function showToast(message) {
    const container = document.getElementById("toastContainer");
    if (!container) return;
    const toast = document.createElement("div");
    toast.className = "toast toast-info";
    toast.textContent = message;
    container.appendChild(toast);
    window.setTimeout(() => toast.remove(), 3000);
}

function categoryMarkup() {
    const list = document.getElementById("genreFilterList");
    if (!list) return;
    list.innerHTML = `
        <li><label class="filter-item-label"><span><input type="checkbox" value="all"> Tất cả thể loại</span></label></li>
        ${state.categories.map((category) => `
            <li><label class="filter-item-label">
                <span><input type="checkbox" value="${escapeHtml(category.id)}" data-category-id="${escapeHtml(category.id)}"> ${escapeHtml(category.name)}</span>
            </label></li>
        `).join("")}`;
    const selected = state.categories.find((category) => String(category.id) === state.categoryId);
    if (!selected && params.has("genre")) {
        const genre = normalize(params.get("genre"));
        const matching = state.categories.find((category) =>
            normalize(category.name) === genre || normalize(category.slug) === genre
        );
        if (matching) state.categoryId = String(matching.id);
    }
    const selectedInput = list.querySelector(`[data-category-id="${CSS.escape(state.categoryId)}"]`);
    if (selectedInput) selectedInput.checked = true;
    else list.querySelector('input[value="all"]').checked = true;
}

function queryRange() {
    const selectedPrice = document.querySelector('input[name="priceRange"]:checked')?.value || "all";
    if (selectedPrice === "under100") {
        state.minPrice = "";
        state.maxPrice = "99999.99";
    } else if (selectedPrice === "100to300") {
        state.minPrice = "100000";
        state.maxPrice = "300000";
    } else if (selectedPrice === "above300") {
        state.minPrice = "300000.01";
        state.maxPrice = "";
    } else {
        state.minPrice = "";
        state.maxPrice = "";
    }

    const conditionOptions = [...document.querySelectorAll("#conditionFilterList input[type='checkbox']:checked")];
    const selectedConditions = conditionOptions.map((input) => input.value);
    if (selectedConditions.length === 1 && selectedConditions[0] === "new") {
        state.minCondition = "100";
        state.maxCondition = "100";
    } else if (selectedConditions.length === 1 && selectedConditions[0] === "likenew") {
        state.minCondition = "98";
        state.maxCondition = "99";
    } else if (selectedConditions.length === 1 && selectedConditions[0] === "good") {
        state.minCondition = "80";
        state.maxCondition = "97";
    } else if (selectedConditions.length > 1) {
        state.minCondition = String(Math.min(...selectedConditions.map((value) =>
            value === "new" ? 100 : value === "likenew" ? 98 : 80
        )));
        state.maxCondition = String(Math.max(...selectedConditions.map((value) =>
            value === "new" ? 100 : value === "likenew" ? 99 : 97
        )));
    } else {
        state.minCondition = "";
        state.maxCondition = "";
    }
}

function makeEndpoint() {
    queryRange();
    const query = new URLSearchParams({
        page: String(state.page),
        pageSize: String(pageSize),
        sort: state.sort,
        direction: state.direction
    });
    if (state.keyword) query.set("keyword", state.keyword);
    if (state.seller) query.set("seller", state.seller);
    if (state.minPrice) query.set("minPrice", state.minPrice);
    if (state.maxPrice) query.set("maxPrice", state.maxPrice);
    if (state.minCondition) query.set("minCondition", state.minCondition);
    if (state.maxCondition) query.set("maxCondition", state.maxCondition);
    if (state.listingType) query.set("listingType", state.listingType);
    const prefix = state.categoryId
        ? `/api/products/category/${encodeURIComponent(state.categoryId)}`
        : state.keyword ? "/api/products/search" : "/api/products";
    return `${prefix}?${query}`;
}

function renderProduct(product) {
    const image = product.images?.[0]?.imageUrl || product.imageUrls?.[0] || "";
    const cover = image
        ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(product.title)}" class="card-cover-img" loading="lazy">`
        : `<div class="card-cover-img catalog-image-placeholder" aria-hidden="true"><i class="fa-solid fa-book-open"></i></div>`;
    const detailUrl = productDetailUrl(product);
    return `
        <article class="comic-catalog-card" data-price="${escapeHtml(product.price)}">
            <div class="card-cover-wrap">
                ${cover}
                <span class="card-condition-tag">${escapeHtml(conditionText(product.conditionPercent))}</span>
                ${product.listingType !== "SELL" ? '<span class="card-trade-badge"><i class="fa-solid fa-right-left"></i> Nhận Đổi</span>' : ""}
            </div>
            <div class="card-body">
                <span class="card-genre">${escapeHtml(product.categoryName || "Truyện")}</span>
                <a href="${detailUrl}" class="card-title">${escapeHtml(product.title)}</a>
                <div class="card-meta"><i class="fa-solid fa-user-pen"></i> ${escapeHtml(product.authorName || "Chưa cập nhật tác giả")} • ${escapeHtml(product.publisherName || "Chưa cập nhật NXB")}</div>
                <div class="card-price-row"><span class="card-price">${escapeHtml(formatVnd(product.price))}</span></div>
                <div class="card-actions">
                    <a href="${detailUrl}" class="btn-view-detail">Xem chi tiết</a>
                    <span class="catalog-seller">${escapeHtml(product.sellerName || "Người bán")}</span>
                </div>
            </div>
        </article>`;
}

function renderPagination(data) {
    let nav = document.querySelector(".catalog-pagination");
    if (!nav) {
        nav = document.createElement("nav");
        nav.className = "catalog-pagination";
        nav.setAttribute("aria-label", "Phân trang sản phẩm");
        document.getElementById("catalogBooksGrid")?.after(nav);
    }
    const totalPages = Number(data.totalPages) || 0;
    nav.innerHTML = `
        <button type="button" data-page-step="-1" ${state.page <= 0 ? "disabled" : ""}>Trang trước</button>
        <span>Trang ${totalPages ? state.page + 1 : 0} / ${totalPages}</span>
        <button type="button" data-page-step="1" ${state.page + 1 >= totalPages ? "disabled" : ""}>Trang sau</button>`;
    nav.querySelectorAll("[data-page-step]").forEach((button) => button.addEventListener("click", () => {
        state.page += Number(button.dataset.pageStep);
        loadProducts();
        window.scrollTo({ top: document.getElementById("catalogBooksGrid").offsetTop, behavior: "smooth" });
    }));
}

async function loadProducts() {
    const grid = document.getElementById("catalogBooksGrid");
    const badge = document.getElementById("catalogMatchBadge");
    if (!grid) return;
    grid.innerHTML = `<p role="status">Đang tải sản phẩm...</p>`;
    try {
        const result = await apiRequest(makeEndpoint());
        const data = result.data || { content: [], totalElements: 0, totalPages: 0 };
        grid.innerHTML = data.content?.map(renderProduct).join("")
            || `<p role="status">Không tìm thấy sản phẩm phù hợp.</p>`;
        if (badge) badge.textContent = `Tìm thấy ${data.totalElements || 0} sản phẩm`;
        state.totalPages = data.totalPages || 0;
        renderPagination(data);
    } catch (error) {
        grid.innerHTML = `<p role="status">${escapeHtml(friendlyApiError(error))}</p>`;
        if (badge) badge.textContent = "Không thể tải danh sách";
        state.totalPages = 0;
        renderPagination({ totalPages: 0 });
    }
}

function applyUrlFilters() {
    const title = document.getElementById("heroTitleText");
    const description = document.getElementById("heroDescText");
    const breadcrumb = document.getElementById("breadcrumbCurrent");
    const searchInput = document.getElementById("catalogSearchInput");
    const genre = params.get("genre");
    const category = state.categories.find((item) => String(item.id) === state.categoryId);
    if (searchInput) searchInput.value = state.keyword;

    if (title && state.keyword) title.textContent = `Tìm kiếm: "${state.keyword}"`;
    else if (title && (genre || category)) title.textContent = `Thể loại: ${category?.name || genre}`;
    else if (title && state.seller) title.textContent = `Tủ sách của: ${state.seller}`;
    if (breadcrumb) breadcrumb.textContent = state.keyword ? `Tìm kiếm "${state.keyword}"` : category?.name || genre || "Tất cả thể loại";
    if (description && state.keyword) description.textContent = "Hiển thị danh sách kết quả phù hợp với từ khóa tìm kiếm.";
    else if (description && (genre || category)) description.textContent = `Danh sách truyện thuộc thể loại ${category?.name || genre}.`;

    const listingRadio = document.querySelector(`input[name="listingType"][value="${CSS.escape(state.listingType)}"]`);
    if (listingRadio) listingRadio.checked = true;
    if (params.has("minCondition")) {
        const condition = Number(state.minCondition);
        const value = condition >= 100 ? "new" : condition >= 98 ? "likenew" : "good";
        const input = document.querySelector(`#conditionFilterList input[value="${value}"]`);
        if (input) input.checked = true;
    }
}

window.filterCatalog = (keyword) => {
    const input = document.getElementById("catalogSearchInput");
    if (typeof keyword === "string") state.keyword = keyword.trim();
    else state.keyword = input?.value.trim() || "";
    state.categoryId = document.querySelector("#genreFilterList input[data-category-id]:checked")?.value || "";
    state.listingType = document.querySelector("input[name='listingType']:checked")?.value || "";
    state.page = 0;
    loadProducts();
};

window.resetCatalogFilters = () => {
    document.querySelectorAll("#genreFilterList input[type='checkbox']").forEach((input) => {
        input.checked = input.value === "all";
    });
    document.querySelectorAll("#conditionFilterList input[type='checkbox']").forEach((input) => {
        input.checked = false;
    });
    const price = document.querySelector('input[name="priceRange"][value="all"]');
    if (price) price.checked = true;
    const search = document.getElementById("catalogSearchInput");
    if (search) search.value = "";
    state.categoryId = "";
    state.keyword = "";
    state.seller = "";
    state.listingType = "";
    state.minCondition = "";
    state.maxCondition = "";
    state.page = 0;
    loadProducts();
    showToast("Đã đặt lại toàn bộ bộ lọc.");
};

window.setSort = (button, sortType) => {
    state.sort = sortType === "price_asc" || sortType === "price_desc" ? "price" : "newest";
    state.direction = sortType === "price_asc" ? "asc" : "desc";
    document.querySelectorAll(".sort-pill").forEach((item) => item.classList.toggle("active", item === button));
    state.page = 0;
    loadProducts();
};

document.addEventListener("DOMContentLoaded", async () => {
    try {
        const result = await apiRequest("/api/categories");
        state.categories = result.data || [];
    } catch (error) {
        document.getElementById("catalogBooksGrid").innerHTML = `<p role="status">${escapeHtml(friendlyApiError(error))}</p>`;
        return;
    }

    categoryMarkup();
    applyUrlFilters();
    document.getElementById("genreFilterList")?.addEventListener("change", (event) => {
        const input = event.target;
        if (!(input instanceof HTMLInputElement)) return;
        if (input.value === "all" && input.checked) {
            document.querySelectorAll("#genreFilterList input[data-category-id]").forEach((item) => item.checked = false);
        } else if (input.dataset.categoryId && input.checked) {
            document.querySelectorAll("#genreFilterList input").forEach((item) => {
                if (item !== input) item.checked = false;
            });
        }
        window.filterCatalog();
    });
    document.querySelectorAll('input[name="priceRange"], #conditionFilterList input[type="checkbox"]')
        .forEach((input) => {
            input.removeAttribute("onchange");
            input.addEventListener("change", () => {
            state.page = 0;
            loadProducts();
            });
        });

    const searchInput = document.getElementById("catalogSearchInput");
    const searchButton = document.getElementById("catalogSearchBtn");
    searchButton?.addEventListener("click", () => window.filterCatalog());
    searchInput?.addEventListener("keydown", (event) => {
        if (event.key === "Enter") window.filterCatalog();
    });
    document.querySelectorAll(".sort-pill").forEach((button) => {
        const sortType = button.getAttribute("onclick")?.match(/'([^']+)'\s*\)/)?.[1] || "newest";
        button.removeAttribute("onclick");
        button.addEventListener("click", () => window.setSort(button, sortType));
    });
    await loadProducts();
});
