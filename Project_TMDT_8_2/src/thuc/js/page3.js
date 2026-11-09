import { apiRequest } from "../../shared/api-client.js";

const toastEl = document.getElementById("toast");

let currentOrder = null;
let orders = [];
let ordersLoaded = false;
let activeOrdersRequest = 0;
const orderStatusLabels = {
    WAITING_PAYMENT: "Chờ thanh toán",
    WAITING_CONFIRM: "Chờ người bán xác nhận",
    CONFIRMED: "Đã xác nhận",
    SHIPPING: "Đang giao hàng",
    COMPLETED: "Đã hoàn thành",
    CANCELLED: "Đã hủy"
};

const paymentStatusLabels = {
    PENDING: "Chờ thanh toán",
    PAID: "Đã thanh toán",
    FAILED: "Thanh toán thất bại",
    REFUNDED: "Đã hoàn tiền"
};

function getRequestedOrderCode() {
    return new URLSearchParams(window.location.search).get("orderCode")?.trim() || "";
}

function initializeOrderPage() {
    document.getElementById("order-status-filter")
        ?.addEventListener("change", renderOrders);
    document.getElementById("orders-container")
        ?.addEventListener("click", handleOrderListClick);
    document.getElementById("back-to-orders")
        ?.addEventListener("click", showOrderList);
    document.getElementById("back-to-orders-inline")
        ?.addEventListener("click", showOrderList);
    document.getElementById("orders-feedback")
        ?.addEventListener("click", handleRetryClick);
    document.getElementById("order-detail-feedback")
        ?.addEventListener("click", handleRetryClick);
    window.addEventListener("popstate", handleOrderLocationChange);
    loadOrders();
}

async function loadOrders() {
    const requestId = ++activeOrdersRequest;
    const requestedCode = getRequestedOrderCode();
    showCurrentView();
    if (requestedCode) {
        setOrderDetailFeedback("Đang tải thông tin đơn hàng...", false, false);
    } else {
        setOrdersFeedback("Đang tải danh sách đơn hàng...");
    }

    try {
        const response = await withTimeout(
            apiRequest("/api/orders/my-orders"),
            "Hết thời gian chờ tải đơn hàng. Vui lòng thử lại."
        );
        if (requestId !== activeOrdersRequest) return;
        if (!Array.isArray(response?.data)) {
            throw new Error("Dữ liệu đơn hàng từ máy chủ không hợp lệ.");
        }

        orders = response.data.filter((order) => order && typeof order === "object");
        ordersLoaded = true;
        if (requestedCode) {
            openOrderDetail(requestedCode);
        } else {
            currentOrder = null;
            renderOrders();
        }
    } catch (error) {
        if (requestId !== activeOrdersRequest) return;
        console.error("Lỗi lấy thông tin đơn hàng:", error);
        const message = error.status === 401 || error.status === 403
            ? "Phiên đăng nhập không hợp lệ hoặc bạn không có quyền xem đơn hàng. Vui lòng đăng nhập lại."
            : error.message || "Không thể tải danh sách đơn hàng.";
        if (requestedCode) {
            setOrderDetailFeedback(message, true, true);
            return;
        }
        setOrdersFeedback(message, true);
    }
}

function showCurrentView() {
    const hasOrderCode = Boolean(getRequestedOrderCode());
    document.getElementById("order-list-view").hidden = hasOrderCode;
    document.getElementById("order-detail-view").hidden = !hasOrderCode;
    document.getElementById("order-detail-content").hidden = true;
    document.getElementById("order-detail-feedback").hidden = !hasOrderCode;
}

function setOrdersFeedback(message, isError = false, retry = false) {
    const feedback = document.getElementById("orders-feedback");
    if (!feedback) return;
    feedback.replaceChildren();
    if (!message) return;

    const text = document.createElement("p");
    text.textContent = message;
    feedback.className = `orders-feedback${isError ? " error" : ""}${!isError && message.startsWith("Đang tải") ? " loading" : ""}`;
    feedback.append(text);
    if (retry) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-primary";
        button.dataset.retryOrders = "true";
        button.textContent = "Thử tải lại";
        feedback.append(button);
    }
}

