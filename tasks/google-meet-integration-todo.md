# Google Meet automatic scheduling checklist

- [x] Finish the manual interview workflow first.
- [x] Freeze provider-aware OpenAPI and add Flyway V9 integration storage.
- [ ] Obtain local-only Google Cloud demo configuration and test users.
- [x] Implement secure connect/callback/status/disconnect operations.
- [x] Add safe OAuth callback web handoff (303 See Other to the server-configured web return URI; see `change_report/google-oauth-web-handoff.md`).
- [x] Add Google Meet provisioning with retry-safe state handling (backend Calendar/Meet creation done; see `change_report/google-meet-calendar-provisioning.md`).
- [x] Add recruiter UI for connection (status page + connect/disconnect + safe callback result; see `change_report/web-google-oauth-connection-ui.md`).
- [x] Add recruiter scheduling UI for auto-create Meet selection and sync-state rendering (Task 4; see `change_report/web-google-meet-scheduling-ui.md`).
- [x] Synchronize reschedule/cancel backend (done; see `change_report/google-meet-reschedule-cancel-sync.md` and `change_report/google-meet-reschedule-cancel-sync-review-fixes.md`) — Android candidate display of final state done (see `change_report/android-interview-meeting-sync-state.md`).
- [ ] Run focused backend/Web/Android tests and a live two-account demo.
- [ ] Write the final `change_report/` handoff; do not include secrets.
