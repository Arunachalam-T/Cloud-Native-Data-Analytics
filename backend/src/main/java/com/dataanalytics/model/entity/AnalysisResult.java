package com.dataanalytics.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "analysis_results",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_id", "result_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "result_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ResultType resultType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultData;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    @PrePersist
    protected void onCreate() {
        computedAt = OffsetDateTime.now();
    }

    public enum ResultType {
        EDA, QUALITY, STATS, VISUALIZATION
    }
}
