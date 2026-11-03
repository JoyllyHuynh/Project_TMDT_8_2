
const TRACKING_CODE = "GHN-VN-98421034";
const toastEl = document.getElementById("toast");

function onElement(id, event, callback) {
  const element = document.getElementById(id);
  if (!element) {
    console.warn(`Không tìm thấy phần tử #${id} để đăng ký sự kiện ${event}.`);
    return;
  }
  element.addEventListener(event, callback);
}

function showToast(message) {
  if (!toastEl) return;
  toastEl.textContent = message;
  toastEl.classList.add("show");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toastEl.classList.remove("show"), 3000);
}

function openModal() {
  const modal = document.getElementById("rescheduleModal");
  if (!modal) return;
  modal.classList.add("show");
  modal.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
}
function closeModal() {
  const modal = document.getElementById("rescheduleModal");
  if (!modal) return;
  modal.classList.remove("show");
  modal.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");
}

document.querySelectorAll("[data-toast]").forEach(btn => {
  btn.addEventListener("click", () => showToast(btn.dataset.toast));
});

const pathMap = {
  "kham-pha": "../../tin/html/home.html",
  "dang-tin-tim-mua": "page1.html",
  "quan-ly-don-hang": "page2.html",
  "theo-doi-trang-thai": "page3.html",
  "huy-va-doi-tra": "page5.html",
  "danh-gia-truyen": "page4.html",
  "truyen-yeu-thich": "page6.html"
};

document.querySelectorAll("[data-path]").forEach(link => {
  link.addEventListener("click", e => {
    e.preventDefault();
    const path = link.dataset.path;
    if (pathMap[path] && path !== "theo-doi-trang-thai") {
      window.location.href = pathMap[path];
      return;
    }
    document.querySelectorAll("[data-path]").forEach(x => x.classList.remove("active"));
    document.querySelectorAll(`[data-path="${link.dataset.path}"]`).forEach(x => x.classList.add("active"));
    window.scrollTo({top: 0, behavior: "smooth"});
  });
});

onElement("copyTracking", "click", async () => {
  try {
    await navigator.clipboard.writeText(TRACKING_CODE);
    showToast(`Đã sao chép mã vận đơn: ${TRACKING_CODE}`);
  } catch {
    const ta = document.createElement("textarea");
    ta.value = TRACKING_CODE;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    ta.remove();
    showToast(`Đã sao chép mã vận đơn: ${TRACKING_CODE}`);
  }
});

onElement("printOrder", "click", () => window.print());

document.addEventListener("keydown", e => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    const globalSearch = document.getElementById("globalSearch");
    if (globalSearch) globalSearch.focus();
  }
  if (e.key === "Escape") closeModal();
});

onElement("editNote", "click", () => {
  const current = 'Hàng truyện tranh sưu tầm dễ gãy móp, xin vui lòng nhẹ tay. Gọi trước 10 phút.';
  const next = prompt("Nhập ghi chú mới cho shipper:", current);
  if (next !== null && next.trim()) {
    document.querySelector(".note-sub").textContent = `"${next.trim()}"`;
    showToast("Đã cập nhật ghi chú giao hàng.");
  }
});

document.querySelectorAll(".pack-photo").forEach(photo => {
  photo.addEventListener("click", () => {
    showToast(`Đang xem ảnh phóng to: ${photo.dataset.photo}`);
  });
});

onElement("fullscreenMap", "click", () => {
  const map = document.querySelector(".map-preview");
  if (map.requestFullscreen) {
    map.requestFullscreen().catch(() => showToast("Trình duyệt không cho phép mở toàn màn hình."));
  } else {
    showToast("Đang mở bản đồ dẫn đường toàn màn hình...");
  }
});

onElement("reschedule", "click", openModal);
onElement("closeModal", "click", closeModal);
onElement("cancelModal", "click", closeModal);

onElement("submitReschedule", "click", () => {
  const selected = document.querySelector('input[name="slot"]:checked');
  const labels = {
    tonight: "18:00 - 20:00 tối nay",
    tomorrow_am: "08:00 - 12:00 sáng mai",
    tomorrow_pm: "14:00 - 18:00 chiều mai"
  };
  closeModal();
  showToast(`Đã cập nhật yêu cầu hẹn giao: ${labels[selected?.value] || "khung giờ đã chọn"}.`);
});

onElement("reportDelay", "click", () => {
  showToast("Đã gửi cảnh báo hối thúc bưu tá Cầu Giấy ưu tiên đơn hàng.");
});
onElement("support", "click", () => {
  showToast("Đang kết nối chuyên viên thẩm định manga MangaTrade qua live chat...");
});

onElement("confirmReceived", "click", () => {
  const ok = confirm("Bạn có chắc chắn đã nhận đủ trọn bộ 1-18 kèm Obi & Poster và đồng ý giải ngân tiền bán cho Shop OtakuStoreVN?");
  if (!ok) return;
  showToast("Cảm ơn bạn! Đơn hàng đã hoàn tất. Bạn nhận được 180 điểm thưởng Manga Collector.");
  setTimeout(() => {
    const review = document.querySelector('[data-path="danh-gia-truyen"]');
    if (review) review.click();
  }, 800);
});

onElement("newsletter", "submit", e => {
  e.preventDefault();
  const input = document.getElementById("newsletterEmail");
  if (!input.checkValidity()) {
    showToast("Vui lòng nhập email hợp lệ.");
    input.focus();
    return;
  }
  showToast("Đăng ký nhận bản tin MangaTrade thành công!");
  input.value = "";
});

onElement("rescheduleModal", "click", e => {
  if (e.target.id === "rescheduleModal") closeModal();
});
