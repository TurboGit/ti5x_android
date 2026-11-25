/*
    ti5x calculator emulator -- symbolic assembler

    Java port of util/assemble: reads the symbolic representation of a
    program (the same syntax as produced by the Disassembler) and returns
    the program opcodes. Also supports location markers (sequence of
    digits ending with colon) and symbolic labels (name ending with colon
    to define it, the name alone to reference it as a 3-digit address).

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Assembler {

  public static final int MaxProgramLoc = 1000; /* default program size limit */
  public static final int MaxRomLoc = 1500; /* size limit for library (ROM) programs */

  public static final class Result {
    public final byte[] Program; /* the opcodes */
    public final List<String> Errors; /* empty if assembly succeeded */
    public final List<String> Warnings; /* label checks, see CheckLabels */

    Result
       (
          byte[] Program,
          List<String> Errors,
          List<String> Warnings
       ) {
      this.Program = Program;
      this.Errors = Errors;
      this.Warnings = Warnings;
    }

    public boolean OK() {
      return Errors.isEmpty();
    }
  }

  public static Result Assemble
     (
        String Source
     ) {
    return Assemble(Source, MaxProgramLoc, false);
  }

  public static Result Assemble
     (
        String Source,
        int MaxLoc, /* MaxProgramLoc or MaxRomLoc */
        boolean CheckLabels /* report undefined and unreferenced labels as warnings */
     ) {
    final Assembler A = new Assembler(MaxLoc);
    A.Run(Source, CheckLabels);
    return new Result(A.ResolveProgram(), A.Errors, A.Warnings);
  }

  /* ---------------------------------------------------------------- symbols */

  private static final int ACT_NONE = 0;
  private static final int ACT_ALT = 1; /* 2nd */
  private static final int ACT_FIX = 2; /* Fix, merged with a preceding Inv */
  private static final int ACT_SBR = 3; /* SBR, Inv SBR is Rtn */

  private static final String EXPECT_REG = "reg";
  private static final String EXPECT_NOINDREG = "noindreg";
  private static final String EXPECT_UNITREG = "unitreg";
  private static final String EXPECT_LOC = "loc";
  private static final String EXPECT_LABEL = "label";
  private static final String EXPECT_FLAG = "flag";

  private static final class Sym {
    Integer Op, AltOp, IndOp, AltIndOp;
    String Expect, Expect2;
    int Act = ACT_NONE;
    boolean Modifier, IsInd;

    Sym Copy() {
      /* copy for the pending instruction, the second operand becomes the expected one */
      final Sym S = new Sym();
      S.Op = Op;
      S.AltOp = AltOp;
      S.IndOp = IndOp;
      S.AltIndOp = AltIndOp;
      S.Act = Act;
      S.Modifier = Modifier;
      S.IsInd = IsInd;
      S.Expect = Expect2;
      return S;
    }
  }

  private static final Map<String, Sym> Symbols = new HashMap<>();

  private static Sym Simple(int Op) {
    return Simple2(Op, Op);
  }

  private static Sym Simple2(int Op, int AltOp) {
    final Sym S = new Sym();
    S.Op = Op;
    S.AltOp = AltOp;
    return S;
  }

  private static Sym RegOp(int BaseCode, int IndCode) {
    return RegOp2(BaseCode, BaseCode, IndCode, IndCode);
  }

  private static Sym RegOp2(int BaseCode, int AltCode, int IndCode, int AltIndCode) {
    final Sym S = Simple2(BaseCode, AltCode);
    S.IndOp = IndCode;
    S.AltIndOp = AltIndCode;
    S.Expect = EXPECT_REG;
    return S;
  }

  private static Sym Expecting(int Op, String Expect) {
    final Sym S = Simple(Op);
    S.Expect = Expect;
    return S;
  }

  private static Sym TransferOp(int BaseCode, Integer IndCode) {
    final Sym S = Expecting(BaseCode, EXPECT_LOC);
    if (IndCode != null) {
      S.IndOp = IndCode;
      S.AltIndOp = IndCode;
    }
    return S;
  }

  private static void Def(String Name, Sym S) {
    Symbols.put(Name, S);
  }

  private static void Synonym(String Name, String Of) {
    Symbols.put(Name, Symbols.get(Of));
  }

  static {
    /* note no entries for digits, these are handled specially */
    Def("a", Simple2(11, 16));
    Def("b", Simple2(12, 17));
    Def("c", Simple2(13, 18));
    Def("d", Simple2(14, 19));
    Def("e", Simple2(15, 10));
    Def("a'", Simple(16));
    Def("b'", Simple(17));
    Def("c'", Simple(18));
    Def("d'", Simple(19));
    Def("e'", Simple(10));
    {
      final Sym S = Simple(21);
      S.Act = ACT_ALT;
      S.Modifier = true;
      Def("2nd", S);
    }
    Def("inv", Simple2(22, 27));
    Def("lnx", Simple2(23, 28));
    Def("log", Simple(28));
    Def("ce", Simple2(24, 29));
    Def("cp", Simple(29));
    Def("clr", Simple2(25, 20));
    Def("%", Simple(51)); /* extension: % and INV % */
    /* no "lrn" */
    Def("pgm", RegOp(36, 62));
    Def("pg*", Expecting(62, EXPECT_REG));
    Def("x<>t", Simple2(32, 37));
    Def("p->r", Simple(37));
    Def("x2", Simple2(33, 38));
    Def("sin", Simple(38));
    Def("sqrt", Simple2(34, 39));
    Def("cos", Simple(39));
    Def("1/x", Simple2(35, 30));
    Def("tan", Simple(30));
    /* no "sst" */
    Def("ins", Simple(46));
    Def("sto", RegOp(42, 72));
    Def("st*", Expecting(72, EXPECT_REG));
    Def("cms", Simple(47));
    Def("rcl", RegOp2(43, 48, 73, 63));
    Def("rc*", Expecting(73, EXPECT_REG));
    Def("exc", RegOp(48, 63));
    Def("ex*", Expecting(63, EXPECT_REG));
    Def("sum", RegOp2(44, 49, 74, 64));
    Def("sm*", Expecting(74, EXPECT_REG));
    Def("prd", RegOp(49, 64));
    Def("pd*", Expecting(64, EXPECT_REG));
    Def("y**x", Simple(45));
    {
      final Sym S = new Sym();
      S.Modifier = true;
      S.IsInd = true; /* special handling by parser */
      Def("ind", S);
    }
    /* no "bst" */
    Def("del", Simple(56));
    Def("ee", Simple2(52, 57));
    Def("eng", Simple(57));
    Def("(", Simple2(53, 58));
    {
      final Sym S = Simple(58);
      S.Act = ACT_FIX;
      Def("fix", S);
    }
    Def(")", Simple2(54, 59));
    Def("int", Simple(59));
    Def("÷", Simple2(55, 50));
    Def("|x|", Simple(50));
    Def("gto", TransferOp(61, 83));
    Def("pause", Simple(66));
    Def("x=t", TransferOp(67, null));
    Def("nop", Simple(68));
    Def("hir", Expecting(82, EXPECT_NOINDREG));
    Def("op", RegOp(69, 84));
    Def("op*", Expecting(84, EXPECT_REG));
    Def("×", Simple2(65, 60));
    Def("deg", Simple(60));
    {
      final Sym S = Simple(71);
      S.Act = ACT_SBR;
      Def("sbr", S);
    }
    Def("rtn", Simple(92));
    Def("lbl", Expecting(76, EXPECT_LABEL));
    Def("x≥t", TransferOp(77, null));
    Def("sigma", Simple(78));
    Def("mean", Simple(79));
    Def("-", Simple2(75, 70));
    Def("rad", Simple(70));
    Def("rst", Simple(81));
    Def("stflg", Expecting(86, EXPECT_FLAG));
    {
      final Sym S = Expecting(87, EXPECT_FLAG);
      S.Expect2 = EXPECT_LOC;
      Def("ifflg", S);
    }
    Def("d.ms", Simple(88));
    Def("pi", Simple(89));
    Def("+", Simple2(85, 80));
    Def("grad", Simple(80));
    Def("r/s", Simple2(91, 96));
    Def("write", Simple(96));
    {
      final Sym S = Expecting(97, EXPECT_UNITREG);
      S.Expect2 = EXPECT_LOC;
      Def("dsz", S);
    }
    Def(".", Simple2(93, 98));
    Def("adv", Simple(98));
    Def("+/-", Simple2(94, 99));
    Def("prt", Simple(99));
    Def("=", Simple2(95, 90));
    Def("list", Simple(90));

    /* synonyms */
    for (String Ch : new String[] {"a", "b", "c", "d", "e"}) {
      Synonym(Ch + "´", Ch + "'");
    }
    Synonym("x⇌t", "x<>t");
    Synonym("x-t", "x<>t");
    Synonym("x/t", "x<>t");
    Synonym("x%t", "x<>t");
    Synonym("p→r", "p->r");
    Synonym("p/r", "p->r");
    Synonym("sqr", "x2");
    Synonym("x²", "x2");
    Synonym("√x", "sqrt");
    Synonym("**", "y**x");
    Synonym("yx", "y**x");
    Synonym("pow", "y**x");
    Synonym("yˣ", "y**x");
    Synonym("abs", "|x|");
    Synonym("ixi", "|x|");
    Synonym("/", "÷");
    Synonym("mean(x)", "mean");
    Synonym("*", "×");
    Synonym("eq", "x=t");
    Synonym("ge", "x≥t");
    Synonym("x>=t", "x≥t");
    Synonym("∑+", "sigma");
    Synonym("s+", "sigma");
    Synonym("sta", "sigma");
    Synonym("iff", "ifflg");
    Synonym("stf", "stflg");
    Synonym("π", "pi");
    Synonym("grd", "grad");
    Synonym("dms", "d.ms");
    Synonym("wrt", "write");
    Synonym("lst", "list");
  }

  private static final Pattern LocationMarker = Pattern.compile("^\\d+:$");
  private static final Pattern Numeric =
     Pattern.compile("^[+\\-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+?)(?:[eE][+\\-]?\\d+)?$");
  private static final Pattern Integral = Pattern.compile("^\\d+$");

  /* ---------------------------------------------------------------- state */

  private final int MaxLoc;
  private final List<String> Errors = new ArrayList<>();
  private final List<String> Warnings = new ArrayList<>();

  /* the program: two-digit opcodes, or symbolic label references resolved at the end */
  private final List<String> Prog = new ArrayList<>();
  private final Map<String, Integer> Labels = new HashMap<>();

  private boolean AltState = false;
  private boolean IndState = false;
  private Integer PendingOut = null; /* hold back last byte for possible merging */
  private String Expect = null;
  private Sym LastSym = null;
  private int Loc = 0;
  private int LineNr = 0, ColNr = 0;
  private final Set<String> LabelsDefined = new HashSet<>();
  private final Set<String> LabelsReferenced = new HashSet<>();

  private Assembler
     (
        int MaxLoc
     ) {
    this.MaxLoc = MaxLoc;
  }

  private void ReportError
     (
        String Msg
     ) {
    Errors.add
       (
          String.format(Locale.US, "Assembler error at line %d, column %d: %s", LineNr, ColNr, Msg)
       );
    /* reset everything to try to recover from error */
    AltState = false;
    Expect = null;
    LastSym = null;
  }

  private void SetAltState() {
    if (AltState) {
      ReportError("redundant \"2nd\"");
    }
    AltState = true;
  }

  private void CheckSize() {
    if (Loc == MaxLoc) {
      ReportError(String.format(Locale.US, "program too large, can't exceed %d steps", MaxLoc));
    }
  }

  private void RealOut
     (
        int B
     ) {
    /* really outputs a single instruction byte. */
    Prog.add(String.format(Locale.US, "%02d", B));
  }

  private void FlushOut() {
    /* writes out any pending instruction byte. */
    if (PendingOut != null) {
      RealOut(PendingOut);
      PendingOut = null;
    }
  }

  private void Out
     (
        int B
     ) {
    /* outputs a single instruction byte. */
    CheckSize();
    FlushOut();
    PendingOut = B;
    Loc++;
  }

  private void OutLoc
     (
        int B
     ) {
    /* outputs a single instruction byte, no merging possible. */
    CheckSize();
    FlushOut();
    RealOut(B);
    Loc++;
  }

  private void OutLabel
     (
        String Label
     ) {
    /* outputs a label reference (3-digit address), resolved at the end. */
    CheckSize();
    FlushOut();
    Prog.add(Label);
    Loc += 2;
  }

  private void RecordLabel
     (
        String Label /* with trailing colon */
     ) {
    FlushOut();
    Labels.put(Label.substring(0, Label.length() - 1), Loc);
  }

  private void Fix() {
    if (PendingOut != null && PendingOut == 22) {
      Out(58);
      Expect = null;
    } else {
      Expect = EXPECT_FLAG;
    }
  }

  private void Sbr() {
    if (PendingOut != null && PendingOut == 22) {
      PendingOut = null;
      Loc--;
      Out(92);
      Expect = null;
    } else {
      Expect = EXPECT_LOC;
    }
  }

  private void DoAct
     (
        int Act
     ) {
    switch (Act) {
      case ACT_ALT:
        SetAltState();
        break;
      case ACT_FIX:
        Fix();
        break;
      case ACT_SBR:
        Sbr();
        break;
    }
  }

  private static String LabelKey(String Lookup, boolean Alt) {
    return Lookup + (Alt ? " (2nd)" : "");
  }

  /* ---------------------------------------------------------------- parsing */

  private void Run
     (
        String Source,
        boolean CheckLabels
     ) {
    boolean InComment = false;
    boolean WasNewline = true;
    boolean IsDszOp = false;
    StringBuilder CurSym = null;
    int Pos = 0;
    for (; ; ) {
      final boolean EOF = Pos >= Source.length();
      final int Ch = EOF ? ' ' : Source.codePointAt(Pos);
      if (!EOF) {
        Pos += Character.charCount(Ch);
      }
      if (WasNewline) {
        LineNr++;
        ColNr = 0;
        WasNewline = false;
      }
      if (Ch == '\n') {
        WasNewline = true;
      } else {
        ColNr++;
      }
      if (InComment) {
        if (EOF)
          break;
        if (Ch == '\n') {
          InComment = false;
        }
      } else if (CurSym == null) {
        if (Ch > ' ') {
          if (Ch == ';' || Ch == '#') {
            InComment = true;
          } else {
            CurSym = new StringBuilder().appendCodePoint(Ch); /* start collecting next symbol */
          }
        } else if (EOF) {
          break;
        }
      } else if (Ch > ' ') {
        CurSym.appendCodePoint(Ch);
      } else {
        /* got a complete symbol */
        IsDszOp = Symbol(CurSym.toString(), IsDszOp);
        CurSym = null;
        if (EOF)
          break;
      }
    }
    FlushOut();
    if (CheckLabels) {
      for (String S : LabelsReferenced) {
        if (!LabelsDefined.contains(S)) {
          Warnings.add("label \"" + S + "\" referenced but not defined");
        }
      }
      for (String S : LabelsDefined) {
        final boolean UserKey = S.matches("^[a-e]'?$");
        if (!LabelsReferenced.contains(S) && !UserKey) {
          Warnings.add("label \"" + S + "\" defined but not referenced");
        }
      }
    }
  }

  private static int ParseInt
     (
        String Digits
     ) {
    /* like Integer.parseInt for a sequence of digits, saturating instead of overflowing */
    try {
      return Integer.parseInt(Digits);
    } catch (NumberFormatException TooLarge) {
      return Integer.MAX_VALUE;
    }
  }

  private boolean Symbol
     (
        String CurSym,
        boolean IsDszOp
     ) {
    /* processes a complete symbol, returns the updated "last opcode was Dsz" state. */
    if (LocationMarker.matcher(CurSym).matches()) {
      final int ExpectLoc = ParseInt(CurSym.substring(0, CurSym.length() - 1));
      if (ExpectLoc != Loc) {
        ReportError
           (
              String.format(Locale.US, "location mismatch, expecting %03d, actually %3d", ExpectLoc, Loc)
           );
      }
      return IsDszOp;
    }
    final String Lookup = CurSym.toLowerCase(Locale.US);
    final Sym Symbol = Symbols.get(Lookup);
    final boolean IsNumeric = Numeric.matcher(CurSym).matches();
    final boolean IsIntegral = Integral.matcher(CurSym).matches();

    if (Symbol != null) {
      IsDszOp = Symbol.Op != null && Symbol.Op == 97;
    }
    if (Expect != null) {
      if (!Expect.equals(EXPECT_LABEL) && Symbol != null && Symbol.IsInd) {
        if (Expect.equals(EXPECT_NOINDREG)) {
          ReportError("\"ind\" not allowed");
        }
        if (IndState) {
          ReportError("redundant \"ind\"");
        }
        IndState = true;
        Expect = EXPECT_REG;
      }
      /* ensure opcode has been output, if not already done so */
      if (LastSym != null && LastSym.Op != null) {
        if (IndState && LastSym.IndOp != null) {
          Out(AltState ? LastSym.AltIndOp : LastSym.IndOp);
        } else {
          Out(AltState ? LastSym.AltOp : LastSym.Op);
          if (IndState) {
            Out(40);
          }
        }
        LastSym.Op = null;
        LastSym.AltOp = null;
        LastSym.IndOp = null;
        LastSym.AltIndOp = null;
      } else if (IndState) {
        Out(40);
      }
      IndState = false;
    }

    if (IsNumeric) {
      if (Expect != null) {
        if (IsIntegral) {
          final int OpVal = ParseInt(CurSym);
          switch (Expect) {
            case EXPECT_LOC:
              if (IsDszOp && OpVal == 51 && CurSym.length() == 2) {
                Out(OpVal);
              } else if (OpVal < 1000) {
                Out(OpVal / 100);
                Out(OpVal % 100);
              } else {
                ReportError("address can't be > 999");
              }
              break;
            case EXPECT_REG:
            case EXPECT_NOINDREG:
              if (OpVal < 100) {
                Out(OpVal);
                FlushOut();
              } else {
                ReportError("reg/op/pgm number can't be > 99");
              }
              break;
            case EXPECT_FLAG:
              if (OpVal < 10) {
                Out(OpVal);
              } else {
                ReportError("flag number can't be > 9");
              }
              break;
            case EXPECT_UNITREG:
              if (OpVal != 40) {
                Out(OpVal);
              } else {
                ReportError("reg number can't be 40");
              }
              break;
            default: /* label */
              ReportError("expecting " + Expect);
              break;
          }
        } else {
          ReportError("expecting integer");
        }
        if (LastSym != null) {
          Expect = LastSym.Expect; /* further operand? */
          if (Expect != null) {
            LastSym.Expect = null;
          } else {
            LastSym = null; /* all done */
          }
        }
        /* else cleared on error */
      } else {
        /* not expecting special operand, so just output keystrokes for entering number */
        boolean WasSigned = false;
        for (int i = 0; i < CurSym.length(); i++) {
          final char Digit = CurSym.charAt(i);
          if (Digit == '-') {
            WasSigned = true;
          } else if (Digit >= '0' && Digit <= '9') {
            Out(Digit - '0');
          } else if (Digit == '.') {
            Out(93); /* "." */
          } else if (Digit == 'e' || Digit == 'E') {
            if (WasSigned) {
              Out(94); /* "+/-" */
              WasSigned = false;
            }
            Out(52); /* "EE" */
          }
        }
        if (WasSigned) {
          Out(94); /* "+/-" */
        }
      }
    } else if (Symbol != null) {
      if (Expect != null) {
        if (Symbol.IsInd) {
          if (Expect.equals(EXPECT_LABEL)) {
            ReportError("cannot use \"ind\" as label");
            Expect = null;
          }
          /* else already handled above */
        } else if (Expect.equals(EXPECT_LOC) || Expect.equals(EXPECT_LABEL)) {
          final Integer Which = AltState ? Symbol.AltOp : Symbol.Op;
          if (Expect.equals(EXPECT_LOC)) {
            LabelsReferenced.add(LabelKey(Lookup, AltState));
          } else if (Symbol.Act != ACT_ALT) {
            if (LabelsDefined.contains(LabelKey(Lookup, AltState))) {
              ReportError("multiply-defined label " + LabelKey(CurSym, AltState));
            }
            LabelsDefined.add(LabelKey(Lookup, AltState));
          }
          if (Which != null) {
            OutLoc(Which);
            Expect = null;
            AltState = false;
          } else if (Symbol.Act == ACT_ALT) {
            DoAct(Symbol.Act);
          } else {
            ReportError
               (
                  "cannot use \"" + CurSym + "\" as label -- no " + (AltState ? "altop" : "op")
               );
          }
        } else {
          ReportError("not expecting symbol for " + Expect);
          Expect = null;
        }
        if (Expect == null) {
          LastSym = null;
        }
      } else if (Symbol.IsInd) {
        ReportError("dangling \"ind\"");
      } else {
        if (Symbol.Act != ACT_NONE) {
          DoAct(Symbol.Act);
        } else {
          Expect = Symbol.Expect;
          if (Expect == null) {
            Out(AltState ? Symbol.AltOp : Symbol.Op);
          }
        }
        if (Expect != null) {
          LastSym = Symbol.Copy();
        } else {
          LastSym = null;
        }
        if (!Symbol.Modifier) {
          AltState = false;
        }
      }
    } else if (CurSym.endsWith(":")) {
      /* this is a label definition */
      RecordLabel(CurSym);
    } else {
      /* a label reference */
      OutLabel(CurSym);
      Expect = null;
    }
    return IsDszOp;
  }

  private byte[] ResolveProgram() {
    /* second pass: resolves the label references. */
    final List<Integer> Codes = new ArrayList<>();
    for (String S : Prog) {
      if (Integral.matcher(S).matches()) {
        Codes.add(Integer.parseInt(S));
      } else if (Labels.containsKey(S)) {
        final int V = Labels.get(S);
        Codes.add(V / 100);
        Codes.add(V % 100);
      } else {
        ReportError("unrecognized symbol \"" + S + "\"");
      }
    }
    final byte[] Result = new byte[Codes.size()];
    for (int i = 0; i < Result.length; i++) {
      Result[i] = (byte) (int) Codes.get(i);
    }
    return Result;
  }
}
