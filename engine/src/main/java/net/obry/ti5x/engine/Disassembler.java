/*
    ti5x calculator emulator -- program disassembler

    Copyright 2011 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015-2026 Pascal Obry <pascal@obry.net>.

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

import java.util.HashMap;
import java.util.Map;

public class Disassembler {

  private static class CodeEntry {
    String sym;
    boolean modifier;
    String expect;
    String expect2;
    boolean hassym = true;
    boolean actPgm = false;
    boolean actSbr = false;
    boolean actFix = false;

    CodeEntry(String sym) {
      this.sym = sym;
    }

    CodeEntry(String sym, boolean modifier) {
      this.sym = sym;
      this.modifier = modifier;
    }
  }

  private static CodeEntry simple(String sym) {
    return new CodeEntry(sym);
  }

  private static CodeEntry regop(String op, boolean hassym) {
    CodeEntry entry = new CodeEntry(op);
    entry.expect = "reg";
    entry.hassym = hassym;
    return entry;
  }

  private static CodeEntry locop(String op) {
    CodeEntry entry = new CodeEntry(op);
    entry.expect = "loc";
    return entry;
  }

  private static final Map<Integer, CodeEntry> codes = new HashMap<>();

  static {
    codes.put(11, simple("A"));
    codes.put(12, simple("B"));
    codes.put(13, simple("C"));
    codes.put(14, simple("D"));
    codes.put(15, simple("E"));
    codes.put(16, simple("A'"));
    codes.put(17, simple("B'"));
    codes.put(18, simple("C'"));
    codes.put(19, simple("D'"));
    codes.put(10, simple("E'"));
    codes.put(22, new CodeEntry("INV", true));
    codes.put(23, simple("lnx"));
    codes.put(24, simple("CE"));
    codes.put(25, simple("CLR"));
    codes.put(27, new CodeEntry("2nd INV", true));
    codes.put(28, simple("log"));
    codes.put(29, simple("CP"));
    codes.put(20, simple("2nd CLR"));
    codes.put(21, simple("2nd"));

    codes.put(32, simple("x⇌t"));
    codes.put(33, simple("x²"));
    codes.put(34, simple("√x"));
    codes.put(35, simple("1/x"));
    CodeEntry pgm = simple("Pgm");
    pgm.actPgm = true;
    codes.put(36, pgm);
    codes.put(37, simple("P→R"));
    codes.put(38, simple("sin"));
    codes.put(39, simple("cos"));
    codes.put(30, simple("tan"));

    codes.put(42, regop("STO", true));
    codes.put(43, regop("RCL", true));
    codes.put(44, regop("SUM", true));
    codes.put(45, simple("y**x"));
    codes.put(46, simple("Ins"));
    codes.put(47, simple("CMs"));
    codes.put(48, regop("Exc", true));
    codes.put(49, regop("Prd", true));

    codes.put(51, simple("%"));
    codes.put(52, simple("EE"));
    codes.put(53, simple("("));
    codes.put(54, simple(")"));
    codes.put(55, simple("÷"));
    codes.put(56, simple("Del"));
    codes.put(57, simple("Eng"));
    CodeEntry fix = simple("Fix");
    fix.actFix = true;
    codes.put(58, fix);
    codes.put(59, simple("Int"));
    codes.put(50, simple("|x|"));

    codes.put(61, locop("GTO"));
    codes.put(62, regop("Pgm Ind", false));
    codes.put(63, regop("Exc Ind", false));
    codes.put(64, regop("Prd Ind", false));
    codes.put(65, simple("×"));
    codes.put(66, simple("Pause"));
    codes.put(67, locop("x=t"));
    codes.put(68, simple("Nop"));
    codes.put(69, regop("Op", true));
    codes.put(60, simple("Deg"));

    CodeEntry sbr = simple("SBR");
    sbr.actSbr = true;
    codes.put(71, sbr);
    codes.put(72, regop("ST*", true));
    codes.put(73, regop("RC*", true));
    codes.put(74, regop("SM*", true));
    codes.put(75, simple("-"));
    CodeEntry lbl = simple("Lbl");
    lbl.expect = "label";
    codes.put(76, lbl);
    codes.put(77, locop("x≥t"));
    codes.put(78, simple("∑+"));
    codes.put(79, simple("mean(x)"));
    codes.put(70, simple("Rad"));

    codes.put(81, simple("RST"));
    codes.put(82, regop("HIR", true));
    codes.put(83, regop("GTO Ind", false));
    codes.put(84, regop("Op Ind", false));
    codes.put(85, simple("+"));
    CodeEntry stflg = simple("Stflg");
    stflg.expect = "flag";
    codes.put(86, stflg);
    CodeEntry ifflg = simple("Ifflg");
    ifflg.expect = "flag";
    ifflg.expect2 = "loc";
    codes.put(87, ifflg);
    codes.put(88, simple("D.MS"));
    codes.put(89, simple("π"));
    codes.put(80, simple("Grad"));

    codes.put(91, simple("R/S"));
    codes.put(92, simple("INV SBR"));
    codes.put(93, simple("."));
    codes.put(94, simple("+/-"));
    codes.put(95, simple("="));
    codes.put(96, simple("Write"));
    CodeEntry dsz = simple("Dsz");
    dsz.expect = "unitreg";
    dsz.expect2 = "loc";
    codes.put(97, dsz);
    codes.put(98, simple("Adv"));
    codes.put(99, simple("Prt"));
    codes.put(90, simple("List"));
  }

  public static String disassemble(byte[] program) {
    StringBuilder sb = new StringBuilder();
    disassemble(program, sb);
    return sb.toString();
  }

  private static void finishLine(StringBuilder out, int currentLineStartPos, int instPC, StringBuilder instOpcodes) {
    int lineLength = out.length() - currentLineStartPos;
    while (lineLength < 30) {
      out.append(' ');
      lineLength++;
    }
    out.append(String.format(Constants.StdLocale, " ; %03d %s\n", instPC, instOpcodes.toString()));
  }

  public static void disassemble(byte[] program, StringBuilder out) {
    if (program == null || program.length == 0) {
      return;
    }

    int endPC = program.length;
    while (endPC > 0 && program[endPC - 1] == 0) {
      endPC--;
    }
    if (endPC == 0) {
      return;
    }

    boolean wasModifier = false;
    boolean invState = false;
    String expect = null;
    String lastcodeExpect = null;
    boolean lastWasDigit = false;
    boolean decimalSeen = false;
    boolean atLineStart = true;

    int loc = -1;
    int currentLineStartPos = 0;
    int instPC = 0;
    StringBuilder instOpcodes = new StringBuilder();

    for (int pc = 0; pc < endPC; pc++) {
      int val = program[pc] & 0xFF;

      if (lastWasDigit && !(val < 10 || (val == 93 && !decimalSeen))) {
        finishLine(out, currentLineStartPos, instPC, instOpcodes);
        atLineStart = true;
        lastWasDigit = false;
        decimalSeen = false;
      }

      if (atLineStart) {
        currentLineStartPos = out.length();
        instPC = pc;
        instOpcodes.setLength(0);
        instOpcodes.append(String.format(Constants.StdLocale, "%02d", val));
        if (val != 76) { // 76 is Lbl
          out.append("   ");
        }
        atLineStart = false;
      } else {
        instOpcodes.append(String.format(Constants.StdLocale, " %02d", val));
      }

      if (expect != null) {
        if ("loc2".equals(expect)) {
          out.append(String.format(Constants.StdLocale, "%03d", loc * 100 + val));
          expect = null;
        } else if ("loc".equals(expect)) {
          expect = null;
          if (val < 10) {
            loc = val;
            expect = "loc2";
          } else if (val == 40) {
            out.append("Ind ");
            expect = "reg";
          } else if (val == 51) {
            out.append("51");
            expect = null;
          } else {
            CodeEntry c = codes.get(val);
            if (c != null && c.sym != null) {
              out.append(c.sym);
            } else {
              out.append(String.format(Constants.StdLocale, "%02d", val));
            }
            expect = null;
          }
        } else if ("reg".equals(expect)) {
          out.append(String.format(Constants.StdLocale, "%02d", val));
          expect = null;
        } else if ("flag".equals(expect) || "unitreg".equals(expect)) {
          if (val == 40) {
            out.append("Ind ");
            expect = "reg";
          } else if ("unitreg".equals(expect) || val < 10) {
            out.append(String.format(Constants.StdLocale, "%1d", val));
            expect = null;
          } else {
            out.append(String.format(Constants.StdLocale, "%02d", val));
            expect = null;
          }
        } else if ("label".equals(expect)) {
          expect = null;
          CodeEntry c = codes.get(val);
          if (c != null && c.hassym) {
            out.append(c.sym);
          } else {
            out.append(String.format(Constants.StdLocale, "%02d", val));
          }
        }

        if (expect == null) {
          if (lastcodeExpect != null) {
            expect = lastcodeExpect;
            lastcodeExpect = null;
            out.append(" ");
          } else {
            finishLine(out, currentLineStartPos, instPC, instOpcodes);
            atLineStart = true;
          }
        }
      } else if (val < 10 || (val == 93 && !decimalSeen)) {
        // digit
        if (val < 10) {
          out.append((char) (val + '0'));
        } else {
          out.append('.');
          decimalSeen = true;
        }
        lastWasDigit = true;
      } else {
        CodeEntry code = codes.get(val);
        if (code != null) {
          out.append(code.sym);
          wasModifier = code.modifier;
          if (code.actPgm) {
            if (!invState) {
              expect = "reg";
            }
          } else if (code.actSbr) {
            if (!invState) {
              expect = "loc";
            }
          } else if (code.actFix) {
            if (!invState) {
              expect = "flag";
            }
          } else if (code.expect != null) {
            expect = code.expect;
            lastcodeExpect = code.expect2;
          }

          if (expect != null || wasModifier) {
            out.append(" ");
          } else {
            finishLine(out, currentLineStartPos, instPC, instOpcodes);
            atLineStart = true;
          }

          if (wasModifier) {
            invState = true;
          }
        } else {
          out.append(String.format(Constants.StdLocale, "%02d", val));
          finishLine(out, currentLineStartPos, instPC, instOpcodes);
          atLineStart = true;
        }

        if (!wasModifier) {
          invState = false;
        }
      }
    }

    if (lastWasDigit) {
      finishLine(out, currentLineStartPos, instPC, instOpcodes);
    }
  }
}