function setOrderDetailFeedback(message, isError, showBack) {
    const panel = document.getElementById("order-detail-feedback");
    const title = panel?.querySelector("h1");
    const text = document.getElementById("order-detail-feedback-message");
    if (!panel || !text) return;
    title.textContent = isError ? "Không thể mở đơn hàng" : "Đang tải đơn hàng...";
    text.textContent = message;
    panel.classList.toggle("error", isError);
    panel.classList.toggle("loading", !isError);
    panel.hidden = false;

    let retry = panel.querySelector("[data-retry-orders]");
    if (isError && getRequestedOrderCode()) {
        if (!retry) {
            retry = document.createElement("button");
            retry.type = "button";
            retry.className = "btn btn-secondary";
            retry.dataset.retryOrders = "true";
            retry.textContent = "Thử tải lại";
        }
        text.after(retry);
    } else {
        retry?.remove();
    }
    const backButton = document.getElementById("back-to-orders");
    if (backButton) backButton.hidden = !showBack;
}

function withTimeout(request, message, timeoutMs = 15000) {
    let timeoutId;
    const timeout = new Promise((_, reject) => {
        timeoutId = window.setTimeout(() => reject(new Error(message)), timeoutMs);
    });
    return Promise.race([request, timeout]).finally(() => window.clearTimeout(timeoutId));
}

function renderOrders() {
    const container = document.getElementById("orders-container");
    const filter = document.getElementById("order-status-filter")?.value || "ALL";
    if (!container) return;
    container.replaceChildren();

    if (!ordersLoaded) return;
    setOrdersFeedback("");
    if (!orders.length) {
        setOrdersFeedback("Bạn chưa có đơn hàng nào. Hãy quay lại mua sắm để khám phá truyện mới.");
        return;
    }

    const filteredOrders = filter === "ALL"
        ? orders
        : orders.filter((order) => order.status === filter);
    if (!filteredOrders.length) {
        setOrdersFeedback("Không có đơn hàng phù hợp với trạng thái đã chọn.");
        return;
    }

    const fragment = document.createDocumentFragment();
    filteredOrders.forEach((order) => fragment.append(createOrderCard(order)));
    container.append(fragment);
}

function createOrderCard(order) {
    const card = document.createElement("article");
    card.className = "order-card";

    const header = document.createElement("div");
    header.className = "order-card-header";
    const code = document.createElement("strong");
    code.className = "order-code";
    code.textContent = order.orderCode ? `Đơn hàng #${order.orderCode}` : "Đơn hàng";
    const date = document.createElement("time");
    date.className = "order-date";
    date.textContent = formatOrderDate(order.createdAt) || "Chưa có ngày đặt";
    header.append(code, date);

    const statuses = document.createElement("div");
    statuses.className = "order-card-statuses";
    statuses.append(
        createStatusBadge(getOrderStatusLabel(order.status), "order"),
        createStatusBadge(getPaymentStatusLabel(order.paymentStatus), "payment")
    );

    const items = document.createElement("ul");
    items.className = "order-card-items";
    (Array.isArray(order.items) ? order.items : []).forEach((item) => {
        const row = document.createElement("li");
        const title = document.createElement("span");
        title.textContent = item?.productTitle || "Sản phẩm";
        const quantity = document.createElement("span");
        quantity.textContent = `SL: ${item?.quantity ?? "—"}`;
        row.append(title, quantity);
        items.append(row);
    });
    if (!items.childElementCount) {
        const emptyItem = document.createElement("li");
        emptyItem.textContent = "Chưa có thông tin sản phẩm.";
        items.append(emptyItem);
    }

    const footer = document.createElement("div");
    footer.className = "order-card-footer";
    const total = document.createElement("strong");
    total.textContent = `Tổng tiền: ${formatCurrency(order.totalAmount)}`;
    const track = document.createElement("button");
    track.type = "button";
    track.className = "btn btn-primary";
    track.dataset.orderCode = order.orderCode || "";
    track.disabled = !order.orderCode;
    track.textContent = "Theo dõi đơn hàng";
    footer.append(total, track);

    card.append(header, statuses, items, footer);
    return card;
}

