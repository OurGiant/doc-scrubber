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

## Screenshots: assume the sibling projects' dead end applies, but confirm first

`aws-idp-saml-ui` and `kiro-control-panel` both confirmed
`java.awt.Robot.createScreenCapture(...)` returns solid black on this
host's Wayland/COSMIC session. This project hasn't independently
confirmed that yet — same host makes it likely, not certain. Try it once;
if it's black, stop debugging it and go straight to the generic skill's
reflection-based fallback (`getText()`/component state, `doClick()`,
synthetic `MouseEvent.dispatchEvent`) rather than re-exploring the dead
ends already logged in `kiro-control-panel`'s `verify` skill (cosmic-screenshot
workspace mismatch, missing xdotool/wmctrl, no D-Bus workspace-switch API,
no AT-SPI bridge).

## Nothing else confirmed yet

This project is new — no project-specific gotchas (first-run state
location, custom dialog sizing quirks, etc.) have been found and
confirmed here yet. Add them to this file as they turn up, the way
`kiro-control-panel`'s `verify` skill records its `JEditorPane` sizing
gotcha and `aws-idp-saml-ui`'s records its bind-mount staleness and
`-Duser.home` isolation notes.
