(function () {
  /**
   * Xóa các khóa liên quan đến phiên đăng nhập (user + admin mock)
   * redirectUrl: đường dẫn sẽ điều hướng sau khi xóa (mặc định tới trang login trong repo)
   */
  function clearAuthStorage() {
    const keys = [
      // frontend user session
      "bookmooch_session",
      "bookmooch_profile",
      "bookmooch_avatar",
      "userRole",
      "userName",
      "rememberMe",
      // admin mock keys (dùng ở src/tu admin demo)
      "bm_admin_session",
      "bm_admin_role",
      "bm_admin_name"
    ];

    keys.forEach(k => {
      try { localStorage.removeItem(k); } catch (e) { /* ignore */ }
      try { sessionStorage.removeItem(k); } catch (e) { /* ignore */ }
    });
  }

  // Gắn hàm ra window để các phần khác của app gọi được
  window.logoutUser = function (redirectUrl = "/src/vu/auth/login/login.html") {
    clearAuthStorage();

    // nếu có service khác cần gọi để revoke token, có thể gọi ở đây (hiện backend dùng JWT stateless)
    // Ví dụ (không bật): await apiRequest('/api/auth/logout', { method: 'POST' });

    // Thông báo ngắn trước khi redirect (nếu có function showToast)
    try {
      if (typeof showToast === "function") showToast("Đã đăng xuất an toàn", "success");
    } catch (_) { /* ignore */ }

    // redirect (delay nhỏ để toast kịp hiển thị)
    setTimeout(() => {
      window.location.href = redirectUrl;
    }, 250);
  };
})();