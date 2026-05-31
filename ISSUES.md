# Known Issues & Backlog

## Documentation Outdated Items

No outstanding documentation issues.

---

## Audit

Audited commits 73eb59f–58e5ba9 (last 20). Findings:

- All code-changing commits verified correct.
- One gap fixed: `ReplayOrchestrator.handleResume` was missing `emitCompletion` — fixed in follow-up commit.
- No regressions, resource leaks, or threading issues found.

---

## Screenshots

Screenshots 14 (`joystick_overlay`) and 15 (`widget_overlay`) require overlay services running — capture manually. Script pauses and prompts at those steps (`pause_for_user`).

---

## Backlog

### UI/UX issues

- Smoke tests do not assert floating widget behavior.
