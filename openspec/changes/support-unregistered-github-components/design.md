# Design

## `ComponentRegistrationResolver`

```kotlin
enum class ComponentRegistration { REGISTERED, UNREGISTERED }

class ComponentRegistrationResolver(private val crsClient: ComponentsRegistryServiceClient) {
    fun resolve(componentName: String): ComponentRegistration =
        try {
            crsClient.getById(componentName)
            ComponentRegistration.REGISTERED
        } catch (_: NotFoundException) {
            ComponentRegistration.UNREGISTERED
        }
}
```

`getById` is version-independent, so the probe takes only the component name. Only
`NotFoundException` is caught — a CRS outage fails the build rather than silently downgrading the
component to the unregistered path.

The enum lives in the same file as the resolver.

## Threading registration through the resolvers

Each affected method takes a `registration: ComponentRegistration` parameter and short-circuits at
the top:

```kotlin
fun resolveSonarServer(
    componentName: String,
    registration: ComponentRegistration,
): SonarServerParametersDTO {
    if (registration == UNREGISTERED) return SonarServerParametersDTO.COMMUNITY
    ...
}
```

The resolvers stay stateless; registration is not a constructor flag.

| Method | `UNREGISTERED` behaviour |
|---|---|
| `SonarServerResolver.resolveSonarServer` | `COMMUNITY` |
| `SonarExecutionResolver.skipSonarMetarunnerExecution` | `false` |
| `SonarExecutionResolver.skipSonarReportGeneration` | `false` |
| `SonarExecutionResolver.resolveSonarPluginBuildSystem` | `null` |
| `CommitStampResolver.resolve` | `resolveWithoutVcsSettings(commitStamps)` |

Every short-circuit is the first statement of its method, above the file-based `skipIfAppliedSast`
and `skipIfDoc` checks: an unregistered component is never skipped, on registry or file grounds.
`getAppliedSastOverride` is not gated, so project key, name and the `sonarServer` override still
apply to an unregistered component listed in `applied-sast.json` — it is scanned under the
override's identity.

`CommitStampResolver.resolve` is the one exception to "first statement": its
`commitStamps.isNotEmpty()` precondition runs first, so a build with no revisions still fails.
`resolveWithoutVcsSettings` itself is unchanged — it already sets
`defaultBranches = DEFAULT_BRANCH_CANDIDATES` and parses the SSH URL.

## Target branch

```kotlin
object BranchConstants {
    /** octopusden repositories use `main`; kept separate from the guess-order candidate list. */
    const val UNREGISTERED_TARGET_BRANCH = "main"
    val DEFAULT_BRANCH_CANDIDATES = listOf("main", "master")
}
```

In `SonarParametersCalculator.resolveBranchContext`, below the pull-request branch and above the
candidate lookup:

```kotlin
if (registration == UNREGISTERED) {
    return BranchContext(
        sourceBranch = sourceBranch,
        targetBranch = UNREGISTERED_TARGET_BRANCH,
        sonarExtraParameters = SonarParameterBuilder.forBranch(sourceBranch, UNREGISTERED_TARGET_BRANCH),
    )
}
```

`TargetBranchResolver` is never entered for an unregistered component, so no VCS Facade call is
made.

An unregistered component whose default branch is `master` gets
`-Dsonar.newCode.referenceBranch=main`. Such a component belongs in CRS.

## `SONAR_TASK`

The Gradle/Maven plugin routing depends on `DetailedComponent.buildSystem`, `labels`, and
`buildParameters.javaVersion`, none of which exist without CRS. `resolveSonarPluginBuildSystem`
returns `null`, so `SONAR_TASK` is empty and the generic metarunner scanner runs. An octopus
component that needs the Gradle plugin is registered in CRS.

## `VcsSshUrlParser`

```kotlin
private val SSH_URL_PATTERN  = Pattern.compile("ssh://[^@/]+@[^/:]+(?::\\d+)?/([^/]+)/([^/]+?)/?")
private val SCP_LIKE_PATTERN = Pattern.compile("[^@/]+@[^/:]+:([^/]+)/([^/]+?)/?")
```

Patterns are tried in that order: an `ssh://` URL with a port
(`ssh://git@host:7999/PROJ/repo.git`) partially resembles the SCP-like shape, and the `[^/:]+`
host class in the SCP pattern keeps it from swallowing a scheme.

Uppercase-project / lowercase-repo normalisation is unchanged, so
`git@github.com:octopusden/octopus-external-systems-client.git` produces
`OCTOPUSDEN_octopus-external-systems-client_<component>`.

The object, file, KDoc, error message and test file all move off the "Bitbucket" name.
