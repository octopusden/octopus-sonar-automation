# Unregistered component handling

## Purpose

Governs how `calculate-sonar-params` produces Sonar parameters for a component that does not exist
in the Components Registry Service — in practice, `octopusden/*` open-source components hosted on
GitHub. Defines how registration is detected, and the value of every emitted TeamCity parameter in
that state. Does not govern registered components, nor pull-request builds, which are resolved
from TeamCity variables before registration is consulted.

## ADDED Requirements

### Requirement: Component registration SHALL be probed once per invocation

`calculate-sonar-params` SHALL determine, exactly once per invocation and before resolving any
Sonar parameter, whether the component exists in CRS.

- The probe SHALL call `ComponentsRegistryServiceClient.getById(componentName)`.
- A `NotFoundException` SHALL yield `UNREGISTERED`.
- A successful response SHALL yield `REGISTERED`.
- Any other exception SHALL propagate and fail the command. A CRS outage SHALL NOT be reported as
  an unregistered component.

#### Scenario: Component absent from CRS
- **WHEN** `getById("octopus-external-systems-client")` throws `NotFoundException`
- **THEN** registration is `UNREGISTERED`
- **AND** no further parameter resolution calls `getVCSSetting` or `getDetailedComponent` for that
  component

#### Scenario: CRS unreachable
- **WHEN** `getById` throws a connection or 5xx error
- **THEN** the exception propagates and the command exits non-zero

### Requirement: An unregistered component SHALL derive project key and name from the VCS URL

`SONAR_PROJECT_KEY` SHALL be `<PROJECT>_<repo>_<componentName>` and `SONAR_PROJECT_NAME` SHALL be
`<PROJECT>/<repo>:<componentName>`, where `PROJECT` and `repo` are parsed from the SSH URL of the
TeamCity revision's VCS root, uppercased and lowercased respectively.

#### Scenario: GitHub SCP-like remote
- **GIVEN** the build revision's VCS URL is `git@github.com:octopusden/octopus-external-systems-client.git`
- **AND** `--component-name` is `octopus-external-systems-client`
- **THEN** `SONAR_PROJECT_KEY` is `OCTOPUSDEN_octopus-external-systems-client_octopus-external-systems-client`
- **AND** `SONAR_PROJECT_NAME` is `OCTOPUSDEN/octopus-external-systems-client:octopus-external-systems-client`

### Requirement: An unregistered component's target branch SHALL be `main` without consulting VCS Facade

For a non-pull-request build of an `UNREGISTERED` component, `SONAR_TARGET_BRANCH` SHALL be the
constant `BranchConstants.UNREGISTERED_TARGET_BRANCH` (`main`), and `TargetBranchResolver` SHALL
NOT be invoked.

#### Scenario: Feature branch on an unregistered component
- **GIVEN** the revision branch is `bitbucket-archived-flag`
- **THEN** `SONAR_SOURCE_BRANCH` is `bitbucket-archived-flag`
- **AND** `SONAR_TARGET_BRANCH` is `main`
- **AND** no call is made to VCS Facade

#### Scenario: Build of the default branch itself
- **GIVEN** the revision branch is `main`
- **THEN** `SONAR_TARGET_BRANCH` is `main`
- **AND** `SONAR_EXTRA_PARAMETERS` is `-Dsonar.branch.name=main` with no
  `-Dsonar.newCode.referenceBranch` flag, per the existing `SonarParameterBuilder.forBranch` rule

### Requirement: An unregistered component SHALL use the Community Edition server

`SONAR_SERVER_ID`, `SONAR_SERVER_URL` and `SONAR_SERVER_TOKEN` SHALL be taken from
`SonarServerParametersDTO.COMMUNITY`. The Developer Edition label check SHALL be skipped.

