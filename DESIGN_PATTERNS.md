# Design Patterns đã áp dụng trong dự án Kinetic E-Bike

Tài liệu này tổng hợp các mẫu thiết kế có thể trình bày khi bảo vệ dự án. Mỗi phần gồm: pattern, nơi áp dụng trong code, ý nghĩa và cách trả lời ngắn khi được hỏi.

## 1. Layered Architecture / MVC

### Áp dụng trong dự án

Dự án backend được chia thành các lớp rõ ràng:

- `Controller`: nhận request và trả response REST API.
- `Service`: xử lý nghiệp vụ.
- `Repository`: truy cập database.
- `Entity`: ánh xạ bảng database.
- `DTO`: định nghĩa dữ liệu request/response.

Ví dụ:

- `ProductController`
- `ProductService`
- `ProductRepository`
- `ProductSummaryDto`, `ProductDetailDto`

### Ý nghĩa

Pattern này giúp tách trách nhiệm giữa các lớp. Controller không chứa logic nghiệp vụ phức tạp, Service tập trung xử lý business logic, Repository phụ trách database.

### Cách trả lời

> Dự án áp dụng Layered Architecture theo kiểu Controller-Service-Repository. Controller nhận request, Service xử lý nghiệp vụ, Repository làm việc với database, còn DTO dùng để truyền dữ liệu giữa backend và frontend. Cách tổ chức này giúp code dễ bảo trì, dễ test và dễ mở rộng.

## 2. Repository Pattern

### Áp dụng trong dự án

Các repository kế thừa Spring Data JPA:

- `ProductRepository`
- `OrderRepository`
- `UserRepository`
- `NotificationRepository`
- `PaymentRepository`
- `ReviewRepository`

Ví dụ:

```java
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
}
```

### Ý nghĩa

Repository Pattern đóng gói thao tác truy cập dữ liệu. Service không cần viết SQL trực tiếp mà gọi qua repository.

### Cách trả lời

> Dự án dùng Repository Pattern thông qua Spring Data JPA. Các class repository chịu trách nhiệm truy vấn database, giúp service không phụ thuộc trực tiếp vào chi tiết lưu trữ dữ liệu.

## 3. Service Layer Pattern

### Áp dụng trong dự án

Nghiệp vụ được đặt trong service thay vì controller.

Ví dụ:

- `OrderService` / `OrderServiceImpl`
- `AuthenticationService` / `AuthenticationServiceImpl`
- `VnPayService` / `VnPayServiceImpl`
- `MediaProxyService` / `MediaProxyServiceImpl`
- `NotificationService` / `NotificationServiceImpl`

### Ý nghĩa

Service Layer giúp gom logic nghiệp vụ vào một tầng riêng. Controller chỉ điều phối request/response.

### Cách trả lời

> Service Layer được dùng để tách business logic khỏi controller. Ví dụ xử lý đặt hàng nằm trong `OrderServiceImpl`, xử lý thanh toán VNPay nằm trong `VnPayServiceImpl`. Nhờ vậy controller gọn hơn và dễ thay đổi logic nghiệp vụ.

## 4. DTO Pattern

### Áp dụng trong dự án

Dự án dùng DTO cho dữ liệu trả về và dữ liệu nhận từ client.

Ví dụ:

- `ProductSummaryDto`
- `ProductDetailDto`
- `ProductReviewDto`
- `ChatbotResponse`
- `VnPayCreatePaymentResponse`
- `OrderCreateRequest`

### Ý nghĩa

DTO giúp không trả trực tiếp Entity ra frontend, tránh lộ cấu trúc database và giúp response phù hợp với từng màn hình.

### Cách trả lời

> Dự án dùng DTO Pattern để tách dữ liệu API khỏi Entity. Entity đại diện cho bảng database, còn DTO đại diện cho dữ liệu request/response. Điều này giúp bảo vệ cấu trúc dữ liệu nội bộ và kiểm soát response tốt hơn.

## 5. Dependency Injection / Inversion of Control

### Áp dụng trong dự án

Spring quản lý dependency thông qua các annotation như:

- `@Service`
- `@Repository`
- `@RestController`
- `@Component`
- `@Configuration`

Các dependency thường được inject qua constructor.

Ví dụ:

```java
public OrderServiceImpl(OrderRepository orderRepository, PaymentRepository paymentRepository) {
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
}
```

### Ý nghĩa

Class không tự tạo dependency bằng `new`, mà nhận dependency từ Spring Container. Điều này giảm coupling và dễ test hơn.

### Cách trả lời

> Dự án áp dụng Dependency Injection của Spring. Các service, repository, filter và config được Spring tạo và inject tự động. Nhờ vậy các class phụ thuộc vào abstraction hoặc bean được quản lý, thay vì tự khởi tạo dependency.

## 6. Singleton Pattern

### Áp dụng trong dự án

