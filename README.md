# CabComply

**Built by a driver, for drivers.**

CabComply is a native Android application (Kotlin + Jetpack Compose) for UK taxi and
private-hire drivers: daily vehicle safety checks, defect tracking with photo evidence,
mileage (including HMRC business mileage), vehicle/driver compliance documents with expiry
reminders, historical records, professional weekly PDF compliance reports, a read-only
"Show to Officer" mode, PIN security, local backup/restore, and a Basic/Pro entitlement
model backed by Google Play Billing.

CabComply is an independent record-keeping tool. It is not a council or government
application, is not officially approved by any licensing authority, and does not itself
guarantee legal or licensing compliance.

## Project structure

```
app/src/main/java/uk/co/cabcomply/app/
├── data/
│   ├── db/            Room entities, DAOs, database, type converters
│   ├── repository/     Repository layer (one per domain area) — the only thing UI talks to
│   ├── seed/            Predefined licensing authorities + default checklist (versioned)
│   ├── security/         PIN storage (PBKDF2, EncryptedSharedPreferences) + app-lock manager
│   ├── billing/          EntitlementManager (Basic/Trial/Pro/Grace/Expired) + FeatureGate
│   ├── pdf/              Weekly report data model + PdfDocument-based generator
│   ├── backup/           Zip (JSON manifest + photos) backup/restore, transactional
│   ├── notifications/    WorkManager-based expiry reminder worker + scheduling
│   └── files/            Photo import/compression/storage
├── di/                 Hilt modules (Room wiring; most classes use @Inject constructor)
└── ui/
    ├── theme/            Compose theme, colours, typography
    ├── navigation/        NavHost, bottom bar, app-root (onboarding vs. home decision, lock overlay)
    ├── components/        Shared building blocks (SectionCard, StatusChip, DateField, dialogs…)
    └── onboarding/, home/, dailycheck/, history/, mileage/, defects/, documents/,
        vehicles/, officer/, settings/, reports/, lock/   One package per feature area,
        each with its own Compose screens and Hilt ViewModels.
```

Architecture notes:
- **Checklist versioning**: every completed inspection stores the id, name and version of the
  checklist used, plus a snapshot of each item's text — a future checklist update never
  rewrites history.
- **Historical snapshots**: inspections store vehicle registration, driver name and licensing
  authority name at the time of the check, so later profile edits never make old reports
  misleading.
- **Transactions**: completing a daily check (inspection + results + defects + photo
  attachments + updated vehicle odometer) happens inside one Room transaction.
- **Entitlement**: `FeatureGate` is the single place Basic/Pro differences are decided; screens
  never hardcode tier checks.

## Building

This project is a standard Gradle/AGP Android app (Kotlin 2.0.21, AGP 8.5.2, compileSdk 35,
minSdk 26, Jetpack Compose, Room, Hilt). To build it:

```
./gradlew assembleDebug
```

You will need Android Studio (or the Android command-line SDK) with `compileSdk 35` and
`build-tools` installed, since the app depends on AndroidX/Compose/Room artifacts hosted on
Google's Maven repository (`dl.google.com`).

**This repository was written in a sandboxed environment with no Android SDK and no network
access to `dl.google.com`, so the build could not be compiled or run here.** The code was
written carefully and checked with static analysis (brace/paren balancing across every file,
duplicate class/function detection, import-usage cross-checks, a full manual read-through of
the most complex screens), but a real `./gradlew assembleDebug` — and a run on a device or
emulator — has not been performed. Please build it locally as the first step and treat any
compiler errors as normal follow-up work for a project of this size, not a sign the
architecture is wrong.

## What's simplified for this first pass

A handful of areas are implemented at a solid, working baseline rather than gold-plated, since
the full 95-section specification is effectively a multi-week product build:

- **Checklist content**: one generic, versioned "Standard Daily Vehicle Check" is seeded
  (grouped into the categories the spec lists). The data model fully supports per-authority
  checklists and custom checklists (`ChecklistEntity.licensingAuthorityId` /`isCustom`); only
  the UI to *author* a custom checklist isn't built yet.
- **Play Billing**: `BillingRepository`/`EntitlementManager` are wired to a real
  `cabcomply_pro_monthly` subscription product and correctly acknowledge purchases, but a
  live purchase obviously can't be tested without a Play Console listing and a device.
- **History/Mileage filtering**: vehicle + simple date-range filters are implemented; a full
  custom date-range picker isn't.
- **CSV export** and other "advanced" Pro export formats are not implemented — only the PDF
  report and the JSON+photos backup file.
- App icon, notification icon and in-app logo are simple original vector artwork (a shield +
  checkmark badge), not a professionally designed brand mark.
- No automated tests are included yet.

## Privacy & data

All data is stored locally in the app's private Room database and files directory. Nothing is
uploaded anywhere by the app itself. Backups are plain files the driver explicitly creates and
controls via Android's Storage Access Framework.