#### Scenario: Server parameters for an unregistered component
- **THEN** `SONAR_SERVER_ID` is `%SONAR_COMMUNITY_ID%`
- **AND** `SONAR_SERVER_URL` is `%SONAR_COMMUNITY_URL%`
- **AND** `SONAR_SERVER_TOKEN` is `%SONAR_COMMUNITY_TOKEN%`

### Requirement: An unregistered component SHALL run the generic metarunner scanner

- `SKIP_SONAR_METARUNNER_EXECUTION` SHALL be `false`.
- `SKIP_SONAR_REPORT_GENERATION` SHALL be `false`.
- `SONAR_TASK` SHALL be the empty string — the build system cannot be determined without CRS, so
  neither the Gradle nor the Maven Sonar plugin is selected.

#### Scenario: Execution parameters for an unregistered component
- **THEN** `SKIP_SONAR_METARUNNER_EXECUTION` is `false`
- **AND** `SKIP_SONAR_REPORT_GENERATION` is `false`
- **AND** `SONAR_TASK` is empty

### Requirement: File-based skip rules SHALL NOT be evaluated for an unregistered component

Registration SHALL be checked before the `applied-sast.json`, `other-doc-components.txt` and
`mismatch-java-version.txt` rules in `skipSonarMetarunnerExecution`, `skipSonarReportGeneration`
and `resolveSonarPluginBuildSystem`. An unregistered component is never skipped.

Project key and name SHALL still come from `applied-sast.json` when the component is listed there,
and the override's `sonarServer` value SHALL still select the server — those are read through
`getAppliedSastOverride`, which is independent of registration.

#### Scenario: Unregistered component listed in applied-sast.json
- **GIVEN** `octopus-example` is absent from CRS but present in `applied-sast.json`
- **THEN** `SONAR_PROJECT_KEY` and `SONAR_PROJECT_NAME` come from the override
- **AND** the override's `sonarServer` value, if set, selects the server instead of Community
- **AND** `SKIP_SONAR_METARUNNER_EXECUTION` is `false` — the component is scanned

#### Scenario: Unregistered documentation component
- **GIVEN** the component name is `doc-octopus-example` and it is absent from CRS
- **THEN** `SKIP_SONAR_METARUNNER_EXECUTION` is `false`
- **AND** `SKIP_SONAR_REPORT_GENERATION` is `false`

### Requirement: Pull-request builds SHALL be unaffected by registration

When the resolved revision branch starts with `pull-requests/`, branch parameters SHALL come from
TeamCity variables exactly as they do for registered components.

#### Scenario: Pull-request build of an unregistered component
- **GIVEN** the revision branch is `pull-requests/42/from`
- **THEN** `SONAR_TARGET_BRANCH` is `%teamcity.pullRequest.target.branch%`
- **AND** `SONAR_EXTRA_PARAMETERS` contains `-Dsonar.pullrequest.key=%teamcity.pullRequest.number%`

## MODIFIED Requirements

### Requirement: SSH URL parsing SHALL accept SCP-like remotes

`VcsSshUrlParser.parseRepository` (renamed from `BitbucketSshUrlParser`) SHALL accept both:

- `ssh://user@host[:port]/PROJECT/repo[.git]`
- `user@host:PROJECT/repo[.git]`

returning the project segment uppercased and the repository segment lowercased with any `.git`
suffix removed. The `ssh://` form SHALL be matched first. Any other input SHALL raise
`IllegalArgumentException` naming both supported forms.

#### Scenario: SSH URL with explicit port
- **GIVEN** `ssh://git@bitbucket.example.com:7999/PROJ/My-Repo.git`
- **THEN** the result is `PROJ` to `my-repo`

#### Scenario: SCP-like GitHub remote
- **GIVEN** `git@github.com:octopusden/Octopus-Repo.git`
- **THEN** the result is `OCTOPUSDEN` to `octopus-repo`

#### Scenario: Unsupported form
- **GIVEN** `https://github.com/octopusden/repo.git`
- **THEN** `IllegalArgumentException` is raised
