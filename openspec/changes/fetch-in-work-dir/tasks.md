## 1. Baseline

- [x] 1.1 Characterization tests of Sonar parameter calculation green (`test/onb-001-baseline`,
      `892fabd`)

## 2. Implementation (test-first)

- [x] 2.1 Failing test over the meta-runner XML: the fetch step has
      `teamcity.build.workingDir=%WORK_DIR%` and no step condition, and the meta-runner declares
      a `WORK_DIR` parameter defaulting to `%WORK_DIR%`
- [x] 2.2 Change `metarunners/CalculateSonarParameters.xml`; describe the step in
      `docs/calculate-sonar-parameters.md`
- [x] 2.3 Redaction: redo the prototype's step change by hand; never copy its `docs/tech-debt.md`
      (it names an internal host and build path). Grep the diff and messages over the forbidden
      list before committing
- [ ] 2.4 Full build green; the program pilot shows the fetch in `WORK_DIR` and the commit taken
      from the Build Working Directory's repository, and also:
  - [ ] (a) after the meta-runner is re-uploaded, existing template steps without the new
        `WORK_DIR` property pick up the meta-runner default
  - [ ] (b) the resolved values of `%CUSTOMIZATION_APP_PATH%` all lie inside the checkout
  - [ ] (c) existing multi-root chains whose `WORK_DIR` lies inside a second root's checkout
        directory still find the target branch
