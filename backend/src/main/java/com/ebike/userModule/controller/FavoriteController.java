package com.ebike.userModule.controller;

import com.ebike.productModule.dto.response.ProductSummaryDto;
import com.ebike.userModule.dto.request.FavoriteProductRequest;
import com.ebike.userModule.service.FavoriteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<ProductSummaryDto> getFavorites(Authentication authentication) {
        return favoriteService.getFavorites(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSummaryDto addFavorite(
        Authentication authentication,
        @RequestBody FavoriteProductRequest request
    ) {
        return favoriteService.addFavorite(authentication.getName(), request.productId());
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(Authentication authentication, @PathVariable Long productId) {
        favoriteService.removeFavorite(authentication.getName(), productId);
    }
}
