# BookMooch Frontend

## Chạy giao diện local

Yêu cầu Node.js đã được cài đặt. Từ thư mục gốc của dự án, chạy:

```powershell
npm run dev
```

Sau đó mở:

- Login: http://localhost:5500/vu/auth/login/login.html
- Register: http://localhost:5500/vu/auth/register/register.html
- Forgot password: http://localhost:5500/vu/auth/forgot-password/forgot-password.html
- Reset password: http://localhost:5500/vu/auth/reset-password/reset-password.html?token=TOKEN
- Profile: http://localhost:5500/vu/profile/profile.html
- Manage address: http://localhost:5500/vu/manage_address/manage_address.html
- Complaint: http://localhost:5500/vu/complain/complain.html
- Support: http://localhost:5500/vu/request_support/request_support.html

Server dừng bằng `Ctrl+C`.

## Luồng Auth mock

Đăng ký và phiên đăng nhập đang lưu trong `localStorage`. Chức năng quên mật khẩu tạo token mock có hạn 15 phút và in URL reset trong DevTools Console. Khi backend sẵn sàng, thay phần gọi mock trong `src/vu/auth/shared/auth-api.js` bằng API thật.

## Cấu trúc chính

```text
src/vu/auth/
├── shared/
├── login/
├── register/
├── forgot-password/
└── reset-password/
```