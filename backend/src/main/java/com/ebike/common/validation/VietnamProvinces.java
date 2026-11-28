package com.ebike.common.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static volatile Set<String> keys;

    private VietnamProvinces() {
    }

    public static boolean isKnown(String name) {
        return name != null && provinceKeys().contains(key(name));
    }

    /** 400 "Tỉnh/thành phố không hợp lệ." unless the name is one of the provinces. */
    public static void require(String name) {
        if (!isKnown(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tỉnh/thành phố không hợp lệ.");
        }
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

    private static Set<String> provinceKeys() {
        Set<String> loaded = keys;
        if (loaded == null) {
            try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
                List<String> names = new ObjectMapper().readValue(in, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
                Set<String> built = new HashSet<>();
                names.forEach(name -> built.add(key(name)));
                loaded = Set.copyOf(built);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read " + RESOURCE, exception);
            }
            keys = loaded;
        }
        return loaded;
    }
}
