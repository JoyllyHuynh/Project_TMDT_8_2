import { apiRequest } from "../../shared/api-client.js";
const PENDING_PAYMENT_STORAGE_KEY = "comichub_pending_payment";
const VOUCHER_STORAGE_KEY = "comichub_checkout_voucher_codes";
const ADDRESS_RETURN_STORAGE_KEY = "comichub_checkout_address_return";

const state = {
    productId: null,
    quantity: null,
    buyNowItem: null,
    checkoutSessionId: null,
    idempotencyKey: null,
    submissionFingerprint: null,
    checkoutRequestSnapshot: null,
    submissionSucceeded: false,

    pendingPayment: null,

    product: null,
    address: null,
    addressStatus: "loading",
    addressError: "",
    addressRequestId: 0,
    addressOptions: [],
    selectedAddressId: "",
    addressModalStatus: "idle",
    addressModalError: "",
    addressModalRequestId: 0,
    voucherCodes: {
        platformCode: null,
        sellerCode: null,
        shippingCode: null
    },
    availableVouchers: [],
    voucherFilter: "ALL",
    voucherListStatus: "idle",
    voucherListError: "",
    voucherListRequestId: 0,
    quote: null,
    quotePayloadFingerprint: null,
    quoteRequestId: 0,
    quotePending: false,
    quoteError: "",
    submitting: false
};

document.addEventListener("DOMContentLoaded", initializeCheckout);
window.addEventListener("pageshow", handleCheckoutPageShow);

async function initializeCheckout() {
    const checkoutButton = document.querySelector(".btn-submit-checkout");
    if (checkoutButton) checkoutButton.disabled = true;
    bindCheckoutControls();
    updateCheckoutButton();

    const packageCard = document.querySelector(".package-summary-card");
    if (packageCard) packageCard.innerHTML = `<p role="status">Đang tải sản phẩm...</p>`;
    renderQuote();
    const paymentAmount = document.querySelector(".highlight-amount");
    if (paymentAmount) paymentAmount.textContent = "—";

    const rawData = sessionStorage.getItem("buyNowItem");
    if (!rawData) {
        showMessage("Không tìm thấy sản phẩm đặt hàng. Vui lòng quay lại trang sản phẩm và chọn Mua Ngay.", "error");
        return;
    }

    let buyNowItem;
    try {
        buyNowItem = JSON.parse(rawData);
    } catch {
        showMessage("Thông tin sản phẩm đặt hàng không hợp lệ. Vui lòng chọn Mua Ngay lại.", "error");
        return;
    }

    state.productId = Number(buyNowItem.productId);
    state.quantity = Number(buyNowItem.quantity);
    if (!Number.isSafeInteger(state.productId) || state.productId < 1
        || !Number.isSafeInteger(state.quantity) || state.quantity < 1) {
        showMessage("Thông tin sản phẩm hoặc số lượng không hợp lệ.", "error");
        return;
    }

    state.buyNowItem = buyNowItem;
    if (typeof buyNowItem.checkoutSessionId !== "string"
        || !buyNowItem.checkoutSessionId.trim()) {
        try {
            buyNowItem.checkoutSessionId = createCheckoutSessionId();
            if (!saveBuyNowItem()) {
                throw new Error("Không thể lưu mã phiên checkout.");
            }
        } catch (error) {
            showMessage(error.message || "Không thể khôi phục phiên checkout an toàn.", "error");
            return;
        }
    }
    state.checkoutSessionId = buyNowItem.checkoutSessionId;

    const pendingPayment = readPendingPayment();
    if (pendingPayment?.checkoutSessionId === state.checkoutSessionId) {
        state.pendingPayment = pendingPayment;
        state.submissionSucceeded = true;
        renderMockPaymentPanel(pendingPayment);
        updateCheckoutButton();
        return;
    }
    if (pendingPayment) {
        try {
            sessionStorage.removeItem(PENDING_PAYMENT_STORAGE_KEY);
        } catch (error) {
            console.error("Không thể xóa trạng thái thanh toán cũ:", error);
        }
    }

    state.submissionFingerprint = typeof buyNowItem.checkoutRequestFingerprint === "string"
        ? buyNowItem.checkoutRequestFingerprint
        : null;
    try {
        state.idempotencyKey = ensureIdempotencyKey(buyNowItem);
    } catch (error) {
        showMessage(error.message || "Không thể chuẩn bị khóa đặt hàng an toàn.", "error");
        return;
    }

    state.checkoutRequestSnapshot = readCheckoutRequestSnapshot(buyNowItem);
    if (state.submissionFingerprint && !state.checkoutRequestSnapshot) {
        showMessage(
            "Checkout trước đó đã được gửi nhưng thiếu dữ liệu an toàn để retry. Hãy kiểm tra đơn hàng trước khi tạo checkout mới.",
            "error"
        );
    }

    if (state.checkoutRequestSnapshot) {
        state.quantity = state.checkoutRequestSnapshot.quantity;
        selectCheckoutOption("shipping_method", state.checkoutRequestSnapshot.shippingMethod);
        selectCheckoutOption("payment_method", state.checkoutRequestSnapshot.paymentMethod);
        state.voucherCodes = voucherCodesFromRequest(state.checkoutRequestSnapshot);
        if (isValidQuote(buyNowItem.checkoutQuote)) {
            state.quote = buyNowItem.checkoutQuote;
            state.quotePayloadFingerprint = JSON.stringify(createQuotePayload(
                state.voucherCodes,
                state.checkoutRequestSnapshot.shippingMethod,
                state.quantity
            ));
        }
    } else {
        state.voucherCodes = readVoucherCodes();
    }

    try {
        const response = await withTimeout(
            apiRequest(`/api/products/${state.productId}`),
            "Hết thời gian chờ tải thông tin sản phẩm."
        );
        state.product = response.data;
        if (!state.product) throw new Error("Không tìm thấy dữ liệu sản phẩm.");
        renderProduct();
        updateCheckoutButton();
    } catch (error) {
        showMessage(`Không thể tải thông tin sản phẩm: ${error.message || "Vui lòng thử lại."}`, "error");
        return;
    }

    await loadCheckoutAddress();

    try {
        await refreshQuote(state.voucherCodes, false);
    } catch (error) {
        if (state.checkoutRequestSnapshot) {
            showMessage(
                `Không thể làm mới báo giá cho lần retry; yêu cầu gốc và idempotency key vẫn được giữ nguyên. ${error.message || ""}`.trim(),
                "error"
            );
            renderQuote();
            updateCheckoutButton();
            return;
        }

        const candidateCodes = Object.entries(state.voucherCodes).find(([, code]) => code);
        if (candidateCodes) {
            const [field, code] = candidateCodes;
            document.querySelector("#coupon-code").value = code;
            document.querySelector("#voucher-type").value = field;
            state.voucherCodes = emptyVoucherCodes();
            saveVoucherCodes(state.voucherCodes);
            renderAppliedVouchers();
            showMessage(`Mã đã chọn chưa được xác nhận: ${error.message || "Vui lòng kiểm tra lại mã."}`, "error");
            try {
                await refreshQuote(state.voucherCodes, true);
            } catch (quoteError) {
                showMessage(`Không thể tính đơn hàng: ${quoteError.message || "Vui lòng thử lại."}`, "error");
            }
        } else {
            showMessage(`Không thể tính đơn hàng: ${error.message || "Vui lòng thử lại."}`, "error");
        }
    }
}

