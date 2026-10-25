package com.pawly.repository;

import com.pawly.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT p FROM Product p
            WHERE (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:species    IS NULL OR p.targetSpecies = :species OR p.targetSpecies = 'all')
              AND (:available  IS NULL OR p.isAvailable = :available)
              AND (:minPrice   IS NULL OR p.price >= :minPrice)
              AND (:maxPrice   IS NULL OR p.price <= :maxPrice)
              AND (:search     IS NULL
                   OR LOWER(p.name)        LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> findAllFiltered(
            @Param("categoryId") Long categoryId,
            @Param("species")    String species,
            @Param("available")  Boolean available,
            @Param("minPrice")   BigDecimal minPrice,
            @Param("maxPrice")   BigDecimal maxPrice,
            @Param("search")     String search,
            Pageable pageable
    );

    List<Product> findByCategoryIdAndIsAvailableTrue(Long categoryId);

    List<Product> findByCreatedByIdAndIsAvailableTrue(Long userId);

    List<Product> findByTargetSpeciesInAndIsAvailableTrue(List<String> species);

    Page<Product> findByCreatedById(Long userId, Pageable pageable);

    // Verrou optimiste pour décrémentation de stock
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty WHERE p.id = :id AND p.stockQuantity >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    // Mise à jour de la note moyenne après chaque avis
    @Modifying
    @Transactional
    @Query("""
            UPDATE Product p
            SET p.averageRating = (SELECT COALESCE(AVG(CAST(r.rating AS double)), 0)
                                   FROM ProductReview r WHERE r.product.id = :id),
                p.reviewCount   = (SELECT COUNT(r) FROM ProductReview r WHERE r.product.id = :id)
            WHERE p.id = :id
            """)
    void refreshRating(@Param("id") Long id);

    Optional<Product> findByIdAndIsAvailableTrue(Long id);

    List<Product> findByIsAvailableTrue();

    @Query("""
            SELECT p FROM Product p
            WHERE p.isAvailable = true
            ORDER BY p.averageRating DESC, p.reviewCount DESC
            """)
    List<Product> findFeatured(Pageable pageable);
}
