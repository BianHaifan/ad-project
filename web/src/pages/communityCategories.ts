export const CATEGORY_OPTIONS = [
  ['JOB_SEEKING', 'Job seeking'],
  ['RECRUITING', 'Recruiting'],
  ['TECH_DISCUSSION', 'Tech discussion'],
  ['HELP', 'Help'],
  ['GENERAL', 'General'],
] as const;

export function categoryLabel(value: string) {
  return CATEGORY_OPTIONS.find(([key]) => key === value)?.[1] ?? value;
}
