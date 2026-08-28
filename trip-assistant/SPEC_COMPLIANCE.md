# Specification compliance and interpretation record

Spec section 64 makes the specification the source of truth and asks that any change to a
functional requirement be **documented rather than silently altered**. This file is that record.

Everything below is either (a) a place where the specification says two things that cannot both be
true and a choice had to be made, (b) a technical decision that leaves the visible behaviour
compliant, or (c) something the specification asks for that is not in this repository.

---

## A. Decision-logic interpretations

### A1. A HARD rule fails when its metric is RED, not merely when it misses target

Spec section 19 says a HARD rule failing makes the offer POOR. Spec section 20 defines an amber
band around every target. Spec section 21 then lists *"one or more enabled metrics are AMBER"*
under **BORDERLINE**, with the only precondition being *"no HARD rule fails"*.

Those three only fit together one way: an AMBER result on a HARD rule is not a failure. So:

* HARD rule RED → POOR.
* HARD rule AMBER → contributes to BORDERLINE like any other amber metric.

Covered by `RuleEngineTest.a hard rule inside the amber band does not force POOR`.

### A2. A HARD rule whose value Uber did not display returns UNKNOWN

The specification does not say what happens when a driver marks "minimum rider rating" as a
dealbreaker and Uber shows an offer with no rating on it.

Treating it as a pass would be exactly the failure mode spec section 63 exists to prevent — a
dealbreaker silently unchecked, on a screen that then shows GOOD. So an active HARD rule whose
metric is missing returns **UNKNOWN**, with the diagnostics naming the field that was not shown.

### A3. A SOFT rule whose value is missing caps the result at BORDERLINE — it does not force UNKNOWN

This is the one place the implementation departs from a worked example in the specification.

Spec section 44's diagnostic example shows `Trip time: NOT FOUND` producing `Result: UNKNOWN`.
Taken as a general rule — *any* missing value that an enabled rule needs makes the offer
unreadable — that would make the product unusable in a common, legitimate case: spec section 14
itself notes that not every offer type displays every field, and a rider with no rating yet is
ordinary. A driver with the default profile would see UNKNOWN on every such offer and learn to
ignore the app.

Implemented instead:

* the offer is scored on what *was* read;
* the unchecked rule is recorded as `NOT_EVALUATED` and named in the reason line
  ("Rider rating not shown");
* the recommendation is **capped at BORDERLINE** — it can never be GOOD, which is what spec
  sections 49 and 63 actually require.

**The spec's example is reproducible exactly**: mark the £/hour rule as a dealbreaker (HARD) and a
missing trip time returns UNKNOWN, per A2. The difference is that it becomes the driver's choice
rather than a fixed behaviour.

### A4. A partly-read offer can never be GOOD

Where the parser had to assume something — it worked out pickup from trip by screen position
because no "away"/"trip" wording was read, or accepted a rating with no star next to it, or chose
between two plausible fares — the offer is marked PARTIAL confidence and capped at BORDERLINE
(spec section 49: *"Never show GOOD based on incomplete data"*). A deterministic repair, such as a
unit conversion or a digit correction that then re-validated, does **not** count as an assumption
and does not degrade confidence (spec section 15 explicitly permits those).

### A5. The global amber tolerance also applies to the rider-rating rule — flagged for review

Spec section 20 makes the tolerance a single global percentage. Applied to a rating, 10% of a 4.75
target is a band down to 4.28, which is much wider than a driver setting "minimum 4.75" probably
intends.

Implemented as specified — global, no special case. Raised here as a product question: if a
per-rule tolerance, or an absolute rather than proportional band for ratings, is wanted, that is a
change to `RuleEngine.statusFor` and the profile data model, and should be a decision rather than
a quiet fix.

### A6. Screens that are not offers produce no recommendation at all

Spec section 49 lists "Uber screen layout unsupported → UNKNOWN". Spec section 48 asks for nothing
that draws prolonged attention while driving. Flashing UNKNOWN at a driver looking at the map for
twenty minutes would satisfy the first and violate the second.