function createStatusBadge(label, type) {
    const badge = document.createElement("span");
    badge.className = `order-status-badge ${type}`;
    badge.textContent = label;
    return badge;
}

function getPaymentStatusLabel(status) {
    return Object.prototype.hasOwnProperty.call(paymentStatusLabels, status)
        ? paymentStatusLabels[status]
        : status || "Chưa có trạng thái";
}

function handleOrderListClick(event) {
    const button = event.target.closest("[data-order-code]");
    if (button?.dataset.orderCode) {
        navigateToOrder(button.dataset.orderCode);
    }
}

function handleRetryClick(event) {
    if (event.target.closest("[data-retry-orders]")) loadOrders();
}

function navigateToOrder(code) {
    const url = new URL(window.location.href);
    url.searchParams.set("orderCode", code);
    window.history.pushState(null, "", url);
    handleOrderLocationChange();
}

function showOrderList() {
    const url = new URL(window.location.href);
    url.searchParams.delete("orderCode");
    window.history.pushState(null, "", url);
    currentOrder = null;
    showCurrentView();
    if (ordersLoaded) renderOrders();
    else loadOrders();
}

function handleOrderLocationChange() {
    const code = getRequestedOrderCode();
    showCurrentView();
    if (!code) {
        currentOrder = null;
        if (ordersLoaded) renderOrders();
        else loadOrders();
        return;
    }
    if (ordersLoaded) openOrderDetail(code);
    else loadOrders();
}

function openOrderDetail(code) {
    currentOrder = orders.find((order) => order?.orderCode === code) || null;
    if (!currentOrder) {
        document.getElementById("order-detail-content").hidden = true;
        setOrderDetailFeedback(`Không tìm thấy đơn hàng có mã ${code}.`, true, true);
        return;
    }

    document.getElementById("order-detail-feedback").hidden = true;
    document.getElementById("order-detail-content").hidden = false;
    renderOrderProduct(currentOrder);
    renderRecipientInfo(currentOrder);
    renderOrderDetails(currentOrder);
    renderOrderStatus(currentOrder);
    renderOrderTimeline(currentOrder);
    renderDeliveryInfo(currentOrder);
}

function initialize() {
    if (getRequestedOrderCode()) {
        showCurrentView();
    } else {
        document.getElementById("order-list-view").hidden = false;
        document.getElementById("order-detail-view").hidden = true;
    }
    initializeOrderPage();
}

initialize();

function renderOrderProduct(order) {
    const item = Array.isArray(order.items) ? order.items[0] : null;
    const title = document.querySelector(".product-title");
    const image = document.querySelector(".product-cover img");
    const quantity = document.querySelector(".volume-count");
    const productMeta = document.querySelector(".product-meta");

    if (!item) {
        if (title) title.textContent = "Không có thông tin sản phẩm";
        if (image) image.removeAttribute("src");
        if (quantity) quantity.textContent = "";
        if (productMeta) productMeta.replaceChildren();
        showToast("Đơn hàng không có thông tin sản phẩm.");
        return;
    }

    if (title) {
        title.textContent = item.productTitle || "Sản phẩm";
    }

    if (image) {
        image.alt = item.productTitle || "Ảnh sản phẩm";
        if (typeof item.imageUrl === "string" && item.imageUrl.trim()) {
            image.src = item.imageUrl;
        } else {
            image.removeAttribute("src");
        }
    }

    if (quantity) {
        quantity.textContent = item.quantity == null ? "Số lượng: —" : `SL: ${item.quantity}`;
    }

    if (productMeta) {
        const condition = document.createElement("span");
        condition.append("✓ Độ mới: ");
        const conditionValue = document.createElement("span");
        conditionValue.className = "strong";
        conditionValue.textContent = item.conditionPercent == null ? "—" : `${item.conditionPercent}%`;
        condition.append(conditionValue);

        const seller = document.createElement("span");
        seller.append("Người bán: ");
        const sellerValue = document.createElement("span");
        sellerValue.className = "strong";
        sellerValue.textContent = order.sellerId == null ? "Chưa có thông tin" : `ID ${order.sellerId}`;
        seller.append(sellerValue);

        const price = document.createElement("span");
        price.className = "strong";
        price.textContent = formatCurrency(item.unitPrice);

        productMeta.replaceChildren(condition, document.createTextNode("•"), seller, document.createTextNode("•"), price);
    }
}