function bindCheckoutControls() {
    const applyButton = document.querySelector(".btn-apply-voucher");
    const voucherInput = document.querySelector("#coupon-code");
    const checkoutButton = document.querySelector(".btn-submit-checkout");

    applyButton?.addEventListener("click", applyVoucher);
    voucherInput?.addEventListener("click", openVoucherModal);
    voucherInput?.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            applyVoucher();
        }
    });

    document.querySelectorAll('input[name="shipping_method"]').forEach((input) => {
        input.addEventListener("change", () => {
            refreshQuote(state.voucherCodes, true).catch((error) => {
                handleQuoteChangeFailure(error, "Không thể cập nhật phí vận chuyển.");
            });
            if (!document.querySelector("#voucher-modal")?.hidden) {
                loadAvailableVouchers();
            }
        });
    });

    checkoutButton?.addEventListener("click", submitCheckout);
    document.querySelector("#btn-change-address")
        ?.addEventListener("click", openAddressModal);
    document.querySelector("#address-modal")
        ?.addEventListener("click", handleAddressModalClick);
    document.querySelector("#address-modal-list")
        ?.addEventListener("change", handleAddressSelection);
    document.querySelector("#btn-confirm-address")
        ?.addEventListener("click", confirmSelectedAddress);
    document.querySelector("#btn-retry-address-modal")
        ?.addEventListener("click", loadAddressOptions);
    document.querySelector("#voucher-modal")
        ?.addEventListener("click", handleVoucherModalClick);
    document.querySelector("#voucher-modal-list")
        ?.addEventListener("click", handleAvailableVoucherClick);
    document.querySelector("#btn-retry-vouchers")
        ?.addEventListener("click", loadAvailableVouchers);
    document.querySelector("#btn-apply-manual-modal-voucher")
        ?.addEventListener("click", applyManualVoucherFromModal);
    document.querySelector("#voucher-modal-code")
        ?.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                event.preventDefault();
                applyManualVoucherFromModal();
            }
        });
    document.querySelector("#btn-add-address")
        ?.addEventListener("click", rememberAddressManagementReturn);
    document.addEventListener("keydown", handleAddressModalKeydown);
    document.querySelector("#btn-mock-success")
        ?.addEventListener("click", () => processMockPayment("SUCCESS"));

    document.querySelector("#btn-mock-failed")
        ?.addEventListener("click", () => processMockPayment("FAILED"));

    document.querySelector("#btn-mock-retry")
        ?.addEventListener("click", retryMockPayment);
    document.querySelector("#btn-retry-address")
        ?.addEventListener("click", loadCheckoutAddress);
    document.querySelector(".summary-breakdown-list")
        ?.addEventListener("click", (event) => {
            if (event.target.closest("#btn-retry-quote")) {
                refreshQuote(state.voucherCodes, true).catch((error) => {
                    showMessage(`Không thể tải báo giá: ${error.message || "Vui lòng thử lại."}`, "error");
                });
            }
        });
}

async function processMockPayment(action) {
    const payment = state.pendingPayment;

    if (!payment?.paymentCode) {
        showMockPaymentMessage("Không tìm thấy mã thanh toán.", "error");
        return;
    }

    if (!["SUCCESS", "FAILED"].includes(action)) {
        return;
    }

    const successButton = document.querySelector("#btn-mock-success");
    const failedButton = document.querySelector("#btn-mock-failed");

    successButton.disabled = true;
    failedButton.disabled = true;

    try {
        const response = await apiRequest(
            `/api/payments/mock/${encodeURIComponent(payment.paymentCode)}`,
            {
                method: "POST",
                body: { action }
            }
        );

        const result = response.data;

        if (!result || !result.status) {
            throw new Error("Backend trả về dữ liệu thanh toán không hợp lệ.");
        }

        const updatedPayment = {
            ...payment,
            paymentId: result.paymentId ?? payment.paymentId,
            paymentCode: result.paymentCode ?? payment.paymentCode,
            amount: result.amount ?? payment.amount,
            status: result.status,
            paidAt: result.paidAt ?? null
        };

        if (!savePendingPayment(updatedPayment)) {
            throw new Error(
                "Không thể lưu trạng thái thanh toán vào trình duyệt."
            );
        }

        renderMockPaymentPanel(updatedPayment);

        if (result.status === "SUCCEEDED") {
            const orderCode = typeof updatedPayment.orderCode === "string"
                ? updatedPayment.orderCode.trim()
                : "";
            if (!orderCode) {
                throw new Error(
                    "Thanh toán thành công nhưng không tìm thấy mã đơn hàng để mở trang theo dõi."
                );
            }

            window.location.href =
                `../../thuc/html/page3.html?orderCode=${encodeURIComponent(orderCode)}`;
        } else if (result.status === "FAILED") {
            showMockPaymentMessage(
                "Thanh toán thất bại. Bạn có thể thử thanh toán lại.",
                "error"
            );
        } else {
            throw new Error(`Trạng thái thanh toán không được hỗ trợ: ${result.status}`);
        }
    } catch (error) {
        showMockPaymentMessage(
            error.message || "Không thể xử lý giao dịch. Vui lòng thử lại.",
            "error"
        );
    } finally {
        successButton.disabled = false;
        failedButton.disabled = false;

        renderMockPaymentPanel(state.pendingPayment);
    }
}

async function retryMockPayment() {
    const payment = state.pendingPayment;

    if (!payment?.paymentCode) {
        showMockPaymentMessage("Không tìm thấy mã thanh toán.", "error");
        return;
    }

    const retryButton = document.querySelector("#btn-mock-retry");
    retryButton.disabled = true;

    try {
        const response = await apiRequest(
            `/api/payments/retry/${encodeURIComponent(payment.paymentCode)}`,
            {
                method: "POST"
            }
        );

        const result = response.data;

        if (!result?.paymentCode || !result?.status) {
            throw new Error("Backend trả về thông tin thanh toán lại không hợp lệ.");
        }

        const updatedPayment = {
            ...payment,
            paymentId: result.paymentId,
            paymentCode: result.paymentCode,
            amount: result.amount ?? payment.amount,
            status: result.status,
            paidAt: result.paidAt ?? null
        };

        if (result.status !== "PENDING") {
            throw new Error("Giao dịch mới không ở trạng thái chờ thanh toán.");
        }

        if (!savePendingPayment(updatedPayment)) {
            throw new Error(
                "Không thể lưu mã thanh toán mới vào trình duyệt."
            );
        }

        renderMockPaymentPanel(updatedPayment);

        showMockPaymentMessage(
            "Đã tạo giao dịch mới. Bạn có thể tiếp tục thanh toán.",
            "success"
        );
    } catch (error) {
        showMockPaymentMessage(
            error.message || "Không thể tạo giao dịch thanh toán lại.",
            "error"
        );
    } finally {
        retryButton.disabled = false;
    }
}

function showMockPaymentMessage(message, type) {
    const region = document.querySelector("#mock-payment-message");

    if (!region) return;

    region.textContent = message;
    region.className = `checkout-message ${type}`;
    region.hidden = false;
}

function renderProduct() {
    const product = state.product;
    const imageUrl = product.images?.[0]?.imageUrl
        || product.imageUrls?.[0]
        || "https://via.placeholder.com/150";
    const packageCard = document.querySelector(".package-summary-card");

    if (packageCard) {
        packageCard.innerHTML = `
            <div class="card-header-bar">
                <div class="card-header-title">
                    <i class="fa-solid fa-boxes-packing"></i>
                    <h2>Kiện Hàng Thanh Toán</h2>
                </div>
                <label class="package-count-tag">
                    Số lượng:
                    <input class="checkout-quantity-input" type="number" min="1"
                        ${Number.isInteger(product.stockQuantity) ? `max="${product.stockQuantity}"` : ""}
                        value="${state.quantity}" aria-label="Số lượng sản phẩm">
                </label>
            </div>
            <div class="package-group-card">
                <div class="package-shop-header">
                    <div class="shop-title-wrap">
                        <i class="fa-solid fa-store"></i>
                        <span>Người bán: <strong>${escapeHtml(product.sellerName || "")}</strong></span>
                    </div>
                    <span class="trade-type-pill">Mua Ngay</span>
                </div>
                <ul class="package-items-list">
                    <li class="package-item-row">
                        <div class="package-thumb">
                            <img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(product.title || "Sản phẩm")}">
                        </div>
                        <div class="package-item-details">
                            <h3 class="package-item-name">${escapeHtml(product.title || "Sản phẩm")}</h3>
                            <p class="package-item-meta">${escapeHtml(product.publisherName || "")}</p>
                            <div class="package-item-tags">
                                <span class="tag-qty">SL: ${state.quantity}</span>
                            </div>
                        </div>
                        <div class="package-item-price">
                            <span class="price-value package-subtotal">Đang tính...</span>
                        </div>
                    </li>
                </ul>
            </div>
        `;

        packageCard.querySelector(".checkout-quantity-input")?.addEventListener("change", (event) => {
            const quantity = Number(event.currentTarget.value);
            if (!Number.isSafeInteger(quantity) || quantity < 1) {
                event.currentTarget.value = String(state.quantity);
                return;
            }

            const previousQuantity = state.quantity;
            state.quantity = quantity;
            state.buyNowItem.quantity = quantity;
            if (!saveBuyNowItem()) {
                state.quantity = previousQuantity;
                state.buyNowItem.quantity = state.quantity;
                event.currentTarget.value = String(state.quantity);
                showMessage("Không thể lưu thay đổi checkout. Vui lòng kiểm tra bộ nhớ trình duyệt rồi thử lại.", "error");
                return;
            }
            packageCard.querySelector(".tag-qty").textContent = `SL: ${quantity}`;
            refreshQuote(state.voucherCodes, true).catch((error) => {
                handleQuoteChangeFailure(error, "Không thể cập nhật báo giá.");
            });
            if (!document.querySelector("#voucher-modal")?.hidden) {
                loadAvailableVouchers();
            }
        });
    }
}

