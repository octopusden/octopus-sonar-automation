## Purpose

Defines where and when the Sonar meta-runner fetches the pull-request target branch, so that
analysis works when the analysed repository is checked out below the checkout root.

## ADDED Requirements

### Requirement: Target-branch fetch runs in the Build Working Directory

The "Fetch target branch" step SHALL run `git fetch` in `WORK_DIR`, which the meta-runner SHALL
accept as a parameter defaulting to the configuration's `%WORK_DIR%`; a blank value SHALL mean the
checkout directory.

#### Scenario: Analysed repository in a Checkout Directory
- **WHEN** a build checks the analysed repository out at `core`, sets `WORK_DIR` to
  `%teamcity.build.checkoutDir%/core/mapper` and runs a pull-request analysis
- **THEN** the build log shows the fetch step running in `core/mapper` and the step succeeds

#### Scenario: Default layout
- **WHEN** `WORK_DIR` is the checkout directory
- **THEN** the fetch step runs at the checkout root, as before

### Requirement: No fetch without a scan

The "Fetch target branch" step SHALL run only when `SKIP_SONAR_METARUNNER_EXECUTION` is `false`.

#### Scenario: Scan skipped
- **WHEN** a build has `SKIP_SONAR_METARUNNER_EXECUTION=true`
- **THEN** the fetch step is skipped and the build does not fail on it
