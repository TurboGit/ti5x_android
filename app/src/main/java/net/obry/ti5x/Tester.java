/*
    The calculation state, number entry and programs.

    Copyright 2015-2018 Pascal Obry <pascal@obry.net>.

    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.obry.ti5x;

class Tester {
  private State Calc;

  private final static String SMALL = "1.-99";
  private final static String ERROR = "9.9999999 99";
  private final static String mERROR = "-9.9999999 99";

  // helpers

  private void SetX(double v) {
    Calc.X.set(v);
    Calc.SetX(Calc.X, false);
  }

  private void SetT(double v) {
    Calc.T.set(v);
  }

  private void Clear() {
    Calc.ClearMemories();
    Calc.InvState = false;
    Calc.CurState = State.ResultState;
    Calc.CurFormat = State.FORMAT_FIXED;
    Calc.CurNrDecimals = -1;
    Calc.CurAng = State.ANG_DEG;
    Calc.OpStackNext = 0;
    Calc.PreviousOp = -1;
    Calc.ResetEntry();

  }

  private void exp(double y, double x, boolean invstate) {
    SetX(y);
    Calc.InvState = invstate;
    Calc.Operator(State.STACKOP_EXP);
    Calc.InvState = false;
    SetX(x);
    Calc.Equals();
  }

  private boolean check(String display, boolean error) {
    boolean inError = Calc.InErrorState();
    if (inError) {
      Calc.CurState = State.ResultState;
      State.inError = false;
    }

    return Calc.CurDisplay.equals(display) && inError == error;
  }

  private boolean Test_1() {
    // commit: bccc9bc
    Clear();

    SetX(90);
    Calc.Cos();

    return Calc.X.getSignum() == 0;
  }

  private boolean Test_2() {
    // commit: a90fb0f
    Clear();

    Calc.Memory[1].set(1);
    Calc.ClearAll();
    Calc.Digit('1');
    Calc.EnterExponent();
    Calc.InvState = true;
    Calc.EnterExponent();
    Calc.Digit('2');

    return check("12", false);
  }

  private boolean Test_3() {
    // commit: 9c74ebd
    Clear();

    Calc.ClearAll();
    Calc.Digit('1');
    Calc.EnterExponent();
    Calc.Digit('2');
    Calc.InvState = true;
    Calc.EnterExponent();
    Calc.Digit('3');

    if (!check("13 02", false))
      return false;

    Calc.Equals();
    return check("1300.", false);
  }

  private boolean Test_4() {
    // commit: 51e6436
    Clear();

    Calc.Memory[0].set(0);
    Calc.Memory[1].set(20.1);
    Calc.SpecialOp(01, true);
    Calc.Memory[1].set(20.9);
    Calc.SpecialOp(01, true);
    Calc.Memory[1].set(-20.9);
    Calc.SpecialOp(21, true);

    return Calc.Memory[0].get() == 2;
  }

  private boolean Test_5() {
    // from TI-59 book
    Clear();

    SetX(30.1348);
    Calc.D_MS();

    if (!check("30.23", false))
      return false;

    Calc.InvState = true;
    Calc.D_MS();
    Calc.InvState = false;

    if (!check("30.1348", false))
      return false;

    SetX(-30.1348);
    Calc.D_MS();

    if (!check("-30.23", false))
      return false;

    Calc.InvState = true;
    Calc.D_MS();

    return check("-30.1348", false);
  }

  private boolean Test_6() {
    // from TI-59 book
    Clear();

    SetX(30);
    SetT(5);
    Calc.Polar();

    if (!check("2.5", false))
      return false;

    Calc.SwapT();

    if (!check("4.330127019", false))
      return false;

    Calc.SwapT();

    Calc.InvState = true;
    Calc.Polar();

    if (!check("30.", false))
      return false;

    Calc.SwapT();

    if (!check("5.", false))
      return false;

    Calc.SetAngMode(State.ANG_RAD);
    SetX(4);
    SetT(3);
    Calc.InvState = true;
    Calc.Polar();

    if (!check("0.927295218", false))
      return false;

    Calc.SwapT();

    if (!check("5.", false))
      return false;

    //  check result in proper quadrant

    Calc.SetAngMode(State.ANG_DEG);
    Calc.InvState = false;

    SetT(1);
    SetX(-178.3044464);
    Calc.Polar();

    if (!check("-.0295886738", false))
      return false;

    //

    Calc.InvState = true;
    Calc.Polar();

    if (!check("181.6955536", false))
      return false;

    Calc.SwapT();

    if (!check("1.", false))
      return false;

    //

    Calc.InvState = false;
    SetT(1);
    SetX(180);
    Calc.Polar();

    if (!check("0.", false))
      return false;

    Calc.SwapT();

    if (!check("-1.", false))
      return false;

    //

    SetT(1);
    SetX(270);
    Calc.Polar();

    if (!check("-1.", false))
      return false;

    Calc.SwapT();

    return check("0.", false);
  }

  private boolean Test_7() {
    // from TI-59 book
    Clear();

    SetX(96);
    Calc.StatsSum();
    SetX(81);
    Calc.StatsSum();
    SetX(97);
    Calc.StatsSum();
    Calc.InvState = true;
    SetX(97);
    Calc.StatsSum();
    Calc.InvState = false;
    SetX(87);
    Calc.StatsSum();
    SetX(70);
    Calc.StatsSum();
    SetX(93);
    Calc.StatsSum();
    SetX(77);
    Calc.StatsSum();

    Calc.StatsResult();

    if (!check("84.", false))
      return false;

    Calc.InvState = true;
    Calc.StatsResult();

    if (!check("9.879271228", false))
      return false;

    Calc.InvState = false;
    Calc.SpecialOp(11, false);

    return check("81.33333333", false) && Calc.Memory[1].get() == 504;
  }

  private boolean Test_8() {
    // from TI-59 book
    Clear();

    SetX(7);
    Calc.SwapT();
    SetX(99);
    Calc.StatsSum();
    SetX(12);
    Calc.SwapT();
    SetX(152);
    Calc.StatsSum();
    SetX(3);
    Calc.SwapT();
    SetX(81);
    Calc.StatsSum();
    SetX(5);
    Calc.SwapT();
    SetX(98);
    Calc.StatsSum();
    SetX(22);
    Calc.SwapT();
    SetX(151);
    Calc.StatsSum();
    Calc.InvState = true;
    SetX(22);
    Calc.SwapT();
    SetX(151);
    Calc.StatsSum();
    Calc.InvState = false;
    SetX(11);
    Calc.SwapT();
    SetX(151);
    Calc.StatsSum();
    SetX(8);
    Calc.SwapT();
    SetX(112);
    Calc.StatsSum();


    SetX(200);
    Calc.SpecialOp(15, false);

    if (!check("17.81578947", false))
      return false;

    SetX(15);
    Calc.SpecialOp(14, false);

    if (!check("176.5561798", false))
      return false;

    Calc.SpecialOp(12, false);

    if (!check("51.66853933", false))
      return false;

    Calc.SwapT();

    return check("8.325842697", false);
  }

  private boolean Test_9() {
    Clear();

    SetX(2);
    Calc.Reciprocal();

    if (!check("0.5", false))
      return false;

    SetX(0);
    Calc.Reciprocal();

    return check(ERROR, true);
  }

  private boolean Test_10() {
    Clear();

    SetX(4);
    Calc.Sqrt();

    if (!check("2.", false))
      return false;

    SetX(-4);
    Calc.Sqrt();

    return check("2.", true);
  }

  private boolean Test_11() {
    Clear();

    SetX(9.258);

    Calc.SetDisplayMode(State.FORMAT_FIXED, 4);
    if (!check("9.2580", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 3);
    if (!check("9.258", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 2);
    if (!check("9.26", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 1);
    if (!check("9.3", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);
    if (!check("9", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, -1);

    return check("9.258", false);
  }

  private boolean Test_12() {
    Clear();

    Calc.Digit('3');
    Calc.Operator(State.STACKOP_EXP);
    Calc.Digit('7');
    Calc.Equals();

    if (!check("2187.", false))
      return false;

    Calc.InvState = true;
    Calc.Operator(State.STACKOP_EXP);
    Calc.InvState = false;
    Calc.Digit('6');
    Calc.Equals();

    return check("3.602810866", false);
  }

  private boolean Test_13() {
    Clear();

    SetX(85.23);
    Calc.EnterExponent();
    Calc.Digit('2');
    Calc.Digit('2');
    Calc.Equals();

    if (!check("8.523 23", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_ENG, -1);

    if (!check("852.3 21", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, -1);

    Calc.DecimalPoint();
    Calc.Digit('1');
    Calc.Digit('2');
    Calc.Digit('3');
    Calc.Digit('4');
    Calc.Digit('5');
    Calc.Digit('6');
    Calc.EnterExponent();
    Calc.Digit('1');
    Calc.Digit('2');
    Calc.ChangeSign();
    Calc.Equals();

    // validated in Ti59
    if (!check("1.23456-13", false))
      return false;

    Calc.DecimalPoint();
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('4');
    Calc.Digit('3');
    Calc.Digit('2');
    Calc.Digit('1');
    Calc.ChangeSign();
    Calc.EnterExponent();
    Calc.Digit('1');
    Calc.Digit('2');
    Calc.ChangeSign();
    Calc.Equals();

    return check("-9.94321-13", false);
  }

  private boolean Test_14() {
    // commit: 6c9cf8f
    Clear();

    SetX(0);
    Calc.Log();

    if (!check(mERROR, true))
      return false;

    SetX(-9);
    Calc.Ln();

    return check("2.197224577", true);
  }

  private boolean Test_15() {
    // commit: 9c9aa2d
    Clear();

    SetX(0);
    Calc.MemoryOp(State.MEMOP_STO, 14, false);

    SetX(14.1);
    Calc.MemoryOp(State.MEMOP_STO, 00, false);

    SetX(1);
    Calc.MemoryOp(State.MEMOP_ADD, 00, true);

    SetX(14.5);
    Calc.MemoryOp(State.MEMOP_STO, 00, false);

    SetX(1);
    Calc.MemoryOp(State.MEMOP_ADD, 00, true);

    SetX(14.9);
    Calc.MemoryOp(State.MEMOP_STO, 00, false);

    SetX(1);
    Calc.MemoryOp(State.MEMOP_ADD, 00, true);

    Calc.MemoryOp(State.MEMOP_RCL, 14, false);

    return check("3.", false);
  }

  private boolean Test_16() {
    // commit: 194c46a
    Clear();

    Calc.Digit('1');
    Calc.Operator(State.STACKOP_ADD);
    Calc.PreviousOp = 85; // 85 is add button
    Calc.Equals();

    return check("1.", true);
  }

  private boolean Test_17() {
    Clear();

    SetX(0.9988776655);

    if (!check(".9988776655", false))
      return false;

    SetX(-0.9988776655);

    if (!check("-.9988776655", false))
      return false;

    SetX(0.9988776650);

    if (!check("0.998877665", false))
      return false;

    Calc.Digit('0');
    Calc.DecimalPoint();
    Calc.Digit('2');
    Calc.Digit('2');
    Calc.Digit('4');
    Calc.Digit('4');
    Calc.Digit('7');
    Calc.Digit('7');
    Calc.Digit('8');
    Calc.Digit('8');
    Calc.EnterExponent();
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Equals();

    if (!check("2.2447788 09", false))
      return false;

    Calc.InvState = true;
    Calc.EnterExponent();

    if (!check("2244778800.", false))
      return false;

    Calc.InvState = false;

    Calc.Digit('0');
    Calc.DecimalPoint();
    Calc.Digit('2');
    Calc.Digit('2');
    Calc.Digit('4');
    Calc.Digit('4');
    Calc.Digit('7');
    Calc.Digit('7');
    Calc.Digit('8');
    Calc.Digit('8');
    Calc.EnterExponent();
    Calc.Digit('1');
    Calc.Digit('2');
    Calc.Equals();

    if (!check("2.2447788 11", false))
      return false;

    Calc.InvState = true;
    Calc.EnterExponent();

    return check("2.2447788 11", false);
  }

  private boolean Test_18() {
    //  from TI-59 book
    Clear();

    exp(2.36, -.23, false);
    if (!check(".8207865654", false))
      return false;

    exp(.8207865654, 3, true);
    if (!check(".9362893421", false))
      return false;

    //     0**0 -> 1
    exp(0, 0, false);
    if (!check("1.", false))
      return false;

    // inv 0**0 -> 1 (flashing)
    exp(0, 0, true);
    if (!check("1.", true))
      return false;

    //     0^-x -> 9.999999 99 (flashing)
    exp(0, -2, false);
    if (!check(ERROR, true))
      return false;

    // inv 0^-x -> 9.999999 99 (flashing)
    exp(0, -2, true);
    if (!check(ERROR, true))
      return false;

    //     0**x -> 0
    exp(0, 2, false);
    if (!check("0.", false))
      return false;

    // inv 0**x -> 0
    exp(0, 6, true);
    if (!check("0.", false))
      return false;

    //     1**0 -> 1
    exp(1, 0, false);
    if (!check("1.", false))
      return false;

    // inv 1**0 -> 1 (flashing)
    exp(1, 0, true);
    if (!check("1.", true))
      return false;

    //     y**0 -> 1
    exp(9, 0, false);
    if (!check("1.", false))
      return false;

    // inv y**0 -> 9.99999 99 (flashing)
    exp(5, 0, true);
    if (!check(ERROR, true))
      return false;

    //     -1**0 -> 1 (flashing)
    exp(-1, 0, false);
    if (!check("1.", true))
      return false;

    // inv -1**0 -> 1 (flashing)
    exp(-1, 0, true);
    if (!check("1.", true))
      return false;

    //     -y**0 -> 1 (flashing)
    exp(-9, 0, false);
    if (!check("1.", true))
      return false;

    // inv -y**0 -> 9.99999 99 (flashing)
    exp(-2, 0, true);
    if (!check(ERROR, true))
      return false;

    //     -y**-x -> y**-x (flashing)
    exp(-8, -2, false);
    if (!check("64.", true))
      return false;

    // inv -y**-x -> inv y**-x (flashing)
    exp(-8, -2, true);
    return check(".3535533906", true);
  }

  private boolean Test_19() {
    Clear();

    Calc.Digit('9');
    Calc.DecimalPoint();
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.EnterExponent();
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Equals();

    if (!check("9.9999999 99", false))
      return false;

    Calc.Operator(State.STACKOP_MUL);
    SetX(2);
    Calc.Equals();

    if (!check(ERROR, true))
      return false;

    Clear();

    Calc.Digit('9');
    Calc.DecimalPoint();
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.EnterExponent();
    Calc.Digit('9');
    Calc.Digit('9');
    Calc.Equals();
    Calc.ChangeSign();

    Calc.Operator(State.STACKOP_MUL);
    SetX(2);
    Calc.Equals();

    return check(mERROR, true);
  }

  private boolean Test_20() {
    // verified on real TI-59
    Clear();

    Calc.Digit('1');
    Calc.EnterExponent();
    Calc.InvState = true;
    Calc.EnterExponent();
    Calc.Equals();

    return check("1.", false);
  }

  private boolean Test_21() {
    // verified on real TI-59
    Clear();

    Calc.SetDisplayMode(State.FORMAT_FIXED, 3);
    Calc.Digit('9');
    Calc.ChangeSign();
    Calc.InvState = true;
    Calc.Log();

    return check("0.000", false);
  }

  private boolean Test_22() {
    // verified on real TI-59
    Clear();

    Calc.Digit('9');
    Calc.EnterExponent();
    Calc.Digit('9');
    Calc.Equals();

    if (!check("9. 09", false))
      return false;

    Calc.ClearAll();

    if (!check("0", false))
      return false;

    Calc.Equals();
    if (!check("0.", false))
      return false;

    Calc.Digit('8');
    Calc.EnterExponent();
    Calc.Digit('8');
    Calc.DecimalPoint();

    if (!check("8. 08", false))
      return false;

    Calc.Digit('3');

    if (!check("8.3 08", false))
      return false;

    Calc.Digit('6');
    Calc.EnterExponent();
    Calc.Digit('2');

    if (!check("8.36 82", false))
      return false;

    Calc.Equals();
    return check("8.36 82", false);
  }

  private boolean Test_23() {
    // verified on real TI-59
    Clear();

    Calc.SetDisplayMode(State.FORMAT_ENG, -1);

    if (!check("0. 00", false))
      return false;

    Calc.Digit('2');

    if (!check("2", false))
      return false;

    Calc.Equals();
    if (!check("2. 00", false))
      return false;

    Calc.InvState = true;
    Calc.EnterExponent();
    Calc.InvState = false;
    Calc.Equals();

    return check("2. 00", false);
  }

  private boolean Test_24() {
    Clear();

    SetX(-3);
    Calc.Ln();
    Calc.Operator(State.STACKOP_DIV);
    SetX(2);
    Calc.Equals();

    return check(".5493061443", true);
  }

  private boolean Test_25() {
    // full test validated on real Ti59
    Clear();

    Calc.SetDisplayMode(State.FORMAT_ENG, -1);

    SetX(.123456e-12);
    Calc.Equals();

    if (!check("123.456-15", false))
      return false;

    SetX(-.654321e-12);
    Calc.Equals();

    if (!check("-654.321-15", false))
      return false;

    SetX(.123456e12);
    Calc.Equals();

    if (!check("123.456 09", false))
      return false;

    SetX(5.2);
    Calc.Equals();

    if (!check("5.2 00", false))
      return false;

    SetX(5.2e12);
    Calc.Equals();

    if (!check("5.2 12", false))
      return false;

    // enter a number, adding exponent must not change format

    Calc.DecimalPoint();
    Calc.Digit('1');
    Calc.Digit('2');
    Calc.Digit('3');
    Calc.Digit('4');
    Calc.Digit('5');
    Calc.Digit('6');
    Calc.EnterExponent();
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.ChangeSign();
    Calc.Equals();

    return check("12.3456-12", false);
  }

  private boolean Test_26() {
    Clear();

    Calc.Digit('2');
    Calc.SetFlag(1, false, true);
    Calc.Digit('3');
    Calc.Equals();

    return check("3.", false);
  }

  private boolean Test_27() {
    Clear();

    Calc.Pi();
    Calc.Operator(State.STACKOP_DIV);
    SetX(100);
    Calc.Equals();

    Calc.SetDisplayMode(State.FORMAT_FIXED, 4);
    if (!check("0.0314", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_ENG, 4);

    return check("31.4159-03", false);
  }

  private boolean Test_28() {
    Clear();

    Calc.Pi();
    Calc.Operator(State.STACKOP_DIV);
    SetX(100);
    Calc.Equals();

    Calc.SetDisplayMode(State.FORMAT_ENG, -1);
    if (!check("31.415927-03", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_ENG, 4);

    if (!check("31.4159-03", false))
      return false;

    Calc.InvState = false;
    Calc.SetDisplayMode(State.FORMAT_ENG, -1);

    return check("31.415927-03", false);
  }

  private boolean Test_29() {
    Clear();

    SetX(8);
    Calc.Square();

    if (!check("64.", false))
      return false;

    SetX(1e-98);
    Calc.Square();

    return check(SMALL, true);
  }

  private boolean Test_30() {
    Clear();

    // 101
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Digit('1');
    Calc.Operator(State.STACKOP_SUB);
    Calc.Digit('.');
    Calc.Digit('5');
    Calc.Equals();
    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);

    if (!check("101", false))
      return false;

    // 102
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Digit('2');
    Calc.Operator(State.STACKOP_SUB);
    Calc.Digit('.');
    Calc.Digit('5');
    Calc.Equals();
    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);

    if (!check("102", false))
      return false;

    // 103
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Digit('3');
    Calc.Operator(State.STACKOP_SUB);
    Calc.Digit('.');
    Calc.Digit('5');
    Calc.Equals();
    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);

    if (!check("103", false))
      return false;

    // 104
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Digit('4');
    Calc.Operator(State.STACKOP_SUB);
    Calc.Digit('.');
    Calc.Digit('5');
    Calc.Equals();
    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);
    return check("104", false);
  }

  private boolean Test_31() {
    //  tested on real TI-59 : "2 * =" and "2 * )" should go in error state

    Clear();

    Calc.Digit('2');
    Calc.Operator(State.STACKOP_MUL);
    Calc.PreviousOp = 65; // 65 is mult button
    Calc.Equals();

    if (!check("2.", true))
      return false;

    Clear();

    Calc.Digit('4');
    Calc.Operator(State.STACKOP_MUL);
    Calc.PreviousOp = 65; // 65 is mult button
    Calc.RParen();

    return check("4.", true);
  }

  private boolean Test_32() {
    //  test % & INV % extensions

    Clear();

    SetX(5);
    Calc.Operator(State.STACKOP_MUL);
    SetX(10);
    Calc.Percent();
    Calc.Equals();

    if (!check("2.5", false))
      return false;

    Clear();

    SetX(5);
    Calc.Operator(State.STACKOP_ADD);
    SetX(10);
    Calc.Percent();
    Calc.Equals();

    if (!check("5.5", false))
      return false;

    Clear();

    SetX(5);
    Calc.Operator(State.STACKOP_MUL);
    SetX(10);
    Calc.InvState = true;
    Calc.Percent();
    Calc.Equals();

    if (!check("5000.", false))
      return false;

    Clear();

    SetX(5);
    Calc.Operator(State.STACKOP_ADD);
    SetX(10);
    Calc.InvState = true;
    Calc.Percent();
    Calc.Equals();

    return check("1005.", false);
  }

  private boolean Test_33() {
    // ensure that D.MS is done with the proper displayed precision
    Clear();

    Calc.Digit('1');
    Calc.Digit('.');
    Calc.Digit('0');
    Calc.Digit('0');
    Calc.Digit('3');

    Calc.D_MS();

    Calc.Operator(State.STACKOP_ADD);

    Calc.Digit('1');
    Calc.Digit('.');
    Calc.Digit('0');
    Calc.Digit('0');
    Calc.Digit('3');

    Calc.D_MS();

    Calc.Equals();

    Calc.InvState = true;
    Calc.D_MS();
    Calc.InvState = false;

    if (!check("2.01", false))
      return false;

    // more tests checked on a real TI-59

    SetX (1.987654);
    Calc.SetDisplayMode(Calc.FORMAT_FIXED, 5);
    Calc.D_MS();

    if (!check("2.65458", false))
      return false;

    Calc.InvState = true;
    Calc.D_MS();
    Calc.InvState = false;

    if (!check("2.39165", false))
      return false;

    Calc.D_MS();

    if (!check("2.65458", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);

    SetX (1.987654);

    Calc.SetDisplayMode(Calc.FORMAT_FIXED, 2);
    Calc.D_MS();

    if (!check("2.65", false))
      return false;

    Calc.InvState = true;
    Calc.D_MS();
    Calc.InvState = false;

    if (!check("2.39", false))
      return false;

    Calc.D_MS();

    if (!check("2.65", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 0);
    return true;
  }

  private boolean Test_34() {
    Clear();

    Calc.SetDisplayMode(State.FORMAT_FIXED, 8);

    if (!check("0.00000000", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_ENG, 8);

    if (!check("0.0000000 00", false))
      return false;

    SetX(9600);
    Calc.Operator(State.STACKOP_DIV);
    SetX(90);
    Calc.Equals();

    if (!check("106.66667 00", false))
      return false;

    return true;
  }

  private boolean Test_35() {
    Clear();

    Calc.SetDisplayMode(State.FORMAT_ENG, 8);

    if (!check("0.0000000 00", false))
      return false;

    SetX(.123);
    Calc.Equals();

    if (!check("123.00000-03", false))
      return false;

    SetX(.123);
    Calc.ChangeSign();
    Calc.Equals();

    if (!check("-123.00000-03", false))
      return false;

    SetX(.12345);
    Calc.Equals();

    if (!check("123.45000-03", false))
      return false;

    return true;
  }
  private boolean Test_36() {
    // all values validated on a real Ti59
    // commit Fri May 3 19:13:43 2024 +0200
    Clear();

    SetX(999999999);
    Calc.EnterExponent();
    Calc.Equals();

    if (!check("1. 09", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, 4);
    Calc.EnterExponent();
    Calc.Equals();

    if (!check("1.0000 09", false))
      return false;

    Calc.SetDisplayMode(State.FORMAT_FIXED, -1);

    SetX(99999999);
    Calc.EnterExponent();
    Calc.Equals();

    if (!check("9.9999999 07", false))
      return false;

    return true;
  }

  private boolean Test_37() {
    // all values validated on a real Ti59
    // commit Sun May 5 20:01:35 2024 +0200
    Clear();

    SetX(23456789);
    Calc.ChangeSign();
    Calc.EnterExponent();
    Calc.Digit('1');
    Calc.Equals();

    if (!check("-2.3456789 08", false))
      return false;

    Calc.Operator(State.STACKOP_SUB);
    Calc.Digit('8');
    Calc.EnterExponent();
    Calc.Digit('8');
    Calc.Equals();

    if (!check("-1.0345679 09", false))
      return false;

    return true;
  }
  private boolean Test_38() {
    // Check for MinMax extension on [INV] [-}
    Clear();

    Calc.Digit('2');
    Calc.SwapT();
    Calc.Digit('3');
    Calc.Operator(State.STACKOP_ADD);
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Digit('0');
    Calc.MinMax();
    Calc.Equals();

    if (!check("5.", false))
      return false;

    Calc.Digit('2');
    Calc.SwapT();
    Calc.Digit('1');
    Calc.Digit('0');
    Calc.Digit('0');
    Calc.MinMax();
    Calc.Operator(State.STACKOP_ADD);
    Calc.Digit('4');
    Calc.Equals();

    if (!check("6.", false))
      return false;

    return true;
  }

  int Run() {
    Calc = Global.Calc;
    int Total = 0;

    if (!Test_1()) return -1;
    Total++;
    if (!Test_2()) return -2;
    Total++;
    if (!Test_3()) return -3;
    Total++;
    if (!Test_4()) return -4;
    Total++;
    if (!Test_5()) return -5;
    Total++;
    if (!Test_6()) return -6;
    Total++;
    if (!Test_7()) return -7;
    Total++;
    if (!Test_8()) return -8;
    Total++;
    if (!Test_9()) return -9;
    Total++;
    if (!Test_10()) return -10;
    Total++;
    if (!Test_11()) return -11;
    Total++;
    if (!Test_12()) return -12;
    Total++;
    if (!Test_13()) return -13;
    Total++;
    if (!Test_14()) return -14;
    Total++;
    if (!Test_15()) return -15;
    Total++;
    if (!Test_16()) return -16;
    Total++;
    if (!Test_17()) return -17;
    Total++;
    if (!Test_18()) return -18;
    Total++;
    if (!Test_19()) return -19;
    Total++;
    if (!Test_20()) return -20;
    Total++;
    if (!Test_21()) return -21;
    Total++;
    if (!Test_22()) return -22;
    Total++;
    if (!Test_23()) return -23;
    Total++;
    if (!Test_24()) return -24;
    Total++;
    if (!Test_25()) return -25;
    Total++;
    if (!Test_26()) return -26;
    Total++;
    if (!Test_27()) return -27;
    Total++;
    if (!Test_28()) return -28;
    Total++;
    if (!Test_29()) return -29;
    Total++;
    if (!Test_30()) return -30;
    Total++;
    if (!Test_31()) return -31;
    Total++;
    if (!Test_32()) return -32;
    Total++;
    if (!Test_33()) return -33;
    Total++;
    if (!Test_34()) return -34;
    Total++;
    if (!Test_35()) return -35;
    Total++;
    if (!Test_36()) return -36;
    Total++;
    if (!Test_37()) return -37;
    Total++;
    if (!Test_38()) return -38;
    Total++;
    Clear();

    return Total;
  }
}