The screen classifier therefore distinguishes three cases:

| Classification | Behaviour |
|---|---|
| Not offer-like at all (map, earnings, waiting) | Overlay stays in its waiting state; nothing is recorded |
| Offer-shaped but no parser matched | **UNKNOWN — "Offer format not recognised"** |
| Offer parsed | Validate, score, display |

The specification's UNKNOWN case is the middle row, which is the one it was describing.

### A7. The last result is held for a few seconds after the card disappears

Offer cards redraw, and a frame where the card is mid-animation would otherwise flip the overlay
back to "waiting" and then back again. The last recommendation stays on screen for 12 seconds after
the offer stops being visible. This is presentation only; nothing extra is recorded.

---

## B. Duplicate protection, outcomes and history

### B1. The de-duplication time window is applied at lookup, not baked into the fingerprint

Spec section 29 lists a time window among the fingerprint inputs. Including it in the hash would
mean the same offer read either side of a window boundary produced two rows — the exact problem
the section is about. The fingerprint is therefore computed from the quantised offer values only,
and the three-minute window is applied when looking for a match. A genuinely identical offer seen
again an hour later is still recorded as its own trip.

A second, tolerant comparison (`OfferFingerprint.isSameOffer`) catches OCR jitter that lands either
side of a quantisation boundary.

### B2. Outcome detection can only ever report "accepted"

Spec section 30 is explicit that an offer disappearing proves nothing. `OutcomeDetector` has no
code path that returns "declined": it matches a short list of phrases that appear on in-trip
screens and nowhere else ("cancel trip", "slide to start", "arrived at pickup"), and only within 90
seconds of the offer being recorded. Everything else stays `UNKNOWN_OUTCOME`, and the statistics
screen reports "Accepted outcome detected for N of M offers" rather than an acceptance rate.

### B3. UNKNOWN evaluations with no economics are not written to history

An offer that could not be read far enough to produce a fare and a distance has nothing to store in
the history schema and would only distort the averages. It is surfaced on the diagnostics screen
instead. An UNKNOWN that *does* have economics — for example one caused by an unverifiable HARD
rule (A2) — is recorded normally.

---

## C. Subscription and entitlement

### C1. There is an install trial as well as a Play introductory offer

Spec section 3 promises a 14-day full free trial; spec section 4 lists a 14-day introductory offer
phase on the Play subscription; spec section 6 asks for no account and minimal onboarding friction.
A Play introductory offer requires the driver to enter payment details on day one, which is at odds
with section 6.

Both are supported. The trial starts when the driver first opens the app, needs no card and no
account, and `EntitlementPolicy` also honours a Play trial phase if the subscription carries one.

**Known limitation:** in V1 the install trial is device-local, so uninstalling and reinstalling
restarts it. The anonymous install id (spec section 6) is already generated and sent to the
entitlement service, which is where that would be closed when the backend exists.

Trial length and the offline allowance are `EntitlementConfig` values, not constants sprinkled
through the code, so moving to 7 days is a one-line change (spec section 3).

### C2. A Play purchase with no known expiry is honoured

Google Play tells the app that a purchase exists; only the Play Developer API can say when it
expires or whether it is in a grace period or on hold, and that needs a service account, so it has
to live on a server. With no entitlement service configured, `expiryTimeMillis` is null and the
policy treats a live Play purchase as entitled rather than locking a paying driver out. The offline
allowance still bounds how long an unrefreshed claim survives.

The subscription screen states which of the two is in force, rather than implying verification that
is not happening.

### C3. Losing a subscription never removes data

Entitlement gates one thing: live evaluation. History, profiles and settings are untouched by every
path in `EntitlementRepository`, and the entitlement cache is excluded from cloud backup so a
restore cannot resurrect a stale "subscribed" claim.

---

## D. Technical decisions (visible behaviour unchanged)

### D1. Two Gradle modules, not one per layer

Spec section 62 lists nine layers. They exist as strictly separated packages, split across two
modules along the line that actually matters: `:core` has no Android dependency at all, which is
what makes the decision logic testable in milliseconds and enforces the separation the section is
asking for. Nine Gradle modules would add build complexity without adding a boundary.

