import { apiRequest } from "../../shared/api-client.js";

document.addEventListener("DOMContentLoaded", async () => {
  const tabs = [...document.querySelectorAll(".order-tab-btn")];
  const searchInput = document.getElementById("search-input");
  const emptyMessage = document.getElementById("empty-message");
  const toast = document.getElementById("toast");
  const ordersListEl = document.getElementById("orders-list");

  let allOrders = [];
  let activeFilter = "all";
  let toastTimer;

  function showToast(message) {
    if (!toast) return;

    toast.textContent = message;
    toast.classList.add("show");

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toast.classList.remove("show");
    }, 2400);
  }

  /* =========================
     1. FETCH ORDERS FROM BACKEND
  ========================= */
  async function fetchMyOrders() {
    if (!ordersListEl) return;

    try {
      const response = await apiRequest("/api/orders/my-orders");
      allOrders = response.data || [];
      renderOrders(allOrders);
      updateTabBadges(allOrders);
    } catch (error) {
      console.error("Lỗi lấy danh sách đơn hàng:", error);
      if (emptyMessage) {
        emptyMessage.classList.add("show");
        const title = emptyMessage.querySelector("strong");
        const sub = emptyMessage.querySelector("span");
        if (title) title.textContent = "Không thể tải danh sách đơn hàng";
        if (sub) sub.textContent = error.message || "Vui lòng kiểm tra lại trạng thái đăng nhập.";
      }
    }
  }

  /* =========================
     2. RENDER ORDERS TO DOM
  ========================= */
  function renderOrders(orders) {
    if (!ordersListEl) return;

    // Xóa tất cả các thẻ order-card cũ
    const existingCards = ordersListEl.querySelectorAll("article.order-card");
    existingCards.forEach(card => card.remove());

    if (!orders || orders.length === 0) {
      if (emptyMessage) emptyMessage.classList.add("show");
      return;
    }

    if (emptyMessage) emptyMessage.classList.remove("show");

    orders.forEach((order, index) => {
      const firstItem = (order.items && order.items.length > 0) ? order.items[0] : {};
      const card = document.createElement("article");
      const statusFilterKey = mapStatusToFilter(order.status);
      const statusInfo = getStatusBadge(order.status);

      card.className = `order-card ${index === 0 ? "active-card" : ""}`;
      card.setAttribute("data-order-id", order.orderCode || "");
      card.setAttribute("data-status", statusFilterKey);
      card.setAttribute("data-search", `${order.orderCode} ${order.sellerName || ''} ${firstItem.productTitle || ''}`);

      card.innerHTML = `
        ${index === 0 ? '<div class="active-line"></div>' : ''}

        <div class="order-header">
          <div class="seller">
            <div class="seller-avatar">${getInitials(order.sellerName || "ComicHub")}</div>
            <div>
              <div class="seller-name">
                ${escapeHtml(order.sellerName || "Người bán ComicHub")}
                <span class="material-symbols-outlined verified">verified</span>
              </div>
              <span>Mã đơn: #${order.orderCode} • Ngày đặt: ${formatDate(order.createdAt)}</span>
            </div>
          </div>
          <span class="status ${statusInfo.cssClass}">
            <span class="material-symbols-outlined">${statusInfo.icon}</span>
            ${statusInfo.text}
          </span>
        </div>

        <div class="order-product">
          <img alt="${escapeHtml(firstItem.productTitle || "Sản phẩm")}" src="${firstItem.imageUrl || "https://via.placeholder.com/150"}">
          <div class="product-info">
            <h3>${escapeHtml(firstItem.productTitle || "Truyện tranh")}</h3>
            <p>Tình trạng: ${firstItem.conditionPercent || 100}% Like New</p>
            <div class="price-row">
              <strong>${formatVnd(firstItem.unitPrice || 0)}</strong>
              <span>SL: ${firstItem.quantity || 1}</span>
            </div>
          </div>
          <div class="order-total">
            <span>Tổng thanh toán</span>
            <strong>${formatVnd(order.totalAmount || 0)}</strong>
          </div>
        </div>

        <div class="order-footer">
          <span>Thanh toán: ${order.paymentMethod || "VNPAY"}</span>
          <div class="card-actions">
            <button class="secondary-action remind-button">
              <span class="material-symbols-outlined">chat</span> Nhắc đóng gói
            </button>
            ${order.status === "WAITING_CONFIRM" ? `<button class="danger-action cancel-button">Hủy yêu cầu</button>` : ""}
          </div>
        </div>
      `;

      // Chọn đơn hàng khi click
      card.addEventListener("click", event => {
        const clickedButton = event.target.closest("button, a");
        if (clickedButton) return;

        document.querySelectorAll(".order-card").forEach(item => {
          item.classList.remove("active-card");
          const oldLine = item.querySelector(".active-line");
          if (oldLine) oldLine.remove();
        });

        card.classList.add("active-card");
        const line = document.createElement("div");
        line.className = "active-line";
        card.prepend(line);

        renderOrderDetail(order);
        showToast(`Đã chọn đơn hàng #${order.orderCode}`);
      });

      ordersListEl.insertBefore(card, emptyMessage);
    });

    // Mặc định hiển thị thông tin chi tiết của đơn đầu tiên
    if (orders.length > 0) {
      renderOrderDetail(orders[0]);
    }
  }

  /* =========================
     3. RENDER ORDER DETAIL PANEL
  ========================= */
  function renderOrderDetail(order) {
    const detailPanel = document.getElementById("detail-panel");
    if (!detailPanel) return;

    const firstItem = (order.items && order.items.length > 0) ? order.items[0] : {};
    const statusInfo = getStatusBadge(order.status);

    detailPanel.innerHTML = `
      <div class="detail-header">
        <div>
          <div class="detail-title">
            <h2>Chi Tiết Đơn Hàng</h2>
            <span>#${order.orderCode}</span>
          </div>
          <p>Đặt ngày: ${formatDate(order.createdAt)}</p>
        </div>
        <button class="close-detail" type="button" aria-label="Đóng">
          <span class="material-symbols-outlined">more_horiz</span>
        </button>
      </div>

      <div class="detail-status">
        <span class="material-symbols-outlined">${statusInfo.icon}</span>
        <div>
          <strong>${statusInfo.text}</strong>
          <span>Mã đơn hàng: ${order.orderCode}</span>
        </div>
      </div>

      <div class="detail-section">
        <div class="section-title">
          <span class="material-symbols-outlined">storefront</span>
          Người bán
        </div>
        <div class="seller-detail">
          <div class="seller-avatar">${getInitials(order.sellerName || "CB")}</div>
          <div>
            <strong>${escapeHtml(order.sellerName || "ComicHub Seller")} <span class="material-symbols-outlined verified">verified</span></strong>
            <span>99.4% đánh giá tốt</span>
          </div>
          <a href="../../duy/html/dealing-chat.html" class="chat-button" style="text-decoration:none; display:inline-flex; align-items:center; justify-content:center;" title="Chat với người bán"><span class="material-symbols-outlined">chat</span></a>
        </div>
      </div>

      <div class="detail-section">
        <div class="section-title">
          <span class="material-symbols-outlined">menu_book</span>
          Sản phẩm
        </div>
        <div class="detail-product">
          <img alt="${escapeHtml(firstItem.productTitle)}" src="${firstItem.imageUrl || "https://via.placeholder.com/150"}">
          <div>
            <strong>${escapeHtml(firstItem.productTitle || "Truyện tranh")}</strong>
            <span>SL: ${firstItem.quantity || 1}</span>
          </div>
          <strong>${formatVnd(firstItem.unitPrice || 0)}</strong>
        </div>
      </div>

      <div class="detail-section price-summary">
        <div><span>Giá sản phẩm</span><strong>${formatVnd(order.subtotalAmount || 0)}</strong></div>
        <div><span>Phí vận chuyển</span><strong>${formatVnd(order.shippingFee || 0)}</strong></div>
        <div class="summary-total"><span>Tổng thanh toán</span><strong>${formatVnd(order.totalAmount || 0)}</strong></div>
      </div>

      <div class="escrow-note">
        <span class="material-symbols-outlined">shield_lock</span>
        <div>
          <strong>Tiền đang được giữ bởi Escrow</strong>
          <span>Người bán chỉ nhận tiền sau khi bạn xác nhận truyện đúng mô tả.</span>
        </div>
      </div>

      <div class="detail-actions">
        <button class="primary-button remind-button">
          <span class="material-symbols-outlined">chat</span>
          Nhắc đóng gói
        </button>
        <a href="page5.html" class="return-button cancel-button" style="text-decoration:none; display:inline-flex; align-items:center; justify-content:center; gap:6px;">
          <span class="material-symbols-outlined">assignment_return</span>
          Yêu cầu Đổi trả / Hủy
        </a>
      </div>

      <div class="report-link">
        <a href="../../vu/complain/complain.html">
          <span class="material-symbols-outlined">flag</span>
          Báo cáo hành vi người bán nghi ngờ truyện fake
        </a>
      </div>
    `;

    // Re-bind sự kiện nút close trong detail
    detailPanel.querySelector(".close-detail")?.addEventListener("click", () => {
      showToast("Bảng chi tiết vẫn được giữ để tiện theo dõi đơn hàng.");
    });
  }

  /* =========================
     4. FILTERING & TABS
  ========================= */
  function filterOrders() {
    const keyword = (searchInput?.value || "").trim().toLowerCase();

    const filtered = allOrders.filter(order => {
      const statusKey = mapStatusToFilter(order.status);
      const matchesStatus = activeFilter === "all" || statusKey === activeFilter;

      const firstItem = (order.items && order.items[0]) ? order.items[0].productTitle.toLowerCase() : "";
      const searchText = `${order.orderCode} ${order.sellerName || ''} ${firstItem}`.toLowerCase();
      const matchesSearch = !keyword || searchText.includes(keyword);

      return matchesStatus && matchesSearch;
    });

    renderOrders(filtered);
  }

  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(item => item.classList.remove("active"));
      tab.classList.add("active");

      activeFilter = tab.dataset.filter || "all";
      filterOrders();
    });
  });

  searchInput?.addEventListener("input", filterOrders);

  window.addEventListener("keydown", event => {
    if (event.key === "Escape" && document.activeElement === searchInput) {
      searchInput.value = "";
      searchInput.blur();
      filterOrders();
      showToast("Đã xóa nội dung tìm kiếm.");
    }
  });

  /* =========================
     5. HELPER FUNCTIONS
  ========================= */
  function mapStatusToFilter(status) {
    switch (status) {
      case "WAITING_CONFIRM": return "pending";
      case "PROCESSING": return "processing";
      case "SHIPPING": return "shipping";
      case "COMPLETED": return "completed";
      case "CANCELLED": return "cancelled";
      default: return "all";
    }
  }

  function getStatusBadge(status) {
    switch (status) {
      case "WAITING_CONFIRM": return { text: "Chờ xác nhận", cssClass: "pending", icon: "schedule" };
      case "PROCESSING": return { text: "Đang xử lý", cssClass: "processing", icon: "inventory_2" };
      case "SHIPPING": return { text: "Đang giao", cssClass: "shipping", icon: "local_shipping" };
      case "COMPLETED": return { text: "Đã hoàn thành", cssClass: "completed", icon: "check_circle" };
      case "CANCELLED": return { text: "Đã hủy", cssClass: "cancelled", icon: "cancel" };
      default: return { text: status || "Chờ xác nhận", cssClass: "pending", icon: "info" };
    }
  }

  function updateTabBadges(orders) {
    // Cập nhật badge tổng số đơn
    const storedBadge = document.querySelector(".stored-badge");
    if (storedBadge) storedBadge.textContent = `${orders.length} đơn đang lưu trữ`;

    // Thống kê số lượng theo từng tab
    const counts = {
      all: orders.length,
      pending: 0,
      processing: 0,
      shipping: 0,
      completed: 0,
      cancelled: 0
    };

    orders.forEach(order => {
      const key = mapStatusToFilter(order.status);
      if (counts[key] !== undefined) {
        counts[key]++;
      }
    });

    // Gán số lượng vào từng tab HTML
    const tabs = document.querySelectorAll("#order-status-tabs .order-tab-btn");
    tabs.forEach(tab => {
      const filter = tab.dataset.filter;
      const badgeEl = tab.querySelector("b");
      if (badgeEl && counts[filter] !== undefined) {
        badgeEl.textContent = counts[filter];
      }
    });
  }

  function formatVnd(amount) {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount).replace("₫", "") + "₫";
  }

  function formatDate(dateStr) {
    if (!dateStr) return "";
    return new Date(dateStr).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
  }

  function getInitials(name) {
    if (!name) return "CB";
    return name.split(" ").map(n => n[0]).join("").toUpperCase().substring(0, 2);
  }

  function escapeHtml(str) {
    if (!str) return "";
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  /* =========================
     INIT DATA CALL
  ========================= */
  await fetchMyOrders();
});