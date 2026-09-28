package com.example.expense_tracker.repository;

import com.example.expense_tracker.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIdOrUserIsNull(UUID userId);
    List<Category> findByUserId(UUID userId);

    @Query("SELECT c FROM Category c WHERE LOWER(c.name) = LOWER(:name) AND (c.user.id = :userId OR c.user IS NULL)")
    Optional<Category> findByNameIgnoreCaseAndUser(@Param("name") String name, @Param("userId") UUID userId);
}
