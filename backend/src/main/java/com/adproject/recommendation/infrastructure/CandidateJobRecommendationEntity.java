package com.adproject.recommendation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "candidate_job_recommendations")
public class CandidateJobRecommendationEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String candidateId;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String jobId;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false, length = 16)
    private String source;
    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;
    @Column(name = "feature_version", nullable = false, length = 100)
    private String featureVersion;
    @Column(name = "strong_matches_json", nullable = false, columnDefinition = "TEXT")
    private String strongMatchesJson;
    @Column(name = "gaps_json", nullable = false, columnDefinition = "TEXT")
    private String gapsJson;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;
    @Column(name = "resume_version", nullable = false)
    private int resumeVersion;
    @Column(name = "preference_version", nullable = false)
    private int preferenceVersion;
    @Column(name = "job_version", nullable = false)
    private int jobVersion;
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected CandidateJobRecommendationEntity() {}

    public CandidateJobRecommendationEntity(
            String id, String candidateId, String jobId, int score, String source,
            String modelVersion, String featureVersion, String strongMatchesJson,
            String gapsJson, String evidenceJson, int resumeVersion, int preferenceVersion,
            int jobVersion, Instant generatedAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.jobId = jobId;
        replace(score, source, modelVersion, featureVersion, strongMatchesJson, gapsJson,
                evidenceJson, resumeVersion, preferenceVersion, jobVersion, generatedAt);
    }

    public void replace(
            int score, String source, String modelVersion, String featureVersion,
            String strongMatchesJson, String gapsJson, String evidenceJson,
            int resumeVersion, int preferenceVersion, int jobVersion, Instant generatedAt) {
        this.score = score;
        this.source = source;
        this.modelVersion = modelVersion;
        this.featureVersion = featureVersion;
        this.strongMatchesJson = strongMatchesJson;
        this.gapsJson = gapsJson;
        this.evidenceJson = evidenceJson;
        this.resumeVersion = resumeVersion;
        this.preferenceVersion = preferenceVersion;
        this.jobVersion = jobVersion;
        this.generatedAt = generatedAt;
    }

    public String getCandidateId() { return candidateId; }
    public String getJobId() { return jobId; }
    public int getScore() { return score; }
    public String getSource() { return source; }
    public String getModelVersion() { return modelVersion; }
    public String getFeatureVersion() { return featureVersion; }
    public String getStrongMatchesJson() { return strongMatchesJson; }
    public String getGapsJson() { return gapsJson; }
    public String getEvidenceJson() { return evidenceJson; }
    public int getResumeVersion() { return resumeVersion; }
    public int getPreferenceVersion() { return preferenceVersion; }
    public int getJobVersion() { return jobVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
}
