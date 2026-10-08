import { apiRequest } from "../../../shared/api-client.js";

function saveSession(auth) {
    const session = JSON.stringify({ token: auth.token, user: auth.user });
    const storage = auth.remember ? localStorage : sessionStorage;
    const otherStorage = auth.remember ? sessionStorage : localStorage;
    storage.setItem("bookmooch_session", session);
    otherStorage.removeItem("bookmooch_session");

    const role = auth.user.role === "ADMIN" ? "ADMIN" : auth.user.seller ? "SELLER" : "BUYER";
    localStorage.setItem("userRole", role);
    localStorage.setItem("userName", auth.user.fullName);
}

export async function registerUser(user) {
    return apiRequest("/api/auth/register", {
        method: "POST",
        body: {
            fullName: user.fullName,
            email: user.email,
            password: user.password
        }
    });
}

export async function loginUser(email, password, remember) {
    const result = await apiRequest("/api/auth/login", {
        method: "POST",
        body: { email, password, remember }
    });

    if (!result.data?.token || !result.data.user) {
        throw new Error("Phản hồi đăng nhập thiếu token hoặc thông tin người dùng.");
    }

    saveSession({ ...result.data, remember });
    return result;
}

export function requestPasswordReset(email) {
    return apiRequest("/api/auth/forgot-password", {
        method: "POST",
        body: { email }
    });
}

export async function requestPasswordResetForCurrentUser() {
    const user = await getCurrentUser();
    return requestPasswordReset(user.email);
}

export function resetPassword(token, password) {
    return apiRequest("/api/auth/reset-password", {
        method: "POST",
        body: { token, password }
    });
}

export async function getCurrentUser() {
    const result = await apiRequest("/api/auth/me");
    if (!result.data) throw new Error("Phản hồi hồ sơ không hợp lệ.");
    return result.data;
}

export async function updateCurrentUserProfile(profile) {
    const result = await apiRequest("/api/auth/me", {
        method: "PUT",
        body: profile
    });
    if (!result.data) throw new Error("Phản hồi hồ sơ không hợp lệ.");

    localStorage.setItem("userName", result.data.fullName);
    const role = result.data.role === "ADMIN" ? "ADMIN" : result.data.seller ? "SELLER" : "BUYER";
    localStorage.setItem("userRole", role);
    return result.data;
}
