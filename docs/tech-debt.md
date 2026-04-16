# Tech Debt

Tracked technical debt items for the sonar-automation project.

---

## TD-001: Replace `TeamcityRestClient` with typed client

**Priority:** Medium
**Component:** `src/main/kotlin/client/TeamcityClient.kt`, `src/main/kotlin/resolver/parameters/CommitStampResolver.kt`

### Current State

`TeamcityRestClient` is a minimal HTTP client that returns raw `Map<String, Any>` from the TeamCity REST API. All callers (`CommitStampResolver`) perform unsafe casts (`as? Map<String, Any>`, `as? List<Map<String, Any>>`) to navigate the response structure.

```kotlin
val revisions = response["revisions"] as? Map<String, Any> ?: emptyMap()
return (revisions["revision"] as? List<Map<String, Any>> ?: emptyList())
    .mapNotNull { entry -> parseRevision(entry) }
```

### Problems

1. **No compile-time safety** — any TeamCity API change silently produces `null` values or `ClassCastException` at runtime.
2. **Compiler warnings** — 3 unchecked cast warnings in `CommitStampResolver`.
3. **No retry/resilience** — a single transient failure will fail the build (timeouts were added but no retry logic).
4. **Hard to test** — tests must construct nested `Map<String, Any>` fixtures manually.

### Proposed Solution

Replace `TeamcityRestClient` with `octopus-external-system-clients` (the existing TeamCity client library used by other octopus projects). This would provide:

- Typed DTOs for build, revision, and VCS root responses
- Built-in retry and error handling
- Consistent client configuration across octopus projects

### Migration Steps

1. Add `octopus-external-system-clients` dependency to `build.gradle.kts`
2. Replace `TeamcityRestClient` usages in `CommitStampResolver` with the typed client
3. Update `CommitStampResolver` to use typed DTOs instead of map navigation
4. Update `CalculateSonarParametersCommand` to construct the new client
5. Simplify test fixtures in `Fixtures.kt` (remove `tcBuildResponse`, `tcRevision`, etc.)
6. Remove `TeamcityRestClient.kt`

### Impact

- Eliminates 3 compiler warnings
- Removes ~50 lines of manual map parsing in `CommitStampResolver`
- Simplifies test fixtures

