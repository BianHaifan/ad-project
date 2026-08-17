package com.adproject.recommendation.application;

import com.adproject.recommendation.api.RecommendationDtos.MatchAnalysis;
import com.adproject.recommendation.application.MlRecommendationClient.MlCandidate;
import com.adproject.recommendation.application.MlRecommendationClient.MlItem;
import com.adproject.recommendation.application.MlRecommendationClient.MlJob;
import com.adproject.recommendation.application.MlRecommendationClient.MlPreferences;
import com.adproject.recommendation.application.MlRecommendationClient.MlResponse;
import com.adproject.recommendation.application.MlRecommendationClient.MlSalary;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reverse ranking engine: scores a set of applicants (resume snapshots) against a single job.
 * It mirrors {@link CandidateRecommendationService} but in the job&rarr;candidates direction and
 * intentionally depends on no application/recruiter repositories &mdash; it only consumes the
 * already-assembled ML input records produced by the application module.
 */
@Service
public class RecruiterApplicantRankingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecruiterApplicantRankingService.class);
    /** Model input ceiling shared with the application assembly guard. */
    public static final int MAX_CANDIDATES = 500;
    /** The Python endpoint returns at most this many ranked items per call. */
    private static final int MAX_MODEL_RESULTS = 100;

    private final MlRecommendationClient mlClient;
    private final Clock clock;

    public RecruiterApplicantRankingService(MlRecommendationClient mlClient, Clock clock) {
        this.mlClient = mlClient;
        this.clock = clock;
    }

    /**
     * Ranks every supplied candidate against {@code job}, returning the full sorted list (stable
     * by {@code score desc, entityId asc}). When the model is unavailable or cannot return a
     * complete ranking, a deterministic rules fallback ranks the full set instead.
     */
    public Result rankCandidates(MlJob job, List<MlCandidate> candidates) {
        try {
            return fromModel(job, candidates);
        } catch (RuntimeException exception) {
            LOGGER.warn("Applicant ranking model unavailable or incomplete; using deterministic fallback ({})",
                    exception.getClass().getSimpleName());
            return fallback(job, candidates);
        }
    }

    private Result fromModel(MlJob job, List<MlCandidate> candidates) {
        int limit = Math.min(candidates.size(), MAX_MODEL_RESULTS);
        MlResponse response = mlClient.recommendCandidates(job, candidates, limit);
        Map<String, MlCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(MlCandidate::entityId, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<ScoredApplicant> values = new ArrayList<>();
        for (MlItem item : response.items()) {
            MlCandidate candidate = byId.get(item.entityId());
            if (candidate == null || !seen.add(item.entityId())) continue;
            int score = Math.max(0, Math.min(100, item.score()));
            values.add(new ScoredApplicant(candidate, score, new MatchAnalysis(
                    safe(item.strongMatches()), safe(item.gaps()), safe(item.evidence()))));
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("ML recommendation service returned no known candidates");
        }
        if (values.size() < candidates.size()) {
            // The model caps its ranked output at MAX_MODEL_RESULTS. A partial result would
            // silently drop applicants, so rank the full set with deterministic rules instead.
            throw new IllegalStateException("ML recommendation returned an incomplete ranking");
        }
        return new Result("MODEL", response.modelVersion(), response.featureVersion(),
                response.inferenceMs(), response.generatedAt(), values);
    }

    private Result fallback(MlJob job, List<MlCandidate> candidates) {
        Set<String> jobSkills = normalized(job.skills());
        List<ScoredApplicant> ranked = candidates.stream()
                .map(candidate -> fallbackScore(job, candidate, jobSkills))
                .sorted(Comparator.comparingInt(ScoredApplicant::score).reversed()
                        .thenComparing(value -> value.candidate().entityId()))
                .toList();
        return new Result("FALLBACK", "fallback-rules-v1", "pair-features-v1", 0,
                clock.instant(), ranked);
    }

    private ScoredApplicant fallbackScore(MlJob job, MlCandidate candidate, Set<String> jobSkills) {
        Set<String> candidateSkills = normalized(candidate.skills());
        Set<String> matched = new LinkedHashSet<>(jobSkills);
        matched.retainAll(candidateSkills);
        Set<String> missing = new LinkedHashSet<>(jobSkills);
        missing.removeAll(candidateSkills);
        double skillCoverage = jobSkills.isEmpty() ? 0 : (double) matched.size() / jobSkills.size();

        MlPreferences prefs = candidate.preferences();
        List<String> desiredTitles = prefs == null || prefs.desiredTitles() == null
                ? List.of() : prefs.desiredTitles();
        List<String> locations = prefs == null || prefs.preferredLocations() == null
                ? List.of() : prefs.preferredLocations();
        List<String> workplaces = prefs == null || prefs.workplaceTypes() == null
                ? List.of() : prefs.workplaceTypes();
        List<String> employments = prefs == null || prefs.employmentTypes() == null
                ? List.of() : prefs.employmentTypes();
        MlSalary minimumSalary = prefs == null ? null : prefs.minimumSalary();

        boolean title = desiredTitles.stream().anyMatch(value -> containsEither(value, job.title()))
                || containsEither(candidate.headline(), job.title());
        boolean location = locations.stream().anyMatch(value -> containsEither(value, job.location()));
        boolean workplace = workplaces.contains(job.workplaceType());
        boolean employment = employments.contains(job.employmentType());
        boolean salary = minimumSalary != null && job.salary() != null && minimumSalary.minimum() != null
                && job.salary().maximum() != null && minimumSalary.minimum() <= job.salary().maximum();

        int score = (int) Math.round(skillCoverage * 45
                + (title ? 25 : 0)
                + (location ? 10 : 0)
                + (workplace ? 10 : 0)
                + (employment ? 5 : 0)
                + (salary ? 5 : 0));

        List<String> strong = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        if (!matched.isEmpty()) strong.add("Skills matched: " + matched.size() + " of " + jobSkills.size());
        if (title) strong.add("Role headline aligns with the job title");
        if (location) strong.add("Location matched");
        if (!missing.isEmpty()) gaps.add("Missing listed skills: " + String.join(", ", missing.stream().limit(3).toList()));
        if (!locations.isEmpty() && !location) gaps.add("Location does not match the job");
        if (!workplaces.isEmpty() && !workplace) gaps.add("Workplace type does not match the stated preference");
        return new ScoredApplicant(candidate, Math.max(0, Math.min(100, score)),
                new MatchAnalysis(strong.stream().limit(3).toList(), gaps.stream().limit(3).toList(),
                        matched.stream().limit(5).toList()));
    }

    private static Set<String> normalized(List<String> values) {
        return values.stream().map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean containsEither(String left, String right) {
        if (left == null || right == null) return false;
        String a = left.toLowerCase(Locale.ROOT);
        String b = right.toLowerCase(Locale.ROOT);
        return a.contains(b) || b.contains(a);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    public record ScoredApplicant(MlCandidate candidate, int score, MatchAnalysis analysis) {}

    public record Result(
            String source,
            String modelVersion,
            String featureVersion,
            int inferenceMs,
            Instant generatedAt,
            List<ScoredApplicant> values) {}
}
