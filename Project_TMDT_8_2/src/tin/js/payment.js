import { apiRequest } from "../../shared/api-client.js";

const VOUCHER_STORAGE_KEY = "comichub_checkout_voucher_codes";

const state = {
    productId: null,
    quantity: null,
    buyNowItem: null,
    idempotencyKey: null,
    submissionFingerprint: null,
    checkoutRequestSnapshot: null,
    submissionSucceeded: false,
    product: null,
    address: null,
    voucherCodes: {
        platformCode: null,
        sellerCode: null,
        shippingCode: null
    },
    quote: null,
    quotePayloadFingerprint: null,
    quoteRequestId: 0,
    quotePending: false,
    submitting: false
};

document.addEventListener("DOMContentLoaded", initializeCheckout);

async function initializeCheckout() {
    const checkoutButton = document.querySelector(".btn-submit-checkout");
    if (checkoutButton) checkoutButton.disabled = true;
    bindCheckoutControls();
    updateCheckoutButton();

    const packageCard = document.querySelector(".package-summary-card");
    if (packageCard) packageCard.innerHTML = `<p role="status">Đang tải sản phẩm...</p>`;
    const summary = document.querySelector(".summary-breakdown-list");
    if (summary) summary.innerHTML = `<p role="status">Đang chờ báo giá từ máy chủ...</p>`;
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
        const response = await apiRequest(`/api/products/${state.productId}`);
        state.product = response.data;
        if (!state.product) throw new Error("Không tìm thấy dữ liệu sản phẩm.");
        renderProduct();
        updateCheckoutButton();
    } catch (error) {
        showMessage(`Không thể tải thông tin sản phẩm: ${error.message || "Vui lòng thử lại."}`, "error");
        return;
    }

    try {
        const response = await apiRequest("/api/addresses");
        const addresses = Array.isArray(response.data) ? response.data : [];
        state.address = state.checkoutRequestSnapshot
            ? addressFromCheckoutRequest(state.checkoutRequestSnapshot)
            : addresses.find((address) => address.isDefault) || addresses[0] || null;
        renderAddress();
    } catch (error) {
        showMessage(
            error.status === 401
                ? "Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập. Vui lòng đăng nhập lại."
                : `Không thể tải địa chỉ nhận hàng: ${error.message || "Vui lòng thử lại."}`,
            "error"
        );
    }

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
                await refreshQuote(state.voucherCodes, false);
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
    voucherInput?.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            applyVoucher();
        }
    });

    document.querySelectorAll('input[name="shipping_method"]').forEach((input) => {
        input.addEventListener("change", () => {
            refreshQuote(state.voucherCodes, true).catch((error) => {
                showMessage(`Không thể cập nhật phí vận chuyển: ${error.message || "Vui lòng thử lại."}`, "error");
            });
        });
    });

    checkoutButton?.addEventListener("click", submitCheckout);
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
                showMessage(`Không thể cập nhật báo giá: ${error.message || "Vui lòng thử lại."}`, "error");
            });
        });
    }
}

