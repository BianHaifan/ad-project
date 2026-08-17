# Community Package 2 Change Report

## 1. Implemented behavior

- Implemented `GET /api/v1/community/posts/{postId}` with persisted post data, public Author projection, real Like/comment counts, viewer-specific Like state, and 404 for a missing Post.
- Implemented idempotent `PUT /api/v1/community/posts/{postId}/like` and `DELETE /api/v1/community/posts/{postId}/like`; both return HTTP 200 with the latest database-derived `likeCount` and `likedByCurrentUser`.
- Implemented `GET /api/v1/community/posts/{postId}/comments` with database pagination, default page 1/pageSize 20, maximum pageSize 50, and stable `createdAt ASC, id ASC` ordering.
- Implemented `POST /api/v1/community/posts/{postId}/comments` with HTTP 201, the persisted Comment, and the latest database-derived `commentCount`.
- No detail embedding of comments, reply hierarchy, edit, delete, search, notification, report, recommendation, Web, or Android behavior was added.

## 2. Modified and added files

Modified for Task 2:

- `backend/src/main/java/com/adproject/community/api/CommunityController.java`
- `backend/src/main/java/com/adproject/community/api/CommunityDtos.java`
- `backend/src/main/java/com/adproject/community/application/CommunityService.java`
- `backend/src/test/java/com/adproject/community/CommunityServicePermissionTest.java`
- `backend/src/test/java/com/adproject/community/application/CommunityTextNormalizerTest.java`

Added for Task 2:

- `backend/src/main/java/com/adproject/community/infrastructure/CommunityCommentEntity.java`
- `backend/src/main/java/com/adproject/community/infrastructure/CommunityCommentRepository.java`
- `backend/src/main/java/com/adproject/community/infrastructure/CommunityPostLikeRepository.java`
- `backend/src/test/java/com/adproject/community/CommunityTask2IntegrationTest.java`
- `change_report/community-package-2.md`

No migration, OpenAPI, API coverage, dependency, Auth/JWT, UserRole, User/Company/CompanyMember Repository, Web, or Android file was changed for Task 2.

## 3. Like/Unlike idempotency and concurrency

- V14 remains the source of truth: `(post_id, user_id)` is the `community_post_likes` primary key.
- Like performs a single database insert and catches Spring's translated `DuplicateKeyException`; a concurrent or repeated insert therefore becomes an idempotent success instead of a 409/500 response.
- Unlike executes a keyed delete; deleting zero rows is an idempotent success.
- Both operations verify the Post first, run inside a transaction, and query the persisted count and current viewer row for the response.
- An integration test starts two concurrent HTTP `PUT .../like` requests for the same account/Post, verifies both return 200 with the same successful state, and verifies exactly one database row remains.

## 4. Comment Unicode normalization

- Comment creation reuses the package-private `CommunityTextNormalizer` introduced in Task 1.
- `String.strip()` removes leading and trailing Java Unicode whitespace.
- `codePointCount()` enforces 1-500 Unicode code points rather than UTF-16 code units.
- The service stores and returns the normalized value without truncating content or replacing internal whitespace.
- Tests cover 500/501 emoji and BMP boundaries, ASCII/EM SPACE boundaries, Unicode-whitespace-only rejection, internal whitespace preservation, and normalized persistence/response content.

## 5. Authorization and privacy

- Every Community service entry point explicitly permits only `CANDIDATE` and `RECRUITER`; future roles are denied by default.
- The existing security layer continues to return the established 401 envelope; service-level role rejection returns the established 403 envelope.
- Missing Posts return the established 404 envelope; invalid bodies and pagination return the established 422 envelope.
- Post and Comment responses use the dedicated Community Author projection only: `userId`, `fullName`, nullable `avatarUrl`, `role`, and nullable `companyName`.
- No Entity, email, password hash, token, Resume, company verification status, or other private account field is returned or logged.

## 6. Tests and actual results

- Focused Community + Task 1 regression command: 24 tests, 0 failures, 0 errors, 0 skipped.
  - `CommunityTextNormalizerTest`: 4 passed.
  - `CommunityIntegrationTest`: 9 passed.
  - `CommunityServicePermissionTest`: 1 passed.
  - `CommunityTask2IntegrationTest`: 10 passed.
- Strengthened concurrent HTTP Like rerun: 10 tests, 0 failures, 0 errors, 0 skipped.
- Final complete backend command with Docker compatibility enabled:
  `mvn.cmd -Dapi.version=1.44 test`
  - 212 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- PMD: BUILD SUCCESS, no violations.
