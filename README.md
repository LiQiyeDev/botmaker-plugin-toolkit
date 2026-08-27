# botmaker-plugin-toolkit

The widgets a **BotMaker Studio plugin** would otherwise hand-roll before it could write its first slot
editor.

`botmaker-studio-api` says *what* a plugin contributes. This says *what it looks like* — and, more usefully,
it already knows the things about the host that a plugin author has no way to discover: which style classes
the host's stylesheet actually names, that a dialog must be themed and owned or it appears behind the editor
as unstyled JavaFX, and that a text field which commits only on Enter silently loses most edits.

## Using it

```xml
<dependency>
    <groupId>com.github.LiQiyeDev</groupId>
    <artifactId>botmaker-plugin-toolkit</artifactId>
    <version>v0.1.0</version>
</dependency>
```

Ordinary `compile` scope — it is resolved onto your plugin's own classloader, so your toolkit version is
yours and no other plugin's. `botmaker-studio-api` and `javafx-controls` are `provided` here; you already
have both.

Then an editor is a predicate and a method reference:

```java
@Override
public List<SlotEditor> slotEditors() {
    return List.of(
            SlotEditor.of(c -> c.type().is(Rect.class), Editors::region),
            SlotEditor.of(c -> c.type().is(Point.class), c -> Editors.numbers(c, "Point", "x", "y")),
            SlotEditor.of(c -> c.type().is(Duration.class), c -> Editors.bounded(c, 0, 30_000, 100)));
}
```

The same editor serves a slot in a bot's source **and** a row of the Parameters window, because both arrive
as a `ValueContext`.

## What is in it

| | |
|---|---|
| `Editors` | whole editors — `region`, `numbers`, `bounded`, `text`, `choice`, `gallery`. Take a `ValueContext`, need nothing else. |
| `Pills` | the summary-plus-menu control nine of the host's own thirteen pickers are. |
| `Fields` | a text field that commits on Enter *and* on blur; a clamped spinner; a slider with a read-out. |
| `Modals` | a themed, correctly-owned numbers dialog and thumbnail chooser. |
| `Values` | reading the `List<String>` a value arrives as. Degrades, never throws. |
| `Styles` | the host's style-class names, as constants. |
| `Thumbnail` | one cell of a picture chooser. |

## What is deliberately not in it

- **No third-party UI dependency.** Nothing transitive reaches your plugin.
- **No form or validation layer.** These six were extracted from the host's own pickers because they
  recurred; a seventh is worth adding the day a second editor wants it, not before.
- **No annotation processing.** That is `botmaker-plugin-processor`, which depends on nothing at all and
  goes on `<annotationProcessorPaths>` rather than in `<dependencies>`.

## Building

```bash
mvn test        # ValuesTest (7)
mvn install     # com.github.LiQiyeDev:botmaker-plugin-toolkit:0.0.0-SNAPSHOT
```

Published through JitPack, which serves each git tag under `com.github.LiQiyeDev` regardless of this pom's
`groupId`/`version`. Releases are cut from the umbrella with `../release.sh --plugin-toolkit <version>`.
