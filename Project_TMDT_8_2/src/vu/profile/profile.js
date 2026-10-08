import {
    getCurrentUser,
    requestPasswordResetForCurrentUser,
    updateCurrentUserProfile
} from "../auth/shared/auth-api.js";

const roles = {
    BUYER: ["Khách hàng", "U", "Mở kênh Người bán", "#seller-channel"],
    SELLER: ["Người bán", "S", "Mở kênh Người bán", "#seller-channel"],
    ADMIN: ["Quản trị viên", "A", "Trang Quản trị Admin", "#admin-dashboard"]
};
const fields = {
    displayName: document.querySelector("#display-name"),
    email: document.querySelector("#email")
};
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

function renderRole(user) {
    const role = user.role === "ADMIN" ? "ADMIN" : user.seller ? "SELLER" : "BUYER";
    const roleInfo = roles[role];
    localStorage.setItem("userRole", role);
    document.querySelector("[data-role-badge]").textContent = roleInfo[0];
    document.querySelector("[data-role-label]").textContent = roleInfo[0];
    document.querySelector("[data-role-symbol]").textContent = roleInfo[1];
    const action = document.querySelector("[data-role-action]");
    action.textContent = roleInfo[2];
    action.href = roleInfo[3];
}

function renderUser(user) {
    fields.displayName.value = user.fullName || "";
    fields.email.value = user.email || "";
    document.querySelector("[data-display-name]").textContent = user.fullName || "Thành viên";
    document.querySelector("[data-display-email]").textContent = user.email || "";
    localStorage.setItem("userName", user.fullName || "");
    renderRole(user);
}

document.querySelector("#avatar-input").addEventListener("change", (event) => {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.addEventListener("load", () => {
        document.querySelector("[data-avatar]").innerHTML = `<img src="${reader.result}" alt="Ảnh đại diện">`;
        localStorage.setItem("bookmooch_avatar", reader.result);
    });
    reader.readAsDataURL(file);
});

const savedAvatar = localStorage.getItem("bookmooch_avatar");
if (savedAvatar) document.querySelector("[data-avatar]").innerHTML = `<img src="${savedAvatar}" alt="Ảnh đại diện">`;

document.querySelector("#profile-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const name = fields.displayName.value.trim();
    const error = name ? "" : "Vui lòng nhập tên hiển thị.";
    const input = fields.displayName;
    const errorElement = document.querySelector("[data-error-for='displayName']");
    input.setAttribute("aria-invalid", String(Boolean(error)));
    errorElement.textContent = error;
    if (error) return;

    try {
        const user = await updateCurrentUserProfile({ fullName: name });
        renderUser(user);
        showToast("Thông tin hồ sơ đã được lưu.", "success");
    } catch (requestError) {
        showToast(requestError.message);
    }
});

document.querySelector("#request-password-reset").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    button.textContent = "Đang gửi...";
    try {
        const response = await requestPasswordResetForCurrentUser();
        showToast(response.message, "success");
    } catch (error) {
        showToast(error.message);
    } finally {
        button.disabled = false;
        button.textContent = "Đổi mật khẩu";
    }
});

getCurrentUser().then(renderUser).catch((error) => {
    showToast(error.message);
    if (error.status === 401) {
        window.setTimeout(() => {
            window.location.href = "../auth/login/login.html";
        }, 800);
    }
});
