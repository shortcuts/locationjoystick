---
name: release-candidate
description: |
  Go/no-go audit of main before tagging a release: build pipeline, tests, doc
  consistency, code quality. Logs every failure to radin's backlog.
  Use for "release checks", "pre-release audit", "is this ready to ship?".
---

# Release Candidate Auditor

Every failure becomes a backlog entry detailed enough to act on without re-running the
audit. Route every finding through the `radin-record` skill — it is the only writer to
`BACKLOG.md`.

Done when every step below has run and every failure it found is logged.

## Step 0: Baseline

Resolve radin's per-project namespace to locate `BACKLOG_FILE`. Read-only here — the
count detects net-new entries at the end:

```bash
bash "$HOME/.claude/radin-lib/radin-namespace.sh"
grep -c '^### ' "$BACKLOG_FILE" 2>/dev/null || echo "0"
```

Identify the last release tag, which bounds "since last release" everywhere below:

```bash
git describe --tags --abbrev=0 2>/dev/null || git log --oneline | tail -1 | awk '{print $1}'
```

## Step 1: Build pipeline

Each step is a hard gate. A non-zero exit stops the pipeline there.

```bash
make format 2>&1
make lint   2>&1
make build  2>&1
```

Per failure, invoke `radin-record`:

> Log a fix: build pipeline failure in `make <step>`. Exit code <N>. Output:
> <last 30 lines>

## Step 2: Tests

```bash
make test 2>&1
```

On failure, invoke `radin-record`:

> Log a fix: `make test` failed. Failures: <failing test names / error summary>

**Smoke tests.** Check for a connected Android device:

```bash
adb devices 2>/dev/null | grep -v "List of devices" | grep -v "^$"
```

A device appears: ask whether to run `make smoke-test` — it covers all navigation paths
and takes a few minutes. On a yes and a failure, invoke `radin-record`:

> Log a fix: `make smoke-test` failed. Failing tests: <failing test names>

## Step 3: Documentation consistency

The codebase is the source of truth. Every user-facing change since the last release
appears in at least one of: `README.md`, `AGENTS.md`, `docs/`, `docs/wiki/`.

Done when every feature area with source changes since the tag is accounted for —
documented, stale-doc logged, or marked not user-facing.

```bash
git diff --name-only <last-release-tag> HEAD
```

Keep `feature/`, `core/`, `app/` sources plus already-changed docs. Group them by feature
area (map, routes, favorites, settings, joystick, widget, location engine). Per area,
read the docs and compare against the changed sources:

1. A doc exists in `docs/features/`.
2. The `docs/wiki/` section matches current behavior.
3. `README.md` names the feature when it is user-visible.
4. `AGENTS.md` reflects new services, modules, or domain models.

Also verify: the `Key Services` table matches the real services, and the module table covers every module added or removed
since the tag.

Per gap, invoke `radin-record`:

> Log a fix: documentation gap in <feature area>. Changed sources: <paths>.
> Gap: <what is missing or stale, specific enough that the writer knows what to write>.
> Doc file: <path>.

A doc that exists and covers the change passes. A user-visible feature with no doc is a
gap. Skip cosmetic nits.

## Step 4: Intermediate verdict

Count entries again and compare to the Step 0 baseline. New entries: report the ❌
verdict below and stop. No new entries: continue to Step 5.

## Step 5: Thermo-nuclear code quality review

Invoke `radin-review` — it runs the review and logs each finding itself:

> Review all commits since `<last-release-tag>` (`git diff <last-release-tag> HEAD`).
> Apply the full thermo-nuclear standards and log findings to the backlog.

Log only findings that meet the thermo-nuclear bar: structural regressions, missed
code-judo, spaghetti growth, bad abstractions, file-size explosions.

## Step 6: Verdict

Count entries and compare to the Step 0 baseline.

No new entries across Steps 1–5:

> ✅ Release is ready. All checks passed: format, lint, build, tests, documentation
> consistency, and thermo-nuclear code quality review.

Then do Step 7.

New entries:

> ❌ Release is NOT ready. N issue(s) were logged to the backlog during this audit.
> Resolve them before tagging a release.
>
> Issues logged:
> - [headings of the items added during this run]

## Step 7: Changelog (✅ verdict only)

Tell the user, verbatim in substance:

> Recommend generating the website changelog now (`docs/wiki/changelog.html`) for the
> upcoming version, before tagging. Also watch for the `release-please` PR — it opens
> automatically (title `chore(main): release <version>`) once these commits land on
> `main`, and its body is the authoritative source list for the changelog entry.

Then invoke `radin-record` to log it as a chore, not a fix — an action item, not a defect.
It does not flip the verdict:

> Log a chore: generate changelog for <version>. Release checks passed. Generate the
> `docs/wiki/changelog.html` entry for <version> before tagging. See
> `CHANGELOG-PROCEDURE.md` in this skill for the procedure.

The procedure lives in [`CHANGELOG-PROCEDURE.md`](CHANGELOG-PROCEDURE.md) — read it when
you generate the changelog in this session rather than logging it for later.

## Notes

- Keep lint errors visible. A suppression that hides a real issue is itself a finding —
  log it.
