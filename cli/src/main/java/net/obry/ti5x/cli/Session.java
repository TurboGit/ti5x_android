/*
    ti5x calculator emulator -- a command line run: load, feed keys, show the display

    Copyright 2026 Pascal Obry <pascal@obry.net>.

    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.obry.ti5x.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.obry.ti5x.engine.Assembler;
import net.obry.ti5x.engine.Calculator;
import net.obry.ti5x.engine.DataFormatException;
import net.obry.ti5x.engine.KeyNames;
import net.obry.ti5x.engine.ProgramFiles;
import net.obry.ti5x.engine.State;

final class Session {

  static final int EXIT_OK = 0;
  static final int EXIT_CALC_ERROR = 1; /* calculator left in error state */
  static final int EXIT_USAGE = 2; /* bad arguments, load or assembly error */
  static final int EXIT_MAX_STEPS = 3; /* program stopped by --max-steps */

  static final class LoadError extends Exception {
    LoadError(String Message) {
      super(Message);
    }
  }

  /* options */
  File Program = null; /* loaded into bank 00 */
  boolean Opcodes = false; /* Program is a list of opcodes instead of source */
  final List<File> Libraries = new ArrayList<>();
  final List<String> KeyScripts = new ArrayList<>();
  boolean Interactive = false;
  boolean Trace = false;
  boolean Fast = false;
  boolean ShowPrinter = true;
  long MaxSteps = 0; /* 0 for no limit */
  File BaseDir = new File(".");

  private final PrintStream Out;
  private final PrintStream Err;
  private final InputStream In;

  private ConsoleScheduler Sched;
  private ConsoleDisplay Disp;
  private Calculator Engine;
  private long Steps = 0;

  Session
     (
        PrintStream Out,
        PrintStream Err,
        InputStream In
     ) {
    this.Out = Out;
    this.Err = Err;
    this.In = In;
  }

  /* ---------------------------------------------------------------- loading */

  private static String ReadText(File F) throws IOException {
    return new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8);
  }

  private static boolean IsArchive(File F, String Ext) {
    return F.getName().toLowerCase(java.util.Locale.US).endsWith(Ext);
  }

  private void LoadArchive
     (
        File F,
        boolean Libs
     )
     throws LoadError {
    final State Calc = Engine.Calc;
    if (Libs) {
      Calc.ResetLibs();
    } else {
      Calc.ResetLabels();
    }
    try (InputStream Archive = new FileInputStream(F)) {
      ProgramFiles.Load(Calc, null, Archive, Libs, false);
    } catch (IOException | DataFormatException Failed) {
      throw new LoadError(F + ": " + Failed.getMessage());
    }
  }

  private byte[] ParseOpcodes
     (
        File F
     )
     throws IOException, LoadError {
    final List<Byte> Codes = new ArrayList<>();
    int LineNr = 0;
    for (String Line : ReadText(F).split("\n")) {
      LineNr++;
      final String Code = Line.replaceAll("[;#].*", "").trim();
      if (Code.isEmpty())
        continue;
      for (String Token : Code.split("\\s+")) {
        if (!Token.matches("\\d{1,2}")) {
          throw new LoadError(F + ":" + LineNr + ": bad opcode \"" + Token + "\"");
        }
        Codes.add((byte) Integer.parseInt(Token));
      }
    }
    final byte[] Result = new byte[Codes.size()];
    for (int i = 0; i < Result.length; i++) {
      Result[i] = Codes.get(i);
    }
    return Result;
  }

  private void LoadProgram
     (
        File F
     )
     throws LoadError {
    if (IsArchive(F, ".ti5p")) {
      LoadArchive(F, false);
      return;
    }
    byte[] Codes;
    try {
      if (Opcodes) {
        Codes = ParseOpcodes(F);
      } else {
        final Assembler.Result R = Assembler.Assemble(ReadText(F), Assembler.MaxProgramLoc, false);
        for (String W : R.Warnings) {
          Err.println(F + ": warning: " + W);
        }
        if (!R.OK()) {
          for (String E : R.Errors) {
            Err.println(F + ": " + E);
          }
          throw new LoadError(F + ": assembly failed");
        }
        Codes = R.Program;
      }
    } catch (IOException Failed) {
      throw new LoadError(F + ": " + Failed.getMessage());
    }
    final State Calc = Engine.Calc;
    if (Codes.length > Calc.MaxProgramSteps) {
      throw new LoadError(F + ": program too large, " + Codes.length + " steps");
    }
    Arrays.fill(Calc.Program, (byte) 0);
    System.arraycopy(Codes, 0, Calc.Program, 0, Codes.length);
    Calc.ResetLabels();
  }

  /* ---------------------------------------------------------------- running */

  private void PressKey
     (
        int Code
     ) {
    Engine.Keys.Press(Code);
    Engine.Keys.Release(Code);
    Disp.FlushPrinter();
    Disp.Refresh();
  }

  private void StartInput() {
    /* reads key scripts from the input, one per line, and queues the keys:
       they are pressed even while a program runs (e.g. r/s to stop it). */
    Sched.SetInputOpen(true);
    final Thread Reader = new Thread(() -> {
      try (BufferedReader Lines = new BufferedReader(new InputStreamReader(In, StandardCharsets.UTF_8))) {
        for (; ; ) {
          final String Line = Lines.readLine();
          if (Line == null)
            break;
          final String Script = Line.replaceAll("[;#].*", "").trim();
          if (Script.equals("quit") || Script.equals("exit"))
            break;
          try {
            for (int Code : KeyNames.Parse(Script)) {
              Sched.post(() -> PressKey(Code));
            }
          } catch (IllegalArgumentException Bad) {
            Err.println("ti5x: " + Bad.getMessage());
          }
        }
      } catch (IOException Failed) {
        Err.println("ti5x: input error: " + Failed);
      } finally {
        Sched.SetInputOpen(false);
      }
    }, "ti5x-input");
    Reader.setDaemon(true);
    Reader.start();
  }

  int Run() {
    Sched = new ConsoleScheduler(Fast);
    Disp = new ConsoleDisplay(Out, Trace, ShowPrinter);
    final ConsoleHost Host = new ConsoleHost(BaseDir);
    Engine = new Calculator(Sched, Disp, null, Host, Host, Host);
    Host.SetEngine(Engine);
    Disp.SetEngine(Engine);
    Engine.Keys.OnStatusChange = Disp::Refresh;

    final ArrayDeque<Integer> Script = new ArrayDeque<>();
    try {
      for (File Lib : Libraries) {
        LoadArchive(Lib, true);
      }
      if (Program != null) {
        LoadProgram(Program);
      }
      final List<String> Scripts = new ArrayList<>(KeyScripts);
      if (Scripts.isEmpty() && Program != null) {
        Scripts.add("rst r/s");
      }
      for (String S : Scripts) {
        for (int Code : KeyNames.Parse(S)) {
          Script.add(Code);
        }
      }
    } catch (LoadError | IllegalArgumentException Failed) {
      Err.println("ti5x: " + Failed.getMessage());
      return EXIT_USAGE;
    }

    Disp.Start();
    if (Interactive) {
      StartInput();
    }
    int Status = EXIT_OK;
    try {
      for (; ; ) {
        /* keys of the script are pressed once the calculator is idle */
        final boolean Idle = Sched.IsEmpty();
        if (Idle && !Script.isEmpty()) {
          PressKey(Script.poll());
          continue;
        }
        final Runnable Task = Sched.Take(true);
        if (Task == null)
          break;
        final boolean Running = Engine.Calc.TaskRunning;
        Task.run();
        Disp.FlushPrinter();
        if (Running && ++Steps == MaxSteps) {
          Engine.Calc.StopProgram();
          Disp.Finish();
          Err.println("ti5x: program stopped after " + MaxSteps + " steps");
          return EXIT_MAX_STEPS;
        }
      }
    } catch (InterruptedException Stopped) {
      Thread.currentThread().interrupt();
    }
    Disp.Finish();
    if (Engine.Calc.InErrorState()) {
      Status = EXIT_CALC_ERROR;
    }
    return Status;
  }
}
