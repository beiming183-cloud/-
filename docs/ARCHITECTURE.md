# Architecture

This repository is a clean-room implementation of a scientific-calculator
interaction model.  It is not a repackaged firmware image and it does not link
the supplied APK, a CAS library, or a third-party calculator project.  The
manual defines observable behavior and limits; the code, state transitions and
algorithms are authored here.

## Product boundary

The Android module now publishes one 991 variant from the shared core:

```text
cn991 Android variant
          |
          +-- retained canvas Android adapter (app)
          |
          +-- :core (pure Java, no Android dependency)
                 |
                 +-- immutable input/reducer state + CN CW shell machine
                 +-- scalar, exact, matrix/vector and statistical engines
                 +-- model/capability and settings contracts
```

`cn991` exposes the ten applications listed for the 991 profile.  Distribution
and Spreadsheet remain outside the published product surface.  The boundary is
declared in Gradle and generated `BuildConfig` constants (`MODEL_ID`,
`MODEL_PROFILE`, `MODEL_LABEL`, `MODEL_EXTENDED`, `HAS_DISTRIBUTION`,
`HAS_SPREADSHEET`, `APPLICATION_COUNT`); the UI consumes that contract instead
of inferring features from a package name.

The published ID is `com.codex.cnscientific.calculator991`.  The Java namespace
remains an internal compatibility detail; it is not a claim of affiliation
with any calculator vendor.

## Interaction pipeline

The latency-sensitive path has one owner per concern:

```text
ACTION_DOWN / POINTER_DOWN
        |
        v
  Touch router  -- typed CnCwKey -->  CnCwMachine / mode reducer
                                             |
                                             v
                                      immutable UI snapshot
                                             |
                                evaluator API / cancellable job
                                             |
                                             v
                                      canvas render + feedback
```

The UI never appends an arbitrary string to a text widget and never owns
evaluation semantics.  `CnCwMachine` maps a typed key to a new UI snapshot;
domain calculations are delegated to core engines.  A transition carries
semantic tokens, cursor, modifier, angle unit, display settings, screen state,
`Ans`, history and undo data.  The touch router commits on touch-down, accepts
at most two active pointers, and preserves down order when fingers are released
in the opposite order.

The current Android shell renders a 3×2 paged HOME grid, a dedicated physical
control deck, central directional/OK navigation, a five-key context row and
six-key function/five-key numeric keypad groups on the retained canvas.
`PhysicalKeyLayout` owns key legends and both hit geometry and visual geometry;
`Cw991LayoutMetrics` in `:core` supplies platform-neutral keycap/font ratios.
`CalculatorView` paints visual bounds but routes through touch bounds, so a
tall phone can keep large touch targets without stretching the keycaps or
silently redefining reducer semantics.  Calculate,
The physical map keeps the fraction-template key separate from arithmetic
division and routes one-shot SHIFT through the reducer, including variables,
complex `i/∠`, derivative/integral commands and the CW function legends.
EXE balances only unmatched opening parentheses; incomplete operators remain
syntax errors so permissive editing does not hide logical mistakes.
Complex, Base-N and the compact structured bridges can reach an evaluator.
Most structured modes still use a comma-entry editor rather than the manual's
dedicated grids/coefficient pages.  New screens must consume the same state boundary and must not
reintroduce per-button listeners, nested formula views, or blocking calculation
work on the main thread.

## Core module

`:core` is dependency-free Java 17 and is runnable in the JVM regression task.
Its main boundaries are:

- `CalculatorState`, `CalculatorReducer`, `CalculatorKey` and
  `KeyInputRouter`: the earlier deterministic COMP regression path retained as
  a compatibility/performance reference.
- `CnCwMachine`, `CnCwUiState`, `CnCwScreen`, `CnCwKey` and `CnCwSettings`:
  model-gated Home/menu/application navigation and first-pass Calculate state.
- `CnCwModel`, `ApplicationMode` and `CalculatorSettings`: manual-derived
  capability and calculation-setting contracts.
