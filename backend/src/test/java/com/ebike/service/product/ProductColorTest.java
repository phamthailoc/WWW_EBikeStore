package com.ebike.service.product;

import com.ebike.model.entity.product.*;
import com.ebike.repository.product.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductColorTest {
    private Product product() {
        Product p=new Product();p.setId(1L);p.setSlug("electric");p.setName("Electric");p.setActive(true);
        Category category=new Category();category.setId(1);category.setName("Xe điện");category.setSlug("xe-dien");p.setCategory(category);
        return p;
    }
    private ProductVariant color(Product product,long id,boolean active,boolean selected) {
        ProductVariant v=new ProductVariant();v.setId(id);v.setActive(active);v.setDefaultVariant(selected);v.setColorName("Color "+id);v.setProduct(product);product.getVariants().add(v);return v;
    }
    private void image(Product product,ProductVariant variant,String url,int order,ProductImageStatus status) {
        ProductImage im=new ProductImage();im.setProduct(product);im.setVariant(variant);im.setImageUrl(url);im.setSortOrder(order);im.setStatus(status);variant.getImages().add(im);product.getImages().add(im);
    }
    @Test void onlyActiveColorsWithTheirOwnActivePhotosAreExposedAndDefaultComesFirst() {
        Product p=product();ProductRepository repo=mock(ProductRepository.class);
        ProductVariant blue=color(p,1,true,false),white=color(p,2,true,true),legacy=color(p,3,false,false);
        image(p,blue,"/blue.png",10,ProductImageStatus.ACTIVE);
        image(p,white,"/white-side.png",1,ProductImageStatus.ACTIVE);
        image(p,white,"/white.png",0,ProductImageStatus.ACTIVE);
        image(p,white,"/deleted.png",2,ProductImageStatus.DELETED);
        image(p,legacy,"/fake.png",3,ProductImageStatus.ACTIVE);
        when(repo.findDetailBySlug("electric")).thenReturn(Optional.of(p));
        var detail=new ProductService(repo).getProductBySlug("electric");
        assertEquals(2,detail.variants().size());assertEquals(2L,detail.variants().get(0).id());
        assertEquals(java.util.List.of("/white.png","/white-side.png"),detail.variants().get(0).images());
        assertEquals("/blue.png",detail.variants().get(1).imageUrl());
        assertFalse(detail.images().contains("/fake.png"));assertFalse(detail.images().contains("/deleted.png"));
    }
    @Test void aColorWithoutVerifiedActivePhotosIsNotSelectable() {
        Product p=product();ProductRepository repo=mock(ProductRepository.class);ProductVariant v=color(p,1,true,true);
        image(p,v,"/deleted.png",0,ProductImageStatus.DELETED);
        when(repo.findDetailBySlug("electric")).thenReturn(Optional.of(p));
        assertTrue(new ProductService(repo).getProductBySlug("electric").variants().isEmpty());
    }
    @Test void inactiveGasolineProductsCannotBeOpened() {
        Product p=product();p.setActive(false);ProductRepository repo=mock(ProductRepository.class);
        when(repo.findDetailBySlug("electric")).thenReturn(Optional.of(p));
        assertThrows(ResponseStatusException.class,()->new ProductService(repo).getProductBySlug("electric"));
    }
}
