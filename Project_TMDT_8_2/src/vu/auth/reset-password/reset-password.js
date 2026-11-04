import { resetPassword, validateResetToken } from "../shared/auth-api.js";
import {
    setFieldError,
    showToast,
    togglePasswordVisibility,
    validatePassword
} from "../shared/auth-shared.js";

const form = document.querySelector("#reset-form");
const password = document.querySelector("#password");
const confirmPassword = document.querySelector("#confirm-password");
const submitButton = document.querySelector("[data-submit]");
const status = document.querySelector("[data-reset-status]");
const token = new URLSearchParams(window.location.search).get("token");

let tokenIsValid = false;

// Ẩn form khi chưa xác minh token
form.hidden = true;

function showInvalidToken(message = "Đường dẫn đổi mật khẩu không hợp lệ, đã hết hạn hoặc đã được sử dụng.") {
    tokenIsValid = false;
    form.hidden = true;
    status.textContent = message;
}

// Kiểm tra token khi mở trang
async function checkResetToken() {
    if (!token) {
        showInvalidToken();
        return;
    }

    status.textContent = "Đang kiểm tra đường dẫn đổi mật khẩu...";

    try {
        await validateResetToken(token);

        tokenIsValid = true;
        form.hidden = false;
        status.textContent = "Đường dẫn hợp lệ. Bạn có thể đặt mật khẩu mới.";
    } catch (error) {
        showInvalidToken(error.message);
    }
}

// Hiện/ẩn mật khẩu
document.querySelectorAll("[data-toggle-password]").forEach((button) => {
    const input = button.dataset.togglePassword === "confirmPassword"
        ? confirmPassword
        : password;

    button.addEventListener("click", () => {
        togglePasswordVisibility(input, button);
    });
});

// Xử lý đổi mật khẩu
form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (!tokenIsValid || !token) {
        showInvalidToken();
        return;
    }

    const passwordValue = password.value;
    const confirmValue = confirmPassword.value;

    const errors = {
        password: validatePassword(passwordValue)
            ? ""
            : "Mật khẩu cần từ 8 đến 72 ký tự.",
        confirmPassword: passwordValue === confirmValue
            ? ""
            : "Mật khẩu nhập lại không khớp."
    };

    setFieldError(
        password,
        document.querySelector("[data-error-for='password']"),
        errors.password
    );

    setFieldError(
        confirmPassword,
        document.querySelector("[data-error-for='confirmPassword']"),
        errors.confirmPassword
    );

    if (Object.values(errors).some(Boolean)) return;

    submitButton.disabled = true;
    submitButton.textContent = "Đang cập nhật...";

    try {
        const response = await resetPassword(token, passwordValue);

        // Token đã được sử dụng thành công
        tokenIsValid = false;

        form.replaceWith(
            Object.assign(document.createElement("p"), {
                className: "auth-subtitle reset-message",
                textContent: response.message
            })
        );

        // Xóa token khỏi URL
        window.history.replaceState(
            {},
            document.title,
            window.location.pathname
        );

        showToast(response.message, "success");

    } catch (error) {
        // Nếu token hết hạn hoặc đã được sử dụng
        showInvalidToken(error.message);
        showToast(error.message);

    } finally {
        if (document.querySelector("#reset-form")) {
            submitButton.disabled = false;
            submitButton.textContent = "Đổi mật khẩu";
        }
    }
});

// Bắt đầu kiểm tra token
checkResetToken();