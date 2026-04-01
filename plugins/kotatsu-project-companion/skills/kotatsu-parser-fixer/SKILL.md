---
name: kotatsu-parser-fixer
description: "Use when fixing broken Kotatsu sources or website parsers in this workspace: selector drift, domain changes, AJAX search changes, chapter/page extraction failures, parser tests, and source-to-site mapping inside `local-kotatsu-parsers/`."
---

# Kotatsu Parser Fixer

## Overview

Use this skill when a manga site stops working in Kotatsu and the likely fix lives in the local parser fork. It provides a repeatable workflow for tracing the source, patching the parser safely, preserving source identity, and choosing the right tests before closing the task.

## Quick Start

1. Confirm the failure surface.
- Is the breakage in list, search, details, chapters, pages, direct link resolution, or domain redirect behavior?
- If the issue is source visibility or app-side blocking rather than site parsing, switch back to `$kotatsu-maintainer`.
2. Locate the parser implementation in `local-kotatsu-parsers/`.
- Search by source ID first, not by guessed filename.
- Preserve the existing source ID and parser annotation unless a migration is explicitly required.
3. Patch with fallbacks instead of hard replacement when the site may still serve mixed layouts.
4. Run the narrowest parser test first, then the app build only if the app module changed.

## References

- Read `references/parser-workflow.md` for the full parser repair flow and common patch patterns.
- Read `references/validation-matrix.md` to choose the right test or build command for the change.

## Workflow

### Step 1: Identify the parser

- Search for the source annotation or source enum usage in `local-kotatsu-parsers/src/main/kotlin/`.
- Then inspect the nearest test in `local-kotatsu-parsers/src/test/kotlin/`.
- For existing custom work in this repo, `olympustaff.com` maps to `TEAMXNOVEL` in `TeamXNovel.kt`.

### Step 2: Patch conservatively

- Preserve `@MangaSourceParser(...)`, the source enum, and `configKeyDomain` unless there is a deliberate migration plan.
- Prefer these patterns:
- modern selector first, old selector fallback
- AJAX search first, traditional search fallback
- deduplicate chapters by `url` or `id`
- ignore dead chapter links such as `href="#"` or gated placeholders
- keep relative/absolute URL handling explicit
- If the fix touches domain redirects or link resolution behavior, check whether app-side code also needs changes.

### Step 3: Keep behavior compatible

- Do not create a new source just because the site HTML changed.
- Avoid breaking saved items, bookmarks, and direct links by changing source identity unnecessarily.
- When filters change, keep existing supported filters when possible and repair the request builder instead of removing capability.

### Step 4: Validate

- Run the dedicated parser test file when it exists.
- If you add or change parser-side behavior without app-side changes, parser tests are the main gate.
- If the app module changed too, run the relevant app unit test and `:app:assembleDebug`.

## Notes

- The local parser fork is part of the intended architecture in this workspace, not a temporary hack.
- Prefer targeted `rg` searches and small code reads over broad scanning.
- Mention clearly when you could not run a live smoke test against a device or emulator.