function renderRecipientInfo(order) {
    const recipientName = document.getElementById("recipientName");
    const recipientPhone = document.getElementById("recipientPhone");
    const shippingAddress = document.getElementById("shippingAddress");

    if (recipientName) {
        recipientName.textContent = order.recipientName || "Chưa có tên người nhận";
    }

    if (recipientPhone) {
        recipientPhone.textContent = order.recipientPhone || "Chưa có số điện thoại";
    }

    if (shippingAddress) {
        shippingAddress.textContent = order.shippingAddress || "Chưa có địa chỉ giao hàng";
    }
}

function renderOrderDetails(order) {
    const breadcrumbOrder = document.querySelector(".breadcrumb .current");
    const buyerNote = document.getElementById("buyerNote");

    if (breadcrumbOrder) {
        breadcrumbOrder.textContent = `Đơn hàng #${order.orderCode}`;
    }

    if (buyerNote) {
        buyerNote.textContent = order.buyerNote == null || order.buyerNote === ""
            ? "Không có ghi chú giao hàng."
            : String(order.buyerNote);
    }

    const amounts = {
        subtotalAmount: order.subtotalAmount,
        shippingFee: order.shippingFee,
        totalAmount: order.totalAmount
    };

    Object.entries(amounts).forEach(([id, value]) => {
        const element = document.getElementById(id);
        if (element) element.textContent = formatCurrency(value);
    });
}

function formatCurrency(value) {
    if (value == null || !Number.isFinite(Number(value))) {
        return "Chưa có thông tin";
    }
    return Number(value).toLocaleString("vi-VN") + " đ";
}

function renderOrderStatus(order) {
    const paymentLabels = {
        PENDING: "Chờ thanh toán",
        PAID: "Đã thanh toán",
        FAILED: "Thanh toán thất bại",
        REFUNDED: "Đã hoàn tiền"
    };
    const orderStatus = document.getElementById("orderStatus");
    const paymentStatus = document.getElementById("paymentStatus");

    if (orderStatus) {
        const dot = orderStatus.querySelector(".dot");
        orderStatus.textContent = getOrderStatusLabel(order.status);
        if (dot) orderStatus.prepend(dot);
    }

    if (paymentStatus) {
        const paymentLabel = Object.prototype.hasOwnProperty.call(paymentLabels, order.paymentStatus)
            ? paymentLabels[order.paymentStatus]
            : order.paymentStatus;
        paymentStatus.textContent =
            paymentLabel || "Chưa có trạng thái";
    }
}

function getOrderStatusLabel(status) {
    return Object.prototype.hasOwnProperty.call(orderStatusLabels, status)
        ? orderStatusLabels[status]
        : status || "Chưa có trạng thái";
}

