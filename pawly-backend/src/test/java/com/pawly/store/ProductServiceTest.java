package com.pawly.store;

import com.pawly.dto.store.*;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.*;
import com.pawly.service.UserService;
import com.pawly.service.store.CategoryService;
import com.pawly.service.store.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private UserService userService;
    @Mock private CategoryService categoryService;

    @InjectMocks private ProductService productService;

    private User vetUser;
    private User adminUser;
    private User ownerUser;
    private ProductCategory category;
    private Product product;

    @BeforeEach
    void setUp() {
        vetUser = buildUser(1L, "vet@test.com", User.Role.VETERINARIAN);
        adminUser = buildUser(2L, "admin@test.com", User.Role.ADMINISTRATOR);
        ownerUser = buildUser(3L, "owner@test.com", User.Role.PET_OWNER);

        category = ProductCategory.builder().name("Food").isActive(true).build();
        setField(category, "id", 1L);

        product = Product.builder()
                .category(category)
                .createdBy(vetUser)
                .name("Royal Canin")
                .price(new BigDecimal("29.99"))
                .stockQuantity(10)
                .isAvailable(true)
                .build();
        setField(product, "id", 1L);
        setField(product, "version", 0L);
    }

    @Test
    void findAll_delegates_to_repository_with_correct_filters() {
        given(productRepository.findAllFiltered(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(product)));

        Page<ProductSummaryDTO> result = productService.findAll(
                null, null, true, null, null, null, false, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Royal Canin");
    }

    @Test
    void findById_throws_not_found_for_unknown_id() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_saves_product_with_creator() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Cat Kibble");
        req.setPrice(new BigDecimal("15.00"));
        req.setCategoryId(1L);
        req.setStockQuantity(20);
        req.setIsAvailable(true);

        given(userService.getUserByEmail("vet@test.com")).willReturn(vetUser);
        given(categoryService.getCategory(1L)).willReturn(category);
        given(productRepository.save(any())).willAnswer(inv -> {
            Product p = inv.getArgument(0);
            setField(p, "id", 10L);
            setField(p, "version", 0L);
            return p;
        });

        ProductDTO result = productService.create(req, "vet@test.com");

        assertThat(result.getName()).isEqualTo("Cat Kibble");
        assertThat(result.getCreatedById()).isEqualTo(vetUser.getId());
        then(productRepository).should().save(any(Product.class));
    }

    @Test
    void update_succeeds_when_vet_owns_product() {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("Updated Name");

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("vet@test.com")).willReturn(vetUser);
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.update(1L, req, "vet@test.com", false);

        assertThat(result.getName()).isEqualTo("Updated Name");
    }

    @Test
    void update_throws_access_denied_when_vet_does_not_own_product() {
        User otherVet = buildUser(99L, "other@test.com", User.Role.VETERINARIAN);
        product.setCreatedBy(otherVet);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("Hack");

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("vet@test.com")).willReturn(vetUser);

        assertThatThrownBy(() -> productService.update(1L, req, "vet@test.com", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_succeeds_for_admin_on_any_product() {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setPrice(new BigDecimal("99.00"));

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.update(1L, req, "admin@test.com", true);

        assertThat(result.getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    void delete_removes_product_owned_by_caller() {
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("vet@test.com")).willReturn(vetUser);

        productService.delete(1L, "vet@test.com", false);

        then(productRepository).should().delete(product);
    }

    @Test
    void updateStock_updates_quantity() {
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("vet@test.com")).willReturn(vetUser);
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.updateStock(1L, 50, "vet@test.com", false);

        assertThat(result.getStockQuantity()).isEqualTo(50);
    }

    @Test
    void updateAvailability_sets_flag() {
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("vet@test.com")).willReturn(vetUser);
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.updateAvailability(1L, false, "vet@test.com", false);

        assertThat(result.getIsAvailable()).isFalse();
    }

    @Test
    void productSummaryDTO_hides_stock_for_pet_owner() {
        given(productRepository.findAllFiltered(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(product)));

        Page<ProductSummaryDTO> result = productService.findAll(
                null, null, true, null, null, null, false, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getStockQuantity()).isNull();
    }

    @Test
    void productSummaryDTO_shows_stock_for_vet() {
        given(productRepository.findAllFiltered(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(product)));

        Page<ProductSummaryDTO> result = productService.findAll(
                null, null, null, null, null, null, true, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getStockQuantity()).isEqualTo(10);
    }

    // ── Helpers ───────────────────────────────────────────────

    private User buildUser(Long id, String email, User.Role role) {
        User u = User.builder()
                .email(email).passwordHash("hash")
                .firstName("First").lastName("Last")
                .role(role).status(User.UserStatus.ACTIVE)
                .build();
        setField(u, "id", id);
        return u;
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
