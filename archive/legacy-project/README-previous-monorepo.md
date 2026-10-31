# E-Bike Multiplatform

Hệ thống bán xe điện đa nền tảng theo mô hình monorepo, gồm backend Spring Boot và các client web, mobile, desktop dùng chung code TypeScript qua workspace.

## Tổng quan

Repository này tập trung vào các luồng cốt lõi của một hệ thống e-commerce cho xe điện:

- quản lý người dùng, đăng nhập và phân quyền theo PBAC
- quản lý danh mục sản phẩm, thông số kỹ thuật, hình ảnh và yêu thích
- đặt hàng, báo giá checkout, nhận xe tại showroom
- thanh toán VNPay
- chatbot tư vấn sản phẩm, FAQ và truy xuất tri thức từ tài liệu PDF
- giao diện web cho khách hàng, admin và manager

## Thành phần chính

- `backend`: Spring Boot API, Flyway migration, bảo mật, business logic
- `packages/web`: ứng dụng web React + Vite
- `packages/mobile`: ứng dụng mobile React Native + Expo
- `packages/desktop`: ứng dụng desktop React + Electron
- `packages/shared-code`: types, API client và phần dùng chung giữa các client

## Tính năng hiện có

### Backend

- xác thực bằng JWT, có các endpoint `register`, `login`, `logout`, `session`, `profile`
- phân quyền theo `PermissionConstants`, hỗ trợ guest/customer/manager/admin
- API sản phẩm và chi tiết sản phẩm
- quản lý danh sách yêu thích
- tạo đơn hàng, báo giá checkout và xem đơn
- hỗ trợ nhận xe tại showroom
- thanh toán VNPay với `create`, `return`, `ipn`
- upload/quản lý ảnh sản phẩm qua S3 metadata
- dashboard manager, xác nhận thanh toán pay-later, tra cứu khách hàng
- chatbot kết hợp FAQ, dữ liệu sản phẩm, showroom, quy trình thanh toán, Gemini và PDF knowledge base

### Web

- trang chủ, danh sách sản phẩm, chi tiết sản phẩm
- đăng nhập, đăng ký
- checkout, trang kết quả thanh toán
- chatbot
- khu vực khách hàng: dashboard, đơn hàng, hồ sơ
- khu vực admin
- khu vực manager: dashboard, orders, payments, customers, products

## Công nghệ sử dụng

### Backend

- Java 17
- Spring Boot 3.1.5
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway
- JWT
- AWS SDK S3
- Apache PDFBox

### Frontend và workspace

- Node.js workspaces
- React 18
- Vite 5
- TypeScript 5
- React Router
- Redux Toolkit
- React Native + Expo
- Electron

## Cấu trúc thư mục

```text
e-bike-multiplatform/
|-- backend/
|   |-- src/main/java/com/ebike/
|   |   |-- authModule/
|   |   |-- chatbotModule/
|   |   |-- managerModule/
|   |   |-- orderModule/
|   |   |-- productModule/
|   |   |-- userModule/
|   |   |-- shared/
|   |   `-- config/
|   `-- src/main/resources/
|       |-- application.properties
|       |-- db/migration/
|       |-- db/schema-overview.sql
|       |-- docs/
|       `-- faq-data/
|-- packages/
|   |-- shared-code/
|   |-- web/
|   |-- mobile/
|   `-- desktop/
|-- package.json
`-- README.md
```

## Yêu cầu môi trường

- Java 17
- Maven 3.9+
- Node.js 18+ và npm 9+
- Docker Desktop nếu muốn chạy PostgreSQL nhanh bằng container

## Chạy nhanh ở local

### 1. Cài dependencies cho workspace

```bash
npm install
```

### 2. Khởi động database

Cách nhanh nhất là dùng PostgreSQL trong `backend/docker-compose.yml`:

```bash
cd backend
docker compose up -d postgres
```

Mặc định profile `dev` của backend dùng:

- database: `ebike_db`
- username: `ebike_user`
- password: `ebike_password_123`
- port: `5432`

### 3. Chạy backend

```bash
cd backend
mvn spring-boot:run
```

Thông tin mặc định:

- port: `8080`
- context path: `/api/v1`
- profile mặc định: `dev`

### 4. Chạy web

Từ thư mục root:

```bash
npm run dev:web
```

Web Vite thường chạy ở `http://localhost:5173`.

### 5. Chạy mobile

```bash
npm run dev:mobile
```

### 6. Chạy desktop

```bash
npm run dev:desktop
```

## Scripts hữu ích ở root

- `npm run dev`: chạy song song web, mobile, desktop
- `npm run dev:backend`: chạy Spring Boot backend
- `npm run build`: build toàn bộ workspace có script build
- `npm run build:backend`: đóng gói backend bằng Maven
- `npm run test`: chạy test ở các workspace có khai báo
- `npm run lint`: chạy lint ở các workspace có khai báo

## Cấu hình backend quan trọng

