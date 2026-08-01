# DocScrubber — Design & Decision Log

This file exists per the team standard (`java-swing-project-setup` skill,
"Docs: skills over CLAUDE.md") that each Java Swing sibling project keep a
living record of *why* non-trivial decisions were made — not just what
shipped. `README.md` and [RULES_AND_SCORING.md](RULES_AND_SCORING.md) are
the "what/how" reference; this file is the "why," including the paths not
taken. Modeled on `kiro-control-panel`'s `SPEC.md`.

New entries go at the bottom of the relevant section as decisions get
made — this is a log, not a one-time snapshot.

## Development workflow

Direct-to-`main` through v1.0.0 (`f5614df`). Post-1.0.0: every change
starts as a GitHub issue, one short-lived branch per issue
(`issue-<#>-slug`), PR into `main` (`fixes #N`), and every issue-fixing PR
bumps `pom.xml`'s patch version in the same PR. `main` has no branch
protection configured yet — solo-maintainer repo, same tradeoff
`kiro-control-panel` made explicitly for the same reason.

## Core architecture

**Two-layer rule model, strictly separated.** `content` rules
(regex/keywordList/unicodeClass) only ever see a fragment's *text*;
`structural` rules (`detector`) only ever see its *visibility attributes*
(color, font size, hidden flags, position) — never its text. Keeps the
two concerns from leaking into each other: a content rule can't be fooled
by formatting tricks it was never shown, and a structural detector can't
be fooled by text content it never reads.

**The scorer does no detection of its own.** `Scorer` only turns
`Finding`s the rules engine already produced into a number and a verdict;
every point on the score traces back to a specific rule that fired. This
is a deliberate boundary (see `Scorer`'s javadoc) that the #9 scoring
change below had to explicitly reconcile with, rather than quietly break.

**`rules.json` is data, not code.** Rule tuning — adding a pattern,
changing a severity, disabling a noisy rule — never requires a code
change or a rebuild. The tradeoff is a validation surface
(`RulesValidator`) that has to catch schema errors before a bad file can
break a scan; see §5.1/5.2 of `RULES_AND_SCORING.md`.

## Hardening

These landed in the pre-1.0.0 hardening pass, direct to `main` (no issue
numbers — before the issue/branch convention started):

- **ReDoS timeout + max file size** (`6e0b84a`): `rules.json` can be
  loaded from a file the user points the app at, so a pathological regex
  in it could hang a scan indefinitely against untrusted document text.
  `TimeBoundedCharSequence` enforces a 500ms-per-fragment budget via the
  JDK's interruptible-`charAt()` technique (the regex engine has no
  native timeout); an offending rule is skipped for the rest of that
  document and surfaced as a scan limitation, never silently. Files over
  100MB are rejected before parsing begins.
- **Dependency CVEs + AboutDialog HTML injection** (`0928d5c`): found via
  `snyk test`/`snyk code test` plus manual review of the parser/rules/
  report-writer/network paths. `poi-ooxml` bumped past CVE-2025-31672 —
  worth calling out specifically because a duplicate-zip-entry-name
  evasion in OOXML is exactly the kind of "different tools read different
  content from the same file" gap this project exists to close, not a
  generic library CVE. Also fixed: an unescaped GitHub release tag name
  rendering as live HTML in `AboutDialog`.
