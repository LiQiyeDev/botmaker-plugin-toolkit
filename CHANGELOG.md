# Changelog

All notable changes to `botmaker-plugin-toolkit`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [Unreleased]

### Added

- **The module** — the eighth BotMaker repository, and the second one a plugin compiles against. It holds
  what `botmaker-studio-api` deliberately cannot: implementation. The contract is interfaces and records and
  must be allowed to version slowly, because a plugin's compiled classes cannot be rewritten; a widget kit is
  nothing but implementation and will move every time an editor does. Keeping them apart is what lets a
  plugin take a new toolkit without taking a new contract.
- **`Slots`** — reads and writes a value that is a Java expression in a bot's source and a row of stored
  strings in the Parameters window, so one editor serves both. Lifted out of the SDK, where it had been by
  accident of who wrote the first editor.
- **`CallSites`** — predicates for an editor chosen by the *call* around a value rather than by its type,
  which is the only way to tell a Steam app id from a window title when both are `String`. Four shapes:
  `firstArgumentOf`, `argumentOf`, `firstArgumentWhere`, `trailingArgumentOf`. Every one declines a
  Parameters row, because a row has no call behind it.
- **`Codecs`** — `ValueCodec`s from three lambdas, with `or` to make a partial parser total and `seeded` for
  a type whose starting value is a choice rather than a fallback.
- **`Source`** — Java source, spelled correctly: a string literal, a char literal, a number as a person
  would have typed it, a constructor, a static call and a qualified type name. A plugin emits Java whether
  it means to or not — a `ValueCodec`'s third function returns the literal a bot compiles, and every slot
  write is an expression — and this project had already written **three** hand-rolled escapers, each of
  which escaped the backslash and the quote and stopped, so a pasted tab produced a slot that would not
  compile. `Slots.quote` is now this, under its old name.
  - **The first dependency this module resolves onto a plugin's classpath**: `com.palantir.javapoet:javapoet`
    (the maintained fork; the original has had no release since 2021), one 106 KB jar with none of its own.
    Taken deliberately against the standing rule that a dependency here becomes every plugin's — the choice
    was never "a dependency or nothing", it was "one implementation or one hand-rolled escaper per plugin".
  - **No JavaPoet type appears in a signature**, so the library is replaceable without breaking a plugin.
  - **`Source.string` is the one member JavaPoet does not implement, and a test pins why**: `$S` splits a
    string containing a newline into a concatenation *across source lines*, which is right for a generated
    file and wrong for a slot — the host writes the result into the middle of an existing line, and one slot
    holds one expression.
- **`AbstractStudioPlugin`** — a `StudioPlugin` that builds each of its four contributions once, on first
  use, so nothing expensive runs in the constructor `ServiceLoader` calls while a project is opening.
- **`testing.TestContexts`** — a recording `SlotContext`/`ValueContext` for unit-testing an editor and its
  predicate with no host, no project and no JavaFX thread.
- **`Editors`** — eleven whole editors, each built from a `ValueContext` and needing nothing else:
  `region` (drag a rectangle on screen), `numbers` (a labelled tuple), `tuplePill` (the same tuple as a
  constructor in a bot's source, with a screen picker), `bounded` (a slider and read-out), `text`,
  `textSlot` (the same, writing Java when the value is a slot), `program` (browse for an executable, or type
  a command), `choice`, `gallery` (a grid of pictures), `boundedPill` (a number with a range, edited in a
  dialog and committed on OK) and `flag`. Extracted from the host's own thirteen pickers rather than
  designed, which is why there are eleven: these are the shapes that recurred.
- **`Editors.tuplePill` + `TupleSpec` + `Pick`** — one editor where the SDK had three. A `TupleSpec` says
  which class the slot constructs, what its numbers are called, how a person reads them back, and whether
  they can come off the screen (`REGION` drags, `POINT` clicks under a magnifier, `MEASURE` drags and throws
  the origin away). The formatter stays with whoever owns the type: *a small tuple of pixels* is a shape,
  *a `Rect` is an origin plus a size* is not. `Editors.tupleLabel` is public alongside it because the label
  is the one half assertable with no JavaFX toolkit, and the half worth asserting — it is read back out of
  what the last pick wrote.
- **`Slots.holdsNumbers`** — whether a value is coordinates at all, rather than a variable or a call that
  happens to read as zeroes. Without it a slot holding `bounds` labels itself `0, 0  0×0`, which claims a
  value the user never set.
- **`Pills`** — the summary-plus-menu control nine of those thirteen are. Its menu is rebuilt on opening,
  because an editor's choices depend on state that moves.
- **`Fields`** — a text field committing on Enter **and** on focus loss (either alone loses edits), a
  spinner clamped to its range, a slider with a read-out for the numbers whose scale nobody knows, and
  `duration`: a length of time as one box per unit. The control that replaced was one amount plus a unit
  dropdown, which could only ever say a multiple of a single unit — four and a half minutes had to be entered
  as 270 seconds. It hands back a millisecond total and draws **no preview label**, because spelling a total
  back out is the wire format owner's job and a preview that disagreed with what was stored would be worse
  than none.
- **`Pills.button`** — a pill with no menu, for a value with genuinely one thing to do to it. A menu of one
  entry makes the user click twice to reach the only destination there was.
- **`Modals`** — themed through `Theme` and owned through `Dialogs.owner()`. A plugin must not build its own
  `Stage`: with no theme it is unstyled JavaFX in the middle of a themed application, and with no owner it
  falls behind the editor. Cancelling calls nothing. Five entry points: `form` (any body, with OK/Cancel —
  the general case, so an editor whose modal is a slider or a grid with a search box has somewhere to go),
  `numbers`, `chooser`, `gallery` and `program`.
- **`Modals.gallery`** — a searchable grid whose contents are **listed off the JavaFX thread**, with an
  optional row for typing an answer the grid does not contain. All three are traps a library browser hits
  exactly once each: scanning on the FX thread freezes the editor mid-draw for a time proportional to how
  much the user owns; a grid is a bad way to choose among two hundred things; and the right answer is often
  not installed on the machine the bot is being written on.
- **`Modals.program`** — the native "choose a program" dialog **run off the calling thread**, falling back to
  JavaFX's own chooser where there is no native one. A native dialog blocks its thread until answered, so
  opening one on the FX thread freezes the application behind a window the user cannot see is theirs — and it
  happens to work on the platform with no native tool, which is the worst way for that bug to be distributed.
- **`Values`** — reading the `List<String>` a value arrives as. Every method degrades and never throws,
  because an editor that throws while building leaves a row of the Parameters window empty.
- **`Styles`** — the host's style-class names as constants. The smallest class here and the one a plugin
  cannot do without: the stylesheet is inside the host's jar, so there is no other way to learn the strings.
- **`Thumbnail`** — one cell of a picture chooser; a missing image is an ordinary state and renders as the
  label alone.

### Deliberately absent

- **Any third-party UI dependency.** ControlsFX's `PropertySheet` is a whole-form abstraction and these are
  bespoke single-value nodes; it would sit unused while every plugin resolved it. To be revisited only with
  a concrete need.
- **A form or validation layer.** A seventh widget is worth adding the day a second editor wants it.
