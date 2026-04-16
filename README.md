# sonar-automation

A command-line tool that automatically resolves and injects SonarQube parameters into a TeamCity build.

Instead of hardcoding SonarQube settings per component, this tool figures out the right parameters at runtime by talking to internal services (TeamCity, Components Registry, VCS Facade).

## How it works

Given a component name, version, and TeamCity build ID, the tool calculates a set of SonarQube parameters and injects them into the running TeamCity build via [service messages](https://www.jetbrains.com/help/teamcity/service-messages.html) (`##teamcity[setParameter ...]`). These parameters are then picked up by the downstream Sonar metarunner.

### What gets set

| Parameter                         | Description                                               |
|-----------------------------------|-----------------------------------------------------------|
| `SONAR_PROJECT_KEY`               | Unique key: `<BB_PROJECT>_<BB_REPO>_<COMPONENT>`          |
| `SONAR_PROJECT_NAME`              | Human-readable name: `<BB_PROJECT>/<BB_REPO>:<COMPONENT>` |
| `SONAR_SOURCE_BRANCH`             | Branch being analysed                                     |
| `SONAR_TARGET_BRANCH`             | Branch to compare against                                 |
| `SONAR_EXTRA_PARAMETERS`          | `-Dsonar.*` flags for the scanner (see below)             |
| `SONAR_SERVER_ID`                 | ID of the SonarQube server to use                         |
| `SONAR_SERVER_URL`                | URL of the SonarQube server to use                        |
| `SKIP_SONAR_METARUNNER_EXECUTION` | `true` if Sonar metarunner scan should be skipped         |
| `SKIP_SONAR_REPORT_GENERATION`    | `true` if report generation should be skipped             |

### How each parameter is resolved

**Branch / PR mode** — The tool checks if the build's commit branch contains `pull-requests/`:
- **PR build**: `SONAR_EXTRA_PARAMETERS` is set to `-Dsonar.pullrequest.key`, `.branch`, and `.base` flags. Source and target branch values are taken from TeamCity's own PR parameters.
- **Regular branch build**: `SONAR_EXTRA_PARAMETERS` is set to `-Dsonar.branch.name` and, when needed, `-Dsonar.newCode.referenceBranch`. The target branch is resolved from VCS Facade based on the component's default branches.

**Applied-SAST override** — If the component exists in `applied-sast.json`, project key/name come from that file, `SONAR_TARGET_BRANCH` is empty, and `SONAR_EXTRA_PARAMETERS` is empty.

**SonarQube server** — Determined by the component's language labels from Components Registry:
- `c`, `cpp`, `objective_c`, `swift` → **Developer Edition**
- everything else → **Community Edition**

**Skip flags** — Sonar execution is skipped when the component is:
- already covered by a dedicated SAST pipeline (`applied-sast.json`)
- a documentation component (name starts with `doc` or listed in `other-doc-components.txt`)
- archived or labelled `test-component`
- a Java/Kotlin component using Java 17/21 (handled by its own Gradle/Maven Sonar plugin)

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
  --component-version=<version>
```

In practice this is invoked automatically by the `CalculateSonarParameters` TeamCity metarunner.
