/*
    ti5x calculator emulator -- key names for key scripts

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KeyNames {
  /* Translates a key script, a sequence of key names separated by blanks
     (e.g. "rst r/s", "5 sto 01", "2nd pgm 01 sbr 012" or "op 99"), into
     the key codes to pass to Keypad.Press. Names are case insensitive and
     use the assembler spellings. A second function name (log, pgm, op,
     lbl...) includes the 2nd key press, an explicit 2nd before it is
     accepted too. A token made of digits and decimal points stands for
     those keys, one per character. */

  private static final int SECOND = 21;

  private static final Map<String, Integer> Codes = new HashMap<>();
  /* key code as stored in a program: for a second function, the key code
     has 5 added to its column (mod 10). */

  private static void Key(int Code, String... Names) {
    for (String Name : Names) {
      Codes.put(Name, Code);
    }
  }

  static {
    /* first functions, as printed on the keys */
    Key(11, "a");
    Key(12, "b");
    Key(13, "c");
    Key(14, "d");
    Key(15, "e");
    Key(21, "2nd");
    Key(22, "inv");
    Key(23, "lnx", "ln");
    Key(24, "ce");
    Key(25, "clr");
    Key(31, "lrn");
    Key(32, "x<>t", "x⇌t", "x/t", "x-t", "x%t");
    Key(33, "x2", "x²", "sqr");
    Key(34, "sqrt", "√x");
    Key(35, "1/x");
    Key(41, "sst");
    Key(42, "sto");
    Key(43, "rcl");
    Key(44, "sum");
    Key(45, "y**x", "yx", "yˣ", "pow", "**");
    Key(51, "bst");
    Key(52, "ee");
    Key(53, "(");
    Key(54, ")");
    Key(55, "÷", "/");
    Key(61, "gto");
    Key(62, "7");
    Key(63, "8");
    Key(64, "9");
    Key(65, "×", "*");
    Key(71, "sbr");
    Key(72, "4");
    Key(73, "5");
    Key(74, "6");
    Key(75, "-");
    Key(81, "rst");
    Key(82, "1");
    Key(83, "2");
    Key(84, "3");
    Key(85, "+");
    Key(91, "r/s", "rs");
    Key(92, "0");
    Key(93, ".");
    Key(94, "+/-");
    Key(95, "=");

    /* second functions */
    Key(16, "a'", "a´");
    Key(17, "b'", "b´");
    Key(18, "c'", "c´");
    Key(19, "d'", "d´");
    Key(10, "e'", "e´");
    Key(28, "log");
    Key(29, "cp");
    Key(20, "%"); /* extension */
    Key(36, "pgm");
    Key(37, "p->r", "p→r", "p/r");
    Key(38, "sin");
    Key(39, "cos");
    Key(30, "tan");
    Key(46, "ins");
    Key(47, "cms");
    Key(48, "exc");
    Key(49, "prd");
    Key(40, "ind");
    Key(56, "del");
    Key(57, "eng");
    Key(58, "fix");
    Key(59, "int");
    Key(50, "|x|", "abs", "ixi");
    Key(66, "pause");
    Key(67, "x=t", "eq");
    Key(68, "nop");
    Key(69, "op");
    Key(60, "deg");
    Key(76, "lbl");
    Key(77, "x≥t", "x>=t", "ge");
    Key(78, "sigma", "∑+", "s+", "sta");
    Key(79, "mean", "mean(x)");
    Key(70, "rad");
    Key(86, "stflg", "stf");
    Key(87, "ifflg", "iff");
    Key(88, "d.ms", "dms");
    Key(89, "pi", "π");
    Key(80, "grad", "grd");
    Key(96, "write", "wrt");
    Key(97, "dsz");
    Key(98, "adv");
    Key(99, "prt");
    Key(90, "list", "lst");
  }

  private KeyNames() {
  }

  public static boolean IsSecondFunction
     (
        int Code
     ) {
    final int Col = Code % 10;
    return Col == 0 || Col > 5;
  }

  public static int BaseKey
     (
        int Code
     ) {
    /* the key to press (after 2nd) for a second function code */
    return IsSecondFunction(Code) ? Code / 10 * 10 + (Code % 10 + 5) % 10 : Code;
  }

  public static Integer Code
     (
        String Name
     ) {
    /* the program code of the named key function, null if unknown. */
    return Codes.get(Name.toLowerCase(Locale.US));
  }

  public static int[] Parse
     (
        String Script
     ) {
    /* returns the key presses for Script, raises IllegalArgumentException
       on an unknown key name. */
    final List<Integer> Keys = new ArrayList<>();
    boolean AfterSecond = false;
    for (String Token : Script.trim().split("\\s+")) {
      if (Token.isEmpty())
        continue;
      final Integer Code = Code(Token);
      if (Code != null) {
        if (IsSecondFunction(Code)) {
          if (!AfterSecond) {
            Keys.add(SECOND);
          }
          Keys.add(BaseKey(Code));
        } else {
          Keys.add(Code);
        }
        AfterSecond = Code == SECOND;
      } else if (Token.matches("[0-9.]+")) {
        for (char Ch : Token.toCharArray()) {
          Keys.add(Code(String.valueOf(Ch)));
        }
        AfterSecond = false;
      } else {
        throw new IllegalArgumentException("unknown key \"" + Token + "\"");
      }
    }
    final int[] Result = new int[Keys.size()];
    for (int i = 0; i < Result.length; i++) {
      Result[i] = Keys.get(i);
    }
    return Result;
  }
}
