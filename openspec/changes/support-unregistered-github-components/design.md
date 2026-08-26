# Design

## Decision 1 — Registration is one probe, resolved once, not four caught exceptions

The rejected alternative is the smallest diff: wrap each of the four CRS calls in
`try { ... } catch (_: NotFoundException) { <local default> }`. It produces the correct output
today, but the rule "an unregistered component runs the generic scanner against Community
Edition on `main`" exists nowhere in the code — it is emergent from four unrelated fallbacks in
three files. There is no single thing to test, and each future CRS call is a new place to
remember the same catch.

Instead, `ComponentRegistrationResolver` answers the question once:

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

`getById` is the cheapest CRS endpoint of the three used and is version-independent, so the probe
does not need `componentVersion`. Only `NotFoundException` is caught — a CRS outage must still
fail the build loudly rather than silently downgrade every component to the unregistered path.

Cost: one extra CRS round-trip for registered components. `getById` is already called twice today
(`SonarServerResolver`, `SonarExecutionResolver.skipSonarReportGeneration`), so this is a third
call to an endpoint that is already hit — acceptable against a per-build latency budget measured
in minutes.

## Decision 2 — Resolvers take `ComponentRegistration` as a parameter, not a constructor flag

Each affected method gains a `registration: ComponentRegistration` parameter and short-circuits at
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

A constructor flag would make the resolvers un-reusable across registrations within one process
and would push the probe into their construction, which happens in `SonarParametersCalculator`'s
default arguments — before `calculate()` runs. A parameter keeps the resolvers stateless and lets a
test exercise both paths against one instance.

## Decision 3 — `main` is a named constant, and VCS Facade is not called at all

```kotlin
object BranchConstants {
    const val UNREGISTERED_TARGET_BRANCH = "main"
    val DEFAULT_BRANCH_CANDIDATES = listOf("main", "master")
}
```

Not `DEFAULT_BRANCH_CANDIDATES.first()`: the two values coincide today but mean different things —
one is "the first branch to try when we have to guess", the other is "the branch octopusden
repositories use". Coupling them means a future reorder of the candidate list silently changes the
GitHub target branch.

The short-circuit lives in `resolveBranchContext`, above the candidate/facade logic, so
`TargetBranchResolver` is never constructed into the call path for an unregistered component. This
is the point of the decision: the fallback path would return `main` anyway, but only after
~6 escalating windows × 2 candidates of VCS Facade calls, each retried under a 180 s budget.

Known limitation, accepted: an unregistered component whose default branch is `master` gets
`-Dsonar.newCode.referenceBranch=main`. Every `octopusden/*` repository uses `main`; if one does
not, it belongs in `applied-sast.json` or in CRS.

## Decision 4 — `SONAR_TASK` stays empty; the metarunner runs

Octopus components are Kotlin/Gradle on Java 17+, which for a *registered* component would route
to the Gradle Sonar plugin (`SONAR_TASK=%SONAR_GRADLE_TASK%`, metarunner skipped). That routing
depends on `DetailedComponent.buildSystem`, `labels`, and `buildParameters.javaVersion` — none of
which exist without CRS. Guessing GRADLE from the absence of data would be inventing a
registry entry. `resolveSonarPluginBuildSystem` returns `null` for `UNREGISTERED`, so the generic
metarunner scanner runs with an empty task. If a specific octopus component needs the Gradle
plugin, registering it in CRS is the mechanism that already exists.

## Decision 5 — `VcsSshUrlParser` tries patterns in order, SCP-like last

```kotlin
private val SSH_URL_PATTERN  = Pattern.compile("ssh://[^@/]+@[^/:]+(?::\\d+)?/([^/]+)/([^/]+?)/?")
private val SCP_LIKE_PATTERN = Pattern.compile("[^@/]+@[^/:]+:([^/]+)/([^/]+)")
```

Order matters: an `ssh://` URL with a port (`ssh://git@host:7999/PROJ/repo.git`) also partially
resembles the SCP-like shape, so the explicit-scheme pattern is tried first. The `[^/:]+` host class
in the SCP pattern prevents it from swallowing a scheme.

Uppercase-project / lowercase-repo normalisation is unchanged, which is what makes
`git@github.com:octopusden/octopus-external-systems-client.git` produce
`OCTOPUSDEN_octopus-external-systems-client_<component>`.

The rename is not cosmetic: the file's KDoc, its error message, and its test file all currently
assert "Bitbucket". Leaving the name would make the next reader assume GitHub is unsupported.

## Decision 6 — `CommitStampResolver` skips `getVCSSetting` entirely when unregistered

`resolve` already has a fallback path — `resolveWithoutVcsSettings` — for components whose
`externalRegistry` is `NOT_AVAILABLE` or whose root list is empty. An unregistered component takes
that same path; the only change is *how it gets there*: by checking registration before the call,
instead of by catching the call's exception. `resolveWithoutVcsSettings` needs no modification —
it already sets `defaultBranches = DEFAULT_BRANCH_CANDIDATES` and parses the SSH URL, and its
"multiple commit stamps, using the first" warning is equally correct here.

Note the ordering constraint: `require(commitStamps.isNotEmpty())` must still run first. An
unregistered component with no TeamCity revisions is a broken build, not an unregistered-component
scenario, and must keep failing.
