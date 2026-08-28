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

**It is the PLUGIN's dependency, and the host resolving a copy of its own is not the disaster this
paragraph used to claim (corrected 2026-08-28).** What stood here was *"`botmaker-studio` must not list it.
Two plugins are entitled to two toolkit versions on two classloaders, and the moment Studio resolves one,
they are not."* The second sentence is **false**, and it is worth knowing why before anybody restores it:
`PluginLoader` is parent-first only for `com.botmaker.plugin.api.**` and the platform namespaces, so the
toolkit is resolved **child-first** — a plugin carrying its own copy still gets its own, and the host's is
the fallback for a plugin that brings none.

The rule mattered anyway, and the platform broke it against itself: Studio's plugin #1 is the SDK, whose
`SdkPlugin` extends `AbstractStudioPlugin` through a dependency the SDK declares `optional` — so not
transitive, so Studio's classpath had no toolkit at all, so `ServiceLoader` threw `NoClassDefFoundError`
while constructing the only plugin Studio ships and **Studio ran with an empty palette**. Studio now carries
the toolkit at **`runtime`** scope. The rule that replaced the old one: *whoever puts a plugin on a
classpath supplies what that plugin needs.*

What survives of it, and is enforced: **no Studio source may name a `com.botmaker.plugin.toolkit` type.**
The `runtime` scope means javac cannot see one, and `StudioSourcesTest` refuses a widening to `compile`.

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
| `Source` (2026-08-28) | Java source: a string literal, a char, a number, a constructor, a static call | a plugin emits Java whether it means to or not — `ValueCodec.literal` and every slot write — and this project already had three hand-rolled escapers, each of which stopped at the backslash and the quote |

`Editors` gained `boundedPill` (a number with a range, in a dialog, committed on OK) and `flag` out of the
same lift. **What did not move is the `Bound` table** naming `setDefaultConfidence` and what its range is —
that is the SDK's knowledge about its own API, and it is the worked example of rule 4 below.

**One thing that looks liftable and is not: the SDK's own `SdkValueTypes` still uses its private
`codec(…)`/`seeded(…)` helpers rather than `Codecs`, and `LiteralWriter` keeps its own escaping rather than
`Source`.** Not an oversight, and the reason **changed on 2026-08-28** without the conclusion changing. It
used to be that Studio carried no toolkit, so a toolkit class named from `internal/authoring` — which
`Authoring` reaches on Studio's *own* classpath — was a `NoClassDefFoundError` on the first project
generation. Studio carries the toolkit at `runtime` now, so that crash is gone and the rule stands on
stronger ground: **the SDK is a library *and* a plugin, and only its plugin half (`plugin/`,
`internal/plugin/`) may name us.** A library half that reached for a plugin's widget kit would be unusable
in every host that does not happen to bundle one — which is every host but Studio.

So the ~15 lines `Source` and `LiteralWriter` have in common are **deliberate duplication**, and the SDK's
copy carries a comment saying so.

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

## The one dependency, and why the bar was met

`botmaker-studio-api` and `javafx-controls` are `provided` — a plugin has both already. **The only
dependency a plugin actually resolves through this module is JavaPoet**
(`com.palantir.javapoet:javapoet`, one 106 KB jar with none of its own), added 2026-08-28 for `Source`.

The bar it had to clear is the one this section has always stated: *it becomes every plugin's dependency,
and its compatibility becomes ours.* What cleared it is that **a plugin writes Java whether it means to or
not** — `ValueCodec.literal` returns Java source and `Slots.write` writes an expression into a bot's file —
so the choice was never "a dependency or nothing", it was "one implementation or a hand-rolled escaper per
plugin". This project had already written three of those (here, in the SDK, and in the generated skeleton),
and each of them escaped the backslash and the quote and stopped, so a pasted tab produced a slot that would
not compile.

**`Source.string` is the one member JavaPoet does not implement**, and that is pinned by a test rather than
left to be rediscovered: `$S` splits a string containing a newline into a concatenation *across source
lines*, which is right for a generated file and wrong for a slot, where the host writes the result into the
middle of an existing line. The structural members (`newInstance`, `call`, `type`) are JavaPoet's, and
**no JavaPoet type appears in a signature here** — so a plugin's own compile is unaffected by which library
is behind it, and the library can be replaced without breaking anybody.

ControlsFX was considered and declined on the same bar and did not clear it: `PropertySheet` is a whole-form
abstraction and these are bespoke single-value nodes, so it would sit unused beside them while every plugin
that wanted a slider resolved it. If the toolbar or panel work later wants `PopOver` specifically, take the
dependency **then**, with the need in hand.

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
mvn test        # ValuesTest, CallSitesTest, SourceTest (29) — what is assertable with no JavaFX toolkit
mvn install     # com.github.LiQiyeDev:botmaker-plugin-toolkit:0.0.0-SNAPSHOT
```

Do not add a test that asserts a builder returned non-null; a compile proves that. `SourceTest` is the
other kind worth having: its output is compiled by **somebody else's** build, so a wrong escape is a
compile error in a bot, reported against a line its author never wrote. What is worth holding is
in `ValuesTest`: every case there is a real state a project file reaches, and in every one the answer is a
default rather than an exception.

Published through JitPack, which serves each git tag under `com.github.LiQiyeDev` regardless of this pom's
`groupId`/`version`. **The maintainer owns the publish** — releases are cut from the umbrella with
`../release.sh --plugin-toolkit <version>`.