- `ScalarExpressionEngine`, `Rational`, `NumericAnalysis` and
  `ScientificConstants`: common scalar/exact/numeric operations.
- `StatisticsEngine`, `DistributionEngine`, `FunctionTableEngine` and
  `SpreadsheetModel`: mode engines with explicit input limits.
- `ComplexValue`/`ComplexExpressionEngine`, `MatrixValue`, `VectorValue`,
  `BaseNEngine`, `PolynomialEngine` and `UnitConverter`: independently tested
  domain primitives.

`DistributionEngine` and `SpreadsheetModel` remain reusable internal core
components, but they are disabled by the cn991 capability contract and are not
part of the version 0.3.1 product or its manual-parity claim.

An engine is not allowed to own Android views or persistence.  It accepts
validated values and returns immutable records or value objects.  Mode
coordinators are responsible for converting editor state into those calls and
for mapping errors to manual-style result screens.

## Expression and exact-value boundary

Input is represented as semantic tokens first.  The long-term MathI/MathO
document model should represent slots rather than a string:

```text
Fraction(numerator, denominator)
Root(index?, radicand)
Power(base, exponent)
Integral(integrand, variable, lower, upper)
Sum(body, variable, lower, upper)
```

The serializer and canvas layout engine can then render fractions and stacked
templates without making the evaluator's output editable text.  `Rational`
and the numeric engines are the current foundation; exact surds, symbolic pi
forms and a complete formatter still need to be connected to this document
boundary.

## Heavy-work boundary

Fast navigation/editing executes inline.  EXE evaluation runs through an
isolated, cancellable snapshot boundary in the Android adapter:

```text
FastNumericEngine  -> common arithmetic and small functions
ExactEngine        -> rational/surd/pi representation
MatrixEngine       -> matrix/vector operations
ModeWorker         -> cancellable mode calculation
```

`CnCwMachine.copyForEvaluation()` deep-copies tokens, variables, history and
other mutable structured-mode state.  Each job carries an input revision; a
result whose revision no longer matches the live editor is discarded, so a slow
calculation cannot overwrite newer input.  `AC` cancels the future immediately
and leaves the live reducer available for editing.  This is a practical worker
boundary, not yet a fully typed `Effect`/`CalcValue` system;
complex/matrix/vector answer storage remains a later architecture gate.

## Performance invariants

- Touch-down feedback is issued before reducer/evaluator work; hit-state
  invalidation is scheduled in the current frame.
- Key insertion performs one reducer transition and one
  `postInvalidateOnAnimation()`; it does not parse or allocate a view tree.
- Paint objects and hit rectangles are retained and recomputed only after a
  size change.
- Ordinary reducer transitions target below 0.25 ms on a mid-range device.
- The JVM editing guard remains a broad regression check, not a device frame
  guarantee.  Device metrics and cold-start measurements are release gates.

## Security and provenance

No network, analytics, ads, WebView, camera, or startup SDK is required by the
calculator path.  No Casio firmware, logo, font, APK resource, `naturalcalc`,
Symja, or other third-party calculator source is copied into this project.
Mathematical formulas are general knowledge; implementation and tests are
written independently.

## Release gates

Version 0.3.1 is an intermediate cn991 release and is not a complete 1:1
implementation of the 991 manual.  Before describing cn991 as full manual
parity, complete all of the following:

1. Complete and test the CN CW settings/catalog actions, then wire each
   specialized mode editor and reducer behind the existing Home grid.
2. Add golden key traces and boundary/error cases from every relevant 991
   manual page.
3. Differentially compare numeric, exact, complex, Base-N, matrix, vector,
   statistics, function-table, equation, inequality and ratio results on a
   physical 991 reference.
4. Verify the 991 capability boundary, APK ID, accessibility, portrait layout
   and Android 8/11/14/16 behavior (the published minimum is API 26).
5. Measure touch latency, frame time, cold start, long-expression editing and
   two-key rollover on representative devices.

See [FEATURE_COVERAGE.md](FEATURE_COVERAGE.md) for the current, deliberately
conservative implementation matrix.
