# Kotatsu Repo Map

## Top-Level Layout

- `app/`: Main Android application module.
- `local-kotatsu-parsers/`: Local composite-build fork that replaces the upstream `kotatsu-parsers` dependency.
- `settings.gradle`: Wires the app module and the local parser fork together with dependency substitution.
- `.agents/plugins/marketplace.json`: Repo-local plugin marketplace entry for this workspace.

## App Areas That Matter Most

- `app/src/main/kotlin/org/koitharu/kotatsu/core/parser/`
  - `MangaRepository.kt`: repository factory and source-to-repository routing.
  - `BlockedContentRepository.kt`: central filter wrapper around repositories.
  - `MangaLinkResolver.kt`: direct-link resolution and source matching.
- `app/src/main/kotlin/org/koitharu/kotatsu/core/content/`
  - `BlockedContentPolicy.kt`: permanent content blocking rules.
  - `BlockedContentCleanup.kt`: one-time cleanup of blocked content from stored app data.
- `app/src/main/kotlin/org/koitharu/kotatsu/details/domain/DetailsLoadUseCase.kt`
  - Applies content restrictions when loading local and remote manga details.
- `app/src/main/kotlin/org/koitharu/kotatsu/filter/data/SavedFiltersRepository.kt`
  - Sanitizes saved filters so blocked ratings or source types do not reappear.
- `app/src/main/kotlin/org/koitharu/kotatsu/backups/data/BackupRepository.kt`
  - Backup export and restore filtering.
- `app/src/main/kotlin/org/koitharu/kotatsu/backups/domain/AppBackupAgent.kt`
  - System backup/restore entry points.
- `app/src/main/kotlin/org/koitharu/kotatsu/sync/domain/SyncHelper.kt`
  - Sync upload/download filtering.
- `app/src/main/kotlin/org/koitharu/kotatsu/settings/sources/manage/SourcesManageFragment.kt`
  - Source management UI, including source visibility behavior.

## Parser Fork Areas That Matter Most

- `local-kotatsu-parsers/src/main/kotlin/org/koitharu/kotatsu/parsers/site/ar/TeamXNovel.kt`
  - Current implementation for `olympustaff.com` through the existing `TEAMXNOVEL` source.
- `local-kotatsu-parsers/src/test/kotlin/org/koitharu/kotatsu/parsers/TeamXNovelTest.kt`
  - Regression coverage for list, pagination, search, details, pages, domain, and direct links.
- `local-kotatsu-parsers/build.gradle.kts`
  - Parser fork build logic and JVM/Kotlin toolchain config.

## Validation Commands

### From repo root

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests org.koitharu.kotatsu.core.content.BlockedContentPolicyTest
.\gradlew.bat :app:assembleDebug
```

### From `local-kotatsu-parsers/`

```powershell
.\gradlew.bat test --tests org.koitharu.kotatsu.parsers.TeamXNovelTest
```

## Practical Heuristics

- If a site breaks but the source ID should remain stable, patch the parser implementation rather than creating a new source.
- If blocked content is touched in one flow, check backup, sync, details, filters, and link resolution together.
- If a task changes both parser behavior and app integration, run parser tests and the app build before closing the task.
