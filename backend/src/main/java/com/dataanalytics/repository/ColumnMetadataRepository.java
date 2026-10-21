package com.dataanalytics.repository;

import com.dataanalytics.model.entity.ColumnMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColumnMetadataRepository extends JpaRepository<ColumnMetadata, Long> {

    List<ColumnMetadata> findByDatasetIdOrderByColumnIndex(Long datasetId);

    void deleteByDatasetId(Long datasetId);
}
