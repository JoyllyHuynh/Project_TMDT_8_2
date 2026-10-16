/**
 * BookMooch Admin Console - Quản lý sản phẩm (products.html)
 * Danh sách sản phẩm toàn sàn, lọc theo trạng thái kiểm duyệt, xem chi tiết
 * và các thao tác cơ bản: duyệt / từ chối / ẩn-hiện / xoá mềm.
 *
 * Dữ liệu hiện là mock tĩnh (giống accounts.js / orders.js). Khi backend sẵn sàng,
 * thay mảng productsData bằng lời gọi shared/api-client.js tới /api/admin/products.
 */

const productsData = [
    { id: 'SP-20458', title: 'Doraemon - Trọn bộ 45 tập', seller: 'Trần Đăng Khoa', sellerId: 'U-10231', category: 'Truyện tranh / Manga', price: 2450000, stock: 1, condition: 90, listingType: 'SELL', moderationStatus: 'APPROVED', active: true, createdAt: '12/10/2026', coverTone: 'orange', coverIcon: 'auto_stories', description: 'Trọn bộ 45 tập, bìa cứng, còn tem nhà xuất bản. Gáy sách đẹp, không rách, không viết vẽ.' },
    { id: 'SP-20461', title: 'Nhà Giả Kim (bìa cứng)', seller: 'Phạm Thị Ngọc', sellerId: 'U-11290', category: 'Văn học nước ngoài', price: 145000, stock: 3, condition: 95, listingType: 'SELL', moderationStatus: 'APPROVED', active: true, createdAt: '12/10/2026', coverTone: 'blue', coverIcon: 'menu_book', description: 'Bản in mới, bìa cứng, còn nguyên seal. Không trầy xước.' },
    { id: 'SP-20473', title: 'Sapiens - Lược sử loài người', seller: 'Ngô Hải Đăng', sellerId: 'U-11623', category: 'Khoa học / Lịch sử', price: 1240000, stock: 2, condition: 100, listingType: 'SELL', moderationStatus: 'PENDING', active: true, createdAt: '14/10/2026', coverTone: 'green', coverIcon: 'import_contacts', description: 'Sách mới 100%, chưa đọc. Bản dịch tiếng Việt đầy đủ.' },
    { id: 'SP-20479', title: 'Conan - Tập 1 đến 20', seller: 'Lê Văn Phát', sellerId: 'U-10902', category: 'Truyện tranh / Manga', price: 620000, stock: 1, condition: 80, listingType: 'SELL_AND_TRADE', active: true, moderationStatus: 'PENDING', createdAt: '14/10/2026', coverTone: 'orange', coverIcon: 'auto_stories', description: 'Combo 20 tập, có thể trao đổi với truyện cùng thể loại. Trạng thái đọc tốt.' },
    { id: 'SP-20488', title: 'Atomic Habits (bản dịch)', seller: 'Đỗ Minh Anh', sellerId: 'U-11402', category: 'Kỹ năng sống', price: 128000, stock: 5, condition: 88, listingType: 'SELL', moderationStatus: 'APPROVED', active: false, createdAt: '10/10/2026', coverTone: 'purple', coverIcon: 'menu_book', description: 'Sách đã đọc 1 lần, còn đẹp. Có ghi chú bút chì ở vài trang.' },
    { id: 'SP-20495', title: 'One Piece - Tập 90 đến 100', seller: 'Trần Đăng Khoa', sellerId: 'U-10231', category: 'Truyện tranh / Manga', price: 340000, stock: 1, condition: 92, listingType: 'TRADE', active: true, moderationStatus: 'PENDING', createdAt: '15/10/2026', coverTone: 'orange', coverIcon: 'auto_stories', description: 'Muốn trao đổi lấy truyện thể loại phiêu lưu. Bản đẹp, ít gấp gáy.' },
    { id: 'SP-20502', title: 'Tuổi Trẻ Đáng Giá Bao Nhiêu', seller: 'Phạm Thị Ngọc', sellerId: 'U-11290', category: 'Kỹ năng sống', price: 98000, stock: 4, condition: 85, listingType: 'SELL', moderationStatus: 'REJECTED', active: false, moderationReason: 'Hình ảnh sản phẩm không rõ nét / không hợp lệ', createdAt: '09/10/2026', coverTone: 'blue', coverIcon: 'menu_book', description: 'Bản in 2021, gáy hơi mờ. Ảnh chụp thiếu sáng nên bị từ chối.' },
    { id: 'SP-20510', title: 'Harry Potter - Trọn bộ 7 tập', seller: 'Bùi Thu Trang', sellerId: 'U-11501', category: 'Văn học nước ngoài', price: 1560000, stock: 1, condition: 98, listingType: 'SELL_AND_TRADE', active: true, moderationStatus: 'APPROVED', createdAt: '08/10/2026', coverTone: 'green', coverIcon: 'import_contacts', description: 'Trọn bộ 7 tập bìa mềm, giữ gìn cẩn thận. Có thể trao đổi.' },
    { id: 'SP-20517', title: 'Đắc Nhân Tâm (tái bản)', seller: 'Vũ Tuấn Kiệt', sellerId: 'U-11045', category: 'Kỹ năng sống', price: 76000, stock: 6, condition: 82, listingType: 'SELL', active: true, moderationStatus: 'PENDING', createdAt: '15/10/2026', coverTone: 'purple', coverIcon: 'menu_book', description: 'Sách cũ còn đọc tốt, bìa có vết gấp nhẹ. Giá tốt cho sinh viên.' },
    { id: 'SP-20523', title: 'Cây Cam Ngọt Của Tôi', seller: 'Ngô Hải Đăng', sellerId: 'U-11623', category: 'Văn học nước ngoài', price: 89000, stock: 2, condition: 96, listingType: 'SELL', active: true, moderationStatus: 'REJECTED', moderationReason: 'Giá bán vi phạm chính sách sàn', createdAt: '13/10/2026', coverTone: 'orange', coverIcon: 'import_contacts', description: 'Sách mới, chưa qua sử dụng. Giá đang để sai do nhập nhầm.' },
    { id: 'SP-20531', title: 'Truyện Kiều (bản chú giải)', seller: 'Trịnh Bảo Châu', sellerId: 'U-11740', category: 'Văn học trong nước', price: 210000, stock: 1, condition: 90, listingType: 'SELL', active: true, moderationStatus: 'APPROVED', createdAt: '07/10/2026', coverTone: 'blue', coverIcon: 'menu_book', description: 'Bản chú giải đầy đủ, bìa cứng. Sách sưu tầm, còn đẹp.' },
];

