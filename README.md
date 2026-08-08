# DocScrubber

[![Build](https://github.com/OurGiant/doc-scrubber/actions/workflows/build.yml/badge.svg)](https://github.com/OurGiant/doc-scrubber/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/OurGiant/doc-scrubber?label=Release)](https://github.com/OurGiant/doc-scrubber/releases/latest)
[![License: MIT](https://img.shields.io/github/license/OurGiant/doc-scrubber)](LICENSE)
[![Java 24](https://img.shields.io/badge/Java-24-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Platforms](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-blue)](#building-and-running)

A Java Swing desktop application that scans documents (PDF, Word/docx, plain text, Markdown, JSON, YAML, XML, and source formats like TypeScript, JavaScript, Python, HTML, and CSV) for **prompt-injection "poison pills"** — hidden or embedded instructions intended to hijack GenAI tools that later process the document.

DocScrubber is a defensive pre-flight scanner: it scores a document's risk and shows exactly what was found and where, so a human can decide what happens next. It never executes, renders, fetches, or follows anything found inside a scanned document — and it never produces a modified or "cleaned" copy of one either (see [Scope](#scope) below).

## Why

Real-world prompt-injection payloads hide behind formatting a human reader never notices: white-on-white text, 1pt fonts, PDF "invisible" render modes, zero-width Unicode characters spliced into ordinary words, or instructions buried in document metadata, comments, and tracked-change deletions. Plain keyword scanning misses all of these — they look like normal instructions once text is extracted. DocScrubber evaluates both the *text* and the *visibility* of every fragment it extracts, so a phrase like "ignore previous instructions" scores very differently depending on whether it's sitting in plain sight or hidden from view.

## Features

- **Multi-format parsing**: plain text, Markdown, docx (via Apache POI), PDF (via Apache PDFBox), JSON, YAML, XML (string values, and — for YAML/XML — comments, since both have a comment syntax a downstream parser never sees), and source/code text formats (TypeScript, TSX, JavaScript, JSX, Python, HTML, CSV) treated as plain text
- **Two-layer rules engine**: content rules (regex, keyword lists, Unicode character classes) and structural detectors (low-contrast text, tiny fonts, hidden runs, invisible PDF render modes, off-page text, suspicious channels, executable/macro signatures in embedded objects), evaluated together with score-multiplying combos when both fire on the same fragment
- **Evasion-resistant matching**: content rules run against a normalized "shadow" copy of each fragment (NFKC Unicode normalization, plus zero-width/bidi/tags-block characters and HTML/XML comments stripped) — so disguised phrases like fullwidth-Unicode or zero-width-interleaved letters are still caught. The unmodified original text is always what's shown in findings evidence and what structural/Unicode-presence rules evaluate, so the disguise itself stays visible to a human reviewer
- **Declarative `rules.json`**: rule changes never require a code change; the bundled ruleset ships with 35 seed rules covering common injection patterns, and the file is schema-validated on load with a clear GUI error on failure
- **Channel-aware**: distinguishes body text from headers/footers, comments, tracked-change deletions, footnotes, metadata/custom properties, alt-text, and hyperlink targets — places a casual read-through never checks
- **Honest about its own limits**: PDF hidden-text detection relies on documented assumptions (e.g. an assumed white page background) rather than guessing at rendered output. Every PDF scan result carries a visible notice explaining exactly what wasn't checked, so a clean score is never presented as a stronger guarantee than it is. Embedded files/OLE objects (docx) and file attachments (PDF) are counted and flagged the same way — via a limitations notice plus a structured `embeddedObjectCount` field in the JSON report — rather than silently ignored. Their raw stream bytes also get one bounded, best-effort structural check (executable magic-byte signatures, OLE macro-storage entries — see `RULES_AND_SCORING.md` §6); text content inside them otherwise remains unscanned
- **Density-aware scoring**: repeated hits of the same rule in the same channel are capped rather than scaling linearly, so a long legitimate document that happens to trip one rule several times in unrelated spots doesn't cross a verdict threshold on volume alone — a single strong finding is never suppressed below its own value, only additional repeats are bounded
- **Hardened against hostile input**: a per-fragment regex time budget (500ms) skips and reports any rule that hits pathological backtracking instead of hanging a scan, files over 100MB are rejected before parsing begins, and the embedded-object structural scan (above) reads a size-capped 20MB from each stream regardless of what the format's own declared size claims
- **JSON + HTML reports**, findings table with revealed hidden/invisible characters in the evidence preview
- **FlatLaf** dark/light theming

## Tech stack

Java 24, Maven, Swing + FlatLaf, Apache PDFBox, Apache POI (XWPF), Jackson (including `jackson-dataformat-yaml`), the JDK's built-in XML DOM parser, JUnit 5. No dynamic code loading — rules are data, never code. Document parsing, rule evaluation, and scoring are fully offline; the only network call anywhere in the app is a startup/Help > About check against GitHub's releases API (see `UpdateChecker`), which never touches document content and can be observed failing closed (silently, with no scan impact) on an offline machine.

XML parsing is hardened against XXE (external entity injection): DOCTYPE declarations are rejected outright and external entity/DTD resolution is disabled, since this parser exists specifically to handle untrusted, potentially adversarial input.

## Building and running

```bash
mvn test              # run the test suite
mvn package            # build target/doc-scrubber-all.jar (shaded, runnable)
java -jar target/doc-scrubber-all.jar
```

## Project layout

```
com.ourgiant.docscrubber
├── model/       ExtractionModel, TextFragment, Channel, VisibilityAttributes
├── parser/      DocumentParser implementations per format + registry
├── rules/       Rule/RuleSet types, JSON loading, schema validation
│   └── detector/  Built-in structural detectors
├── engine/      RulesEngine — evaluates rules against fragments -> Findings
├── score/       Scorer — weighted score + combo multipliers -> verdict
├── report/      JSON and HTML report writers
└── gui/         Swing UI
```

`rules.json` (bundled at `src/main/resources/rules/rules.json`) is read at startup; a different file can be loaded from the GUI's Rules menu. See [RULES_AND_SCORING.md](RULES_AND_SCORING.md) for the full schema reference, the bundled ruleset, and how scoring works — or the class docs on `Rule`, `RuleSet`, and `RulesValidator` for the code-level detail. See [SPEC.md](SPEC.md) for the design/decision log — *why* things work this way, not just how.

## Testing

`src/test/java/.../fixtures/FixtureBuilder.java` programmatically generates docx/PDF/JSON/YAML/XML fixtures for each payload class (white-on-white text, vanish runs, invisible PDF render mode, zero-width Unicode smuggling, metadata payloads, off-page text, nested/comment payloads, an XXE attempt) — no hand-crafted binaries are committed to the repo.

## Status

1.0.0 shipped parsers, the rules engine, scoring, a seed ruleset, fixtures/tests, a functional scan/results/export GUI, and an in-app rules explorer/editor (Rules menu) with save-time and ad hoc validation.

1.1.0 adds JSON/YAML/XML parsing, evasion-resistant content-rule matching (Unicode normalization plus zero-width/comment stripping), density-aware scoring (repeated-hit capping), embedded-object/attachment reporting, a hardened ReDoS timeout and max-file-size limit, and [RULES_AND_SCORING.md](RULES_AND_SCORING.md) documenting the rules engine and scoring in full.

1.2.0 adds structural detectors for executable/macro signatures inside embedded objects (bounded, best-effort — never parses or executes stream content), folder support for the file queue (drag-and-drop or Add Files/Folder... now accept a directory, walked recursively, only registered file types added), a Remove All button for the file queue, and a persistent caution banner reminding users to treat documents headed to an AI tool with the same source-verification and least-privilege/zero-trust judgment as an email attachment.

1.3.0 adds system tray support so the app can stay dormant instead of exiting when its window is closed (View > Minimize to Tray, on by default when the platform supports it, with a tray menu to reopen or exit), and a directory watch feature (Tools > Watch Folders...) that auto-scans new files dropped into registered folders and reports the verdict and score as a severity-styled tray notification. Batch-mode UI polish is still planned for a later release.

## Scope

DocScrubber intentionally does not produce a "cleaned" copy of a scanned document, and won't. A tool that edits out what it found is taking ownership of the document's safety — and a detector that also hands back a guaranteed-safe rewrite is promising something it can't back up. Missing one payload variant and returning a document that now carries an implicit stamp of approval is worse than not sanitizing at all. Stripping to plain text to sidestep that risk just trades one problem for another (reformats the document, discards structure the recipient may need) without removing it, and a scan-clean-rescan loop only ever chases whatever the current ruleset happens to catch — in an adversarial setting, what it misses is exactly the risk.

The correct response to a compromised document is to reject it and have the originator correct and resubmit it, not to make it usable. DocScrubber's job stops at giving you the evidence to make that call.

## License

MIT — see [LICENSE](LICENSE).
