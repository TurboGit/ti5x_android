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
| `app/src/main/java/net/obry/ti5x/` | Android app sources: the user interface on top of the engine (see "Architecture") |
| `engine/` | Pure-Java calculator engine (`net.obry.ti5x.engine`) used by the app and the CLI |
| `cli/` | Command-line front-end (`net.obry.ti5x.cli`) running programs on the console |
| `app/src/main/res/`, `app/src/main/assets/` | Android resources, help HTML, fonts |
| `app/src/main/res/raw/*.ti5l`, `*.ti5p` | **Generated** at build time from `modules/` and `programs/` (git-ignored) |
| `app/preprocess.gradle` | `preprocess` task (hooked on `preBuild`) that calls `util/libmake` and `util/makeprog` |
| `modules/<xx>-<name>/` | ROM library module sources: `progNN.src` (program source), `cardNN.xml` (label card), `helpNN.html`, `help.html` |
| `programs/<name>/` | Built-in programs: `prog`, `card`, `help`, and optional `banksrc`/`mem` files |
| `examples/` | Example programs, built with `make -C examples` |
| `util/` | Python 3 tools: `assemble`, `disassemble`, `makeprog`, `libmake`, `rendercard` (needs GhostScript), `check-assemble-disassemble`, … |
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

The calculator engine lives in `engine/` (package `net.obry.ti5x.engine`,
pure Java). The Android app in `app/` (package `net.obry.ti5x`) holds the UI
and uses the engine.

Engine (`engine/src/main/java/net/obry/ti5x/engine/`):

- `Calculator.java`: creates and connects the engine parts and the host
  services. The engine parts reach each other and the host only through it.
- `State.java`: the whole calculator (X/T registers, the op stack, memories,
  flags, program banks, return stack, the `Interpret` loop, and the
  program/label/register listers). It runs programs through the `Scheduler`
  (`TaskRunner`, `ContinueTaskRunner`, `DelayedStep`).
- `Number.java`: TI-59 number arithmetic with 13-digit precision and rounding.
- `Keypad.java`: the key-entry state machine (`Press`/`Release` with key codes
  11..95 as printed on the keys: 2nd/Inv, operand collection, learn mode via
  `State.StoreInstr`). Host hooks: `OnNop` (save state) and `OnStatusChange`.
- `Printer.java`: PC-100C printer emulation (paper content, character set, line rendering).
- `Importer.java` / `Exporter.java`: data import/export (`Op 96`/`Op 97` extensions).
- `Disassembler.java`: opcodes → source text.
- `Assembler.java`: source text → opcodes. A Java port of `util/assemble`
  (same syntax, errors and output). `AssemblerTest` checks that both give the
  same bytes on every source in `modules/`, `programs/` and `examples/` (needs
  `python3`). Change both together.
- `Card.java`, `CardStore.java`: magnetic card contents (`2nd Write`/bank files).
- `CardDef.java`: text of a simple user label card (title, id, A'–E' and A–E labels)
  as `<carddef>` XML (the `util/rendercard` format), saved as `cardsrcNN` next to `cardNN`.
- `ProgramFiles.java`: loading and saving of programs (`.ti5p`), libraries
  (`.ti5l`), card banks (`.ti5b`) and the calculator state (ZIP + XML, see
  `file_format.txt`). Host UI state saved with it goes through `UiState`.
- `Tester.java`: built-in regression suite. Running `Op 99` on the calculator
  must display **44** (the number of tests passed). A negative value `-n` means
  test `n` failed. `CalculatorTest` runs it in `./gradlew :engine:test`.
  `KeypadTest` drives the engine through key presses (see `TestHost` for an
  in-memory host). `ProgramFilesTest` loads every generated `res/raw` file
  (skipped if the app was not built) and checks save/load round trips.
- Host interfaces the app or CLI implement: `Scheduler` (run loop),
  `DisplaySink` (LED display), `HelpSink` (label card and help), `FileStore`
  (files), `ErrorSink` (I/O error reports), and `BankStore` (card bank files).

App (`app/src/main/java/net/obry/ti5x/`):

- `Main.java`: the activity. Creates `Global.Engine` (`Calculator`) with `AppHost`
  (scheduler on the UI `Handler`, error dialogs, bank files), `Display`,
  `LabelCard`, and `AppData` as the host services.
- `ButtonGrid.java`: draws the keypad, handles touch and click/vibrate
  feedback, and passes key presses to `Global.Engine.Keys`.
- `Display.java`: the LED display view. `LabelCard.java`: the label card and help
  (tap/long press in Pgm 00 opens `CardEditor`).
- `CardEditor.java`: in-app editor of the user program label card, saved in
  `Ti5x/Card/inapp.png` and `.xml`. `CardRenderer.java`: draws a `CardDef` like `util/rendercard`.
- `Persistent.java`: background load/save tasks, built-in modules from
  `res/raw`, saved state location, and bank file location, on top of `ProgramFiles`.
- `PrinterActivity.java`: shows and saves the printer paper.
- `AppData.java`: file access (Storage Access Framework, with a fallback).
- `Global.java`: static singletons (`Engine`, `Calc`, `Disp`, `Buttons`, `Print`, `Label`, …) for the UI.

Command line (`cli/src/main/java/net/obry/ti5x/cli/`):

- `Main.java`: options (see `ti5x --help`). `Session.java`: loads the
  program (`.ti5p`, source text or `--opcodes`) and `--lib` modules, presses the
  `--keys` script when the calculator is idle, reads more key scripts from stdin
  in interactive mode, and runs the event loop (`ConsoleScheduler`, real time or
  `--fast`). `ConsoleDisplay` prints the display (`--trace`: one line per
  change) and the printer output. `ConsoleHost`: files under `--dir`.
- Key scripts use `engine/…/KeyNames.java` (assembler spellings; second
  functions include the 2nd press).
- Examples:
  ```sh
  ti5x --trace --fast --max-steps 50 examples/counter/prog
  ti5x --keys "op 99"                 # built-in tests, shows 44.
  ti5x --lib app/src/main/res/raw/ml.ti5l --keys "pgm 01 sbr ="
  ```

Rules for the engine:

- Engine code must not import `android.*`, `androidx.*`, or `R`.
- Engine code may only use Java APIs available on Android API 23.
- The engine reaches the UI only through the host interfaces, never through `Global`.

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

- Engine unit tests: `./gradlew :engine:test` (JUnit 5, `engine/src/test/`).
- CLI tests: `./gradlew :cli:test` (`SessionTest` runs whole sessions in memory).
- Calculator behaviour: run `Op 99` and check that the display shows `44`.
  When fixing a calculator bug, add a test case to `Tester.java` (and update the expected count everywhere it is mentioned).
- Assembler/disassembler changes: `./gradlew :engine:test` (`AssemblerTest`)
  and `util/check-assemble-disassemble`.
- UI changes: test on a device/emulator (portrait and landscape, phone and tablet layouts).

## Git

- Commit messages: a short sentence ending with a period
  (e.g. `Fix 2nd List to list the currently loaded program.`), with an optional body.
- Work on a branch and target `master`.
