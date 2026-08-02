# The Rules Engine and Scoring

This is the part of DocScrubber that actually decides whether a document is safe: every other
piece of the app (parsers, the GUI, report writers) exists to feed it clean input and present its
output. This document explains how it works, what ships in the box, and how to tune or extend it.

## 1. Overview: how a scan flows

```
file on disk
   │
   ▼
DocumentParser (per format)          -> ExtractionModel: TextFragments + limitations
   │
   ▼
TextNormalizer.shadow(...)           -> a "shadow" copy of each fragment's text (see §2.3)
   │
   ▼
RulesEngine.evaluate(...)            -> raw Findings (one per rule x fragment match)
   │
   ▼
Scorer.score(...)                    -> a 0-100 score + a Verdict
   │
   ▼
JSON / HTML report, GUI results panel
```

Each stage is deliberately narrow:

- **Parsers** turn a format-specific file into a flat list of `TextFragment`s (text + channel +
  location + visibility attributes) and a list of plain-English `limitations` — honest caveats
  about what this parser could not determine reliably (see [`README.md`](README.md) for the
  per-format list). Parsers never decide what's suspicious; that's the rules engine's job alone.
- **The rules engine** matches every enabled rule against every fragment and produces `Finding`s —
  raw, unweighted evidence. It never modifies, removes, or "cleans" anything (see the README's
  [Scope](README.md#scope) section for why).
- **The scorer** turns those findings into a single number and a verdict, on purpose *not* doing
  any of its own detection — every point on the score traces back to a specific rule that fired.

## 2. The rules engine

### 2.1 Rule families and types

Every rule in `rules.json` has a `family` and a `type`, and the two are constrained together:

| Family | Allowed `type` | What it evaluates |
|---|---|---|
| `content` | `regex`, `keywordList`, `unicodeClass` | The fragment's **text** |
| `structural` | `detector` | The fragment's **visibility attributes** (color, font size, hidden flags, position) — never its text |

- **`regex`** — a Java regex (`pattern`), matched with `Pattern.compile(pattern).matcher(text).find()`.
  Case-insensitivity is opt-in per pattern via an inline `(?i)` flag, not a separate schema field.
- **`keywordList`** — a plain `keywords` list; a fragment matches if it contains any one of them
  as a substring. Case-insensitive by default; set `"caseSensitive": true` to change that.
- **`unicodeClass`** — a `params.ranges` list of `["U+XXXX", "U+YYYY"]` code-point pairs; matches if
  the fragment contains any code point in any range.
- **`detector`** — delegates to a named, built-in Java class (see §6) via `params.detector`'s id;
  the `params` object is passed through to it untyped, so each detector defines its own knobs.

A regex rule gets a **500ms per-fragment time budget** (`RulesEngine.REGEX_TIMEOUT_MILLIS`). A
pattern that exceeds it is skipped for the rest of that document (not re-attempted per fragment)
and surfaced as a scan limitation — this exists so a pathological pattern in a hand-edited
`rules.json` can't hang a scan on adversarial input (ReDoS protection), and so a timeout is always
visible rather than silently producing an incomplete result.

### 2.2 Channels

Every rule has a `channels` list — `["*"]` for "every channel", or a subset like
`["metadata", "comment"]`. A rule only evaluates fragments whose `Channel` is in that list
(case-insensitive). The channels a parser can produce:

| Channel | What it is |
|---|---|
| `BODY` | Ordinary visible document text |
| `HEADER_FOOTER` | Page headers/footers (docx) |
| `COMMENT` | Review comments (docx), or YAML `#`/XML `<!-- -->` comments |
| `TRACKED_CHANGE` | Tracked-change insertions/deletions (docx) — a deletion is still physically in the file until accepted |
| `FOOTNOTE` | Footnote text (docx) |
| `METADATA` | Document properties/custom properties (docx, PDF) |
| `ALT_TEXT` | Image/object alt-text and title (docx) |
| `HYPERLINK_TARGET` | The URL a hyperlink run points to, not its visible label (docx) |
| `EMBEDDED_OBJECT` | Synthetic fragments the parser creates only when an embedded object's raw stream trips a structural signal (STRUCT-008A/008B below) — not one fragment per embedded object. Text content inside embedded objects is otherwise not scanned; see §4's note on embedded-object counting |

Channels matter because they're where the STRUCT-006A/006B rules (§3) and the "channel readers
rarely check" framing throughout the bundled ruleset come from — a payload sitting in alt-text or
a tracked-change deletion is invisible to a casual read-through even though it's fully present in
the file.

### 2.3 The shadow copy: evasion-resistant matching

`regex` and `keywordList` rules do not match against a fragment's raw text. They match against a
**shadow copy** produced by `TextNormalizer.shadow(...)`:

1. **NFKC Unicode normalization** — collapses compatibility characters (e.g. the fullwidth Latin
   block, `ｉｇｎｏｒｅ`) back to plain ASCII.
2. **Zero-width/bidi/tags-block characters stripped** — the same code-point ranges CONTENT-020/
   021/022 detect (zero-width spaces/joiners, bidi override/isolate controls, the Unicode tags
   block). Without this, a phrase like `i​g​n​o​r​e previous instructions` (zero-width space
   spliced between every letter) would defeat CONTENT-001 outright.
3. **HTML/XML comments stripped** (`<!-- ... -->`, replaced with a single space, not deleted —
   deleting would concatenate adjacent words with no separator and still defeat a rule's required
   whitespace gap).

Two things this deliberately does **not** do:

- It does **not** strip HTML/XML tags generically. CONTENT-005 (`<system>`/`<SYS>` fake markers),
  CONTENT-006 (`<|im_start|>`-style tokens), and CONTENT-011 (`<img src=...>` exfiltration) are
  themselves regex rules that run against this same shadow copy and rely on that literal `<...>`
  syntax as their own detection signature — stripping tags generically would remove the payload
  before those rules ever saw it.
- `unicodeClass` rules (CONTENT-020/021/022/023) and the **evidence** shown in every finding always
  use the **original, unmodified** text. The former exists specifically to detect these characters'
  presence — stripping them first would make those rules never fire. The latter exists so a human
  reviewer can see the disguise itself (via `EvidenceUtil`, which renders characters like
  `[ZWSP]`, `[BIDI]`, `[TAG]` instead of letting them render as invisible blank space).

`structural` (`detector`) rules never see the shadow copy either — they don't evaluate text at all.

## 3. The bundled ruleset

`rules.json` ships 35 seed rules: 25 `content` and 10 `structural`. IDs are stable identifiers —
referenced by the GUI, reports, and the `combos` below — not a strict numeric sequence (STRUCT-006
was split into `006A`/`006B` when its false-positive rate on long-form comments/alt-text needed a
separate, higher threshold than metadata).

### Content rules

| ID | Type | Severity | Tags | Catches |
|---|---|---|---|---|
| CONTENT-001 | regex | critical | injection, override | "ignore previous/prior/above instructions" |
| CONTENT-002 | regex | critical | injection, override | "forget/disregard everything above/before/prior" |
| CONTENT-003 | regex | high | injection, override | "new instructions:" |
| CONTENT-004 | regex | medium | injection, persona | "you are now" / "act as a/an/the" |
| CONTENT-005 | regex | critical | injection, system-marker | Fake system markers: `<system>`, `[SYS]`, `<<SYS>>`, Llama 3 header tokens, `<\|system\|>`, `### Instruction` |
| CONTENT-006 | regex | critical | injection, chatml | Raw ChatML tokens: `<\|im_start\|>`, `<\|im_end\|>`, etc. |
| CONTENT-007 | regex | high | injection, ai-addressing | "Dear AI/Assistant/Copilot/Claude/ChatGPT/GPT" |
| CONTENT-008 | regex | high | injection, ai-addressing | "If you are an AI... reading/processing/parsing this" |
| CONTENT-009 | regex | critical | injection, tool-abuse | "run/execute the following command", "modify your config", "write this to a file" |
| CONTENT-010 | regex | high | injection, tool-abuse, mcp | JSON shapes/filenames tied to MCP/tool-calling config |
| CONTENT-011 | regex | critical | exfiltration | Markdown/HTML image link with a query-string payload |
| CONTENT-012 | regex | high | exfiltration | "send/summarize/forward this/the document/data to..." |
| CONTENT-013 | regex | critical | injection, secrecy | "do not tell/mention/inform/alert the user", "respond normally but..." |
| CONTENT-014 | regex | medium | encoded | Long base64-looking run (80+ chars, tolerates line-wrapped/CRLF blobs) |
| CONTENT-015 | regex | critical | injection, propagation | "copy/include this text into any new document/output/response" |
| CONTENT-016 | regex | low | encoded | Long hex-looking run (40+ byte-pairs) |
| CONTENT-017 | regex | low | encoded | Long percent-encoded run (20+) |
| CONTENT-018 | keywordList | medium | injection, persona | Fixed phrases: "your new persona", "system prompt:", "override your instructions", etc. |
| CONTENT-019 | keywordList | low | tool-abuse, mcp | Agent config filenames: `mcp.json`, `.kiro/settings`, etc. |
| CONTENT-020 | unicodeClass | high | hidden-text, unicode-smuggling | Zero-width space/joiner/non-joiner/word-joiner |
| CONTENT-021 | unicodeClass | high | hidden-text, unicode-smuggling | Bidi override/isolate control characters |
| CONTENT-022 | unicodeClass | critical | hidden-text, unicode-smuggling | Unicode "tags" block (U+E0000-E007F) |
| CONTENT-023 | unicodeClass | medium | hidden-text, unicode-smuggling | Private-use-area code points |
| CONTENT-024 | regex | high | injection, delimiter | Forged output-boundary markers: `---END OF TEXT---`, `<\|im_end\|>`, etc. |
| CONTENT-025 | regex | high | exfiltration | Markdown link to a suspected exfiltration endpoint (`/exfil?`, `/steal?`, ...) |

### Structural rules

| ID | Detector | Severity | Channels | Catches |
|---|---|---|---|---|
| STRUCT-001 | `lowContrastText` | critical | `*` | Font/background color nearly identical (white-on-white), 8+ chars |
| STRUCT-002 | `tinyFont` | high | `*` | Font size under 2.0pt |
| STRUCT-003 | `hiddenRun` | critical | `*` | docx `w:vanish` explicit hidden flag |
| STRUCT-004 | `invisibleRenderMode` | critical | `*` | PDF text render mode 3 (neither fill nor stroke) |
| STRUCT-005 | `offPageText` | high | `*` | PDF text positioned outside the page's crop box |
| STRUCT-006A | `suspiciousChannel` | medium | `metadata` | 40+ chars of prose in document properties |
| STRUCT-006B | `suspiciousChannel` | low | `comment`, `tracked_change`, `alt_text`, `footnote` | 200+ chars of prose (threshold raised because long prose is routine in these channels) |
| STRUCT-007 | `overlappedText` | high | `*` | **Disabled by default** — see §6 |
| STRUCT-008A | `embeddedExecutableSignature` | critical | `embedded_object` | Embedded object's raw stream begins with an MZ (Windows/DOS) or ELF (Linux) executable signature |
| STRUCT-008B | `embeddedMacroStorage` | high | `embedded_object` | Embedded OLE compound-file object contains a macro-storage entry (`_VBA_PROJECT`, `VBA`, `Macros`) |

### Combos

A combo multiplies the score contribution of every finding on a fragment when that fragment's
findings collectively carry *all* of the combo's `requireTags` (see §4.2):

| ID | Requires (tags) | Multiplier | Rationale |
|---|---|---|---|
| COMBO-001 | `injection` + `hidden-text` | 1.75x | Instruction-override text that's also structurally hidden |
| COMBO-002 | `injection` + `off-page` | 1.5x | Instruction-like text positioned off the visible page |
| COMBO-003 | `exfiltration` + `hidden-text` | 2.0x | Exfiltration instructions/URLs that are also hidden |
| COMBO-004 | `injection` + `unicode-smuggling` | 1.5x | Instruction-like text interleaved with zero-width/bidi/tag characters |

## 4. Scoring

### 4.1 Weights

Each `Severity` has a default point weight, overridable per-rule via an optional `weight` field:

| Severity | Weight |
|---|---|
| info | 5 |
| low | 10 |
| medium | 20 |
| high | 30 |
| critical | 40 |

### 4.2 Per-finding contribution and combos

Each finding's raw contribution is `weight x comboMultiplier`, where `comboMultiplier` is the
product of every combo (§3) whose required tags are all present among that *specific fragment's*
findings (not the whole document) — so a combo only fires when the qualifying signals land in the
same place, not merely the same document.

### 4.3 The repeat-hit cap

Findings are then grouped by `(ruleId, channel)`. A group's total contribution is capped at
**1.5x the rule's base weight** — but never suppressed below its own strongest single instance.

This exists because pure summation over-scores volume: a 100-page legitimate manual that happens
to mention "system prompt:" (CONTENT-018, medium/20) in three unrelated chapters would otherwise
sum to 60 points — enough to cross the `suspicious` threshold (40) on repetition alone, even though
a single-page document with the identical hits is a much stronger signal. With the cap, that same
group scores 30 (`1.5 x 20`), landing in `lowRisk` instead.

The "never below the strongest single instance" part matters just as much: if one instance is
already combo-boosted past the cap (say, 40 points from a 20-point rule with a 2.0x combo), that
finding's own value is the floor — the cap only bounds what *additional* repeats add on top of it,
never a genuinely strong single signal.

Different rules, and the same rule in a different channel, are separate groups and never
cross-capped against each other.

### 4.4 The final score and verdict

The capped-per-group contributions are summed, then the whole score is capped at **100**. The
verdict is read off `verdictThresholds` (inclusive lower bounds; default shown, all
schema-overridable):

| Score | Verdict |
|---|---|
| < 15 | Clean |
| 15-39 | Low Risk |
| 40-69 | Suspicious |
| 70+ | Likely Compromised |

### 4.5 What does *not* affect the score

`limitations` — PDF's heuristic-background disclosure, a regex rule that hit its time budget and
was skipped, an embedded-object/attachment count — are carried through to the report and GUI
**unconditionally**, but never adjust the score itself. Inflating or deflating the number to
account for what wasn't checked would misrepresent what was actually checked; the honest move is
to say so plainly next to the number, not fold it into the number.

## 5. Customizing `rules.json`

### 5.1 Loading a custom ruleset

The GUI's **Rules** menu:

- **View Rules...** — opens a full in-app editor (not just a viewer): Add/Edit/Duplicate/Delete
  Rule, with Save/Save As. Edits apply to an in-memory working copy and are re-validated before
  being accepted; nothing touches a scan until you Save.
- **Load Rules File...** — loads a different `rules.json` from disk for the current session.
- **Reload Rules** — re-reads the currently-loaded file (pick up external edits).
- **Use Bundled Default Rules** — reverts to the shipped seed ruleset.
- **Validate Rules** — runs `RulesValidator` against the current in-memory ruleset on demand.

A ruleset is also validated automatically on every load. **Errors** (missing required fields, a
`family`/`type` mismatch, an invalid regex, a duplicate id, an unknown channel) block the scan from
starting and are shown as a clear GUI error. **Warnings** (an unknown detector id) don't block
anything — a `rules.json` written for a newer DocScrubber build degrades gracefully on an older one,
since a rule referencing a detector that build doesn't know about simply never matches.

### 5.2 Schema reference

Top level:

```jsonc
{
  "schemaVersion": 1,
  "severityWeights": { "info": 5, "low": 10, "medium": 20, "high": 30, "critical": 40 }, // optional, these are the defaults
  "verdictThresholds": { "lowRisk": 15, "suspicious": 40, "likelyCompromised": 70 },      // optional, these are the defaults
  "combos": [ /* see §3 */ ],
  "rules": [ /* see below */ ]
}
```

A rule (fields not mentioned below are optional with the shown default):

```jsonc
{
  "id": "CONTENT-999",           // required, unique
  "name": "Human-readable name", // required
  "family": "content",           // required: "content" | "structural"
  "type": "regex",                // required: "regex" | "keywordList" | "unicodeClass" | "detector"
  "channels": ["*"],              // default ["*"]; or a subset, e.g. ["metadata", "comment"]
  "severity": "high",             // required: info | low | medium | high | critical
  "weight": 25,                   // optional int override of severityWeights[severity]
  "description": "...",           // required, shown in the GUI rules explorer and findings table
  "remediation": "remove",        // free text hint, e.g. "remove" | "flag-only"
  "enabled": true,                // default true
  "tags": ["injection"],          // default []; combos and findings key off these
  "caseSensitive": false,         // keywordList only, default false

  // exactly one of the following, matching "type":
  "pattern": "(?i)example",                                  // regex
  "keywords": ["exact phrase one", "exact phrase two"],       // keywordList
  "params": { "ranges": [["U+200B", "U+200D"]] },             // unicodeClass
  "detector": "lowContrastText", "params": { "maxPt": 2.0 }   // detector (params shape is detector-specific, see §6)
}
```

A combo:

```jsonc
{
  "id": "COMBO-999",
  "description": "...",
  "requireTags": ["injection", "hidden-text"], // all must be present among one fragment's findings
  "sameFragment": true,                         // default true; the only supported value today
  "multiplier": 1.75
}
```

### 5.3 Tuning tips

- Start from the bundled `rules.json` (`src/main/resources/rules/rules.json`) and edit a copy —
  `Load Rules File...` never overwrites the bundled default.
- A broad `regex`/`keywordList` rule that's noisy on your corpus is usually better scored `medium`
  or lower and left in place (so it still contributes to combos) than deleted outright — see how
  CONTENT-004 and CONTENT-018 are deliberately scored below `critical` for exactly this reason.
- If you add a new signal that should compound with an existing one only when both are genuinely
  present together, add a `tags` entry and a `combos` entry rather than trying to encode the
  conjunction into a single regex.

## 6. Extending detectors

`structural` rules can only reference a `detector` id that's registered in code —
`DetectorRegistry`'s constructor is the complete list (`lowContrastText`, `tinyFont`, `hiddenRun`,
`invisibleRenderMode`, `offPageText`, `suspiciousChannel`, `overlappedText`,
`embeddedExecutableSignature`, `embeddedMacroStorage`). Adding a new one means
implementing the two-method `Detector` interface:

```java
public interface Detector {
    String id();
    boolean evaluate(TextFragment fragment, Map<String, Object> params);
}
```

and registering an instance in `DetectorRegistry`. `params` is the rule's own JSON `params` object,
untyped — read values out of it with the small helpers in `ParamUtil` (`getDouble`/`getInt` with a
default). Once registered, any `rules.json` can reference the new id from a `structural` rule
without further code changes — thresholds and enablement are data from then on; only the check
itself required code.

`STRUCT-007` (`overlappedText`) is a real example of a detector that's registered but intentionally
always returns `false`: reliable paint-order overlap detection (text hidden beneath a later-painted
image or shape) risks flagging watermarks and decorative images as "hidden text," which conflicts
with this project's no-false-positive priority. It ships as a real, documented, disabled rule
(`"enabled": false`) rather than a silently-missing detector id, specifically so referencing it
never trips the "unknown detector" validation warning and the gap stays visible rather than quietly
absent.

`STRUCT-008A`/`008B` (`embeddedExecutableSignature`/`embeddedMacroStorage`) follow the same
"parser populates, detector reads `VisibilityAttributes`" shape as every other structural detector
— they never touch a fragment's text. `PdfParser`/`DocxParser` hand each embedded object's raw
bytes to `EmbeddedStreamInspector`, a bounded, best-effort check (magic-byte signature match, plus
an OLE2 compound-file directory listing capped at 20MB) that never parses further or executes
anything. It only produces a fragment (`Channel.EMBEDDED_OBJECT`) when it finds something —
document text inside embedded objects otherwise remains unscanned, per `embeddedObjectCount` and
the parsers' limitations notices.
