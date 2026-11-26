package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.user.UserFavorite;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.user.UserFavoriteRepository;
import com.ebike.service.product.ProductService;
import com.ebike.service.user.FavoriteService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FavoriteServiceTest {

    UserRepository users = mock(UserRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    UserFavoriteRepository favorites = mock(UserFavoriteRepository.class);
    ProductService productService = mock(ProductService.class);
    FavoriteService service;
    User me = Fx.user(5L, "tien", "t@x.com", "CUSTOMER");
    ProductSummaryDto dto = mock(ProductSummaryDto.class);

    @BeforeEach
    void setUp() {
        service = new FavoriteService(users, products, favorites, productService);
        when(users.findByUsername("tien")).thenReturn(Optional.of(me));
        when(productService.toSummaryDto(any())).thenReturn(dto);
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void everyOperation_needsASignedInName_401(String name) {
        assertThatThrownBy(() -> service.getFavorites(name)).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.addFavorite(name, 1L)).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.removeFavorite(name, 1L)).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
    }

    @Test
    void add_createsFavourite_forActiveProduct() {
        Product p = Fx.product(9L, "Amy");
        when(products.findById(9L)).thenReturn(Optional.of(p));
        assertThat(service.addFavorite("tien", 9L)).isSameAs(dto);
        ArgumentCaptor<UserFavorite> saved = ArgumentCaptor.forClass(UserFavorite.class);
        verify(favorites).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(me);
        assertThat(saved.getValue().getProduct()).isSameAs(p);
    }

    @Test
    void add_isIdempotent_noDuplicateRow() {
        Product p = Fx.product(9L, "Amy");
        when(products.findById(9L)).thenReturn(Optional.of(p));
        when(favorites.existsByUserIdAndProductId(5L, 9L)).thenReturn(true);
        service.addFavorite("tien", 9L);
        verify(favorites, never()).save(any());
    }

    @Test
    void add_inactiveOrMissingProduct_is404() {
        Product hidden = Fx.product(9L, "Hidden");
        hidden.setActive(false);
        when(products.findById(9L)).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> service.addFavorite("tien", 9L)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.addFavorite("tien", 404L)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        verify(favorites, never()).save(any());
    }

    @Test
    void add_nullProductId_is400_unknownUser_is404() {
        assertThatThrownBy(() -> service.addFavorite("tien", null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addFavorite("ghost", 1L)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void get_listsOnlyActiveProductsOfThatUser() {
        UserFavorite f = new UserFavorite();
        f.setUser(me);
        f.setProduct(Fx.product(9L, "Amy"));
        when(favorites.findByUserUsernameAndProductActiveTrueOrderByCreatedAtDesc("tien")).thenReturn(List.of(f));
        assertThat(service.getFavorites("tien")).containsExactly(dto);
    }

    @Test
    void remove_deletesOnlyTheCallersFavourite_missingIsNoOp() {
        UserFavorite f = new UserFavorite();
        when(favorites.findByUserUsernameAndProductId("tien", 9L)).thenReturn(Optional.of(f));
        service.removeFavorite("tien", 9L);
        verify(favorites).delete(f);
        service.removeFavorite("tien", 10L);
        verify(favorites, org.mockito.Mockito.times(1)).delete(any());
    }
}
