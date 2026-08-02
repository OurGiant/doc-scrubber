---
name: verify
description: How to build, launch, and drive DocScrubber to verify a Swing UI change actually works on this dev setup. Use before trusting the generic verify-java-swing skill's screenshot step; read that skill too for the underlying techniques (modal-dialog deadlock, synthetic MouseEvent dispatch, process safety).
---

# Verifying DocScrubber

This is the project-specific companion to the generic `verify-java-swing`
skill (techniques) and `java-swing-project-setup` (build/structure
standard this project follows). Read those first — this file is what to
actually type for *this* project.

## Build here, run there

Maven only exists in the Docker container, not on the host:

```bash
docker exec festive_bardeen bash -c "cd /projects/doc-scrubber && mvn -q package -DskipTests"
```

If `festive_bardeen` doesn't respond, find the current container:
`docker ps -a --format '{{.Names}} {{.Status}} {{.Image}}'` and
`docker start <name>` if stopped — the name can drift across sessions.

`/projects` is bind-mounted from the host's `~/projects`, so the jar lands
at `target/doc-scrubber-all.jar`, visible on the host. The container is
headless (no `DISPLAY`) — run the jar on the **host**, not inside the
container, or it dies at `JFrame` construction with `HeadlessException`.

```bash
java -jar target/doc-scrubber-all.jar
```

Main class: `com.ourgiant.docscrubber.Main`.

## Screenshots: Robot actually works here — confirmed, don't assume the sibling dead end

Unlike `aws-idp-saml-ui` and `kiro-control-panel` (where `Robot.createScreenCapture`
reliably returns solid black on their Wayland/COSMIC sessions), **this
project's dev host has a real, working X11 display (`:1`)** and
`Robot.createScreenCapture(...)` returns a genuine, non-black screenshot.
Confirmed by actually sampling pixel values (average sampled RGB well
above 0), not just eyeballing the PNG. Try it first here — don't
preemptively skip to the reflection-based fallback just because sibling
projects needed it.

## Component.paint() offscreen capture: fast, but can show a stale-clip artifact — cross-check with Robot before trusting it

Rendering a top-level window offscreen via
`component.paint(graphics2D)` into a `BufferedImage` (no OS screen
capture involved at all) is a good fast/deterministic alternative to
`Robot` when you want to inspect a component tree without a display, or
in a loop. But for a window containing a `JSplitPane` whose divider
location was requested smaller than a child's minimum size (so Swing
grows it after the fact), this technique was observed to paint using a
**stale clip region from an earlier layout pass** — a real button
(confirmed present and correctly bounded via reflection:
`component.getBounds()`) was completely absent from the rendered pixels,
even after an explicit `validate()` and a 1.5s settle. A real `Robot`
capture of the same live window immediately after showed the button
rendered correctly. If `component.paint()` output looks wrong in a way
that contradicts reflected component state, don't trust it — reach for
`Robot` (now confirmed working here) as the tiebreaker before concluding
there's an app bug.

## Nothing else confirmed yet

No other project-specific gotchas (first-run state location, custom
dialog sizing quirks, etc.) have been found and confirmed here yet. Add
them to this file as they turn up, the way `kiro-control-panel`'s
`verify` skill records its `JEditorPane` sizing gotcha and
`aws-idp-saml-ui`'s records its bind-mount staleness and `-Duser.home`
isolation notes.
