package com.pawly.dto.store;

import com.pawly.entity.Order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotNull(message = "Payment method is required")
    private Order.PaymentMethod paymentMethod;

    @NotBlank(message = "Shipping address is required")
    @Size(max = 500, message = "Shipping address must not exceed 500 characters")
    private String shippingAddress;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String shippingCity;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String shippingCountry;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}
