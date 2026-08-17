# Community Demo Package 3

## Scope and implementation

- Added Recruiter Community feed at `/recruiter/community` with database-backed pagination, post creation, and loading/empty/error/content/submitting states.
- Added post detail at `/recruiter/community/:postId` with server-authoritative counts and Like state, idempotent Like/Unlike calls, paged comments, and comment creation.
- Added the Recruiter AppShell Community entry, minimal router wiring, centralized authenticated HTTP client, and React Query cache handling.
- Failed post/comment requests retain input; pending actions disable repeat submission. No mock is used by production Community code.
- Character counters use Unicode code points and do not impose the browser's UTF-16 `maxLength`; over-limit input is retained and submission is disabled.

## Task 3 files

- Modified: `web/src/api/contract.ts`
- Added: `web/src/api/communityHttpClient.ts`, `web/src/api/communityQueries.ts`, `web/src/api/communityHttpClient.test.ts`
- Added: `web/src/pages/CommunityPage.tsx`, `web/src/pages/CommunityDetailPage.tsx`, `web/src/pages/CommunityPages.test.tsx`
- Modified: `web/src/components/AppShell.tsx`, `web/src/components/AppShell.test.tsx`
- Modified: `web/src/router/index.tsx`, `web/src/theme/global.css`
- Added: `change_report/community-package-3.md`

## Verification

- Preflight: `git fetch --all --prune` succeeded; `HEAD` and `origin/main` were both `f1eed84baff086ad46727974a321c4e19936978c` (ahead/behind `0/0`).
- Community targeted tests: 3 files, 6 tests passed; 0 failed.
- Full Web tests: 20 files, 166 tests passed; 0 failed.
- `npm.cmd run typecheck`: passed.
- `npm.cmd run lint`: passed.
- `git diff --check`: passed.
- Build was intentionally not run because it would overwrite tracked `web/dist`.

## Git and scope

The working tree still contains the previously approved, uncommitted Task 0-2 files in `docs`, `backend`, `tasks`, and `change_report`. Task 3 itself changed only the Web Community files/minimal route and AppShell wiring listed above plus this report. No backend, Android, migration, Auth/JWT, Admin, dependency, or unrelated module was changed in Task 3. No `git add`, commit, or push was executed.

Blockers: none. Task 4 was not started.
