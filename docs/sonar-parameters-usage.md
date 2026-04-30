# Using Calculated Sonar Parameters

Explains how the parameters produced by `CalculateSonarParameters` are consumed by the different SonarQube analysis methods.

---

## `SONAR_PARAMETERS`

`SONAR_PARAMETERS` must be defined at the **TeamCity project or build-template level** as a parameter that composes the individual outputs of the `CalculateSonarParameters` metarunner into a single `-Dsonar.*` string.

The parameter value must contain at minimum:

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

#### `SONAR_PLUGIN_TASK`

The `CalculateSonarParameters` metarunner also sets `SONAR_PLUGIN_TASK`. This parameter is set to `sonar` for Gradle components and `sonar:sonar` for Maven components, when the component is Java/Kotlin-based and uses a modern Java version (17 or 21, including components in the mismatch-java-version list). Otherwise, it is set to an empty string.

This allows composing it into the default `GRADLE_TASK` TeamCity parameter so that the Sonar analysis task is included only when applicable:

```text
build %SONAR_PLUGIN_TASK% publish
```

When `SONAR_PLUGIN_TASK` is empty the command becomes `build  publish`, which Gradle handles normally.

### 3. Maven SonarQube Plugin

When using the [Maven SonarQube plugin](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/), pass `SONAR_PARAMETERS` as system properties on the Maven command line:

```bash
mvn sonar:sonar %SONAR_PARAMETERS%
```

Similarly, `SONAR_PLUGIN_TASK` can be composed into Maven goals:

```text
clean install %SONAR_PLUGIN_TASK%
```

