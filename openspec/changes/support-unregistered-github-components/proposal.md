# Proposal

## Why

Octopus open-source components (`octopusden/*` on GitHub) are built on the same TeamCity chains as
registered components, but they do not exist in the Components Registry Service. Four CRS calls in
`calculate-sonar-params` throw `NotFoundException` for them —
`CommitStampResolver.resolve` (`getVCSSetting`), `SonarServerResolver.resolveSonarServer`
(`getById`), and `SonarExecutionResolver`'s three public methods — so the command aborts before
printing a single service message.

Two further gaps block these components even once CRS is bypassed:

- `BitbucketSshUrlParser` only understands `ssh://user@host/PROJECT/repo.git`. GitHub remotes on
  these builds are SCP-like (`git@github.com:octopusden/repo.git`), so the project key and name
  cannot be derived.
- `TargetBranchResolver` resolves the base branch through VCS Facade, which has no GitHub provider.
  Every candidate lookup fails against a client configured with `getTimeRetryInMillis() = 180000`,
  costing minutes per build to arrive at a fallback.

## What Changes

Introduce one explicit notion of component registration, resolved once per invocation, and branch
on it.

**New `ComponentRegistrationResolver`** — probes CRS once via `getById(componentName)` and returns
a `ComponentRegistration` (`REGISTERED` / `UNREGISTERED`). `NotFoundException` means
`UNREGISTERED`; any other failure propagates.

**`SonarParametersCalculator.calculate()`** resolves registration first and threads it into the
resolvers. When `UNREGISTERED`:

| Parameter | Behaviour |
|---|---|
| `SONAR_PROJECT_KEY` / `SONAR_PROJECT_NAME` | Derived from the parsed VCS URL (`OCTOPUSDEN_repo_component` / `OCTOPUSDEN/repo:component`) |
| `SONAR_SOURCE_BRANCH` | The TeamCity revision's branch |
| `SONAR_TARGET_BRANCH` | Fixed `main`. VCS Facade is not called |
| `SONAR_SERVER_*` | Community Edition |
| `SONAR_EXTRA_PARAMETERS` / `SONAR_RUNNER_EXTRA_PARAMETERS` | Built by `SonarParameterBuilder` from the source/target pair |
| `SKIP_SONAR_METARUNNER_EXECUTION` | `false` |
| `SKIP_SONAR_REPORT_GENERATION` | `false` |
| `SONAR_TASK` | Empty — the generic metarunner scanner runs, not the Gradle/Maven Sonar plugin |

**`BitbucketSshUrlParser` → `VcsSshUrlParser`**, gaining the SCP-like form
(`user@host:PROJECT/repo.git`) and an optional port on the `ssh://` form.

**`applied-sast.json` supplies project identity only.** Registration is checked before the
file-based skip rules, so an unregistered component is never skipped — but it keeps the override's
key/name and `sonarServer` value, which are read independently of CRS.

## Scope

- Registered components behave identically. Every existing test passes unmodified.
- Pull-request builds are untouched: the `pull-requests/` branch marker still wins, and PR
  parameters still come from TeamCity variables.
- No new CLI option. Registration is discovered, not declared — a component moving into CRS later
  needs no pipeline change.
- No GitHub API client, no new dependency. The SSH URL from TeamCity is the only GitHub-shaped
  input.

## Impact

- `src/main/kotlin/resolver/parameters/ComponentRegistrationResolver.kt` — new
- `src/main/kotlin/resolver/parameters/SonarParametersCalculator.kt` — orchestration
- `src/main/kotlin/resolver/parameters/CommitStampResolver.kt` — takes registration
- `src/main/kotlin/resolver/parameters/SonarServerResolver.kt` — takes registration
- `src/main/kotlin/resolver/parameters/SonarExecutionResolver.kt` — takes registration
- `src/main/kotlin/util/BitbucketSshUrlParser.kt` → `VcsSshUrlParser.kt`
- `src/main/kotlin/util/BranchConstants.kt` — `UNREGISTERED_TARGET_BRANCH`
- `docs/calculate-sonar-parameters.md`
