package com.pawly.controller.store;

import com.pawly.dto.store.*;
import com.pawly.entity.User;
import com.pawly.service.store.ProductService;
import com.pawly.service.store.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/store/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;

    // ── Catalogue — public ────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<ProductSummaryDTO>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @AuthenticationPrincipal UserDetails principal,
            Pageable pageable) {

        boolean includeStock = hasStockAccess(principal);
        // Public browsing shows only available products; VET/ADMIN see all
        Boolean available = hasStockAccess(principal) ? null : true;

        Page<ProductSummaryDTO> page = productService.findAll(
                categoryId, species, available, minPrice, maxPrice, search, includeStock, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/featured")
    public ResponseEntity<List<ProductSummaryDTO>> featured(
            @RequestParam(defaultValue = "8") int limit,
            @AuthenticationPrincipal UserDetails principal) {

        boolean includeStock = hasStockAccess(principal);
        return ResponseEntity.ok(
                productService.findFeatured(includeStock, PageRequest.of(0, limit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    // ── Write — VETERINARIAN + ADMINISTRATOR ──────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public ResponseEntity<ProductDTO> create(
            @Valid @RequestBody CreateProductRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(req, principal.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public ResponseEntity<ProductDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        boolean isAdmin = isAdmin(principal);
        return ResponseEntity.ok(productService.update(id, req, principal.getUsername(), isAdmin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        productService.delete(id, principal.getUsername(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public ResponseEntity<ProductDTO> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(
                productService.updateStock(id, req.getStockQuantity(), principal.getUsername(), isAdmin(principal)));
    }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public ResponseEntity<ProductDTO> updateAvailability(
            @PathVariable Long id,
            @RequestParam boolean available,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(
                productService.updateAvailability(id, available, principal.getUsername(), isAdmin(principal)));
    }

    // ── Reviews — authenticated ───────────────────────────────

    @GetMapping("/{id}/reviews")
    public ResponseEntity<Page<ReviewDTO>> listReviews(
            @PathVariable Long id,
            Pageable pageable) {

        return ResponseEntity.ok(reviewService.findByProduct(id, pageable));
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewDTO> addReview(
            @PathVariable Long id,
            @Valid @RequestBody ReviewDTO dto,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(id, dto, principal.getUsername()));
    }

    @PutMapping("/{id}/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewDTO> updateReview(
            @PathVariable Long id,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewDTO dto,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(
                reviewService.update(id, reviewId, dto, principal.getUsername(), isAdmin(principal)));
    }

    @DeleteMapping("/{id}/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long id,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal UserDetails principal) {

        reviewService.delete(id, reviewId, principal.getUsername(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Role is read exclusively from the Spring Security context (JWT-derived authority).
     * Angular never sends the role — only the JWT Bearer token is trusted.
     */
    private boolean hasStockAccess(UserDetails principal) {
        if (principal == null) return false;
        return principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VETERINARIAN")
                            || a.getAuthority().equals("ROLE_ADMINISTRATOR"));
    }

    private boolean isAdmin(UserDetails principal) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));
    }
}
