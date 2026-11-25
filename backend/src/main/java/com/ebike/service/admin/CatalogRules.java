package com.ebike.service.admin;

import com.ebike.dto.admin.request.CatalogCategoryRequest;
import com.ebike.dto.admin.request.CatalogProductRequest;
import com.ebike.model.entity.product.BatteryType;
import com.ebike.model.entity.product.VehicleType;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The rules of the admin catalogue form, kept in the application (the database holds no CHECK constraints, functions or procedures).
 * Every refusal is a 400 with a sentence the form can show next to the field.
 */
public final class CatalogRules {

    public static final int MAX_NAME = 255;
    public static final int MAX_CATEGORY_NAME = 100;
    public static final int MAX_DESCRIPTION = 5000;
    /** The price columns are DECIMAL(10,2). */
    public static final BigDecimal MAX_PRICE = new BigDecimal("99999999");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]+");

    private CatalogRules() {
    }

    /** "Xe điện Dibao" becomes "xe-dien-dibao"; an empty result stays empty. */
    public static String slugify(String text) {
        if (text == null) {
            return "";
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)).replaceAll("");
        return NOT_SLUG.matcher(plain).replaceAll("-").replaceAll("^-+|-+$", "");
    }

    /** The slug the form sent, or one made from the name when it left the field empty. */
    public static String slugFor(String requested, String name, int maxLength) {
        String slug = requested == null || requested.isBlank() ? slugify(name) : requested.trim();
        if (slug.isEmpty()) {
            throw bad("Không tạo được đường dẫn từ tên này. Hãy nhập đường dẫn gồm chữ thường, số và dấu gạch ngang.");
        }
        if (slug.length() > maxLength || !SLUG.matcher(slug).matches()) {
            throw bad("Đường dẫn chỉ gồm chữ thường không dấu, số và dấu gạch ngang (tối đa " + maxLength + " ký tự).");
        }
        return slug;
    }

    public static String requiredName(String raw, int max, String what) {
        String name = raw == null ? "" : raw.trim();
        if (name.length() < 2) {
            throw bad(what + " phải có ít nhất 2 ký tự.");
        }
        if (name.length() > max) {
            throw bad(what + " tối đa " + max + " ký tự.");
        }
        return name;
    }

    public static String optionalText(String raw, int max, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.length() > max) {
            throw bad(what + " tối đa " + max + " ký tự.");
        }
        return text;
    }

    public static void checkProduct(CatalogProductRequest request) {
        if (request == null) {
            throw bad("Thiếu nội dung yêu cầu.");
        }
        requiredName(request.name(), MAX_NAME, "Tên sản phẩm");
        optionalText(request.description(), MAX_DESCRIPTION, "Mô tả");
        if (request.categoryId() == null) {
            throw bad("Vui lòng chọn loại sản phẩm.");
        }
        checkPrices(request.price(), request.discountPrice());
        enumValue(VehicleType.class, request.vehicleType(), "Loại xe");
        enumValue(BatteryType.class, request.batteryType(), "Loại pin");
        positive(request.batteryCapacityAh(), "Dung lượng pin", new BigDecimal("100000"));
        positive(request.batteryVoltageV(), "Điện áp pin", new BigDecimal("10000"));
        positive(request.maxSpeedKmh(), "Tốc độ tối đa", new BigDecimal("1000"));
        positive(request.maxRangeKm(), "Quãng đường tối đa", new BigDecimal("10000"));
        if (request.motorPowerWatts() != null && (request.motorPowerWatts() <= 0 || request.motorPowerWatts() > 100000)) {
            throw bad("Công suất động cơ phải từ 1 đến 100000 W.");
        }
        if (request.warrantyMonths() != null && (request.warrantyMonths() < 0 || request.warrantyMonths() > 120)) {
            throw bad("Thời hạn bảo hành phải từ 0 đến 120 tháng.");
        }
        optionalText(request.brand(), 100, "Thương hiệu");
        optionalText(request.modelCode(), 100, "Mã mẫu");
    }

    public static void checkPrices(BigDecimal price, BigDecimal discountPrice) {
        if (price == null || price.signum() <= 0) {
            throw bad("Giá bán phải lớn hơn 0.");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw bad("Giá bán tối đa 99.999.999 ₫.");
        }
        if (discountPrice != null) {
            if (discountPrice.signum() <= 0) {
                throw bad("Giá khuyến mãi phải lớn hơn 0 (để trống nếu không giảm giá).");
            }
            if (discountPrice.compareTo(price) >= 0) {
                throw bad("Giá khuyến mãi phải thấp hơn giá bán.");
            }
        }
    }

    public static void checkCategory(CatalogCategoryRequest request) {
        if (request == null) {
            throw bad("Thiếu nội dung yêu cầu.");
        }
        requiredName(request.name(), MAX_CATEGORY_NAME, "Tên loại");
        optionalText(request.description(), MAX_DESCRIPTION, "Mô tả");
    }

    /** Null or blank means "not set"; anything else must be a constant of the enum. */
    public static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw bad(what + " không hợp lệ.");
        }
    }

    private static void positive(BigDecimal value, String what, BigDecimal max) {
        if (value != null && (value.signum() <= 0 || value.compareTo(max) > 0)) {
            throw bad(what + " phải lớn hơn 0 và không vượt quá " + max.toPlainString() + ".");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