function renderAddress() {
    const name = document.querySelector(".recipient-name");
    const phone = document.querySelector(".recipient-phone");
    const address = document.querySelector(".recipient-address");
    const defaultBadge = document.querySelector(".badge-address-default");

    if (!isAddressUsable(state.address)) {
        if (name) name.textContent = "Chưa có địa chỉ nhận hàng";
        if (phone) phone.textContent = "";
        if (address) address.textContent = "Vui lòng thêm tên người nhận, số điện thoại và địa chỉ đầy đủ.";
        if (defaultBadge) defaultBadge.hidden = true;
        showMessage("Địa chỉ nhận hàng chưa đầy đủ. Hãy cập nhật địa chỉ rồi quay lại thanh toán.", "error");
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

async function applyVoucher() {
    const input = document.querySelector("#coupon-code");
    const typeSelect = document.querySelector("#voucher-type");
    const applyButton = document.querySelector(".btn-apply-voucher");
    const code = input?.value.trim();
    const field = typeSelect?.value;

    if (!state.product || !state.productId || !state.quantity) {
        showMessage("Chưa thể kiểm tra voucher vì dữ liệu Buy Now chưa tải xong.", "error");
        return;
    }

    if (!code) {
        showMessage("Vui lòng nhập mã voucher.", "error");
        return;
    }
    if (!["platformCode", "sellerCode", "shippingCode"].includes(field)) {
        showMessage("Vui lòng chọn đúng loại voucher.", "error");
        return;
    }

    const candidateCodes = { ...state.voucherCodes, [field]: code };
    if (applyButton) {
        applyButton.disabled = true;
        applyButton.textContent = "Đang kiểm tra...";
    }

    try {
        const quoteUpdated = await refreshQuote(candidateCodes, false);
        if (!quoteUpdated) return;
        state.voucherCodes = candidateCodes;
        renderAppliedVouchers();
        input.value = "";
        const saved = saveVoucherCodes(candidateCodes);
        showMessage(
            saved
                ? "Voucher đã được Backend xác nhận và áp dụng."
                : "Voucher đã được Backend xác nhận cho đơn hiện tại, nhưng không thể lưu lựa chọn trong trình duyệt.",
            saved ? "success" : "error"
        );
    } catch (error) {
        showMessage(error.message || "Backend không chấp nhận voucher này.", "error");
    } finally {
        if (applyButton) {
            applyButton.textContent = "Áp dụng";
        }
        updateCheckoutButton();
    }
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
    if (clearPreviousQuote) {
        state.quote = null;
        state.quotePayloadFingerprint = null;
        renderQuote();
    }
    updateCheckoutButton();

    try {
        const response = await apiRequest("/api/vouchers/validate", {
            method: "POST",
            body: payload
        });
        if (requestId !== state.quoteRequestId) return false;
        if (!isValidQuote(response.data)) {
            throw new Error("Dữ liệu báo giá từ máy chủ không hợp lệ.");
        }
        state.quote = response.data;
        state.quotePayloadFingerprint = JSON.stringify(payload);
        renderQuote();
        return true;
    } finally {
        if (requestId === state.quoteRequestId) {
            state.quotePending = false;
            updateCheckoutButton();
        }
    }
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
        summary.innerHTML = `<p role="status">Đang chờ báo giá từ máy chủ...</p>`;
        const subtotal = document.querySelector(".package-subtotal");
        if (subtotal) subtotal.textContent = "Đang tính...";
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
        const orderCode = response.data?.orderCode;
        state.submissionSucceeded = true;
        showMessage(
            `Đã tạo đơn hàng${orderCode ? ` ${orderCode}` : ""}. Đơn đang chờ thanh toán; giao dịch thanh toán chưa được xác nhận.`,
            "success"
        );
        sessionStorage.removeItem("buyNowItem");
        state.buyNowItem = null;
        try {
            sessionStorage.removeItem(VOUCHER_STORAGE_KEY);
        } catch {
            showMessage("Đơn đã được tạo thành công. Không thể xóa mã voucher tạm đã lưu trên trình duyệt.", "error");
        }
        window.setTimeout(() => {
            window.location.href = "../../thuc/html/page3.html";
        }, 1800);
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
    const blockedReason = checkoutBlockedReason();
    if (button) {
        button.disabled = Boolean(blockedReason);
        button.setAttribute("aria-describedby", "checkout-submit-status");
    }

    const status = document.querySelector("#checkout-submit-status");
    if (status) {
        status.textContent = blockedReason || "";
        status.hidden = !blockedReason;
        status.className = `checkout-message${blockedReason ? " error" : ""}`;
    }

    const applyButton = document.querySelector(".btn-apply-voucher");
    if (applyButton) {
        applyButton.disabled = state.submitting || state.submissionSucceeded || Boolean(state.submissionFingerprint)
            || state.quotePending || !state.product;
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
    if (!isAddressUsable(state.address)) {
        return "Vui lòng thêm địa chỉ nhận hàng gồm tên người nhận, số điện thoại và địa chỉ đầy đủ.";
    }
    if (!state.quote && !state.checkoutRequestSnapshot) {
        return "Chưa có báo giá hợp lệ. Vui lòng chờ tải báo giá hoặc thử tải lại trang.";
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
