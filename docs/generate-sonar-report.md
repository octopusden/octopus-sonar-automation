# Generate Sonar Report

Checks the SonarQube quality gate, notifies TeamCity, and generates an HTML SAST report for a component.

---

## How It Works

The `generate-sonar-report` command performs three steps:

1. **Quality Gate Check** — queries SonarQube for quality gate status, new issue count, and metric ratings.
2. **TeamCity Notification** — translates the result into build-level feedback (build problems or status warnings).
3. **HTML Report Generation** _(production branches only)_ — fetches all issues and hotspots, then renders a self-contained HTML report.

The HTML report is only generated when `sourceBranch == targetBranch` (i.e. on production/release branches). For feature branches and PRs, only the quality gate check and TeamCity notification are performed.

---

## Quality Gate Check

The `QualityGateChecker` queries SonarQube for:

| Check               | API                            | Description                                        |
|----------------------|--------------------------------|----------------------------------------------------|
| Quality gate status  | `qualitygates/project_status`  | Overall pass/fail                                  |
| New issue count      | `issues/search` (new code)     | Unresolved issues in the new code period           |
| Failed metrics       | `measures/component`           | Rating metrics below target                        |

**Metrics checked:**

| Metric Key                                | Display Name        |
|-------------------------------------------|---------------------|
| `software_quality_reliability_rating`     | reliability         |
| `software_quality_security_rating`        | security            |
| `software_quality_maintainability_rating` | maintainability     |
| `security_review_rating`                  | security hotspots   |

---

## TeamCity Notification

The `TeamCityNotifier` translates the quality gate result into TeamCity service messages:

| Condition                                                  | Message Type    | Effect              |
|------------------------------------------------------------|-----------------|---------------------|
| Quality gate **FAILED**                                    | `buildProblem`  | Fails the build     |
| New issues found (feature/PR branch)                       | `buildStatus`   | Warning with count  |
| New issues + failed metrics (production branch)            | `buildStatus`   | Warning with count + metric names |
| No new issues but failed metrics (production branch)       | `buildStatus`   | Warning with metric names |
| All clean                                                  | _(none)_        | Clean build         |

All messages include a direct link to the SonarQube dashboard or issues page.

---

## HTML Report Pipeline

### 1. Data Fetching (`ReportDataFetcher`)

Fetches from SonarQube with automatic pagination (page size: 500):

| Data            | API Endpoint                  | Filter                    |
|-----------------|-------------------------------|---------------------------|
| Issues          | `issues/search`               | Unresolved only           |
| Hotspots        | `hotspots/search`             | `TO_REVIEW` status only   |
| Quality gate    | `qualitygates/project_status` | —                         |
| Total effort    | `issues/search` (p=1)         | Effort total from header  |

### 2. Data Mapping (`ReportDataMapper`)

Maps raw SonarQube DTOs into report-friendly structures:

- **Issues**: severity (from impacts if available), message, rule, type, file name, line number, and effort.
- **Hotspots**: message, rule, vulnerability probability, security category, file name, and line number.
- **Repository**: extracted from `SONAR_PROJECT_NAME` (the part before `:`).

### 3. HTML Rendering (`ReportHtmlRenderer`)

Renders the report using Apache Velocity templates:

| Template                  | Used When                        |
|---------------------------|----------------------------------|
| `sast-report.vm`          | Issues or hotspots exist         |
| `sast-no-issue-report.vm` | No issues and no hotspots found  |

The report includes:
- Component name, version, and repository
- Quality gate status with visual indicator
- Total effort estimate
- Summary counts by severity (blocker, high, medium, low, info)
- Detailed issue table with links to SonarQube
- Detailed hotspot table with links to SonarQube

### Output

The report is written to the working directory as:

```
<component-name>-<component-version>-sast-report.html
```

Special characters in component name and version are replaced with `_`.

---

## Usage

```bash
java -jar sonar-automation-<version>.jar generate-sonar-report \
  --sonar-server-url=<url> \
  --sonar-username=<user> \
  --sonar-password=<password> \
  --component-name=<name> \
  --component-version=<version> \
  --sonar-project-key=<key> \
  --sonar-project-name=<name> \
  --sonar-source-branch=<branch> \
  --sonar-target-branch=<branch>
```

| Option                   | Required | Description                                               |
|--------------------------|----------|-----------------------------------------------------------|
| `--sonar-server-url`     | Yes      | SonarQube server URL                                      |
| `--sonar-username`       | Yes      | SonarQube username                                        |
| `--sonar-password`       | Yes      | SonarQube password                                        |
| `--component-name`       | Yes      | Component name                                            |
| `--component-version`    | Yes      | Component version                                         |
| `--sonar-project-key`    | Yes      | SonarQube project key (from `calculate-sonar-params`)     |
| `--sonar-project-name`   | Yes      | SonarQube project name (`PROJECT/repo:component`)         |
| `--sonar-source-branch`  | Yes      | Source branch being analysed                              |
| `--sonar-target-branch`  | Yes      | Target/base branch for comparison                         |

In practice this is invoked automatically by the `GenerateSonarReport` TeamCity metarunner, using parameters set by [`calculate-sonar-params`](calculate-sonar-parameters.md).