function renderOrderTimeline(order) {
    const steps = Array.from(document.querySelectorAll(".steps .step"));
    const trackFill = document.getElementById("trackFill");
    const detailTimeline = document.querySelector(".timeline");
    const progressByStatus = {
        WAITING_PAYMENT: { activeIndex: 0, completedCount: 0 },
        WAITING_CONFIRM: { activeIndex: 1, completedCount: 1 },
        CONFIRMED: { activeIndex: 2, completedCount: 2 },
        SHIPPING: { activeIndex: 3, completedCount: 3 },
        COMPLETED: { activeIndex: -1, completedCount: 5 },
        CANCELLED: { activeIndex: -1, completedCount: 1 }
    };
    const progress = Object.prototype.hasOwnProperty.call(progressByStatus, order.status)
        ? progressByStatus[order.status]
        : { activeIndex: -1, completedCount: 1 };
    const stepContent = [
        {
            title: "1. Đặt hàng",
            note: order.paymentStatus === "PAID" ? "Đã thanh toán" :
                order.paymentStatus === "PENDING" ? "Chờ thanh toán" : "Đơn hàng đã được ghi nhận"
        },
        {
            title: "2. Người bán xác nhận",
            note: progress.completedCount >= 2 ? "Được xác nhận theo trạng thái đơn hàng." :
                order.status === "WAITING_CONFIRM" ? "Đang chờ người bán xác nhận." : "Chưa có dữ liệu xác nhận."
        },
        {
            title: "3. Bắt đầu vận chuyển",
            note: progress.completedCount >= 3 ? "Được xác nhận theo trạng thái đơn hàng." :
                order.status === "CONFIRMED" ? "Chưa có thông tin vận chuyển." : "Chưa có dữ liệu vận chuyển."
        },
        {
            title: "4. Đang giao hàng",
            note: order.status === "SHIPPING" ? "Backend xác nhận đơn hàng đang giao." :
                order.status === "COMPLETED" ? "Đơn hàng đã hoàn thành." : "Chưa có dữ liệu giao hàng."
        },
        {
            title: "5. Đơn hàng hoàn thành",
            note: order.status === "COMPLETED" ? "Backend xác nhận đơn đã hoàn thành." : "Chưa có dữ liệu hoàn thành."
        }
    ];

    steps.forEach((step, index) => {
        const title = step.querySelector(".step-title");
        const time = step.querySelector(".step-time");
        const note = step.querySelector(".step-note");
        const icon = step.querySelector(".step-circle .material-symbols-outlined");
        const isCompleted = index < progress.completedCount;
        const isActive = index === progress.activeIndex;

        step.classList.toggle("completed", isCompleted);
        step.classList.toggle("active", isActive);
        step.classList.toggle("upcoming", !isCompleted && !isActive);

        if (title) title.textContent = stepContent[index].title;
        if (note) note.textContent = stepContent[index].note;
        if (time) {
            const createdAt = index === 0 ? formatOrderDate(order.createdAt) : "";
            time.textContent = createdAt;
            time.hidden = !createdAt;
        }
        if (icon) {
            icon.textContent = isCompleted ? "check" :
                isActive ? ["shopping_bag", "storefront", "local_shipping", "local_shipping", "task_alt"][index] :
                    "radio_button_unchecked";
        }
    });

    if (trackFill) {
        const progressIndex = progress.completedCount === 5
            ? 4
            : Math.max(0, progress.activeIndex, progress.completedCount - 1);
        trackFill.style.width = `${(progressIndex / 4) * 100}%`;
    }

    if (detailTimeline) {
        const currentStatus = getOrderStatusLabel(order.status);
        const entries = [];
        if (order.createdAt) {
            entries.push({
                title: "Đơn hàng được tạo",
                text: "Thời điểm tạo đơn do Backend cung cấp.",
                time: formatOrderDate(order.createdAt)
            });
        }
        entries.push({
            title: `Trạng thái hiện tại: ${currentStatus}`,
            text: "Backend chưa cung cấp lịch sử chi tiết của các mốc vận chuyển.",
            time: ""
        });

        detailTimeline.replaceChildren(...entries.map(entry => {
            const article = document.createElement("article");
            article.className = "log";
            const dot = document.createElement("span");
            dot.className = "log-dot";
            const card = document.createElement("div");
            card.className = "log-card";
            const top = document.createElement("div");
            top.className = "log-top";
            const title = document.createElement("span");
            title.className = "log-title";
            title.textContent = entry.title;
            top.append(title);
            if (entry.time) {
                const time = document.createElement("span");
                time.className = "log-time";
                time.textContent = entry.time;
                top.append(time);
            }
            const text = document.createElement("p");
            text.className = "log-text";
            text.textContent = entry.text;
            card.append(top, text);
            article.append(dot, card);
            return article;
        }));
    }
}