### D2. The overlay is built from plain Android views, not Compose

The floating window lives outside any Activity, is recreated on rotation, and must redraw in a few
milliseconds while OCR is running on the same device. Plain views keep it cheap and predictable and
avoid hand-rolling the lifecycle/saved-state plumbing a `ComposeView` needs in a service-owned
window. The rest of the app is Compose, as specified.

### D3. The OCR sample library stores recognised text, not screenshots

Spec section 55 asks for a permanent library of sanitised screenshots with expected values.
Committing screenshots of real offers to a repository is a privacy problem even after sanitising,
and the parser never sees a pixel — its input is recognised text. `ParserSamples.kt` therefore
stores the recognised text of each layout plus its expected values, which is what the parser
actually consumes and what a regression test needs. The rule that matters is kept: **add a sample
when Uber changes, never edit an existing one.**

If a screenshot-level suite is wanted later (to test the ML Kit step itself, not the parser), it
belongs in `androidTest` on device, with the images held outside version control.

### D4. Reason and status text lives in `:core`, not `strings.xml`

The overlay, the history row and the rule tester must never disagree about what an offer was worth,
so the formatting is in one place. V1 is a UK/GBP English product (spec sections 1 and 15).
Localisation would mean moving `Formats` and `ReasonBuilder` output behind a string-resource
interface; the overlay's own status words (GOOD / BORDERLINE / POOR / CAN'T READ) are already in
`strings.xml`.

### D5. Play Billing 9.1.0 dictates the Kotlin version

Spec section 4 names 9.1.0 as the current library, and Play requires a recent one for new releases,
so that is what `app/build.gradle.kts` pins. That choice is not free: billing 9.1.0 is compiled with
Kotlin 2.2.10 and brings that stdlib with it, and a Kotlin 2.0.x compiler cannot read 2.2 metadata —
the build fails in `kspDebugKotlin` with "module was compiled with an incompatible version of
Kotlin". The first real build hit exactly this.

The rest of the toolchain follows from that one constraint:

| | | |
|---|---|---|
| Kotlin | 2.2.10 | the stdlib Play Billing 9.1.0 ships |
| KSP | 2.2.10-2.0.2 | the KSP2 build for that Kotlin — no KSP1 build exists for 2.2.x |
| Hilt | 2.57.2 | its processor targets KSP API **2.0.2**, the same generation. 2.51.1 targets KSP 1.0.x and cannot work with Kotlin 2.2 |
| Room | 2.7.2 | 2.6.x predates KSP2 |
| AGP | 8.7.3 | supports compileSdk 35 without the warning 8.5.2 emits |

