package com.pawly.store;

import com.pawly.dto.store.CategoryDTO;
import com.pawly.entity.ProductCategory;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.ProductCategoryRepository;
import com.pawly.service.store.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock  private ProductCategoryRepository categoryRepository;
    @InjectMocks private CategoryService categoryService;

    private ProductCategory food;

    @BeforeEach
    void setUp() {
        food = ProductCategory.builder().name("Food").isActive(true).build();
        food = setId(food, 1L);
    }

    @Test
    void findAllActive_returns_only_active_categories() {
        ProductCategory inactive = ProductCategory.builder().name("Old").isActive(false).build();
        given(categoryRepository.findByIsActiveTrueOrderByNameAsc()).willReturn(List.of(food));

        List<CategoryDTO> result = categoryService.findAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Food");
    }

    @Test
    void create_saves_and_returns_dto() {
        CategoryDTO dto = CategoryDTO.builder().name("Toys").description("Fun stuff").isActive(true).build();
        ProductCategory saved = ProductCategory.builder().name("Toys").isActive(true).build();
        saved = setId(saved, 2L);

        given(categoryRepository.existsByNameIgnoreCase("Toys")).willReturn(false);
        given(categoryRepository.save(any())).willReturn(saved);

        CategoryDTO result = categoryService.create(dto);

        assertThat(result.getName()).isEqualTo("Toys");
        then(categoryRepository).should().save(any(ProductCategory.class));
    }

    @Test
    void create_throws_when_name_already_exists() {
        CategoryDTO dto = CategoryDTO.builder().name("Food").build();
        given(categoryRepository.existsByNameIgnoreCase("Food")).willReturn(true);

        assertThatThrownBy(() -> categoryService.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_applies_partial_changes() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(food));
        given(categoryRepository.findByNameIgnoreCase("Petfood")).willReturn(Optional.empty());
        given(categoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CategoryDTO dto = CategoryDTO.builder().name("Petfood").build();
        CategoryDTO result = categoryService.update(1L, dto);

        assertThat(result.getName()).isEqualTo("Petfood");
    }

    @Test
    void delete_throws_when_category_has_products() {
        // Simulate a category with 1 product by using spy approach
        ProductCategory withProducts = new ProductCategory();
        withProducts.setName("Busy");
        withProducts.setIsActive(true);
        // Add a dummy product reference
        com.pawly.entity.Product dummyProduct = new com.pawly.entity.Product();
        withProducts.getProducts().add(dummyProduct);
        withProducts = setId(withProducts, 3L);

        given(categoryRepository.findById(3L)).willReturn(Optional.of(withProducts));

        assertThatThrownBy(() -> categoryService.delete(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete category with existing products");
    }

    @Test
    void getCategory_throws_not_found_for_unknown_id() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Utility: simulate JPA-assigned id via reflection
    private ProductCategory setId(ProductCategory c, Long id) {
        try {
            var field = ProductCategory.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }
}
