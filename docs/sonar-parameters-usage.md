# Using Calculated Sonar Parameters

Explains how the parameters produced by `CalculateSonarParameters` are consumed by the different SonarQube analysis methods.

---

## `SONAR_PARAMETERS`

The `SONAR_PARAMETERS` TeamCity parameter is the single string that carries all core `-Dsonar.*` flags needed for analysis.
It is assembled from the output of the `CalculateSonarParameters` metarunner and must contain at minimum:

```text
-Dsonar.projectKey=%SONAR_PROJECT_KEY%
-Dsonar.projectName=%SONAR_PROJECT_NAME%
-Dsonar.projectVersion=%BUILD_VERSION%
-Dsonar.host.url=%SONAR_SERVER_URL%
-Dsonar.token=%SONAR_SERVER_TOKEN%
%SONAR_EXTRA_PARAMETERS%
```

| Variable                   | Source                       | Description                                                                        |
|----------------------------|------------------------------|------------------------------------------------------------------------------------|
| `%SONAR_PROJECT_KEY%`      | `CalculateSonarParameters`   | Unique project identifier in SonarQube                                             |
| `%SONAR_PROJECT_NAME%`     | `CalculateSonarParameters`   | Human-readable project name shown in SonarQube                                     |
| `%BUILD_VERSION%`          | TeamCity build configuration | Version of the component being built                                               |
| `%SONAR_SERVER_URL%`       | `CalculateSonarParameters`   | URL of the SonarQube server (Community or Developer edition)                       |
| `%SONAR_SERVER_TOKEN%`     | `CalculateSonarParameters`   | Authentication token (Community or Developer edition)                              |
| `%SONAR_EXTRA_PARAMETERS%` | `CalculateSonarParameters`   | Branch/PR-specific flags (e.g. `sonar.branch.name`, `sonar.pullrequest.key`, etc.) |

Additional flags, for example:

```text
-Dsonar.qualitygate.wait=true
%SONAR_ADDITIONAL_PARAMETERS%
```

---

## Analysis Methods

### 1. SonarRunner Metarunner

The `SonarRunner` metarunner uses the TeamCity SonarQube Runner plugin. `SONAR_PARAMETERS` is passed via the `additionalParameters` field.

Additional parameters are configured directly on the metarunner:

| Metarunner Parameter        | Maps to                  | Description                                     |
|-----------------------------|--------------------------|-------------------------------------------------|
| `SONAR_PROJECT_SOURCES`     | `sonar.sources`          | Source directories                              |
| `SONAR_PROJECT_BINARIES`    | `sonar.java.binaries`    | Compiled class directories                      |
| `SONAR_PROJECT_TESTS`       | `sonar.tests`            | Test source directories                         |
| `SONAR_PROJECT_MODULES`     | `sonar.modules`          | Sub-modules in a multi-module project           |

### 2. Gradle SonarQube Plugin

When using the [Gradle SonarQube plugin](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/), pass `SONAR_PARAMETERS` as system properties on the Gradle command line:

```bash
./gradlew sonar %SONAR_PARAMETERS%
```

### 3. Maven SonarQube Plugin

When using the [Maven SonarQube plugin](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/), pass `SONAR_PARAMETERS` as system properties on the Maven command line:

```bash
mvn sonar:sonar %SONAR_PARAMETERS%
```