function renderAddress() {
    const name = document.querySelector(".recipient-name");
    const phone = document.querySelector(".recipient-phone");
    const address = document.querySelector(".recipient-address");
    const defaultBadge = document.querySelector(".badge-address-default");
    const message = document.querySelector("#address-load-message");
    const retryButton = document.querySelector("#btn-retry-address");

    if (message) {
        message.textContent = state.addressStatus === "loading"
            ? "Đang tải địa chỉ nhận hàng..."
            : state.addressError;
        message.className = `checkout-message${state.addressStatus === "error" ? " error" : ""}`;
        message.hidden = !state.addressError && state.addressStatus !== "loading";
    }
    if (retryButton) {
        retryButton.hidden = state.addressStatus !== "error";
        retryButton.disabled = state.addressStatus === "loading";
    }

    if (!isAddressUsable(state.address)) {
        if (name) name.textContent = state.addressStatus === "loading"
            ? "Đang tải địa chỉ..."
            : "Chưa có địa chỉ nhận hàng";
        if (phone) phone.textContent = "";
        if (address) {
            address.textContent = state.addressStatus === "loading"
                ? "Đang tải địa chỉ nhận hàng..."
                : state.addressStatus === "error"
                    ? "Không thể tải địa chỉ. Hãy thử lại."
                    : "Vui lòng thêm tên người nhận, số điện thoại và địa chỉ đầy đủ.";
        }
        if (defaultBadge) defaultBadge.hidden = true;
        if (state.addressStatus === "empty") {
            showMessage("Địa chỉ nhận hàng chưa đầy đủ. Hãy cập nhật địa chỉ rồi quay lại thanh toán.", "error");
        }
        updateCheckoutButton();
        return;
    }

    if (name) name.textContent = state.address.recipientName || "";
    if (phone) phone.textContent = state.address.phone || "";
    if (address) {
        address.textContent = [
            state.address.addressDetail,
            state.address.ward,
            state.address.district,
            state.address.province
        ].filter(Boolean).join(", ");
    }
    if (defaultBadge) defaultBadge.hidden = !state.address.isDefault;
    updateCheckoutButton();
}

async function loadCheckoutAddress() {
    const requestId = ++state.addressRequestId;
    state.addressStatus = "loading";
    state.addressError = "";
    renderAddress();

    if (state.checkoutRequestSnapshot) {
        state.address = addressFromCheckoutRequest(state.checkoutRequestSnapshot);
        state.addressStatus = "ready";
        renderAddress();
        return;
    }

    try {
        const response = await withTimeout(
            apiRequest("/api/addresses"),
            "Hết thời gian chờ tải địa chỉ nhận hàng."
        );
        if (requestId !== state.addressRequestId) return;
        const addresses = Array.isArray(response.data) ? response.data : [];
        state.address = addresses.find((item) => item?.isDefault) || addresses[0] || null;
        state.addressStatus = isAddressUsable(state.address) ? "ready" : "empty";
    } catch (error) {
        if (requestId !== state.addressRequestId) return;
        state.address = null;
        state.addressStatus = "error";
        state.addressError = error.status === 401 || error.status === 403
            ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại rồi thử tải địa chỉ."
            : `Không thể tải địa chỉ nhận hàng: ${error.message || "Vui lòng thử lại."}`;
    }
    renderAddress();
}

async function openAddressModal() {
    if (state.submitting || state.submissionSucceeded || state.submissionFingerprint) return;
    const modal = document.querySelector("#address-modal");
    if (!modal) return;

    modal.hidden = false;
    document.querySelector("#btn-change-address")?.setAttribute("aria-expanded", "true");
    document.body.classList.add("modal-open");
    document.querySelector("#address-modal-title")?.focus();
    await loadAddressOptions();
}

async function loadAddressOptions() {
    const requestId = ++state.addressModalRequestId;
    state.addressModalStatus = "loading";
    state.addressModalError = "";
    renderAddressModal();

    try {
        const response = await withTimeout(
            apiRequest("/api/addresses"),
            "Hết thời gian chờ tải danh sách địa chỉ."
        );
        if (requestId !== state.addressModalRequestId) return;
        if (!Array.isArray(response.data)) {
            throw new Error("Dữ liệu danh sách địa chỉ từ máy chủ không hợp lệ.");
        }

        state.addressOptions = response.data;
        const selectedAddress = state.addressOptions.find(
            (address) => String(address?.id) === String(state.address?.id)
        ) || state.addressOptions.find((address) => address?.isDefault)
            || state.addressOptions[0]
            || null;
        state.selectedAddressId = selectedAddress?.id == null ? "" : String(selectedAddress.id);
        state.addressModalStatus = state.addressOptions.length ? "success" : "empty";
    } catch (error) {
        if (requestId !== state.addressModalRequestId) return;
        state.addressOptions = [];
        state.selectedAddressId = "";
        state.addressModalStatus = "error";
        state.addressModalError = error.status === 401 || error.status === 403
            ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."
            : error.message || "Không thể tải danh sách địa chỉ.";
    }
    renderAddressModal();
}

function renderAddressModal() {
    const message = document.querySelector("#address-modal-message");
    const retryButton = document.querySelector("#btn-retry-address-modal");
    const list = document.querySelector("#address-modal-list");
    const emptyState = document.querySelector("#address-modal-empty");
    const confirmButton = document.querySelector("#btn-confirm-address");
    if (!message || !list || !emptyState) return;

    list.replaceChildren();
    emptyState.hidden = state.addressModalStatus !== "empty";
    if (state.addressModalStatus === "loading") {
        message.textContent = "Đang tải danh sách địa chỉ...";
        message.className = "address-modal-message loading";
    } else if (state.addressModalStatus === "error") {
        message.textContent = state.addressModalError;
        message.className = "address-modal-message error";
    } else if (state.addressModalStatus === "empty") {
        message.textContent = "";
        message.className = "address-modal-message";
    } else {
        message.textContent = "Chọn địa chỉ dùng cho đơn hàng này.";
        message.className = "address-modal-message";
        state.addressOptions.forEach((address) => {
            const label = document.createElement("label");
            label.className = "address-option";
            label.classList.toggle(
                "selected",
                String(address?.id) === state.selectedAddressId
            );

            const radio = document.createElement("input");
            radio.type = "radio";
            radio.name = "checkout-address-selection";
            radio.value = address?.id == null ? "" : String(address.id);
            radio.checked = radio.value === state.selectedAddressId;
            radio.disabled = !isAddressUsable(address);

            const details = document.createElement("span");
            details.className = "address-option-details";
            const recipient = document.createElement("strong");
            recipient.className = "address-option-recipient";
            recipient.textContent = address?.recipientName || "Chưa có tên người nhận";
            const phone = document.createElement("span");
            phone.className = "address-option-phone";
            phone.textContent = address?.phone || "Chưa có số điện thoại";
            const fullAddress = document.createElement("span");
            fullAddress.className = "address-option-full";
            fullAddress.textContent = addressText(address) || "Chưa có địa chỉ đầy đủ";
            details.append(recipient, phone, fullAddress);

            label.append(radio, details);
            if (address?.isDefault) {
                const badge = document.createElement("span");
                badge.className = "address-option-default";
                badge.textContent = "Mặc định";
                label.append(badge);
            }
            list.append(label);
        });
    }

    if (retryButton) retryButton.hidden = state.addressModalStatus !== "error";
    if (confirmButton) {
        const selectedAddress = state.addressOptions.find(
            (address) => String(address?.id) === state.selectedAddressId
        );
        confirmButton.disabled = state.addressModalStatus !== "success"
            || !isAddressUsable(selectedAddress);
    }
}

