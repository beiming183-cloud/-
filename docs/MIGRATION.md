# Migration from the legacy APK

This document describes an architectural migration, not a binary patch.  The
supplied APK is treated as a behavioral reference only.  Its package, signing
key and private storage are not part of this project, and no code or resources
are copied from it.

## What changed in the input path

| Legacy concern | Clean-room boundary in this project |
| --- | --- |
| Per-button listener committed after touch-up | Central touch-down hit testing and `CnCwTouchRouter` |
| Strings passed into a formula view | Typed `CalculatorKey` intents and semantic tokens |
| Formula editor owned interaction rules | `CnCwMachine` + immutable `CnCwUiState` snapshot |
| ES/EX behavior mixed in one screen | Explicit 991 capability profile and separate EXE/relation intents |
| Dozens of views, pagers and nested scrollers | One retained canvas with reusable hit rectangles |
| MP3 click assets and startup work | System haptic feedback before reducer work; no bundled click assets |
| Generic CAS on the main path | Small clean-room engines in `:core`, with a worker boundary for heavy modes |
| Ads, analytics, remote config and network startup | No network/tracking dependencies in the calculator path |

The retained canvas now hosts the CN CW Home grid, physical-control area, menus,
application selectors and Calculate editor.  Structured engines are reachable
through compact comma-entry workflows, while the manual's dedicated data
grids, coefficient editors and paged result screens remain scaffolding.

## Product migration

The new application installs alongside the legacy package because it uses
independent IDs:

```text
CN Scientific 991  -> com.codex.cnscientific.calculator991
```

The single published variant consumes the shared `:core` library.  `cn991` has
the ten 991 applications.  The model contract is generated at build time, so
the menu layer selects capabilities without runtime package-name heuristics.

Current release variants and commands:

```powershell
./gradlew.bat :core:regressionTest
./gradlew.bat :app:assembleCn991Debug
```

The resulting debug APK is under `app/build/outputs/apk/cn991/debug/`
(AGP may include the variant name in the exact filename).

## Staged feature migration

1. **Input contract** — touch-down ordering, two-key rollover, semantic
   deletion, EXE/relationship separation and one-shot modifier behavior are in
   place; physical-device timing still needs verification.
2. **CN CW shell** — the home grid, Home/Back/OK/EXE/directional navigation,
   catalog relation symbols, ÷R/DMS utilities and first-pass settings/tools
   menus are in place; complete each manual single-select page and accessibility
   state.
3. **Structured expression document** — connect MathI/MathO slots, exact
   rational/surd output and manual display settings to the renderer.
4. **Mode coordinators** — compact Calculate, Statistics, Function Table,
   Equation, Inequality, Complex, Base-N, Matrix, Vector and Ratio bridges are
   in place; replace them with the manual's typed
   table/grid/coefficient sessions.
5. **Worker/persistence layer** — isolated snapshot evaluation with revision
   cancellation is in place; add typed `CalcValue` answers and a documented
   export format after the session contracts stabilize.
6. **Validation** — run manual-derived golden traces, differential numeric
   checks, accessibility checks and device performance measurements.

The current status of each stage is maintained in
[FEATURE_COVERAGE.md](FEATURE_COVERAGE.md).  A core engine row is not promoted
to “UI wired” until a user can enter data, navigate the mode, see errors and
return to Home/Calculate without test-only hooks.

## Data and settings compatibility

The legacy app's preferences/history are not imported.  Its private schema is
not a stable public contract, and guessing at it risks silently changing user
calculations.  When persistence is added, use a versioned JSON export with:

- model ID and app version;
- calculation settings and angle unit;
- semantic expression/history records;
- explicit locale/decimal-mark metadata;
- checksums and length limits for imported records.

Import must validate the 991 capability contract before opening a record.

## Clean-room and branding policy

The implementation uses the manual's public behavior and mathematical
definitions, then reimplements parsing, state transitions, rendering and
algorithms independently.  It does not include vendor firmware, vendor logos,
vendor fonts, the supplied APK's resources, `naturalcalc`, Symja, or code with
unclear licensing.  Product names in this repository are the independent
“CN Scientific” labels; trademark ownership remains with the respective owner.

## Full-parity gate

Do not describe a release as “1:1” until every manual application has a wired
screen, every documented limit/error has a golden test, and results have been
checked against a physical reference.  The current release is an
architecture/core-engine milestone with compact UI workflows; dedicated manual
editors, complete answer typing and physical-device differential validation are
still pending.
