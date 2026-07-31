# DocScrubber

A Java Swing desktop application that scans documents (PDF, Word/docx, plain text, markdown) for **prompt-injection "poison pills"** — hidden or embedded instructions intended to hijack GenAI tools that later process the document.

DocScrubber is a defensive pre-flight scanner: it scores a document's risk, shows exactly what was found and where, and (in a future release) produces a cleaned copy. It never executes, renders, fetches, or follows anything found inside a scanned document.

## Why

Real-world prompt-injection payloads hide behind formatting a human reader never notices: white-on-white text, 1pt fonts, PDF "invisible" render modes, zero-width Unicode characters spliced into ordinary words, or instructions buried in document metadata, comments, and tracked-change deletions. Plain keyword scanning misses all of these — they look like normal instructions once text is extracted. DocScrubber evaluates both the *text* and the *visibility* of every fragment it extracts, so a phrase like "ignore previous instructions" scores very differently depending on whether it's sitting in plain sight or hidden from view.

## Features

- **Multi-format parsing**: plain text, Markdown, docx (via Apache POI), PDF (via Apache PDFBox)
- **Two-layer rules engine**: content rules (regex, keyword lists, Unicode character classes) and structural detectors (low-contrast text, tiny fonts, hidden runs, invisible PDF render modes, off-page text, suspicious channels), evaluated together with score-multiplying combos when both fire on the same fragment
- **Declarative `rules.json`**: rule changes never require a code change; the bundled ruleset ships with 30 seed rules covering common injection patterns, and the file is schema-validated on load with a clear GUI error on failure
- **Channel-aware**: distinguishes body text from headers/footers, comments, tracked-change deletions, footnotes, metadata/custom properties, alt-text, and hyperlink targets — places a casual read-through never checks
- **Honest about its own limits**: PDF hidden-text detection relies on documented assumptions (e.g. an assumed white page background) rather than guessing at rendered output. Every PDF scan result carries a visible notice explaining exactly what wasn't checked, so a clean score is never presented as a stronger guarantee than it is
- **JSON + HTML reports**, findings table with revealed hidden/invisible characters in the evidence preview
- **FlatLaf** dark/light theming

## Tech stack

Java 24, Maven, Swing + FlatLaf, Apache PDFBox, Apache POI (XWPF), Jackson, JUnit 5. No network access at runtime, no dynamic code loading — rules are data, never code.

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

`rules.json` (bundled at `src/main/resources/rules/rules.json`) is read at startup; a different file can be loaded from the GUI's Rules menu. See the class docs on `Rule`, `RuleSet`, and `RulesValidator` for the schema.

## Testing

`src/test/java/.../fixtures/FixtureBuilder.java` programmatically generates docx/PDF fixtures for each payload class (white-on-white text, vanish runs, invisible PDF render mode, zero-width Unicode smuggling, metadata payloads, off-page text) — no hand-crafted binaries are committed to the repo.

## Status

This is a Phase 1 build: parsers, the rules engine, scoring, a seed ruleset, fixtures/tests, and a functional scan/results/export GUI. Sanitization (producing a cleaned copy) and batch-mode UI polish are planned for later phases.

## License

MIT — see [LICENSE](LICENSE).
