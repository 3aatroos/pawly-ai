package com.pawly.repository;

import com.pawly.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByIsActiveTrueOrderByNameAsc();

    Optional<ProductCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
