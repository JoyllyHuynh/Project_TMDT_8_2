import { apiRequest } from "../../shared/api-client.js";
import {
    conditionText,
    escapeHtml,
    formatVnd,
    friendlyApiError,
    productDetailUrl
} from "./catalog-ui.js";

document.addEventListener("DOMContentLoaded", () => {
    const productsGrid = document.querySelector(".products-grid");
    const categoriesGrid = document.querySelector(".categories-grid");
    if (!productsGrid) return;

    const tabsHost = document.querySelector(".market-tabs");
    let loadMoreButton = document.querySelector(".btn-load-more");
    const productSection = document.querySelector(".marketplace-section");
    const productSectionContainer = productSection?.querySelector(".container");
    const icons = ["cat-orange", "cat-indigo", "cat-rose", "cat-blue", "cat-amber", "cat-slate"];
    let selectedTab = "all";
    let currentPage = 0;
    let totalPages = 1;
    let categories = [];

    const showStatus = (message) => {
        productsGrid.innerHTML = `<p role="status" class="catalog-status">${escapeHtml(message)}</p>`;
    };

    function makeQuery(page) {
        const params = new URLSearchParams({ page: String(page), pageSize: "5", sort: "newest", direction: "desc" });
        if (selectedTab === "sell") params.set("listingType", "SELL");
        if (selectedTab === "trade") params.set("listingType", "TRADE");
        if (selectedTab === "under500") params.set("maxPrice", "500000");
        return params;
    }

    function renderProduct(product) {
        const href = productDetailUrl(product);
        const image = product.images?.[0]?.imageUrl || product.imageUrls?.[0] || "";
        const categoryName = product.categoryName || "Truyện";
        const publisherName = product.publisherName || "Chưa cập nhật NXB";
        const imageMarkup = image
            ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(product.title)}" loading="lazy">`
            : `<div class="catalog-image-placeholder" aria-hidden="true"><i class="fa-solid fa-book-open"></i></div>`;
        return `
            <article class="product-card" data-product-id="${escapeHtml(product.id)}">
                <div class="product-img-wrap">
                    ${imageMarkup}
                    <span class="badge-custom">${escapeHtml(categoryName)}</span>
                </div>
                <div class="product-info">
                    <div>
                        <div class="prod-publisher">${escapeHtml(publisherName)} • <span>${escapeHtml(conditionText(product.conditionPercent))}</span></div>
                        <h4 class="prod-title"><a href="${href}" style="text-decoration:none;color:inherit;">${escapeHtml(product.title)}</a></h4>
                    </div>
                    <div class="prod-foot">
                        <div class="prod-pricing"><span class="price-current">${escapeHtml(formatVnd(product.price))}</span></div>
                        <div class="prod-bottom-row">
                            <span class="seller-name">${escapeHtml(product.sellerName || "Người bán ComicWorm")}</span>
                            <a class="btn-buy-now" href="${href}" style="text-decoration:none;">Xem chi tiết</a>
                        </div>
                    </div>
                </div>
            </article>`;
    }

    function renderCategories(items) {
        if (!categoriesGrid) return;
        categoriesGrid.innerHTML = items.map((category, index) => `
            <a href="category.html?categoryId=${encodeURIComponent(category.id)}" class="category-card">
                <div class="cat-icon-wrap ${icons[index % icons.length]}"><i class="fa-solid fa-book"></i></div>
                <h3 class="cat-title">${escapeHtml(category.name)}</h3>
                <p class="cat-desc">${escapeHtml(category.parentName || "Khám phá truyện theo thể loại")}</p>
            </a>
        `).join("") || `<p role="status" class="catalog-status">Chưa có danh mục nào.</p>`;
    }

    function renderTabs() {
        if (!tabsHost) return;
        const options = [
            ["all", "Tất cả bài đăng"],
            ["sell", "Chỉ bán"],
            ["trade", "Chỉ trao đổi"],
            ["under500", "Giá dưới 500k"]
        ];
        tabsHost.replaceChildren(...options.map(([value, label]) => {
            const button = document.createElement("button");
            button.className = `tab-btn${value === selectedTab ? " active" : ""}`;
            button.type = "button";
            button.textContent = label;
            button.addEventListener("click", () => {
                selectedTab = value;
                currentPage = 0;
                renderTabs();
                loadProducts(0, false);
            });
            return button;
        }));
    }

    function renderLoadMore() {
        if (!loadMoreButton || !productSectionContainer) return;
        const button = loadMoreButton.cloneNode(false);
        button.textContent = totalPages > currentPage + 1 ? "Xem thêm sản phẩm" : "Đã hiển thị tất cả sản phẩm";
        button.disabled = totalPages <= currentPage + 1;
        button.addEventListener("click", () => loadProducts(currentPage + 1, true));
        loadMoreButton.replaceWith(button);
        loadMoreButton = button;
    }

    async function loadProducts(page, append) {
        if (!append) showStatus("Đang tải sản phẩm...");
        try {
            const result = await apiRequest(`/api/products?${makeQuery(page)}`);
            const data = result.data;
            const products = data?.content || [];
            currentPage = data?.page ?? page;
            totalPages = data?.totalPages ?? 0;
            const markup = products.map(renderProduct).join("");
            if (append) {
                if (page === 0) productsGrid.replaceChildren();
                productsGrid.insertAdjacentHTML("beforeend", markup);
            } else {
                productsGrid.innerHTML = markup || `<p role="status" class="catalog-status">Chưa có sản phẩm đang được duyệt.</p>`;
            }
            renderLoadMore();
        } catch (error) {
            showStatus(friendlyApiError(error));
            totalPages = 0;
            renderLoadMore();
        }
    }

    async function loadHomeCatalog() {
        try {
            const result = await apiRequest("/api/categories");
            categories = result.data || [];
            renderCategories(categories);
            const categorySelect = document.querySelectorAll(".search-filters-grid select")[1];
            if (categorySelect) {
                categorySelect.innerHTML = `<option value="">Tất cả thể loại</option>${categories.map((category) =>
                    `<option value="${escapeHtml(category.id)}">${escapeHtml(category.name)}</option>`
                ).join("")}`;
            }
        } catch (error) {
            if (categoriesGrid) categoriesGrid.innerHTML = `<p role="status" class="catalog-status">${escapeHtml(friendlyApiError(error))}</p>`;
        }
        renderTabs();
        await loadProducts(0, false);
    }

    loadHomeCatalog();
});
