package com.envmatch.repository;

import com.envmatch.model.AIModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AIModelRepository extends JpaRepository<AIModel, String> {
    Optional<AIModel> findFirstByIdentifier(String identifier);
    List<AIModel> findAllByOrderBySortOrderAscCreatedAtDesc();
    List<AIModel> findByIsDefault(String isDefault);
}
