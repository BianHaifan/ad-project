# Community Package 1 Change Report

## Completed

- Added the V14 Community schema for posts, likes, and comments so Task 2 can reuse the same tables.
- Added Community Post persistence and separate API DTOs; JPA entities are never returned directly.
- Implemented `GET /api/v1/community/posts` with stable database pagination ordered by `createdAt DESC, id DESC`.
- Implemented `POST /api/v1/community/posts` with Unicode-aware normalize-then-validate behavior and HTTP 201.
- Added explicit service authorization for Candidate and Recruiter roles.
- Added the public Community Author projection and real Like/comment metrics queries.
- Added Task 1 integration, permission, and MySQL/Flyway schema tests.

## Added files

- `backend/src/main/resources/db/migration/V14__create_community_tables.sql`
- `backend/src/main/java/com/adproject/community/api/CommunityController.java`
- `backend/src/main/java/com/adproject/community/api/CommunityDtos.java`
- `backend/src/main/java/com/adproject/community/application/CommunityService.java`
- `backend/src/main/java/com/adproject/community/application/CommunityTextNormalizer.java`
- `backend/src/main/java/com/adproject/community/infrastructure/CommunityPostEntity.java`
- `backend/src/main/java/com/adproject/community/infrastructure/CommunityPostRepository.java`
- `backend/src/main/java/com/adproject/community/infrastructure/CommunityPostMetricsRepository.java`
- `backend/src/test/java/com/adproject/community/CommunityIntegrationTest.java`
- `backend/src/test/java/com/adproject/community/CommunityServicePermissionTest.java`
- `backend/src/test/java/com/adproject/community/CommunityMySqlFlywayIntegrationTest.java`
- `backend/src/test/java/com/adproject/community/application/CommunityTextNormalizerTest.java`
- `change_report/community-package-1.md`

No existing User, Company, or CompanyMember Repository required modification; their current read methods are sufficient.

## V14 migration

- Creates `community_posts` with UUID-style `CHAR(36)` IDs, author foreign key, text body, and microsecond timestamps.
- Creates `community_post_likes` with `(post_id, user_id)` as the primary key and foreign keys to posts and users.
- Creates `community_comments` with post/author foreign keys, text body, and microsecond timestamps.
- Adds `idx_community_posts_created_id (created_at, id)` and
  `idx_community_comments_post_created_id (post_id, created_at, id)`.
- Uses `DATETIME(6)` for every timestamp and body check constraints matching the approved 2,000/500 limits.
- Inserts no users or demonstration data and does not alter V1-V10.

## API behavior

### Feed

- Defaults to page 1 and page size 20; accepts at most 50.
- Invalid page/pageSize values use the existing 422 validation envelope.
- Uses JPA database pagination with `createdAt DESC, id DESC` stable ordering.
- Per the responsible-reviewer decision, posts sharing the same microsecond remain ordered by `id DESC`; no write-order guarantee is required. ID and timestamp generation were not changed.
- Returns the approved `data + meta` response with page, pageSize, total, and hasNext.
- Like count, comment count, and viewer Like state are read from the real V14 tables in batch queries; they are not hard-coded.

### Create post

- Accepts only the approved `body` request field.
- Uses `String.strip()` to remove leading and trailing Java Unicode whitespace, then uses `codePointCount` to enforce 1-2,000 Unicode code points and stores the normalized value.
- Does not count UTF-16 code units, truncate content, or replace internal whitespace. The package-private Community normalizer can be reused by Task 2 comments without implementing comment behavior early.
- Blank, null, and overlong bodies return the existing 422 validation envelope.
- Returns HTTP 201 with the approved data envelope and persisted Community Post.

## Authorization and privacy

- Every service entry point explicitly permits only `CANDIDATE` and `RECRUITER`; unsupported authenticated principals receive 403.
- Missing credentials remain handled by the existing security layer as 401. Auth/JWT code was not modified.
- Community Author contains only userId, fullName, nullable avatarUrl, role, and nullable companyName.
- Candidate companyName is null. Recruiter companyName is resolved read-only through CompanyMember and Company; missing membership/company produces null.
- Community responses do not expose email, passwordHash, tokens, resume/profile content, or company verification status.

## Tests and results

Commands used a task-local Maven cache and `-Dmaven.compiler.fork=true` because in-process javac could not close JARs through the Windows sandbox.

- `mvn ... -DskipTests compile`: passed after enabling forked javac.
- `mvn ... -Dtest=com.adproject.community.*Test test`: Community integration and permission tests passed with 10 tests and 0 failures/errors; during this earlier run, 2 MySQL Testcontainers tests were skipped because Docker was unavailable.
- `mvn ... -Dtest=CommunityTextNormalizerTest test`: passed with 3 tests and 0 failures/errors.
- `mvn ... test`: passed with 21 suites, 201 tests, 0 failures, 0 errors, and 8 skipped Docker/Testcontainers tests.
- `mvn.cmd "-Dapi.version=1.44" "-Dtest=CommunityMySqlFlywayIntegrationTest" test`: subsequently passed against Docker Desktop and `mysql:8.4` (actual MySQL 8.4.11) with 2 tests, 0 failures, 0 errors, and 0 skipped. The temporary Docker client compatibility parameter `-Dapi.version=1.44` is required in this environment.
- `mvn ... pmd:check`: passed with no PMD violations.
- H2 test profile: successfully migrated an empty schema through V14 and passed Hibernate schema validation.
- Unicode tests cover the 2,000/2,001 emoji code-point boundary, the 2,000/2,001 BMP boundary, ASCII and EM SPACE boundary stripping, Unicode-whitespace-only rejection, preservation of internal whitespace, and normalized persistence/response content.
- The MySQL 8.4 test now verifies V1-V14 migration; complete columns, types, nullability, `DATETIME(6)` precision, and utf8mb4 metadata; ordered index columns; the Like composite primary key; all five foreign-key mappings and non-CASCADE delete rules; CHECK metadata and enforcement; real utf8mb4 2,000/2,001 post and 500/501 comment boundaries; duplicate-Like rejection; and Hibernate schema validation through Spring context startup.

## Not run

- H2 counts supplementary characters as UTF-16 code units in its `CHAR_LENGTH` behavior, unlike the required MySQL Unicode-character semantics. Exact 2,000-emoji acceptance is therefore asserted in the Java normalizer test and the real MySQL behavior test, not weakened in V14 for H2 compatibility.
- No Web or Android build was run, as both are outside Task 1.

## Current limitations and next safe step

- Task 1 implements Feed and post creation only.
- Post detail, Like/Unlike operations, and comment read/create operations are not implemented.
- No edit, delete, search, recommendation, notification, Admin, OAuth/Meet, ML, or Agent behavior was added.
- The real MySQL 8.4 migration, metadata, constraint, Unicode-boundary, duplicate-Like, and Hibernate validation gate has passed.
- Task 2 must not start until this completed Task 1 package receives final human acceptance and explicit authorization.
