/*
    ti5x calculator emulator -- command line front-end

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

import java.io.File;

public final class Main {

  private static final String USAGE =
       "Usage: ti5x [options] [PROGRAM]\n"
     + "\n"
     + "Runs a TI-59 program on the console, showing the calculator display.\n"
     + "\n"
     + "  PROGRAM            program loaded into bank 00: a .ti5p file, or program\n"
     + "                     source text (the util/assemble syntax)\n"
     + "  --opcodes          PROGRAM is a list of opcodes (two-digit numbers)\n"
     + "  --lib FILE.ti5l    load a library module, 2nd Pgm nn selects its programs\n"
     + "  --keys \"KEYS\"      key script, e.g. \"rst r/s\", \"5 sto 01\", \"op 99\",\n"
     + "                     \"2nd pgm 01 sbr 012\"; can be repeated. The keys are\n"
     + "                     pressed when the calculator is idle. Default with a\n"
     + "                     PROGRAM: \"rst r/s\"\n"
     + "  -i, --interactive  then read key scripts from standard input, one per line,\n"
     + "                     \"quit\" or end of file to exit (default when standard\n"
     + "                     input is a terminal)\n"
     + "  -b, --batch        never read standard input\n"
     + "  --trace            print each display change on its own line (default when\n"
     + "                     standard output is not a terminal)\n"
     + "  --fast             don't wait for Pause and the other display delays\n"
     + "  --max-steps N      stop a running program after N steps\n"
     + "  --no-printer       don't show the printer output (PRT lines)\n"
     + "  --dir DIR          directory for the Data/ and Banks/ files (default .)\n"
     + "  --no-rom           start in emulator mode: don't use the original TI-59\n"
     + "                     ROM for the trigonometric, logarithmic and power\n"
     + "                     functions (Op 95 switches the mode)\n"
     + "  --rom DIR          use another ROM: DIR has the ROM dump.txt and the\n"
     + "                     constants Const.c\n"
     + "  -h, --help         show this help\n"
     + "\n"
     + "Display: |       3.14| with \" !\" when flashing (error), \"C\" while running,\n"
     + "then the 2nd, Inv, Rad/Grad and Lrn indicators.\n"
     + "\n"
     + "Exit status: 0 ok, 1 calculator in error state, 2 bad arguments or load\n"
     + "error, 3 stopped by --max-steps.\n";

  private Main() {
  }

  private static boolean IsTerminal
     (
        boolean Input
     ) {
    final java.io.Console C = System.console();
    if (C == null)
      return false;
    try {
      /* Java 22+: a console may exist without a terminal */
      return (Boolean) java.io.Console.class.getMethod("isTerminal").invoke(C);
    } catch (ReflectiveOperationException Older) {
      return true;
    }
  }

  private static String Value
     (
        String[] Args,
        int i
     ) {
    if (i >= Args.length) {
      throw new IllegalArgumentException("missing value for " + Args[i - 1]);
    }
    return Args[i];
  }

  static int Run
     (
        String[] Args
     ) {
    final Session S = new Session(System.out, System.err, System.in);
    Boolean Interactive = null;
    Boolean Trace = null;
    try {
      for (int i = 0; i < Args.length; i++) {
        final String A = Args[i];
        switch (A) {
          case "-h":
          case "--help":
            System.out.print(USAGE);
            return Session.EXIT_OK;
          case "--opcodes":
            S.Opcodes = true;
            break;
          case "--lib":
            S.Libraries.add(new File(Value(Args, ++i)));
            break;
          case "--keys":
            S.KeyScripts.add(Value(Args, ++i));
            break;
          case "-i":
          case "--interactive":
            Interactive = true;
            break;
          case "-b":
          case "--batch":
            Interactive = false;
            break;
          case "--trace":
            Trace = true;
            break;
          case "--fast":
            S.Fast = true;
            break;
          case "--max-steps":
            S.MaxSteps = Long.parseLong(Value(Args, ++i));
            break;
          case "--no-printer":
            S.ShowPrinter = false;
            break;
          case "--dir":
            S.BaseDir = new File(Value(Args, ++i));
            break;
          case "--no-rom":
            net.obry.ti5x.engine.Number.SetRomMode(false);
            break;
          case "--rom": {
            final File Dir = new File(Value(Args, ++i));
            try (java.io.InputStream Dump = new java.io.FileInputStream(new File(Dir, "dump.txt"));
                 java.io.InputStream Consts = new java.io.FileInputStream(new File(Dir, "Const.c"))) {
              net.obry.ti5x.engine.Number.SetRom(new net.obry.ti5x.engine.RomMath(Dump, Consts));
            } catch (java.io.IOException Failed) {
              throw new IllegalArgumentException("cannot load the ROM: " + Failed.getMessage());
            }
            break;
          }
          default:
            if (A.startsWith("-") && A.length() > 1) {
              throw new IllegalArgumentException("unknown option " + A);
            }
            if (S.Program != null) {
              throw new IllegalArgumentException("only one PROGRAM can be given");
            }
            if (A.toLowerCase(java.util.Locale.US).endsWith(".ti5l")) {
              S.Libraries.add(new File(A));
            } else {
              S.Program = new File(A);
            }
            break;
        }
      }
    } catch (IllegalArgumentException Bad) {
      System.err.println("ti5x: " + Bad.getMessage());
      System.err.print(USAGE);
      return Session.EXIT_USAGE;
    }
    S.Interactive = Interactive != null ? Interactive : IsTerminal(true);
    S.Trace = Trace != null ? Trace : !IsTerminal(false);
    return S.Run();
  }

  public static void main
     (
        String[] Args
     ) {
    System.exit(Run(Args));
  }
}
