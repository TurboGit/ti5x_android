/*
    ti5x calculator emulator -- Number unit tests

    Ported from the former tests/test1.java and tests/test2.java, the
    expected values are the ones from tests/test1.out and tests/test2.out.

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NumberTest {

  /* these tests check the classic mode (Op 95, --no-rom): the arithmetic
     rounded to 13 digits and the functions of ti5x; see RomArithmeticTest
     and RomMathTest for the ROM mode */

  private static RomMath Saved;

  @BeforeAll
  static void NoRom() {
    Saved = Number.Rom();
    Number.SetRom(null);
  }

  @AfterAll
  static void RestoreRom() {
    Number.SetRom(Saved);
  }

  private static String str(Number n) {
    return String.valueOf(n.get());
  }

  @Test
  void addAndCos() {
    final Number n1 = new Number(1.0);
    final Number n2 = new Number(2.0);
    n1.add(n2);
    n2.cos(Number.ANG_RAD);
    assertEquals("3.0", str(n1));
    assertEquals("-0.4161468365471", str(n2));
    n2.fracPart();
    assertEquals("-0.4161468365471", str(n2));
  }

  @Test
  void intPart() {
    for (double d : new double[] {1.0, 1.1, 1.4, 1.5, 1.9, 1.999999999}) {
      final Number n = new Number(d);
      n.intPart();
      assertEquals("1.0", str(n), "intPart(" + d + ")");
    }
  }

  @Test
  void randomFromOriginalTI() {
    final Number n1 = new Number(24298);
    final Number n2 = new Number(99991);
    final Number n3 = new Number(199017);
    final Number r9 = new Number();
    final Number x = new Number();
    final Number e5 = new Number();
    final Number Pi = new Number();
    Pi.Pi();
    e5.set(10);
    e5.pow(new Number(5));
    r9.set(Pi);

    final String[] expected = {"0.88598", "0.08556"};
    for (String e : expected) {
      x.set(n1);
      x.mult(r9);
      x.add(n2);
      r9.set(n3);
      x.div(n3);
      x.fracPart();
      r9.mult(x);
      x.mult(e5);
      x.intPart();
      x.div(e5);
      assertEquals(e, str(x));
    }
  }

  @Test
  void copyIsIndependent() {
    final Number orig = new Number(10);
    final Number copy = new Number(orig);
    copy.x2();
    assertEquals("10.0", str(orig));
    assertEquals("100.0", str(copy));
  }

  @Test
  void trigAndSqrt() {
    final Number x = new Number();
    x.set(90);
    x.cos(Number.ANG_DEG);
    assertEquals("0.0", str(x));
    x.set(45);
    x.cos(Number.ANG_DEG);
    assertEquals("0.7071067811867", str(x));
    x.set(Number.PI);
    x.cos(Number.ANG_RAD);
    assertEquals("-1.0", str(x));

    x.set(8);
    x.sqrt();
    assertEquals("2.828427124746", str(x));
    assertFalse(x.isError());
    x.set(0);
    x.sqrt();
    assertEquals("0.0", str(x));
    assertFalse(x.isError());
    x.set(-5);
    x.sqrt();
    /* classic mode: rounded (the ROM truncates: 2.236067977499) */
    assertEquals("2.2360679775", str(x));
    assertTrue(x.isError());
  }

  @Test
  void powAndLog() {
    final Number x = new Number();
    x.set(98765);
    x.log();
    assertEquals("4.994603068071", str(x));
    assertFalse(x.isError());
    x.set(98765);
    x.ln();
    assertEquals("11.50049856996", str(x));
    assertFalse(x.isError());
    x.set(10);
    x.pow(new Number(2.5));
    /* the exact value is 316.2277660168 */
    assertEquals("316.2277660168", str(x));
    assertFalse(x.isError());
  }

  @Test
  void precision() {
    final Number one = new Number(1.0);
    final Number lc = new Number(0.05);
    final Number n4 = new Number(0.1);
    for (int k = 0; k < 100; k++) {
      one.add(lc);
      one.add(n4);
      one.sub(lc);
    }
    n4.set(0.1 * 100);
    one.sub(n4);
    assertTrue(one.isEqual(new Number(1)), "got " + str(one));
  }

  @Test
  void figuresAndScale() {
    final Number x = new Number(123.456);
    assertEquals(3, x.figuresBeforeDecimal(0));
    assertEquals(2, x.figuresBeforeDecimal(1));
    assertEquals(1, x.figuresBeforeDecimal(2));
    assertEquals(1, x.figuresBeforeDecimal(3));

    final double[] values = {9.76, 69.76, 169.76, 8169.76};
    final int[] eng = {0, 0, 0, 3};
    final int[] flt = {0, 1, 2, 3};
    for (int i = 0; i < values.length; i++) {
      x.set(values[i]);
      assertEquals(eng[i], x.scaleExp(Number.FORMAT_ENG), "eng " + values[i]);
      assertEquals(flt[i], x.scaleExp(Number.FORMAT_FLOAT), "float " + values[i]);
    }
  }

  @Test
  void nDigitsAndRem() {
    final Number x = new Number(123.456789);
    x.nDigits(1);
    assertEquals("123.4", str(x));
    x.set("123.456789");
    x.nDigits(3);
    assertEquals("123.456", str(x));

    x.set(123.456789);
    x.rem(23);
    assertEquals("8.456789", str(x));
  }

  @Test
  void stateString() {
    /* fixed notation as before when exact, scientific notation otherwise */
    assertEquals("3.2500000000000000", new Number(3.25).toStateString());
    assertEquals("-0.0001230000000000", new Number(-0.000123).toStateString());
    assertEquals("0.0000000000000000", new Number().toStateString());
    assertEquals("-1E-20", new Number("-1E-20").toStateString());
    assertEquals("1.234567890123E-10", new Number("1.234567890123E-10").toStateString());
    final Number Big = new Number("9.876543210123E+99");
    assertTrue(Big.isEqual(new Number(Big.toStateString())));
  }

  @Test
  void formatString() {
    final Number x = new Number(123.456789);
    x.rem(23);
    final String[] pos =
       {"8", "8.5", "8.46", "8.457", "8.4568", "8.45679", "8.456789", "8.4567890"};
    for (int k = 0; k < pos.length; k++) {
      assertEquals(pos[k], x.formatString(Locale.US, k));
    }
    x.set(-6.987654);
    final String[] neg =
       {"-7", "-7.0", "-6.99", "-6.988", "-6.9877", "-6.98765", "-6.987654", "-6.9876540"};
    for (int k = 0; k < neg.length; k++) {
      assertEquals(neg[k], x.formatString(Locale.US, k));
    }

    final double[] values = {1.1, 12.12, 123.123, 1234.1234, 12345.12345, 123456.123456};
    final String[] eight =
       {"1.10000000", "12.12000000", "123.12300000", "1234.12340000",
        "12345.12345000", "123456.12345600"};
    for (int i = 0; i < values.length; i++) {
      x.set(values[i]);
      assertEquals(eight[i], x.formatString(Locale.US, 8));
    }
  }
}
