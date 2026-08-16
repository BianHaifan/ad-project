from ad_recommender.features import PairFeatureExtractor
from ad_recommender.schemas import CandidatePreferences


def test_matching_pair_has_strong_structured_features(candidate, matching_job, unrelated_job):
    extractor = PairFeatureExtractor().fit([candidate], [matching_job, unrelated_job])

    matching = extractor.transform(candidate, matching_job).as_mapping()
    unrelated = extractor.transform(candidate, unrelated_job).as_mapping()

    assert matching["text_similarity"] > unrelated["text_similarity"]
    assert matching["skill_coverage"] == 1.0
    assert matching["location_match"] == 1.0
    assert unrelated["location_match"] == 0.0


def test_missing_preferences_are_marked_missing(candidate, matching_job):
    candidate = candidate.model_copy(update={"preferences": CandidatePreferences()})
    extractor = PairFeatureExtractor().fit([candidate], [matching_job])

    features = extractor.transform(candidate, matching_job).as_mapping()

    assert features["location_missing"] == 1.0
    assert features["workplace_missing"] == 1.0
    assert features["employment_missing"] == 1.0
