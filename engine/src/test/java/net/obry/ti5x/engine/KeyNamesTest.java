/*
    ti5x calculator emulator -- key script tests

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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KeyNamesTest {

  @Test
  void firstFunctions() {
    assertArrayEquals(new int[] {81, 91}, KeyNames.Parse("rst r/s"));
    assertArrayEquals(new int[] {81, 91}, KeyNames.Parse("  RST   R/S  "));
    assertArrayEquals(new int[] {82, 85, 83, 95}, KeyNames.Parse("1 + 2 ="));
  }

  @Test
  void numbersAreDigitKeys() {
    assertArrayEquals(new int[] {42, 92, 73}, KeyNames.Parse("sto 05"));
    assertArrayEquals(new int[] {82, 93, 73}, KeyNames.Parse("1.5"));
  }

  @Test
  void secondFunctions() {
    /* Op is 2nd 9, log is 2nd ln x */
    assertArrayEquals(new int[] {21, 64, 64, 64}, KeyNames.Parse("op 99"));
    assertArrayEquals(new int[] {21, 64, 64, 64}, KeyNames.Parse("2nd op 99"));
    assertArrayEquals(new int[] {21, 23}, KeyNames.Parse("log"));
    assertArrayEquals(new int[] {21, 23}, KeyNames.Parse("2nd lnx"));
    assertArrayEquals(new int[] {21, 11}, KeyNames.Parse("a'"));
    assertArrayEquals(new int[] {21, 31, 92, 82}, KeyNames.Parse("pgm 01")); /* Pgm is 2nd LRN */
  }

  @Test
  void codes() {
    assertEquals(69, KeyNames.Code("op"));
    assertEquals(64, KeyNames.BaseKey(69));
    assertEquals(85, KeyNames.BaseKey(85));
  }

  @Test
  void unknownKey() {
    assertThrows(IllegalArgumentException.class, () -> KeyNames.Parse("rst bogus"));
    assertThrows(IllegalArgumentException.class, () -> KeyNames.Parse("-5"));
  }

  @Test
  void keyScriptDrivesTheCalculator() {
    final Calculator Engine = new TestHost().NewCalculator();
    for (int Code : KeyNames.Parse("2 + 3 = sto 05 clr rcl 05 log")) {
      Engine.Keys.Press(Code);
      Engine.Keys.Release(Code);
    }
    assertEquals(".6989700043", Engine.Calc.CurDisplay.trim()); /* no room for the leading 0 */
  }
}
