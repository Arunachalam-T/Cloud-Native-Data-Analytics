package com.dataanalytics.repository;

import com.dataanalytics.model.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByDatasetIdAndResultType(Long datasetId, AnalysisResult.ResultType resultType);

    void deleteByDatasetId(Long datasetId);
}
