# Tasks

Work test-first: each task's test bullet precedes its implementation bullet.

## 1. `VcsSshUrlParser`

- [x] 1.1 Rename `src/test/kotlin/util/BitbucketSshUrlParserTest.kt` → `VcsSshUrlParserTest.kt`.
      Add cases: SCP-like GitHub remote, SCP-like with mixed case, `ssh://` with explicit port,
      `https://` rejected. Existing `ssh://` cases pass unchanged.
- [x] 1.2 Rename `src/main/kotlin/util/BitbucketSshUrlParser.kt` → `VcsSshUrlParser.kt`, object
      included. Two ordered patterns; KDoc and `IllegalArgumentException` message name both forms.
- [x] 1.3 Update the two call sites in `CommitStampResolver` (`resolveWithoutVcsSettings`,
      `resolveWithVcsSettings`).

## 2. `ComponentRegistrationResolver`

- [x] 2.1 `src/test/kotlin/resolver/ComponentRegistrationResolverTest.kt`: `getById` succeeds →
      `REGISTERED`; throws `NotFoundException` → `UNREGISTERED`; throws `RuntimeException` →
      propagates.
- [x] 2.2 `src/main/kotlin/resolver/parameters/ComponentRegistrationResolver.kt`, with the
      `ComponentRegistration` enum in the same file.

## 3. Thread registration through the resolvers

Each sub-task adds a `registration: ComponentRegistration` parameter and the short-circuit given in
`design.md`, as the method's first statement. Existing tests pass `REGISTERED` and are otherwise
untouched — a changed assertion in an existing test means the registered path regressed.

- [x] 3.1 `SonarServerResolver.resolveSonarServer` → `COMMUNITY`, without calling `getById`.
      Test both branches.
- [x] 3.2 `SonarExecutionResolver.skipSonarMetarunnerExecution` → `false`. Test: unregistered +
      in applied-sast → `false`; unregistered + `doc-` prefix → `false`; unregistered plain →
      `false`.
- [x] 3.3 `SonarExecutionResolver.skipSonarReportGeneration` → `false`.
- [x] 3.4 `SonarExecutionResolver.resolveSonarPluginBuildSystem` → `null`.
- [x] 3.5 `CommitStampResolver.resolve` → skip `getVCSSetting`, go straight to
      `resolveWithoutVcsSettings`, keeping the `commitStamps.isNotEmpty()` precondition first.
      Test: unregistered with a GitHub SCP-like revision yields the expected project/repo keys and
      `DEFAULT_BRANCH_CANDIDATES`.

## 4. Branch short-circuit

- [x] 4.1 Add `UNREGISTERED_TARGET_BRANCH` to `BranchConstants`.
- [x] 4.2 `SonarParametersCalculatorTest`: unregistered non-PR build → `SONAR_TARGET_BRANCH` is
      `main` **and** `targetBranchResolver` records zero interactions, which is what keeps VCS
      Facade out of the path. The zero-interaction assertion is the requirement; the returned value
      alone is not sufficient evidence.
- [x] 4.3 Add the short-circuit to `resolveBranchContext`.

## 5. Orchestration

- [x] 5.1 `SonarParametersCalculatorTest`: end-to-end case for an unregistered component covering
      all twelve `SonarParametersDTO` fields against the spec's scenarios — ten in the
      full-parameter-set test, source and target branch in the sibling branch test — including
      `SONAR_RUNNER_EXTRA_PARAMETERS` as the newline-joined form of `SONAR_EXTRA_PARAMETERS`.
- [x] 5.2 Add `componentRegistrationResolver` to `SonarParametersCalculator`'s default-argument
      constructor list; resolve registration as the first statement of `calculate()` and pass it to
      `commitStampResolver`, `sonarServerResolver` and the three `sonarExecutionResolver` calls.
- [x] 5.3 PR-build regression test for an unregistered component — confirms the branch
      short-circuit sits below the PR check.

## 6. Docs and verification

- [x] 6.1 `docs/calculate-sonar-parameters.md`: new section on unregistered components with the
      parameter table from the proposal; update any text asserting CRS registration is required.
- [x] 6.2 `./gradlew build` — unit tests, detekt and ktlint.