function handleAddressSelection(event) {
    const radio = event.target.closest('input[name="checkout-address-selection"]');
    if (!radio) return;
    state.selectedAddressId = radio.value;
    document.querySelectorAll(".address-option").forEach((option) => {
        option.classList.toggle(
            "selected",
            option.querySelector('input[name="checkout-address-selection"]')?.checked === true
        );
    });
    const selectedAddress = state.addressOptions.find(
        (address) => String(address?.id) === state.selectedAddressId
    );
    const confirmButton = document.querySelector("#btn-confirm-address");
    if (confirmButton) confirmButton.disabled = !isAddressUsable(selectedAddress);
}

function confirmSelectedAddress() {
    const selectedAddress = state.addressOptions.find(
        (address) => String(address?.id) === state.selectedAddressId
    );
    if (!isAddressUsable(selectedAddress)) return;

    state.address = selectedAddress;
    state.addressStatus = "ready";
    state.addressError = "";
    renderAddress();
    closeAddressModal();

    if (Object.values(state.voucherCodes).some(Boolean)) {
        refreshQuote(state.voucherCodes, true).catch((error) => {
            handleQuoteChangeFailure(error, "Không thể xác nhận lại voucher sau khi đổi địa chỉ.");
        });
    }
}

function handleAddressModalClick(event) {
    if (event.target.closest("[data-close-address-modal]")) {
        closeAddressModal();
    }
}

function handleAddressModalKeydown(event) {
    if (event.key !== "Escape") return;
    if (!document.querySelector("#address-modal")?.hidden) {
        closeAddressModal();
    } else if (!document.querySelector("#voucher-modal")?.hidden) {
        closeVoucherModal();
    }
}

function closeAddressModal() {
    const modal = document.querySelector("#address-modal");
    if (!modal) return;
    modal.hidden = true;
    document.querySelector("#btn-change-address")?.setAttribute("aria-expanded", "false");
    document.body.classList.remove("modal-open");
    document.querySelector("#btn-change-address")?.focus();
}

function rememberAddressManagementReturn() {
    try {
        sessionStorage.setItem(ADDRESS_RETURN_STORAGE_KEY, "true");
    } catch (error) {
        console.error("Không thể lưu trạng thái quay lại checkout:", error);
    }
    closeAddressModal();
}

function handleCheckoutPageShow() {
    try {
        if (sessionStorage.getItem(ADDRESS_RETURN_STORAGE_KEY) !== "true") return;
        sessionStorage.removeItem(ADDRESS_RETURN_STORAGE_KEY);
    } catch (error) {
        console.error("Không thể kiểm tra trạng thái quay lại checkout:", error);
    }
    loadCheckoutAddress();
    if (!document.querySelector("#address-modal")?.hidden) {
        loadAddressOptions();
    }
}

async function applyVoucher() {
    const input = document.querySelector("#coupon-code");
    const typeSelect = document.querySelector("#voucher-type");
    const applyButton = document.querySelector(".btn-apply-voucher");
    const code = input?.value.trim();
    const field = typeSelect?.value;

    if (!state.product || !state.productId || !state.quantity) {
        showMessage("Chưa thể kiểm tra voucher vì dữ liệu Buy Now chưa tải xong.", "error");
        return false;
    }

    if (!code) {
        showMessage("Vui lòng nhập mã voucher.", "error");
        return false;
    }
    if (!["platformCode", "sellerCode", "shippingCode"].includes(field)) {
        showMessage("Vui lòng chọn đúng loại voucher.", "error");
        return false;
    }

    const candidateCodes = { ...state.voucherCodes, [field]: code };
    const modalApplyButton = document.querySelector("#btn-apply-manual-modal-voucher");
    const isVoucherModalOpen = !document.querySelector("#voucher-modal")?.hidden;
    if (isVoucherModalOpen) {
        setVoucherModalMessage("Đang kiểm tra voucher với Backend...", "loading");
    }
    if (applyButton) {
        applyButton.disabled = true;
        applyButton.textContent = "Đang kiểm tra...";
    }
    if (modalApplyButton) modalApplyButton.disabled = true;

    try {
        const quoteUpdated = await refreshQuote(candidateCodes, false);
        if (!quoteUpdated) return false;
        state.voucherCodes = candidateCodes;
        renderAppliedVouchers();
        input.value = "";
        const saved = saveVoucherCodes(candidateCodes);
        if (isVoucherModalOpen) {
            setVoucherModalMessage("Voucher đã được Backend xác nhận và áp dụng.", "success");
        }
        showMessage(
            saved
                ? "Voucher đã được Backend xác nhận và áp dụng."
                : "Voucher đã được Backend xác nhận cho đơn hiện tại, nhưng không thể lưu lựa chọn trong trình duyệt.",
            saved ? "success" : "error"
        );
        return true;
    } catch (error) {
        const message = error.message || "Backend không chấp nhận voucher này.";
        if (isVoucherModalOpen) setVoucherModalMessage(message, "error");
        else showMessage(message, "error");
        return false;
    } finally {
        if (applyButton) {
            applyButton.textContent = "Áp dụng";
        }
        if (modalApplyButton) modalApplyButton.disabled = false;
        updateCheckoutButton();
    }
}

function setVoucherModalMessage(message, type = "") {
    const region = document.querySelector("#voucher-modal-message");
    if (!region) return;
    region.textContent = message;
    region.className = `voucher-modal-message${type ? ` ${type}` : ""}`;
}

async function openVoucherModal() {
    if (state.submitting || state.submissionSucceeded || state.submissionFingerprint) return;
    if (!state.product || !state.productId || !state.quantity) {
        showMessage("Vui lòng đợi tải sản phẩm trước khi chọn voucher.", "error");
        return;
    }

    const modal = document.querySelector("#voucher-modal");
    const manualCode = document.querySelector("#voucher-modal-code");
    if (!modal) return;

    modal.hidden = false;
    document.querySelector("#coupon-code")?.setAttribute("aria-expanded", "true");
    document.body.classList.add("modal-open");
    if (manualCode) {
        manualCode.value = document.querySelector("#coupon-code")?.value || "";
        document.querySelector("#voucher-modal-type").value =
            document.querySelector("#voucher-type")?.value || "platformCode";
        manualCode.focus();
    }
    await loadAvailableVouchers();
}

async function loadAvailableVouchers() {
    const requestId = ++state.voucherListRequestId;
    state.voucherListStatus = "loading";
    state.voucherListError = "";
    renderAvailableVouchers();

    try {
        const response = await withTimeout(
            apiRequest("/api/vouchers/available", {
                method: "POST",
                body: {
                    productId: state.productId,
                    quantity: state.quantity,
                    shippingMethod: (
                        document.querySelector('input[name="shipping_method"]:checked')?.value || "standard"
                    ).toUpperCase()
                }
            }),
            "Hết thời gian chờ tải danh sách voucher."
        );
        if (requestId !== state.voucherListRequestId) return;
        if (!Array.isArray(response.data)) {
            throw new Error("Dữ liệu danh sách voucher từ máy chủ không hợp lệ.");
        }

        state.availableVouchers = response.data.filter(isAvailableVoucher);
        state.voucherListStatus = "success";
    } catch (error) {
        if (requestId !== state.voucherListRequestId) return;
        state.availableVouchers = [];
        state.voucherListStatus = "error";
        state.voucherListError = error.status === 401 || error.status === 403
            ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."
            : error.message || "Không thể tải voucher phù hợp.";
    }
    renderAvailableVouchers();
}

