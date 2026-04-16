# sonar-automation

A command-line tool that automates SonarQube analysis within TeamCity builds. Instead of hardcoding SonarQube settings per component, this tool resolves the right parameters at runtime by talking to internal services (TeamCity, Components Registry, VCS Facade).

---

## Features

| Command | Description | Documentation |
|---------|-------------|---------------|
| `calculate-sonar-params` | Resolves and injects SonarQube parameters into the current TeamCity build | [Calculate Sonar Parameters](docs/calculate-sonar-parameters.md) |
| `generate-sonar-report` | Checks quality gate, notifies TeamCity, and generates an HTML SAST report | [Generate Sonar Report](docs/generate-sonar-report.md) |

---

## Quick Start

```bash
# Step 1: Calculate Sonar parameters
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

# Step 2: Generate Sonar report (uses parameters from step 1)
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

In practice, both commands are invoked automatically by TeamCity metarunners (`CalculateSonarParameters` and `GenerateSonarReport`).
