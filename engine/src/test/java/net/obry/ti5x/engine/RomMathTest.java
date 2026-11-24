/*
    ti5x calculator emulator -- tests of the ROM coprocessor

    The ROM is not part of ti5x: these tests run only when its files
    (dump.txt and Const.c) are found, in the directory given by the
    TI59_ROM environment variable or in workspace/emul.

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RomMathTest {

  private static RomMath Rom;

  private static File RomDir() {
    final String Env = System.getenv("TI59_ROM");
    return Env != null ? new File(Env) : new File("../workspace/emul");
  }

  @BeforeAll
  static void Load() throws IOException {
    final File Dir = RomDir();
    if (!new File(Dir, "dump.txt").exists() || !new File(Dir, "Const.c").exists())
      return;
    try (InputStream Dump = new FileInputStream(new File(Dir, "dump.txt"));
         InputStream Consts = new FileInputStream(new File(Dir, "Const.c"))) {
      Rom = new RomMath(Dump, Consts);
    }
  }

  /* x, angular unit, sin(x) computed by the ROM (reference emulator) */

  private static final Object[][] ROM_SIN = {
      {"0.6666666666666", Number.ANG_DEG, "0.01163526580108"},
      {"30", Number.ANG_DEG, "0.5"},
      {"379.53385", Number.ANG_RAD, "0.5637021197963"},
      {"9.79442474", Number.ANG_DEG, "0.1701136116241"},
      {"0.1017", Number.ANG_GRAD, "0.001597499184209"},
      {"-0.2579066167", Number.ANG_RAD, "-0.2550569654316"},
      {"-0.7102", Number.ANG_DEG, "-0.01239501093749"},
      {"-0.1262882", Number.ANG_RAD, "-0.1259527786199"},
      {"-931.859881", Number.ANG_GRAD, "-0.8773648888373"},
      {"387.83081", Number.ANG_GRAD, "-0.1899912061614"},
      {"4.82", Number.ANG_RAD, "-0.9942155195494"},
      {"-0.093744812", Number.ANG_RAD, "-0.093607566016"},
      {"-19.731", Number.ANG_GRAD, "-0.3049956134238"},
      {"-0.0601", Number.ANG_RAD, "-0.0600638262331"},
      {"-5.8287556956", Number.ANG_RAD, "0.4389498846572"},
      {"-0.75504426", Number.ANG_GRAD, "-0.01185992945181"},
      {"-597.841", Number.ANG_GRAD, "-0.03390699230823"},
      {"-416.02540962", Number.ANG_GRAD, "-0.2490764611891"},
      {"-364.09629889", Number.ANG_GRAD, "0.5345490018636"},
      {"-94.63", Number.ANG_DEG, "-0.9967367497671"},
      {"-994.34", Number.ANG_RAD, "-0.9996639624265"},
      {"0.005", Number.ANG_GRAD, "0.00007853981575786"},
      {"-0.0036271769", Number.ANG_GRAD, "-0.00005697556090758"},
      {"3.393525", Number.ANG_RAD, "-0.2492757706298"},
      {"52.37042", Number.ANG_DEG, "0.7919745391208"},
      {"-0.663", Number.ANG_RAD, "-0.6154840660891"},
      {"-68.0", Number.ANG_DEG, "-0.9271838545678"},
      {"0.008", Number.ANG_GRAD, "0.0001256637053304"},
      {"9.2285953077", Number.ANG_DEG, "0.1603738299665"},
      {"-1.817628", Number.ANG_RAD, "-0.969691413884"},
      {"8.7257", Number.ANG_GRAD, "0.1366342279168"},
      {"-0.0086133", Number.ANG_RAD, "-0.008613193497743"},
  };

  @Test
  void sin() {
    assumeTrue(Rom != null, "no ROM in " + RomDir());
    for (Object[] C : ROM_SIN) {
      final BigDecimal R = Rom.Sin(new BigDecimal((String) C[0]), (Integer) C[1]);
      assertNotNull(R);
      assertEquals(0, new BigDecimal((String) C[2]).compareTo(R), "sin " + C[0] + " = " + C[2] + ", got " + R);
    }
  }

  @Test
  void registerFormat() {
    final int[] Reg = new int[16];
    for (String V : new String[] {"0", "1", "-2.5", "1.23E-13", "-9.999999999999E99", "0.32"}) {
      RomMath.ToRam(new BigDecimal(V), Reg);
      assertEquals(0, new BigDecimal(V).compareTo(RomMath.FromRam(Reg)), V);
    }
  }
}