const moderationMeta = {
    PENDING: { label: 'Chờ duyệt', badge: 'warning' },
    APPROVED: { label: 'Đã duyệt', badge: 'success' },
    REJECTED: { label: 'Từ chối', badge: 'danger' },
};

const listingTypeLabel = {
    SELL: 'Bán',
    TRADE: 'Trao đổi',
    SELL_AND_TRADE: 'Bán / Trao đổi',
};

const PRODUCTS_PAGE_SIZE = 5;
let currentProductsPage = 1;

let approveTargetId = null;
let rejectTargetId = null;
let visibilityTarget = null; // { id, nextActive }
let deleteTargetId = null;

document.addEventListener('DOMContentLoaded', () => {
    renderKpis();
    renderProductCounts();
    renderProductsTable();

    initSlidingTabs(document.getElementById('productFilterTabs'), () => {
        currentProductsPage = 1;
        renderProductsTable();
    });

    const search = document.getElementById('productSearch');
    if (search) {
        search.addEventListener('input', debounce(() => {
            currentProductsPage = 1;
            renderProductsTable();
        }, 200));
    }
});

/* ==========================================================================
   KPI & SỐ LƯỢNG TAB
   ========================================================================== */
function countBy(status) {
    return productsData.filter((p) => p.moderationStatus === status).length;
}

function countHidden() {
    return productsData.filter((p) => p.active === false).length;
}

function renderKpis() {
    const total = productsData.length;
    const pending = countBy('PENDING');
    const approvedActive = productsData.filter((p) => p.moderationStatus === 'APPROVED' && p.active !== false).length;
    const hidden = productsData.filter((p) => p.active === false || p.moderationStatus === 'REJECTED').length;

    setText('kpiTotal', formatNumber(total));
    setText('kpiPending', formatNumber(pending));
    setText('kpiApproved', formatNumber(approvedActive));
    setText('kpiHidden', formatNumber(hidden));
}

