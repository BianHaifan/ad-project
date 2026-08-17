# Community Package 5 Change Report

## Implemented

- Candidate Android Community Feed can open a post detail screen without changing bottom navigation.
- Detail uses the authenticated Community Retrofit API for the persisted Post, Like/Unlike, and paged comments.
- Like/Unlike disables repeat taps; success adopts the server `likeCount` and `likedByCurrentUser`; failure reloads the persisted Post and retains a readable error.
- Comments use server order and database pagination. Create strips leading/trailing Unicode whitespace, preserves internal whitespace, enforces 1-500 Unicode code points, retains failed input, and uses the server response's Comment and `commentCount`.
- Author rendering uses only public Community fields and retains the existing null-avatar fallback in Feed.

## Files

- Modified Community Android API/repository, Feed screen/ViewModel, and minimal `AdCandidateApp` navigation.
- Added `CommunityDetailViewModel.kt`, `CommunityDetailScreen.kt`, and `CommunityTask5Test.kt`.
- No backend, Web, Auth, migration, OpenAPI, dependency, or bottom-navigation change.

## Tests

- Audit follow-up: initial comment-load and pagination failures now show `Retry comments`; retry records and requests the same failed page while preserving draft/input state.
- Audit follow-up: MockWebServer verifies `PUT`/`DELETE /api/v1/community/posts/{postId}/like` and the returned server `likeCount`/`likedByCurrentUser`; ViewModel tests verify initial and page-two comment retries request pages `1,1` and `1,2,2` respectively.
- Community Debug unit tests: 11 passed.
- Android `test lintDebug assembleDebug`: Debug 90 and Release 90 tests passed; 0 failures/errors/skips; lint and APK assembly passed.
- `git diff --check`: passed.

## Git and limits

- No git add, commit, or push was run.
- No Task 5 blocker is known. Web production build was not run because it would overwrite tracked `web/dist`; it is unrelated to this Android-only task.
- Task 5 stops here pending Checkpoint B review.