Các file cấu hình chính:

- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-dev.properties`
- `backend/src/main/resources/application-prod.properties`

Một số cấu hình đáng chú ý:

- `server.servlet.context-path=/api/v1`
- `jwt.secret`, `jwt.expiration`
- `app.cors.allowed-origin-patterns`
- `app.storage.s3.*`
- `vnpay.*`
- `app.ai.gemini.*`
- `app.ai.pdf-knowledge.*`

PDF knowledge base của chatbot đang đọc tài liệu từ:

- `backend/src/main/resources/docs/*.pdf`

FAQ tĩnh đang nằm tại:

- `backend/src/main/resources/faq-data/faq.csv`

## Database và Flyway

Flyway chạy migration từ:

- `backend/src/main/resources/db/migration`

Hiện tại migration đã được squash thành baseline:

- `backend/src/main/resources/db/migration/V2_0_0__baseline.sql`

Các migration lịch sử `V1_*` được chuyển sang:

- `backend/src/main/resources/db/migration-archive/`

Schema tổng quan để đọc nhanh:

- `backend/src/main/resources/db/schema-overview.sql`

Ghi chú thêm về migration hiện tại:

- Flyway hiện chỉ đọc migration active trong `backend/src/main/resources/db/migration/`
- `backend/src/main/resources/db/migration-archive/` chỉ dùng để lưu lịch sử `V1_*`, không còn được chạy trực tiếp
- từ baseline `V2_0_0`, thay đổi schema mới nên được thêm bằng migration mới theo nhánh `V2_*`

Baseline `V2_0_0` đã gộp toàn bộ các thay đổi chính trước đây:

- schema auth, product, order, user
- showroom pickup flow
- metadata ảnh S3
- user favorites
- VNPay payment fields
- PBAC permissions
- merge `STAFF` vào `MANAGER`
- cấp quyền favorites cho manager

Lưu ý khi chuyển sang baseline:

- nếu database local cũ đang dùng lịch sử `V1_*`, nên reset DB dev một lần để sạch hoàn toàn
- seed sản phẩm vẫn nằm ngoài Flyway, dùng file trong `backend/data-backup/`

## Import dữ liệu sản phẩm mẫu

Nếu bạn đang dùng container PostgreSQL tên `ebike-postgres`, có thể import thêm dữ liệu SQL từ file:

- `backend/data-backup/dataCawl/output/products_db_ready_clean.sql`: là SQL thật, nhưng `image_url` dùng đường dẫn ảnh local
- `backend/data-backup/dataCawl/output/products_db_ready_clean.s3.sql`: là SQL thật và là file phù hợp nhất với DB/app hiện tại vì dùng URL ảnh S3

Ví dụ:

```bash
docker cp backend/data-backup/dataCawl/output/products_db_ready_clean.s3.sql ebike-postgres:/tmp/products_db_ready_clean.s3.sql
docker exec -i ebike-postgres psql -U ebike_user -d ebike_db -v ON_ERROR_STOP=1 -f /tmp/products_db_ready_clean.s3.sql
```

## API và quyền truy cập

Một vài route đáng chú ý:

- `GET /api/v1/products`
- `POST /api/v1/orders`
- `POST /api/v1/orders/quote`
- `POST /api/v1/payments/vnpay/create`
- `GET /api/v1/payments/vnpay/return`
- `POST /api/v1/chatbot/ask`
- `GET /api/v1/manager/dashboard`
- `GET /api/v1/manager/payments`

Security hiện cấu hình theo hướng:

- guest vẫn có thể xem sản phẩm, tìm kiếm, dùng chatbot và tạo đơn trong một số luồng
- customer có thể quản lý thông tin cá nhân, đơn hàng và yêu thích
- manager có thể xem dashboard, khách hàng, payments và nghiệp vụ vận hành
- admin có quyền quản trị sâu hơn theo permission

## Gợi ý đọc code khi mới vào dự án

Nếu cần hiểu nhanh codebase, nên đọc theo thứ tự:

1. `backend/pom.xml`
2. `backend/src/main/resources/application.properties`
3. `backend/src/main/resources/application-dev.properties`
4. `backend/src/main/resources/db/schema-overview.sql`
5. `backend/src/main/resources/db/migration/`
6. `backend/src/main/java/com/ebike/config/SecurityConfiguration.java`
7. `backend/src/main/java/com/ebike/shared/constants/PermissionConstants.java`
8. `packages/shared-code/src/api/endpoints.ts`
9. `packages/web/src/router/index.tsx`

## Ghi chú

- Repo đang phát triển tích cực, có thể có thay đổi chưa phản ánh hết ở mọi màn hình hoặc tài liệu phụ.
- Nếu thêm tính năng mới liên quan dữ liệu, ưu tiên cập nhật theo thứ tự: migration, entity, service, controller, shared types, frontend.
- Không nên sửa migration cũ đã dùng chung trong team nếu không thật sự cần thiết; hãy thêm migration mới.
