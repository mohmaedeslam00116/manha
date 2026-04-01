---
name: kotatsu-maintainer
description: "Use when working on this Kotatsu workspace: Android app changes, parser or source breakages, Gradle/build issues, backup or sync behavior, blocked-content policy, and repository navigation across `app/` and `local-kotatsu-parsers/`."
---

# Kotatsu Maintainer

## Overview

Use this skill for day-to-day maintenance of the Kotatsu Android project in this workspace. It captures the repo layout, the local parser-fork workflow, the current project-specific decisions, and the validation commands we should keep using before closing work.

## Quick Start

1. Classify the request first.
- Android UI, data, settings, reader, backup, sync, and repository wiring usually start in `app/`.
- Broken websites and chapter/page parsing usually start in `local-kotatsu-parsers/`.
- Direct-link or source-resolution issues usually touch `app/src/main/kotlin/org/koitharu/kotatsu/core/parser/`.
2. Preserve current project invariants.
- Keep the composite build in `settings.gradle` that substitutes `com.github.KotatsuApp:kotatsu-parsers` with `local-kotatsu-parsers`.
- Keep source IDs stable. For `olympustaff.com`, maintain `TEAMXNOVEL` and patch `TeamXNovel.kt` instead of inventing a new source unless migration is explicitly required.
- Preserve the permanent blocked-content policy for `HENTAI`, `ADULT`, and `SUGGESTIVE`.
3. Validate the narrowest useful slice first, then run the broader app build if app code changed.

## References

- Read `references/repo-map.md` when you need the current repo layout, file ownership hints, or validation commands.
- Read `references/project-decisions.md` when a task touches custom behavior we intentionally introduced in this workspace.

## Workflow

### Parser and source work

- Parser implementations live in `local-kotatsu-parsers/src/main/kotlin/`.
- Prefer selector/request fixes and backward-compatible fallbacks over source renames.
- Keep parser tests close to the edited source. `TeamXNovelTest.kt` is the current regression suite for `olympustaff.com`.

### App-side content and safety work

- Repository wrapping and source resolution live under `app/src/main/kotlin/org/koitharu/kotatsu/core/parser/`.
- Permanent blocked-content behavior lives in `BlockedContentPolicy`, `BlockedContentRepository`, `BlockedContentCleanup`, and the backup/sync/details flows listed in `references/project-decisions.md`.
- Do not reintroduce NSFW toggles or allow blocked content back into filters, backup, sync, or direct-link flows without explicit user approval.

### Data and integration work

- Backup and restore changes usually require matching checks in `BackupRepository` and `AppBackupAgent`.
- Sync-related changes usually require matching checks in `SyncHelper`.
- If filters or source visibility change, inspect saved filters, source settings UI, details loading, and link resolution together rather than in isolation.

## Validation

### Parser-only validation

```powershell
Set-Location local-kotatsu-parsers
.\gradlew.bat test --tests org.koitharu.kotatsu.parsers.TeamXNovelTest
```

### App-side validation

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests org.koitharu.kotatsu.core.content.BlockedContentPolicyTest
.\gradlew.bat :app:assembleDebug
```

### Notes

- Start with `rg` and targeted reads; do not guess across app and parser layers.
- Prefer minimal fixes that preserve source IDs, saved user data, and existing app flows.
- Mention when emulator or manual smoke testing was not run.
- If Gradle hits a stale lock around `app/build/tmp/kotlin-classes/debug`, clear that directory and retry instead of making larger unrelated changes.
