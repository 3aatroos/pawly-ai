package com.pawly.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.store.CreateProductRequest;
import com.pawly.dto.store.ReviewDTO;
import com.pawly.dto.store.UpdateProductRequest;
import com.pawly.dto.store.UpdateStockRequest;
import com.pawly.entity.*;
import com.pawly.repository.*;
import com.pawly.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User vetUser;
    private User petOwner;
    private User adminUser;
    private ProductCategory category;
    private Product product;

    private String vetToken;
    private String ownerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        vetUser = userRepository.save(User.builder()
                .email("vet@store.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Vet").lastName("User")
                .role(User.Role.VETERINARIAN).status(User.UserStatus.ACTIVE).build());

        petOwner = userRepository.save(User.builder()
                .email("owner@store.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Owner").lastName("User")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        adminUser = userRepository.save(User.builder()
                .email("admin@store.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Admin").lastName("User")
                .role(User.Role.ADMINISTRATOR).status(User.UserStatus.ACTIVE).build());

        vetToken   = "Bearer " + jwtUtil.generateToken(vetUser.getEmail(),   vetUser.getRole().name());
        ownerToken = "Bearer " + jwtUtil.generateToken(petOwner.getEmail(), petOwner.getRole().name());
        adminToken = "Bearer " + jwtUtil.generateToken(adminUser.getEmail(), adminUser.getRole().name());

        category = categoryRepository.save(
                ProductCategory.builder().name("IntegrationFood").isActive(true).build());

        product = productRepository.save(Product.builder()
                .category(category).createdBy(vetUser)
                .name("Integration Product").description("Test desc")
                .price(new BigDecimal("19.99")).stockQuantity(15)
                .isAvailable(true).targetSpecies("dog").build());
    }

    // ── GET /api/store/products — public ──────────────────────

    @Test
    void GET_products_is_accessible_without_authentication() throws Exception {
        mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void GET_products_filters_by_category() throws Exception {
        mockMvc.perform(get("/api/store/products")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Integration Product"));
    }

    @Test
    void GET_product_by_id_returns_detail() throws Exception {
        mockMvc.perform(get("/api/store/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Integration Product"))
                .andExpect(jsonPath("$.price").value(19.99));
    }

    @Test
    void GET_product_by_id_returns_404_for_unknown() throws Exception {
        mockMvc.perform(get("/api/store/products/99999"))
                .andExpect(status().isNotFound());
    }

    // ── Stock visibility ──────────────────────────────────────

    @Test
    void GET_products_hides_stock_for_unauthenticated_caller() throws Exception {
        mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stockQuantity").doesNotExist());
    }

    @Test
    void GET_products_shows_stock_for_vet() throws Exception {
        mockMvc.perform(get("/api/store/products")
                        .header("Authorization", vetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stockQuantity").value(15));
    }

    // ── POST /api/store/products ──────────────────────────────

    @Test
    void POST_product_succeeds_for_vet() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("New Cat Food");
        req.setPrice(new BigDecimal("12.50"));
        req.setCategoryId(category.getId());
        req.setStockQuantity(30);
        req.setIsAvailable(true);

        mockMvc.perform(post("/api/store/products")
                        .header("Authorization", vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Cat Food"))
                .andExpect(jsonPath("$.createdById").value(vetUser.getId()));
    }

    @Test
    void POST_product_returns_403_for_pet_owner() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Forbidden Product");
        req.setPrice(new BigDecimal("5.00"));
        req.setCategoryId(category.getId());

        mockMvc.perform(post("/api/store/products")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_product_returns_401_without_token() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("No Auth");
        req.setPrice(new BigDecimal("5.00"));
        req.setCategoryId(category.getId());

        mockMvc.perform(post("/api/store/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_product_returns_400_when_name_missing() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setPrice(new BigDecimal("10.00"));
        req.setCategoryId(category.getId());

        mockMvc.perform(post("/api/store/products")
                        .header("Authorization", vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ── PUT /api/store/products/{id} ──────────────────────────

    @Test
    void PUT_product_succeeds_for_owner_vet() throws Exception {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("Updated by Owner Vet");

        mockMvc.perform(put("/api/store/products/{id}", product.getId())
                        .header("Authorization", vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated by Owner Vet"));
    }

    @Test
    void PUT_product_returns_403_when_vet_does_not_own_product() throws Exception {
        User otherVet = userRepository.save(User.builder()
                .email("othvet@store.test").passwordHash(passwordEncoder.encode("p"))
                .firstName("Other").lastName("Vet")
                .role(User.Role.VETERINARIAN).status(User.UserStatus.ACTIVE).build());
        String otherToken = "Bearer " + jwtUtil.generateToken(otherVet.getEmail(), otherVet.getRole().name());

        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("Hijack attempt");

        mockMvc.perform(put("/api/store/products/{id}", product.getId())
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void PUT_product_succeeds_for_admin_on_any_product() throws Exception {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setPrice(new BigDecimal("99.00"));

        mockMvc.perform(put("/api/store/products/{id}", product.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(99.0));
    }

    // ── PATCH stock ───────────────────────────────────────────

    @Test
    void PATCH_stock_updates_quantity() throws Exception {
        UpdateStockRequest req = new UpdateStockRequest();
        req.setStockQuantity(100);

        mockMvc.perform(patch("/api/store/products/{id}/stock", product.getId())
                        .header("Authorization", vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(100));
    }

    @Test
    void PATCH_stock_returns_400_for_negative_value() throws Exception {
        UpdateStockRequest req = new UpdateStockRequest();
        req.setStockQuantity(-5);

        mockMvc.perform(patch("/api/store/products/{id}/stock", product.getId())
                        .header("Authorization", vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH availability ────────────────────────────────────

    @Test
    void PATCH_availability_toggles_product() throws Exception {
        mockMvc.perform(patch("/api/store/products/{id}/availability", product.getId())
                        .header("Authorization", vetToken)
                        .param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    // ── DELETE /api/store/products/{id} ───────────────────────

    @Test
    void DELETE_product_removes_it() throws Exception {
        mockMvc.perform(delete("/api/store/products/{id}", product.getId())
                        .header("Authorization", vetToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/store/products/{id}", product.getId()))
                .andExpect(status().isNotFound());
    }

    // ── Reviews ───────────────────────────────────────────────

    @Test
    void POST_review_succeeds_for_pet_owner() throws Exception {
        ReviewDTO dto = ReviewDTO.builder().rating(4).comment("Really good").build();

        mockMvc.perform(post("/api/store/products/{id}/reviews", product.getId())
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.userId").value(petOwner.getId()));
    }

    @Test
    void POST_review_returns_400_for_invalid_rating() throws Exception {
        ReviewDTO dto = ReviewDTO.builder().rating(6).comment("Out of range").build();

        mockMvc.perform(post("/api/store/products/{id}/reviews", product.getId())
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_review_returns_400_when_user_already_reviewed() throws Exception {
        ReviewDTO dto = ReviewDTO.builder().rating(3).build();

        mockMvc.perform(post("/api/store/products/{id}/reviews", product.getId())
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/store/products/{id}/reviews", product.getId())
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_reviews_is_accessible_without_authentication() throws Exception {
        mockMvc.perform(get("/api/store/products/{id}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
