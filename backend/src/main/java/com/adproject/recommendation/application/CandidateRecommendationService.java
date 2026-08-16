package com.adproject.recommendation.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.recommendation.api.RecommendationDtos.MatchAnalysis;
import com.adproject.recommendation.api.RecommendationDtos.RecommendationMeta;
import com.adproject.recommendation.api.RecommendationDtos.RecommendedJob;
import com.adproject.recommendation.api.RecommendationDtos.RecommendedJobResponse;
import com.adproject.recommendation.application.MlRecommendationClient.MlCandidate;
import com.adproject.recommendation.application.MlRecommendationClient.MlItem;
import com.adproject.recommendation.application.MlRecommendationClient.MlJob;
import com.adproject.recommendation.application.MlRecommendationClient.MlPreferences;
import com.adproject.recommendation.application.MlRecommendationClient.MlResponse;
import com.adproject.recommendation.application.MlRecommendationClient.MlSalary;
import com.adproject.recommendation.application.RecommendationSnapshotService.SnapshotInput;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceEntity;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateRecommendationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CandidateRecommendationService.class);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final Pattern REQUIRED_YEARS = Pattern.compile("(?i)\\b(\\d{1,2})\\+?\\s+years?\\b");

    private final ResumeRepository resumes;
    private final CandidateJobPreferenceRepository preferences;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final MlRecommendationClient mlClient;
    private final RecommendationSnapshotService snapshots;
    private final RecommendationProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CandidateRecommendationService(
            ResumeRepository resumes,
            CandidateJobPreferenceRepository preferences,
            JobRepository jobs,
            CompanyRepository companies,
            MlRecommendationClient mlClient,
            RecommendationSnapshotService snapshots,
            RecommendationProperties properties,
            ObjectMapper mapper,
            Clock clock) {
        this.resumes = resumes;
        this.preferences = preferences;
        this.jobs = jobs;
        this.companies = companies;
        this.mlClient = mlClient;
        this.snapshots = snapshots;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    public RecommendedJobResponse recommendJobs(AuthenticatedUser principal, int limit) {
        CandidateJobPreferenceService.requireCandidate(principal);
        Input input = loadInput(principal.userId());
        if (input.jobs().isEmpty()) {
            return new RecommendedJobResponse(List.of(), new RecommendationMeta(
                    "NONE", "none", "none", "NOT_APPLICABLE", 0, clock.instant(), limit));
        }

        Result result;
        try {
            result = fromModel(input, limit);
        } catch (RuntimeException exception) {
            LOGGER.warn("Recommendation model unavailable; using deterministic fallback ({})",
                    exception.getClass().getSimpleName());
            result = fallback(input, limit);
        }

        snapshots.save(principal.userId(), input.resume().getVersion(), input.preferenceVersion(),
                result.source(), result.modelVersion(), result.featureVersion(), result.generatedAt(),
                result.values().stream().map(value ->
                        new SnapshotInput(value.job(), value.score(), value.analysis())).toList());

        List<RecommendedJob> data = new ArrayList<>();
        for (int index = 0; index < result.values().size(); index++) {
            ScoredJob value = result.values().get(index);
            CompanyEntity company = input.companies().get(value.job().getCompanyId());
            data.add(toDto(value, company, index + 1));
        }
        return new RecommendedJobResponse(data, new RecommendationMeta(
                result.source(), result.modelVersion(), result.featureVersion(),
                result.source().equals("MODEL") ? "ACTIVE" : "DEGRADED",
                result.inferenceMs(), result.generatedAt(), limit));
    }

    @Transactional(readOnly = true)
    Input loadInput(String candidateId) {
        ResumeEntity resume = resumes.findByCandidateId(candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "RESUME_REQUIRED", "Create a resume before requesting recommendations"));
        CandidateJobPreferenceEntity preference = preferences.findById(candidateId).orElse(null);
        int maxJobs = Math.max(1, Math.min(500, properties.getMaxJobs()));
        var page = jobs.findAll(visibleJobs(), PageRequest.of(0, maxJobs,
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        Map<String, CompanyEntity> companyMap = companies.findAllById(page.getContent().stream()
                        .map(JobEntity::getCompanyId).distinct().toList()).stream()
                .collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        List<JobEntity> visible = page.getContent().stream()
                .filter(job -> companyMap.containsKey(job.getCompanyId())).toList();
        return new Input(resume, preference, preference == null ? 0 : preference.getVersion(),
                visible, companyMap);
    }

    private Result fromModel(Input input, int limit) {
        MlCandidate candidate = toCandidate(input.resume(), input.preference());
        List<MlJob> mlJobs = input.jobs().stream().map(this::toMlJob).toList();
        MlResponse response = mlClient.recommendJobs(candidate, mlJobs,
                Math.min(limit, mlJobs.size()));
        Map<String, JobEntity> jobsById = input.jobs().stream()
                .collect(Collectors.toMap(JobEntity::getId, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<ScoredJob> values = new ArrayList<>();
        for (MlItem item : response.items()) {
            JobEntity job = jobsById.get(item.entityId());
            if (job == null || !seen.add(item.entityId())) continue;
            int score = Math.max(0, Math.min(100, item.score()));
            values.add(new ScoredJob(job, score, new MatchAnalysis(
                    safe(item.strongMatches()), safe(item.gaps()), safe(item.evidence()))));
            if (values.size() == limit) break;
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("ML recommendation service returned no known jobs");
        }
        return new Result("MODEL", response.modelVersion(), response.featureVersion(),
                response.inferenceMs(), response.generatedAt(), values);
    }

    private Result fallback(Input input, int limit) {
        PreferenceData preference = preferenceData(input.preference(), input.resume());
        Set<String> candidateSkills = normalized(readStrings(input.resume().getSkillsJson()));
        List<ScoredJob> ranked = input.jobs().stream()
                .map(job -> fallbackScore(job, preference, candidateSkills))
                .sorted(Comparator.comparingInt(ScoredJob::score).reversed()
                        .thenComparing(value -> value.job().getId()))
                .limit(limit)
                .toList();
        return new Result("FALLBACK", "fallback-rules-v1", "pair-features-v1", 0,
                clock.instant(), ranked);
    }

    private ScoredJob fallbackScore(
            JobEntity job, PreferenceData preference, Set<String> candidateSkills) {
        Set<String> jobSkills = normalized(readStrings(job.getSkillsJson()));
        Set<String> matched = new LinkedHashSet<>(jobSkills);
        matched.retainAll(candidateSkills);
        Set<String> missing = new LinkedHashSet<>(jobSkills);
        missing.removeAll(candidateSkills);
        double skillCoverage = jobSkills.isEmpty() ? 0 : (double) matched.size() / jobSkills.size();
        boolean title = preference.desiredTitles().stream().anyMatch(value ->
                containsEither(value, job.getTitle()));
        boolean location = preference.locations().stream().anyMatch(value ->
                containsEither(value, job.getLocation()));
        boolean workplace = preference.workplaces().contains(job.getWorkplaceType().name());
        boolean employment = preference.employments().contains(job.getEmploymentType().name());
        boolean salary = preference.minimumSalary() != null
                && preference.minimumSalary() <= job.getSalaryMax();
        int score = (int) Math.round(skillCoverage * 45
                + (title ? 25 : 0)
                + (location ? 10 : 0)
                + (workplace ? 10 : 0)
                + (employment ? 5 : 0)
                + (salary ? 5 : 0));
        List<String> strong = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        if (!matched.isEmpty()) strong.add("Skills matched: " + matched.size() + " of " + jobSkills.size());
        if (title) strong.add("Desired role aligns with the job title");
        if (location) strong.add("Preferred location matched");
        if (!missing.isEmpty()) gaps.add("Missing listed skills: " + String.join(", ", missing.stream().limit(3).toList()));
        if (!preference.locations().isEmpty() && !location) gaps.add("Location does not match the stated preference");
        if (!preference.workplaces().isEmpty() && !workplace) gaps.add("Workplace type does not match the stated preference");
        return new ScoredJob(job, Math.max(0, Math.min(100, score)),
                new MatchAnalysis(strong.stream().limit(3).toList(), gaps.stream().limit(3).toList(),
                        matched.stream().limit(5).toList()));
    }

    private MlCandidate toCandidate(ResumeEntity resume, CandidateJobPreferenceEntity entity) {
        PreferenceData preference = preferenceData(entity, resume);
        MlSalary salary = preference.minimumSalary() == null ? null
                : new MlSalary(preference.minimumSalary(), null,
                preference.salaryCurrency(), preference.salaryPeriod());
        return new MlCandidate(resume.getCandidateId(),
                resume.getSummary() + " " + resume.getExperiencesJson(), resume.getHeadline(),
                readStrings(resume.getSkillsJson()), null,
                new MlPreferences(preference.desiredTitles(), preference.locations(),
                        preference.workplaces(), preference.employments(), salary));
    }

    private MlJob toMlJob(JobEntity job) {
        List<String> requirements = readStrings(job.getRequirementsJson());
        return new MlJob(job.getId(), job.getTitle(), job.getDescription(), requirements,
                readStrings(job.getSkillsJson()), job.getLocation(), job.getWorkplaceType().name(),
                job.getEmploymentType().name(), new MlSalary(job.getSalaryMin(), job.getSalaryMax(),
                job.getSalaryCurrency().name(), job.getSalaryPeriod().name()),
                requiredYears(job.getDescription() + " " + String.join(" ", requirements)));
    }

    private PreferenceData preferenceData(
            CandidateJobPreferenceEntity entity, ResumeEntity resume) {
        if (entity == null) {
            return new PreferenceData(List.of(), List.of(resume.getLocation()), List.of(),
                    List.of(), null, "SGD", "MONTH");
        }
        return new PreferenceData(readStrings(entity.getDesiredTitlesJson()),
                readStrings(entity.getPreferredLocationsJson()),
                readStrings(entity.getWorkplaceTypesJson()),
                readStrings(entity.getEmploymentTypesJson()), entity.getMinimumSalary(),
                entity.getSalaryCurrency().name(), entity.getSalaryPeriod().name());
    }

    private RecommendedJob toDto(ScoredJob value, CompanyEntity company, int rank) {
        JobEntity job = value.job();
        return new RecommendedJob(job.getId(), job.getTitle(), company.getId(), company.getName(),
                job.getLocation(), job.getEmploymentType(), job.getWorkplaceType(),
                job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency(), job.getSalaryPeriod(),
                job.getDescription(), readStrings(job.getSkillsJson()), value.score(), rank,
                value.analysis());
    }

    private List<String> readStrings(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(value, STRINGS);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored list field is invalid", exception);
        }
    }

    private static Set<String> normalized(List<String> values) {
        return values.stream().map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean containsEither(String left, String right) {
        String a = left.toLowerCase(Locale.ROOT);
        String b = right.toLowerCase(Locale.ROOT);
        return a.contains(b) || b.contains(a);
    }

    private static Double requiredYears(String text) {
        Matcher matcher = REQUIRED_YEARS.matcher(text);
        int maximum = -1;
        while (matcher.find()) maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
        return maximum < 0 ? null : (double) maximum;
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static Specification<JobEntity> visibleJobs() {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("status"), JobStatus.ACTIVE),
                builder.equal(root.get("visibility"), Visibility.PUBLIC));
    }

    record Input(
            ResumeEntity resume,
            CandidateJobPreferenceEntity preference,
            int preferenceVersion,
            List<JobEntity> jobs,
            Map<String, CompanyEntity> companies) {}
    record PreferenceData(
            List<String> desiredTitles,
            List<String> locations,
            List<String> workplaces,
            List<String> employments,
            Long minimumSalary,
            String salaryCurrency,
            String salaryPeriod) {}
    record ScoredJob(JobEntity job, int score, MatchAnalysis analysis) {}
    record Result(
            String source,
            String modelVersion,
            String featureVersion,
            int inferenceMs,
            Instant generatedAt,
            List<ScoredJob> values) {}
}
