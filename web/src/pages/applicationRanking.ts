import type {RecruiterApplicationSummary} from '../models/recruiter';

export function rankApplicationsByMatch(values: RecruiterApplicationSummary[]) {
  return [...values].sort((left, right) =>
    (right.matchScore ?? -1) - (left.matchScore ?? -1) || left.applicationId.localeCompare(right.applicationId));
}
