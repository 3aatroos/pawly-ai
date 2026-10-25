package com.pawly.store;

import com.pawly.entity.ProductCategory;
import com.pawly.repository.ProductCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductCategoryRepositoryTest {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
    }

    @Test
    void save_and_findById_persist_category() {
        ProductCategory category = ProductCategory.builder()
                .name("Food")
                .description("Pet food and treats")
                .isActive(true)
                .build();

        ProductCategory saved = categoryRepository.save(category);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Food");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByIsActiveTrueOrderByNameAsc_returns_only_active() {
        categoryRepository.save(ProductCategory.builder().name("Food").isActive(true).build());
        categoryRepository.save(ProductCategory.builder().name("Toys").isActive(true).build());
        categoryRepository.save(ProductCategory.builder().name("Archived").isActive(false).build());

        List<ProductCategory> active = categoryRepository.findByIsActiveTrueOrderByNameAsc();

        assertThat(active).hasSize(2);
        assertThat(active).extracting(ProductCategory::getName).containsExactly("Food", "Toys");
    }

    @Test
    void findByNameIgnoreCase_returns_matching_category() {
        categoryRepository.save(ProductCategory.builder().name("Hygiene").isActive(true).build());

        Optional<ProductCategory> result = categoryRepository.findByNameIgnoreCase("hygiene");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Hygiene");
    }

    @Test
    void existsByNameIgnoreCase_returns_true_for_existing_name() {
        categoryRepository.save(ProductCategory.builder().name("Health").isActive(true).build());

        assertThat(categoryRepository.existsByNameIgnoreCase("health")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("Unknown")).isFalse();
    }
}
