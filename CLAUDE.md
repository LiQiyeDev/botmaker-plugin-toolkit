# CLAUDE.md

Guidance for working in **botmaker-plugin-toolkit**, the widget kit a BotMaker Studio plugin compiles
against.

Read the umbrella `../CLAUDE.md` first, `../botmaker-studio-api/CLAUDE.md` for the contract this sits on,
and `../docs/refactor/24-plugin-platform.md` for why either exists.

## What this module is, and what it is not

Implementation — which is exactly what `botmaker-studio-api` may not contain. That split is the whole
reason there are two modules:

- **The contract must version slowly.** A plugin's compiled `.class` files cannot be rewritten by anybody,
  so a change there that an already-built plugin cannot survive is a Studio-major decision.
- **A widget kit will not.** It moves every time an editor moves.

A plugin takes a new toolkit without taking a new contract. That is the property to protect; if a change
here would force the contract to move with it, the change is in the wrong module.

**It is the PLUGIN's dependency, never the host's.** `botmaker-studio` must not list it. Two plugins are
entitled to two toolkit versions on two classloaders, and the moment Studio resolves one, they are not.

## What is in here, after the 2026-08-28 lift

Seven widget classes (`Editors`, `Pills`, `Fields`, `Modals`, `Values`, `Styles`, `Thumbnail`) and, since
the lift out of the SDK, five more that are not widgets at all:

| class | what it is | why it is here and not in a plugin |
|---|---|---|
| `Slots` | reads and writes a value that is Java source in a slot and stored text in a Parameters row | not a line of it named an SDK type; **every** editor that can sit in source needs it |
| `CallSites` | the four matcher shapes for an editor chosen by the call around a value, not by its type | the matching is generic; the class and method **names** are the plugin's and stay there |
| `Codecs` | `ValueCodec`s from lambdas, plus `or` (total) and `seeded` | three one-line answers should not cost eleven lines of anonymous class per type |
| `AbstractStudioPlugin` | the four contributions, each built once on first use | the build hooks **cannot be fields**: `ServiceLoader` constructs a plugin while a project is opening |
| `testing.TestContexts` | a recording `SlotContext`/`ValueContext` | a plugin author could not unit-test an editor without writing this first, so the predicate half went untested |

`Editors` gained `boundedPill` (a number with a range, in a dialog, committed on OK) and `flag` out of the
same lift. **What did not move is the `Bound` table** naming `setDefaultConfidence` and what its range is —
that is the SDK's knowledge about its own API, and it is the worked example of rule 4 below.

**One thing that looks liftable and is not: the SDK's own `SdkValueTypes` still uses its private
`codec(…)`/`seeded(…)` helpers rather than `Codecs`.** Not an oversight. `Authoring` reaches
`SdkValueTypes.CATALOG` on Studio's *own* classpath — Studio depends on the SDK and **must not** depend on
this module — so a toolkit class named from there would be a `NoClassDefFoundError` the first time anyone
generated a project. The toolkit is a plugin's dependency; the SDK is a library *and* a plugin, and only its
plugin half may name us.

## The three rules

**1. A widget takes a `ValueContext` and gets everything else from it.** Not a `CodeEditorService`, not a
`ProjectConfig`, not a `Stage` the caller found somewhere. `ctx.services()` is theming, capture, dialogs and
the project's paths, and it is the only door. A widget that needs something not on `StudioServices` is
telling you the *contract* is missing something — say so, do not route around it.

**2. Nothing throws while building a node.** A value is a `List<String>` that a user may have typed anything
into, or that a newer version of the plugin wrote. `Values` degrades in every case, on purpose. An editor
that throws in its constructor leaves a row of the Parameters window with no widget in it and no explanation
— which reads as the host being broken.

**3. Building an editor never writes.** Not even to normalise what is already there. A project opened and
closed must come back byte-identical, and a widget that "tidies" a value on render rewrites every bot the
user merely looked at.

**4. Nothing here may name a plugin's vocabulary.** Not in a signature, not in a name, not in a javadoc
sentence. If a member has to say "Steam", "duration", "capture source" or "confidence", it belongs to the
plugin that owns that word. `Fields.duration` is the worked precedent — it returns a `long` and draws no
preview label, because spelling a total back out is the wire format owner's job, and `WireText.spellDuration`
stayed in the SDK. `Editors.NumberRange` passes because a *bounded number* is a shape; the table saying
`setDefaultConfidence` runs 0 to 1 does not, and stayed.

This is the acceptance test for every lift out of a plugin, and it is what stops this module becoming the
SDK's second home. A widget that is generic only because its one caller happens to be generic is not generic.

## Why there is no dependency here

`botmaker-studio-api` and `javafx-controls`, both `provided`, and nothing else. ControlsFX was considered
and declined: `PropertySheet` is a whole-form abstraction and these are bespoke single-value nodes, so it
would sit unused beside them while every plugin that wanted a slider resolved it. If the toolbar or panel
work later wants `PopOver` specifically, take the dependency **then**, with the need in hand — and know that
it becomes every plugin's dependency, and its JavaFX compatibility becomes ours.

## Why it flattens and the other two plugin-facing modules do not

`botmaker-studio-api` and `botmaker-plugin-processor` run no `flatten-maven-plugin` and carry no
`.deps.env`, because both exist to bake a `-D`-injected `${botmaker.*.version}` into a published pom and
neither module pins a BotMaker upstream. **This one does** — the contract, whose `ValueContext` every widget
takes. So it gets both, exactly like `botmaker-session` and `botmaker-sdk`, and for the reason their headers
give: Maven publishes the *committed* pom, not the effective one, so without flatten a `-D` changes what
this build resolves and nothing about what a plugin resolving this toolkit from JitPack sees. That is the
shape of the `0.0.0-SNAPSHOT` bug that shipped in every SDK up to v1.0.24.

`flattenMode=oss` with `<repositories>keep</repositories>`: `oss` strips the jitpack repository declaration
otherwise, and a consumer resolving a `com.github.LiQiyeDev` artifact needs it.

## Style

`../docs/refactor/00-conventions.md` applies, and so does the contract's own rule that **Javadoc is the
deliverable**: a plugin author has this module's Javadoc and nothing else. Every widget's doc says which
mistake it exists to prevent — that a bare `TextField` loses edits made by clicking away, that an unowned
`Stage` falls behind the editor — because those are the things the author cannot find out any other way.

## Building

```bash
mvn test        # ValuesTest (7) — the only behaviour assertable without a JavaFX toolkit
mvn install     # com.github.LiQiyeDev:botmaker-plugin-toolkit:0.0.0-SNAPSHOT
```

Do not add a test that asserts a builder returned non-null; a compile proves that. What is worth holding is
in `ValuesTest`: every case there is a real state a project file reaches, and in every one the answer is a
default rather than an exception.

Published through JitPack, which serves each git tag under `com.github.LiQiyeDev` regardless of this pom's
`groupId`/`version`. **The maintainer owns the publish** — releases are cut from the umbrella with
`../release.sh --plugin-toolkit <version>`.
