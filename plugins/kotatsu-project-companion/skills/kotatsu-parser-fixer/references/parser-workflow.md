# Parser Workflow

## Goal

Repair a broken site in Kotatsu with the smallest safe parser change while preserving source identity and existing user data compatibility.

## 1. Find the parser

- Search by source ID:

```powershell
rg -n '@MangaSourceParser\\(\"SOURCE_ID\"' local-kotatsu-parsers/src/main/kotlin
```

- Search by enum usage:

```powershell
rg -n 'MangaParserSource\\.SOURCE_ID' local-kotatsu-parsers/src/main
```

- Search for the nearest regression test:

```powershell
rg -n 'SOURCE_ID|SourceName' local-kotatsu-parsers/src/test/kotlin
```

## 2. Classify the failure

- `getListPage` or list parsing:
  - changed catalogue selectors
  - changed pagination or sort/filter query parameters
- search:
  - AJAX endpoint changed
  - search HTML changed
  - fallback page required
- `getDetails`:
  - chapter container changed
  - description or cover selectors changed
  - chapter pagination changed
- `getPages`:
  - image selectors changed
  - lazy-loading attributes changed
  - image requests now require different URLs
- domain or direct link:
  - redirect to another host
  - app-side link resolution mismatch

## 3. Preferred patch patterns

- New layout first, old layout fallback:

```kotlin
root.select("new-selector").ifEmpty {
    root.select("old-selector")
}
```

- AJAX search first, full-page search fallback when the AJAX payload is empty.
- Deduplicate chapter/page results after merging multiple layouts or paginated chapter blocks.
- Filter unusable URLs before generating IDs or model objects.
- Keep `toAbsoluteUrl`, `toRelativeUrl`, and `attrAsRelativeUrlOrNull` usage explicit so the parser remains robust across mirror/domain changes.

## 4. Compatibility rules

- Keep `@MangaSourceParser(...)` and the source enum unchanged unless migration is explicitly required.
- Keep `configKeyDomain` stable unless the site truly moved and the project wants the new domain persisted.
- Prefer expanding selectors and request builders rather than deleting supported behavior.

## 5. When app code may also need attention

Inspect `app/` if:
- the issue is only with direct links
- a source resolves correctly in parser tests but not from app intents
- blocked-content rules affect visibility or details loading
- repository wrappers or source-management UI behave differently from parser expectations

Likely app files:
- `app/src/main/kotlin/org/koitharu/kotatsu/core/parser/MangaRepository.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/core/parser/MangaLinkResolver.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/details/domain/DetailsLoadUseCase.kt`

## Example From This Workspace

- `olympustaff.com` is handled by `TEAMXNOVEL`.
- `TeamXNovel.kt` already uses:
  - AJAX search with fallback
  - modern chapter selectors with legacy fallback
  - page image selectors with fallback
  - duplicate chapter cleanup
- Treat that parser as the local model for future site-fix work in this repo.
