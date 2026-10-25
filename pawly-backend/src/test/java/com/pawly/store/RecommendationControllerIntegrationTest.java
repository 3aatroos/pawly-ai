package com.pawly.store;

import com.pawly.entity.Pet;
import com.pawly.entity.Product;
import com.pawly.entity.ProductCategory;
import com.pawly.entity.User;
import com.pawly.repository.PetRepository;
import com.pawly.repository.ProductCategoryRepository;
import com.pawly.repository.ProductRepository;
import com.pawly.repository.RecommendationRepository;
import com.pawly.repository.UserRepository;
import com.pawly.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private PetRepository petRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User owner;
    private String ownerToken;
    private String vetToken;
    private String adminToken;
    private Product dogProduct;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("owner@store.test")
                .passwordHash(passwordEncoder.encode("pass"))
                .firstName("Owner")
                .lastName("User")
                .role(User.Role.PET_OWNER)
                .status(User.UserStatus.ACTIVE)
                .build());
        User vet = userRepository.save(User.builder()
                .email("vet@store.test")
                .passwordHash(passwordEncoder.encode("pass"))
                .firstName("Vet")
                .lastName("User")
                .role(User.Role.VETERINARIAN)
                .status(User.UserStatus.ACTIVE)
                .build());
        User admin = userRepository.save(User.builder()
                .email("admin@store.test")
                .passwordHash(passwordEncoder.encode("pass"))
                .firstName("Admin")
                .lastName("User")
                .role(User.Role.ADMINISTRATOR)
                .status(User.UserStatus.ACTIVE)
                .build());
        ownerToken = "Bearer " + jwtUtil.generateToken(owner.getEmail(), owner.getRole().name());
        vetToken = "Bearer " + jwtUtil.generateToken(vet.getEmail(), vet.getRole().name());
        adminToken = "Bearer " + jwtUtil.generateToken(admin.getEmail(), admin.getRole().name());

        petRepository.save(Pet.builder()
                .owner(owner)
                .name("SecretPetName")
                .species("dog")
                .gender(Pet.PetGender.UNKNOWN)
                .dateOfBirth(LocalDate.of(2010, 3, 15))
                .microchipId("123456789012345")
                .healthInfo("owner@store.test has a private note")
                .build());

        ProductCategory category = categoryRepository.save(
                ProductCategory.builder().name("RecoFood").isActive(true).build());
        dogProduct = productRepository.save(Product.builder()
                .category(category)
                .createdBy(vet)
                .name("Dog Kibble")
                .description("Complete food for adult dogs")
                .price(new BigDecimal("19.99"))
                .stockQuantity(10)
                .isAvailable(true)
                .targetSpecies("dog")
                .averageRating(new BigDecimal("5.00"))
                .reviewCount(4)
                .build());
        productRepository.save(Product.builder()
                .category(category)
                .createdBy(vet)
                .name("Cat Litter")
                .description("Litter for cats")
                .price(new BigDecimal("12.00"))
                .stockQuantity(8)
                .isAvailable(true)
                .targetSpecies("cat")
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build());
    }

    @Test
    void recommendations_require_authentication() throws Exception {
        mockMvc.perform(get("/api/store/recommendations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarian_and_administrator_can_request_recommendations() throws Exception {
        mockMvc.perform(get("/api/store/recommendations").header("Authorization", vetToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/store/recommendations").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("content_based"));
    }

    @Test
    void authenticated_owner_receives_species_match_without_personal_data() throws Exception {
        mockMvc.perform(get("/api/store/recommendations").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(dogProduct.getId()))
                .andExpect(jsonPath("$[0].name").value("Dog Kibble"))
                .andExpect(jsonPath("$[0].source").value("content_based"))
                .andExpect(jsonPath("$[0].score").isNumber())
                .andExpect(jsonPath("$[0].reason").exists())
                .andExpect(jsonPath("$[0].stockQuantity").doesNotExist())
                .andExpect(content().string(not(containsString("SecretPetName"))))
                .andExpect(content().string(not(containsString("owner@store.test"))))
                .andExpect(content().string(not(containsString("123456789012345"))))
                .andExpect(content().string(not(containsString("private note"))));

        var stored = recommendationRepository.findByUserIdOrderByScoreDesc(owner.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10));
        org.junit.jupiter.api.Assertions.assertFalse(stored.isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(dogProduct.getId(), stored.get(0).getProduct().getId());
    }
}
