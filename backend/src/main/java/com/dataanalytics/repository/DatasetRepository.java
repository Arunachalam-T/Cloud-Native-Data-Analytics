package com.dataanalytics.repository;

import com.dataanalytics.model.entity.Dataset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Page<Dataset> findByStatusNotOrderByCreatedAtDesc(Dataset.DatasetStatus status, Pageable pageable);

    List<Dataset> findByStatusOrderByCreatedAtDesc(Dataset.DatasetStatus status);

    Optional<Dataset> findByS3Key(String s3Key);

    @Query("SELECT d FROM Dataset d WHERE d.status != 'DELETED' AND " +
           "LOWER(d.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Dataset> searchByName(@Param("query") String query);

    long countByStatus(Dataset.DatasetStatus status);
}
