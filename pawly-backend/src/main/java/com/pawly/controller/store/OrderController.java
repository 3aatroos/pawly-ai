package com.pawly.controller.store;

import com.pawly.dto.store.CreateOrderRequest;
import com.pawly.dto.store.OrderDTO;
import com.pawly.dto.store.UpdateOrderStatusRequest;
import com.pawly.entity.Order;
import com.pawly.service.store.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ── Create — any authenticated user ──────────────────────

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDTO> create(
            @Valid @RequestBody CreateOrderRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(req, principal.getUsername()));
    }

    // ── Buyer history ─────────────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<OrderDTO>> myOrders(
            @AuthenticationPrincipal UserDetails principal,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.findMyOrders(principal.getUsername(), pageable));
    }

    @GetMapping("/my/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDTO> myOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(orderService.findMyOrderById(orderId, principal.getUsername()));
    }

    // ── Admin ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Page<OrderDTO>> listAll(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) Long orderId,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(status, orderId, pageable));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderDTO> getById(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.findById(orderId));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderDTO> updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest req) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, req));
    }
}
