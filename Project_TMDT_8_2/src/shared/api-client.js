const API_BASE_URL = "http://localhost:8080";

function readSession(storage) {
    const value = storage.getItem("bookmooch_session");
    if (!value) return null;
    return JSON.parse(value);
}

function getAuthToken() {
    const localSession = readSession(localStorage);
    const temporarySession = readSession(sessionStorage);
    return [localSession, temporarySession].find((session) => typeof session?.token === "string")?.token || null;
}

export async function apiRequest(path, { method = "GET", body } = {}) {
    const headers = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";

    const token = getAuthToken();
    if (token) headers.Authorization = `Bearer ${token}`;

    const response = await fetch(`${API_BASE_URL}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body)
    });

    let result;
    try {
        result = await response.json();
    } catch {
        throw new Error("Phản hồi từ máy chủ không hợp lệ.");
    }

    if (!response.ok || result?.ok === false) {
        const error = new Error(result?.message || "Yêu cầu không thành công.");
        error.status = response.status;
        throw error;
    }

    return result;
}