- H2 test profile migrated an empty database through V1-V14 and completed Hibernate schema validation.
- OpenAPI read-only validation: YAML parsed; 56 operationIds are unique; 422 internal references resolve; all five Task 2 operations exist.

## 7. Real MySQL result

- Command: `mvn.cmd "-Dapi.version=1.44" "-Dtest=CommunityMySqlFlywayIntegrationTest" test`.
- Testcontainers 1.21.3 connected to Docker Desktop and started `mysql:8.4`.
- Actual database version: MySQL 8.4.11.
- Empty-database V1-V14 migration and Hibernate schema validation succeeded.
- Community metadata, foreign keys, delete rules, CHECK behavior, utf8mb4 boundaries, and duplicate Like behavior succeeded.
- Result: 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- The same Community MySQL tests also ran with 0 skipped inside the final 212-test backend run.

## 8. Not run

- Web and Android builds/tests were not run because Task 2 is backend-only and explicitly forbids client implementation.
- No Task 3 or later-stage test was run.

## 9. Git status

```text
 M docs/API_COVERAGE.csv
 M docs/openapi-v1.yaml
?? backend/src/main/java/com/adproject/community/
?? backend/src/main/resources/db/migration/V14__create_community_tables.sql
?? backend/src/test/java/com/adproject/community/
?? change_report/community-package-0.md
?? change_report/community-package-1.md
?? change_report/community-package-2.md
?? tasks/community-demo-implementation-plan.md
?? tasks/community-demo-todo.md
```

## 10. Complete changed-file list including untracked files

```text
backend/src/main/java/com/adproject/community/api/CommunityController.java
backend/src/main/java/com/adproject/community/api/CommunityDtos.java
backend/src/main/java/com/adproject/community/application/CommunityService.java
backend/src/main/java/com/adproject/community/application/CommunityTextNormalizer.java
backend/src/main/java/com/adproject/community/infrastructure/CommunityCommentEntity.java
backend/src/main/java/com/adproject/community/infrastructure/CommunityCommentRepository.java
backend/src/main/java/com/adproject/community/infrastructure/CommunityPostEntity.java
backend/src/main/java/com/adproject/community/infrastructure/CommunityPostLikeRepository.java
backend/src/main/java/com/adproject/community/infrastructure/CommunityPostMetricsRepository.java
backend/src/main/java/com/adproject/community/infrastructure/CommunityPostRepository.java
backend/src/main/resources/db/migration/V14__create_community_tables.sql
backend/src/test/java/com/adproject/community/application/CommunityTextNormalizerTest.java
backend/src/test/java/com/adproject/community/CommunityIntegrationTest.java
backend/src/test/java/com/adproject/community/CommunityMySqlFlywayIntegrationTest.java
backend/src/test/java/com/adproject/community/CommunityServicePermissionTest.java
backend/src/test/java/com/adproject/community/CommunityTask2IntegrationTest.java
change_report/community-package-0.md
change_report/community-package-1.md
change_report/community-package-2.md
docs/API_COVERAGE.csv
docs/openapi-v1.yaml
tasks/community-demo-implementation-plan.md
tasks/community-demo-todo.md
```

## 11. Out-of-scope audit

- Task 2 changed only the approved Community production/test paths and this report.
- V1-V14, OpenAPI, API coverage, dependencies, Auth/JWT, UserRole, Admin, OAuth/Meet, ML, Agent, Job/Application/Interview, Web, Android, and task-plan files were not changed during Task 2.
- No `git add`, commit, push, stash, rebase, reset, restore, or destructive Git operation was performed.

## 12. Known risks

- Like/count responses are current at their database query time; another authenticated user can legitimately change the count immediately afterward.
- Idempotent concurrent Like handling depends on V14's primary key and Spring's standard duplicate-key exception translation, both covered by concurrent HTTP and real MySQL constraint tests.
- Flyway logs a compatibility recommendation because the project's current Flyway version officially lists support through MySQL 8.1; actual MySQL 8.4.11 migration, constraints, and Hibernate validation pass. No dependency was upgraded because Task 2 forbids dependency changes.

## 13. Checkpoint A status

- The five approved Task 2 operations, success paths, 401, explicit-role 403, missing-resource 404, validation 422, idempotent/repeated/concurrent Like, comment pagination/order, Unicode normalization, privacy, and cross-account consistency are covered.
- Empty-database H2 and real MySQL 8.4.11 V1-V14 migration and Hibernate validation pass.
- Complete backend tests, PMD, OpenAPI read-only validation, and Git whitespace/range checks pass.
- Technical Checkpoint A criteria are satisfied, subject to final human read-only review. Task 3/Web and Android work must not begin without separate authorization.