Các Spring Bean mặc định có scope là singleton, nghĩa là mỗi bean chỉ có một instance trong Application Context.

Ví dụ:

- `ProductService`
- `OrderServiceImpl`
- `GeminiChatClient`
- `JwtAuthenticationFilter`
- `ServerRateLimitFilter`
- `RedisCacheConfiguration`

### Ý nghĩa

Các service stateless hoặc config object được dùng chung trong toàn application, tiết kiệm tài nguyên và thống nhất trạng thái cấu hình.

### Cách trả lời

> Dự án có áp dụng Singleton thông qua Spring Bean Singleton. Các class như service, filter, configuration được Spring quản lý mặc định ở singleton scope. Tuy nhiên dự án không tự viết singleton thủ công bằng `private static instance`, mà dùng cơ chế chuẩn của Spring.

## 7. Factory Pattern

### Áp dụng trong dự án

Dự án chưa có custom factory class riêng, nhưng có sử dụng Factory Pattern thông qua Spring Bean Factory và các class `@Configuration`.

Ví dụ:

- `RedisCacheConfiguration` tạo cache configuration, key generator, cache error handler.
- `SecurityConfiguration` tạo security filter chain.
- `StorageConfiguration` tạo cấu hình storage.
- `RestTemplateBuilder` trong `GeminiChatClient` tạo `RestTemplate`.

### Ý nghĩa

Việc tạo object được gom vào configuration hoặc framework container, thay vì rải rác trong business logic.

### Cách trả lời

> Dự án có sử dụng Factory Pattern ở mức framework. Spring IoC Container đóng vai trò Bean Factory để tạo và quản lý object. Ngoài ra các class `@Configuration` có các method `@Bean` để tạo object cấu hình. Hiện tại dự án chưa cần custom factory riêng vì Spring đã xử lý phần khởi tạo bean.

## 8. Strategy Pattern

### Áp dụng trong dự án

Dự án dùng interface và implementation cho nhiều nghiệp vụ:

- `AuthenticationService` / `AuthenticationServiceImpl`
- `OrderService` / `OrderServiceImpl`
- `VnPayService` / `VnPayServiceImpl`
- `MediaProxyService` / `MediaProxyServiceImpl`
- `NotificationService` / `NotificationServiceImpl`

Ngoài ra, phần lọc sản phẩm bằng Specification cũng có tính chất strategy vì mỗi điều kiện lọc là một chiến lược truy vấn có thể ghép lại.

### Ý nghĩa

Controller hoặc module khác phụ thuộc vào interface thay vì implementation cụ thể. Sau này có thể thay đổi hoặc thêm implementation mới.

### Cách trả lời

> Dự án áp dụng Strategy Pattern ở mức interface-service implementation. Ví dụ controller gọi `VnPayService`, còn chi tiết xử lý nằm trong `VnPayServiceImpl`. Nếu sau này thêm MoMo hoặc ZaloPay, có thể tạo implementation khác mà không cần thay đổi nhiều ở controller.

## 9. Specification Pattern

### Áp dụng trong dự án

`ProductRepository` dùng `JpaSpecificationExecutor<Product>`. `ProductService` tạo các điều kiện lọc động theo:

- keyword/query
- category
- minPrice/maxPrice
- brand
- vehicleType
- batteryType
- range
- stock

### Ý nghĩa

Specification Pattern giúp ghép nhiều điều kiện query một cách linh hoạt, tránh phải viết rất nhiều method repository cho từng tổ hợp filter.

### Cách trả lời

> Với chức năng lọc sản phẩm, dự án dùng Specification Pattern. Mỗi điều kiện lọc được biểu diễn thành một specification, sau đó ghép lại để tạo query động. Cách này phù hợp vì màn hình sản phẩm có nhiều bộ lọc kết hợp.

## 10. Filter Chain / Chain of Responsibility

### Áp dụng trong dự án

Các request đi qua chuỗi filter trước khi vào controller.

Ví dụ:

- `JwtAuthenticationFilter`
- `ServerRateLimitFilter`

### Ý nghĩa

Mỗi filter xử lý một trách nhiệm riêng. Filter JWT xác thực token, filter rate limit giới hạn số request.

### Cách trả lời

> Dự án dùng Filter Chain theo cơ chế của Spring Security và Servlet Filter. Request đi qua nhiều filter, mỗi filter xử lý một phần như xác thực JWT hoặc rate limiting. Đây là dạng Chain of Responsibility vì mỗi bước có thể xử lý hoặc chặn request.

## 11. Adapter / Facade Pattern

### Áp dụng trong dự án

Một số service đóng vai trò bọc hệ thống hoặc thư viện bên ngoài:

- `GeminiChatClient`: bọc API Gemini.
- `VnPayServiceImpl`: bọc logic tạo URL và xác thực callback VNPay.
- `MediaProxyServiceImpl`: bọc việc gọi remote image.
- `ProductImageStorageService`: bọc việc lưu ảnh/storage.

