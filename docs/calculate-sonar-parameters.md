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

| Parameter                         | Description                                                            |
|-----------------------------------|------------------------------------------------------------------------|
| `SONAR_PROJECT_KEY`               | Default: `<BB_PROJECT>_<BB_REPO>_<COMPONENT_NAME>`                     |
| `SONAR_PROJECT_NAME`              | Default: `<BB_PROJECT>/<BB_REPO>:<COMPONENT_NAME>`                     |
| `SONAR_SOURCE_BRANCH`             | Branch or PR being analysed                                            |
| `SONAR_TARGET_BRANCH`             | Branch to compare against (base branch)                                |
| `SONAR_SERVER_ID`                 | ID of the SonarQube server to use (TC parameter reference)             |
| `SONAR_SERVER_URL`                | URL of the SonarQube server to use (TC parameter reference)            |
| `SONAR_SERVER_TOKEN`              | Authentication token for the SonarQube server (TC parameter reference) |
| `SONAR_EXTRA_PARAMETERS`          | `-Dsonar.*` flags for the scanner                                      |
| `SKIP_SONAR_METARUNNER_EXECUTION` | `true` if Sonar metarunner scan should be skipped                      |
| `SKIP_SONAR_REPORT_GENERATION`    | `true` if report generation should be skipped                          |
| `SONAR_PLUGIN_TASK`               | `sonar` for Gradle, `sonar:sonar` for Maven, empty otherwise           |

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

