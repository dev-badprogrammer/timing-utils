## Summary

<!-- Provide a brief summary of the changes introduced by this PR. What problem does it solve? How does it fix it? -->

## Type of Change

- [ ] build: changes to a build system or external dependencies
- [ ] chore: maintenance / config
- [ ] ci: changes to CI/CD configuration or workflows
- [ ] docs: documentation
- [ ] feat: new feature
- [ ] fix: bug fix
- [ ] perf: performance improvement
- [ ] refactor: no functional change
- [ ] revert: reverts a previous commit
- [ ] test: tests only

## Checklist

- [ ] `mvn clean verify` passes locally (Checkstyle + Tests + Java sources jar + Java docs jar + JaCoCo Coverage ≥ 80%)
- [ ] SonarQube Cloud analysis run locally against this branch (`-Dsonar.branch.name=<branch>`) and Quality Gate passed

  `mvn clean verify sonar:sonar -Dsonar.token=$SONAR_TOKEN -Dsonar.branch.name=<your-branch-name>`

- [ ] PR title follows Conventional Commits format
- [ ] Linked issue referenced in the commit message (e.g. `(#10)`), so it carries through to the auto-populated PR title
