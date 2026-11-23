package com.ebike.service.seo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.product.Product;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.product.ProductService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

/** V35: the sitemap lists what {@code ProductService.getProducts} returns, so that call must only ever ask for products on sale. */
class SitemapActiveProductsTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theProductListTheSitemapReadsOnlyAsksForActiveProducts() {
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll(any(Specification.class))).thenReturn(List.of());
        new ProductService(repo).getProducts(null, null, null, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Specification> captured = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captured.capture());
        Root<Product> root = mock(Root.class);
        Path active = mock(Path.class);
        when(root.get("active")).thenReturn(active);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        captured.getValue().toPredicate(root, mock(CriteriaQuery.class), builder);
        verify(builder).isTrue(active);
    }
}
