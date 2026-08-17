# Merge PR #13 (community) into accumulated local work

## Outcome

- Merge commit: `1e21f8b` — `Merge PR #13: cross-platform community feature`
- Parents: `6648df8` (accumulated recruiter/candidate/avatar/saved-jobs/AI-ranked work) + `c8648a1` (PR #13 head, `upstream/integration/community-on-main`).
- PR #13 is a single commit, fast-forwardable from `ef4e5d3` (== `origin/main`), so the merge is clean with a 2-parent topology.

## Migration renumber (the one hard cross-cutting conflict)

PR #13 ships `V14__create_community_tables.sql`; the local tree already had a
`V14__create_user_avatars.sql`. Both are new/untracked on their respective sides,
so Flyway would collide at V14. Resolution: **renumbered the local migrations**, not
the colleague's:

| Old (local) | New |
|---|---|
| `V14__create_user_avatars.sql` | `V18__create_user_avatars.sql` |
| `V15__add_candidate_profile_contact_and_gender.sql` | `V19__add_candidate_profile_contact_and_gender.sql` |
| `V16__add_candidate_profile_age.sql` | `V20__add_candidate_profile_age.sql` |
| `V17__create_candidate_saved_jobs.sql` | `V21__create_candidate_saved_jobs.sql` |

Community tables FK-depend only on `users` (V1), so ordering is independent of the
avatar/profile/saved-jobs chain; no semantic change to either side.

## Conflict resolutions

Six files conflicted; three auto-merged. Resolutions that were not pure union:

- **Android `AdCandidateApp.kt` + `RealProfileScreens.kt`** — the local tree had
  refactored the profile screen into a "Me page" (ProfileEdit + avatar + SavedJobs);
  PR #13 built on the old inline-form structure. Kept the local refactor and layered
  PR #13's community additions on top: `Route.Community` / `Route.CommunityDetail`,
  `onCommunity` param threaded through `RealProfileScreen` → `MeContent`, and a
  "Community" `ActionRow`.
- **Web `AppShell.tsx`** — the local tree deliberately removed the "Integrations"
  nav link (its own test asserts it is gone). Kept that removal and added PR #13's
  "Community" nav link, yielding nav = Dashboard / Jobs / Applications / Messages /
  Community. Google OAuth remains reachable via ApplicationDetailPage links.
- **`docs/openapi-v1.yaml`** — PR #13 normalized schema indentation to 4-space;
  retained that normalization while keeping the local semantic additions:
  `AvatarMetadata`, and `UpdateRecruiterProfileRequest` with
  `additionalProperties: false` and no `avatarUrl` (read-only).

## Verification (all green)

- Backend `mvn -o test` → exit 0.
- Web `npm test` → 219 passed (26 files); `npm run build` (tsc + vite) → exit 0.
- Android `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.

## Left uncommitted (pre-existing, not part of this merge)

- `web/dist/` regenerated build artifacts (stale hashed `index-*.js/css` deleted,
  `index.html` modified, new hashed assets).
- adb UI-capture artifacts `screen1.png`, `screen2.png`, `screen3.png`, `ui_dump.xml`.
