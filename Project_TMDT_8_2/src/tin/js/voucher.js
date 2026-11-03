/**
 * ============================================================================
 * COMICHUB - KHO VOUCHER & TỐI ƯU ƯU ĐÃI (VOUCHER VAULT LOGIC)
 * ============================================================================
 * File: voucher-vault.js / script.js
 * Mô tả: Quản lý mã voucher đang chọn và chuyển mã sang trang Checkout để Backend xác thực.
 */

document.addEventListener('DOMContentLoaded', () => {
    'use strict';

    document.querySelectorAll('.voucher-tabs-bar, .voucher-category-group').forEach((section) => {
        section.hidden = true;
    });

    // --------------------------------------------------------------------------
    // 0. TIỆN ÍCH DÙNG CHUNG: TOAST NOTIFICATION
    // --------------------------------------------------------------------------
    /**
     * Hiển thị thông báo Toast nhanh ở góc dưới màn hình
     * @param {string} message - Nội dung thông báo
     * @param {string} type - Loại thông báo: 'success' | 'warning' | 'danger' | 'info' | 'primary'
     * @param {string} icon - Class FontAwesome icon (VD: 'fa-circle-info')
     */
    const showToast = (message, type = 'info', icon = 'fa-circle-info') => {
        let container = document.getElementById('comichub-toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'comichub-toast-container';
            container.style.cssText = `
        position: fixed;
        bottom: 90px;
        right: 24px;
        z-index: 99999;
        display: flex;
        flex-direction: column;
        gap: 10px;
        pointer-events: none;
      `;
            document.body.appendChild(container);
        }

        const toast = document.createElement('div');
        const bgColors = {
            success: '#16a34a',
            warning: '#f59e0b',
            danger: '#e11d48',
            info: '#0f172a',
            primary: '#f97316'
        };

        toast.style.cssText = `
      background-color: ${bgColors[type] || bgColors.info};
      color: #ffffff;
      padding: 12px 18px;
      border-radius: 12px;
      box-shadow: 0 10px 25px -5px rgba(0,0,0,0.25);
      font-size: 13px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 10px;
      opacity: 0;
      transform: translateY(20px);
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      pointer-events: auto;
      max-width: 380px;
      line-height: 1.4;
    `;

        const toastIcon = document.createElement('i');
        toastIcon.className = `fa-solid ${icon}`;
        toastIcon.style.fontSize = '16px';
        const toastMessage = document.createElement('span');
        toastMessage.textContent = message;
        toast.append(toastIcon, toastMessage);
        container.appendChild(toast);

        requestAnimationFrame(() => {
            toast.style.opacity = '1';
            toast.style.transform = 'translateY(0)';
        });

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(-10px)';
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    };

    // --------------------------------------------------------------------------
    // 1. TIỆN ÍCH ĐỊNH DẠNG TIỀN TỆ & STATE VOUCHER DANG CHỌN
    // --------------------------------------------------------------------------
    const voucherStorageKey = 'comichub_checkout_voucher_codes';
    const voucherState = {
        platformCode: null,
        sellerCode: null,
        shippingCode: null
    };

    try {
        const saved = JSON.parse(sessionStorage.getItem(voucherStorageKey) || 'null');
        for (const field of Object.keys(voucherState)) {
            if (typeof saved?.[field] === 'string') voucherState[field] = saved[field];
        }
    } catch {
        sessionStorage.removeItem(voucherStorageKey);
    }

    const updateFloatingBar = () => {
        const activeCodes = Object.values(voucherState).filter(Boolean);

        const floatingTitleRow = document.querySelector('.floating-title-row');
        if (floatingTitleRow) {
            if (activeCodes.length === 0) {
                floatingTitleRow.innerHTML = `<strong>Chưa chọn mã ưu đãi nào</strong>`;
            } else {
                const badges = activeCodes.map((code) => {
                    const badge = document.createElement('span');
                    badge.className = 'tag-code-active';
                    badge.textContent = code;
                    return badge;
                });
                floatingTitleRow.replaceChildren();
                const message = document.createElement('strong');
                message.textContent = `Đã chọn ${activeCodes.length} mã; cần xác thực ở thanh toán: `;
                floatingTitleRow.append(message, ...badges);
            }
        }

        const savingCalcEl = document.querySelector('.floating-saving-calc');
        if (savingCalcEl) {
            savingCalcEl.textContent = 'Số tiền giảm sẽ được tính sau khi Backend xác thực mã tại trang thanh toán.';
        }

        const cartContextTag = document.querySelector('.cart-context-badge .tag-auto-applied, .cart-context-badge-tag');
        if (cartContextTag) {
            cartContextTag.textContent = activeCodes.length > 0
                ? `${activeCodes.length} MÃ ĐANG CHỌN · CHỜ XÁC THỰC`
                : 'CHƯA CHỌN MÃ';
        }

        const btnConfirm = document.querySelector('.btn-confirm-checkout');
        if (btnConfirm) {
            btnConfirm.disabled = false;
            btnConfirm.style.opacity = '1';
            btnConfirm.style.cursor = 'pointer';
        }
    };

    // --------------------------------------------------------------------------
    // 3. TƯƠNG TÁC TABS PHÂN LOẠI VOUCHER (SHOPEE-STYLE TABS)
    // --------------------------------------------------------------------------
    const tabButtons = document.querySelectorAll('.voucher-tab-item, button[class*="voucher-tab"]');
    const categoryGroups = document.querySelectorAll('.voucher-category-group');

    if (tabButtons.length > 0) {
        tabButtons.forEach((tab, index) => {
            tab.addEventListener('click', () => {
                tabButtons.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');

                const tabText = tab.textContent.trim();
                showToast(`Đang hiển thị: ${tabText}`, 'info', 'fa-filter');

                categoryGroups.forEach((group, gIdx) => {
                    group.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
                    group.style.opacity = '0';
                    group.style.transform = 'translateY(6px)';

                    setTimeout(() => {
                        if (index === 0) {
                            group.style.display = 'block';
                        } else if (index === 1) {
                            group.style.display = (gIdx === 0) ? 'block' : 'none';
                        } else if (index === 2) {
                            group.style.display = (gIdx === 1) ? 'block' : 'none';
                        } else if (index === 3) {
                            group.style.display = (gIdx === 2) ? 'block' : 'none';
                        } else {
                            group.style.display = (gIdx >= 3) ? 'block' : 'none';
                        }

                        requestAnimationFrame(() => {
                            group.style.opacity = '1';
                            group.style.transform = 'translateY(0)';
                        });
                    }, 150);
                });
            });
        });
    }

    // --------------------------------------------------------------------------
    // 4. BỘ SẮP XẾP VOUCHER (SORT DROPDOWN)
    // --------------------------------------------------------------------------
    const sortSelect = document.querySelector('.sort-select, select[class*="sort"]');
    if (sortSelect) {
        sortSelect.addEventListener('change', (e) => {
            const val = e.target.value;
            showToast(`Đã sắp xếp danh sách voucher theo: ${val}`, 'info', 'fa-arrow-down-wide-short');
            const allGrids = document.querySelectorAll('.voucher-cards-grid');
            allGrids.forEach(grid => {
                grid.style.opacity = '0.5';
                setTimeout(() => { grid.style.opacity = '1'; }, 200);
            });
        });
    }

    // --------------------------------------------------------------------------
    // 5. XỬ LÝ CHỌN / BỎ CHỌN / ĐỔI VOUCHER TRÊN TỪNG THẺ VÉ (TICKET CARDS)
    // --------------------------------------------------------------------------
    const initTicketActions = () => {
        const ticketCards = document.querySelectorAll('.voucher-ticket-card');

        ticketCards.forEach((card) => {
            const codeBadge = card.querySelector('.ticket-code-badge, [class*="code"]')?.textContent.trim() || '';
            const actionBtn = card.querySelector('.btn-ticket-action');
            const termsBtn = card.querySelector('.btn-terms');

            const categoryTitle = card.closest('.voucher-category-group')
                ?.querySelector('.category-title-wrap h2')?.textContent.toLowerCase() || '';
            const field = categoryTitle.includes('vận chuyển')
                ? 'shippingCode'
                : categoryTitle.includes('toàn sàn')
                    ? 'platformCode'
                    : categoryTitle.includes('shop')
                        ? 'sellerCode'
                        : null;

            card.classList.remove('applied-border');
            card.querySelector('.badge-in-use')?.remove();
            if (actionBtn?.textContent.includes('Bỏ Chọn')) {
                actionBtn.className = 'btn-ticket-action btn-primary-orange';
                actionBtn.textContent = 'Dùng Ngay';
            }

            // 5.1 Nút Thao tác chính trên thẻ Voucher
            if (actionBtn) {
                actionBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    const btnText = actionBtn.textContent.trim();

                    // Thao tác: "Bỏ Chọn"
                    if (btnText.includes('Bỏ Chọn')) {
                        const previousCode = field ? voucherState[field] : null;
                        if (field) voucherState[field] = null;
                        if (!saveVoucherCodes()) {
                            if (field) voucherState[field] = previousCode;
                            return;
                        }

                        card.classList.remove('applied-border');
                        actionBtn.className = 'btn-ticket-action btn-primary-orange';
                        actionBtn.textContent = 'Dùng Ngay';

                        const badgeInUse = card.querySelector('.badge-in-use');
                        if (badgeInUse) badgeInUse.remove();

                        updateFloatingBar();
                        showToast(`Đã gỡ voucher "${codeBadge}"`, 'info', 'fa-xmark');
                        return;
                    }

                    // Thao tác: "Đổi Mã Này" hoặc "Dùng Ngay"
                    if (btnText.includes('Đổi') || btnText.includes('Dùng Ngay')) {
                        if (!field) {
                            showToast('Backend checkout hiện chỉ hỗ trợ voucher PLATFORM, SELLER và SHIPPING.', 'warning', 'fa-triangle-exclamation');
                            return;
                        }

                        // Reset trạng thái hiển thị của các thẻ cùng phân loại
                        ticketCards.forEach(otherCard => {
                            const otherTitle = otherCard.closest('.voucher-category-group')
                                ?.querySelector('.category-title-wrap h2')?.textContent.toLowerCase() || '';
                            const otherField = otherTitle.includes('vận chuyển')
                                ? 'shippingCode'
                                : otherTitle.includes('toàn sàn')
                                    ? 'platformCode'
                                    : otherTitle.includes('shop')
                                        ? 'sellerCode'
                                        : null;
                            if (otherField === field && otherCard !== card) {
                                otherCard.classList.remove('applied-border');
                                const otherBtn = otherCard.querySelector('.btn-ticket-action');
                                if (otherBtn) {
                                    otherBtn.className = 'btn-ticket-action btn-primary-orange';
                                    otherBtn.textContent = 'Dùng Ngay';
                                }
                                const otherBadge = otherCard.querySelector('.badge-in-use');
                                if (otherBadge) otherBadge.remove();
                            }
                        });

                        const previousCode = voucherState[field];
                        voucherState[field] = codeBadge;
                        if (!saveVoucherCodes()) {
                            voucherState[field] = previousCode;
                            return;
                        }

                        card.classList.add('applied-border');
                        card.classList.remove('highlight-border');
                        actionBtn.className = 'btn-ticket-action btn-outline-cancel';
                        actionBtn.textContent = 'Bỏ Chọn (chờ kiểm tra)';

                        const badgeRow = card.querySelector('.ticket-badge-row');
                        if (badgeRow && !badgeRow.querySelector('.badge-in-use')) {
                            const inUsePill = document.createElement('span');
                            inUsePill.className = 'badge-in-use';
                            inUsePill.textContent = 'CHỜ BACKEND XÁC THỰC';
                            badgeRow.insertBefore(inUsePill, badgeRow.firstChild);
                        }

                        updateFloatingBar();
                        showToast(`Đã chọn mã "${codeBadge}". Mã sẽ được xác thực tại thanh toán.`, 'info', 'fa-ticket');
                        return;
                    }

                    // Thao tác: "Lưu Thêm" / "Lưu Mã"
                    if (btnText.includes('Lưu')) {
                        showToast('Backend hiện chưa cung cấp API lưu hoặc liệt kê kho voucher.', 'warning', 'fa-circle-info');
                        return;
                    }

                    // Thao tác: "Nhắc Tôi" (Flash Sale)
                    if (btnText.includes('Nhắc Tôi')) {
                        actionBtn.innerHTML = '<i class="fa-solid fa-check"></i> Đã Đặt Nhắc';
                        actionBtn.style.backgroundColor = '#dcfce7';
                        actionBtn.style.color = '#16a34a';
                        showToast('Hệ thống sẽ gửi thông báo đẩy trước 5 phút khi mở đợt flash voucher 12:00 trưa!', 'success', 'fa-bell');
                        return;
                    }
                });
            }

            // 5.2 Nút Xem "Điều Kiện"
            if (termsBtn) {
                termsBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    const vTitle = card.querySelector('.voucher-discount-title')?.textContent.trim() || 'Mã Ưu Đãi';
                    const vCond = card.querySelector('.voucher-condition-text')?.textContent.trim() || 'Áp dụng cho mọi đơn hàng đủ điều kiện.';
                    const vHsd = card.querySelector('.expiring-time')?.textContent.trim() || 'HSD: 31/12/2026';
                    showTermsModal(codeBadge, vTitle, vCond, vHsd);
                });
            }
        });
    };

    // --------------------------------------------------------------------------
    // 6. MODAL XEM CHI TIẾT ĐIỀU KIỆN SỬ DỤNG VOUCHER
    // --------------------------------------------------------------------------
    const showTermsModal = (code, title, condition, hsd) => {
        const modalBackdrop = document.createElement('div');
        modalBackdrop.id = 'voucher-terms-modal';
        modalBackdrop.style.cssText = `
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.65);
      backdrop-filter: blur(4px);
      z-index: 99999;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
      opacity: 0;
      transition: opacity 0.25s ease;
    `;

        modalBackdrop.innerHTML = `
      <div style="
        background: #ffffff;
        border-radius: 20px;
        padding: 28px;
        max-width: 480px;
        width: 100%;
        box-shadow: 0 25px 50px -12px rgba(0,0,0,0.3);
        transform: translateY(20px);
        transition: transform 0.25s ease;
        font-family: inherit;
      ">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
          <div style="display: flex; align-items: center; gap: 10px;">
            <div style="width: 36px; height: 36px; border-radius: 10px; background: #ffedd5; color: #f97316; display: flex; align-items: center; justify-content: center; font-size: 16px;">
              <i class="fa-solid fa-receipt"></i>
            </div>
            <div>
              <h3 style="font-size: 16px; font-weight: 800; color: #0f172a; margin: 0;">Điều Kiện Áp Dụng Voucher</h3>
              <span style="font-size: 11px; color: #64748b;">Mã ưu đãi độc quyền sưu tầm ComicHub</span>
            </div>
          </div>
          <button class="btn-close-modal" style="border: none; background: transparent; font-size: 20px; color: #94a3b8; cursor: pointer;">&times;</button>
        </div>

        <div style="background: #fff7ed; border: 1px solid #fed7aa; border-radius: 12px; padding: 14px; margin-bottom: 16px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <span style="font-size: 14px; font-weight: 800; color: #ea580c;">${code || 'COMICHUB'}</span>
            <span style="font-size: 11px; font-weight: 700; color: #b45309;">${hsd}</span>
          </div>
          <h4 style="font-size: 15px; font-weight: 800; color: #0f172a; margin: 0;">${title}</h4>
        </div>

        <div style="display: flex; flex-direction: column; gap: 10px; font-size: 12px; color: #475569; line-height: 1.5; margin-bottom: 22px;">
          <div style="display: flex; align-items: flex-start; gap: 8px;">
            <i class="fa-solid fa-check text-[#16a34a] mt-1"></i>
            <span><strong>Điều kiện chi tiêu:</strong> ${condition}</span>
          </div>
          <div style="display: flex; align-items: flex-start; gap: 8px;">
            <i class="fa-solid fa-check text-[#16a34a] mt-1"></i>
            <span><strong>Cơ chế gộp mã:</strong> Cho phép áp dụng đồng thời với 01 mã Freeship SafeShip và 01 mã trợ giá Kèo Barter / Xu Otaku.</span>
          </div>
          <div style="display: flex; align-items: flex-start; gap: 8px;">
            <i class="fa-solid fa-check text-[#16a34a] mt-1"></i>
            <span><strong>Quy chuẩn đóng gói:</strong> Tự động tích hợp miễn phí đóng gói 3 lớp chống sốc bảo vệ góc cạnh truyện.</span>
          </div>
        </div>

        <div style="display: flex; justify-content: flex-end;">
          <button class="btn-close-terms" style="padding: 10px 24px; border-radius: 9999px; border: none; background: #f97316; color: #ffffff; font-weight: 700; font-size: 13px; cursor: pointer; box-shadow: 0 4px 12px rgba(249, 115, 22, 0.35);">
            Đã Hiểu
          </button>
        </div>
      </div>
    `;

        document.body.appendChild(modalBackdrop);

        requestAnimationFrame(() => {
            modalBackdrop.style.opacity = '1';
            modalBackdrop.firstElementChild.style.transform = 'translateY(0)';
        });

        const closeModal = () => {
            modalBackdrop.style.opacity = '0';
            modalBackdrop.firstElementChild.style.transform = 'translateY(20px)';
            setTimeout(() => modalBackdrop.remove(), 250);
        };

        modalBackdrop.querySelector('.btn-close-modal').addEventListener('click', closeModal);
        modalBackdrop.querySelector('.btn-close-terms').addEventListener('click', closeModal);
        modalBackdrop.addEventListener('click', (e) => {
            if (e.target === modalBackdrop) closeModal();
        });
    };

    // --------------------------------------------------------------------------
    // 7. FORM NHẬP MÃ VOUCHER MỚI & GỢI Ý 1-CLICK TẠI HERO BANNER
    // --------------------------------------------------------------------------
    const voucherInput = document.querySelector('.voucher-text-input');
    const voucherTypeSelect = document.querySelector('.voucher-type-select');
    const btnSaveVoucher = document.querySelector('.btn-save-voucher');

    const processInputCode = (rawCode, field) => {
        const clean = rawCode.trim().toUpperCase();
        if (!clean) {
            showToast('Vui lòng nhập mã ưu đãi.', 'warning', 'fa-triangle-exclamation');
            return;
        }
        if (!Object.hasOwn(voucherState, field)) {
            showToast('Vui lòng chọn loại voucher PLATFORM, SELLER hoặc SHIPPING.', 'warning', 'fa-triangle-exclamation');
            return;
        }

        const previousCode = voucherState[field];
        voucherState[field] = clean;
        if (!saveVoucherCodes()) {
            voucherState[field] = previousCode;
            return;
        }
        updateFloatingBar();
        if (voucherInput) voucherInput.value = '';
        showToast(`Đã chuyển mã "${clean}" sang checkout. Backend sẽ xác thực trước khi áp dụng.`, 'info', 'fa-ticket');
    };

    if (btnSaveVoucher) {
        btnSaveVoucher.addEventListener('click', (e) => {
            e.preventDefault();
            if (voucherInput) processInputCode(voucherInput.value, voucherTypeSelect?.value);
        });
    }

    if (voucherInput) {
        voucherInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                processInputCode(voucherInput.value, voucherTypeSelect?.value);
            }
        });
    }

    function saveVoucherCodes() {
        try {
            sessionStorage.setItem(voucherStorageKey, JSON.stringify(voucherState));
            return true;
        } catch {
            showToast('Không thể lưu mã tạm trong trình duyệt. Vui lòng kiểm tra quyền lưu trữ rồi thử lại.', 'danger', 'fa-circle-exclamation');
            return false;
        }
    }

    // --------------------------------------------------------------------------
    // 8. ĐỒNG Ý & THANH TOÁN (LƯU LOCALSTORAGE VÀ ĐIỀU HƯỚNG SANG CHECKOUT.HTML)
    // --------------------------------------------------------------------------
    const btnCheckoutConfirm = document.querySelector('.btn-confirm-checkout');
    const btnContinueBrowse = document.querySelector('.btn-continue-browse, a[href*="cart"]');

    if (btnCheckoutConfirm) {
        btnCheckoutConfirm.addEventListener('click', (e) => {
            e.preventDefault();

            if (!saveVoucherCodes()) return;
            showToast('Đang chuyển sang thanh toán. Backend sẽ xác thực các mã đã chọn.', 'primary', 'fa-shield-halved');

            setTimeout(() => {
                window.location.href = 'payment.html';
            }, 700);
        });
    }

    if (btnContinueBrowse) {
        btnContinueBrowse.addEventListener('click', (e) => {
            e.preventDefault();
            showToast('Đang quay lại giỏ hàng...', 'info', 'fa-arrow-left');
            setTimeout(() => {
                window.location.href = 'shopping_cart.html';
            }, 500);
        });
    }

    // --------------------------------------------------------------------------
    // 9. ĐỒNG HỒ ĐẾM NGƯỢC KHUNG GIỜ VÀNG (GOLDEN HOURS TIMER)
    // --------------------------------------------------------------------------
    const countdownDigits = document.querySelector('.countdown-digits');
    if (countdownDigits) {
        let secondsLeft = 2 * 3600 + 14 * 60 + 35; // 02:14:35
        setInterval(() => {
            if (secondsLeft > 0) {
                secondsLeft--;
                const h = String(Math.floor(secondsLeft / 3600)).padStart(2, '0');
                const m = String(Math.floor((secondsLeft % 3600) / 60)).padStart(2, '0');
                const s = String(secondsLeft % 60).padStart(2, '0');
                countdownDigits.textContent = `${h}:${m}:${s}`;
            }
        }, 1000);
    }

    // --------------------------------------------------------------------------
    // 10. KHỞI TẠO TẤT CẢ LOGIC BAN ĐẦU
    // --------------------------------------------------------------------------
    initTicketActions();
    updateFloatingBar();

    console.log('ComicHub Voucher Vault script (voucher-vault.js) initialized successfully.');
});