export function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    })[character]);
}

export function formatVnd(value) {
    return `${new Intl.NumberFormat("vi-VN").format(Number(value) || 0)} ₫`;
}

export function friendlyApiError(error) {
    if (error.status === 400) return error.message || "Thông tin lọc không hợp lệ.";
    if (error.status === 401) return "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.";
    if (error.status === 403) return "Bạn không có quyền thực hiện thao tác này.";
    if (error.status === 404) return "Không tìm thấy sản phẩm hoặc danh mục.";
    if (error.status === 409) return "Dữ liệu bị trùng hoặc xung đột.";
    if (error.status >= 500) return "Máy chủ đang gặp sự cố. Vui lòng thử lại sau.";
    return error.message || "Không thể tải dữ liệu. Vui lòng thử lại.";
}

export function productDetailUrl(product) {
    return `product_details.html?id=${encodeURIComponent(product.id)}`;
}

export function conditionText(percent) {
    const value = Number(percent);
    if (value >= 100) return "Mới 100%";
    if (value >= 98) return `Like New ${value}%`;
    return `Tình trạng ${value}%`;
}
