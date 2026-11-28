package com.ebike.common.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The provinces a delivery or an address book entry may name. The list is the same file the storefront selects read
 * (static/data/vietnam-provinces.json), so the page and the server cannot drift apart.
 * Comparison ignores accents, case and a leading "Tỉnh" / "Thành phố" / "TP.", like the delivery-fee lookup does.
 */
public final class VietnamProvinces {

    private static final String RESOURCE = "static/data/vietnam-provinces.json";
    private static volatile Map<String, String> keys;

    private VietnamProvinces() {
    }

    public static boolean isKnown(String name) {
        return canonical(name).isPresent();
    }

    /** The name as the province list spells it ("ha noi" gives "Hà Nội"), empty when it is not a province. */
    public static Optional<String> canonical(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(provinceKeys().get(key(name)));
    }

    /** 400 "Tỉnh/thành phố không hợp lệ." unless the name is one of the provinces; returns the list spelling. */
    public static String require(String name) {
        return canonical(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tỉnh/thành phố không hợp lệ."));
    }

    /** "Thành phố Hồ Chí Minh", "TP. Ho Chi Minh" and "Hồ Chí Minh" all become "ho chi minh". */
    public static String key(String name) {
        if (name == null) {
            return "";
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace("đ", "d").replace("Đ", "D")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return plain.replaceFirst("^(thanh pho|tinh|tp) ", "");
    }

    private static Map<String, String> provinceKeys() {
        Map<String, String> loaded = keys;
        if (loaded == null) {
            try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
                List<String> names = new ObjectMapper().readValue(in, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
                Map<String, String> built = new HashMap<>();
                names.forEach(name -> built.putIfAbsent(key(name), name));
                loaded = Map.copyOf(built);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read " + RESOURCE, exception);
            }
            keys = loaded;
        }
        return loaded;
    }
}
