# Community Package 0 Change Report

## Completed

- Added the draft OpenAPI contract for the seven Community Demo operations.
- Added public Community author, post, comment, interaction, request, response, pagination, and path-parameter schemas.
- Added seven matching DRAFT rows to the API coverage catalog.
- Kept Community scoped as a P2 demo enhancement with no business implementation.

## Modified files

- `docs/openapi-v1.yaml`
- `docs/API_COVERAGE.csv`
- `change_report/community-package-0.md`

## Community API operations

- `GET /api/v1/community/posts` (`listCommunityPosts`)
- `POST /api/v1/community/posts` (`createCommunityPost`)
- `GET /api/v1/community/posts/{postId}` (`getCommunityPost`)
- `PUT /api/v1/community/posts/{postId}/like` (`likeCommunityPost`)
- `DELETE /api/v1/community/posts/{postId}/like` (`unlikeCommunityPost`)
- `GET /api/v1/community/posts/{postId}/comments` (`listCommunityComments`)
- `POST /api/v1/community/posts/{postId}/comments` (`createCommunityComment`)

The OpenAPI servers already include `/api/v1`, so path keys begin with `/community` and do not duplicate the version prefix.

## Permissions and business rules

- Every operation requires Bearer authentication and is limited to `CANDIDATE` and `RECRUITER`.
- Every operation declares 401, 403, 404, and 422 using the existing error envelope.
- Feed order is `createdAt DESC, id DESC`; comment order is `createdAt ASC, id ASC`.
- Community pagination is 1-based, defaults to page 1 and page size 20, and limits page size to 50 without changing the shared maximum of 100.
- Post bodies are text only and must contain 1–2,000 characters after trimming.
- Comment bodies are text only and must contain 1–500 characters after trimming.
- Like and Unlike are idempotent and return the latest `likeCount` and `likedByCurrentUser` state.
- Comment creation returns the created comment and latest server-derived `commentCount`.
- `CommunityAuthor` exposes only `userId`, `fullName`, nullable `avatarUrl`, role, and nullable `companyName`.

## Validation

Checked YAML syntax, internal references, unique operation IDs, Community paths and final URL composition, coverage mapping, permissions and errors, pagination, body constraints, Author privacy, idempotency, sorting, `git diff --check`, Git status, and diff statistics.

No repository-specific OpenAPI semantic validator was installed or run. No new dependency was installed.

## Current limitations and next step

- This package contains contract and coverage documentation only; there is no Community backend, Web, Android, or database implementation.
- No Flyway migration number has been reserved.
- Before Task 1 starts, Git status and the latest Flyway migration number must be checked and coordinated again.
- Task 1 must not start until this contract draft has been reviewed.
