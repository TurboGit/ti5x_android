/*
    ti5x calculator emulator -- tests of the ROM coprocessor

    The expected values were computed by the reference emulator
    (workspace/emul) running the same ROM.

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RomMathTest {

  private static RomMath Rom;

  @BeforeAll
  static void Load() {
    Rom = new RomMath();
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
    for (Object[] C : ROM_SIN) {
      final BigDecimal R = Rom.Sin(new BigDecimal((String) C[0]), (Integer) C[1]);
      assertNotNull(R);
      assertEquals(0, new BigDecimal((String) C[2]).compareTo(R), "sin " + C[0] + " = " + C[2] + ", got " + R);
    }
  }

  /* function, angular unit, x, y, result computed by the ROM (keyboard
     of the reference emulator) */

  private static final Object[][] ROM_FUNCTIONS = {
      {"Cos", Number.ANG_DEG, "2.5", "", "0.9990482215833"},
      {"Tan", Number.ANG_RAD, "2.5", "", "-0.7470222972388"},
      {"Ln", 0, "2.5", "", "0.9162907318742"},
      {"Log", 0, "2.5", "", "0.397940008672"},
      {"Exp", 0, "0.25", "", "1.284025416687"},
      {"Exp10", 0, "0.25", "", "1.778279410035"},
      {"Pow", 0, "2.5", "2.7", "11.86965301456"},
      {"Root", 0, "2.5", "3", "1.357208808296"},
      {"Cos", Number.ANG_DEG, "123.456", "", "-0.5512964442861"},
      {"Tan", Number.ANG_RAD, "123.456", "", "1.351805082139"},
      {"Ln", 0, "123.456", "", "4.815884817283"},
      {"Log", 0, "123.456", "", "2.091512201627"},
      {"Exp", 0, "12.3456", "", "229946.0511098"},
      {"Exp10", 0, "12.3456", "", "2216154325833"},
      {"Pow", 0, "123.456", "2.7", "443693.3752948"},
      {"Root", 0, "123.456", "3", "4.979327984671"},
      {"Asin", Number.ANG_DEG, "0.5", "", "29.99999999993"},
      {"Acos", Number.ANG_GRAD, "0.5", "", "66.66666666664"},
      {"Atan", Number.ANG_DEG, "0.5", "", "26.56505117705"},
      {"Asin", Number.ANG_DEG, "0.1234567891", "", "7.091646025263"},
      {"Acos", Number.ANG_GRAD, "0.1234567891", "", "92.1203933052"},
      {"Atan", Number.ANG_DEG, "0.1234567891", "", "7.037940705426"},
  };

  private static BigDecimal Function(String F, int Angle, BigDecimal X, BigDecimal Y) {
    switch (F) {
      case "Cos": return Rom.Cos(X, Angle);
      case "Tan": return Rom.Tan(X, Angle);
      case "Asin": return Rom.Asin(X, Angle);
      case "Acos": return Rom.Acos(X, Angle);
      case "Atan": return Rom.Atan(X, Angle);
      case "Ln": return Rom.Ln(X);
      case "Log": return Rom.Log(X);
      case "Exp": return Rom.Exp(X);
      case "Exp10": return Rom.Exp10(X);
      case "Pow": return Rom.Pow(X, Y);
      case "Root": return Rom.Root(X, Y);
      default: throw new IllegalArgumentException(F);
    }
  }

  @Test
  void functions() {
    for (Object[] C : ROM_FUNCTIONS) {
      final String Y = (String) C[3];
      final BigDecimal R = Function((String) C[0], (Integer) C[1], new BigDecimal((String) C[2]),
         Y.isEmpty() ? null : new BigDecimal(Y));
      assertNotNull(R);
      assertEquals(0, new BigDecimal((String) C[4]).compareTo(R), C[0] + " " + C[2] + " " + Y + " = " + C[4] + ", got " + R);
      assertFalse(Rom.LastError, C[0] + " " + C[2]);
    }
  }

  @Test
  void polar() {
    // 5 x<>t 30 P->R in degrees: y = 2.5, x = 4.330127018927
    BigDecimal[] R = Rom.Polar(false, new BigDecimal(5), new BigDecimal(30), Number.ANG_DEG);
    assertEquals(0, new BigDecimal("2.5").compareTo(R[0]), R[0].toString());
    assertEquals(0, new BigDecimal("4.330127018927").compareTo(R[1]), R[1].toString());
    // x = 5.330127018927, y = 1: angle 10.62589805097, radius 5.423122166967
    R = Rom.Polar(true, new BigDecimal("5.330127018927"), BigDecimal.ONE, Number.ANG_DEG);
    assertEquals(0, new BigDecimal("10.62589805097").compareTo(R[0]), R[0].toString());
    assertEquals(0, new BigDecimal("5.423122166967").compareTo(R[1]), R[1].toString());
  }

  @Test
  void errors() {
    // ln of a negative number: ln |x| and the error
    assertEquals(0, new BigDecimal("0.69314718056").compareTo(Rom.Ln(new BigDecimal(-2))));
    assertTrue(Rom.LastError);
    assertEquals(0, new BigDecimal(2).compareTo(Rom.Asin(new BigDecimal(2), Number.ANG_DEG)));
    assertTrue(Rom.LastError);
    Rom.Ln(new BigDecimal(2));
    assertFalse(Rom.LastError);
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
