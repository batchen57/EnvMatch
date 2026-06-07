package com.envmatch.repository;

import com.envmatch.model.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, String> {
    List<PromptTemplate> findAllByOrderByCreatedAtDesc();
}
