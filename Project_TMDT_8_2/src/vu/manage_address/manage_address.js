import { apiRequest } from "../../shared/api-client.js";

function setFieldError(inputEl, errorEl, message) {
    if (inputEl) {
        inputEl.setAttribute("aria-invalid", String(Boolean(message)));
    }

    if (errorEl) {
        errorEl.textContent = message;
    }
}

function clearFieldErrors() {
    ["recipientName", "phone", "province", "district", "ward", "addressDetail"].forEach((name) => {
        const input = form.elements[name];
        const error = document.querySelector(`[data-error-for="${name}"]`);
        setFieldError(input, error, "");
    });
}

function showToast(message, type = "error") {
    const region = document.querySelector("[data-toast-region]");
    if (!region) return;

    region.replaceChildren();

    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    toast.setAttribute("role", "status");
    toast.textContent = message;

    region.append(toast);
    window.setTimeout(() => toast.remove(), 4000);
}

const form = document.querySelector("#address-form");
const formCard = document.querySelector("[data-address-form-card]");
const list = document.querySelector("[data-address-list]");
const formTitle = document.querySelector("[data-form-title]");

const fields = Object.fromEntries(
    [
        "id",
        "recipientName",
        "phone",
        "province",
        "district",
        "ward",
        "addressDetail",
        "isDefault"
    ].map((name) => [name, form.elements[name]])
);

let addresses = [];
let isSubmitting = false;

const addressText = (item) =>
    `${item.addressDetail}, ${item.ward}, ${item.district}, ${item.province}`;

// =========================
// 1. LẤY DANH SÁCH ĐỊA CHỈ
// =========================

async function loadAddresses() {
    list.replaceChildren();

    const loading = document.createElement("p");
    loading.className = "empty-state";
    loading.textContent = "Đang tải danh sách địa chỉ...";
    list.append(loading);

    try {
        const response = await apiRequest("/api/addresses");
        addresses = Array.isArray(response.data) ? response.data : [];
        render();
    } catch (error) {
        list.replaceChildren();

        const message = document.createElement("p");
        message.className = "empty-state";
        message.textContent = error.status === 401
            ? "Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập. Vui lòng đăng nhập lại."
            : error.message || "Không thể tải danh sách địa chỉ.";

        list.append(message);
        showToast(message.textContent);
    }
}

// =========================
// 2. HIỂN THỊ DANH SÁCH
// =========================

function render() {
    list.replaceChildren();

    if (!addresses.length) {
        const empty = document.createElement("p");
        empty.className = "empty-state";
        empty.textContent = "Bạn chưa lưu địa chỉ nào.";
        list.append(empty);
        return;
    }

    addresses.forEach((item) => {
        const card = document.createElement("article");
        card.className = `address-card ${item.isDefault ? "default" : ""}`;

        const header = document.createElement("header");

        const recipientInfo = document.createElement("div");

        const name = document.createElement("h2");
        name.textContent = item.recipientName;

        const phone = document.createElement("p");
        phone.className = "phone";
        phone.textContent = item.phone;

        recipientInfo.append(name, phone);
        header.append(recipientInfo);

        if (item.isDefault) {
            const defaultLabel = document.createElement("span");
            defaultLabel.className = "default-label";
            defaultLabel.textContent = "Mặc định";
            header.append(defaultLabel);
        }

        const address = document.createElement("p");
        address.className = "address-text";
        address.textContent = addressText(item);

        const actions = document.createElement("div");
        actions.className = "address-actions";

        const editButton = document.createElement("button");
        editButton.className = "button secondary";
        editButton.type = "button";
        editButton.dataset.edit = item.id;
        editButton.textContent = "Chỉnh sửa";
        actions.append(editButton);

        if (!item.isDefault) {
            const defaultButton = document.createElement("button");
            defaultButton.className = "button secondary";
            defaultButton.type = "button";
            defaultButton.dataset.default = item.id;
            defaultButton.textContent = "Đặt mặc định";
            actions.append(defaultButton);
        }

        const deleteButton = document.createElement("button");
        deleteButton.className = "button secondary danger";
        deleteButton.type = "button";
        deleteButton.dataset.delete = item.id;
        deleteButton.textContent = "Xóa";
        actions.append(deleteButton);

        card.append(header, address, actions);
        list.append(card);
    });
}

