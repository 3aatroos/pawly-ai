package com.pawly.config;

import com.pawly.entity.ProductCategory;
import com.pawly.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ProductCategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            return; // already seeded
        }

        List<ProductCategory> defaults = List.of(
                build("Food",        "Pet food and treats"),
                build("Accessories", "Collars, leashes and carriers"),
                build("Toys",        "Toys and enrichment"),
                build("Health",      "Vitamins and supplements"),
                build("Grooming",    "Brushes, shampoos and tools")
        );

        categoryRepository.saveAll(defaults);
        log.info("Seeded {} default product categories.", defaults.size());
    }

    private static ProductCategory build(String name, String description) {
        return ProductCategory.builder()
                .name(name)
                .description(description)
                .isActive(true)
                .build();
    }
}
