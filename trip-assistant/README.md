# Trip Decision Assistant

A native Android decision-support tool for UK private-hire drivers. When Uber shows a trip offer,
it reads the offer from the screen, works out what the trip is actually worth once the unpaid
pickup is counted, compares that with rules the driver set, and shows **GOOD / BORDERLINE / POOR**
— or **CAN'T READ** — in a small floating window, within about a second.

It never taps Accept or Decline, never automates the Uber app, and never sends a screen image
anywhere. The driver decides; this tool just removes the mental arithmetic.

Trip Assistant is an independent product. It is not produced, endorsed or supported by Uber.

---

## Project layout

Two Gradle modules, split along the line that matters: **everything that decides whether a trip is
worth taking is plain Kotlin with no Android dependency.**

```
trip-assistant/
├── core/          Pure Kotlin/JVM. No Android. 99 unit tests, runs in about a second.
│   └── uk/co/tripassistant/core/
│       ├── model/         Offers, metrics, rules, recommendations, confidence
│       ├── text/          OcrText + normalised 0..1 boxes, safe OCR digit repair
│       ├── parser/        Screen classifier, versioned Uber layout parsers, registry
│       ├── validation/    Sanity + contradiction checks
│       ├── economics/     £/mile, £/hour, pickup proportion
│       ├── rules/         Green/amber/red, GOOD/BORDERLINE/POOR/UNKNOWN, reason text
│       ├── dedupe/        Offer fingerprinting
│       ├── outcome/       Accept-only outcome detection
│       ├── entitlement/   Trial, subscription states, offline allowance
│       ├── format/        £/mi, £/h, miles, ratings — one source of truth
│       └── pipeline/      OfferAnalyzer: recognised text in, recommendation out
└── app/           Android: capture, OCR, overlay, storage, billing, Compose UI
    └── uk/co/tripassistant/app/
        ├── capture/       MediaProjection, frame-change detection, image conversion
        ├── ocr/           ML Kit bridge (bundled model) -> core's OcrText
        ├── pipeline/      Live pipeline: record, alert, drive the overlay
        ├── overlay/       Floating window (plain views), haptics/sound
        ├── service/       Foreground service, notification, shared assistant state
        ├── data/          Room, DataStore, repositories, Play Billing, entitlement
        └── ui/            Compose screens, theme, navigation
```

The point of the split is spec section 62: **an Uber layout change should only ever touch
`core/parser/`.** Nothing in the scoring, storage, overlay or UI knows what an offer card looks
like.

### The pipeline

```
MediaProjection frame
  → frame-change detection + throttle   (skip still screens; ~3 analyses/sec when moving)
  → ML Kit text recognition, on device  (bundled model, nothing uploaded)
  → OcrText, minus this app's own overlay region
  → screen classifier                   (not an offer? stay silent)
  → versioned layout parser             (UBER_UK_STANDARD_V1 / _V2)
  → validation                          (implausible or contradictory ⇒ UNKNOWN)
  → economics                           (£/mile, £/hour, pickup share)
  → rule engine                         (deterministic; no model, no learning)
  → overlay + de-duplicated history
```

`Observe → Read → Validate → Calculate → Evaluate → Display`. When any step is unsure, the answer
is UNKNOWN — never a guess dressed up as a recommendation.

---

## Building

Standard Gradle/AGP project: Kotlin 2.2.10, AGP 8.7.3, compileSdk 35, minSdk 26, Jetpack Compose,
Room 2.7, Hilt 2.57, ML Kit Text Recognition, Play Billing 9.1.0.

The Kotlin version is not a free choice: Play Billing 9.1.0 is compiled with Kotlin 2.2.10 and
brings that stdlib with it, so anything older fails in `kspDebugKotlin` on incompatible metadata.
KSP, Hilt and Room follow from it — see the comment at the top of `build.gradle.kts`.

```bash
cd trip-assistant
./gradlew :core:test        # decision logic — no Android SDK needed
./gradlew assembleDebug     # needs the Android SDK and access to dl.google.com
```

Optional build configuration:

