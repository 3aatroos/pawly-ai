package com.pawly.repository;

import com.pawly.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(User.Role role);

    long countByStatus(User.UserStatus status);

    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> findAllFiltered(
            @Param("role") User.Role role,
            @Param("status") User.UserStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