function isAvailableVoucher(voucher) {
    return Boolean(
        voucher
        && typeof voucher.code === "string"
        && voucher.code.trim()
        && ["PLATFORM", "SELLER", "SHIPPING"].includes(voucher.voucherType)
        && ["FIXED", "PERCENTAGE"].includes(voucher.discountType)
        && voucher.discountValue !== null
        && voucher.discountValue !== ""
        && Number.isFinite(Number(voucher.discountValue))
    );
}

function renderAvailableVouchers() {
    const message = document.querySelector("#voucher-modal-message");
    const list = document.querySelector("#voucher-modal-list");
    const empty = document.querySelector("#voucher-modal-empty");
    const retry = document.querySelector("#btn-retry-vouchers");
    if (!message || !list || !empty) return;

    list.replaceChildren();
    empty.hidden = true;
    if (state.voucherListStatus === "loading") {
        message.textContent = "Đang tìm voucher có thể áp dụng cho đơn hàng...";
        message.className = "voucher-modal-message loading";
    } else if (state.voucherListStatus === "error") {
        message.textContent = state.voucherListError;
        message.className = "voucher-modal-message error";
    } else if (state.voucherListStatus === "success") {
        message.textContent = "Danh sách đã được Backend kiểm tra theo điều kiện đơn hàng hiện tại.";
        message.className = "voucher-modal-message";
        const vouchers = state.voucherFilter === "ALL"
            ? state.availableVouchers
            : state.availableVouchers.filter((voucher) => voucher.voucherType === state.voucherFilter);

        if (!vouchers.length) {
            empty.textContent = state.availableVouchers.length
                ? "Không có voucher phù hợp với loại đang chọn."
                : "Chưa có voucher phù hợp cho đơn hàng này.";
            empty.hidden = false;
        } else {
            vouchers.forEach((voucher) => list.append(createAvailableVoucherCard(voucher)));
        }
    } else {
        message.textContent = "";
        message.className = "voucher-modal-message";
    }

    if (retry) retry.hidden = state.voucherListStatus !== "error";
    document.querySelectorAll("[data-voucher-filter]").forEach((button) => {
        button.setAttribute(
            "aria-pressed",
            String(button.dataset.voucherFilter === state.voucherFilter)
        );
    });
}

function createAvailableVoucherCard(voucher) {
    const field = voucherFieldForType(voucher.voucherType);
    const applied = state.voucherCodes[field]?.toUpperCase() === voucher.code.toUpperCase();
    const card = document.createElement("article");
    card.className = "voucher-option";
    if (applied) card.classList.add("applied");

    const heading = document.createElement("div");
    heading.className = "voucher-option-heading";
    const name = document.createElement("strong");
    name.textContent = voucher.name || voucher.code;
    const type = document.createElement("span");
    type.className = "voucher-option-type";
    type.textContent = voucher.voucherType;
    heading.append(name, type);

    const code = document.createElement("p");
    code.className = "voucher-option-code";
    code.textContent = voucher.code;

    const description = document.createElement("p");
    description.className = "voucher-option-description";
    description.textContent = voucher.description || "Ưu đãi do Backend cung cấp.";

    const facts = document.createElement("dl");
    facts.className = "voucher-option-facts";
    appendVoucherFact(facts, "Mức giảm", formatVoucherDiscount(voucher));
    appendVoucherFact(
        facts,
        "Đơn tối thiểu",
        Number(voucher.minOrderAmount) > 0
            ? formatVnd(voucher.minOrderAmount)
            : "Không yêu cầu"
    );
    if (voucher.maxDiscountAmount != null) {
        appendVoucherFact(facts, "Giảm tối đa", formatVnd(voucher.maxDiscountAmount));
    }
    appendVoucherFact(facts, "Hạn sử dụng", formatVoucherDate(voucher.endAt));

    const apply = document.createElement("button");
    apply.type = "button";
    apply.className = "voucher-option-apply";
    apply.dataset.voucherCode = voucher.code;
    apply.dataset.voucherType = field;
    apply.textContent = applied ? "Đang áp dụng" : "Áp dụng";
    apply.disabled = applied;

    card.append(heading, code, description, facts, apply);
    return card;
}

function appendVoucherFact(list, label, value) {
    const row = document.createElement("div");
    const term = document.createElement("dt");
    term.textContent = label;
    const description = document.createElement("dd");
    description.textContent = value;
    row.append(term, description);
    list.append(row);
}

function formatVoucherDiscount(voucher) {
    if (voucher.discountType === "PERCENTAGE") {
        return `${Number(voucher.discountValue).toLocaleString("vi-VN")}%`;
    }
    return formatVnd(voucher.discountValue);
}

function formatVoucherDate(value) {
    if (!value) return "Không có thông tin";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Không có thông tin" : date.toLocaleString("vi-VN");
}

function voucherFieldForType(type) {
    return {
        PLATFORM: "platformCode",
        SELLER: "sellerCode",
        SHIPPING: "shippingCode"
    }[type] || "";
}

function handleVoucherModalClick(event) {
    const closeButton = event.target.closest("[data-close-voucher-modal]");
    if (closeButton) {
        closeVoucherModal();
        return;
    }

    const filterButton = event.target.closest("[data-voucher-filter]");
    if (filterButton) {
        state.voucherFilter = filterButton.dataset.voucherFilter || "ALL";
        renderAvailableVouchers();
    }
}

async function handleAvailableVoucherClick(event) {
    const button = event.target.closest("[data-voucher-code]");
    if (!button || button.disabled) return;
    button.disabled = true;
    const input = document.querySelector("#coupon-code");
    const typeSelect = document.querySelector("#voucher-type");
    if (input) input.value = button.dataset.voucherCode;
    if (typeSelect) typeSelect.value = button.dataset.voucherType;

    if (await applyVoucher()) {
        closeVoucherModal();
    } else {
        button.disabled = false;
    }
}

async function applyManualVoucherFromModal() {
    const code = document.querySelector("#voucher-modal-code")?.value.trim();
    const field = document.querySelector("#voucher-modal-type")?.value;
    const input = document.querySelector("#coupon-code");
    const typeSelect = document.querySelector("#voucher-type");
    if (input) input.value = code || "";
    if (typeSelect) typeSelect.value = field || "platformCode";

    const applyButton = document.querySelector("#btn-apply-manual-modal-voucher");
    if (applyButton) applyButton.disabled = true;
    try {
        if (await applyVoucher()) {
            document.querySelector("#voucher-modal-code").value = "";
            closeVoucherModal();
        }
    } finally {
        if (applyButton) applyButton.disabled = false;
    }
}

function closeVoucherModal() {
    const modal = document.querySelector("#voucher-modal");
    if (!modal) return;
    modal.hidden = true;
    document.querySelector("#coupon-code")?.setAttribute("aria-expanded", "false");
    document.body.classList.remove("modal-open");
    document.querySelector("#coupon-code")?.focus();
}

async function refreshQuote(
    voucherCodes,
    clearPreviousQuote,
    shippingMethod = document.querySelector('input[name="shipping_method"]:checked')?.value || "standard",
    quantity = state.quantity
) {
    const requestId = ++state.quoteRequestId;
    const payload = createQuotePayload(voucherCodes, shippingMethod, quantity);

    state.quotePending = true;
    state.quoteError = "";
    if (clearPreviousQuote) {
        state.quote = null;
        state.quotePayloadFingerprint = null;
        renderQuote();
    }
    updateCheckoutButton();

    try {
        const response = await withTimeout(
            apiRequest("/api/vouchers/validate", {
                method: "POST",
                body: payload
            }),
            "Hết thời gian chờ lấy báo giá."
        );
        if (requestId !== state.quoteRequestId) return false;
        if (!isValidQuote(response.data)) {
            throw new Error("Dữ liệu báo giá từ máy chủ không hợp lệ.");
        }
        state.quote = response.data;
        state.quotePayloadFingerprint = JSON.stringify(payload);
        renderQuote();
        return true;
    } catch (error) {
        if (requestId === state.quoteRequestId) {
            state.quoteError = error.message || "Không thể tải báo giá.";
            renderQuote();
        }
        throw error;
    } finally {
        if (requestId === state.quoteRequestId) {
            state.quotePending = false;
            renderQuote();
            updateCheckoutButton();
        }
    }
}

