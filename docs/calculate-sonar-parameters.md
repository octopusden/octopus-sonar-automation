# Calculate Sonar Parameters

Resolves all SonarQube parameters for the current TeamCity build and injects them via [service messages](https://www.jetbrains.com/help/teamcity/service-messages.html).

---

## How It Works

Given a component name, version, and TeamCity build ID, the tool:

1. **Resolves the VCS context** — matches the build's revision list against the component's VCS settings from the Components Registry.
2. **Calculates SonarQube parameters** — determines project key, branches, server, and extra flags.
3. **Injects the parameters** into the running TeamCity build via `##teamcity[setParameter ...]`.

These parameters are then picked up by the downstream Sonar metarunner.

### Legacy Override Support

Components with SonarQube analysis already set up manually are supported via configuration files from the `RELENG/sonar-config` repository:

| File                        | Purpose                                                                                  |
|-----------------------------|------------------------------------------------------------------------------------------|
| `applied-sast.json`         | Components with manually applied SonarQube parameters (project key, name, extra params)  |
| `other-doc-components.txt`  | Documentation components that should be skipped for Sonar                                |
| `mismatch-java-version.txt` | Java/Kotlin components registered as JDK 1.8 but actually using Java 17/21               |

---

## Output Parameters

| Parameter                         | Description                                                    |
|-----------------------------------|----------------------------------------------------------------|
| `SONAR_PROJECT_KEY`               | Default: `<BB_PROJECT>_<BB_REPO>_<COMPONENT>`                  |
| `SONAR_PROJECT_NAME`              | Default: `<BB_PROJECT>/<BB_REPO>:<COMPONENT>`                  |
| `SONAR_SOURCE_BRANCH`             | Branch or PR being analysed                                    |
| `SONAR_TARGET_BRANCH`             | Branch to compare against (base branch)                        |
| `SONAR_EXTRA_PARAMETERS`          | `-Dsonar.*` flags for the scanner                              |
| `SONAR_SERVER_ID`                 | ID of the SonarQube server to use (TC parameter reference)     |
| `SONAR_SERVER_URL`                | URL of the SonarQube server to use (TC parameter reference)    |
| `SKIP_SONAR_METARUNNER_EXECUTION` | `true` if Sonar metarunner scan should be skipped              |
| `SKIP_SONAR_REPORT_GENERATION`    | `true` if report generation should be skipped                  |

---

## Parameter Resolution Details

### Sonar Project Key & Name

| Scenario                              | Key                                              | Name                                                |
|---------------------------------------|--------------------------------------------------|-----------------------------------------------------|
| Default                               | `<BB_PROJECT>_<BB_REPO>_<COMPONENT>`             | `<BB_PROJECT>/<BB_REPO>:<COMPONENT>`                |
| Component in `applied-sast.json`      | Value from override file                         | Value from override file                            |

Where `<BB_PROJECT>` and `<BB_REPO>` are extracted from the TeamCity build's VCS changes and matched against the component's VCS settings from the Components Registry.
- If no VCS settings are configured, defaults to the first VCS change found for the build.
- If VCS settings exist but none match VCS changes, an error is thrown.

### Source & Target Branches

| Build Mode      | Source Branch                          | Target Branch                                          |
|-----------------|----------------------------------------|--------------------------------------------------------|
| Branch build    | Branch from matched VCS settings       | Resolved via [Target Branch Analysis](#target-branch-analysis) |
| PR build        | `pull-requests/<PR_NUMBER>` from VCS   | `%teamcity.pullRequest.target.branch%`                 |
| `applied-sast`  | Branch from matched VCS settings       | Empty (handled by legacy config)                       |

### Sonar Extra Parameters

| Build Mode      | Parameters Set                                                                                                |
|-----------------|---------------------------------------------------------------------------------------------------------------|
| PR build        | `sonar.pullrequest.key`, `sonar.pullrequest.branch`, `sonar.pullrequest.base` — from TeamCity's PR parameters |
| Branch build    | `sonar.branch.name` = source branch; `sonar.newCode.referenceBranch` = target branch (omitted when source = target) |
| `applied-sast`  | Empty (handled by legacy config)                                                                              |

### Sonar Server ID & URL

Determined by the component's language labels from the Components Registry:

| Language Labels                        | Server Edition       |
|----------------------------------------|----------------------|
| `c`, `cpp`, `objective_c`, `swift`     | Developer Edition    |
| Everything else                        | Community Edition    |

### Skip Sonar Metarunner Execution

The new metarunner scan is executed **only** for:
- Components **not** in `applied-sast.json`
- Java/Kotlin components with JDK 1.8 (components using JDK 17/21 use gradle/maven plugin directly)
- Non-Java/Kotlin components

### Skip Sonar Report Generation

Report generation is skipped for documentation, test, and archived components.

---

## Target Branch Analysis

For **pull-request builds**, the target branch is simply read from the TeamCity parameter `%teamcity.pullRequest.target.branch%` — no analysis is needed.

For **regular branch builds**, the tool must determine which production/release branch the source branch was forked from. This is handled by the `TargetBranchResolver`.

### Algorithm

1. **Short-circuit**: if the source branch is itself one of the candidate branches (e.g. building `main` directly), it is returned immediately with no VCS Facade calls.

2. **Candidate-first, window-second iteration**:
   - Candidates are evaluated in the order provided (typically `[main, master]` or the component's default branches).
   - For each candidate, the resolver tries progressively wider time windows to find a **common commit hash** (the diverge point) between the source branch and that candidate.
   - As soon as a common commit is found, that candidate is returned immediately — remaining candidates and windows are not checked.
   - If all windows are exhausted for a candidate without finding a match, the next candidate is tried.

3. **Window growth**:
   - Starts at `initialWindowDays` (default: **10 days**).
   - Multiplied by `windowGrowthFactor` (default: **×2**) each iteration.
   - Capped at `maxWindowDays` (default: **365 days**).
   - Example progression: 10 → 20 → 40 → 80 → 160 → 320 → 365 days.


4. **Fallback behaviour**:

   | Scenario                                          | Result                        |
   |---------------------------------------------------|-------------------------------|
   | Source branch is a candidate                       | Returns source branch         |
   | Common commit found with a candidate               | Returns that candidate        |
   | VCS Facade error fetching source branch commits    | Returns first candidate       |
   | Candidate branch not found (`NotFoundException`)   | Skips candidate, tries next   |
   | VCS Facade error fetching candidate commits        | Skips candidate, tries next   |
   | No common commit found after all windows/candidates| Returns first candidate       |
   | Source branch has no commits in any window          | Returns first candidate       |

### Example

```
Source branch: feature/ABC-123
Candidates:   [main, master]

Candidate "main":
  Window 10 days:
    feature/ABC-123 commits: [f3, f2, f1]
    main commits:            [m5, m4, m3]        → no common hash
  Window 20 days:
    feature/ABC-123 commits: [f3, f2, f1, base]
    main commits:            [m5, m4, m3, base]  → common hash "base" found!

Result: main  (candidate "master" was never checked)
```

---

## Usage

```bash
java -jar sonar-automation-<version>.jar calculate-sonar-params \
  --teamcity-url=<url> \
  --teamcity-user=<user> \
  --teamcity-password=<password> \
  --teamcity-build-id=<id> \
  --components-registry-url=<url> \
  --vcs-facade-url=<url> \
  --component-name=<name> \
  --component-version=<version> \
  --sonar-config-dir=<path>
```

| Option                       | Required | Description                                |
|------------------------------|----------|--------------------------------------------|
| `--teamcity-url`             | Yes      | TeamCity base URL                          |
| `--teamcity-user`            | Yes      | TeamCity username                          |
| `--teamcity-password`        | Yes      | TeamCity password                          |
| `--teamcity-build-id`        | Yes      | TeamCity build ID (integer)                |
| `--components-registry-url`  | Yes      | Components Registry Service base URL       |
| `--vcs-facade-url`           | Yes      | VCS Facade Service base URL                |
| `--component-name`           | Yes      | Component name                             |
| `--component-version`        | Yes      | Component version                          |
| `--sonar-config-dir`         | Yes      | Directory containing sonar config files    |

In practice this is invoked automatically by the `CalculateSonarParameters` TeamCity metarunner.

