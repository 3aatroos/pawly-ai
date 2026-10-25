package com.pawly.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.store.CreateOrderRequest;
import com.pawly.dto.store.UpdateOrderStatusRequest;
import com.pawly.entity.*;
import com.pawly.repository.*;
import com.pawly.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User buyer;
    private User otherBuyer;
    private User admin;
    private Product product;
    private String buyerToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder()
                .email("buyer@order.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Buyer").lastName("Order")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        otherBuyer = userRepository.save(User.builder()
                .email("other@order.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Other").lastName("Order")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        admin = userRepository.save(User.builder()
                .email("admin@order.test").passwordHash(passwordEncoder.encode("pass"))
                .firstName("Admin").lastName("Order")
                .role(User.Role.ADMINISTRATOR).status(User.UserStatus.ACTIVE).build());

        buyerToken = "Bearer " + jwtUtil.generateToken(buyer.getEmail(), buyer.getRole().name());
        otherToken = "Bearer " + jwtUtil.generateToken(otherBuyer.getEmail(), otherBuyer.getRole().name());
        adminToken = "Bearer " + jwtUtil.generateToken(admin.getEmail(), admin.getRole().name());

        ProductCategory cat = categoryRepository.save(
                ProductCategory.builder().name("OrderTestFood").isActive(true).build());

        product = productRepository.save(Product.builder()
                .category(cat).createdBy(admin)
                .name("Order Test Product").description("desc")
                .price(new BigDecimal("30.00")).stockQuantity(50)
                .isAvailable(true).build());

        // Add product to buyer's cart
        cartItemRepository.save(CartItem.builder()
                .user(buyer).product(product).quantity(2).build());
    }

    // ── POST /api/store/orders ────────────────────────────────

    @Test
    void POST_order_requires_authentication() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        mockMvc.perform(post("/api/store/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_order_creates_CONFIRMED_order_and_clears_cart() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);

        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Order Test Product"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.shippingFee").value(0.0))
                .andExpect(jsonPath("$.totalAmount").value(60.0));

        // Cart should be empty after order
        mockMvc.perform(get("/api/store/cart").header("Authorization", buyerToken))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void POST_order_decrements_product_stock() throws Exception {
        int stockBefore = product.getStockQuantity();
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);

        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(stockBefore - 2);
    }

    @Test
    void POST_order_returns_400_for_empty_cart() throws Exception {
        cartItemRepository.deleteAllByUserId(buyer.getId());
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);

        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_order_returns_400_when_address_missing() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY);
        // shippingAddress intentionally omitted

        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.shippingAddress").exists());
    }

    @Test
    void POST_order_returns_400_when_product_unavailable() throws Exception {
        product.setIsAvailable(false);
        productRepository.save(product);

        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/store/orders/my ──────────────────────────────

    @Test
    void GET_my_orders_returns_only_caller_orders() throws Exception {
        // Place an order for buyer
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/store/orders/my").header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.content[0].buyerEmail").value(buyer.getEmail()));
    }

    @Test
    void GET_my_orders_returns_empty_for_user_with_no_orders() throws Exception {
        mockMvc.perform(get("/api/store/orders/my").header("Authorization", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ── GET /api/store/orders/my/{id} ─────────────────────────

    @Test
    void GET_my_order_by_id_returns_order_detail() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        MvcResult createResult = mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asLong();

        mockMvc.perform(get("/api/store/orders/my/{id}", orderId)
                        .header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.payment").exists());
    }

    @Test
    void GET_my_order_by_id_returns_404_for_other_buyers_order() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        MvcResult createResult = mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asLong();

        mockMvc.perform(get("/api/store/orders/my/{id}", orderId)
                        .header("Authorization", otherToken))
                .andExpect(status().isNotFound());
    }

    // ── Admin — GET /api/store/orders ─────────────────────────

    @Test
    void GET_all_orders_requires_admin() throws Exception {
        mockMvc.perform(get("/api/store/orders").header("Authorization", buyerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void GET_all_orders_admin_sees_all() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/store/orders").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThan(0))));
    }

    @Test
    void GET_all_orders_filters_by_status() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/store/orders")
                        .param("status", "CONFIRMED")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/store/orders")
                        .param("status", "DELIVERED")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ── Admin — PATCH status ──────────────────────────────────

    @Test
    void PATCH_status_transitions_order() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        MvcResult createResult = mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asLong();

        UpdateOrderStatusRequest statusReq = new UpdateOrderStatusRequest();
        statusReq.setStatus(Order.OrderStatus.PREPARING);

        mockMvc.perform(patch("/api/store/orders/{id}/status", orderId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    void PATCH_status_returns_403_for_non_admin() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        MvcResult createResult = mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asLong();

        UpdateOrderStatusRequest statusReq = new UpdateOrderStatusRequest();
        statusReq.setStatus(Order.OrderStatus.SHIPPED);

        mockMvc.perform(patch("/api/store/orders/{id}/status", orderId)
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void PATCH_status_returns_400_for_terminal_DELIVERED_order() throws Exception {
        CreateOrderRequest req = buildOrderRequest(Order.PaymentMethod.CASH_ON_DELIVERY);
        MvcResult createResult = mockMvc.perform(post("/api/store/orders")
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asLong();

        // Advance to DELIVERED
        for (Order.OrderStatus s : new Order.OrderStatus[]{
                Order.OrderStatus.PREPARING, Order.OrderStatus.SHIPPED,
                Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.OUT_FOR_DELIVERY,
                Order.OrderStatus.DELIVERED}) {
            UpdateOrderStatusRequest statusReq = new UpdateOrderStatusRequest();
            statusReq.setStatus(s);
            mockMvc.perform(patch("/api/store/orders/{id}/status", orderId)
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusReq)))
                    .andExpect(status().isOk());
        }

        // Now try to change from DELIVERED
        UpdateOrderStatusRequest statusReq = new UpdateOrderStatusRequest();
        statusReq.setStatus(Order.OrderStatus.CANCELLED);
        mockMvc.perform(patch("/api/store/orders/{id}/status", orderId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isBadRequest());
    }

    // ── Helper ────────────────────────────────────────────────

    private CreateOrderRequest buildOrderRequest(Order.PaymentMethod method) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(method);
        req.setShippingAddress("123 Test Street, Tunis");
        req.setShippingCity("Tunis");
        req.setShippingCountry("TN");
        return req;
    }
}