async function handleQuoteChangeFailure(error, context) {
    const hasAppliedVouchers = Object.values(state.voucherCodes).some(Boolean);
    if (error.status === 400 && hasAppliedVouchers) {
        const noVouchers = emptyVoucherCodes();
        try {
            await refreshQuote(noVouchers, true);
            state.voucherCodes = noVouchers;
            saveVoucherCodes(noVouchers);
            renderAppliedVouchers();
            showMessage(
                "Điều kiện đơn hàng đã thay đổi; voucher cũ không còn hợp lệ. Hãy chọn lại voucher phù hợp.",
                "error"
            );
            return;
        } catch (quoteError) {
            showMessage(
                `${context} ${quoteError.message || "Vui lòng thử lại."}`,
                "error"
            );
            return;
        }
    }

    showMessage(`${context} ${error.message || "Vui lòng thử lại."}`, "error");
}

function isValidQuote(quote) {
    return quote && [
        "subtotalAmount",
        "shippingFee",
        "platformDiscount",
        "sellerDiscount",
        "shippingDiscount",
        "totalDiscount",
        "totalAmount"
    ].every((key) => quote[key] !== null && quote[key] !== ""
        && Number.isFinite(Number(quote[key])));
}

function renderQuote() {
    const summary = document.querySelector(".summary-breakdown-list");
    if (!summary) return;

    if (!state.quote) {
        summary.replaceChildren();
        const message = document.createElement("p");
        message.setAttribute("role", "status");
        message.textContent = state.quotePending
            ? "Đang lấy báo giá từ máy chủ..."
            : state.quoteError || "Chưa có báo giá hợp lệ.";
        summary.append(message);
        if (!state.quotePending) {
            const retry = document.createElement("button");
            retry.type = "button";
            retry.id = "btn-retry-quote";
            retry.className = "btn-text-action";
            retry.textContent = "Thử tải lại báo giá";
            summary.append(retry);
        }
        const subtotal = document.querySelector(".package-subtotal");
        if (subtotal) subtotal.textContent = state.quotePending ? "Đang tính..." : "—";
        const amount = document.querySelector(".highlight-amount");
        if (amount) amount.textContent = "—";
        renderAppliedVouchers();
        return;
    }

    const quote = state.quote;
    const rows = [
        ["Tạm tính tiền hàng:", quote.subtotalAmount, "subtotal"],
        ["Phí vận chuyển:", quote.shippingFee, "shipping"],
        ["Giảm giá từ sàn:", quote.platformDiscount, "platformDiscount"],
        ["Giảm giá từ người bán:", quote.sellerDiscount, "sellerDiscount"],
        ["Giảm phí vận chuyển:", quote.shippingDiscount, "shippingDiscount"]
    ];
    summary.innerHTML = `
        ${rows.map(([label, amount, key]) => `
            <div class="breakdown-item ${String(key).includes("Discount") ? "discount-item" : ""}">
                <span class="item-label">${label}</span>
                <span class="item-value ${String(key).includes("Discount") ? "text-discount" : ""}">
                    ${String(key).includes("Discount") && Number(amount) > 0 ? "-" : ""}${formatVnd(amount)}
                </span>
            </div>
        `).join("")}
        <div class="breakdown-item discount-item">
            <span class="item-label">Tổng giảm giá:</span>
            <span class="item-value text-discount">-${formatVnd(quote.totalDiscount)}</span>
        </div>
        <div class="summary-separator-line"></div>
        <div class="total-payable-row">
            <div class="total-text-group">
                <span class="total-title">Tổng thanh toán:</span>
            </div>
            <div class="total-price-display">
                <span class="grand-total-amount">${formatVnd(quote.totalAmount)}</span>
            </div>
        </div>
    `;

    const subtotal = document.querySelector(".package-subtotal");
    if (subtotal) subtotal.textContent = formatVnd(quote.subtotalAmount);
    const amount = document.querySelector(".highlight-amount");
    if (amount) amount.textContent = formatVnd(quote.totalAmount);

    document.querySelectorAll(".shipping-price").forEach((element) => {
        const method = element.closest(".shipping-option-item")?.querySelector(".shipping-radio")?.value;
        element.textContent = method === document.querySelector('input[name="shipping_method"]:checked')?.value
            ? formatVnd(quote.shippingFee)
            : "Chọn để tính phí";
    });

    renderAppliedVouchers();
    updateCheckoutButton();
}

function renderAppliedVouchers() {
    const container = document.querySelector(".applied-vouchers-group");
    if (!container) return;
    container.replaceChildren();

    const discounts = {
        platformCode: ["PLATFORM", state.quote?.platformDiscount, "fa-tag"],
        sellerCode: ["SELLER", state.quote?.sellerDiscount, "fa-store"],
        shippingCode: ["SHIPPING", state.quote?.shippingDiscount, "fa-truck-fast"]
    };

    Object.entries(state.voucherCodes).forEach(([field, code]) => {
        if (!code || !state.quote) return;
        const [type, discount, icon] = discounts[field];
        const pill = document.createElement("div");
        pill.className = "applied-voucher-pill";

        const iconElement = document.createElement("i");
        iconElement.className = `fa-solid ${icon}`;
        const name = document.createElement("span");
        name.className = "pill-name";
        name.textContent = `${code} (${type}: -${formatVnd(discount)})`;
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn-remove-pill";
        remove.setAttribute("aria-label", `Gỡ mã ${code}`);
        remove.textContent = "×";
        remove.addEventListener("click", () => removeVoucher(field));

        pill.append(iconElement, name, remove);
        container.append(pill);
    });
}

async function removeVoucher(field) {
    const candidateCodes = { ...state.voucherCodes, [field]: null };
    try {
        const quoteUpdated = await refreshQuote(candidateCodes, false);
        if (!quoteUpdated) return;
        state.voucherCodes = candidateCodes;
        renderAppliedVouchers();
        const saved = saveVoucherCodes(candidateCodes);
        showMessage(
            saved
                ? "Đã gỡ voucher và cập nhật lại báo giá."
                : "Đã gỡ voucher cho đơn hiện tại, nhưng không thể lưu thay đổi trong trình duyệt.",
            saved ? "success" : "error"
        );
    } catch (error) {
        showMessage(`Không thể cập nhật báo giá: ${error.message || "Vui lòng thử lại."}`, "error");
    }
}