| Build Mode     | Source Branch                                                            | Target Branch                                                                                                                                                                                                                   |
|----------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Branch build   | Branch from matched VCS settings                                         | Resolved via [Target Branch Analysis](#target-branch-analysis)                                                                                                                                                                  |
| PR build       | `pull-requests/<PR_NUMBER>` from VCS                                     | `%teamcity.pullRequest.target.branch%`                                                                                                                                                                                          |
| `applied-sast` | Branch from matched VCS settings or `pull-requests/<PR_NUMBER>` from VCS | Best-effort only (no VCS Facade calls): source branch if it matches a candidate, otherwise first candidate. Not used for Sonar parameters — `SONAR_EXTRA_PARAMETERS` is left empty since legacy config handles branch settings. |

### Sonar Server ID, URL, and Token

Determined by the component's language labels from the Components Registry:

| Language Labels                        | Server Edition       |
|----------------------------------------|----------------------|
| `c`, `cpp`, `objective_c`, `swift`     | Developer Edition    |
| Everything else                        | Community Edition    |

### Sonar Extra Parameters

| Build Mode     | Parameters Set                                                                                                      |
|----------------|---------------------------------------------------------------------------------------------------------------------|
| PR build       | `sonar.pullrequest.key`, `sonar.pullrequest.branch`, `sonar.pullrequest.base` — from TeamCity's PR parameters       |
| Branch build   | `sonar.branch.name` = source branch; `sonar.newCode.referenceBranch` = target branch (omitted when source = target) |
| `applied-sast` | Empty (handled by legacy config)                                                                                    |

### Skip Sonar Metarunner Execution

The metarunner scan is **skipped** when any of the following hold:
- Component is in `applied-sast.json`
- Component name starts with `doc-` or `doc_` (case-insensitive), or is listed in `other-doc-components.txt`
- Component is archived
- Component is labelled `test-component`
- Java/Kotlin component using a **Gradle or Maven** build system and JDK 17/21 or listed in `mismatch-java-version.txt` (handled by Gradle/Maven plugin)

### Skip Sonar Report Generation

Report generation is **skipped** when any of the following hold:
- Component name starts with `doc-` or `doc_` (case-insensitive), or is listed in `other-doc-components.txt`
- Component is archived
- Component is labelled `test-component`

### Sonar Plugin Task (`SONAR_PLUGIN_TASK`)

The `SONAR_PLUGIN_TASK` parameter is resolved based on the component's build system:

| Build System | Task          |
|--------------|---------------|
| Gradle       | `sonar`       |
| Maven        | `sonar:sonar` |
| Other/skip   | _(empty)_     |

The plugin task is set (non-empty) only when **all** of the following conditions are met:
- Component is **not** in `applied-sast.json`
- Component is **not** a documentation component
- Component is **not** archived
- Component is **not** labelled `test-component`
- Component uses the **Gradle** or **Maven** build system
- Component is labelled `java` or `kotlin`
- Component uses Java version **17** or **21**, or is listed in `mismatch-java-version.txt`

Otherwise it is set to an **empty string**.

This parameter can be composed into build-tool task parameters. For example, in a Gradle `GRADLE_TASK` parameter:

```text
build %SONAR_PLUGIN_TASK% publish
```

Or in a Maven goals parameter:

```text
clean install %SONAR_PLUGIN_TASK%
```

---

## Target Branch Analysis

For **pull-request builds**, the target branch is simply read from the TeamCity parameter `%teamcity.pullRequest.target.branch%` — no analysis is needed.

For **applied-SAST builds**, target branch is resolved via **best-effort** — if the source branch matches any candidate, it is returned; otherwise the first candidate is used. No VCS Facade calls are made.

For **regular branch builds**, the tool must determine which production/release branch the source branch was forked from. This is handled by the `TargetBranchResolver`.

### Algorithm

1. **Single candidate**: if only one candidate branch exists, it is returned immediately with no VCS Facade calls.

2. **Short-circuit**: if the source branch is itself one of the candidate branches (e.g. building `main` directly), it is returned immediately with no VCS Facade calls.

3. **Window-first, best-candidate-per-window iteration**:
   - The resolver iterates over progressively wider time windows (outer loop).
   - For each window, it first fetches the **source branch** commits (cached across windows). If fetching fails, it immediately returns the first candidate as a fallback. If no commits are found in the current window, it skips to the next (wider) window.
   - Within each window, it evaluates **all non-skipped candidate branches** (inner loop). For each candidate, it fetches the candidate's commits in the same window and finds the first commit hash shared with the source branch. The position of that shared commit in the source-branch history (its index, with index 0 being the most recent commit) is recorded.
   - After all candidates have been evaluated for the current window, the candidate with the **lowest index** (i.e. the most recent common ancestor, meaning the branch the source diverged from most recently) is selected and returned. Candidate order is used only as a tie-breaker when two candidates share the same index.
   - If a candidate branch is not found (`NotFoundException`) or any other error occurs fetching its commits, that candidate is **permanently skipped** for all subsequent windows.
   - If no candidate matches in the current window, the next (wider) window is tried with all non-skipped candidates.

4. **Window growth**:
   - Starts at `initialWindowDays` (default: **10 days**).
   - Multiplied by `windowGrowthFactor` (default: **×2**) each iteration.
   - Capped at `maxWindowDays` (default: **365 days**).
   - Example progression: 10 → 20 → 40 → 80 → 160 → 320 → 365 days.


5. **Fallback behaviour**:

   | Scenario                                            | Result                      |
   |-----------------------------------------------------|-----------------------------|
   | Only one candidate                                  | Returns that candidate      |
   | Source branch is a candidate                        | Returns source branch       |
   | Common commit found with a candidate                | Returns closest ancestor    |
   | VCS Facade error fetching source branch commits     | Returns first candidate     |
   | Candidate branch not found (`NotFoundException`)    | Skips candidate, tries next |
   | VCS Facade error fetching candidate commits         | Skips candidate, tries next |
   | No common commit found after all windows/candidates | Returns first candidate     |
   | Source branch has no commits in any window          | Returns first candidate     |

### Example

```text
Source branch: feature/FIX
Candidates:   [main, release/1.0]

Commit graph:
  A -- B -- C -- D -- E        main
       \
        R1 -- R2 -- R3         release/1.0
                      \
                       F1 -- F2 -- F3    feature/FIX

Window 10 days (source commits: [F3, F2, F1, R3]):
  Candidate "main":       no common hash in window
  Candidate "release/1.0": common hash "R3" at index 3

Window 20 days (source commits: [F3, F2, F1, R3, R2, R1, B, A]):
  Candidate "main":       common hash "B" at index 6
  Candidate "release/1.0": common hash "R3" at index 3  ← lower index (closer ancestor)

Best candidate in window: release/1.0 (index 3 < 6)
Result: release/1.0
```

This ensures the most recently diverged branch is always returned, regardless of candidate order.

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

