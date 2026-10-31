# Email OTP flow trong Zalo Lite

Tài liệu này mô tả theo code hiện tại của project.

## Phạm vi

Project đang dùng email theo 2 luồng:

- Đăng ký tài khoản bằng email/password: dùng OTP 6 số qua email.
- Quên mật khẩu: gửi link reset qua email, không dùng OTP.

Luồng OTP nằm trong `backend/services/user-service`. Web và mobile chỉ gọi API, hiển thị màn nhập mã, lưu token sau khi verify thành công.

## Các file chính

- Backend service:
  - `backend/services/user-service/src/services/auth.service.ts`
  - `backend/services/user-service/src/services/email.service.ts`
  - `backend/services/user-service/src/routes/auth.routes.ts`
  - `backend/services/user-service/src/controllers/auth.controller.ts`
  - `backend/services/user-service/src/config/env.ts`
  - `backend/services/user-service/prisma/schema.prisma`
  - `backend/services/user-service/prisma/migrations/20260525090000_add_email_verification_sessions/migration.sql`
- Gateway:
  - `backend/api-gateway/src/index.ts`
- Frontend web:
  - `frontend/web/app/lib/auth.ts`
  - `frontend/web/app/register/page.tsx`
  - `frontend/web/app/register/verify/page.tsx`
- Frontend mobile:
  - `frontend/mobile/lib/auth.ts`
  - `frontend/mobile/app/register.tsx`
  - `frontend/mobile/app/register-verify.tsx`

## API qua gateway

Client gọi qua API Gateway:

- `POST /api/auth/register`
- `POST /api/auth/register/verify`
- `POST /api/auth/register/resend`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`

Gateway map `/api/auth/*` sang user-service `/auth/*`.

## Cấu hình email

Email được gửi bằng `nodemailer` trong `EmailService`.

Các biến môi trường liên quan:

- `SMTP_HOST`
- `SMTP_PORT`, mặc định `587`
- `SMTP_SECURE`, mặc định `false`
- `SMTP_USER`
- `SMTP_PASS`
- `SMTP_FROM`, mặc định `no-reply@zalolite.local`
- `PASSWORD_RESET_URL_BASE`

Nếu `SMTP_HOST` trống:

- Ở môi trường khác production, OTP được log ra console theo format `[DEV][OTP] email: code`.
- Ở production, service trả lỗi `email_service_not_configured`.

## Database

OTP đăng ký được lưu trong bảng `email_verification_sessions`.

Các cột quan trọng:

- `email`: email đang đăng ký.
- `full_name`, `phone`, `avatar_url`: thông tin user tạm thời.
- `password_hash`: mật khẩu đã hash, chỉ chuyển sang `local_credentials` sau khi OTP đúng.
- `otp_hash`: mã OTP đã hash SHA-256, không lưu OTP dạng plain text.
- `otp_expires_at`: thời điểm hết hạn OTP.
- `resend_available_at`: thời điểm được gửi lại mã.
- `attempt_count`: số lần nhập sai.
- `status`: `PENDING`, `VERIFIED`, `EXPIRED`, `CANCELLED`.
- `user_id`: được gán sau khi verify thành công.

## Luồng đăng ký bằng OTP

### 1. Client gọi đăng ký

Web/mobile gửi thông tin đăng ký lên:

```http
POST /api/auth/register
```

Payload chính:

```json
{
  "email": "user@example.com",
  "password": "Password123",
  "fullName": "User Name",
  "phone": "0911222333"
}
```

Backend validate:

- `email` đúng định dạng.
- `password` dài 8-72 ký tự.
- `fullName` dài 2-100 ký tự.
- `phone` nếu có thì dài 8-20 ký tự.

Sau đó `AuthService.initiateCredentialRegistration()`:

1. Normalize email về lowercase và trim.
2. Kiểm tra email/phone chưa bị dùng bởi local credential hiện có.
3. Hash password.
4. Sinh OTP 6 số bằng `randomInt(100000, 1000000)`.
5. Hash OTP bằng SHA-256.
6. Tạo record `email_verification_sessions` với status `PENDING`.
7. Gửi OTP qua email.

Các hằng số hiện tại:

- OTP hết hạn sau `15` phút.
- Được resend sau `60` giây.
- Nhập sai tối đa `3` lần.

Response thành công:

```json
{
  "message": "register_pending_verification",
  "data": {
    "verificationSessionId": "...",
    "email": "user@example.com",
    "expiresAt": "...",
    "resendAfterSeconds": 60,
    "maxAttempts": 3
  }
}
```

Client chuyển sang màn verify và truyền `session`, `email`, `expiresAt` qua route params.

### 2. Client nhập OTP

Client gửi:

```http
POST /api/auth/register/verify
```

Payload:

```json
{
  "verificationSessionId": "...",
  "code": "123456"
}
```

Backend kiểm tra:

1. `code` phải đúng format 6 chữ số.
2. Session phải tồn tại.
3. Session phải còn `PENDING`.
4. `attempt_count` chưa đạt 3.
5. OTP chưa hết hạn.
6. Hash của code nhập vào phải bằng `otp_hash`.

Nếu OTP sai:

- Tăng `attempt_count`.
- Nếu đủ 3 lần sai, đổi status thành `CANCELLED`.
- Trả lỗi `verification_code_invalid` hoặc `verification_failed_max_attempts`.

Nếu OTP đúng:

1. Kiểm tra lại email/phone vẫn còn khả dụng.
2. Tạo hoặc update user.
3. Tạo `local_credentials` từ `password_hash` đã lưu ở verification session.
4. Update verification session thành `VERIFIED` và gán `user_id`.
5. Trả về access token và user.

Response thành công:

```json
{
  "message": "register_success",
  "data": {
    "token": "...",
    "user": {
      "id": "...",
      "email": "user@example.com",
      "fullName": "User Name",
      "phone": "0911222333",
      "avatarUrl": null,
      "role": "USER",
      "plan": "FREE"
    }
  }
}
```

Web lưu token/user vào `localStorage`. Mobile lưu token/user vào `AsyncStorage`.

### 3. Gửi lại OTP

Client gọi:

```http
POST /api/auth/register/resend
```

Payload:

```json
{
  "verificationSessionId": "..."
}
```

Backend kiểm tra:

- Session tồn tại.
- Session còn `PENDING`.
- Chưa nhập sai quá 3 lần.
- OTP/session chưa hết hạn.
- Đã qua `resend_available_at`.
- Email/phone vẫn còn khả dụng.

Nếu hợp lệ:

1. Sinh OTP mới.
2. Update `otp_hash`.
3. Update `resend_available_at` thêm 60 giây.
4. Gửi email OTP mới.

Lưu ý: code hiện tại không kéo dài `otp_expires_at` khi resend. Nghĩa là OTP mới vẫn dùng hạn cuối của session ban đầu.

## Frontend behavior

Web và mobile có behavior gần giống nhau:

- Sau đăng ký thành công, chuyển đến màn verify.
- Màn verify đếm ngược theo `expiresAt`.
- Chặn resend trong 60 giây.
- Chỉ cho nhập số, tối đa 6 ký tự.
- Nếu nhập sai, client tự tăng attempt count để hiển thị số lần còn lại.
- Khi hết hạn hoặc bị lock, yêu cầu đăng ký lại.
- Khi verify thành công, lưu auth session và chuyển về trang chính.

## Error chính

- `email_already_registered`: email đã có local credential.
- `phone_already_used`: số điện thoại đã được dùng.
- `email_service_not_configured`: chưa cấu hình SMTP ở production.
- `verification_session_not_found`: không tìm thấy session.
- `verification_session_inactive`: session không còn `PENDING`.
- `verification_code_invalid`: OTP sai hoặc sai format.
- `verification_code_expired`: OTP hết hạn.
- `verification_failed_max_attempts`: sai quá 3 lần.
- `resend_too_soon`: resend trước thời điểm cho phép.

## Luồng quên mật khẩu

Quên mật khẩu không dùng OTP. Luồng hiện tại gửi link reset bằng JWT:

1. Client gọi `POST /api/auth/forgot-password` với email.
2. Backend kiểm tra user tồn tại, có local credential và còn active.
3. Backend tạo password reset token JWT, hạn `5` phút.
4. Backend tạo link từ `PASSWORD_RESET_URL_BASE?token=...`.
5. EmailService gửi link reset.
6. Client mở link đến `/reset-password`, nhập mật khẩu mới.
7. Client gọi `POST /api/auth/reset-password` với `token` và `newPassword`.
8. Backend verify JWT rồi update `local_credentials.password_hash`.

Nếu `SMTP_HOST` trống ở development, link reset được log ra console theo format `[DEV][RESET_LINK] email: link`.

## Ghi chú kỹ thuật

- OTP không được lưu plain text trong database.
- Chưa có job dọn các session `EXPIRED`, `CANCELLED` hoặc `VERIFIED`.
- Chưa thấy rate limit riêng theo email/IP cho OTP ngoài rate limit chung của service/gateway nếu có cấu hình.
- Resend không reset `attempt_count` ở backend. Client có reset attempt count sau resend, nhưng backend vẫn giữ số lần sai trước đó.
- Nếu muốn mỗi lần resend có đủ 15 phút mới, cần update thêm `otpExpiresAt` trong `resendCredentialVerificationCode()`.
