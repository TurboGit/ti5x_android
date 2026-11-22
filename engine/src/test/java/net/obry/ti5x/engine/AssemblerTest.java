/*
    ti5x calculator emulator -- assembler tests

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

package net.obry.ti5x.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class AssemblerTest {
  private static final File Root = new File("..");

  private static byte[] Codes(int... Ops) {
    final byte[] Result = new byte[Ops.length];
    for (int i = 0; i < Ops.length; i++) {
      Result[i] = (byte) Ops[i];
    }
    return Result;
  }

  private static byte[] Asm(String Source) {
    final Assembler.Result R = Assembler.Assemble(Source);
    assertTrue(R.OK(), R.Errors.toString());
    return R.Program;
  }

  /* --------------------------------------------------------- unit cases */

  @Test
  void simpleOps() {
    assertArrayEquals(Codes(85, 1, 95, 91, 81), Asm("+ 1 = r/s rst"));
    assertArrayEquals(Codes(85, 1, 95, 91, 81), Asm("; a counter\n+ 1 =\nR/S # stop\nRST\n"));
  }

  @Test
  void secondAndSynonyms() {
    assertArrayEquals(Codes(28, 28, 38, 38), Asm("2nd lnx log 2nd x2 sin"));
    assertArrayEquals(Codes(32, 32, 45, 45), Asm("x<>t x⇌t y**x yˣ"));
  }

  @Test
  void registers() {
    assertArrayEquals(Codes(42, 5, 43, 12), Asm("sto 05 rcl 12"));
    assertArrayEquals(Codes(72, 5, 73, 12, 72, 7), Asm("sto ind 05 rc* 12 st* 07"));
    assertArrayEquals(Codes(69, 99), Asm("op 99"));
  }

  @Test
  void transfers() {
    assertArrayEquals(Codes(61, 1, 23), Asm("gto 123"));
    assertArrayEquals(Codes(71, 11), Asm("sbr a"));
    assertArrayEquals(Codes(76, 16), Asm("lbl a'"));
    assertArrayEquals(Codes(87, 3, 0, 15), Asm("ifflg 3 015"));
    assertArrayEquals(Codes(97, 2, 1, 0), Asm("dsz 2 100"));
    assertArrayEquals(Codes(97, 2, 51), Asm("dsz 2 51")); /* undocumented "decrement, no jump" */
  }

  @Test
  void invMerging() {
    assertArrayEquals(Codes(22, 58), Asm("inv fix"));
    assertArrayEquals(Codes(92), Asm("inv sbr"));
    assertArrayEquals(Codes(58, 2), Asm("fix 2"));
  }

  @Test
  void numbersAreKeystrokes() {
    assertArrayEquals(Codes(1, 93, 5, 52, 3, 94), Asm("1.5e-3"));
    assertArrayEquals(Codes(2, 94, 52, 1), Asm("-2e1"));
  }

  @Test
  void symbolicLabels() {
    assertArrayEquals(Codes(1, 61, 0, 0, 76, 11, 61, 0, 9), Asm("start: 1 gto start lbl a gto here here:"));
  }

  @Test
  void locationMarkers() {
    assertTrue(Assembler.Assemble("000: + 001: 1 002: =").OK());
    final Assembler.Result R = Assembler.Assemble("+ 1\n005: =");
    assertFalse(R.OK());
    assertTrue(R.Errors.get(0).contains("line 2"), R.Errors.toString());
  }

  @Test
  void errors() {
    assertFalse(Assembler.Assemble("fix 12").OK());
    assertFalse(Assembler.Assemble("gto 1000").OK());
    assertFalse(Assembler.Assemble("ind").OK());
    assertFalse(Assembler.Assemble("gto nowhere").OK());
    assertFalse(Assembler.Assemble("1 ".repeat(1001)).OK());
    assertTrue(Assembler.Assemble("1 ".repeat(1001), Assembler.MaxRomLoc, false).OK());
  }

  @Test
  void checkLabels() {
    final Assembler.Result R = Assembler.Assemble("lbl a lbl sin gto cos", Assembler.MaxProgramLoc, true);
    assertTrue(R.OK());
    assertEquals(2, R.Warnings.size(), R.Warnings.toString());
  }

  /* --------------------------------------------- comparison with util/assemble */

  private static List<File> Sources() {
    final List<File> Result = new ArrayList<>();
    for (String Dir : new String[] {"modules", "programs", "examples"}) {
      final File[] Subs = new File(Root, Dir).listFiles(File::isDirectory);
      if (Subs == null)
        continue;
      for (File Sub : Subs) {
        final File[] Files = Sub.listFiles
           (
              (D, Name) -> Name.matches("prog\\d*\\.src|prog|bank\\d*|banksrc.*")
           );
        if (Files != null) {
          for (File F : Files) {
            Result.add(F);
          }
        }
      }
    }
    return Result;
  }

  private static boolean IsRom(File F) {
    return F.getPath().contains("modules");
  }

  private static byte[] PythonAssemble(File Source) throws IOException, InterruptedException {
    final List<String> Cmd = new ArrayList<>();
    Cmd.add("python3");
    Cmd.add(new File(Root, "util/assemble").getPath());
    if (IsRom(Source)) {
      Cmd.add("--rom");
    }
    final Process P = new ProcessBuilder(Cmd)
       .redirectInput(Source)
       .redirectError(ProcessBuilder.Redirect.DISCARD)
       .start();
    final ByteArrayOutputStream Out = new ByteArrayOutputStream();
    P.getInputStream().transferTo(Out);
    assertTrue(P.waitFor(30, TimeUnit.SECONDS));
    if (P.exitValue() != 0) {
      return null; /* assembly error */
    }
    final String Text = Out.toString(StandardCharsets.UTF_8).trim();
    if (Text.isEmpty()) {
      return new byte[0];
    }
    final String[] Tokens = Text.split("\\s+");
    final byte[] Result = new byte[Tokens.length];
    for (int i = 0; i < Tokens.length; i++) {
      Result[i] = (byte) Integer.parseInt(Tokens[i]);
    }
    return Result;
  }

  private static boolean HavePython() {
    try {
      return new ProcessBuilder("python3", "--version").start().waitFor() == 0;
    } catch (IOException | InterruptedException NoPython) {
      return false;
    }
  }

  @Test
  void matchesPythonAssembler() throws Exception {
    assumeTrue(HavePython(), "python3 not available");
    final List<File> All = Sources();
    assumeTrue(!All.isEmpty(), "no sources found");
    int Checked = 0;
    for (File F : All) {
      final byte[] Expected = PythonAssemble(F);
      final Assembler.Result R = Assembler.Assemble
         (
            new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8),
            IsRom(F) ? Assembler.MaxRomLoc : Assembler.MaxProgramLoc,
            false
         );
      if (Expected == null) {
        assertFalse(R.OK(), F + ": util/assemble fails but Java does not");
      } else {
        assertTrue(R.OK(), F + ": " + R.Errors);
        assertArrayEquals(Expected, R.Program, F.getPath());
      }
      Checked++;
    }
    assertTrue(Checked > 300, "only " + Checked + " sources checked");
  }

  @Test
  void disassembleRoundTrip() throws IOException {
    int Checked = 0;
    for (File F : Sources()) {
      final Assembler.Result R = Assembler.Assemble
         (
            new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8),
            Assembler.MaxRomLoc,
            false
         );
      if (!R.OK())
        continue;
      /* trailing zeroes are not disassembled */
      int End = R.Program.length;
      while (End > 0 && R.Program[End - 1] == 0) {
        End--;
      }
      final byte[] Program = java.util.Arrays.copyOf(R.Program, End);
      final String Text = Disassembler.disassemble(Program);
      final Assembler.Result Back = Assembler.Assemble(Text, Assembler.MaxRomLoc, false);
      assertTrue(Back.OK(), F + ": " + Back.Errors + "\n" + Text);
      assertArrayEquals(Program, Back.Program, F.getPath());
      Checked++;
    }
    assertTrue(Checked > 300, "only " + Checked + " sources checked");
  }
}