Only the Kotlin, KSP and Hilt versions could be verified from this environment (Maven Central is
reachable, Google's Maven is not). The Room, AGP and Compose BOM pins are chosen but unverified.

`PlayBillingDataSource.kt` is still the **only** file that imports the Play SDK, behind the
`BillingDataSource` interface, so a difference in its surface is a single-file fix.

### D6. compileSdk / targetSdk 35 with AGP 8.5.2

Spec section 7 requires the latest Play-compliant SDK **at release time**. This build pins 35 with
AGP 8.5.2, matching the known-good toolchain in this repository. Raising compile/target SDK to
whatever Play requires on the release date is a build-file change plus a pass over the
`Build.VERSION` guards in `AssistantService` and `OverlayController`.

### D7. Screen capture is downscaled

The virtual display is created at a maximum long edge of 1440px rather than the panel's native
resolution. Offer text is large, and a shift-long capture at 1440p is a battery and memory problem
(spec section 53). Parsers work in normalised 0..1 coordinates, so nothing downstream is affected.

---

## D8. The parsers are now written against real cards, not assumptions

The first version of `core/parser` was written without ever having seen an Uber offer card. Real
UK cards were supplied afterwards and the leg format turned out to be right —
`12 mins (4.3 mi) away` / `10 mins (4.0 mi) trip` — but four things were wrong or fragile, and each
is now a named test in `RealWorldCardTest.kt`:

1. **The holiday-pay breakdown.** UK cards show `£8.85 + est. holiday pay of £0.19` under the
   headline fare. Both amounts were fare candidates; only text prominence kept the right one
   winning. "holiday pay" and "est." are now fare-exclusion keywords.
2. **A rating-shaped fare.** `£4.62` matches the shape of a rating exactly, so a cheap trip could
   report a rider rating that was never on screen. Rating detection now skips any line carrying
   money or a unit.
3. **A lost star glyph.** Rating detection required `★`, which ML Kit's Latin recogniser has no
   obligation to return. A mangled glyph meant PARTIAL confidence, which caps at BORDERLINE — so
   every offer would have looked borderline and the product would have seemed broken. A
   rating-shaped number alone on its line is now a confident read whatever decorates it.
4. **The accepted screen.** Accepting replaces the "Confirm" card with the *same card* under a
   "Matched" heading and a "Let's go" button. Outcome detection only ran on screens that did not
   parse as an offer, so an accepted trip was never recorded as accepted. The signal is now checked
   before parsing, and both phrases are evidence (spec section 30).

Still unseen, and therefore still guesses: Reserve/scheduled offers, multi-stop trips, a stacked
offer arriving mid-trip, surge and guarantee wording, and non-UK layouts.

## E. In the specification, not in this repository

| Spec section | Item | Status |
|---|---|---|
| 5 | The entitlement backend service itself | Not included — it is a server, not part of the Android app. The client, the request/response shape and the offline allowance are implemented. |
| 4 | Play Console subscription products, base plans, offer phases | Product ids are defined (`trip_assistant_monthly`, `trip_assistant_annual`); the Console setup is an operational task. |
| 50 | Published Privacy Policy document and URL | The in-app disclosure is written (Settings › Privacy) and matches the code. The hosted policy and the Data Safety declaration still have to be authored. |
| 54 | Device-condition matrix, rotation, revocation, lock/unlock | Handled in code (configuration changes, projection callbacks, permission loss) but requires real hardware to verify. |
| 53 | Several-hour battery, memory and stability runs | Designed for (throttling, change detection, downscaled capture, explicit teardown on every exit path); not measured. |
| 55 | ML-Kit-level image tests | See D3. |
| 51, 52 | Analytics and crash reporting | Deliberately absent from V1, as the specification suggests. Before adding either, confirm the SDK cannot attach screen content, and keep purchase tokens and recognised text out of logs — `proguard-rules.pro` already strips verbose/debug logging in release. |
| — | Automated instrumentation and UI tests | Not written. |
| 1 | Final commercial name and designed brand mark | Working title "Trip Assistant" and original placeholder vector artwork. |

---

## F. Requirements verified by automated test

Every one of these is a passing test in `core/src/test` (`./gradlew :core:test`):

* section 17 — total distance, total time, effective £/mile, passenger £/mile, £/hour, pickup
  proportion, including the worked examples;
* section 20 — green/amber/red boundaries for minimum and maximum rules, both worked examples,
  configurable tolerance;
* section 21 — every path to GOOD, BORDERLINE, POOR and UNKNOWN;
* section 22 — the full worked example, end to end from recognised text to overlay strings;
* section 23 — the exact reason wording of all three examples, and reason ordering;
* section 16 — missing mandatory fields, out-of-range values, contradictory values, and the
  "a rating must not become mileage" case;
* section 29 — fingerprint stability, jitter tolerance, and distinguishing genuinely different
  offers;
* section 30 — accepted detected from evidence; never inferred from an offer disappearing;
* section 55 — every layout sample parses to its recorded expected values;
* section 56 — the complete list, including no division by zero anywhere;
* section 57 — new user, trial start, trial countdown, trial expiry, configurable trial length,
  paid, cancelled-but-valid, grace, hold, paused, offline allowance, configurable allowance,
  never-verified, recovery, resubscription;
* section 27 — the app's own overlay is excluded from the frame before anything is read.