### Ý nghĩa

Các module còn lại không cần biết chi tiết API bên ngoài hoạt động ra sao. Chúng chỉ gọi service nội bộ.

### Cách trả lời

> Dự án dùng Adapter/Facade cho các tích hợp bên ngoài như Gemini, VNPay và storage. Ví dụ `GeminiChatClient` che giấu chi tiết gọi API Gemini, còn chatbot service chỉ cần gọi `generateAnswer`. Điều này giảm coupling với external service.

## 12. Observer / Event-driven Pattern

### Áp dụng trong dự án

Module notification có xử lý theo hướng event-driven.

Ví dụ:

- `NotificationEventBridge`
- `NotificationCreationService`
- `NotificationStreamService`

### Ý nghĩa

Khi có sự kiện nghiệp vụ như thay đổi trạng thái đơn hàng hoặc thanh toán, hệ thống có thể phát event để notification module xử lý.

### Cách trả lời

> Dự án có áp dụng hướng Observer/Event-driven cho notification. Các module nghiệp vụ không cần phụ thuộc trực tiếp quá nhiều vào module notification, mà có thể phát event để notification xử lý sau.

## 13. Cache-aside Pattern

### Áp dụng trong dự án

ProductService dùng Redis cache cho:

- danh sách sản phẩm theo query/category/minPrice/maxPrice
- chi tiết sản phẩm theo slug

Cache được evict khi có thao tác thay đổi sản phẩm, ảnh sản phẩm hoặc review liên quan.

### Ý nghĩa

Khi đọc dữ liệu, hệ thống kiểm tra cache trước. Nếu cache miss thì lấy từ database và lưu lại vào Redis. Khi dữ liệu thay đổi thì xóa cache để tránh trả dữ liệu cũ.

### Cách trả lời

> Dự án áp dụng Cache-aside Pattern với Redis cho ProductService. Khi đọc danh sách hoặc chi tiết sản phẩm, hệ thống lấy từ Redis nếu có. Nếu chưa có thì truy vấn database rồi cache lại. Khi sản phẩm, ảnh hoặc review thay đổi, cache sẽ bị evict để đảm bảo dữ liệu đúng.

## 14. Builder Pattern

### Áp dụng trong dự án

Dự án có sử dụng builder-style API từ thư viện:

- `HttpClient.newBuilder()` trong `MediaProxyServiceImpl`
- `HttpRequest.newBuilder()` trong `MediaProxyServiceImpl`
- `RestTemplateBuilder` trong `GeminiChatClient`
- `Jwts.parserBuilder()` trong JWT processing

### Ý nghĩa

Builder giúp tạo object có nhiều cấu hình một cách rõ ràng, dễ đọc hơn constructor dài.

### Cách trả lời

> Dự án có sử dụng Builder Pattern thông qua các builder API của Java và Spring, ví dụ `HttpClient.newBuilder`, `HttpRequest.newBuilder`, `RestTemplateBuilder`. Các builder này giúp cấu hình object như timeout, header, redirect một cách rõ ràng.

## Câu trả lời tổng hợp ngắn

Nếu cần trả lời nhanh trong buổi bảo vệ:

> Dự án áp dụng nhiều design pattern. Về kiến trúc, backend dùng Layered Architecture với Controller-Service-Repository. Về dữ liệu, dự án dùng Repository Pattern qua Spring Data JPA và DTO Pattern cho request/response. Về quản lý object, dự án dùng Dependency Injection, Singleton bean và Bean Factory của Spring. Với nghiệp vụ mở rộng, dự án dùng Strategy thông qua service interface và implementation. Với lọc sản phẩm, dự án dùng Specification Pattern. Với security và rate limit, dự án dùng Filter Chain/Chain of Responsibility. Với tích hợp Gemini, VNPay và storage, dự án dùng Adapter/Facade. Với notification có hướng Event-driven/Observer. Với Redis product cache, dự án dùng Cache-aside Pattern.

## Lưu ý khi bị hỏi sâu

- Không nói dự án tự viết Singleton thủ công. Hãy nói dùng Spring singleton bean.
- Không nói dự án có custom Factory riêng nếu bị hỏi cụ thể. Hãy nói factory chủ yếu đến từ Spring Bean Factory và `@Configuration`.
- Strategy hiện tại thể hiện qua interface/implementation. Có thể mở rộng rõ hơn nếu thêm nhiều payment provider như VNPay, MoMo, ZaloPay.
- Builder chủ yếu là builder từ thư viện/framework, không phải builder tự viết cho domain object.
- Nên đưa ví dụ code cụ thể khi trả lời, ví dụ `ProductRepository`, `OrderServiceImpl`, `JwtAuthenticationFilter`, `VnPayServiceImpl`, `GeminiChatClient`.
