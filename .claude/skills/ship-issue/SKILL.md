---
name: ship-issue
description: The standard workflow for shipping a bug fix or feature to DocScrubber — file a GitHub issue, branch off main, implement, verify, bump the patch version, and open a PR. Use whenever picking up a bug fix or feature for this repo.
---

# Shipping a change to DocScrubber

Follow `java-swing-ship-issue` (the generic workflow shared across the
Java Swing project family) with these DocScrubber specifics:

- **Project path**: `/projects/doc-scrubber` inside the build container.
- **Verify**: use this repo's own `.claude/skills/verify/SKILL.md` for
  build/launch mechanics.
- **Rules changes need extra care**: if the change touches
  `src/main/resources/rules/rules.json` or anything under `rules/` or
  `rules/detector/`, re-read `RULES_AND_SCORING.md` first — rules are
  schema-validated on load and drive real detection/scoring behavior, not
  just configuration.
- **Untrusted-input surfaces**: this app parses attacker-controllable
  documents (PDF/docx/etc.). A change to `parser/` is a good candidate for
  documenting the security reasoning in README's hardening section (ReDoS
  timeouts, size caps, XXE defenses) alongside the code, matching this
  project's existing practice — not just implementing it silently.
- No repo-specific branch-naming or extra PR-checklist step beyond the
  generic workflow has been established here yet; follow
  `java-swing-ship-issue` as-is until one is.
