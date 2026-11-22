# AGENTS.md

Guidance for AI coding agents (and humans) working on this repository.

## Project

ti5x is an Android emulator of the Texas Instruments TI-58/58C/59 programmable
calculators (1977). It aims for 100% authenticity, including undocumented
features such as the HIR registers and `Dsz nn 51` (decrement without jump).
It also adds a few extensions: more program steps, card/bank files, data
import/export, and `Op 99` self-test.

- Language: Java. Android `minSdk 23`, `targetSdk 36`, `namespace net.obry.ti5x`.
- License: GPLv3 (see `COPYING`). Every new source file gets the same GPL header
  as the existing ones.

## Repository layout

| Path | Content |
|---|---|
| `app/src/main/java/net/obry/ti5x/` | Android app sources (engine and UI are currently mixed, see "Architecture") |
| `engine/` | Pure-Java calculator engine (`net.obry.ti5x.engine`), being extracted from `app/` |
| `cli/` | Command-line front-end (`net.obry.ti5x.cli.Main`) running programs on the console |
| `app/src/main/res/`, `app/src/main/assets/` | Android resources, help HTML, fonts |
| `app/src/main/res/raw/*.ti5l`, `*.ti5p` | **Generated** at build time from `modules/` and `programs/` (git-ignored) |
| `app/preprocess.gradle` | `preprocess` task (hooked on `preBuild`) that calls `util/libmake` and `util/makeprog` |
| `modules/<xx>-<name>/` | ROM library module sources: `progNN.src` (program source), `cardNN.xml` (label card), `helpNN.html`, `help.html` |
| `programs/<name>/` | Built-in programs: `prog`, `card`, `help`, and optional `banksrc`/`mem` files |
| `examples/` | Example programs, built with `make -C examples` |
| `util/` | Python 3 tools: `assemble`, `disassemble`, `makeprog`, `libmake`, `rendercard` (needs GhostScript), `check-assemble-disassemble`, … |
| `tests/` | Old stand-alone tests for `Number` (`make -C tests`) |
| `file_format.txt` | Format of `.ti5p` (program), `.ti5l` (library), and saved state |

## Building

```sh
./gradlew assembleDebug        # build the APK (runs the preprocess task first)
./gradlew :engine:test         # engine unit tests (JUnit 5)
./gradlew :cli:installDist     # CLI, then run cli/build/install/ti5x/bin/ti5x
./gradlew clean
make -C examples               # build examples/*.ti5p
util/check-assemble-disassemble  # assembler/disassembler round trip
```

Requirements: a full JDK 17 or 21 (it must include `javac`; a JRE-only
JDK fails with "does not provide the required capabilities: [JAVA_COMPILER]",
so set `JAVA_HOME`, e.g. `/usr/lib/jvm/java-21-openjdk-amd64`), the Android SDK (`local.properties` → `sdk.dir`), `python3`
on the `PATH`, and GhostScript for card rendering. `keystore.properties` is needed
for signing (see `INSTALL`). Never commit `local.properties`, `keystore.properties`,
or generated `*.ti5*` files.

## Architecture

The main classes:

- `State.java`: the whole calculator (X/T registers, the op stack, memories,
  flags, program banks, return stack, the `Interpret` loop, and the
  program/label/register listers). It runs programs on an `android.os.Handler`
  (`TaskRunner`, `ContinueTaskRunner`, `DelayedStep`).
- `Number.java`: TI-59 number arithmetic with 13-digit precision and rounding.
- `ButtonGrid.java`: draws the keypad and holds the key-entry state machine
  (`Invoke`: 2nd/Inv, operand collection, learn mode via `State.StoreInstr`).
- `Display.java`: the LED display view (`SetShowing`, `SetShowingError`, `SetShowingRunning`).
- `Persistent.java`: ZIP + XML load/save of programs, libraries, bank files, and saved state.
- `Printer.java` / `PrinterActivity.java`: PC-100C printer emulation.
- `Importer.java` / `Exporter.java`: data import/export (`Op 96`/`Op 97` extensions).
- `Disassembler.java`: opcodes → source text. `util/assemble` does the reverse.
- `Tester.java`: built-in regression suite. Running `Op 99` on the calculator
  must display **43** (the number of tests passed). A negative value `-n` means test `n` failed.
- `Global.java`: static singletons (`Calc`, `Disp`, `Buttons`, `Print`, `Label`, …) shared across classes.

A refactoring is planned to move the engine (State, Number, keypad logic,
file formats, assembler) into a pure-Java `:engine` Gradle module, with a
`:cli` module and the Android `:app` depending on it. When working toward that:

- Engine code must not import `android.*`, `androidx.*`, or `R`.
- Engine code may only use Java APIs available on Android API 23.
- The engine reaches the UI through small host interfaces (display, printer,
  scheduler, file store, …), never through `Global`.

## Domain notes

- Program memory holds opcodes 00–99, one per step. `MaxProgramSteps` is 1500
  (the original had 960). Bank 0 is the user program. Banks 01..99 are the
  programs of the loaded library module, selected with `2nd Pgm nn`.
- Opcodes are "merged codes": key row/column (11..95). A 2nd function is the
  key code with 5 added to the column modulo 10 (`ln x` = 23, `log` = 28). Some
  instructions are merged with `Ind` (e.g. `RC*` = 73).
- Number display, rounding, and error state (flashing `9.9999999 99`) must
  match the real calculator. Check behaviour against the TI-59 manuals before
  "fixing" something that looks odd.
- File formats must stay compatible: existing user `.ti5p`/`.ti5b` files and
  the saved state (`state.ti5s`) must still load after any change.
- Use `Global.StdLocale` (US) for all number/string formatting, never the default locale.

## Code style

Follow the existing style:

- 2-space indentation, braces on the same line.
- PascalCase for methods and most fields (`SetShowing`, `CurBank`, `InvState`).
- Multi-line parameter lists with the parenthesis on its own line, as in the existing code:
  ```java
  void SetShowing
     (
        String ToShow
     ) {
  ```
- Comments in `/* ... */` or `//` explaining the TI-59 behaviour being emulated.

## Testing

- Calculator behaviour: run `Op 99` and check that the display shows `43`.
  When fixing a calculator bug, add a test case to `Tester.java` (and update the expected count everywhere it is mentioned).
- Assembler/disassembler changes: run `util/check-assemble-disassemble`.
- UI changes: test on a device/emulator (portrait and landscape, phone and tablet layouts).

## Git

- Commit messages: a short sentence ending with a period
  (e.g. `Fix 2nd List to list the currently loaded program.`), with an optional body.
- Work on a branch and target `master`.
