package com.pawly.service.store;

import com.pawly.dto.store.CategoryDTO;
import com.pawly.entity.ProductCategory;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;

    public List<CategoryDTO> findAllActive() {
        return categoryRepository.findByIsActiveTrueOrderByNameAsc()
                .stream()
                .map(CategoryDTO::from)
                .toList();
    }

    public List<CategoryDTO> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryDTO::from)
                .toList();
    }

    public CategoryDTO findById(Long id) {
        return CategoryDTO.from(getCategory(id));
    }

    @Transactional
    public CategoryDTO create(CategoryDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("Category already exists: " + dto.getName());
        }
        ProductCategory category = ProductCategory.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .imageUrl(dto.getImageUrl())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        return CategoryDTO.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO update(Long id, CategoryDTO dto) {
        ProductCategory category = getCategory(id);

        if (dto.getName() != null) {
            String trimmed = dto.getName().trim();
            categoryRepository.findByNameIgnoreCase(trimmed)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(dup -> { throw new IllegalArgumentException("Category name already in use: " + trimmed); });
            category.setName(trimmed);
        }
        if (dto.getDescription() != null) category.setDescription(dto.getDescription());
        if (dto.getImageUrl()    != null) category.setImageUrl(dto.getImageUrl());
        if (dto.getIsActive()    != null) category.setIsActive(dto.getIsActive());

        return CategoryDTO.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        ProductCategory category = getCategory(id);
        if (!category.getProducts().isEmpty()) {
            throw new IllegalStateException("Cannot delete category with existing products. Deactivate it instead.");
        }
        categoryRepository.delete(category);
    }

    public ProductCategory getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
