## 1. Baseline

- [x] 1.1 Characterization tests of Sonar parameter calculation green (`test/onb-001-baseline`,
      `892fabd`)

## 2. Implementation (test-first)

- [ ] 2.1 Failing test over the meta-runner XML: the fetch step has
      `teamcity.build.workingDir=%WORK_DIR%` and the condition
      `SKIP_SONAR_METARUNNER_EXECUTION == false`, and the meta-runner declares a `WORK_DIR`
      parameter defaulting to `%WORK_DIR%`
- [ ] 2.2 Change `metarunners/CalculateSonarParameters.xml`; describe the step in
      `docs/calculate-sonar-parameters.md`
- [ ] 2.3 Redaction: redo the prototype's step change by hand; never copy its `docs/tech-debt.md`
      (it names an internal host and build path). Grep the diff and messages over the forbidden
      list before committing
- [ ] 2.4 Full build green; the program pilot shows the fetch in `WORK_DIR` and the commit taken
      from the Build Working Directory's repository
