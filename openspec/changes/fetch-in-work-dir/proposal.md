## Why

The *Calculate Sonar Parameters* meta-runner runs its "Fetch target branch" step with `git fetch`
at the checkout root (`metarunners/CalculateSonarParameters.xml:51-59`). When a build places the
analysed repository in a subdirectory of the checkout root (a Checkout Directory, with the build
running in a Build Working Directory inside it), there is no `.git` at the root, the step fails and
the build fails, although the analysis itself would work. The step also runs when the scan is
skipped. Program change: `onb-001-multi-vcs-root-component` (ADR-001 revision 3) in the program
repository. Baseline: `test/onb-001-baseline` (`892fabd`).

## What Changes

- The fetch step runs in `WORK_DIR`. The meta-runner declares `WORK_DIR` as a parameter defaulting
  to the build configuration's `%WORK_DIR%`, as `metarunners/SonarRunner.xml:23` already does; the
  compile templates own `WORK_DIR`, and the build-chain generator sets it per configuration when a
  Build Working Directory is registered. Blank means the checkout directory.
- The fetch step runs only when the Sonar scan runs (`SKIP_SONAR_METARUNNER_EXECUTION` is
  `false`).
- The Kotlin parameter calculation is unchanged: `CommitStampResolver` already takes the first
  build revision that matches a registry root, and the generator attaches the Build Working
  Directory's repository first.

## Capabilities

### New Capabilities

- `sonar-target-branch-fetch`: where and when the meta-runner fetches the pull-request target
  branch.

### Modified Capabilities

None.

## Impact

- `metarunners/CalculateSonarParameters.xml` and its document `docs/calculate-sonar-parameters.md`.
- Existing configurations with a default `WORK_DIR` (the checkout directory) behave as today.
- Independent of the generator; can ship any time after the registry. A local, unmerged prototype
  of the same step change exists; it is redone on mainline, not cherry-picked, because it also
  carries an internal-only document.