- **XXE hardening on the XML parser** (`83b31c1`, issue #15): DOCTYPE
  declarations rejected outright, external entity/DTD resolution
  disabled — this parser exists specifically to handle untrusted,
  adversarial input, so it doesn't get the JDK's permissive defaults.
  Verified with a test that a classic external-entity file-disclosure
  attempt fails closed rather than leaking the target file.

## Scope: no sanitizer, ever (`f6f8bd3`)

DocScrubber will never produce a "cleaned" copy of a scanned document.
This was on the original roadmap (Phase 2, "Sanitization") and was
deliberately dropped, not deferred:

A tool that edits out what it found is taking ownership of the
document's safety, and a detector that also hands back a
guaranteed-safe rewrite is promising something it can't back up. Missing
one payload variant and returning a document that now carries an
implicit stamp of approval is worse than not sanitizing at all.
Stripping to plain text to sidestep that risk just trades one problem
for another (reformats the document, discards structure) without
removing it, and a scan-clean-rescan loop only ever chases whatever the
current ruleset happens to catch. The correct response to a compromised
document is rejection and resubmission by the originator, not automated
cleanup. See [Scope](README.md#scope) in the README for the user-facing
version of this.

This is a permanent boundary, not a v1 limitation — don't re-propose a
sanitizer/cleaned-copy feature without a decision to explicitly reverse
this entry first.

## Evasion resistance (issues #7, #8)

**NFKC normalization + zero-width shadow copy (#7).** Two verified real
gaps: fullwidth-Unicode text (`ｉｇｎｏｒｅ`) and zero-width-space-
interleaved text (`i​g​n​o​r​e`) both defeated CONTENT-001 outright, and
the ZWSP case meant COMBO-004 could never fire for that exact technique
since nothing connected the disguise back to the phrase. Fixed by
matching content rules against a normalized "shadow" copy instead of raw
text — see `RULES_AND_SCORING.md` §2.3 for the full mechanism. Two
things this deliberately does *not* do, both load-bearing: `unicodeClass`
rules and finding evidence keep using the *original* text, since
stripping first would make the "detect this character's presence" rules
never fire and would hide the disguise from the human reviewer.

**HTML/XML comment stripping, not tag stripping (#8).** Same shadow-copy
pattern, extended to strip `<!-- ... -->` before content regexes run —
speculative relative to #7 (matters only if some downstream consumer
strips comments before an LLM sees the text) but cheap defense-in-depth.
Explicitly does **not** strip tags generically: CONTENT-005/006/011 are
themselves regex rules that rely on literal `<...>` syntax as their own
detection signature, so generic tag-stripping would delete the payload
before those rules ever saw it.

## Scoring: the repeat-hit cap (#9)

Flagged as **"a scoring-philosophy change, not a tweak"** against
`Scorer`'s own javadoc commitment to never inflate/deflate the score
itself. Four mechanisms were on the table: (a) cap repeated hits of the
same rule+channel at a ceiling, (b) require ≥2 distinct signal tags
before a verdict can cross into `likelyCompromised`, (c) length/density
normalization of the raw score, (d) some combination. Chose **(a)**:
findings are grouped by `(ruleId, channel)` and capped at 1.5x the rule's
base weight, but never suppressed below the group's own strongest single
instance. Rationale for the floor: if one instance is already
combo-boosted past the cap, that instance's value is the floor — the cap
only bounds what *additional* repeats add on top, never a genuinely
strong single signal. See `RULES_AND_SCORING.md` §4.3 for the worked
example (a 100-page manual with 3 unrelated `CONTENT-018` hits landing at
`lowRisk` instead of `suspicious` on volume alone).

## Embedded objects: count-only is a boundary, not an oversight (#10)

`Channel.EMBEDDED_OBJECT` existed in the enum from day one but was never
populated — no embedded-stream extraction/counting logic existed at all
until #10. The decision: report *counts* of embedded files/attachments
(DOCX `getAllEmbeddedParts().size()`, PDF `/EmbeddedFiles` name tree) as
a structured `embeddedObjectCount` field plus a limitations notice — not
content extraction, not structural inspection. This was explicitly
called out as a conscious v1 boundary so it wouldn't get silently
mistaken for an oversight later.

That boundary was revisited once, in-session, against an external design
review proposing two extensions (issues currently open, not yet
implemented):

- **#24 — structural-only heuristics** (magic-byte executable
  signatures, OLE macro-storage stream names) on embedded-stream
  *metadata*: accepted in principle. Real, human-relevant threat
  (an embedded executable/macro container is dangerous regardless of
  whether an LLM ever reads it) at low cost and zero new attack surface
  — inspecting header bytes/stream names, never parsing or executing.
- **#25 — full text extraction** from inside embedded binary streams:
  did **not** clear the bar, and was split into its own issue tagged
  `experimental` rather than folded into #24 or quietly dropped. This is
  the first real use of "experimental" as a category in this project:
  parked behind an explicit toggle-if-built / removable-without-
  structural-harm / no-implementation-until-a-concrete-case constraint,
  rather than either committing to it or deleting the idea outright. The
  threat model it chases (a downstream LLM pipeline walking into an OLE/
  binary stream for context) is speculative, not demonstrated the way
  #7's evasion gaps were verified — default is: don't build it absent a
  concrete case.

Precedent for future scope debates on this project: a threat that's real
regardless of any downstream LLM behavior (#24) clears the bar directly;
a threat that's contingent on a specific, unverified downstream
consumer's behavior (#25) goes to the `experimental` track instead of
either roadmap or silent rejection.

## `STRUCT-007` (`overlappedText`): shipped disabled on purpose

Registered in `DetectorRegistry` and referenceable from `rules.json`, but
its `evaluate()` always returns `false`. Reliable paint-order overlap
detection (text hidden beneath a later-painted image/shape) risks
flagging watermarks and decorative images as hidden text, which conflicts
with this project's no-false-positive priority. Shipped as a real,
documented, disabled rule (`"enabled": false`) rather than a missing
detector id specifically so referencing it never trips the "unknown
detector" validation warning — the gap stays visible in `rules.json`
itself, not just in a doc somewhere.

## Cross-project alignment

This file is doc-scrubber's contribution to the sibling-project alignment
initiative (issue #27) — doc-scrubber is the newest of the three Java
Swing projects and the only one that didn't already have one.
doc-scrubber's `gui/` package layout and thin `ship-issue`/`verify`
skills are themselves the reference examples cited for the other two
projects' equivalent alignment issues.
