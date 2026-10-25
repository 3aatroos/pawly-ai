package com.pawly.repository;

import com.pawly.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByTopicAndSpecies(String topic, String species);

    List<KnowledgeChunk> findBySpecies(String species);

    @Query(value = "SELECT * FROM knowledge_chunks WHERE MATCH(content_text) AGAINST(:searchTerm IN BOOLEAN MODE) LIMIT :lim",
           nativeQuery = true)
    List<KnowledgeChunk> searchByContent(@Param("searchTerm") String searchTerm, @Param("lim") int lim);

    List<KnowledgeChunk> findByUrgencyLevel(String urgencyLevel);

    @Query("SELECT k FROM KnowledgeChunk k WHERE k.keywords LIKE %:keyword%")
    List<KnowledgeChunk> findByKeyword(@Param("keyword") String keyword);

    @Query("SELECT k FROM KnowledgeChunk k WHERE k.species = :species OR k.species IS NULL ORDER BY k.urgencyLevel DESC, k.topic ASC")
    List<KnowledgeChunk> findRelevantChunks(@Param("species") String species);
}