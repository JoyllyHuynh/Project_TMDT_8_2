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

async function parseResponse(response) {
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

    return parseResponse(response);
}

export async function uploadFile(path, file) {
    const token = getAuthToken();
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${API_BASE_URL}${path}`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData
    });

    return parseResponse(response);
}