async function submitCheckout(event) {
    event.preventDefault();
    const blockedReason = checkoutBlockedReason();
    if (blockedReason) {
        showMessage(blockedReason, "error");
        return;
    }

    const button = document.querySelector(".btn-submit-checkout");
    const selectedPayment = document.querySelector('input[name="payment_method"]:checked')?.value || "vnpay";
    const selectedShipping = document.querySelector('input[name="shipping_method"]:checked')?.value || "standard";
    const shippingAddress = addressText(state.address);
    const requestFields = {
        productId: state.productId,
        quantity: state.quantity,
        recipientName: state.address.recipientName,
        recipientPhone: state.address.phone,
        shippingAddress,
        shippingMethod: selectedShipping.toUpperCase(),
        paymentMethod: selectedPayment.toUpperCase(),
        ...state.voucherCodes
    };
    const quotePayload = createQuotePayload(
        state.voucherCodes,
        requestFields.shippingMethod,
        state.quantity
    );

    state.submitting = true;
    if (button) {
        button.disabled = true;
        const label = button.querySelector("span");
        if (label) label.textContent = "Đang kiểm tra báo giá...";
    }
    updateCheckoutButton();

    try {
        if (!state.submissionFingerprint
            && state.quotePayloadFingerprint !== JSON.stringify(quotePayload)) {
            try {
                const quoteUpdated = await refreshQuote(
                    state.voucherCodes,
                    true,
                    selectedShipping,
                    state.quantity
                );
                if (!quoteUpdated) return;
            } catch (error) {
                showMessage(
                    `Không thể xác nhận lại báo giá; chưa gửi yêu cầu đặt hàng. ${error.message || "Vui lòng thử lại."}`,
                    "error"
                );
                return;
            }
        }

        const requestFingerprint = JSON.stringify(requestFields);
        if (state.submissionFingerprint && state.submissionFingerprint !== requestFingerprint) {
            showMessage(
                "Thông tin checkout đã thay đổi sau lần gửi trước. Để tránh tạo đơn trùng, không thể dùng lại khóa này với nội dung khác. Hãy kiểm tra trạng thái đơn hàng trước khi bắt đầu checkout mới.",
                "error"
            );
            return;
        }
        if (!state.submissionFingerprint) {
            const payload = { ...requestFields, idempotencyKey: state.idempotencyKey };
            state.buyNowItem.checkoutRequestFingerprint = requestFingerprint;
            state.buyNowItem.checkoutRequest = payload;
            state.buyNowItem.checkoutQuote = state.quote;
            if (!saveBuyNowItem()) {
                delete state.buyNowItem.checkoutRequestFingerprint;
                delete state.buyNowItem.checkoutRequest;
                delete state.buyNowItem.checkoutQuote;
                showMessage("Không thể lưu trạng thái retry an toàn. Yêu cầu đặt hàng chưa được gửi.", "error");
                return;
            }
            state.submissionFingerprint = requestFingerprint;
            state.checkoutRequestSnapshot = payload;
        }

        if (state.submissionFingerprint !== requestFingerprint) {
            showMessage(
                "Thông tin checkout đã thay đổi sau lần gửi trước. Để tránh tạo đơn trùng, không thể dùng lại khóa này với nội dung khác. Hãy kiểm tra trạng thái đơn hàng trước khi bắt đầu checkout mới.",
                "error"
            );
            return;
        }

        const payload = state.checkoutRequestSnapshot;
        if (button) {
            const label = button.querySelector("span");
            if (label) label.textContent = "Đang xử lý...";
        }

        const response = await apiRequest("/api/orders/buy-now", {
            method: "POST",
            body: payload
        });

        const order = response.data;

        if (
            !order?.orderCode
            || !order?.paymentCode
            || !Number.isFinite(Number(order.totalAmount))
        ) {
            throw new Error(
                "Backend chưa trả về đầy đủ mã đơn hàng, mã thanh toán hoặc số tiền."
            );
        }

        // Lưu thông tin đơn hàng và giao dịch đang chờ thanh toán.
        const pendingPayment = {
            orderCode: order.orderCode,
            paymentCode: order.paymentCode,
            paymentId: order.paymentId ?? null,
            amount: Number(order.totalAmount),
            status: "PENDING",
            paidAt: null,
            checkoutSessionId: state.checkoutSessionId
        };

        // Phải lưu thành công trước khi khóa checkout.
        if (!savePendingPayment(pendingPayment)) {
            throw new Error(
                "Đơn hàng có thể đã được tạo nhưng không thể lưu thông tin thanh toán. "
                + "Vui lòng không tạo checkout mới; hãy kiểm tra đơn hàng trước."
            );
        }

        state.submissionSucceeded = true;

        // Hiển thị giao diện thanh toán.
        renderMockPaymentPanel(pendingPayment);

        showMessage(
            `Đã tạo đơn hàng ${order.orderCode}. Vui lòng hoàn tất thanh toán.`,
            "success"
        );

        updateCheckoutButton();
    } catch (error) {
        showMessage(checkoutErrorMessage(error), "error");
    } finally {
        state.submitting = false;
        if (button && !state.submissionSucceeded) {
            const label = button.querySelector("span");
            if (label) label.textContent = "Xác Nhận Đặt Hàng";
        }
        updateCheckoutButton();
    }
}

function ensureIdempotencyKey(buyNowItem) {
    if (typeof buyNowItem.idempotencyKey === "string" && buyNowItem.idempotencyKey.trim()) {
        return buyNowItem.idempotencyKey;
    }
    if (!window.crypto?.getRandomValues) {
        throw new Error("Trình duyệt không hỗ trợ tạo khóa checkout an toàn.");
    }

    let key;
    if (window.crypto.randomUUID) {
        key = window.crypto.randomUUID();
    } else {
        const bytes = new Uint8Array(16);
        window.crypto.getRandomValues(bytes);
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
        key = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }

    buyNowItem.idempotencyKey = key;
    if (!saveBuyNowItem()) {
        delete buyNowItem.idempotencyKey;
        throw new Error("Không thể lưu khóa checkout. Yêu cầu đặt hàng chưa được gửi.");
    }
    return key;
}

