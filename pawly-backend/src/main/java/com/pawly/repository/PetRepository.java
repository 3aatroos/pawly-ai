package com.pawly.repository;

import com.pawly.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByOwnerId(Long ownerId);

    Optional<Pet> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    List<Pet> findByAssignedVetId(Long vetProfileId);
}
