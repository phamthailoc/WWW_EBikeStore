package com.ebike.service.user;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.product.ProductService;
import com.ebike.model.entity.user.UserFavorite;
import com.ebike.repository.user.UserFavoriteRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FavoriteService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final ProductService productService;

    public FavoriteService(
        UserRepository userRepository,
        ProductRepository productRepository,
        UserFavoriteRepository userFavoriteRepository,
        ProductService productService
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getFavorites(String username) {
        requireUsername(username);
        return userFavoriteRepository.findByUserUsernameAndProductActiveTrueOrderByCreatedAtDesc(username).stream()
            .map(UserFavorite::getProduct)
            .map(productService::toSummaryDto)
            .toList();
    }

    @Transactional
    public ProductSummaryDto addFavorite(String username, Long productId) {
        requireUsername(username);
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
        }

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Product product = productRepository.findById(productId)
            .filter(item -> Boolean.TRUE.equals(item.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        if (!userFavoriteRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            UserFavorite favorite = new UserFavorite();
            favorite.setUser(user);
            favorite.setProduct(product);
            userFavoriteRepository.save(favorite);
        }

        return productService.toSummaryDto(product);
    }

    @Transactional
    public void removeFavorite(String username, Long productId) {
        requireUsername(username);
        userFavoriteRepository.findByUserUsernameAndProductId(username, productId)
            .ifPresent(userFavoriteRepository::delete);
    }

    private void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
    }
}
