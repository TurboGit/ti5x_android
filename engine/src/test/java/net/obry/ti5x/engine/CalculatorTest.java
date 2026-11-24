/*
    ti5x calculator emulator -- engine regression tests

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalculatorTest {

  @Test
  void builtinTestsPass() {
    /* same as keying Op 99 on the calculator: 44 tests must pass */
    final TestHost Host = new TestHost();
    final Calculator Engine = Host.NewCalculator();
    assertEquals(44, Engine.RunTests());
    assertTrue(Host.Errors.isEmpty(), Host.Errors.toString());
  }

  @Test
  void op99ShowsTestCount() {
    final TestHost Host = new TestHost();
    final State Calc = Host.NewCalculator().Calc;
    Calc.SpecialOp(99, false);
    assertEquals("44.", Calc.CurDisplay.trim());
  }
}
