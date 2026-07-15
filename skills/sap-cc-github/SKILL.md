---
name: sap-cc-github
description: >-
  Git commit and GitHub pull-request conventions for the SAP Commerce Developers Toolset plugin —
  how to write commit messages, sign off (DCO), branch, push to the fork, and open a PR into the
  upstream epam repository with the right title, labels, assignee, reviewer, milestone, and
  CHANGELOG entry. Use this whenever committing, "commit this", "create a PR", "open a pull
  request", "push changes", "apply labels", "follow the commit pattern", "update the changelog with
  the PR link", or preparing any change in this repo for review.
---

# SAP Commerce Developers Toolset — commit & PR conventions

Architecture and build/test commands are in the repo's root `CLAUDE.md`; Kotlin code-style rules
are in `skills/sap-commerce-plugin-dev/SKILL.md`. This skill is the single source of truth for
**getting a change committed and into a pull request**.

## Scope & consent (read first)

- **Apply changes to the current branch only.** When asked to fix, tweak, or "apply the same change",
  edit the branch the user is already on. Do **not** spin the change onto a new branch or a separate
  PR — even when the change touches a different module/file than the branch's main topic — unless the
  user **explicitly** asks for a new branch/PR. If a fix seems to belong elsewhere, surface that in
  your summary and let the user relocate it manually; don't relocate it for them.
- **Create a PR only on explicit request.** "Commit and push" means commit and push to the current
  branch — it does not authorize opening a new PR.
- **Never roll back or rewrite without being asked.** Do not revert commits, close/reopen PRs,
  force-push, `git reset`, or undo pushed work on your own initiative. If something looks misplaced,
  say so and leave it — the user will fix it manually.

## Remote topology

- `origin` — the contributor's **fork** (e.g. `ekalenchuk/sap-commerce-intellij-idea-plugin`). Push
  branches here.
- `upstream` — `epam/sap-commerce-intellij-idea-plugin`. This is the **PR target**; PRs merge into
  `epam:main`.
- Never commit directly to `main`. Branch first: `git checkout -b feature/<short-topic>`.

### Pushing

Push the branch to `origin` (the fork). The assistant's sandboxed shell often lacks working GitHub
credentials (no keychain entry over HTTPS; the only available SSH key may map to a suspended/other
account). When `git push` fails with `could not read Username` or `Permission denied`/`account is
suspended`, **do not keep retrying** — ask the user to run it themselves in-session:

```
!git push -u origin feature/<short-topic>
```

## Commit messages

Format the subject as:

```
<Category> | <Imperative description>
```

- `<Category>` matches the CHANGELOG section for the change, e.g. `AI`, `Project Import`, `Spring`,
  `Type System`, `ImpEx`, `Bean System`, `Other`.
- **Do NOT prefix the subject with `#NNNN` yourself.** The merged history shows `#1950 | AI | …`,
  but that number is applied by the maintainer's **squash-merge** (the PR number). Local commits
  omit it. (See the `commit-message-workflow` memory: don't flag a missing `#NNNN` on local
  commits.)
- **DCO sign-off is required** (`CONTRIBUTING.md`). End the message with:
  `Signed-off-by: Real Name <email>` — the contributor's own name/email (e.g.
  `Signed-off-by: Eugeni Kalenchuk <e.kalenchuk@gmail.com>`).
- **Do not add a `Co-Authored-By` line for the AI assistant.**

Because PRs are squash-merged, multiple small commits on a branch are fine — they collapse into one
on merge. If a commit is already pushed, prefer adding a **follow-up commit** over `git commit
--amend` + force-push (the amend of a pushed commit is treated as destructive and will be blocked).

## Opening the PR

Create it against **`epam/…:main`** from **`<fork>:<branch>`**. `gh` is usually not installed, so
create the PR via the browser (GitHub compare page):

`https://github.com/epam/sap-commerce-intellij-idea-plugin/compare/main...<fork-owner>:sap-commerce-intellij-idea-plugin:<branch>?expand=1`

Set, matching the convention in recent similar PRs (open one as a template first):

- **Title** — the commit subject `<Category> | <description>` (no `#NNNN`; GitHub adds it on merge).
- **Description** — describe what/why. The demo-style "prompt + tool output" body used by AI/MCP
  PRs requires actually running the tool; never fabricate tool output. If the new code isn't loaded
  into a running sandbox IDE, write an accurate factual description instead.
- **Labels** — the cross-cutting label plus the feature label(s). Examples:
  - AI/MCP type-system tool → `AI` + `Type System`
  - AI/MCP ImpEx tool → `AI` + `ImpEx`
  Check a recent analogous PR for the exact set.
- **Assignee** — self (the PR author).
- **Reviewer** — `mlytvyn` (Mykhailo Lytvyn), the maintainer; consistent across PRs.
- **Milestone** — the current open milestone.
- Create as a **ready-for-review** PR (use the "Create pull request" dropdown option), not a draft,
  unless the work is WIP.

## CHANGELOG

Add a bullet under the current unreleased version's matching `### \`<Category>\`` section in
`CHANGELOG.md`, and include the PR link in the **same format as existing entries**:

```
- <Imperative description> [#NNNN](https://github.com/epam/sap-commerce-intellij-idea-plugin/pull/NNNN)
```

The PR number isn't known until the PR exists, so the flow is: commit the change (changelog line
without the link) → push → open the PR → add the `[#NNNN](…)` link → commit that as a follow-up →
push again.

Don't hand-craft plugin change notes elsewhere — marketplace notes are rendered from `CHANGELOG.md`
by the `org.jetbrains.changelog` Gradle plugin (see `CLAUDE.md`).
