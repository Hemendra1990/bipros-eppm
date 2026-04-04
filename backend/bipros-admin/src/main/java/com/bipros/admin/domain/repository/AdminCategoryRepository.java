package com.bipros.admin.domain.repository;

import com.bipros.admin.domain.model.AdminCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminCategoryRepository extends JpaRepository<AdminCategory, UUID> {
    List<AdminCategory> findByCategoryType(String categoryType);

    List<AdminCategory> findByParentId(UUID parentId);
}
