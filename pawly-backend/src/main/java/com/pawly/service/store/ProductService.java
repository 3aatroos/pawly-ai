package com.pawly.service.store;

import com.pawly.dto.store.*;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.*;
import com.pawly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UserService userService;
    private final CategoryService categoryService;

    // ── Public catalogue ──────────────────────────────────────

    public Page<ProductSummaryDTO> findAll(
            Long categoryId, String species, Boolean available,
            BigDecimal minPrice, BigDecimal maxPrice, String search,
            boolean includeStock, Pageable pageable) {

        return productRepository
                .findAllFiltered(categoryId, species, available, minPrice, maxPrice, search, pageable)
                .map(p -> ProductSummaryDTO.from(p, includeStock));
    }

    public ProductDTO findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return ProductDTO.from(product);
    }

    public List<ProductSummaryDTO> findFeatured(boolean includeStock, Pageable pageable) {
        return productRepository.findFeatured(pageable)
                .stream()
                .map(p -> ProductSummaryDTO.from(p, includeStock))
                .toList();
    }

    // ── Write operations (VET + ADMIN) ────────────────────────

    @Transactional
    public ProductDTO create(CreateProductRequest req, String callerEmail) {
        User creator = userService.getUserByEmail(callerEmail);
        ProductCategory category = categoryService.getCategory(req.getCategoryId());

        Product product = Product.builder()
                .createdBy(creator)
                .category(category)
                .name(req.getName().trim())
                .description(req.getDescription())
                .price(req.getPrice())
                .stockQuantity(req.getStockQuantity() != null ? req.getStockQuantity() : 0)
                .isAvailable(req.getIsAvailable() != null ? req.getIsAvailable() : true)
                .targetSpecies(req.getTargetSpecies())
                .build();

        applyImages(product, req.getImages());
        return ProductDTO.from(productRepository.save(product));
    }

    @Transactional
    public ProductDTO update(Long id, UpdateProductRequest req, String callerEmail, boolean isAdmin) {
        Product product = getProductForWrite(id, callerEmail, isAdmin);

        if (req.getName()          != null) product.setName(req.getName().trim());
        if (req.getDescription()   != null) product.setDescription(req.getDescription());
        if (req.getPrice()         != null) product.setPrice(req.getPrice());
        if (req.getIsAvailable()   != null) product.setIsAvailable(req.getIsAvailable());
        if (req.getTargetSpecies() != null) product.setTargetSpecies(req.getTargetSpecies());
        if (req.getStockQuantity() != null) product.setStockQuantity(req.getStockQuantity());

        if (req.getCategoryId() != null) {
            product.setCategory(categoryService.getCategory(req.getCategoryId()));
        }
        if (req.getImages() != null) {
            product.getImages().clear();
            applyImages(product, req.getImages());
        }

        return ProductDTO.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id, String callerEmail, boolean isAdmin) {
        Product product = getProductForWrite(id, callerEmail, isAdmin);
        productRepository.delete(product);
    }

    @Transactional
    public ProductDTO updateStock(Long id, int quantity, String callerEmail, boolean isAdmin) {
        Product product = getProductForWrite(id, callerEmail, isAdmin);
        product.setStockQuantity(quantity);
        return ProductDTO.from(productRepository.save(product));
    }

    @Transactional
    public ProductDTO updateAvailability(Long id, boolean available, String callerEmail, boolean isAdmin) {
        Product product = getProductForWrite(id, callerEmail, isAdmin);
        product.setIsAvailable(available);
        return ProductDTO.from(productRepository.save(product));
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Ownership check: ADMINISTRATOR can touch any product.
     * VETERINARIAN can only touch products they created.
     */
    private Product getProductForWrite(Long id, String callerEmail, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (!isAdmin) {
            User caller = userService.getUserByEmail(callerEmail);
            if (!product.getCreatedBy().getId().equals(caller.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You can only modify products you created");
            }
        }
        return product;
    }

    private void applyImages(Product product, List<ProductImageDTO> imageDTOs) {
        if (imageDTOs == null || imageDTOs.isEmpty()) return;

        boolean hasPrimary = imageDTOs.stream().anyMatch(dto -> Boolean.TRUE.equals(dto.getIsPrimary()));

        for (int i = 0; i < imageDTOs.size(); i++) {
            ProductImageDTO dto = imageDTOs.get(i);
            boolean isPrimary = hasPrimary
                    ? Boolean.TRUE.equals(dto.getIsPrimary())
                    : (i == 0);
            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .imageUrl(dto.getImageUrl())
                    .isPrimary(isPrimary)
                    .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i)
                    .build());
        }
    }
}