function renderProductCounts() {
    setText('tabCountAll', productsData.length);
    setText('tabCountPending', countBy('PENDING'));
    setText('tabCountApproved', countBy('APPROVED'));
    setText('tabCountRejected', countBy('REJECTED'));
    setText('tabCountHidden', countHidden());
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

/* ==========================================================================
   BẢNG SẢN PHẨM & PHÂN TRANG
   ========================================================================== */
function getCurrentFilter() {
    const activeTab = document.querySelector('#productFilterTabs .tab-chip.active');
    return activeTab ? activeTab.dataset.filter : 'all';
}

function getFilteredProducts() {
    const filter = getCurrentFilter();
    const searchEl = document.getElementById('productSearch');
    const query = (searchEl ? searchEl.value : '').trim().toLowerCase();

    return productsData.filter((p) => {
        let visible = true;
        if (filter === 'pending') visible = p.moderationStatus === 'PENDING';
        else if (filter === 'approved') visible = p.moderationStatus === 'APPROVED';
        else if (filter === 'rejected') visible = p.moderationStatus === 'REJECTED';
        else if (filter === 'hidden') visible = p.active === false;

        if (visible && query) {
            visible = p.title.toLowerCase().includes(query)
                || p.id.toLowerCase().includes(query)
                || p.seller.toLowerCase().includes(query)
                || p.category.toLowerCase().includes(query);
        }
        return visible;
    });
}

function renderProductsTable() {
    const filtered = getFilteredProducts();
    const totalPages = Math.max(1, Math.ceil(filtered.length / PRODUCTS_PAGE_SIZE));
    if (currentProductsPage > totalPages) currentProductsPage = totalPages;
    const start = (currentProductsPage - 1) * PRODUCTS_PAGE_SIZE;
    const pageItems = filtered.slice(start, start + PRODUCTS_PAGE_SIZE);

    const body = document.getElementById('productsTableBody');
    body.innerHTML = pageItems.length ? pageItems.map(renderProductRow).join('')
        : `<tr><td colspan="7" style="text-align:center; padding:32px; color:var(--text-muted);">Không tìm thấy sản phẩm phù hợp</td></tr>`;

    renderProductsPagination(filtered.length, start, pageItems.length, totalPages);
}

function renderProductRow(p) {
    const meta = moderationMeta[p.moderationStatus];
    const visibilityText = p.active === false ? 'Đã ẩn khỏi sàn' : 'Đang hiển thị';

    let actions = `<button class="btn btn-outline btn-sm" onclick="viewProductDetail('${p.id}')">Xem</button>`;
    if (p.moderationStatus === 'PENDING') {
        actions += `
          <button class="btn btn-primary btn-sm" onclick="openApproveModal('${p.id}')">Duyệt</button>
          <button class="btn btn-sm btn-danger" onclick="openRejectModal('${p.id}')">Từ chối</button>`;
    } else if (p.active === false) {
        actions += `
          <button class="btn btn-primary btn-sm" onclick="openVisibilityModal('${p.id}', true)">Hiện</button>`;
    } else {
        actions += `
          <button class="btn btn-outline btn-sm" onclick="openVisibilityModal('${p.id}', false)">Ẩn</button>`;
    }

    return `
    <tr data-tone="${p.coverTone}">
      <td>
        <div class="product-title-cell">
          <div class="product-thumb tone-${p.coverTone}"><span class="material-symbols-outlined">${p.coverIcon}</span></div>
          <div class="product-title-text">
            <span title="${escapeHtml(p.title)}">${escapeHtml(p.title)}</span>
            <span class="product-code">${p.id}</span>
          </div>
        </div>
      </td>
      <td>
        <div class="identity-name" style="font-size:.86rem;">${escapeHtml(p.seller)}</div>
        <div class="identity-sub">${p.sellerId}</div>
      </td>
      <td>${escapeHtml(p.category)}</td>
      <td style="text-align:right; font-weight:700; color:var(--text-main); white-space:nowrap;">${formatNumber(p.price)} ₫</td>
      <td style="text-align:center;">${p.stock}</td>
      <td style="text-align:center;">
        <span class="badge badge-${meta.badge}"><span class="badge-dot"></span>${meta.label}</span>
        <span class="mod-visibility-sub">${visibilityText}</span>
      </td>
      <td style="text-align:center;">
        <div style="display:flex; gap:6px; justify-content:center; flex-wrap:wrap;">${actions}</div>
      </td>
    </tr>`;
}

function renderProductsPagination(total, start, count, totalPages) {
    const info = document.getElementById('productsPaginationInfo');
    info.innerHTML = total === 0
        ? 'Không có sản phẩm nào phù hợp với bộ lọc hiện tại'
        : `Hiển thị <strong>${start + 1} - ${start + count}</strong> của <strong>${total}</strong> sản phẩm`;

    const controls = document.getElementById('productsPaginationControls');
    let pageButtons = '';
    for (let p = 1; p <= totalPages; p++) {
        pageButtons += `<button class="page-btn ${p === currentProductsPage ? 'active' : ''}" onclick="goToProductsPage(${p})">${p}</button>`;
    }
    controls.innerHTML = `
      <button class="page-btn" ${currentProductsPage <= 1 ? 'disabled' : ''} onclick="goToProductsPage(${currentProductsPage - 1})"><span class="material-symbols-outlined" style="font-size:16px;">chevron_left</span></button>
      ${pageButtons}
      <button class="page-btn" ${currentProductsPage >= totalPages ? 'disabled' : ''} onclick="goToProductsPage(${currentProductsPage + 1})"><span class="material-symbols-outlined" style="font-size:16px;">chevron_right</span></button>
    `;
}

function goToProductsPage(page) {
    currentProductsPage = page;
    renderProductsTable();
}

/* ==========================================================================
   CHI TIẾT SẢN PHẨM
   ========================================================================== */
function findProduct(id) {
    return productsData.find((p) => p.id === id);
}

function viewProductDetail(id) {
    const p = findProduct(id);
    if (!p) return;

    const meta = moderationMeta[p.moderationStatus];
    const visibilityText = p.active === false ? 'Đang ẩn khỏi sàn' : 'Đang hiển thị công khai';

    document.getElementById('productDetailBody').innerHTML = `
    <div class="product-detail-head">
      <div class="product-thumb tone-${p.coverTone}"><span class="material-symbols-outlined">${p.coverIcon}</span></div>
      <div style="min-width:0;">
        <div style="color:var(--text-muted); font-size:.72rem; font-weight:700; text-transform:uppercase; letter-spacing:.04em; margin-bottom:4px;">Sản phẩm</div>
        <div style="font-weight:700; color:var(--text-main); font-size:.94rem; margin-bottom:4px;">${escapeHtml(p.title)}</div>
        <div class="product-code" style="color:var(--text-muted); font-size:.8rem; font-weight:600;">${p.id} <button class="copy-btn" style="background:none;border:0;cursor:pointer;" onclick="copyText('${p.id}')" title="Sao chép mã sản phẩm"><span class="material-symbols-outlined" style="font-size:14px;">content_copy</span></button></div>
      </div>
      <span class="badge badge-${meta.badge}" style="margin-left:auto; align-self:flex-start;"><span class="badge-dot"></span>${meta.label}</span>
    </div>

    <div class="stat-mini-row">
      <div class="stat-mini"><h5>Người bán</h5><div class="val" style="font-size:.86rem;">${escapeHtml(p.seller)}</div><div class="identity-sub" style="margin-top:2px;">${p.sellerId}</div></div>
      <div class="stat-mini"><h5>Thể loại</h5><div class="val" style="font-size:.86rem;">${escapeHtml(p.category)}</div></div>
      <div class="stat-mini"><h5>Giá bán</h5><div class="val" style="font-size:.86rem;">${formatNumber(p.price)} ₫</div></div>
      <div class="stat-mini"><h5>Hình thức</h5><div class="val" style="font-size:.86rem;">${listingTypeLabel[p.listingType] || p.listingType}</div></div>
      <div class="stat-mini"><h5>Tồn kho</h5><div class="val" style="font-size:.86rem;">${p.stock}</div></div>
      <div class="stat-mini"><h5>Tình trạng</h5><div class="val" style="font-size:.86rem;">${p.condition}%</div></div>
      <div class="stat-mini"><h5>Ngày đăng</h5><div class="val" style="font-size:.86rem;">${p.createdAt}</div></div>
      <div class="stat-mini"><h5>Hiển thị</h5><div class="val" style="font-size:.86rem;">${visibilityText}</div></div>
    </div>

    <div class="product-detail-desc">${escapeHtml(p.description)}</div>

    ${p.moderationReason ? `<div class="product-reject-note"><strong>Lý do từ chối:</strong> ${escapeHtml(p.moderationReason)}</div>` : ''}
  `;

    const extra = document.getElementById('productDetailExtraActions');
    if (p.moderationStatus === 'PENDING') {
        extra.innerHTML = `
          <button class="btn btn-danger" onclick="closeModal('productDetailModal'); openRejectModal('${p.id}');">Từ chối</button>
          <button class="btn btn-primary" onclick="closeModal('productDetailModal'); openApproveModal('${p.id}');">Phê duyệt</button>`;
    } else {
        const visibilityBtn = p.active === false
            ? `<button class="btn btn-primary" onclick="closeModal('productDetailModal'); openVisibilityModal('${p.id}', true);">Hiện sản phẩm</button>`
            : `<button class="btn btn-outline" onclick="closeModal('productDetailModal'); openVisibilityModal('${p.id}', false);">Ẩn sản phẩm</button>`;
        extra.innerHTML = `${visibilityBtn}
          <button class="btn btn-danger" onclick="closeModal('productDetailModal'); openDeleteModal('${p.id}');">Xoá</button>`;
    }

    openModal('productDetailModal');
}

/* ==========================================================================
   PHÊ DUYỆT
   ========================================================================== */
function openApproveModal(id) {
    const p = findProduct(id);
    if (!p) return;
    approveTargetId = id;
    document.getElementById('approveProductName').textContent = p.title;
    openModal('productApproveModal');
}

function confirmApprove() {
    if (!approveTargetId) return;
    const p = findProduct(approveTargetId);
    if (p) {
        p.moderationStatus = 'APPROVED';
        p.active = true;
        delete p.moderationReason;
        refreshAll();
        showToast(`Đã phê duyệt sản phẩm "${p.title}"`, 'success');
    }
    closeModal('productApproveModal');
    approveTargetId = null;
}

/* ==========================================================================
   TỪ CHỐI
   ========================================================================== */
function openRejectModal(id) {
    const p = findProduct(id);
    if (!p) return;
    rejectTargetId = id;
    document.getElementById('rejectProductName').textContent = p.title;
    document.getElementById('rejectReasonSelect').selectedIndex = 0;
    document.getElementById('rejectNote').value = '';
    openModal('productRejectModal');
}

function confirmReject() {
    if (!rejectTargetId) return;
    const p = findProduct(rejectTargetId);
    if (p) {
        const select = document.getElementById('rejectReasonSelect');
        const reason = select.options[select.selectedIndex].text;
        const note = document.getElementById('rejectNote').value.trim();
        p.moderationStatus = 'REJECTED';
        p.active = false;
        p.moderationReason = note ? `${reason} - ${note}` : reason;
        refreshAll();
        showToast(`Đã từ chối sản phẩm "${p.title}"`, 'danger');
    }
    closeModal('productRejectModal');
    rejectTargetId = null;
}

/* ==========================================================================
   ẨN / HIỆN
   ========================================================================== */
function openVisibilityModal(id, nextActive) {
    const p = findProduct(id);
    if (!p) return;
    visibilityTarget = { id, nextActive };
    document.getElementById('visibilityModalTitle').textContent = nextActive ? 'Hiện sản phẩm' : 'Ẩn sản phẩm';
    document.getElementById('visibilityModalDesc').innerHTML = nextActive
        ? `Cho phép sản phẩm <strong>${escapeHtml(p.title)}</strong> hiển thị công khai trở lại với người mua?`
        : `Ẩn sản phẩm <strong>${escapeHtml(p.title)}</strong> khỏi sàn? Người mua sẽ không còn thấy sản phẩm này.`;
    const btn = document.getElementById('visibilityConfirmBtn');
    btn.textContent = nextActive ? 'Hiện sản phẩm' : 'Ẩn sản phẩm';
    openModal('productVisibilityModal');
}

function confirmVisibility() {
    if (!visibilityTarget) return;
    const p = findProduct(visibilityTarget.id);
    if (p) {
        p.active = visibilityTarget.nextActive;
        refreshAll();
        showToast(visibilityTarget.nextActive
            ? `Đã hiện lại sản phẩm "${p.title}"`
            : `Đã ẩn sản phẩm "${p.title}"`, visibilityTarget.nextActive ? 'success' : 'warning');
    }
    closeModal('productVisibilityModal');
    visibilityTarget = null;
}

/* ==========================================================================
   XOÁ MỀM
   ========================================================================== */
function openDeleteModal(id) {
    const p = findProduct(id);
    if (!p) return;
    deleteTargetId = id;
    document.getElementById('deleteModalDesc').innerHTML =
        `Xoá sản phẩm <strong>${escapeHtml(p.title)}</strong> (${p.id})? Sản phẩm sẽ bị gỡ khỏi sàn và không thể khôi phục trên giao diện quản trị.`;
    openModal('productDeleteModal');
}

function confirmDelete() {
    if (!deleteTargetId) return;
    const index = productsData.findIndex((p) => p.id === deleteTargetId);
    if (index !== -1) {
        const removed = productsData.splice(index, 1)[0];
        refreshAll();
        showToast(`Đã xoá sản phẩm "${removed.title}"`, 'danger');
    }
    closeModal('productDeleteModal');
    deleteTargetId = null;
}

/* ==========================================================================
   TIỆN ÍCH DÙNG CHUNG
   ========================================================================== */
function refreshAll() {
    renderKpis();
    renderProductCounts();
    renderProductsTable();
}

