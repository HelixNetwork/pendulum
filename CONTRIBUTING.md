First off, thanks for taking the time to contribute!

This document covers a basic set of guidelines for contributing to the Helix Network and its packages, which are hosted in the HelixNetwork Organization on GitHub. These are mostly guidelines, not fixed rules. Use your best judgment, and feel free to propose changes to this document in a pull request.
Please be sure to read our code of conduct on: https://hlx.readme.io/docs/contribution-guidelines.

# Contribution

When contributing to a HelixNetwork repository, please first propose the changes you wish to make via an issue. If there is an existing issue that has been passed by the reviewers and addresses the change, please commit the change to your dedicated branch.

## Contribution Example

1.  Open issue, e.g. "Unexpected conversion result (#153)" (note that each issue has an unique id).
2. Issue has been discussed and specs for implementation have been approved.
3. Fork `dev` branch to `dev-conversion-patch`.
4. Implement changes and compile build.
5. Push to `dev-conversion-patch` and submit a pull request to `dev` (The pull request title should contain the id of the issue, in this example: (#153)).
6. Pull request is reviewed and approved

# Branch Policies
`dev` and `master` are protected by the following policies:
- Repository maintainer (oliver) has to approve changes
- At least one reviewer has to approve changes
- Commits have to be signed ([tutorial](https://help.github.com/en/articles/signing-commits))
- Status Checks: Pull Requests have to pass travis integration test
