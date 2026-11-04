import { loginUser, loginWithGoogle } from "../shared/auth-api.js";
import { setFieldError, showToast, togglePasswordVisibility, validateEmail, validatePassword } from "../shared/auth-shared.js";

const form = document.querySelector("#login-form");
const email = document.querySelector("#email");
const password = document.querySelector("#password");
const submitButton = document.querySelector("[data-submit]");
const googleButton = document.querySelector("#google-signin-button");
const googleError = document.querySelector("#google-error");
const rememberCheckbox = document.querySelector("#remember");
const GOOGLE_CLIENT_ID = "1021470481637-vv36ulkhn7f0mv47vumauputq70rqnt7.apps.googleusercontent.com";

let googleSdkPromise;

function setGoogleError(message) {
    googleError.textContent = message;
    googleError.hidden = !message;
}

function loadGoogleIdentityServices() {
    if (window.google?.accounts?.id) return Promise.resolve();
    if (googleSdkPromise) return googleSdkPromise;

    googleSdkPromise = new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.src = "https://accounts.google.com/gsi/client";
        script.async = true;
        script.defer = true;
        script.onload = () => window.google?.accounts?.id
            ? resolve()
            : reject(new Error("Google Sign-In chưa sẵn sàng."));
        script.onerror = () => reject(new Error("Không tải được Google Sign-In."));
        document.head.append(script);
    });
    return googleSdkPromise;
}

async function handleGoogleCredential(response) {
    if (!response?.credential) {
        setGoogleError("Google không trả về thông tin đăng nhập. Vui lòng thử lại.");
        return;
    }

    googleButton.setAttribute("aria-busy", "true");
    setGoogleError("Đang xác minh tài khoản Google...");
    try {
        await loginWithGoogle(response.credential, rememberCheckbox.checked);
        showToast("Đăng nhập Google thành công.", "success");
        setTimeout(() => {
            window.location.href = "../../profile/profile.html";
        }, 800);
    } catch (error) {
        setGoogleError(error.message || "Đăng nhập Google không thành công.");
    } finally {
        googleButton.removeAttribute("aria-busy");
    }
}

async function initializeGoogleSignIn() {
    try {
        await loadGoogleIdentityServices();
        window.google.accounts.id.initialize({
            client_id: GOOGLE_CLIENT_ID,
            callback: handleGoogleCredential,
            auto_select: false
        });
        window.google.accounts.id.renderButton(googleButton, {
            theme: "outline",
            size: "large",
            text: "continue_with",
            shape: "rectangular",
            width: Math.min(googleButton.clientWidth || 320, 400)
        });
    } catch {
        setGoogleError("Không tải được Google Sign-In. Bạn vẫn có thể đăng nhập bằng email và mật khẩu.");
    }
}

document.querySelector("[data-toggle-password='password']").addEventListener("click", (event) => togglePasswordVisibility(password, event.currentTarget));

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const emailValue = email.value.trim();
    const passwordValue = password.value;
    const emailError = !validateEmail(emailValue) ? "Vui lòng nhập email hợp lệ." : "";
    const passwordError = !validatePassword(passwordValue) ? "Mật khẩu cần từ 8 đến 72 ký tự." : "";
    setFieldError(email, document.querySelector("[data-error-for='email']"), emailError);
    setFieldError(password, document.querySelector("[data-error-for='password']"), passwordError);
    if (emailError || passwordError) return;
    submitButton.disabled = true;
    submitButton.textContent = "Đang đăng nhập...";
    try {
        await loginUser(emailValue, passwordValue, document.querySelector("#remember").checked);
        showToast("Đăng nhập thành công.", "success");
        setTimeout(() => {
            window.location.href = "../../profile/profile.html";
        }, 800);
    } catch (error) {
        showToast(error.message);
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Đăng nhập";
    }
});

initializeGoogleSignIn();