// =========================
// 3. MỞ / ĐÓNG FORM
// =========================

function openForm(item = null) {
    form.reset();
    clearFieldErrors();

    fields.id.value = item?.id || "";

    [
        "recipientName",
        "phone",
        "province",
        "district",
        "ward",
        "addressDetail"
    ].forEach((name) => {
        fields[name].value = item?.[name] || "";
    });

    fields.isDefault.checked = Boolean(item?.isDefault);

    formTitle.textContent = item
        ? "Chỉnh sửa địa chỉ"
        : "Thêm địa chỉ mới";

    formCard.hidden = false;

    form.querySelector("input:not([type='hidden'])").focus();
}

function closeForm() {
    formCard.hidden = true;
    form.reset();
    clearFieldErrors();
}

document.querySelectorAll("[data-open-address-form]").forEach((button) => {
    button.addEventListener("click", () => openForm());
});

document.querySelectorAll("[data-close-address-form]").forEach((button) => {
    button.addEventListener("click", closeForm);
});

// =========================
// 4. THÊM / CẬP NHẬT ĐỊA CHỈ
// =========================

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (isSubmitting) return;

    clearFieldErrors();

    const values = Object.fromEntries(new FormData(form).entries());

    const requiredFields = [
        "recipientName",
        "phone",
        "province",
        "district",
        "ward",
        "addressDetail"
    ];

    let hasError = false;

    requiredFields.forEach((name) => {
        const value = values[name]?.trim() || "";

        if (!value) {
            const input = form.elements[name];
            const error = document.querySelector(`[data-error-for="${name}"]`);

            setFieldError(input, error, "Vui lòng nhập thông tin này.");
            hasError = true;
        }
    });

    if (hasError) {
        const firstInvalid = form.querySelector('[aria-invalid="true"]');
        firstInvalid?.focus();
        return;
    }

    const address = {
        recipientName: values.recipientName.trim(),
        phone: values.phone.trim(),
        province: values.province.trim(),
        district: values.district.trim(),
        ward: values.ward.trim(),
        addressDetail: values.addressDetail.trim(),
        isDefault: fields.isDefault.checked
    };

    const addressId = fields.id.value;

    isSubmitting = true;

    try {
        if (addressId) {
            await apiRequest(`/api/addresses/${addressId}`, {
                method: "PUT",
                body: address
            });

            showToast("Cập nhật địa chỉ thành công.", "success");
        } else {
            await apiRequest("/api/addresses", {
                method: "POST",
                body: address
            });

            showToast("Thêm địa chỉ thành công.", "success");
        }

        closeForm();
        await loadAddresses();
    } catch (error) {
        showToast(error.message || "Không thể lưu địa chỉ.");
    } finally {
        isSubmitting = false;
    }
});

// =========================
// 5. XỬ LÝ CÁC NÚT TRÊN CARD
// =========================

list.addEventListener("click", async (event) => {
    const button = event.target.closest("button");
    if (!button) return;

    const editId = button.dataset.edit;
    const defaultId = button.dataset.default;
    const deleteId = button.dataset.delete;

    // Chỉnh sửa
    if (editId) {
        const item = addresses.find(
            (address) => String(address.id) === editId
        );

        if (item) openForm(item);
        return;
    }

    // Đặt địa chỉ mặc định
    if (defaultId) {
        try {
            await apiRequest(`/api/addresses/${defaultId}/default`, {
                method: "PUT"
            });

            showToast("Đã chọn địa chỉ mặc định.", "success");
            await loadAddresses();
        } catch (error) {
            showToast(error.message || "Không thể đặt địa chỉ mặc định.");
        }

        return;
    }

    // Xóa địa chỉ
    if (deleteId) {
        const confirmed = window.confirm(
            "Bạn có chắc chắn muốn xóa địa chỉ này không?"
        );

        if (!confirmed) return;

        try {
            await apiRequest(`/api/addresses/${deleteId}`, {
                method: "DELETE"
            });

            showToast("Đã xóa địa chỉ.", "success");
            await loadAddresses();
        } catch (error) {
            showToast(error.message || "Không thể xóa địa chỉ.");
        }
    }
});


loadAddresses();