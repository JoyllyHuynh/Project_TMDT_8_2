import { apiRequest } from "../api-client.js";

const HISTORY_LIMIT = 8;
const SITE_ROOT = new URL("../../", import.meta.url);

function siteUrl(path) {
    const relativePath = String(path || "").replace(/^\/+/, "");
    if (relativePath.includes("..")) return SITE_ROOT.href;
    const target = new URL(relativePath, SITE_ROOT);
    return target.origin === SITE_ROOT.origin && target.pathname.startsWith(SITE_ROOT.pathname)
        ? target.href
        : SITE_ROOT.href;
}

function addStylesheet() {
    const href = new URL("./chatbot.css", import.meta.url).href;
    if (document.querySelector(`link[href="${href}"]`)) return;
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = href;
    document.head.append(link);
}

function element(tagName, className, text) {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function mountChatbot() {
    if (document.querySelector(".cw-chatbot")) return;
    addStylesheet();

    const root = element("div", "cw-chatbot");
    const toggle = element("button", "cw-chatbot__toggle", "Hỏi ComicWorm");
    toggle.type = "button";
    toggle.setAttribute("aria-expanded", "false");
    toggle.setAttribute("aria-controls", "cw-chatbot-panel");

    const panel = element("section", "cw-chatbot__panel");
    panel.id = "cw-chatbot-panel";
    panel.hidden = true;
    panel.setAttribute("aria-label", "Trợ lý ComicWorm");

    const header = element("header", "cw-chatbot__header");
    const title = element("div", "cw-chatbot__heading");
    title.append(element("strong", "", "Trợ lý ComicWorm"));
    title.append(element("span", "", "Không nhập mật khẩu, OTP hay thông tin thanh toán"));
    const close = element("button", "cw-chatbot__close", "Đóng");
    close.type = "button";
    close.setAttribute("aria-label", "Đóng trợ lý");
    header.append(title, close);

    const transcript = element("div", "cw-chatbot__messages");
    transcript.setAttribute("role", "log");
    transcript.setAttribute("aria-live", "polite");
    transcript.append(createMessage("assistant", "Xin chào! Mình có thể giúp tìm thông tin catalog hoặc hướng dẫn trên ComicWorm."));

    const form = element("form", "cw-chatbot__form");
    const input = element("textarea", "cw-chatbot__input");
    input.name = "message";
    input.maxLength = 2000;
    input.rows = 2;
    input.required = true;
    input.placeholder = "Nhập câu hỏi...";
    input.setAttribute("aria-label", "Tin nhắn");
    const send = element("button", "cw-chatbot__send", "Gửi");
    send.type = "submit";
    form.append(input, send);

    panel.append(header, transcript, form);
    root.append(toggle, panel);
    document.body.append(root);

    const history = [];
    const setOpen = (open) => {
        panel.hidden = !open;
        toggle.setAttribute("aria-expanded", String(open));
        if (open) input.focus();
    };

    toggle.addEventListener("click", () => setOpen(panel.hidden));
    close.addEventListener("click", () => setOpen(false));
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !panel.hidden) setOpen(false);
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const message = input.value.trim();
        if (!message || send.disabled) return;

        transcript.append(createMessage("user", message));
        input.value = "";
        send.disabled = true;
        input.disabled = true;
        send.textContent = "…";
        const loading = createMessage("assistant", "Đang tìm thông tin…");
        loading.setAttribute("aria-label", "Đang chờ phản hồi");
        transcript.append(loading);
        scrollToLatest(transcript);

        try {
            const result = await apiRequest("/api/chatbot/message", {
                method: "POST",
                body: { message, history: history.slice(-HISTORY_LIMIT) }
            });
            const response = result.data;
            loading.remove();
            transcript.append(createMessage("assistant", response.answer || "Hiện chưa có câu trả lời."));
            appendProducts(transcript, response.products);
            appendLinks(transcript, response.links);
            if (response.requiresLogin) {
                appendLinks(transcript, [{ label: "Đăng nhập", url: "/vu/auth/login/login.html" }]);
            }
            history.push(
                { role: "USER", content: message },
                { role: "ASSISTANT", content: response.answer || "" }
            );
            history.splice(0, Math.max(0, history.length - HISTORY_LIMIT));
        } catch (error) {
            loading.remove();
            transcript.append(createMessage(
                "assistant",
                error?.message || "Không thể kết nối với trợ lý lúc này. Vui lòng thử lại."
            ));
        } finally {
            send.disabled = false;
            input.disabled = false;
            send.textContent = "Gửi";
            input.focus();
            scrollToLatest(transcript);
        }
    });
}

function createMessage(role, text) {
    const message = element("div", `cw-chatbot__message cw-chatbot__message--${role}`);
    message.textContent = text;
    return message;
}

function appendProducts(transcript, products) {
    if (!Array.isArray(products) || products.length === 0) return;
    const list = element("div", "cw-chatbot__cards");
    products.forEach((product) => {
        const card = element("article", "cw-chatbot__product");
        const title = element("a", "cw-chatbot__product-title", product.title || "Sản phẩm ComicWorm");
        title.href = siteUrl(product.url);
        title.target = "_blank";
        title.rel = "noopener noreferrer";
        card.append(title);
        if (product.price !== null && product.price !== undefined) {
            card.append(element(
                "strong",
                "cw-chatbot__price",
                new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(product.price)
            ));
        }
        if (Number.isInteger(product.stockQuantity)) {
            card.append(element(
                "span",
                "cw-chatbot__stock",
                product.stockQuantity > 0 ? `Còn ${product.stockQuantity} sản phẩm` : "Tạm hết hàng"
            ));
        }
        list.append(card);
    });
    transcript.append(list);
}

function appendLinks(transcript, links) {
    if (!Array.isArray(links) || links.length === 0) return;
    const list = element("div", "cw-chatbot__links");
    links.forEach((item) => {
        const link = element("a", "", item.label || "Xem thêm");
        link.href = siteUrl(item.url);
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        list.append(link);
    });
    transcript.append(list);
}

function scrollToLatest(transcript) {
    transcript.scrollTop = transcript.scrollHeight;
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mountChatbot, { once: true });
} else {
    mountChatbot();
}
