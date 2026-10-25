package com.pawly.store;

import com.pawly.entity.*;
import com.pawly.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private ProductCategory category;
    private User admin;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(
                ProductCategory.builder().name("Food").isActive(true).build());

        admin = userRepository.save(User.builder()
                .email("admin@test.com")
                .passwordHash("$2a$12$hash")
                .firstName("Admin")
                .lastName("Test")
                .role(User.Role.ADMINISTRATOR)
                .status(User.UserStatus.ACTIVE)
                .build());
    }

    private Product buildProduct(String name, String species, boolean available, BigDecimal price) {
        return Product.builder()
                .category(category)
                .createdBy(admin)
                .name(name)
                .description("Description for " + name)
                .price(price)
                .stockQuantity(10)
                .isAvailable(available)
                .targetSpecies(species)
                .build();
    }

    @Test
    void save_and_findById_persist_product_with_correct_defaults() {
        Product product = productRepository.save(buildProduct("Royal Canin Cat", "cat", true, new BigDecimal("29.99")));

        assertThat(product.getId()).isNotNull();
        assertThat(product.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(product.getReviewCount()).isZero();
        assertThat(product.getIsAvailable()).isTrue();
        assertThat(product.getVersion()).isNotNull();
        assertThat(product.getCreatedAt()).isNotNull();
    }

    @Test
    void findAllFiltered_filters_by_category() {
        ProductCategory other = categoryRepository.save(
                ProductCategory.builder().name("Toys").isActive(true).build());

        productRepository.save(buildProduct("Cat Food", "cat", true, new BigDecimal("15.00")));
        Product toy = Product.builder()
                .category(other).createdBy(admin)
                .name("Ball").price(new BigDecimal("5.00"))
                .stockQuantity(5).isAvailable(true).build();
        productRepository.save(toy);

        Page<Product> results = productRepository.findAllFiltered(
                category.getId(), null, true, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getName()).isEqualTo("Cat Food");
    }

    @Test
    void findAllFiltered_filters_by_availability() {
        productRepository.save(buildProduct("Available", "dog", true,  new BigDecimal("10.00")));
        productRepository.save(buildProduct("Unavailable", "dog", false, new BigDecimal("10.00")));

        Page<Product> available = productRepository.findAllFiltered(
                null, null, true, null, null, null, PageRequest.of(0, 10));

        assertThat(available.getContent()).hasSize(1);
        assertThat(available.getContent().get(0).getName()).isEqualTo("Available");
    }

    @Test
    void findAllFiltered_filters_by_price_range() {
        productRepository.save(buildProduct("Cheap",      "all", true, new BigDecimal("5.00")));
        productRepository.save(buildProduct("Mid-range",  "all", true, new BigDecimal("25.00")));
        productRepository.save(buildProduct("Expensive",  "all", true, new BigDecimal("99.00")));

        Page<Product> results = productRepository.findAllFiltered(
                null, null, null,
                new BigDecimal("10.00"), new BigDecimal("50.00"),
                null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getName()).isEqualTo("Mid-range");
    }

    @Test
    void findAllFiltered_searches_by_name() {
        productRepository.save(buildProduct("Royal Canin Maxi", "dog", true, new BigDecimal("30.00")));
        productRepository.save(buildProduct("Whiskas Tuna",     "cat", true, new BigDecimal("12.00")));

        Page<Product> results = productRepository.findAllFiltered(
                null, null, null, null, null, "royal", PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getName()).startsWith("Royal");
    }

    @Test
    void decrementStock_succeeds_when_sufficient_stock() {
        Product product = productRepository.save(
                buildProduct("Test Product", "dog", true, new BigDecimal("10.00")));

        int updated = productRepository.decrementStock(product.getId(), 3);

        assertThat(updated).isEqualTo(1);
        em.refresh(product);
        assertThat(product.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void decrementStock_fails_when_insufficient_stock() {
        Product product = productRepository.save(
                buildProduct("Low Stock", "cat", true, new BigDecimal("10.00")));
        product.setStockQuantity(2);
        productRepository.save(product);

        int updated = productRepository.decrementStock(product.getId(), 5);

        assertThat(updated).isZero();
        em.refresh(product);
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void findByIdAndIsAvailableTrue_returns_empty_for_unavailable_product() {
        Product product = productRepository.save(
                buildProduct("Discontinued", "all", false, new BigDecimal("10.00")));

        Optional<Product> result = productRepository.findByIdAndIsAvailableTrue(product.getId());

        assertThat(result).isEmpty();
    }
}
