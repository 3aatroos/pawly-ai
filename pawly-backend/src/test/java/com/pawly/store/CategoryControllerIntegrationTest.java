package com.pawly.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.store.CategoryDTO;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String vetToken;
    private String ownerToken;
    private ProductCategory category;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.builder()
                .email("admin@cat.test").passwordHash(passwordEncoder.encode("p"))
                .firstName("Admin").lastName("Cat")
                .role(User.Role.ADMINISTRATOR).status(User.UserStatus.ACTIVE).build());

        User vet = userRepository.save(User.builder()
                .email("vet@cat.test").passwordHash(passwordEncoder.encode("p"))
                .firstName("Vet").lastName("Cat")
                .role(User.Role.VETERINARIAN).status(User.UserStatus.ACTIVE).build());

        User owner = userRepository.save(User.builder()
                .email("owner@cat.test").passwordHash(passwordEncoder.encode("p"))
                .firstName("Owner").lastName("Cat")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        adminToken = "Bearer " + jwtUtil.generateToken(admin.getEmail(), admin.getRole().name());
        vetToken   = "Bearer " + jwtUtil.generateToken(vet.getEmail(),   vet.getRole().name());
        ownerToken = "Bearer " + jwtUtil.generateToken(owner.getEmail(), owner.getRole().name());

        category = categoryRepository.save(
                ProductCategory.builder().name("CatTestCategory").isActive(true).build());
    }

    @Test
    void GET_categories_is_public() throws Exception {
        mockMvc.perform(get("/api/store/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void GET_categories_all_requires_admin() throws Exception {
        mockMvc.perform(get("/api/store/categories/all")
                        .header("Authorization", vetToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/store/categories/all")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void POST_category_requires_admin() throws Exception {
        CategoryDTO dto = CategoryDTO.builder().name("NewCatByVet").build();

        mockMvc.perform(post("/api/store/categories")
                        .header("Authorization", vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_category_admin_creates_successfully() throws Exception {
        CategoryDTO dto = CategoryDTO.builder().name("UniqueNewCategory").description("desc").build();

        mockMvc.perform(post("/api/store/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("UniqueNewCategory"));
    }

    @Test
    void POST_category_returns_400_for_duplicate_name() throws Exception {
        CategoryDTO dto = CategoryDTO.builder().name("CatTestCategory").build();

        mockMvc.perform(post("/api/store/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PUT_category_updates_name() throws Exception {
        CategoryDTO dto = CategoryDTO.builder().name("Renamed Category").build();

        mockMvc.perform(put("/api/store/categories/{id}", category.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Category"));
    }

    @Test
    void DELETE_category_without_products_succeeds() throws Exception {
        ProductCategory empty = categoryRepository.save(
                ProductCategory.builder().name("EmptyToDelete").isActive(true).build());

        mockMvc.perform(delete("/api/store/categories/{id}", empty.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void POST_category_returns_400_when_name_blank() throws Exception {
        CategoryDTO dto = CategoryDTO.builder().name("").build();

        mockMvc.perform(post("/api/store/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
