/*
    ti5x calculator emulator -- key entry tests

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

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeypadTest {
  /* key codes as printed on the keys */
  private static final int SECOND = 21, INV = 22, LNX = 23, CLR = 25;
  private static final int LRN = 31, STO = 42, RCL = 43, OP = 69;
  private static final int DIV = 55, PLUS = 85, EQUALS = 95, RST = 81, RS = 91;
  private static final int[] DIGIT = {92, 82, 83, 84, 72, 73, 74, 62, 63, 64};

  private TestHost Host;
  private Calculator Engine;

  @BeforeEach
  void setUp() {
    Host = new TestHost();
    Engine = Host.NewCalculator();
  }

  private void Keys(int... Codes) {
    for (int Code : Codes) {
      Engine.Keys.Press(Code);
      Engine.Keys.Release(Code);
    }
  }

  private void Number(int N) {
    for (char c : Integer.toString(N).toCharArray()) {
      Keys(DIGIT[c - '0']);
    }
  }

  private String Display() {
    return Engine.Calc.CurDisplay.trim();
  }

  private void RunUntilStopped() {
    for (int i = 0; i < 10000 && Engine.Calc.TaskRunning; i++) {
      Host.RunPending(1);
    }
    assertFalse(Engine.Calc.TaskRunning, "program did not stop");
  }

  @Test
  void addition() {
    Number(2);
    Keys(PLUS);
    Number(3);
    Keys(EQUALS);
    assertEquals("5.", Display());
  }

  @Test
  void divideByZeroIsError() {
    Number(1);
    Keys(DIV);
    Number(0);
    Keys(EQUALS);
    assertTrue(Engine.Calc.InErrorState());
    Keys(CLR);
    assertFalse(Engine.Calc.InErrorState());
  }

  @Test
  void storeAndRecall() {
    Number(7);
    Keys(STO, DIGIT[0], DIGIT[5], CLR);
    assertEquals("0", Display());
    Keys(RCL, DIGIT[0], DIGIT[5]);
    assertEquals("7.", Display());
  }

  @Test
  void secondFunction() {
    /* 2nd ln x is log */
    Number(100);
    Keys(SECOND, LNX);
    assertEquals("2.", Display());
    assertFalse(Engine.Keys.AltState);
  }

  @Test
  void inverseFunction() {
    /* INV ln x is e^x */
    Number(0);
    Keys(INV, LNX);
    assertEquals("1.", Display());
    assertFalse(Engine.Calc.InvState);
  }

  @Test
  void op99RunsBuiltinTests() {
    Keys(OP, DIGIT[9], DIGIT[9]);
    assertEquals("46.", Display());
  }

  @Test
  void learnAndRunProgram() {
    /* LRN + 1 = R/S RST LRN : a counter */
    Keys(LRN, PLUS, DIGIT[1], EQUALS, RS, RST, LRN);
    assertArrayEquals
       (
          new byte[] {85, 1, 95, 91, 81, 0},
          Arrays.copyOf(Engine.Calc.Program, 6)
       );

    Keys(CLR, RST, RS);
    assertTrue(Engine.Calc.TaskRunning);
    RunUntilStopped();
    assertEquals("1.", Display());

    Keys(RS);
    RunUntilStopped();
    assertEquals("2.", Display());
  }
}
