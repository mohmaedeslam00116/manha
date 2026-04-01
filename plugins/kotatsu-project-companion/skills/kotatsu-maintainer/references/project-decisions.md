# Project Decisions

## Local Parser Fork Is Intentional

- `settings.gradle` replaces the upstream `com.github.KotatsuApp:kotatsu-parsers` artifact with the local `local-kotatsu-parsers` build.
- Keep this substitution unless there is an explicit decision to return to a remote dependency.
- Parser fixes for current source breakages should land in the local fork first.

## `olympustaff.com` Uses `TEAMXNOVEL`

- The source identity stays `MangaParserSource.TEAMXNOVEL`.
- The parser implementation lives in `TeamXNovel.kt`.
- Preserve source identity and saved-data compatibility when fixing the site.
- Prefer backward-compatible selector fallbacks where practical.

## Blocked Content Policy Is Permanent

- The app now permanently blocks:
  - all `ContentType.HENTAI` sources
  - all manga rated `ADULT`
  - all manga rated `SUGGESTIVE`
- This policy is enforced in:
  - repository wrapping
  - details loading
  - direct-link resolution
  - saved filter sanitization
  - backup export and restore
  - sync upload and download
  - one-time cleanup of previously stored app data
- Do not add NSFW toggles back into settings or source-management UI without explicit user approval.

## Data Hygiene Is Part Of The Feature

- Stored blocked manga should be cleaned from app data through `BlockedContentCleanup`.
- Local files remain on disk, but the app should not surface or re-index blocked content.
- Backup and sync code must avoid re-importing blocked items after cleanup.
