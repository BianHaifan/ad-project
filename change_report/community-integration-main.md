# Community Integration on Latest Main

## Baseline and backup

- Integration worktree: `E:\ad\ad-project-community-integration`
- Branch: `integration/community-on-main`
- Baseline: `origin/main` at `ef4e5d39d9171833d7617692a7952fdde26d20c9`.
- The original `E:\ad\ad-project` worktree on `feature/community-demo` was not modified.
- Backup: `E:\ad\community-integration-backup-20260816`.
  - It contains all 43 Task 0-4 changed or untracked files, `git-status-short.txt`, `HEAD.txt`, `file-list.txt`, and per-file SHA-256 hashes in `SHA256SUMS.txt`.
  - SHA-256 of the manifest: `30F9EE7E48B9DFCFCDB4734B6E590D2E8A6DA18B9A19D1609F235BFA4D703556`.
  - Original source HEAD: `f1eed84baff086ad46727974a321c4e19936978c`.

## Remote changes assessed and retained

The eight commits between the original source HEAD and `origin/main` were retained:

- `ef4e5d3` merge of end-to-end ML recommendation service;
- `428732c` Android recommendation company-id mapping fix;
- `b6c3554` mainline conflict resolution for recruiter/candidate and recommendations;
- `96b7f6d` recruiter/candidate profiles, message attachments, and interview auto-message;
- `de19603` implementation commit for those profiles/attachments/interview behavior;
- `0533b36` recommendation migration renumbering to V13;
- `0299884` MySQL migration fixture update for skills;
- `3e0dfb1` ML recommendation service.

No remote migration was changed. Remote V11, V12, and V13 remain intact.

## Controlled merge result

- Task-plan files were byte-identical to remote and deliberately left untouched.
- Android retains remote Profile/recommendation/public-profile/attachment behavior and adds only the existing Task 4 Community route, Profile entry, authenticated Retrofit API, and repository wiring. Bottom navigation remains unchanged.
- Web retains remote Profile route/AppShell behavior and adds Community API client, routes, pages, tests, navigation entry, and Community-only styles.
- OpenAPI and API coverage use `origin/main` as their base and contain the seven approved Community operations.
- A structural repair was required in the remote OpenAPI content: its Admin path blocks and Profile/Admin schemas were indented outside their intended `paths`/`components.schemas` locations, leaving internal `$ref` values unresolved. The repair restores those existing remote objects without changing their behavior. `SalaryCurrency` (`SGD`) and `SalaryPeriod` (`HOUR`, `MONTH`, `YEAR`) schemas were added to match the pre-existing backend enums referenced by the remote recommendation contract.

## V14 migration

- Community migration is now `V14__create_community_tables.sql`.
- V1-V13 were not edited.
- Community Flyway test names/assertions and Package 1/2 migration references were updated from V11/V1-V11 to V14/V1-V14.
- The persistent local MySQL database was confirmed at V6 before integration, then migrated normally through V7-V14. It now records successful versions 1-14 and contains `community_posts`, `community_post_likes`, and `community_comments`. No Flyway history row was edited manually.

## OpenAPI and catalog verification

- YAML syntax: passed.
- Internal `$ref` resolution: passed.
- operationId uniqueness: 74 unique operationIds out of 74 operations.
- Final computed catalog:
  - `uniqueOperations: 74`
  - `candidateOperations: 34`
  - `recruiterOperations: 42`
  - `sharedOperations: 11`
  - `adminOperations: 9`
- `docs/API_COVERAGE.csv` contains 69 records, including seven Community records.

## Validation results

- `git diff --check`: passed.
- Backend full suite (`mvn.cmd "-Dapi.version=1.44" test`): 251 tests, 0 failures, 0 errors, 0 skipped.
- Community MySQL gate (`mvn.cmd "-Dapi.version=1.44" "-Dtest=CommunityMySqlFlywayIntegrationTest" test`): 2 tests, 0 failures, 0 errors, 0 skipped; Testcontainers MySQL 8.4 passed.
- PMD (`mvn.cmd "-Dapi.version=1.44" pmd:check`): passed.
- Persistent MySQL 8.4.11: normal Flyway V6-to-V14 migration passed; Hibernate `ddl-auto=validate` initialized the EntityManagerFactory with no schema-validation error.
- Web: `npm ci`, typecheck, lint, and Vitest passed; 23 files / 178 tests passed.
- Android: `test lintDebug assembleDebug` passed with `ANDROID_HOME` set only for the command; Debug and Release unit suites each passed 84 tests (168 total), 0 failures/errors/skips.
- Web production build was not run because it would overwrite tracked `web/dist`.

## Risks and Task 5 gate

- No unresolved code conflicts remain. The only non-functional warning is Flyway's upstream notice that its current version has not tested MySQL beyond 8.1; actual MySQL 8.4.11 migration and Community Testcontainers gates passed.
- Scope remains Task 0-4 integration only. Task 5, Checkpoint B, commits, and pushes were not started.
- Task 5's integration baseline, Flyway ordering, OpenAPI contract, backend/web/android regression, and persistent-development-db gates are satisfied. Human review of this uncommitted integration worktree is still required before Task 5 begins.

## Final Git status

`integration/community-on-main` remains at uncommitted baseline `ef4e5d39d9171833d7617692a7952fdde26d20c9`.

- Modified tracked integration files: the three minimal Android route/container/Profile files; `docs/openapi-v1.yaml`; `docs/API_COVERAGE.csv`; and the five minimal Web contract/AppShell/router/style files.
- Untracked Community files: Android Community implementation and test; backend Community implementation, tests, and V14 migration; Package 0-4 reports plus this integration report; and Community Web API/pages/tests.
- There are no changed V1-V13 migrations, task-plan files, backend/Web/Android files outside the approved Community integration surface, commits, staged files, or pushed changes.