function createCheckoutSessionId() {
    if (!window.crypto?.getRandomValues) {
        throw new Error("Trình duyệt không hỗ trợ tạo mã phiên checkout an toàn.");
    }
    if (window.crypto.randomUUID) return window.crypto.randomUUID();

    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function withTimeout(request, message, timeoutMs = 15000) {
    let timeoutId;
    const timeout = new Promise((_, reject) => {
        timeoutId = window.setTimeout(() => reject(new Error(message)), timeoutMs);
    });
    return Promise.race([request, timeout]).finally(() => window.clearTimeout(timeoutId));
}

function readCheckoutRequestSnapshot(buyNowItem) {
    const request = buyNowItem.checkoutRequest;
    if (!request || typeof request !== "object"
        || request.productId !== state.productId
        || request.idempotencyKey !== buyNowItem.idempotencyKey
        || typeof buyNowItem.checkoutRequestFingerprint !== "string") {
        return null;
    }

    const { idempotencyKey, ...requestFields } = request;
    if (JSON.stringify(requestFields) !== buyNowItem.checkoutRequestFingerprint
        || !Number.isSafeInteger(request.quantity)
        || request.quantity < 1
        || !["STANDARD", "FAST"].includes(request.shippingMethod)
        || !["VNPAY", "MOMO", "VIETQR", "CREDIT_CARD"].includes(request.paymentMethod)
        || !["recipientName", "recipientPhone", "shippingAddress"].every(
            (field) => typeof request[field] === "string" && request[field].trim()
        )) {
        return null;
    }

    return request;
}

function voucherCodesFromRequest(request) {
    return {
        platformCode: request.platformCode || null,
        sellerCode: request.sellerCode || null,
        shippingCode: request.shippingCode || null
    };
}

function addressFromCheckoutRequest(request) {
    return {
        recipientName: request.recipientName,
        phone: request.recipientPhone,
        addressDetail: request.shippingAddress,
        ward: "",
        district: "",
        province: "",
        isDefault: false
    };
}

function selectCheckoutOption(name, value) {
    const option = Array.from(document.querySelectorAll(`input[name="${name}"]`))
        .find((input) => input.value.toUpperCase() === value);
    if (option) option.checked = true;
}

function createQuotePayload(voucherCodes, shippingMethod, quantity) {
    return {
        productId: state.productId,
        quantity,
        shippingMethod: shippingMethod.toUpperCase(),
        ...voucherCodes
    };
}

function saveBuyNowItem() {
    try {
        sessionStorage.setItem("buyNowItem", JSON.stringify(state.buyNowItem));
        return true;
    } catch {
        return false;
    }
}

function readPendingPayment() {
    try {
        const rawData = sessionStorage.getItem(PENDING_PAYMENT_STORAGE_KEY);

        if (!rawData) return null;

        const payment = JSON.parse(rawData);

        if (
            !payment
            || typeof payment.orderCode !== "string"
            || !payment.orderCode.trim()
            || typeof payment.paymentCode !== "string"
            || !payment.paymentCode.trim()
            || !Number.isFinite(Number(payment.amount))
            || Number(payment.amount) < 0
        ) {
            sessionStorage.removeItem(PENDING_PAYMENT_STORAGE_KEY);
            return null;
        }

        return payment;
    } catch {
        sessionStorage.removeItem(PENDING_PAYMENT_STORAGE_KEY);
        return null;
    }
}

function checkoutErrorMessage(error) {
    if (error.status === 400) {
        return `Backend từ chối dữ liệu đặt hàng: ${error.message || "Vui lòng kiểm tra thông tin checkout."}`;
    }
    if (error.status === 401) {
        return "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Đăng nhập lại rồi thử lại cùng checkout này.";
    }
    if (error.status === 403) {
        return `Tài khoản không có quyền đặt đơn này: ${error.message || "Vui lòng liên hệ hỗ trợ."}`;
    }
    if (error.status === 409) {
        return `Backend báo xung đột idempotency: ${error.message || "Khóa này đã gắn với yêu cầu khác."} Không tự tạo khóa mới; hãy kiểm tra đơn hàng trước khi bắt đầu checkout khác.`;
    }
    if (!error.status || error.status === 408 || error.status >= 500) {
        return `Chưa xác định được Backend đã tạo đơn hay chưa do lỗi kết nối. Hãy thử lại với checkout không thay đổi; khóa idempotency được giữ nguyên. ${error.message || ""}`.trim();
    }
    return `Đặt hàng thất bại (${error.status}): ${error.message || "Vui lòng thử lại."}`;
}

function readVoucherCodes() {
    const codes = emptyVoucherCodes();
    try {
        const saved = JSON.parse(sessionStorage.getItem(VOUCHER_STORAGE_KEY) || "null");
        for (const field of Object.keys(codes)) {
            if (typeof saved?.[field] === "string" && saved[field].trim()) {
                codes[field] = saved[field].trim();
            }
        }
    } catch {
        sessionStorage.removeItem(VOUCHER_STORAGE_KEY);
    }
    return codes;
}

function emptyVoucherCodes() {
    return { platformCode: null, sellerCode: null, shippingCode: null };
}

function saveVoucherCodes(codes) {
    try {
        sessionStorage.setItem(VOUCHER_STORAGE_KEY, JSON.stringify(codes));
        return true;
    } catch {
        return false;
    }
}

function updateCheckoutButton() {
    const button = document.querySelector(".btn-submit-checkout");
    const changeAddressButton = document.querySelector("#btn-change-address");
    const blockedReason = checkoutBlockedReason();
    const checkoutLocked = state.submitting || state.submissionSucceeded || Boolean(state.submissionFingerprint);
    if (button) {
        button.disabled = Boolean(blockedReason);
        button.setAttribute("aria-describedby", "checkout-submit-status");
    }
    if (changeAddressButton) {
        changeAddressButton.disabled = checkoutLocked;
        changeAddressButton.setAttribute("aria-expanded", String(
            !document.querySelector("#address-modal")?.hidden
        ));
    }

    const status = document.querySelector("#checkout-submit-status");
    if (status) {
        status.textContent = blockedReason || "";
        status.hidden = !blockedReason;
        status.className = `checkout-message${blockedReason ? " error" : ""}`;
    }

    const applyButton = document.querySelector(".btn-apply-voucher");
    const voucherInput = document.querySelector("#coupon-code");
    if (voucherInput) voucherInput.disabled = checkoutLocked;
    if (applyButton) {
        applyButton.disabled = checkoutLocked || state.quotePending || !state.product;
    }
    const lockCheckoutOptions = state.submitting || state.quotePending
        || state.submissionSucceeded || Boolean(state.submissionFingerprint);
    document.querySelectorAll('input[name="shipping_method"], .checkout-quantity-input')
        .forEach((input) => {
            input.disabled = lockCheckoutOptions;
        });
    document.querySelectorAll('input[name="payment_method"]').forEach((input) => {
        if (input.dataset.checkoutInitiallyDisabled === undefined) {
            input.dataset.checkoutInitiallyDisabled = String(input.disabled);
        }
        input.disabled = lockCheckoutOptions || input.dataset.checkoutInitiallyDisabled === "true";
    });
    document.querySelectorAll(".btn-remove-pill").forEach((button) => {
        button.disabled = state.submitting || state.submissionSucceeded || Boolean(state.submissionFingerprint);
    });
}

function checkoutBlockedReason() {
    if (state.submitting) return "Đang gửi yêu cầu tạo đơn hàng...";
    if (state.submissionSucceeded) return "Đơn hàng đã được tạo.";
    if (state.quotePending) return "Đang xác nhận báo giá, vui lòng đợi.";
    if (!state.product || !state.idempotencyKey) return "Đang tải thông tin sản phẩm...";
    if (state.addressStatus === "loading") return "Đang tải địa chỉ nhận hàng...";
    if (state.addressStatus === "error") return "Không thể tải địa chỉ nhận hàng. Hãy thử tải lại trước khi đặt hàng.";
    if (!isAddressUsable(state.address)) {
        return "Vui lòng thêm địa chỉ nhận hàng gồm tên người nhận, số điện thoại và địa chỉ đầy đủ.";
    }
    const currentQuoteFingerprint = JSON.stringify(createQuotePayload(
        state.voucherCodes,
        document.querySelector('input[name="shipping_method"]:checked')?.value || "standard",
        state.quantity
    ));
    if ((!state.quote || state.quotePayloadFingerprint !== currentQuoteFingerprint)
        && !state.checkoutRequestSnapshot) {
        return state.quoteError
            ? "Không thể tải báo giá. Hãy thử tải lại trước khi đặt hàng."
            : "Chưa có báo giá hợp lệ. Vui lòng chờ tải báo giá hoặc thử tải lại.";
    }
    if (state.submissionFingerprint && !state.checkoutRequestSnapshot) {
        return "Không thể khôi phục yêu cầu checkout trước đó. Hãy kiểm tra đơn hàng trước khi thử tạo đơn mới.";
    }
    return "";
}

function isAddressUsable(address) {
    return Boolean(
        address?.recipientName?.trim()
        && address?.phone?.trim()
        && addressText(address)
    );
}

function addressText(address) {
    return [
        address?.addressDetail,
        address?.ward,
        address?.district,
        address?.province
    ].filter((part) => typeof part === "string" && part.trim()).join(", ");
}

function savePendingPayment(payment) {
    try {
        sessionStorage.setItem(
            PENDING_PAYMENT_STORAGE_KEY,
            JSON.stringify(payment)
        );
        state.pendingPayment = payment;
        return true;
    } catch (error) {
        console.error("Không thể lưu thông tin thanh toán:", error);
        return false;
    }
}

function renderMockPaymentPanel(payment) {
    const panel = document.querySelector("#mock-payment-panel");

    if (!panel || !payment) {
        console.error("Không tìm thấy Mock Payment panel hoặc dữ liệu thanh toán.");
        return;
    }

    const orderCode = document.querySelector("#mock-order-code");
    const paymentCode = document.querySelector("#mock-payment-code");
    const amount = document.querySelector("#mock-payment-amount");
    const status = document.querySelector("#mock-payment-status");
    const message = document.querySelector("#mock-payment-message");

    if (orderCode) {
        orderCode.textContent = payment.orderCode || "—";
    }

    if (paymentCode) {
        paymentCode.textContent = payment.paymentCode || "—";
    }

    if (amount) {
        amount.textContent = formatVnd(payment.amount);
    }

    if (status) {
        status.textContent = payment.status === "SUCCEEDED"
            ? "Thanh toán thành công"
            : payment.status === "FAILED"
                ? "Thanh toán thất bại"
                : "Đang chờ thanh toán";

        status.dataset.status = payment.status || "PENDING";
    }

    if (message) {
        message.textContent = payment.status === "FAILED"
            ? "Giao dịch thất bại. Bạn có thể thử thanh toán lại."
            : payment.status === "SUCCEEDED"
                ? "Giao dịch đã được xác nhận thành công."
                : "Vui lòng chọn kết quả thanh toán để tiếp tục.";

        message.className = `checkout-message ${
            payment.status === "FAILED"
                ? "error"
                : payment.status === "SUCCEEDED"
                    ? "success"
                    : ""
        }`;

        message.hidden = false;
    }

    const successButton = document.querySelector("#btn-mock-success");
    const failedButton = document.querySelector("#btn-mock-failed");
    const retryButton = document.querySelector("#btn-mock-retry");

    if (successButton) {
        successButton.hidden = payment.status !== "PENDING";
    }

    if (failedButton) {
        failedButton.hidden = payment.status !== "PENDING";
    }

    if (retryButton) {
        retryButton.hidden = payment.status !== "FAILED";
    }

    panel.hidden = false;
    panel.scrollIntoView({
        behavior: "smooth",
        block: "center"
    });
}

function showMessage(message, type) {
    const region = document.querySelector("#checkout-message");
    if (!region) {
        if (type === "error") alert(message);
        return;
    }
    region.textContent = message;
    region.className = `checkout-message ${type}`;
    region.hidden = false;
}

function formatVnd(amount) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(Number(amount));
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}
