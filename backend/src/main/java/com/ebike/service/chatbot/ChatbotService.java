package com.ebike.service.chatbot;

import com.ebike.dto.chatbot.request.ChatbotAskRequest;
import com.ebike.dto.chatbot.response.ChatbotDebugResponse;
import com.ebike.dto.chatbot.response.ChatbotRecommendationDto;
import com.ebike.dto.chatbot.response.ChatbotResponse;
import com.ebike.dto.savings.SavingsResponse;
import com.ebike.model.entity.order.Showroom;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.PricingService;
import com.ebike.service.savings.SavingsService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ChatbotService {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|\\s+-\\s+");

    private final ProductRepository productRepository;
    private final ShowroomRepository showroomRepository;
    private final GeminiChatClient geminiChatClient;
    private final PdfKnowledgeBaseService pdfKnowledgeBaseService;
    private final PricingService pricingService;
    private final SavingsService savingsService;
    // FAQ entries (Vietnamese only); see faq-data/faq.csv
    private List<FaqEntry> faqEntries = List.of();
    /** The chat id comes from the browser, so cap how many conversations are remembered and how long an id may be. */
    private static final int MAX_REMEMBERED_CHATS = 2000;
    private static final int MAX_CHAT_ID_LENGTH = 64;
    private final Map<String, List<ChatbotRecommendationDto>> recentRecommendationsByChatId = new ConcurrentHashMap<>();

    /** For tests that build the service by hand: it makes its own calculator. */
    public ChatbotService(
        ProductRepository productRepository,
        ShowroomRepository showroomRepository,
        GeminiChatClient geminiChatClient,
        PdfKnowledgeBaseService pdfKnowledgeBaseService,
        PricingService pricingService
    ) {
        this(productRepository, showroomRepository, geminiChatClient, pdfKnowledgeBaseService, pricingService, new SavingsService(productRepository, pricingService));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChatbotService(
        ProductRepository productRepository,
        ShowroomRepository showroomRepository,
        GeminiChatClient geminiChatClient,
        PdfKnowledgeBaseService pdfKnowledgeBaseService,
        PricingService pricingService,
        SavingsService savingsService
    ) {
        this.productRepository = productRepository;
        this.showroomRepository = showroomRepository;
        this.geminiChatClient = geminiChatClient;
        this.pdfKnowledgeBaseService = pdfKnowledgeBaseService;
        this.pricingService = pricingService;
        this.savingsService = savingsService; // the Spring bean, so its proxy and transactions apply
        loadFaqEntries();
    }

    public ChatbotResponse ask(ChatbotAskRequest request) {
        ChatbotAnalysis analysis = analyze(request);
        String aiAnswer = sanitizeAiAnswer(geminiChatClient.generateAnswer(buildAiPrompt(analysis)));
        if (isUsableAiAnswer(aiAnswer)) {
            return new ChatbotResponse(aiAnswer, "ai_rag_light", analysis.recommendations());
        }

        if (shouldPreferRuleBasedFallback(analysis.ruleBasedResponse())) {
            return analysis.ruleBasedResponse();
        }

        if (analysis.pdfKnowledgeContext().hasSnippets()) {
            return buildPdfFallbackResponse(analysis);
        }

        return analysis.ruleBasedResponse();
    }

    public ChatbotDebugResponse debug(ChatbotAskRequest request) {
        ChatbotAnalysis analysis = analyze(request);
        return new ChatbotDebugResponse(
            analysis.userMessage(),
            analysis.normalizedMessage(),
            analysis.faqMatch() == null ? null : analysis.faqMatch().question(),
            analysis.faqMatch() == null ? null : analysis.faqMatch().category(),
            analysis.ruleBasedResponse().matchedIntent(),
            geminiChatClient.isConfigured(),
            analysis.showroomContext(),
            analysis.orderPaymentContext(),
            analysis.pdfKnowledgeContext().combinedContext(),
            analysis.pdfKnowledgeContext().sourceLabels(),
            analysis.ruleBasedResponse().answer(),
            analysis.recommendations()
        );
    }

    private ChatbotAnalysis analyze(ChatbotAskRequest request) {
        String userMessage = request == null ? null : request.effectiveMessage();
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        String chatId = normalizeChatId(request.chatId());
        String normalizedMessage = userMessage.trim().toLowerCase(Locale.ROOT);
        FaqEntry faqMatch = faqFor().stream()
            .filter(entry -> entry.matches(normalizedMessage))
            // An exact question wins; then priority; then the entry sharing more keywords with the message.
            .max(Comparator.comparing((FaqEntry entry) -> entry.isAskedExactly(normalizedMessage))
                .thenComparingInt(FaqEntry::priorityScore)
                .thenComparingInt(entry -> entry.keywordHits(normalizedMessage)))
            .map(this::withCurrentFees)
            .orElse(null);
        List<ChatbotRecommendationDto> recommendations = findProductRecommendations(normalizedMessage);
        // A follow-up that points at the bikes just suggested ("xe đầu tiên giá bao nhiêu?") is about those bikes, even when a loose word of it also scores some other bike
        List<ChatbotRecommendationDto> remembered = recentRecommendationsByChatId.getOrDefault(chatId, List.of());
        if (isFollowUpPriceQuestion(normalizedMessage) && !remembered.isEmpty()
            && (recommendations.isEmpty() || findMentionedProduct(normalizedMessage) == null)) {
            recommendations = remembered;
        }
        Product mentionedProduct = findMentionedProduct(normalizedMessage);
        String showroomContext = buildShowroomContext(normalizedMessage);
        String orderPaymentContext = buildOrderPaymentContext(normalizedMessage);
        // A question the FAQ has word for word is answered by the FAQ
        String savingsContext = faqMatch != null && faqMatch.isAskedExactly(normalizedMessage) ? "" : buildSavingsContext(normalizedMessage, mentionedProduct);
        PdfKnowledgeBaseService.PdfKnowledgeContext pdfKnowledgeContext = pdfKnowledgeBaseService.findRelevantContext(userMessage);
        ChatbotResponse ruleBasedResponse = buildRuleBasedResponse(
            faqMatch,
            recommendations,
            mentionedProduct,
            showroomContext,
            orderPaymentContext,
            savingsContext,
            isFollowUpPriceQuestion(normalizedMessage)
        );

        if (!recommendations.isEmpty()) {
            if (recentRecommendationsByChatId.size() >= MAX_REMEMBERED_CHATS) {
                recentRecommendationsByChatId.clear();
            }
            recentRecommendationsByChatId.put(chatId, recommendations);
        }

        return new ChatbotAnalysis(
            chatId,
            userMessage.trim(),
            normalizedMessage,
            faqMatch,
            recommendations,
            showroomContext,
            orderPaymentContext,
            savingsContext,
            pdfKnowledgeContext,
            ruleBasedResponse
        );
    }

    private ChatbotResponse buildRuleBasedResponse(
        FaqEntry faqMatch,
        List<ChatbotRecommendationDto> recommendations,
        Product mentionedProduct,
        String showroomContext,
        String orderPaymentContext,
        String savingsContext,
        boolean priceQuestion
    ) {
        if (!savingsContext.isBlank()) {
            // V33: a question about the cost of riding is answered with the prices in force and the way to the calculator on the bike's page
            // (a battery pack or an accessory has no such tool, so it gets a few bikes instead)
            List<ChatbotRecommendationDto> cards = SavingsService.isRidden(mentionedProduct)
                ? List.of(toRecommendation(mentionedProduct, savingsCardReason()))
                : suggestedBikesForSavings();
            return new ChatbotResponse(savingsContext, "running_cost", cards);
        }

        if (mentionedProduct != null) {
            return new ChatbotResponse(
                buildProductDetailAnswer(mentionedProduct),
                "product_detail",
                recommendations.isEmpty() ? List.of(toRecommendation(mentionedProduct, "khớp với mẫu xe bạn hỏi")) : recommendations
            );
        }

        if (priceQuestion && !recommendations.isEmpty()) {
            // "Xe đầu tiên giá bao nhiêu?" asks about the bikes just shown: say their prices, not a general FAQ that happens to mention prices
            return new ChatbotResponse(buildRecommendationAnswer(recommendations), "product_recommendation", recommendations);
        }

        if (faqMatch != null && !recommendations.isEmpty()) {
            return new ChatbotResponse(
                faqMatch.answer() + " Dựa trên câu hỏi của bạn, đây là vài mẫu xe phù hợp để tham khảo.",
                "faq+product_recommendation",
                recommendations
            );
        }

        if (faqMatch != null) {
            return new ChatbotResponse(faqMatch.answer(), "faq", List.of());
        }

        if (!showroomContext.isBlank()) {
            return new ChatbotResponse(
                "Đây là thông tin showroom hiện có:\n" + showroomContext,
                "showroom_context",
                List.of()
            );
        }

        if (!orderPaymentContext.isBlank()) {
            return new ChatbotResponse(orderPaymentContext, "order_payment_context", List.of());
        }

        if (!recommendations.isEmpty()) {
            return new ChatbotResponse(
                buildRecommendationAnswer(recommendations),
                "product_recommendation",
                recommendations
            );
        }

        if (priceQuestion) {
            return new ChatbotResponse(
                "Mình chưa xác định được bạn muốn xem giá của mẫu xe nào. Bạn có thể nhập tên mẫu xe hoặc hỏi lại sau khi mình gợi ý một vài mẫu phù hợp.",
                "price_followup_missing_context",
                List.of()
            );
        }

        return new ChatbotResponse(
            "Mình có thể tư vấn mẫu xe điện phù hợp, tầm giá, pin, quãng đường, tốc độ, bảo hành, showroom nhận xe và thanh toán. Bạn có thể nói rõ nhu cầu như: đi học, đi làm hằng ngày, muốn xe giá tốt, đi xa hoặc ưu tiên tốc độ.",
            "fallback",
            List.of()
        );
    }

    private boolean shouldPreferRuleBasedFallback(ChatbotResponse response) {
        return response != null && Set.of(
            "product_detail",
            "product_recommendation",
            "faq+product_recommendation",
            "showroom_context",
            "order_payment_context",
            "running_cost",
            "price_followup_missing_context"
        ).contains(response.matchedIntent());
    }

    private String buildRecommendationAnswer(List<ChatbotRecommendationDto> recommendations) {
        StringBuilder answer = new StringBuilder("Hiện mình tìm thấy một vài mẫu xe phù hợp với câu hỏi của bạn:");
        for (ChatbotRecommendationDto recommendation : recommendations) {
            answer.append("\n- ")
                .append(recommendation.name())
                .append(" giá khoảng ")
                .append(formatCurrency(recommendation.price()))
                .append(": ")
                .append(recommendation.reason());
        }
        answer.append("\nBạn muốn mình lọc tiếp theo ngân sách, quãng đường đi mỗi ngày hay kiểu dáng xe không?");
        return answer.toString();
    }

    private List<ChatbotRecommendationDto> findProductRecommendations(String message) {
        if (isCatalogQuestion(message)) {
            return productRepository.findAll().stream()
                .filter(product -> Boolean.TRUE.equals(product.getActive()))
                .sorted(Comparator
                    .comparing((Product product) -> product.getFeatured() != null && product.getFeatured()).reversed()
                    .thenComparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(product -> new ChatbotRecommendationDto(
                    product.getId(),
                    product.getName(),
                    product.getSlug(),
                    product.getPrice(),
                    describeProduct(product)
                ))
                .toList();
        }

        return productRepository.findAll().stream()
            .filter(product -> Boolean.TRUE.equals(product.getActive()))
            .map(product -> scoreProduct(product, message))
            .filter(scoredProduct -> scoredProduct.score() > 0)
            .sorted(Comparator.comparingInt(ScoredProduct::score).reversed())
            .limit(3)
            .map(scoredProduct -> new ChatbotRecommendationDto(
                scoredProduct.product().getId(),
                scoredProduct.product().getName(),
                scoredProduct.product().getSlug(),
                scoredProduct.product().getPrice(),
                scoredProduct.reason()
            ))
            .toList();
    }

    private Product findMentionedProduct(String message) {
        String normalizedMessage = normalizeFallbackText(message);
        return productRepository.findAll().stream()
            .filter(product -> Boolean.TRUE.equals(product.getActive()))
            .filter(product -> {
                String productName = normalizeFallbackText(product.getName());
                String productSlug = normalizeFallbackText(product.getSlug()).replace("-", " ");
                return (!productName.isBlank() && normalizedMessage.contains(productName))
                    || (!productSlug.isBlank() && normalizedMessage.contains(productSlug));
            })
            .max(Comparator.comparingInt(product -> normalizeFallbackText(product.getName()).length()))
            .orElse(null);
    }

    private ChatbotRecommendationDto toRecommendation(Product product, String reason) {
        return new ChatbotRecommendationDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getPrice(),
            reason
        );
    }

    private String buildProductDetailAnswer(Product product) {
        StringBuilder answer = new StringBuilder();
        answer.append(product.getName())
            .append(" hiện có giá khoảng ")
            .append(formatCurrency(product.getPrice()))
            .append(".");

        if (hasVietnameseMarks(product.getDescription())) {
            answer.append("\n- Mô tả: ").append(product.getDescription().trim());
        }

        ProductSpecification specification = product.getSpecification();
        if (specification != null) {
            List<String> specs = new ArrayList<>();
            if (specification.getMaxRangeKm() != null) {
                specs.add("quãng đường tối đa khoảng " + specification.getMaxRangeKm().stripTrailingZeros().toPlainString() + " km");
            }
            if (specification.getMaxSpeedKmh() != null) {
                specs.add("tốc độ tối đa khoảng " + specification.getMaxSpeedKmh().stripTrailingZeros().toPlainString() + " km/h");
            }
            if (specification.getMotorPowerWatts() != null) {
                specs.add("động cơ " + specification.getMotorPowerWatts() + "W");
            }
            if (specification.getBatteryCapacityAh() != null && specification.getBatteryVoltageV() != null) {
                specs.add("pin " + specification.getBatteryVoltageV().stripTrailingZeros().toPlainString() + "V "
                    + specification.getBatteryCapacityAh().stripTrailingZeros().toPlainString() + "Ah");
            }
            if (specification.getChargingTimeHours() != null) {
                specs.add("sạc khoảng " + specification.getChargingTimeHours().stripTrailingZeros().toPlainString() + " giờ");
            }
            if (specification.getWarrantyMonths() != null) {
                specs.add("bảo hành khoảng " + specification.getWarrantyMonths() + " tháng");
            }
            if (!specs.isEmpty()) {
                answer.append("\n- Thông số chính: ").append(String.join(", ", specs)).append(".");
            }
        }

        answer.append("\nBạn muốn mình so sánh mẫu này với xe giá tương đương hay xem thêm màu/biến thể không?");
        return answer.toString();
    }

    private boolean hasVietnameseMarks(String text) {
        return text != null && text.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ].*");
    }

    private ScoredProduct scoreProduct(Product product, String message) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        ProductSpecification specification = product.getSpecification();
        String normalizedSearch = normalizeFallbackText(message);

        if (containsAny(normalizedSearch, "di lam", "hang ngay", "di hang ngay", "di chuyen", "commute", "daily", "do thi")
            && specification != null) {
            if (specification.getMaxRangeKm() != null && specification.getMaxRangeKm().compareTo(BigDecimal.valueOf(50)) >= 0) {
                score += 3;
                reasons.add("phù hợp đi làm hằng ngày");
            }
            if (specification.getMaxSpeedKmh() != null && specification.getMaxSpeedKmh().compareTo(BigDecimal.valueOf(35)) >= 0) {
                score += 1;
                reasons.add("tốc độ đủ dùng trong đô thị");
            }
            if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.valueOf(30_000_000L)) <= 0) {
                score += 1;
                reasons.add("chi phí hợp lý cho nhu cầu sử dụng thường xuyên");
            }
        }

        if (containsAny(message, "cheap", "budget", "student", "gia re", "giá rẻ", "duoi 20", "dưới 20", "under 20", "sinh viên")) {
            BigDecimal threshold = BigDecimal.valueOf(20_000_000L);
            if (product.getPrice() != null && product.getPrice().compareTo(threshold) <= 0) {
                score += 3;
                reasons.add("phù hợp nhu cầu giá tốt");
            }
        }

        if (containsAny(message, "fast", "speed", "toc do", "tốc độ", "nhanh") && specification != null && specification.getMaxSpeedKmh() != null) {
            if (specification.getMaxSpeedKmh().compareTo(BigDecimal.valueOf(50)) >= 0) {
                score += 3;
                reasons.add("có tốc độ tối đa tốt");
            }
        }

        if (containsAny(message, "far", "range", "quang duong", "quãng đường", "di xa", "đi xa", "long distance") && specification != null
            && specification.getMaxRangeKm() != null) {
            if (specification.getMaxRangeKm().compareTo(BigDecimal.valueOf(70)) >= 0) {
                score += 3;
                reasons.add("hỗ trợ quãng đường di chuyển dài");
            }
        }

        if (containsAny(message, "battery", "pin") && specification != null && specification.getBatteryType() != null) {
            score += 1;
            reasons.add("phù hợp câu hỏi về pin");
        }

        if (containsAny(message, "scooter", "xe tay ga", "xe ga", "e_scooter")
            && specification != null && specification.getVehicleType() != null
            && specification.getVehicleType().name().contains("SCOOTER")) {
            score += 2;
            reasons.add("thuộc nhóm xe tay ga điện");
        }

        if (containsAny(message, "bike", "xe dap", "xe đạp", "xe đạp điện", "ebike")
            && specification != null && specification.getVehicleType() != null
            && specification.getVehicleType().name().contains("BIKE")) {
            score += 2;
            reasons.add("thuộc nhóm xe đạp điện");
        }

        String productName = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ROOT);
        String productSlug = product.getSlug() == null ? "" : product.getSlug().toLowerCase(Locale.ROOT);
        if (score == 0 && containsAny(message, productName, productSlug)) {
            score = 1;
            reasons.add("khớp với tên sản phẩm bạn nhắc đến");
        }

        String reason = reasons.isEmpty() ? "phù hợp nhu cầu bạn mô tả" : String.join(", ", reasons);
        return new ScoredProduct(product, score, reason);
    }

    private boolean isCatalogQuestion(String message) {
        return containsAny(message, "sản phẩm", "san pham", "mẫu nào", "mau nao", "có xe", "co xe", "đang có", "dang co", "bán xe", "ban xe", "what bikes", "which bikes", "which models", "what models", "do you sell", "what do you sell", "available bikes")
            || containsAny(message, "xe đạp điện nào", "xe dap dien nao", "xe điện nào", "xe dien nao");
    }

    private boolean isFollowUpPriceQuestion(String message) {
        return containsAny(message, "giá", "gia", "bao nhiêu", "bao nhieu", "giá tiền", "gia tien", "tầm giá", "tam gia", "price", "how much", "cost")
            && containsAny(
                message,
                "those bikes",
                "those models",
                "the first",
                "the second",
                "the third",
                "the last",
                "this one",
                "that one",
                "các dòng xe đó",
                "cac dong xe do",
                "dòng xe đó",
                "dong xe do",
                "mẫu đó",
                "mau do",
                "mấy mẫu",
                "may mau",
                "mẫu trên",
                "mau tren",
                "những mẫu",
                "nhung mau",
                "xe đó",
                "xe do",
                // Pointing at one of the bikes just suggested ("xe đầu tiên giá bao nhiêu?"); the list keeps its order.
                "đầu tiên",
                "dau tien",
                "thứ nhất",
                "thu nhat",
                "thứ hai",
                "thu hai",
                "thứ 2",
                "thu 2",
                "thứ ba",
                "thu ba",
                "thứ 3",
                "thu 3",
                "cuối cùng",
                "cuoi cung",
                "xe này",
                "xe nay",
                "mẫu này",
                "mau nay"
            );
    }

    private String normalizeChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return "default";
        }
        String trimmed = chatId.trim();
        return trimmed.length() > MAX_CHAT_ID_LENGTH ? trimmed.substring(0, MAX_CHAT_ID_LENGTH) : trimmed;
    }

    private String buildShowroomContext(String message) {
        if (!isShowroomQuestion(message)) {
            return "";
        }

        List<Showroom> showrooms = showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc();
        if (showrooms.isEmpty()) {
            return "Hiện chưa có showroom hoạt động trong dữ liệu hệ thống.";
        }

        return showrooms.stream()
            .limit(8)
            .map(showroom -> "- " + showroom.getName()
                + " | " + showroom.getDistrict()
                + " | " + showroom.getAddress()
                + (showroom.getPhone() == null || showroom.getPhone().isBlank() ? "" : " | " + showroom.getPhone())
                + (showroom.getOpeningHours() == null || showroom.getOpeningHours().isBlank() ? "" : " | " + showroom.getOpeningHours()))
            .toList()
            .stream()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private boolean isShowroomQuestion(String message) {
        return containsAny(
            message,
            "showroom",
            "cửa hàng",
            "cua hang",
            "địa chỉ",
            "dia chi",
            "nhận xe",
            "nhan xe",
            "ở đâu",
            "o dau",
            "quận",
            "quan",
            "store",
            "address",
            "pick up",
            "pickup",
            "where can i"
        );
    }

    private String buildOrderPaymentContext(String message) {
        if (!isOrderPaymentQuestion(message)) {
            return "";
        }

        return """
            Quy trình đặt hàng: khách hàng đăng nhập rồi nhập họ tên số điện thoại email, xác thực OTP email, chọn showroom nhận xe, địa chỉ cụ thể và ghi chú nếu có. Hệ thống không yêu cầu CCCD khi đặt hàng.
            Nhận xe: khách chọn giao xe tận nhà (phí %s mỗi xe nếu tỉnh nhận xe có showroom, %s mỗi xe nếu khác tỉnh) hoặc nhận xe tại cửa hàng (miễn phí).
            Báo giá checkout: phí hỗ trợ đăng ký biển số %s mỗi xe, chỉ khi khách nhận xe tại cửa hàng (khách tự chọn, không bắt buộc); chủ xe cần có mặt và tự kê khai trên VNeID mức 2. Giao tận nhà thì chủ xe tự đăng ký.
            Thanh toán: hỗ trợ lưu phương thức thanh toán theo đơn và có luồng VNPay. Khi thanh toán VNPay thành công hệ thống cập nhật payment PAID và đơn hàng CONFIRMED.
            VNPay IPN: backend nhận callback tại /api/v1/payments/vnpay/ipn. Return URL dùng để hiển thị kết quả cho khách hàng.
            """.formatted(
                formatCurrency(pricingService.homeDeliveryPerBike(true)),
                formatCurrency(pricingService.homeDeliveryPerBike(false)),
                formatCurrency(pricingService.registrationServicePerBike())
            ).trim();
    }

    /**
     * V33: the answer to a question about what riding costs or saves, built from the admin's pricing table; empty for any other question. When the
     * question names a bike that is ridden, the answer starts with that bike's own figures for the default 20 km a day.
     */
    private String buildSavingsContext(String message, Product mentionedProduct) {
        if (!isRunningCostQuestion(message)) {
            return "";
        }
        String general = "Mỗi xe điện có công cụ \"Xe điện tiết kiệm bao nhiêu?\" ở trang chi tiết (phía dưới thông số): bạn nhập số km đi mỗi ngày và số ngày đi mỗi tháng, cửa hàng so tiền sạc theo dung lượng pin của đúng xe đó với tiền xăng của xe máy cùng phân khúc. Giá đang áp dụng: điện %s mỗi kWh, xăng %s mỗi lít; xe máy xăng cùng phân khúc tiêu hao khoảng %s lít/100 km (so với xe tay ga điện), %s (xe đạp điện) và %s (xe máy điện). Đây chỉ là ước tính tham khảo. Bạn hãy mở trang xe cần xem để tính."
            .formatted(
                formatCurrency(pricingService.savingsElectricityPricePerKwh()),
                formatCurrency(pricingService.savingsPetrolPricePerLiter()),
                pricingService.savingsPetrolLitersPer100Km("E_SCOOTER").stripTrailingZeros().toPlainString(),
                pricingService.savingsPetrolLitersPer100Km("E_BIKE").stripTrailingZeros().toPlainString(),
                pricingService.savingsPetrolLitersPer100Km("E_MOTORBIKE").stripTrailingZeros().toPlainString());
        String figures = SavingsService.isRidden(mentionedProduct) ? savingsFiguresFor(mentionedProduct) : "";
        return figures.isBlank() ? general : figures + "\n" + general;
    }

    /** The figures of one bike for the default distance, in a sentence; the same estimate the bike's page shows. */
    private String savingsFiguresFor(Product product) {
        SavingsResponse answer = savingsService.forProduct(product, null, null);
        if (!answer.calculable()) {
            return product.getName() + " chưa có đủ thông số pin và quãng đường hợp lý nên chưa tính được.";
        }
        if (!answer.saves()) {
            return "Với %s, đi %d km mỗi ngày và %d ngày mỗi tháng: tiền sạc khoảng %s, tiền xăng xe máy cùng phân khúc khoảng %s, với giá hiện tại xe này chưa rẻ hơn xe xăng."
                .formatted(product.getName(), answer.kmPerDay(), answer.daysPerMonth(),
                    formatCurrency(answer.monthlyElectricityCost()), formatCurrency(answer.monthlyPetrolCost()));
        }
        return "Với %s, đi %d km mỗi ngày và %d ngày mỗi tháng: tiền sạc khoảng %s, tiền xăng xe máy cùng phân khúc khoảng %s, tiết kiệm khoảng %s mỗi tháng (%s mỗi năm)."
            .formatted(product.getName(), answer.kmPerDay(), answer.daysPerMonth(),
                formatCurrency(answer.monthlyElectricityCost()), formatCurrency(answer.monthlyPetrolCost()),
                formatCurrency(answer.monthlySaving()), formatCurrency(answer.yearlySaving()));
    }

    private String savingsCardReason() {
        return "mở trang xe này và dùng mục \"Xe điện tiết kiệm bao nhiêu?\"";
    }

    /** A few ridden bikes (featured first, then the cheapest) so that the answer has a link to a page with the tool. */
    private List<ChatbotRecommendationDto> suggestedBikesForSavings() {
        return productRepository.findAll().stream()
            .filter(product -> Boolean.TRUE.equals(product.getActive()) && SavingsService.isRidden(product))
            .sorted(Comparator
                .comparing((Product product) -> product.getFeatured() != null && product.getFeatured()).reversed()
                .thenComparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(3)
            .map(product -> toRecommendation(product, savingsCardReason()))
            .toList();
    }

    /**
     * Costs of riding are asked in many ways, so the words that are only about cost always count; "saving" counts only next to petrol, charging, a
     * month or the like, and never in a question about a promotion ("how much does the promo code save me").
     */
    private boolean isRunningCostQuestion(String message) {
        if (containsAny(
            message,
            "tiền sạc", "tien sac", "tiền xăng", "tien xang", "tiền điện", "tien dien", "giá điện", "gia dien", "giá xăng", "gia xang",
            "chi phí sạc", "chi phi sac", "chi phí đi lại", "chi phi di lai", "chi phí vận hành", "chi phi van hanh",
            "chi phí sử dụng", "chi phi su dung", "chi phí nuôi xe", "chi phi nuoi xe", "nuôi xe", "nuoi xe",
            "chi phí mỗi tháng", "chi phi moi thang", "chi phí hàng tháng", "chi phi hang thang", "chi phí hằng tháng",
            "so với xe xăng", "so voi xe xang", "mỗi tháng tốn", "moi thang ton",
            "running cost", "cost to charge", "charging cost", "cost of charging", "petrol cost", "fuel cost", "electricity cost",
            "cost of electricity", "cost of petrol", "cost of fuel", "cost per km", "monthly cost", "compared to petrol", "compared with petrol",
            "versus petrol", "vs petrol"
        )) {
            return true;
        }
        if (containsAny(message, "khuyến mãi", "khuyen mai", "mã giảm", "ma giam", "ưu đãi", "uu dai", "voucher", "coupon", "promo", "discount")) {
            return false;
        }
        if (!containsAny(message, "tiết kiệm", "tiet kiem", "save", "saving")) {
            return false;
        }
        return containsAny(
            message,
            "xăng", "xang", "sạc", "sac pin", "mỗi tháng", "moi thang", "hàng tháng", "hang thang", "hằng tháng", "mỗi năm", "moi nam",
            "hàng năm", "hằng năm", "mỗi ngày", "moi ngay", "hàng ngày", "hằng ngày", "một tháng", "1 tháng", "km",
            "petrol", "fuel", "gasoline", "charging", "electricity", "a month", "per month", "each month", "monthly", "a year", "per year",
            "yearly", "a day", "per day", "every day", "daily",
            "how much can i save", "how much would i save", "how much do i save", "how much will i save", "how much does it save"
        );
    }

    private boolean isOrderPaymentQuestion(String message) {
        return containsAny(
            message,
            "đặt hàng",
            "dat hang",
            "checkout",
            "thanh toán",
            "thanh toan",
            "vnpay",
            "phí đăng ký",
            "phi dang ky",
            "ưu đãi",
            "uu dai",
            "tổng thanh toán",
            "tong thanh toan",
            "otp",
            "giao dịch",
            "giao dich",
            "giao xe",
            "tận nhà",
            "tan nha",
            "phí giao",
            "phi giao",
            "biển số",
            "bien so",
            "order",
            "payment",
            "pay ",
            "registration",
            "delivery",
            "discount",
            "shipping"
        );
    }

    private String describeProduct(Product product) {
        ProductSpecification specification = product.getSpecification();
        List<String> details = new ArrayList<>();
        if (product.getCategory() != null && product.getCategory().getName() != null) {
            details.add("danh mục " + product.getCategory().getName());
        }
        if (specification != null && specification.getVehicleType() != null) {
            details.add("loại " + specification.getVehicleType().name());
        }
        if (specification != null && specification.getMaxRangeKm() != null) {
            details.add("quãng đường tối đa khoảng " + specification.getMaxRangeKm().stripTrailingZeros().toPlainString() + " km");
        }
        if (specification != null && specification.getMaxSpeedKmh() != null) {
            details.add("tốc độ tối đa khoảng " + specification.getMaxSpeedKmh().stripTrailingZeros().toPlainString() + " km/h");
        }
        return details.isEmpty() ? "đang được bán trên hệ thống" : String.join(", ", details);
    }

    /** FAQ answers name fees as placeholders so the chatbot always quotes what the admin set. */
    /** FAQ for the support page: category, question and answer, with the current fees filled in. */
    public List<com.ebike.dto.support.FaqItemDto> listFaq() {
        return faqFor().stream()
            .map(this::withCurrentFees)
            .map(entry -> new com.ebike.dto.support.FaqItemDto(entry.category(), entry.question(), entry.answer()))
            .toList();
    }

    private FaqEntry withCurrentFees(FaqEntry entry) {
        String answer = entry.answer()
            .replace("{REGISTRATION_SERVICE_FEE}", formatCurrency(pricingService.registrationServicePerBike()))
            .replace("{HOME_DELIVERY_SAME_CITY_FEE}", formatCurrency(pricingService.homeDeliveryPerBike(true)))
            .replace("{HOME_DELIVERY_OTHER_PROVINCE_FEE}", formatCurrency(pricingService.homeDeliveryPerBike(false)));
        return new FaqEntry(entry.category(), entry.question(), answer, entry.keywords(), entry.priority());
    }

    private String formatCurrency(BigDecimal amount) {
        return amount == null ? "đang cập nhật" : String.format("%,.0f VND", amount);
    }

    private String buildAiPrompt(ChatbotAnalysis analysis) {
        StringBuilder context = new StringBuilder();
        appendContextSection(
            context,
            "FAQ lien quan",
            analysis.faqMatch() == null
                ? ""
                : "- Cau hoi: " + analysis.faqMatch().question() + "\n- Tra loi: " + analysis.faqMatch().answer()
        );

        if (!analysis.recommendations().isEmpty()) {
            StringBuilder recommendations = new StringBuilder();
            for (ChatbotRecommendationDto recommendation : analysis.recommendations()) {
                recommendations.append("- ")
                    .append(recommendation.name())
                    .append(" | Gia: ")
                    .append(recommendation.price())
                    .append(" VND | Ly do: ")
                    .append(recommendation.reason())
                    .append('\n');
            }
            appendContextSection(context, "San pham goi y tu database", recommendations.toString().trim());
        }

        appendContextSection(context, "Showroom dang hoat dong", analysis.showroomContext());
        appendContextSection(context, "Thong tin dat hang va thanh toan", analysis.orderPaymentContext());
        appendContextSection(context, "Cong cu tinh tien tiet kiem cua xe dien", analysis.savingsContext());
        appendContextSection(context, "Tai lieu PDF lien quan", analysis.pdfKnowledgeContext().combinedContext());
        appendContextSection(context, "Cau tra loi fallback neu thieu du lieu", analysis.ruleBasedResponse().answer());

        return """
            Ban la tro ly tu van xe dien cua Kinetic E-Bike.
            Nhiem vu cua ban la tra loi bang tieng Viet ro rang, day du, than thien va dung thong tin trong CONTEXT.
            Yeu cau bat buoc:
            - Khong dung markdown nhu **, ##, hoac tieu de dang dang.
            - Tra loi thanh 1 doan ngan hoac 3-5 bullet ro rang.
            - Neu cau hoi ve chinh sach, quy trinh, bao hanh, doi tra, sac pin: hay tom tat y chinh that cu the.
            - Neu thong tin trong tai lieu co dieu kien ap dung, phai neu ro dieu kien.
            - Neu CONTEXT chua du, hay noi ro phan chua chac va huong dan khach lien he showroom.
            - Khong bia dat thong so, gia, khuyen mai hoac chinh sach ngoai CONTEXT.

            CONTEXT:
            %s

            CAU HOI KHACH HANG:
            %s
            """.formatted(context.toString().trim(), analysis.userMessage());
    }

    private void appendContextSection(StringBuilder context, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!context.isEmpty()) {
            context.append("\n\n");
        }
        context.append(title).append(":\n").append(content.trim());
    }

    private ChatbotResponse buildPdfFallbackResponse(ChatbotAnalysis analysis) {
        List<String> bullets = buildPdfFallbackBullets(analysis);
        StringBuilder answer = new StringBuilder("Minh tra cuu trong tai lieu PDF noi bo va tim thay:\n");
        for (String bullet : bullets) {
            answer.append("\n- ").append(bullet);
        }
        answer.append("\n\nNguon: ")
            .append(String.join(", ", analysis.pdfKnowledgeContext().sourceLabels()));
        if (bullets.isEmpty()) {
            answer.append("\n\nMinh chua tim thay noi dung du cu the de ket luan. Ban co the hoi ro hon ve thoi han, dieu kien ap dung hoac truong hop khong duoc ho tro.");
        }
        return new ChatbotResponse(answer.toString(), "pdf_knowledge", analysis.recommendations());
    }

    private List<String> buildPdfFallbackBullets(ChatbotAnalysis analysis) {
        Set<String> queryTokens = tokenizeForFallback(analysis.normalizedMessage());
        List<String> bullets = new ArrayList<>();
        for (PdfKnowledgeBaseService.KnowledgeSnippet snippet : analysis.pdfKnowledgeContext().snippets()) {
            for (String sentence : splitExcerptIntoSentences(snippet.excerpt())) {
                String cleaned = sentence.trim();
                if (cleaned.length() < 35 || cleaned.length() > 260) {
                    continue;
                }
                if (!queryTokens.isEmpty() && scoreSentence(cleaned, queryTokens) == 0) {
                    continue;
                }
                bullets.add(cleaned + " (" + snippet.sourceName() + ", trang " + snippet.pageNumber() + ")");
                if (bullets.size() >= 4) {
                    return bullets;
                }
            }
        }

        if (bullets.isEmpty()) {
            return analysis.pdfKnowledgeContext().snippets().stream()
                .limit(3)
                .map(snippet -> shorten(snippet.excerpt(), 220) + " (" + snippet.sourceName() + ", trang " + snippet.pageNumber() + ")")
                .toList();
        }
        return bullets;
    }

    private List<String> splitExcerptIntoSentences(String excerpt) {
        if (excerpt == null || excerpt.isBlank()) {
            return List.of();
        }
        return List.of(SENTENCE_BOUNDARY.split(excerpt));
    }

    private int scoreSentence(String sentence, Set<String> queryTokens) {
        String normalizedSentence = normalizeFallbackText(sentence);
        int score = 0;
        for (String token : queryTokens) {
            if (normalizedSentence.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private Set<String> tokenizeForFallback(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalizeFallbackText(text).split("[^\\p{L}\\p{N}]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() >= 3 && !Set.of("toi", "minh", "ban", "cho", "biet", "muon", "ve", "cua").contains(normalized)) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private String normalizeFallbackText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT);
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        int boundary = normalized.lastIndexOf(' ', maxLength);
        return normalized.substring(0, boundary > 80 ? boundary : maxLength).trim() + "...";
    }

    private String sanitizeAiAnswer(String text) {
        if (text == null) {
            return null;
        }
        String sanitized = text
            .replace("\r", "")
            .replace("**", "")
            .replaceAll("(?m)^\\s*[*#]+\\s*$\\n?", "")
            .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();

        if (sanitized.endsWith(":") || sanitized.endsWith("-")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        }
        return sanitized;
    }

    private boolean isUsableAiAnswer(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.length() < 40) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return !normalized.matches(".*\\b(ve|muc|phan|chinh sach|bao hanh)\\b\\s*$");
    }

    private record ChatbotAnalysis(
        String chatId,
        String userMessage,
        String normalizedMessage,
        FaqEntry faqMatch,
        List<ChatbotRecommendationDto> recommendations,
        String showroomContext,
        String orderPaymentContext,
        String savingsContext,
        PdfKnowledgeBaseService.PdfKnowledgeContext pdfKnowledgeContext,
        ChatbotResponse ruleBasedResponse
    ) {
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void loadFaqEntries() {
        faqEntries = readFaq("faq-data/faq.csv");
    }

    private List<FaqEntry> faqFor() {
        return faqEntries;
    }

    private List<FaqEntry> readFaq(String resource) {
        List<FaqEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new ClassPathResource(resource).getInputStream(),
            StandardCharsets.UTF_8
        ))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.equals("...")) {
                    continue;
                }
                List<String> parts = splitCsvLine(line);
                if (parts.size() < 6) {
                    continue;
                }
                entries.add(new FaqEntry(
                    parts.get(1).trim(),
                    parts.get(2).trim(),
                    parts.get(3).trim(),
                    parts.get(4).trim(),
                    parts.get(5).trim()
                ));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load FAQ data", exception);
        }
        return entries;
    }

    /** Splits one CSV line; a field may be wrapped in double quotes (then it can hold commas, and "" is a quote). */
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private record FaqEntry(
        String category,
        String question,
        String answer,
        String keywords,
        String priority
    ) {
        boolean matches(String message) {
            String normalizedQuestion = question.toLowerCase(Locale.ROOT);
            if (message.contains(normalizedQuestion)) {
                return true;
            }
            for (String keyword : keywords.toLowerCase(Locale.ROOT).split(" ")) {
                if (!keyword.isBlank() && message.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        boolean isAskedExactly(String message) {
            return message.contains(question.toLowerCase(Locale.ROOT));
        }

        int keywordHits(String message) {
            return (int) Arrays.stream(keywords.toLowerCase(Locale.ROOT).split(" "))
                .filter(keyword -> !keyword.isBlank() && message.contains(keyword))
                .distinct()
                .count();
        }

        int priorityScore() {
            return switch (priority.toLowerCase(Locale.ROOT)) {
                case "high" -> 3;
                case "medium" -> 2;
                default -> 1;
            };
        }
    }

    private record ScoredProduct(Product product, int score, String reason) {
    }
}
