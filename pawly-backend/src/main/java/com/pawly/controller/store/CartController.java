package com.pawly.controller.store;

import com.pawly.dto.store.AddToCartRequest;
import com.pawly.dto.store.CartDTO;
import com.pawly.dto.store.UpdateCartItemRequest;
import com.pawly.service.store.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * All endpoints require authentication — the cart is strictly personal.
 * Ownership is enforced in CartService via findByIdAndUserId, not by trusting
 * any client-supplied user identifier.
 */
@RestController
@RequestMapping("/api/store/cart")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartDTO> getCart(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(cartService.getCart(principal.getUsername()));
    }

    @PostMapping
    public ResponseEntity<CartDTO> addItem(
            @Valid @RequestBody AddToCartRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(cartService.addItem(req, principal.getUsername()));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<CartDTO> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                cartService.updateItem(itemId, req.getQuantity(), principal.getUsername()));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<CartDTO> removeItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(cartService.removeItem(itemId, principal.getUsername()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserDetails principal) {
        cartService.clearCart(principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
