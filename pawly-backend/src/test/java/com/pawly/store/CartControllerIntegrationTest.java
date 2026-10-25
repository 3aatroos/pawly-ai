package com.pawly.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.store.AddToCartRequest;
import com.pawly.dto.store.UpdateCartItemRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User buyer;
    private User otherBuyer;
    private Product product;
    private String buyerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder()
                .email("buyer@cart.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Cart").lastName("Buyer")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        otherBuyer = userRepository.save(User.builder()
                .email("other@cart.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Other").lastName("Buyer")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        User admin = userRepository.save(User.builder()
                .email("admin@cart.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Admin").lastName("Cart")
                .role(User.Role.ADMINISTRATOR).status(User.UserStatus.ACTIVE).build());

        buyerToken = "Bearer " + jwtUtil.generateToken(buyer.getEmail(), buyer.getRole().name());
        otherToken = "Bearer " + jwtUtil.generateToken(otherBuyer.getEmail(), otherBuyer.getRole().name());

        ProductCategory cat = categoryRepository.save(
                ProductCategory.builder().name("CartTestFood").isActive(true).build());

        product = productRepository.save(Product.builder()
                .category(cat).createdBy(admin)
                .name("Test Product").description("desc")
                .price(new BigDecimal("15.00")).stockQuantity(20)
                .isAvailable(true).build());
    }

    // ── GET /api/store/cart ───────────────────────────────────

    @Test
    void GET_cart_returns_401_without_token() throws Exception {
        mockMvc.perform(get("/api/store/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    void GET_cart_returns_empty_cart_for_new_user() throws Exception {
        mockMvc.perform(get("/api/store/cart").header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    // ── POST /api/store/cart ──────────────────────────────────

    @Test
    void POST_cart_adds_product_and_returns_updated_cart() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(2);

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Test Product"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(30.0))
                .andExpect(jsonPath("$.subtotal").value(30.0));
    }

    @Test
    void POST_cart_increments_quantity_on_second_add() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void POST_cart_returns_400_for_quantity_zero() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(0);

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_cart_returns_400_for_insufficient_stock() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(9999);

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_cart_returns_404_for_unknown_product() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(99999L);
        req.setQuantity(1);

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void POST_cart_returns_400_for_unavailable_product() throws Exception {
        product.setIsAvailable(false);
        productRepository.save(product);

        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/store/cart/{itemId} ──────────────────────────

    @Test
    void PUT_cart_item_updates_quantity() throws Exception {
        // Add first
        AddToCartRequest addReq = new AddToCartRequest();
        addReq.setProductId(product.getId());
        addReq.setQuantity(1);
        MvcResult addResult = mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andReturn();

        Long itemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .path("items").get(0).path("id").asLong();

        // Update
        UpdateCartItemRequest updateReq = new UpdateCartItemRequest();
        updateReq.setQuantity(5);
        mockMvc.perform(put("/api/store/cart/{id}", itemId)
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].lineTotal").value(75.0));
    }

    @Test
    void PUT_cart_item_returns_404_for_other_users_item() throws Exception {
        // Other buyer adds an item
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);
        MvcResult result = mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long otherItemId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("items").get(0).path("id").asLong();

        // First buyer tries to update other buyer's item
        UpdateCartItemRequest updateReq = new UpdateCartItemRequest();
        updateReq.setQuantity(3);
        mockMvc.perform(put("/api/store/cart/{id}", otherItemId)
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/store/cart/{itemId} ───────────────────────

    @Test
    void DELETE_cart_item_removes_it() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);
        MvcResult addResult = mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long itemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .path("items").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/store/cart/{id}", itemId)
                        .header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void DELETE_cart_item_returns_404_for_other_users_item() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);
        MvcResult result = mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long otherItemId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("items").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/store/cart/{id}", otherItemId)
                        .header("Authorization", buyerToken))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/store/cart ────────────────────────────────

    @Test
    void DELETE_cart_clears_all_items() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);
        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/store/cart").header("Authorization", buyerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/store/cart").header("Authorization", buyerToken))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ── Shipping calculation ──────────────────────────────────

    @Test
    void GET_cart_applies_shipping_fee_below_threshold() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(2); // 2 × 15.00 = 30.00 < 50.00

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.shippingFee").value(5.0))
                .andExpect(jsonPath("$.total").value(35.0));
    }

    @Test
    void GET_cart_free_shipping_above_threshold() throws Exception {
        product.setStockQuantity(100);
        productRepository.save(product);

        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(product.getId());
        req.setQuantity(4); // 4 × 15.00 = 60.00 ≥ 50.00

        mockMvc.perform(post("/api/store/cart")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.shippingFee").value(0.0))
                .andExpect(jsonPath("$.total").value(60.0));
    }
}
