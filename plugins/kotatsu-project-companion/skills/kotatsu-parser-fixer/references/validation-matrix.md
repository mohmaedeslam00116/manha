# Validation Matrix

## Parser-only changes

Run the closest dedicated parser test first.

### Example

```powershell
Set-Location local-kotatsu-parsers
.\gradlew.bat test --tests org.koitharu.kotatsu.parsers.TeamXNovelTest
```

## What `TeamXNovelTest` currently covers

- list
- pagination
- title search
- details
- pages
- domain
- direct link resolution

This makes it a good template for future source-specific parser tests.

## When to run broader parser tests

Run broader parser tests when:
- shared parser utilities changed
- a base class changed
- the fix may affect multiple sources

## When to run app-side validation

Also run app validation if the change touched `app/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests org.koitharu.kotatsu.core.content.BlockedContentPolicyTest
.\gradlew.bat :app:assembleDebug
```

## Manual checks worth mentioning

- search returns the expected title
- details load with chapters present
- first chapter opens and images load
- direct site link resolves to the same manga
- no unexpected source ID or domain drift happened

## Reporting guidance

When you finish a parser task, state:
- what broke
- which parser file changed
- which test you ran
- whether app build or device smoke testing was not run
