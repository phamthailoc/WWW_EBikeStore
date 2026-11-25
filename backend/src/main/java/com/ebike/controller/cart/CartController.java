package com.ebike.controller.cart;

import com.ebike.dto.cart.CartLine;
import com.ebike.service.cart.SessionCartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The cart of the current visitor, kept in the server-side HTTP session. */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final SessionCartService cart;

    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public CartController(SessionCartService cart, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.cart = cart;
        this.mapper = mapper;
    }

    // Only a PUT starts a session: a visitor who never put anything in the cart gets none (and every page view asks).
    @GetMapping
    public List<CartLine> get(HttpServletRequest request) {
        return cart.read(request.getSession(false));
    }

    /** A cart of 20 lines is well under 4 KB: at most 8 KB of the body is read, and anything longer is refused before it is parsed. */
    static final int MAX_BODY_BYTES = 8192;

    @PutMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public List<CartLine> replace(HttpServletRequest request) throws java.io.IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Dữ liệu giỏ hàng quá lớn.");
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Dữ liệu giỏ hàng quá lớn.");
        }
        List<CartLine> lines;
        try {
            lines = mapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<List<CartLine>>() { });
        } catch (java.io.IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Giỏ hàng không hợp lệ.");
        }
        if (lines == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Giỏ hàng không hợp lệ.");
        }
        return cart.replace(request.getSession(true), lines);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(HttpServletRequest request) {
        cart.clear(request.getSession(false));
    }
}