```bash
./gradlew assembleRelease -PentitlementBackendUrl=https://your-entitlement-service.example
```

With no entitlement URL configured the app verifies subscriptions against Google Play alone and
says so on the subscription screen, rather than implying a level of verification it is not doing.

### Build and verification status — please read

This repository was written in a sandbox with **no Android SDK and no network access to
`dl.google.com`**, so the `:app` module has never been compiled or run.

What that means in practice:

| | |
|---|---|
| `:core` — parsing, validation, economics, rules, entitlement | **Compiled; 99 unit tests pass.** Run `./gradlew :core:test`. |
| `:app` — capture, OCR, overlay, Room, billing, Compose UI | **Not compiled.** Written carefully and statically checked (imports resolved against declared symbols, brace balance, every `R.string`/`R.color`/`R.drawable` and view-binding field cross-checked against the resources), but a real `assembleDebug` has not run. |

Treat compiler errors in `:app` on the first build as normal follow-up work for a project this
size, not a sign the architecture is wrong. The one file most likely to need attention is
`data/billing/PlayBillingDataSource.kt` — see SPEC_COMPLIANCE.md.

---

## Testing

`core/src/test` holds the two suites the specification asks for:

* **`ParserSamples.kt`** — the permanent sample library (spec section 55). Each entry is the
  recognised text of one sanitised offer screen plus the values that screen is known to contain.
  The `UK_2026_*` entries are transcribed from **real UK Uber Driver cards** — fares, distances,
  times and ratings exactly as they appeared, with addresses replaced by fictional ones of the
  same shape. The rest cover the stacked layout, an unlabelled compact card, kilometres, damaged
  OCR, a promotion line, a missing rating, and two screens that are not offers at all.
  **When Uber changes its interface, add a sample — never edit an existing one.**
* **`RealWorldCardTest.kt`** — the regressions the real cards exposed: the
  "£8.85 + est. holiday pay of £0.19" line being taken as the fare, a rating-shaped fare
  ("£4.62") being read as a rating, a lost star glyph downgrading every offer, the
  "1 mi from fast charger" strip disturbing the legs, and the accepted screen (same card under
  "Matched" / "Let's go") never recording an outcome.
* **`RuleEngineTest.kt`, `OfferValidatorTest.kt`, `EconomicsEngineTest.kt`,
  `EntitlementPolicyTest.kt`, `OfferFingerprintTest.kt`, `OutcomeDetectorTest.kt`,
  `OfferAnalyzerTest.kt`** — the scoring and billing cases of spec sections 56 and 57, including
  every tolerance boundary, hard/soft combinations, each UNKNOWN path, and every subscription
  state from trial through grace, hold and resubscription.

Device-condition testing (spec section 54) and several-hour battery/stability runs (spec
section 53) require real hardware and are not automated here.

---

## Privacy

* Screen frames are analysed on the device and discarded immediately. None is written to storage.
* No screen image or recognised screen text leaves the phone. There is no cloud OCR.
* History stores the economics of an offer and the decision made about it — fare, distances,
  times, rating, rates, recommendation, reason, profile, outcome. It does not store screenshots,
  rider names, messages, or pickup and destination addresses.
* The only thing sent anywhere is subscription information: Google Play handles payment, and a
  purchase token may go to the entitlement service over HTTPS to confirm the subscription is live.
* No analytics or crash-reporting SDK is included in this build.

A published Privacy Policy and a Play Data Safety declaration still have to be written to match
this behaviour before release; the in-app disclosure is at Settings › Privacy.

---

## What is not here

* The entitlement backend itself (a small server that calls the Play Developer API). The app's half
  of it — the client, the request/response contract and the offline allowance — is implemented.
* Play Console artefacts: store listing, subscription products, Data Safety form, Privacy Policy
  URL.
* Instrumentation and UI tests.
* A designed brand mark. The launcher icon is original vector artwork (a rising route line), good
  enough to build against and deliberately unlike any rideshare operator's branding.

See **SPEC_COMPLIANCE.md** for every place the implementation interprets or departs from the
specification, and why.
