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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The cart of the current visitor, kept in the server-side HTTP session. */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final SessionCartService cart;

    public CartController(SessionCartService cart) {
        this.cart = cart;
    }

    // Only a PUT starts a session: a visitor who never put anything in the cart gets none (and every page view asks).
    @GetMapping
    public List<CartLine> get(HttpServletRequest request) {
        return cart.read(request.getSession(false));
    }

    @PutMapping
    public List<CartLine> replace(@RequestBody List<CartLine> lines, HttpSession session) {
        return cart.replace(session, lines);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(HttpServletRequest request) {
        cart.clear(request.getSession(false));
    }
}
