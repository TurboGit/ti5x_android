/*
    ti5x calculator emulator -- command line tests

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionTest {

  private final ByteArrayOutputStream Out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream Err = new ByteArrayOutputStream();
  private int Status;

  private Session NewSession(String Input) {
    final Session S = new Session
       (
          new PrintStream(Out, true, StandardCharsets.UTF_8),
          new PrintStream(Err, true, StandardCharsets.UTF_8),
          new ByteArrayInputStream(Input.getBytes(StandardCharsets.UTF_8))
       );
    S.Trace = true;
    S.Fast = true;
    return S;
  }

  private List<String> Lines() {
    return Arrays.asList(Out.toString(StandardCharsets.UTF_8).split("\n"));
  }

  private String LastLine() {
    final List<String> L = Lines();
    return L.get(L.size() - 1);
  }

  private Path Source(Path Dir, String Text) throws IOException {
    final Path P = Dir.resolve("prog.src");
    Files.write(P, Text.getBytes(StandardCharsets.UTF_8));
    return P;
  }

  @Test
  void displayPadding() {
    assertEquals("           0 ", ConsoleDisplay.Pad("0"));
    assertEquals("           1.", ConsoleDisplay.Pad("1."));
    assertEquals("        3.14", ConsoleDisplay.Pad("3.14").substring(1));
    assertEquals(" 9.9999999 99", ConsoleDisplay.Pad("9.9999999 99"));
  }

  @Test
  void keyScript() {
    final Session S = NewSession("");
    S.KeyScripts.add("2 + 3 =");
    Status = S.Run();
    assertEquals(Session.EXIT_OK, Status);
    assertEquals("|           5.|", LastLine());
  }

  @Test
  void builtinTests() {
    final Session S = NewSession("");
    S.KeyScripts.add("op 99");
    S.ShowPrinter = false;
    assertEquals(Session.EXIT_OK, S.Run());
    assertEquals("|          43.|", LastLine());
  }

  @Test
  void errorState() {
    final Session S = NewSession("");
    S.KeyScripts.add("1 / 0 =");
    assertEquals(Session.EXIT_CALC_ERROR, S.Run());
    assertEquals("| 9.9999999 99| !", LastLine());
  }

  @Test
  void runsSourceProgram(@TempDir Path Dir) throws IOException {
    /* the counter of examples/counter: shows 1, 2, 3... on each Pause */
    final Session S = NewSession("");
    S.Program = Source(Dir, "; a simple counter\n+ 1 =\npause\nrst\n").toFile();
    S.MaxSteps = 20;
    assertEquals(Session.EXIT_MAX_STEPS, S.Run());
    final List<String> L = Lines();
    assertTrue(L.contains("|C            |"), L.toString());
    assertTrue(L.contains("|           1.|"), L.toString());
    assertTrue(L.contains("|           4.|"), L.toString());
  }

  @Test
  void runStopRun(@TempDir Path Dir) throws IOException {
    final Session S = NewSession("");
    S.Program = Source(Dir, "lbl a + 1 = r/s gto a").toFile();
    S.KeyScripts.add("clr a r/s r/s");
    assertEquals(Session.EXIT_OK, S.Run());
    assertEquals("|           3.|", LastLine());
  }

  @Test
  void opcodes(@TempDir Path Dir) throws IOException {
    final Path P = Dir.resolve("prog.opc");
    Files.write(P, "85 01 95 ; + 1 =\n91 81\n".getBytes(StandardCharsets.UTF_8));
    final Session S = NewSession("");
    S.Program = P.toFile();
    S.Opcodes = true;
    S.KeyScripts.add("rst r/s r/s");
    assertEquals(Session.EXIT_OK, S.Run());
    assertEquals("|           2.|", LastLine());
  }

  @Test
  void interactive() {
    final Session S = NewSession("2 + 3 =\nsto 05 clr\nrcl 05 x2\nquit\n");
    S.Interactive = true;
    assertEquals(Session.EXIT_OK, S.Run());
    assertEquals("|          25.|", LastLine());
  }

  @Test
  void assemblyError(@TempDir Path Dir) throws IOException {
    final Session S = NewSession("");
    S.Program = Source(Dir, "fix 12").toFile();
    assertEquals(Session.EXIT_USAGE, S.Run());
    assertTrue(Err.toString(StandardCharsets.UTF_8).contains("flag number can't be > 9"));
    assertEquals("", Out.toString(StandardCharsets.UTF_8));
  }

  @Test
  void printer() {
    final Session S = NewSession("");
    S.KeyScripts.add("42 prt");
    assertEquals(Session.EXIT_OK, S.Run());
    assertTrue(Lines().contains("PRT           42."), Lines().toString());
  }

  @Test
  void dataExportAndBankFiles(@TempDir Path Dir) throws IOException {
    /* Inv Op 96 opens data file 1.dat for export, Prt writes to it, Inv Adv
       closes it; 2nd Write saves bank 1 as card 001 */
    final Session S = NewSession("");
    S.BaseDir = Dir.toFile();
    S.KeyScripts.add("1 inv op 96 42 prt inv adv 1.001 write");
    assertEquals(Session.EXIT_OK, S.Run());
    final File Data = new File(Dir.toFile(), "Data/1.dat");
    assertTrue(Data.isFile());
    assertEquals("42.", new String(Files.readAllBytes(Data.toPath()), StandardCharsets.UTF_8).trim());
    assertTrue(new File(Dir.toFile(), "Banks/001.ti5b").isFile());
  }

  @Test
  void masterLibrary() {
    final File Ml = new File("../app/src/main/res/raw/ml.ti5l");
    assumeTrue(Ml.exists(), "build the app first");
    final Session S = NewSession("");
    S.Libraries.add(Ml);
    S.KeyScripts.add("pgm 01 sbr =");
    S.MaxSteps = 100000;
    assertEquals(Session.EXIT_OK, S.Run());
    assertTrue(Lines().stream().anyMatch(L -> L.matches("PRT +MASTER")), Lines().toString());
  }
}
