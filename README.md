# CN Scientific Calculator

An independent, clean-room Android scientific-calculator rebuild focused on a
predictable physical-calculator interaction model: touch-down input, semantic
editing, two-key rollover and a retained display/keyboard canvas.  The supplied
manual is used as a behavioral specification; this repository does not contain
vendor firmware, branding assets or copied calculator source.

## Product

This release publishes only the requested 991 profile.  The shared `:core`
module remains model-aware so the reducer can be tested independently, but no
999 APK or 999 launcher entry is generated:

| Variant | Label | Application ID | Version code | Applications |
| --- | --- | --- | ---: | ---: |
| `cn991` | CN Scientific 991 | `com.codex.cnscientific.calculator991` | 320 | 10 |

Debug packages append `.debug` to the application ID so they can coexist with
their release build.

The UI receives the 991 capability contract through generated
`BuildConfig.MODEL_ID`, `MODEL_PROFILE`, `MODEL_LABEL`, `MODEL_EXTENDED`,
`HAS_DISTRIBUTION`, `HAS_SPREADSHEET` and `APPLICATION_COUNT` fields.  Keeping
the contract explicit prevents unsupported 999-only applications from leaking
into the 991 HOME grid.

The current build is version `0.3.8` (debug variants append `-debug`).  It is a
991 interaction/UI milestone, not a claim of 1:1 manual parity.  See the
[feature coverage matrix](docs/FEATURE_COVERAGE.md) for the exact boundary.

## Current implementation

- Android 8.0+ (`minSdk 26`), target/compile SDK 34.  API 26 is intentional:
  the shared Java core uses the Android 8 collection/unsigned-number surface;
  API 33-only `Math.fma` is isolated behind `Compat.multiplyAdd`.
- No ads, analytics, network permission, WebView, camera or startup SDKs.
- One hardware-accelerated canvas for the 991 HOME grid, menus, physical
  control deck, context keys, six-key function rows, five-column numeric rows
  and result display.
- State changes begin on touch-down; haptic feedback is issued before reducer
  work so a long calculation cannot delay the physical-key response.
- Two-key rollover preserves touch-down order when fingers release in either
  order.
- A 991-gated CN CW state machine with Home/Back/OK/EXE/directional
  navigation, separate relation `=`, semantic-token editing, DEL, SHIFT and
  in-memory history.
- Dependency-free engines for scalar expressions, exact rationals, statistics,
  function tables, equations/inequalities, complex numbers, Base-N, matrices,
  vectors and units.  Distribution and spreadsheet capabilities are not part
  of the 991 product surface.
- Compact comma-entry bridges make Statistics, Function Table, Equation,
  Inequality, Matrix, Vector and Ratio calculations reachable now; their
  dedicated table/coefficient editors remain a later UI layer.
- ÷R, DMS input, statement assignment, relation verification and engineering/
  coordinate utility APIs are independently implemented; ÷R/DMS/relations are
  reachable through the catalog.
- The current JVM gates pass 169 general regression checks, 53 CN CW machine
  checks, 20 structured-mode checks, 24 manual-utility checks and 26 dedicated
  991 semantic checks (295 assertions in these suites; some lower-level cases
  are intentionally shared).

The mode engines are deliberately separated from Android.  Every application
enabled for the cn991 product has a HOME entry and landing selector.  Structured
modes currently use a compact, deterministic comma-entry editor; the full
manual table/grid, coefficient, answer-page and MathI/MathO editors are still
release work.

## Build and verify

Use the bundled Gradle wrapper and a Java 17 runtime:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
./gradlew.bat :core:regressionTest
./gradlew.bat :app:assembleCn991Debug
```

For a clean verification/build pass:

```powershell
./gradlew.bat clean check :app:assembleCn991Debug
```

The APK is written below `app/build/outputs/apk/cn991/`.  A connected device is
not required for the JVM regression suite.  Device-level frame, accessibility
and manual-differential checks remain release gates.

## Architecture and migration

- [Architecture](docs/ARCHITECTURE.md) — state boundaries, release contract,
  worker model and performance invariants.
- [Feature coverage](docs/FEATURE_COVERAGE.md) — manual-derived status without
  overstating UI or verification.
- [Migration](docs/MIGRATION.md) — legacy-path replacement, staged mode wiring,
  data compatibility and clean-room policy.

## Why a rebuild

The supplied legacy package is `com.nstudio.calc.casio.business` (version
4.4.2), whose original signing key and exact source are unavailable.  Its
interaction path was approximately:

```text
touch-up listener -> keyboard fragment -> formula display -> generic editor
                  -> asynchronous evaluator
```

That hierarchy makes physical-calculator semantics, latency and model-specific
menus difficult to reason about.  This project instead owns the reducer and
typed input path, and keeps heavy calculations behind explicit core APIs.  No
GPL or unclear-license ancestor is linked into the product.

## Provenance and trademarks

Mathematical definitions and observable behavior are derived from the supplied
official manual and general numerical knowledge.  Code, tests, UI assets and
branding are authored independently.  “CN Scientific” is this project's label;
any third-party trademarks remain the property of their owners.  This is not an
official vendor product.
