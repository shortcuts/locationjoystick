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

Never stop at the first failure. Run every step, log every failure, and decide the verdict
once, in the last step. One run then lists every blocker, so the maintainer fixes them in one
pass.

## Step 0: Baseline

Count the backlog entries. Read-only here — the count detects net-new entries at the end:

```bash
radin backlog count
```

Identify the last release tag, which bounds "since last release" everywhere below:

```bash
git describe --tags --abbrev=0 2>/dev/null || git log --oneline | tail -1 | awk '{print $1}'
```

## Step 1: Build pipeline

Run each gate alone, in this order. A non-zero exit gets logged, then the next gate still runs.

- `make format 2>&1`
- `make lint 2>&1`
- `make build 2>&1`

Per failed gate, invoke `radin-record`:

> Log a fix: `make <target>` failed. Exit code <N>. Output: <last 30 lines>

Done when all three gates exit 0, or the failing gate is logged.

## Step 2: Tests

```bash
make test 2>&1
```

On failure, invoke `radin-record`:

> Log a fix: `make test` failed. Failures: <failing test names / error summary>

**Live checks.** The maintainer runs `make smoke-test` by hand on a connected device. This
audit never asks whether to run it and never waits for its result.

Done when `make test` exits 0, or its failure is logged.

## Step 3: CI

Local gates do not prove the pipeline is green. Check the latest run on `main`:

```bash
gh run list --branch main --limit 1 --json conclusion,headSha,url
```

Done when the run's `headSha` equals `git rev-parse HEAD` and its `conclusion` is
`success`. An empty `conclusion` means the run is still in progress: it does not pass.

An older SHA or a failure: invoke `radin-record` with the run URL. For a failure, add the
failing job:

```bash
gh run view <run-id> --json jobs --jq '.jobs[] | select(.conclusion == "failure") | .name'
```

## Step 4: Documentation consistency

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

## Step 5: Thermo-nuclear code quality review

Invoke `radin-review` — it runs the review and logs each finding itself:

> Review all commits since `<last-release-tag>` (`git diff <last-release-tag> HEAD`).
> Apply the full thermo-nuclear standards and log findings to the backlog.

Log only findings that meet the thermo-nuclear bar: structural regressions, missed
code-judo, spaghetti growth, bad abstractions, file-size explosions.

## Step 6: Release PR

Merging the release-please PR tags the release and ships it. Inspect it before the verdict:

```bash
gh pr list --search 'chore(main): release in:title' --json number,title,headRefName,url
gh pr list --head release-please--branches--main --json number,title,url   # title has no colon in practice
gh pr view <PR#> --json body,mergeable,statusCheckRollup
```

1. The PR exists. None means no `feat` or `fix` commit landed since the tag: nothing to release.
2. The proposed version matches the commits under the rules in `release-please-config.json`.
3. The PR body lists every user-facing `feat` and `fix` since the tag. Compare it with
   `git log <last-release-tag>..HEAD --oneline`.
4. The PR is mergeable and every check in `statusCheckRollup` passes.

Per defect, invoke `radin-record` with the PR URL. A wrong version is a bad commit message:
fix it at the source, never by editing `CHANGELOG.md` or `.release-please-manifest.json`.

Done when items 2–4 hold, or each defect is logged.

## Step 7: Verdict

Run `radin backlog count` and compare it to the Step 0 baseline.

No new entries across Steps 1–6:

> ✅ Release is ready. All checks passed: format, lint, build, tests, CI, documentation
> consistency, thermo-nuclear code quality review, and release PR.

Then do Step 8.

New entries:

> ❌ Release is NOT ready. N issue(s) were logged to the backlog during this audit.
> Resolve them before tagging a release.
>
> Issues logged:
> - [headings of the items added during this run]

## Step 8: Changelog (✅ verdict only)

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