function renderDeliveryInfo(order) {
    const deliveryStatus = document.getElementById("deliveryStatus");
    const deliveryMessage = document.getElementById("deliveryMessage");
    const messages = {
        WAITING_PAYMENT: "Đơn hàng đang chờ thanh toán. Chưa có thông tin vận chuyển.",
        WAITING_CONFIRM: order.paymentStatus === "PAID"
            ? "Đơn hàng đã thanh toán. Đang chờ người bán xác nhận. Chưa có thông tin vận chuyển."
            : "Đang chờ người bán xác nhận. Chưa có thông tin vận chuyển.",
        CONFIRMED: "Người bán đã xác nhận đơn hàng. Backend chưa cung cấp mã vận đơn hoặc thông tin đơn vị vận chuyển.",
        SHIPPING: "Backend xác nhận đơn đang giao. Chưa có thông tin về shipper hoặc vị trí giao hàng.",
        COMPLETED: "Backend xác nhận đơn đã hoàn thành. Không có thêm thông tin vận chuyển.",
        CANCELLED: "Đơn hàng đã bị hủy. Không có thông tin vận chuyển."
    };

    if (deliveryStatus) {
        deliveryStatus.textContent = getOrderStatusLabel(order.status);
    }

    if (deliveryMessage) {
        deliveryMessage.textContent =
            Object.prototype.hasOwnProperty.call(messages, order.status)
                ? messages[order.status]
                : "Backend chưa cung cấp thông tin vận chuyển cho trạng thái này.";
    }
}

function formatOrderDate(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("vi-VN");
}


function onElement(id, event, callback) {
  const element = document.getElementById(id);
  if (!element) {
    console.warn(`Không tìm thấy phần tử #${id} để đăng ký sự kiện ${event}.`);
    return;
  }
  element.addEventListener(event, callback);
}

function showToast(message) {
  if (!toastEl) return;
  toastEl.textContent = message;
  toastEl.classList.add("show");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toastEl.classList.remove("show"), 3000);
}

document.querySelectorAll("[data-toast]").forEach(btn => {
  btn.addEventListener("click", () => showToast(btn.dataset.toast));
});

const pathMap = {
  "kham-pha": "../../tin/html/home.html",
  "dang-tin-tim-mua": "page1.html",
  "quan-ly-don-hang": "page2.html",
  "theo-doi-trang-thai": "page3.html",
  "huy-va-doi-tra": "page5.html",
  "danh-gia-truyen": "page4.html",
  "truyen-yeu-thich": "page6.html"
};

document.querySelectorAll("[data-path]").forEach(link => {
  link.addEventListener("click", e => {
    e.preventDefault();
    const path = link.dataset.path;
    if (pathMap[path] && path !== "theo-doi-trang-thai") {
      window.location.href = pathMap[path];
      return;
    }
    document.querySelectorAll("[data-path]").forEach(x => x.classList.remove("active"));
    document.querySelectorAll(`[data-path="${link.dataset.path}"]`).forEach(x => x.classList.add("active"));
    window.scrollTo({top: 0, behavior: "smooth"});
  });
});

onElement("copyTracking", "click", async () => {
    const code = currentOrder?.orderCode;

    if (!code) {
        showToast("Chưa có thông tin mã đơn hàng.");
        return;
    }

    try {
        await navigator.clipboard.writeText(code);
        showToast(`Đã sao chép mã đơn hàng: ${code}`);
    } catch {
        const textarea = document.createElement("textarea");
        textarea.value = code;
        document.body.appendChild(textarea);
        textarea.select();

        let copied = false;
        try {
            copied = document.execCommand("copy");
        } catch (error) {
            console.error("Không thể sao chép mã đơn hàng:", error);
        } finally {
            textarea.remove();
        }

        showToast(copied
            ? `Đã sao chép mã đơn hàng: ${code}`
            : "Không thể sao chép mã đơn hàng.");
    }
});

onElement("printOrder", "click", () => window.print());

document.addEventListener("keydown", e => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    const globalSearch = document.getElementById("globalSearch");
    if (globalSearch) globalSearch.focus();
  }
});

onElement("support", "click", () => {
  showToast("Kênh hỗ trợ trực tuyến hiện chưa được kết nối.");
});

onElement("newsletter", "submit", e => {
  e.preventDefault();
  const input = document.getElementById("newsletterEmail");
  if (!input.checkValidity()) {
    showToast("Vui lòng nhập email hợp lệ.");
    input.focus();
    return;
  }
  showToast("Đăng ký nhận bản tin MangaTrade thành công!");
  input.value = "";
});
