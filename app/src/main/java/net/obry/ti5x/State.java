/*
    The calculation state, number entry and programs.

    Copyright 2011-2012 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2014-2015 Pascal Obry <pascal@obry.net>.

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

import android.util.SparseIntArray;

class State {
  /* the calculator state, number entry and programs */

  private final android.content.Context ctx;
  /* number-entry state */
  final static int EntryState = 0;
  final static int DecimalEntryState = 1;
  final static int ExponentEntryState = 2;
  final static int ResultState = 10;
  int CurState = EntryState;
  boolean ExponentEntered = false; /* whether the dispay as 00 exp at the end */
  boolean InvState = false; /* INV has been pressed/executed */
  private int ParenCount = 0;
  private boolean FromResult = false;
  // whether the current value is from a result RCL, or a typed number

  static boolean inError = false;
  boolean TracePrintActivated = false;

  String CurDisplay; /* current number display */
  private android.os.Handler BGTask;
  private Runnable DelayTask = null;
  private Runnable ExecuteTask;
  private Runnable RunProg = null;
  Runnable OnStop = null;

  static class ImportEOFException extends RuntimeException {
    /* indicates no more data to import. */

    ImportEOFException
       (
          String Message
       ) {
      super(Message);
    }
  }

  static abstract class ImportFeeder {
    abstract Number Next()
       throws
       ImportEOFException,
       Persistent.DataFormatException;
    /* returns the next input value or raises ImportEOFException if none. */

    abstract void Reset()
       throws Persistent.DataFormatException;

    void End() {
      /* stops further invocations of the task. Subclass may add
        further cleanup, but must also invoke this superclass method. */
      if (Global.Calc != null) {
        Global.Calc.Import = null;
      }
    }
  }

  ImportFeeder Import = null;

  private java.security.SecureRandom Random = new java.security.SecureRandom();

  /* number-display format */
  static final int FORMAT_FIXED = 0;
  static final int FORMAT_FLOAT = 1;
  static final int FORMAT_ENG = 2;
  int CurFormat;
  int CurNrDecimals = -1;

  /* Max and Min number that can be represented using a fixed format */
  private final static Number minFixed = new Number(5.0 * Math.pow(10.0, -9.0));
  private final static Number maxFixed = new Number(Math.pow(10.0, 10.0));

  /* angle units */
  static final int ANG_RAD = 1;
  static final int ANG_DEG = 2;
  static final int ANG_GRAD = 3;
  int CurAng;

  /* pending-operation stack */
  final static int STACKOP_ADD = 1;
  final static int STACKOP_SUB = 2;
  final static int STACKOP_MUL = 3;
  final static int STACKOP_DIV = 4;
  final static int STACKOP_MOD = 5;
  final static int STACKOP_EXP = 6;
  final static int STACKOP_ROOT = 7;

  private static final int[] STACKOP_CODE = {85, 75, 65, 55, 55, 45, 34};

  static class OpStackEntry {
    Number Operand;
    int Operator;
    int ParenFollows;

    OpStackEntry
       (
          Number Operand,
          int Operator,
          int ParenFollows
       ) {
      this.Operand = Operand;
      this.Operator = Operator;
      this.ParenFollows = ParenFollows;
    }
  }

  final int MaxOpStack = 8;
  private final static int MaxParen = 9;
  Number X, T;
  OpStackEntry[] OpStack;
  int OpStackNext;
  int PreviousOp = -1;
  // wether the last entry was an operator requiring 2 operands:
  // 1 + = (should set calculator in error)

  private boolean IsOperator(int Op) {
    return Op == 45 || Op == 55 || Op == 65 || Op == 75 || Op == 85;
  }

  static class ProgBank {
    byte[] Program;
    SparseIntArray Labels;
    /* mapping from symbolic codes to program locations */
    android.graphics.Bitmap Card; /* card image to display when bank is selected, can be null */
    byte[] Help; /* HTML help to display, can be null */

    ProgBank
       (
          byte[] Program,
          android.graphics.Bitmap Card,
          byte[] Help
       ) {
      this.Program = Program;
      this.Card = Card;
      this.Help = Help;
      this.Labels = null;
    }
  }

  boolean ProgMode; /* true for program-entry mode, false for calculation mode */
  final int MaxMemories = 100; /* maximum addressable */

  final int MaxProgramSteps = 1500; /* absolute max supported by emulator (original was 960) */

  final int MaxCardProgramSteps = 1500;
  /* a SSSM have 5000 steps and a single program can have more than 1000 steps. */

  final int MaxBanks = 100;
  final int BankNumber = 4;
  /* 00 is user-entered program, others are loaded from library modules */
  final int MaxFlags = 10;
  final Number[] Memory;
  final Number[] CardMemory;
  final byte[] Program;
  final byte[] CardProgram;
  final boolean[] CardBankUsed; // program or memory used in one of the 4 banks
  final ProgBank[] Bank; /* Bank[0].Program always points to Program */
  byte[] ModuleHelp; /* overall help for loaded library module */
  final boolean[] Flag;

  final CardStore Store;

  /* special flag numbers: */
  private final static int FLAG_ERROR_COND = 7;
  /* can be set by Op 18/19 to indicate error/no-error, and
     by Op 40 to indicate printer present */
  private final static int FLAG_STOP_ON_ERROR = 8; /* if set, program stops on error */

  int PC, CurBank;
  private int RunPC, RunBank, NextBank;
  int RegOffset;
  boolean TaskRunning; /* program currently executing */
  private boolean ProgRunningSlowly; /* executing program pauses to show intermediate result */
  private boolean AllowRunningSlowly;
  private boolean SaveRunningSlowly; /* for one-off pauses */

  /* use of memories for stats operations */
  private static final int STATSREG_SIGMAY = 1;
  private static final int STATSREG_SIGMAY2 = 2;
  private static final int STATSREG_N = 3;
  private static final int STATSREG_SIGMAX = 4;
  private static final int STATSREG_SIGMAX2 = 5;
  private static final int STATSREG_SIGMAXY = 6;
  private static final int STATSREG_FIRST = 1; /* lowest-numbered memory used for stats */
  private static final int STATSREG_LAST = 6; /* highest-numbered memory used for stats */

  static class ReturnStackEntry {
    int BankNr, Addr;
    boolean FromInteractive;

    ReturnStackEntry
       (
          int BankNr,
          int Addr,
          boolean FromInteractive
       ) {
      this.BankNr = BankNr;
      this.Addr = Addr;
      this.FromInteractive = FromInteractive;
    }
  }

  final int MaxReturnStack = 6;
  ReturnStackEntry[] ReturnStack; /* for subroutine calls */
  int ReturnLast; /* top of ReturnStack */

  private String LastShowing = null;

  final byte[] PrintRegister;

  void Reset
     (
        boolean ClearLibs /* wipe out loaded library module as well */
     ) {
    /* resets to power-up/blank state. */
    CurFormat = FORMAT_FIXED;
    CurNrDecimals = -1;
    CurAng = ANG_DEG;
    OpStackNext = 0;
    ParenCount = 0;
    PreviousOp = -1;
    X = new Number();
    T = new Number();
    PC = 0;
    RunPC = 0;
    CurBank = 0;
    RegOffset = 0;
    ReturnLast = -1;
    ClearImport();
    TaskRunning = false;
    ProgRunningSlowly = false;
    AllowRunningSlowly = false;
    ResetLabels();
    inError = false;
    for (int i = 0; i < MaxFlags; ++i) {
      Flag[i] = false;
    }
    for (int i = 0; i < MaxMemories; ++i) {
      Memory[i] = new Number();
      CardMemory[i] = new Number();
    }
    for (int i = 0; i < MaxProgramSteps; ++i) {
      Program[i] = (byte) 0;
    }
    for (int i = 0; i < MaxCardProgramSteps; ++i) {
      CardProgram[i] = (byte) 0;
    }
    if (ClearLibs) {
      ResetLibs();
    }
    ProgMode = false;
    for (int i = 0; i < PrintRegister.length; ++i) {
      PrintRegister[i] = 0;
    }
    for (int i = 0; i < BankNumber; ++i) {
      CardBankUsed[i] = false;
    }
    ResetEntry();
  }

  void ResetLibs() {
    /* wipes out loaded library modules */
    for (int i = 1; i < MaxBanks; ++i) {
      if (Bank[i] != null && Bank[i].Card != null) {
        /* avoid "bitmap allocation exceeds budget" crashes */
        if (CurBank == i) {
          Global.Label.SetHelp(null, null);
        } else {
          Bank[i].Card.recycle();
          Bank[i].Card = null;
        }
      }
      Bank[i] = null;
    }
    ModuleHelp = null;
    PC = 0;
  }

  State
     (
        android.content.Context ctx
     ) {
    this.ctx = ctx;
    OpStack = new OpStackEntry[MaxOpStack];
    Memory = new Number[MaxMemories];
    Program = new byte[MaxProgramSteps];
    CardMemory = new Number[MaxMemories];
    CardProgram = new byte[MaxCardProgramSteps];
    CardBankUsed = new boolean[BankNumber];
    Store = new CardStore();
    Bank = new ProgBank[MaxBanks];
    Bank[0] = new ProgBank(Program, null, null);
    Flag = new boolean[MaxFlags];
    BGTask = new android.os.Handler();
    ExecuteTask = new TaskRunner();
    ReturnStack = new ReturnStackEntry[MaxReturnStack];
    PrintRegister = new byte[Printer.CharColumns];
    Reset(true);
  }

  class DelayedStep implements Runnable {
    public void run() {
      ShowCurProg();
    }
  }

  private void ClearDelayedStep() {
    if (DelayTask != null) {
      BGTask.removeCallbacks(DelayTask);
    }
  }

  private void SetShowing
     (
        String ToDisplay
     ) {
    ClearDelayedStep();
    LastShowing = ToDisplay;
    if (!TaskRunning || ProgRunningSlowly) {
      if (InErrorState()) {
        Global.Disp.SetShowingError(ToDisplay);
      } else {
        Global.Disp.SetShowing(ToDisplay);
      }
    }
  }

  void ResetEntry() {
    CurState = EntryState;
    CurDisplay = "0";
    ExponentEntered = false;
    SetShowing(CurDisplay);
  }

  void Enter(int op) {
    /* finishes the entry of the current number. */
    if (CurState != ResultState) {
      int Exp;
      if (ExponentEntered) {
        Exp = Integer.parseInt(CurDisplay.substring(CurDisplay.length() - 2));
        if (CurDisplay.charAt(CurDisplay.length() - 3) == '-') {
          Exp = -Exp;
        }
      } else {
        Exp = 0;
      }
      X = new Number
         (
            CurDisplay.substring
               (
                  0,
                  ExponentEntered ? CurDisplay.length() - 3 : CurDisplay.length()
               )
         );
      if (ExponentEntered) {
        Number E = new Number(Math.pow(10, Exp));
        X.mult(E);
      }

      SetX(X, false);
      FromResult = false;
    }

    if (TracePrintActivated && Global.Print != null) {
      TraceDisplay
         // no number when:
            (op != 53     // opening a parenthesis
                  && op != 25  // clr
                  && op != 20  // 2nd clr
                  && op != 24, // ce
               (InvState ? "I" : " ") + Printer.KeyCodeSym(op));
    }
  }

  void SetErrorState
     (
        boolean AlsoStopProgram
     ) {
    ClearDelayedStep();
    if (!TaskRunning || ProgRunningSlowly) {
      Global.Disp.SetShowingError(LastShowing);
    }
    inError = true;
    if (AlsoStopProgram || Flag[FLAG_STOP_ON_ERROR]) {
      StopProgram();
    }
  }

  boolean InErrorState() {
    return inError;
  }

  boolean ImportInProgress() {
    return Import != null;
  }

  void ClearAll() {
    Enter(25);
    inError = false;
    OpStackNext = 0;
    ParenCount = 0;
    PreviousOp = -1;
    if (CurFormat == FORMAT_FLOAT)
      CurFormat = FORMAT_FIXED;
    ResetEntry();
  }

  void ClearEntry() {
    if (inError) {
      inError = false;
      SetX(X, true);
    } else if (CurState != ResultState)
      ResetEntry();
  }

  void Digit
     (
        char TheDigit
     ) {
    if (CurState == ResultState) {
      ResetEntry();
    }

    String SaveExponent = "";

    switch (CurState) {
      case EntryState:
      case DecimalEntryState:
        if (ExponentEntered) {
          SaveExponent = CurDisplay.substring(CurDisplay.length() - 3);
          CurDisplay = CurDisplay.substring(0, CurDisplay.length() - 3);
        }
        break;
    }

    switch (CurState) {
      case EntryState:
        if (CurDisplay.charAt(0) == '0') {
          CurDisplay = new String(new char[]{TheDigit}) + CurDisplay.substring(1);
        } else if (CurDisplay.charAt(0) == '-' && CurDisplay.charAt(1) == '0') {
          CurDisplay = "-" + new String(new char[]{TheDigit}) + CurDisplay.substring(2);
        } else {
          CurDisplay =
             CurDisplay.substring(0, CurDisplay.length())
                +
                new String(new char[]{TheDigit})
                +
                CurDisplay.substring(CurDisplay.length());
        }
        break;
      case DecimalEntryState:
        CurDisplay = CurDisplay + new String(new char[]{TheDigit});
        break;
      case ExponentEntryState:
        /* old exponent units digit becomes tens digit, new digit
           becomes units digit */
        CurDisplay =
           CurDisplay.substring(0, CurDisplay.length() - 2)
              +
              CurDisplay.substring(CurDisplay.length() - 1)
              +
              new String(new char[]{TheDigit});
        break;
    }
    CurDisplay += SaveExponent;
    SetShowing(CurDisplay);
  }

  void DecimalPoint() {
    if (CurState == ResultState) {
      ResetEntry();
    }
    switch (CurState) {
      case EntryState:
      case ExponentEntryState:
        CurState = DecimalEntryState;

        // Add decimal point if needed
        if (!CurDisplay.contains(".")) {
          final int len = CurDisplay.length();
          if (ExponentEntered) {
            CurDisplay =
               CurDisplay.substring(0, len - 3)
                  +
                  new String(new char[]{'.'})
                  +
                  CurDisplay.substring(len - 3);
          } else {
            CurDisplay = CurDisplay + ".";
          }
          SetShowing(CurDisplay);
        }
        break;
    }
  }

  private boolean NullExponent() {
    return CurDisplay.length() <= 3
       || CurDisplay.substring(CurDisplay.length() - 3).equals(" 00");
  }

  private boolean HasExponent() {
    return CurDisplay.length() > 3
       && (CurDisplay.charAt(CurDisplay.length() - 3) == '-'
       || CurDisplay.charAt(CurDisplay.length() - 3) == ' ');
  }

  void EnterExponent() {
    switch (CurState) {
      case EntryState:
      case DecimalEntryState:
        if (!ExponentEntered) {
          CurDisplay = CurDisplay + " 00";
          if (CurFormat == FORMAT_FIXED)
            CurFormat = FORMAT_FLOAT;
        }
        CurState = ExponentEntryState;
        SetShowing(CurDisplay);
        ExponentEntered = true;
        break;
      case ExponentEntryState:
        if (InvState) {
          // reset the display only if we have no exponent that is:
          //   1 ee inv-ee     must display :  1
          //   1 ee 1 inv-ee 2 must display : 12 01

          if (ExponentEntered && NullExponent()) {
            CurDisplay = CurDisplay.substring(0, CurDisplay.length() - 3);
            SetShowing(CurDisplay);
            ExponentEntered = false;
          }

          if (CurFormat == FORMAT_FLOAT)
            CurFormat = FORMAT_FIXED;

          if (FromResult)
            CurState = DecimalEntryState;
          else
            CurState = EntryState;
        } else if (CurFormat == FORMAT_FIXED)
          CurFormat = FORMAT_FLOAT;
        break;
      case ResultState:
        if (InvState) {
          ExponentEntered = false;
          if (CurFormat != FORMAT_FIXED) {
            if (CurFormat == FORMAT_FLOAT)
              CurFormat = FORMAT_FIXED;
            CurNrDecimals = -1;
            SetX(X, false); /* will cause redisplay */
          }
        } else {
          FromResult = true;
          CurFormat = FORMAT_FLOAT;

          if (!ExponentEntered) {
            // ?? the following test is because just above (InvState) we set ExponentEntered.
            //    but the exponent can still be displayed. This is wrong and should be fixed
            //    at some point.
            if (!HasExponent()) {
              CurDisplay = CurDisplay + " 00";
              CurState = ExponentEntryState;
              SetShowing(CurDisplay);
              ExponentEntered = true;
            }
          }
        }
        break;
    }
  }

  private static String RemoveTrailingZeros(String str) {
    String Result = str;

    while (Result.length() != 0
       &&
       Result.charAt(Result.length() - 1) == '0') {
      Result = Result.substring(0, Result.length() - 1);
    }

    return Result;
  }

  private static String RemoveLeadingZero(String str, int maxSize) {
    String Result = str;
    final int len = Result.length();

    if (len == maxSize && Result.charAt(0) == '0')
      Result = Result.substring(1, Result.length());
    else if (len == (maxSize + 1) && Result.charAt(0) == '-' && Result.charAt(1) == '0')
      Result = "-" + Result.substring(2, Result.length());

    return Result;
  }

  private static String FormatNumber
     (
        Number X,
        int UseFormat,
        int NrDecimals,
        boolean ExponentPad /* leave spaces if exponent is omitted */
     ) {
    /* formats X for display according to the specified settings. */
    String Result = null;

    Number aX = new Number(X);
    aX.abs();

    // check that FORMAT_FIXED can be used, if outside supported range use FORMAT_FLOAT

    final int CurFormat = UseFormat;

    if (UseFormat == FORMAT_FIXED
       && NrDecimals == -1
       && X.getSignum() != 0
       && (aX.compareTo(minFixed) < 0
       || aX.compareTo(maxFixed) >= 0)) {
      UseFormat = FORMAT_FLOAT;
    }

    final int Exp = X.scaleExp(UseFormat);
    final int BeforeDecimal = X.figuresBeforeDecimal(Exp);

    switch (UseFormat) {
      case FORMAT_FLOAT:
      case FORMAT_ENG:
        final Number Factor = new Number(Math.pow(10.0, Exp));
        Number N = new Number(X);
        N.div(Factor);

        final int MaxDecimal = 8;

        final int UseNrDecimalsF = NrDecimals == -1
            ? Math.max(MaxDecimal - BeforeDecimal, 0)
            : Math.min(NrDecimals, MaxDecimal - BeforeDecimal);

        Result = N.formatString(Global.StdLocale, UseNrDecimalsF);
        if (NrDecimals == -1) {
          Result = RemoveTrailingZeros(Result);
        }
                /* assume there will always be a decimal point? */
        Result += (Exp < 0 ? "-" : " ") + String.format(Global.StdLocale, "%02d", Math.abs(Exp));
        break;

      case FORMAT_FIXED:
        if (NrDecimals >= 0) {
          Result = X.formatString
             (Global.StdLocale, Math.max(Math.min(NrDecimals, 10 - BeforeDecimal), 0));
        } else {
          final int UseNrDecimals = Math.max(10 - BeforeDecimal, 0);
          Result = X.formatString(Global.StdLocale, UseNrDecimals);

          if (UseNrDecimals > 0) {
            Result = RemoveTrailingZeros(Result);

            // this can happen only when number <1, remove leading zero
            // length is +2 because of 0 and decimal point
            if (UseNrDecimals == 10)
              Result = RemoveLeadingZero(Result, 12);
          } else {
            Result += ".";
          }
        }

        if (Result.length() == 0) {
          Result = "0.";
        }
        if (ExponentPad) {
          Result += "   ";
        }
        break;
    }

    return Result;
  }

  void SetX
     (
        Number NewX,
        boolean Trace
     ) {
    /* sets the display to show the specified value. */
    CurState = ResultState;
    if (NewX.isInfinite() || NewX.isSmall()) {
      SetErrorState(false);
    }
    if (!NewX.isNaN()) {
      X = new Number(NewX);
      CurDisplay = FormatNumber(X, CurFormat, CurNrDecimals, false);
      SetShowing(CurDisplay);
    } else {
      SetErrorState(false);
    }
    if (Trace)
      TraceDisplay(true, "");
  }

  void ChangeSign() {
    switch (CurState) {
      case EntryState:
      case DecimalEntryState:
        if (CurDisplay.charAt(0) == '-') {
          CurDisplay = CurDisplay.substring(1);
        } else {
          CurDisplay = "-" + CurDisplay;
        }
        SetShowing(CurDisplay);
        break;
      case ExponentEntryState:
        CurDisplay =
           CurDisplay.substring(0, CurDisplay.length() - 3)
              +
              (CurDisplay.charAt(CurDisplay.length() - 3) == '-' ? ' ' : '-')
              +
              CurDisplay.substring(CurDisplay.length() - 2);
        SetShowing(CurDisplay);
        break;
      case ResultState:
        X.negate();
        SetX(X, true);
        break;
    }
  }

  void SetDisplayMode
     (
        int NewMode,
        int NewNrDecimals
     ) {
    Enter(NewMode == FORMAT_FIXED ? 58 : 57);
    CurFormat = NewMode;
    CurNrDecimals = NewNrDecimals >= 0 && NewNrDecimals < 9 ? NewNrDecimals : -1;
    SetX(X, true);
  }

  private void DoStackTop() {
    final OpStackEntry ThisOp = OpStack[--OpStackNext];
    switch (ThisOp.Operator) {
      case STACKOP_ADD:
        X.add(ThisOp.Operand);
        break;
      case STACKOP_SUB:
        ThisOp.Operand.sub(X);
        X = ThisOp.Operand;
        break;
      case STACKOP_MUL:
        X.mult(ThisOp.Operand);
        break;
      case STACKOP_DIV:
        ThisOp.Operand.div(X);
        X = ThisOp.Operand;
        if (X.isError()) {
          SetX(X, false);
          SetErrorState(false);
        }
        break;
      case STACKOP_MOD:
        ThisOp.Operand.rem(X);
        X = ThisOp.Operand;
        break;
      case STACKOP_EXP:
        ThisOp.Operand.pow(X);
        X = ThisOp.Operand;

        if (X.isError()) {
          SetX(X, false);
          SetErrorState(false);
        }
        break;
      case STACKOP_ROOT:
        if (X.getSignum() == 0) {
          if (ThisOp.Operand.getSignum() == 0
             || ThisOp.Operand.compareTo(Number.ONE) == 0
             || ThisOp.Operand.compareTo(Number.mONE) == 0) {
            X.set(1);
          } else {
            X.set(Number.ERROR);
          }
          SetX(X, false);
          SetErrorState(false);
        } else if (X.getSignum() < 0 && ThisOp.Operand.getSignum() <= 0) {
          if (ThisOp.Operand.getSignum() == 0) {
            X.set(Number.ERROR);
          } else if (ThisOp.Operand.getSignum() < 0) {
            ThisOp.Operand.abs();
            X.reciprocal();
            ThisOp.Operand.pow(X);
            X = ThisOp.Operand;
          }
          SetX(X, false);
          SetErrorState(false);
        } else {
          X.reciprocal();
          ThisOp.Operand.pow(X);
          X = ThisOp.Operand;
        }
        break;
    }
    /* leave it to caller to update display */
  }

  private static int Precedence
     (
        int OpCode
     ) {
    int Result = -1;
    switch (OpCode) {
      case STACKOP_ADD:
      case STACKOP_SUB:
        Result = 1;
        break;
      case STACKOP_MUL:
      case STACKOP_DIV:
      case STACKOP_MOD:
        Result = 2;
        break;
      case STACKOP_EXP:
      case STACKOP_ROOT:
        Result = 3;
        break;
    }
    return Result;
  }

  private void StackPush
     (
        int OpCode
     ) {
    if (OpStackNext == MaxOpStack) {
      /* overflow! */
      SetErrorState(false);
    } else {
      OpStack[OpStackNext++] = new OpStackEntry(new Number(X), OpCode, 0);
    }
  }

  void Operator
     (
        int OpCode
     ) {
    Enter(STACKOP_CODE[OpCode - 1]);
    if (InvState) {
      switch (OpCode) {
        case STACKOP_EXP:
          OpCode = STACKOP_ROOT;
          break;
      }
    }
    boolean PoppedSomething = false;
    for (; ; ) {
      if
         (
         OpStackNext == 0
            ||
            OpStack[OpStackNext - 1].ParenFollows != 0
            ||
            Precedence(OpStack[OpStackNext - 1].Operator) < Precedence(OpCode)
         )
        break;
      DoStackTop();
      PoppedSomething = true;
    }
    if (PoppedSomething) {
      SetX(X, false);
    }
    StackPush(OpCode);
  }

  void LParen() {
    Enter(53);

    if (ParenCount == MaxParen)
      SetErrorState(false);
    else
      ParenCount++;

    if (OpStackNext != 0) {
      ++OpStack[OpStackNext - 1].ParenFollows;
    }
  }

  void RParen() {
    Enter(54);
    if (IsOperator(PreviousOp))
      SetErrorState(false);
    else {
      ParenCount--;
      boolean PoppedSomething = false;
      for (; ; ) {
        if (OpStackNext == 0)
          break;
        if (OpStack[OpStackNext - 1].ParenFollows != 0) {
          --OpStack[OpStackNext - 1].ParenFollows;
          break;
        }
        DoStackTop();
        PoppedSomething = true;
      }
      if (PoppedSomething) {
        SetX(X, true);
      }
    }
  }

  void Equals() {
    Enter(95);
    if (IsOperator(PreviousOp))
      SetErrorState(false);
    else {
      while (OpStackNext != 0) {
        DoStackTop();
      } /*while*/
      SetX(X, true);
    }
  }

  void SetAngMode
     (
        int NewMode
     ) {
    CurAng = NewMode;
  }

  void Square() {
    Enter(33);
    X.x2();
    SetX(X, true);
  }

  void Sqrt() {
    Enter(34);
    X.sqrt();
    if (X.isError()) {
      SetErrorState(false);
    }
    SetX(X, true);
  }

  void Reciprocal() {
    Enter(35);
    X.reciprocal();
    if (X.isError()) {
      SetErrorState(false);
    }
    SetX(X, true);
  }

  void Sin() {
    Enter(38);
    if (InvState) {
      X.asin(CurAng);
      if (X.isError()) {
        SetErrorState(false);
      }
    } else {
      X.sin(CurAng);
    }

    SetX(X, true);
  }

  void Cos() {
    Enter(39);
    if (InvState) {
      X.acos(CurAng);
      if (X.isError()) {
        SetErrorState(false);
      }
    } else {
      X.cos(CurAng);
    }

    SetX(X, true);
  }

  void Tan() {
    Enter(30);
    if (InvState) {
      X.atan(CurAng);
    } else {
      X.tan(CurAng);
    }

    if (X.isError()) {
      SetErrorState(false);
    }

    SetX(X, true);
  }

  void Ln() {
    Enter(23);
    if (InvState) {
      X.exp();
    } else {
      X.ln();
    }

    if (X.isError()) {
      SetErrorState(false);
    }

    SetX(X, true);
  }

  void Log() {
    Enter(28);
    if (InvState) {
      Number NewX = new Number(10.0);
      NewX.pow(X);
      X = NewX;
    } else {
      X.log();
    }

    if (X.isError()) {
      SetErrorState(false);
    }

    SetX(X, true);
  }

  void Percent() {
    Enter(20);

    if (InvState)
      X.mult(100);
    else {
      X.div(100);

      if (OpStackNext > 0) {
          X.mult(OpStack[OpStackNext - 1].Operand);
      }
    }

    if (X.isError()) {
      SetErrorState(false);
    }

    SetX(X, true);
  }

  void Pi() {
    if (InvState) /* extension! */ {
      X.trigScale(CurAng); // ?? check that it is on same order or reciprocal
    } else {
      X.Pi();
    }
    SetX(X, true);
  }

  void Int() {
    Enter(59);
    if (InvState) {
      X.fracPart();
    } else {
      X.intPart();
    }

    SetX(X, true);
  }

  void Abs() {
    Enter(50);

    X.abs();
    SetX(X, true);
  }

  void SwapT() {
    Enter(32);
    final Number SwapTemp = X;
    SetX(T, true);
    T = SwapTemp;
  }

  void Polar() {
    Enter(37);

    Number NewX, NewY;
    Number OldX, OldT;
    Number New180;
    Number New360;

    /*
     *  To ensure that we handle all cases equally, we initialize
     *  the semicircle and full-circle parameters for the various
     *  coordinate systems.
     *
     *  Full-Circle:
     *    Gradian system = 400
     *    Radian system  =  2π
     *    Degree system  = 360
     */
    switch (CurAng) {
      case ANG_GRAD:
        New180 = new Number(200);
        break;
      case ANG_RAD:
        New180 = new Number(Number.PI);
        break;
      case ANG_DEG:
      default: // Degrees
        New180 = new Number(180);
        break;
    }

    New360 = new Number(New180);
    New360.add(New180);   // We simply double whatever the 180 number was
    OldX = new Number(X);
    OldT = new Number(T);

    if (InvState) {

            /* Rectangular -> Polar  */
      Number X2 = new Number(X);
      X2.x2();
      Number Y2 = new Number(T);
      Y2.x2();

      NewX = new Number(X2);
      NewX.add(Y2);
      NewX.sqrt();

      NewY = X;
      NewY.div(T);
      NewY.atan(CurAng);

      /*
       *  The function must NOW determine the quadrant in which the
       *  result is delivered...
       *
       *                  y
       *                  ^
       *        II        |         I
       *           θ=+    |   θ=+
       *                  |
       *     -x <---------+---------> x
       *                  |
       *           θ=+    |   θ=-
       *        III       |        IV
       *                  V
       *                  -y
       *
       *    For Cartesian (Rectangular) to Polar, we need to be careful
       *    to note when the hardware inverts the sign of the angle in
       *    the expected result so we use the following tests:
       *
       *      (x= 1, y= 0) = (R=1,     θ=   0°) Ok
       *      (x= 1, y= 1) = (R=1.41~, θ=  45°) Ok
       *      (x= 0, y= 1) = (R=1,     θ=  90°) Ok
       *      (x=-1, y= 1) = (R=1.41~, θ= 135°) Ok
       *      (x=-1, y= 0) = (R=1,     θ= 180°) Ok
       *      (x=-1, y=-1) = (R=1.41~, θ= 225°) Ok
       *      NOTE: At ≥270°, the Hardware returns
       *            a NEGATIVE Angular result
       *      (x= 0, y=-1) = (R=1,     θ= -90°) Ok
       *      (x= 1, y=-1) = (R=1.41~, θ= -45°) Ok
       *
       *  General Rules:
       *
       *  Quadrant  Value of atan
       *  I         (Use Calculator Value) = 45
       *            Coordinates (8[x],8[t])
       *  II        Add 180 to the calculator value (Use Calculator Value)
       *            Coordinates (-8[x],8[t])
       *  III       Add 180 to the calculator value
       *            Coordinates (-8[x],-8[t])
       *  IV        Add 360 to the calculator value (Use Calculator Value)
       *            Coordinates (8[x],-8[t])
       *
       *  OldX is "Y"
       *  OltT is "X"
       */
      if (OldX.compareTo(Number.ZERO) < 0) {
        if (OldT.compareTo(Number.ZERO) < 0) {
          /*
           * Quadrant III
           * -X,-Y
           */
          NewY.add(New180);
        } else {
          /*
           * Quadrant IV
           * +X,-Y
           */
          NewY.abs();
          NewY.negate();
        }

      } else {
        if (OldT.compareTo(Number.ZERO) < 0) {
          /*
           * Quadrant II
           * -X,+Y
           */
          NewY.add(New180);
        } else {
          /*
           * Quadrant I
           * +X,+Y
           */
          // We don't bother adding because the signs are correct
        }
      }
    } else {
      /*
       *  to convert from Cartesian Coordinates (x,y) to Polar Coordinates (r,θ):
       *
       *    r = √ ( x2 + y2 )
       *    θ = tan-1 ( y / x )
       *
       *  Calculators may give the wrong value of tan-1 () when x or y are negative
       *
       *  The function must NOW determine the quadrant in which the
       *  result is delivered...
       *    t=R
       *    θ=X
       *  Result:
       *    t=x
       *    X=y
       *
       *    On the Hardware:
       *
       *    For Polar to Cartesian (Rectangular):
       *
       *      Java Reliable below θ=180°             Result OK  Modified
       *                                             in Java?   Routine?
       *      ------------------------------------------------------------
       *      (R=1, θ=   0°) = (x= 1,     y=  0    ) Ok         Ok
       *      (R=1, θ=  45°) = (x= 0.70~, y=  0.70~) Ok         Ok
       *      (R=1, θ=  90°) = (x= 0,     y=  1    ) Ok         Ok
       *      (R=1, θ= 135°) = (x=-0.70~, y=  0.70~) Ok         Ok
       *      ------------------------------------------------------------
       *      (R=1, θ= 180°) = (x=-1,     y=  0    ) NO         Ok
       *      (R=1, θ= 225°) = (x=-0.70~, y= -0.70~) Ok         Ok
       *      (R=1, θ= 270°) = (x= 0,     y= -1    ) NO         Ok
       *      (R=1, θ= 315°) = (x= 0.70~, y= -0.70~) Ok         Ok
       *      ------------------------------------------------------------
       *
       *    To Address the Discrepancies:
       *
       *    We need to modify the routine to calculate the return
       *    quadrants because the Java library doesn't do an appropriate
       *    job returning these values (at θ above 180°).
       *
       *    The means by which we can keep the angular values of θ
       *    below 180 requires a little trick: we take the modulo(180) of θ
       *    and use OldX (Modulo 360) value to determine the quadrant,
       *    and therefore, correct the signs of the result are returned.
       *
       *    This algorithm works for all values of positive and negative θ.
       *
       */

      /* Polar -> Rectangular */

      Number Xtheta = new Number(X);
      Number Xmod360 = new Number(X);

      Xtheta.rem(New180);
      Xmod360.rem(New360);
      Xmod360.abs();

      Number Xcos = new Number(Xtheta);
      Number Xsin = new Number(Xtheta);

      Xcos.cos(CurAng);
      Xsin.sin(CurAng);

      NewX = new Number(T);
      NewX.mult(Xcos);

      NewY = new Number(T);
      NewY.mult(Xsin);

      if (Xmod360.compareTo(New180) >= 0) {
        NewY.negate();
        NewX.negate();
      }
    }

    T = NewX;
    SetX(NewY, true);
  }

  void D_MS() {
    Enter(88);

    // Must be done on the displayed value and not X. That is if Fix-01 is set, the number must
    // be with a single digit.

    // X = new Number (CurDisplay);

    if(CurNrDecimals == -1)
      X.roundUpNDigits(9);
    else
      X.roundUpNDigits(CurNrDecimals);

    Number Sign = new Number(X);
    Sign.signum();

    X.abs();

    Number Degrees = new Number(X);
    Degrees.intPart();

    Number Fraction = new Number(X);
    Fraction.fracPart();

    Number Minutes = new Number(Fraction);
    Number Seconds;

    if (InvState) {
      Minutes.mult(60);
      Seconds = new Number(Minutes);

      Minutes.intPart();
      Seconds.fracPart();

      Minutes.div(100);
      Seconds.mult(6);
      Seconds.div(1000);
    } else {
      Minutes.mult(100);
      Seconds = new Number(Minutes);

      Minutes.intPart();
      Seconds.fracPart();

      Minutes.div(60);
      Seconds.div(36);
    }

    X.set(Degrees);

    X.add(Minutes);
    X.add(Seconds);
    X.mult(Sign);

    SetX(X, true);
  }

  private void ShowCurProg() {
    SetShowing
       (
          CurBank != 0 ?
             String.format
                (
                   Global.StdLocale,
                   "%02d %03d %02d",
                   CurBank,
                   PC,
                   (int) Bank[CurBank].Program[PC]
                )
             :
             String.format(Global.StdLocale, "%03d %02d", PC, (int) Program[PC])
       );
  }

  private void TraceDisplay
     (
        boolean Data,
        String Label
     ) {
    String L4 = (Label.length() == 1 ? "  " + Label + " "
       : (Label.length() == 4 ? Label :
       String.format(Global.StdLocale, "%4s", Label)));
    if (TracePrintActivated && Global.Print != null)
      PrintDisplay(Data, false, L4);
  }

  void PrintDisplay
     (
        boolean Data,
        boolean Labelled,
        String Label
     ) {
    if (Global.Print != null && CurDisplay != null) {
      final byte[] Translated = new byte[Printer.CharColumns];
      String Disp = (Data ? CurDisplay : "");
      Global.Print.Translate
         (
            String.format
               (
                  Global.StdLocale,
                  String.format
                     (Global.StdLocale, "%%%ds", Math.max(1, 14 - Disp.length())),
                  " "
               ).substring(1) /* because I can't have a 0-length format width */
               + Disp
               + (InErrorState() ? "?" : " ")
               + (Label.length() == 4 ? Label : ""),
            Translated
         );

      if (Labelled) {
        /* clear left-most characters of the last column */
        Translated[Printer.CharColumns - 5] = 0;
        System.arraycopy
           (
              PrintRegister,Printer.CharColumns - 4,
              Translated,   Printer.CharColumns - 4,
              4
           );
      }
      Global.Print.Render(Translated);
    }
  }

  void SetProgMode
     (
        boolean NewProgMode
     ) {
    // entering the program mode always clear the error state
    inError = false;
    ProgMode = NewProgMode;
    if (ProgMode) {
      ShowCurProg();
    } else {
      SetShowing(CurDisplay);
    }
  }

  void ClearMemories() {
    Enter(24); /*?*/
    for (int i = 0; i < MaxMemories; ++i) {
      Memory[i].set(0);
    }
  }

  void ClearProgram() {
    Enter(29); /*?*/
    for (int i = 0; i < MaxProgramSteps; ++i) {
      Program[i] = (byte) 0;
    }
    PC = 0;
    ReturnLast = -1;
    T.set(0);
    for (int i = 0; i < MaxFlags; ++i) {
      Flag[i] = false;
    }
      /* wipe any loaded help as well */
    if (CurBank == 0) {
      Global.Label.SetHelp(null, null);
    }
    Bank[0].Card = null;
    Bank[0].Help = null;
  }

  void SelectProgram
     (
        int ProgNr,
        boolean Indirect
     ) {
    if (ProgNr >= 0) {
      boolean OK = false;
      do /*once*/ {
        if (Indirect) {
          ProgNr = (ProgNr + RegOffset) % 100;
          if (ProgNr >= MaxMemories)
            break;
          ProgNr = (int) Memory[ProgNr].getInt();
          if (ProgNr < 0)
            break;
        }
        if (ProgNr >= MaxBanks || Bank[ProgNr] == null)
          break;
        FillInLabels(ProgNr); /* if not done already */
        if (TaskRunning) {
          NextBank = ProgNr;
        } else {
          CurBank = ProgNr;
          if (Global.Label != null) {
            Global.Label.SetHelp(Bank[ProgNr].Card, Bank[ProgNr].Help);
          }
        }
        /* all done */
        OK = true;
      }
      while (false);
      if (!OK) {
        SetErrorState(true);
      }
    }
  }

  static final int MEMOP_STO = 1;
  static final int MEMOP_RCL = 2;
  static final int MEMOP_ADD = 3;
  static final int MEMOP_SUB = 4;
  static final int MEMOP_MUL = 5;
  static final int MEMOP_DIV = 6;
  static final int MEMOP_EXC = 7;

  private static final int[] MEMOP_CODE = {42, 43, 44, 44, 49, 49, 48};

  void MemoryOp
     (
        int Op,
        int RegNr,
        boolean Indirect
     ) {
    if (RegNr >= 0) {
      Enter(MEMOP_CODE[Op - 1]);
      if (InvState) {
        switch (Op) {
          case MEMOP_ADD:
            Op = MEMOP_SUB;
            break;
          case MEMOP_MUL:
            Op = MEMOP_DIV;
            break;
        }
      }
      boolean OK = false; /* to begin with */
      do /*once*/ {
        RegNr = (RegNr + RegOffset) % 100;
        if (RegNr >= MaxMemories)
          break;
        if (Indirect) {
          RegNr = ((int) Memory[RegNr].getInt() + RegOffset) % 100;
          if (RegNr < 0 || RegNr >= MaxMemories)
            break;
        }
        switch (Op) {
          case MEMOP_STO:
            Memory[RegNr] = new Number(X);
            break;
          case MEMOP_RCL:
            SetX(Memory[RegNr], true);
            ExponentEntered = false;
            break;
          case MEMOP_ADD:
            Memory[RegNr].add(X);
            break;
          case MEMOP_SUB:
            Memory[RegNr].sub(X);
            break;
          case MEMOP_MUL:
            Memory[RegNr].mult(X);
            break;
          case MEMOP_DIV:
            Memory[RegNr].div(X);
            if (Memory[RegNr].isError())
              SetErrorState(false);
            break;
          case MEMOP_EXC:
            final Number Temp = Memory[RegNr];
            Memory[RegNr] = X;
            SetX(Temp, true);
            break;
        }
        /* all done */
        OK = true;
      }
      while (false);
      if (!OK) {
        SetErrorState(true);
      }
    }
  }

  private static final int HIROP_STO = 0;
  private static final int HIROP_RCL = 1;
  private static final int HIROP_ADD = 3;
  private static final int HIROP_MUL = 4;
  private static final int HIROP_SUB = 5;
  private static final int HIROP_DIV = 6;

  private void SetPrintRegister(int ColStart, long Contents) {
    for (int i = 5; ; ) {
      if (i == 0)
        break;
      --i;

      byte val = (byte) (Contents % 100);
      byte col = (byte) (val % 10);
      byte line = (byte) (val / 10);

      if (col > 7) {
        col -= 8;
        line += 1;
      }

      val = (byte) (line * 10 + col);

      PrintRegister[i + ColStart] = val;
      Contents /= 100;
    }
  }

  private void HirOp
     (
        int Code
     ) {
    if (Code >= 0) {
      Enter(Code);
      boolean OK = false; /* to begin with */
      int Op = 0;
      int RegNr = Code;
      while (RegNr > 10) {
        RegNr = RegNr - 10;
        Op++;
      }
      // Note that RegNr = 0 must reference X
      do /*once*/ {
        if (RegNr > MaxOpStack)
          break;

        // ensure that we are not referencing a non initilized stack entry
        if (RegNr != 0 && OpStack[RegNr - 1] == null)
          OpStack[RegNr - 1] = new OpStackEntry(new Number(), Code, 0);

        switch (Op) {
          case HIROP_STO:
            if (RegNr != 0)
              OpStack[RegNr - 1].Operand = new Number(X);
            break;
          case HIROP_RCL:
            if (RegNr != 0)
              SetX(OpStack[RegNr - 1].Operand, true);
            break;
          case HIROP_ADD:
            if (RegNr == 0)
              X.add(X);
            else
              OpStack[RegNr - 1].Operand.add(X);
            break;
          case HIROP_SUB:
            if (RegNr == 0)
              X.set(0);
            else
              OpStack[RegNr - 1].Operand.sub(X);
            break;
          case HIROP_MUL:
            if (RegNr == 0)
              X.x2();
            else
              OpStack[RegNr - 1].Operand.mult(X);
            break;
          case HIROP_DIV:
            if (RegNr == 0)
              X.set(1);
            else
              OpStack[RegNr - 1].Operand.div(X);

            if (X.isError())
              SetErrorState(false);
            break;
        }
        /* all done */
        OK = true;
      }
      while (false);

      /* emulate the printer registers HIR 05 is the OP 01 register and so on */

      if (RegNr >= 5 && RegNr <= 8) {
        final Number Factor = new Number(1e10);
        Number OpVal = new Number(OpStack[RegNr - 1].Operand);

        if (OpVal.compareTo(10) < 0)
          OpVal.mult(100);
        else if (OpVal.compareTo(100) < 0)
          OpVal.mult(10);
        else if (OpVal.compareTo(1000) >= 0)  // ??PO was 100 again in previous code (and mult(1))
          OpVal.div(10);

        OpVal.fracPart();
        OpVal.mult(Factor);
        OpVal.abs();
        OpVal.intPart();

        final int ColStart = (RegNr - 5) * 5;

        SetPrintRegister(ColStart, OpVal.getInt());
      }

      if (!OK) {
        SetErrorState(true);
      }
    }
  }

  private boolean StatsRegsAvailable() {
    /* ensures the statistics registers are accessible with the current
       partition/offset setting. */
    return
       /* sufficient to check that first & last reg are within accessible range */
       (RegOffset + STATSREG_FIRST) % 100 < MaxMemories
          &&
          (RegOffset + STATSREG_LAST) % 100 < MaxMemories;
  }

  private Number StatsSlope() {
    /* estimated slope from linear regression, used in a lot of other results. */
    Number Result;

    Number C1 = new Number(Memory[(RegOffset + STATSREG_SIGMAX) % 100]);
    C1.mult(Memory[(RegOffset + STATSREG_SIGMAY) % 100]);
    C1.div(Memory[(RegOffset + STATSREG_N) % 100]);
    C1.negate();
    C1.add(Memory[(RegOffset + STATSREG_SIGMAXY) % 100]);

    Number C2 = new Number(Memory[(RegOffset + STATSREG_SIGMAX) % 100]);
    C2.x2();
    C2.div(Memory[(RegOffset + STATSREG_N) % 100]);
    C2.negate();
    C2.add(Memory[(RegOffset + STATSREG_SIGMAX2) % 100]);

    Result = C1;
    Result.div(C2);

    return Result;
  }

  void SpecialOp
     (
        int OpNr,
        boolean Indirect
     ) {
    if (OpNr >= 0) {
      Enter(OpNr);
      boolean OK = false;
      do /*once*/ {
        if (Indirect) {
          OpNr = (OpNr + RegOffset) % 100;
          if (OpNr >= MaxMemories)
            break;
          // if Memory[OpNr] is negative, do nothing, no error
          if (Memory[OpNr].getSignum() < 0) {
            OK = true;
            break;
          }
          OpNr = (int) Memory[OpNr].getInt();
        }
        if (OpNr >= 20 && OpNr < 30) {
          Memory[OpNr - 20 + RegOffset].add(Number.ONE);
          OK = true;
          break;
        }
        if (OpNr >= 30 && OpNr < 40) {
          Memory[OpNr - 30 + RegOffset].sub(Number.ONE);
          OK = true;
          break;
        }
        switch (OpNr) {
          case 0:
            for (int i = 0; i < PrintRegister.length; ++i) {
              PrintRegister[i] = 0;
            }
            OK = true;
            break;
          case 1:
          case 2:
          case 3:
          case 4: {
            final int ColStart = (OpNr - 1) * 5;
            long Contents = Math.abs(X.getInt());

            // emulate the HIR register (OP 01 use HIR 05, 02 -> 06, 03 -> 07 and 04 -> 08) */

            if (OpStack[OpNr + 3] == null)
              OpStack[OpNr + 3] = new OpStackEntry(null, OpNr, 0);

            final Number E12 = new Number(1e12);
            OpStack[OpNr + 3].Operand = new Number(X);
            OpStack[OpNr + 3].Operand.div(E12);

            /* manual says fractional part of display is discarded as a side-effect,
               tested on a real TI-59 */

            X.intPart();
            X.abs();
            SetX(X, true);

            // but the decimal are considered for printing (just spaces)

            if (CurNrDecimals != -1)
              Contents = Contents * (long) Math.pow(10, CurNrDecimals);

            SetPrintRegister(ColStart, Contents);
          }
          OK = true;
          break;
          case 5:
            if (Global.Print != null) {
              Global.Print.Render(PrintRegister);
            }
            OK = true;
            break;
          case 6:
            PrintDisplay(true, true, "");
            OK = true;
            break;
          case 7:
            if (Global.Print != null) {
              Enter(OpNr);
              final int PlotX = (int) X.getInt();
              if (PlotX >= 0 && PlotX < Printer.CharColumns) {
                final byte[] Plot = new byte[Printer.CharColumns];
                for (int i = 0; i < Printer.CharColumns; ++i) {
                  Plot[i] = (byte) (i == PlotX ? 51 : 0);
                }
                Global.Print.Render(Plot);
              } else {
                SetErrorState(false);
              }
            }
            OK = true;
            break;
          case 8:
            if (!TaskRunning) {
              StartLabelListing();
            }
            OK = true;
            break;
          case 9:
            if (!TaskRunning && CurBank > 0) {
              final ProgBank Bank = this.Bank[CurBank];
              if
                 (
                 Bank != null
                    &&
                    Bank.Program != null
                    &&
                    Bank.Program.length <= MaxProgramSteps
                 ) {
                System.arraycopy
                   (
                      Bank.Program, 0,
                      Program, 0,
                      Bank.Program.length
                   );
                for (int i = Bank.Program.length; i < MaxProgramSteps; ++i) {
                  Program[i] = 0;
                }
                ResetLabels();
              } else {
                SetErrorState(true);
              }
            }
            OK = true;
            break;
          case 10:
            X.signum();
            SetX(X, true);
            OK = true;
            break;
          case 11:
                    /* sample variance */
            if (StatsRegsAvailable()) {
              final Number N_SIGMAX2 = Memory[(RegOffset + STATSREG_SIGMAX2) % 100];
              final Number N_SIGMAY2 = Memory[(RegOffset + STATSREG_SIGMAY2) % 100];
              final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
              final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
              final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];

              Number N_N_2 = new Number(N_N);
              N_N_2.x2();

              Number C1 = new Number(N_SIGMAX);
              C1.x2();
              C1.div(N_N_2);

              Number C2 = new Number(N_SIGMAY);
              C2.x2();
              C2.div(N_N_2);

              T = new Number(N_SIGMAX2);
              T.div(N_N);
              T.sub(C1);

              X = new Number(N_SIGMAY2);
              X.div(N_N);
              X.sub(C2);

              SetX(X, true);
              OK = true;
            }
            break;
          case 12:
                    /* slope and intercept */
            if (StatsRegsAvailable()) {
              final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
              final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
              final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];
              final Number m = StatsSlope();

              T = m;

              Number C1 = new Number(m);
              C1.mult(N_SIGMAX);

              X = new Number(N_SIGMAY);
              X.sub(C1);
              X.div(N_N);
              SetX(X, true);
              OK = true;
            }
            break;
          case 13:
                    /* correlation coefficient */
            if (StatsRegsAvailable()) {
              final Number N_SIGMAX2 = Memory[(RegOffset + STATSREG_SIGMAX2) % 100];
              final Number N_SIGMAY2 = Memory[(RegOffset + STATSREG_SIGMAY2) % 100];
              final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
              final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
              final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];
              final Number m = StatsSlope();

              Number C1 = new Number(N_SIGMAX);
              C1.x2();
              C1.div(N_N);
              C1.negate();
              C1.add(N_SIGMAX2);
              C1.sqrt();

              Number C2 = new Number(N_SIGMAY);
              C2.x2();
              C2.div(N_N);
              C2.negate();
              C2.add(N_SIGMAY2);
              C2.sqrt();

              X = m;
              X.mult(C1);
              X.div(C2);

              SetX(X, true);
              OK = true;
            }
            break;
          case 14:
                    /* estimated y from x */
            if (StatsRegsAvailable()) {
              final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
              final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
              final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];
              final Number m = StatsSlope();

              Number C1 = new Number(N_SIGMAX);
              C1.mult(m);
              C1.negate();
              C1.add(N_SIGMAY);
              C1.div(N_N);

              X.mult(m);
              X.add(C1);
              SetX(X, true);
              OK = true;
            }
            break;
          case 15:
                    /* estimated x from y */
            if (StatsRegsAvailable()) {
              final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
              final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
              final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];
              final Number m = StatsSlope();

              Number C1 = new Number(N_SIGMAX);
              C1.mult(m);
              C1.negate();
              C1.add(N_SIGMAY);
              C1.div(N_N);

              X.sub(C1);
              X.div(m);

              SetX(X, true);
              OK = true;
            }
            break;
          case 17:
            /* not implemented, fall through */
          case 16:
            Number N = new Number(MaxMemories);
            N.sub(1);
            N.div(100);
            N.add(MaxProgramSteps);
            N.sub(1);
            SetX(N, true);
            OK = true;
            break;
          case 18:
          case 19:
            if (OpNr == (InErrorState() ? 19 : 18)) {
              Flag[FLAG_ERROR_COND] = !InvState;
              /* meaning of INV Op 18/19 taken from “52 Notes” volume 2 number 8 */
            }
            OK = true;
            break;
          /* 20-39 handled above */
          case 40:
            if (Global.Print != null) {
              Flag[FLAG_ERROR_COND] = true;
            }
            OK = true;
            break;
          case 50: /* extension! TIME IN SECONDS */
            X.set(System.currentTimeMillis() / 1000.0);
            SetX(X, true);
            OK = true;
            break;
          case 51: /* extension! RANDOM */ {
            byte[] V = new byte[7];
            Random.nextBytes(V);
            X.set((double) (((long) V[0] & 255)
               |
               ((long) V[1] & 255) << 8
               |
               ((long) V[2] & 255) << 16
               |
               ((long) V[3] & 255) << 24
               |
               ((long) V[4] & 255) << 32
               |
               ((long) V[5] & 255) << 40
               |
               ((long) V[6] & 255) << 48
            )
               /
               (double) 0x0100000000000000L);
            SetX(X, true);
          }
          OK = true;
          break;
          case 52: /* extension! DISPLAY REG OFFSET */
            X.set(RegOffset);
            SetX(X, true);
            OK = true;
            break;
          case 53: /* extension! SET REG OFFSET */ {
            final int NewRegOffset = (int) X.getInt();
            if (NewRegOffset >= 0 && NewRegOffset < 100) {
              RegOffset = NewRegOffset;
              OK = true;
            }
          }
          break;
          case 96:
            if (InvState) {
              ExportNew(false);
            } else {
              ImportNew();
            }
            OK = true;
            break;
          case 97:
            if (InvState) {
              ExportNew(true);
            } else {
              ResetImport();
            }
            OK = true;
            break;
          case 98:
            TracePrintActivated = !TracePrintActivated;
            OK = true;
            break;
          case 99: /* extension! Test */
            int Result = Global.Test.Run();

            X.set(Result);
            SetX(X, true);

            if (Result < 0)
              SetErrorState(false);

            OK = true;
            break;
        }
      }
      while (false);
      if (!OK) {
        SetErrorState(true);
      }
    }
  }

  void StatsSum() {
    Enter(78);
    if (StatsRegsAvailable()) {
      Number X2 = new Number(X);
      X2.x2();

      Number T2 = new Number(T);
      T2.x2();

      Number XT = new Number(X);
      XT.mult(T);

      if (InvState) {
        /* remove sample */
        Memory[(RegOffset + STATSREG_SIGMAY) % 100].sub(X);
        Memory[(RegOffset + STATSREG_SIGMAY2) % 100].sub(X2);
        Memory[(RegOffset + STATSREG_N) % 100].sub(1);
        Memory[(RegOffset + STATSREG_SIGMAX) % 100].sub(T);
        Memory[(RegOffset + STATSREG_SIGMAX2) % 100].sub(T2);
        Memory[(RegOffset + STATSREG_SIGMAXY) % 100].sub(XT);

        T.sub(1);
      } else {
        /* accumulate sample */
        Memory[(RegOffset + STATSREG_SIGMAY) % 100].add(X);
        Memory[(RegOffset + STATSREG_SIGMAY2) % 100].add(X2);
        Memory[(RegOffset + STATSREG_N) % 100].add(1);
        Memory[(RegOffset + STATSREG_SIGMAX) % 100].add(T);
        Memory[(RegOffset + STATSREG_SIGMAX2) % 100].add(T2);
        Memory[(RegOffset + STATSREG_SIGMAXY) % 100].add(XT);

        T.add(1);
      }

      SetX(Memory[(RegOffset + STATSREG_N) % 100], true);
    } else {
      SetErrorState(true);
    }
  }

  void StatsResult() {
    if (StatsRegsAvailable()) {
      if (InvState) {
        final Number N_SIGMAX2 = Memory[(RegOffset + STATSREG_SIGMAX2) % 100];
        final Number N_SIGMAY2 = Memory[(RegOffset + STATSREG_SIGMAY2) % 100];
        final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
        final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
        final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];

        Number N_SIGMAX_2 = new Number(N_SIGMAX);
        N_SIGMAX_2.x2();
        N_SIGMAX_2.div(N_N);

        Number N_SIGMAY_2 = new Number(N_SIGMAY);
        N_SIGMAY_2.x2();
        N_SIGMAY_2.div(N_N);

        Number N_N1 = new Number(N_N);
        N_N1.sub(1);

        T = new Number(N_SIGMAX2);
        T.sub(N_SIGMAX_2);
        T.div(N_N1);
        T.sqrt();

        X = new Number(N_SIGMAY2);
        X.sub(N_SIGMAY_2);
        X.div(N_N1);
        X.sqrt();

        SetX(X, true);
      } else {
        final Number N_SIGMAX = Memory[(RegOffset + STATSREG_SIGMAX) % 100];
        final Number N_SIGMAY = Memory[(RegOffset + STATSREG_SIGMAY) % 100];
        final Number N_N = Memory[(RegOffset + STATSREG_N) % 100];

        T = new Number(N_SIGMAX);
        T.div(N_N);

        X = new Number(N_SIGMAY);
        X.div(N_N);

        SetX(X, true);
      }
    } else {
      SetErrorState(true);
    }
  }

  void GetNextImport() {
    /* gets next value from current importer, if any. */
    boolean OK = true;
    boolean EOF = true;
    Number Value;
    do /*once*/ {
      if (Import == null)
        break;
      try {
        Value = Import.Next();
        EOF = false;
      } catch (ImportEOFException Done) {
        Import.End();
        break;
      } catch (Persistent.DataFormatException Bad) {
        android.widget.Toast.makeText
           (
              ctx,
              String.format
                 (
                    Global.StdLocale,
                    ctx.getString(R.string.import_error),
                    Bad.toString()
                 ),
              android.widget.Toast.LENGTH_LONG
           ).show();
        OK = false;
        Import.End();
        break;
      }
      SetX(Value, false);
      OK = true;
    }
    while (false);
    Flag[FLAG_ERROR_COND] = EOF;
    if (!OK) {
      SetErrorState(true);
    }
  }

  void StepPC
     (
        boolean Forward
     ) {
    if (Forward) {
      if (PC < Bank[CurBank].Program.length - 1) {
        ++PC;
        ShowCurProg();
      }
    } else {
      if (PC > 0) {
        --PC;
        ShowCurProg();
      }
    }
  }

  void ResetLabels() {
    /* invalidates labels because of a change to user-entered program contents. */
    Bank[0].Labels = null;
  }

  boolean ProgramWritable() {
    /* can the user enter code into the currently-selected bank. */
    return CurBank == 0;
  }

  void StoreInstr
     (
        int Instr
     ) {
    ResetLabels();
    Program[PC] = (byte) Instr;
    if (PC < MaxProgramSteps - 1) {
      ClearDelayedStep();
      if (DelayTask == null) {
        DelayTask = new DelayedStep();
      }
      ShowCurProg(); /* show updated contents of current location */
      ++PC;
      /* give user a chance to see current contents before stepping to next location
         -- this is a nicety the original calculator did not have */
      BGTask.postDelayed(DelayTask, 250);
    } else {
      SetProgMode(false);
    }
  }

  void StorePrevInstr
     (
        int Instr
     ) {
    if (PC > 0) {
      --PC;
      StoreInstr(Instr);
    } else {
      SetErrorState(true);
    }
  }

  void InsertAtCurInstr() {
    System.arraycopy(Program, PC, Program,PC + 1, MaxProgramSteps - 1 - PC);
    Program[PC] = (byte) 0;
    ResetLabels();
    ShowCurProg();
  }

  void DeleteCurInstr() {
    System.arraycopy(Program,PC + 1, Program, PC, MaxProgramSteps - 1 - PC);
    Program[MaxProgramSteps - 1] = (byte) 0;
    ResetLabels();
    ShowCurProg();
  }

  void StepProgram() {
    SetProgramStarted(false);
    Interpret(true);
    PC = RunPC;
    SetProgramStopped();
    /* fixme: if I just executed a Pgm nn instruction, this setting
       of NextBank will not be properly passed to the next instruction */
    /* fixme: should single-stepping to a subroutine call cause execution
       of the complete subroutine, stopping when it returns? */
  }

  class TaskRunner implements Runnable {
    public void run() {
      if (TaskRunning && RunProg != null) {
        RunProg.run();
        ContinueTaskRunner();
      }
    }
  }

  private void ContinueTaskRunner() {
    if (TaskRunning) {
      if (ProgRunningSlowly) {
        Global.Disp.SetShowing(LastShowing);
        BGTask.postDelayed(ExecuteTask, 600);
      } else {
        /* run as fast as possible */
        BGTask.post(ExecuteTask);
      }
    }
  }

  class ProgRunner implements Runnable {
    public void run() {
      Interpret(true);
    }
  }

  class LabelLister implements Runnable {
    class LabelDef {
      int Code;
      int Loc;

      LabelDef
         (
            int Code,
            int Loc
         ) {
        this.Code = Code;
        this.Loc = Loc;
      }
    }

    final LabelDef[] SortedLabels;
    int Index;

    LabelLister() {
      super();
      final java.util.TreeSet<LabelDef> SortedLabelsTemp =
         new java.util.TreeSet<LabelDef>
            (
               new java.util.Comparator<LabelDef>() {
                 @Override
                 public int compare
                    (
                       LabelDef Label1,
                       LabelDef Label2
                    ) {
                   return Label1.Loc - Label2.Loc;
                 } /*compare*/
               }
            );
      for (int i =0; i < Bank[0].Labels.size(); i++) {
        final int Value = Bank[0].Labels.valueAt(i);
        if (Value >= PC + 2) {
          SortedLabelsTemp.add(new LabelDef(Bank[0].Labels.keyAt(i), Value));
        }
      }
      SortedLabels = new LabelDef[SortedLabelsTemp.size()];
      int i = 0;
      for (LabelDef ThisLabel : SortedLabelsTemp) {
        SortedLabels[i++] = ThisLabel;
      }
      Index = 0;
    }

    public void run() {
      if (Index < SortedLabels.length) {
        final LabelDef ThisLabel = SortedLabels[Index];
        final byte[] Translated = new byte[Printer.CharColumns];
        Global.Print.Translate
           (
              String.format
                 (
                    Global.StdLocale,
                    "      %03d  %02d %3s",
                    ThisLabel.Loc - 1,
                          /* seems to match original, pointing at label symbol
                            (location of "Lbl" + 1) */
                    ThisLabel.Code,
                    Printer.KeyCodeSym(ThisLabel.Code)
                 ),
              Translated
           );
        Global.Print.Render(Translated);
        ++Index;
      }
      if (Index == SortedLabels.length) {
        StopTask();
      }
    }
  }

  class RegisterLister implements Runnable {
    int CurReg;
    final int EndReg;
    boolean Wrapped;

    RegisterLister
       (
          int StartReg
       ) {
      CurReg = (StartReg + RegOffset) % 100;
      EndReg = (RegOffset + MaxMemories) % 100;
      Wrapped = CurReg < EndReg;
    }

    public void run() {
      if (CurReg == MaxMemories && !Wrapped) {
        CurReg = 0;
        Wrapped = true;
      }
      if (CurReg < (Wrapped ? EndReg : MaxMemories)) {
        final byte[] Translated = new byte[Printer.CharColumns];
        Global.Print.Translate
           (
              String.format
                 (
                    Global.StdLocale,
                    "%16s  %02d",
                    FormatNumber(Memory[CurReg], FORMAT_FIXED, -1, true),
                    CurReg /* post-RegOffset number */
                 ),
              Translated
           );
        Global.Print.Render(Translated);
        ++CurReg;
      }
      if (Wrapped && CurReg >= EndReg) {
        StopTask();
      }
    }
  }

  class ProgramLister implements Runnable {
    int ListPC, EndPC;
    int Expecting;
    boolean InvState;
    /* following original, state machine is somewhat simpler than full interpreter/disassembler */
    final int ExpectOpcode = 0;
    final int ExpectTwoDigits = 1;
    final int ExpectLoc = 2;
    final int ExpectRegFlag = 3;
    final int ExpectTwoDigitsPlusLoc = 4;
    final int ExpectRegPlusLoc = 5;
    final int ExpectSym = 6;

    ProgramLister() {
      ListPC = PC;
      EndPC = MaxProgramSteps;
      for (; ; ) /* omit trailing zero bytes */ {
        if (EndPC == 0)
          break;
        --EndPC;
        if (Program[EndPC] != 0)
          break;
      }
      Expecting = ExpectOpcode;
      InvState = false;
    }

    public void run() {
      if (ListPC < MaxProgramSteps) {
        final int Val = Program[ListPC];
        String Symbol = Printer.KeyCodeSym(Val);
        int NextExpecting = ExpectOpcode;
        boolean WasModifier = false;
        switch (Expecting) {
          case ExpectOpcode:
            if (Val < 10) {
              /* digit entry */
              Symbol = String.format(Global.StdLocale, " %1d ", Val);
            }
            switch (Val) {
              case 22: /*INV*/
                /* case 27: */ /*?*/
                InvState = !InvState;
                WasModifier = true;
                break;
              case 36: /*Pgm*/
              case 42: /*STO*/
              case 43: /*RCL*/
              case 44: /*SUM*/
              case 48: /*Exc*/
              case 49: /*Prd*/
              case 62: /*Pgm Ind*/
              case 63: /*Exc Ind*/
              case 64: /*Prd Ind*/
              case 69: /*Op*/
              case 72: /*STO Ind*/
              case 73: /*RCL Ind*/
              case 74: /*SUM Ind*/
              case 83: /*GTO Ind*/
              case 84: /*Op Ind*/
                NextExpecting = ExpectTwoDigits;
                break;
              case 57: /*Fix*/
                if (!InvState) {
                  NextExpecting = ExpectRegFlag;
                }
                break;
              case 61: /*GTO*/
                NextExpecting = ExpectLoc;
                break;
              case 71: /*SBR*/
                if (!InvState) {
                  NextExpecting = ExpectLoc;
                }
                break;
              case 67: /*x=t*/
              case 77: /*x≥t*/
                NextExpecting = ExpectLoc;
                break;
              case 76: /*Lbl*/
                NextExpecting = ExpectSym;
                break;
              case 86: /*St flg*/
                NextExpecting = ExpectRegFlag;
                break;
              case 87: /*If flg*/
              case 97: /*Dsz*/
                NextExpecting = ExpectRegPlusLoc;
                break;
            }
            break;
          case ExpectTwoDigits:
            Symbol = String.format(Global.StdLocale, " %02d", Val);
            break;
          case ExpectLoc:
            if (Val < 10 || Val == 40) {
              NextExpecting = ExpectTwoDigits;
            }
            break;
          case ExpectRegFlag:
            if (Val == 40) {
              NextExpecting = ExpectTwoDigits;
            } else {
              Symbol = String.format(Global.StdLocale, " %02d", Val);
            }
            break;
          case ExpectTwoDigitsPlusLoc:
            Symbol = String.format(Global.StdLocale, " %02d", Val);
            NextExpecting = ExpectLoc;
            break;
          case ExpectRegPlusLoc:
            if (Val == 40) {
              NextExpecting = ExpectTwoDigitsPlusLoc;
            } else {
              Symbol = String.format(Global.StdLocale, " %02d", Val);
              NextExpecting = ExpectLoc;
            }
            break;
          case ExpectSym:
            Symbol = Printer.KeyCodeSym(Val);
            break;
        }
        if (!WasModifier) {
          InvState = false;
        }
        final byte[] Translated = new byte[Printer.CharColumns];
        Global.Print.Translate
           (
              String.format
                 (
                    Global.StdLocale,
                    "       %03d  %02d %3s  ",
                    ListPC,
                    Val,
                    Symbol
                 ),
              Translated
           );
        Global.Print.Render(Translated);
        Expecting = NextExpecting;
        ++ListPC;
      }
      if (ListPC == MaxProgramSteps || ListPC > EndPC && Expecting == ExpectOpcode) {
        StopTask();
      }
    }
  }

  private void SetShowingRunning() {
    Global.Disp.SetShowingRunning(Import != null || Global.Export.IsOpen() ? 'c' : 'C');
  }

  private void SetProgramStarted
     (
        boolean AllowRunningSlowly
     ) {
    ClearDelayedStep();
    FillInLabels(CurBank);
    ProgRunningSlowly = false; /* just in case */
    this.AllowRunningSlowly = AllowRunningSlowly;
    SaveRunningSlowly = false;
    TaskRunning = true;
    SetShowingRunning();
    RunPC = PC;
    RunBank = CurBank;
    NextBank = RunBank;
  }

  private void SetProgramStopped() {
    TaskRunning = false;
    ClearDelayedStep();
    RunBank = CurBank;
    if (InErrorState()) {
      Global.Disp.SetShowingError(LastShowing);
    } else {
      Global.Disp.SetShowing(LastShowing);
    }
  }

  private void StartTask
     (
        Runnable TheTask,
        boolean AllowRunningSlowly
     ) {
    RunProg = TheTask;
    SetProgramStarted(AllowRunningSlowly);
    ContinueTaskRunner();
  }

  private void StopTask() {
    SetProgramStopped();
    BGTask.removeCallbacks(ExecuteTask);
    RunProg = null;
  }

  void StartProgram() {
    StartTask(new ProgRunner(), true);
  }

  void StopProgram() {
    if (TaskRunning && OnStop != null) {
      OnStop.run();
    }

    // if the PC is past the last instruction, move it back to last one
    if (TaskRunning && RunPC >= Bank[CurBank].Program.length)
      RunPC = Bank[CurBank].Program.length - 1;

    PC = RunPC;
    StopTask();
  }

  void WriteBank() {
    Enter(96);

    final int BANK_MEM = 30;
    final int BANK_PROG = 240;

    // Special case, not compatible with original TI-59 but handy for the emulator to support
    // many cards. The actual card number is entered as N.nnn where N is the bank number between
    // 1 and 4 and nnn the optional card index. The 9nn indexes are reserved for the internal
    // libraries and programs.

    int Part = 0; // part of the bank to read (0 = Pro & Mem, default)
    int N = (int)Math.abs(X.getInt());
    final int CardId = (int)Math.abs((int)Math.round((X.get() - N) * 1000));

    while(InvState && N >= 10) {
      Part = Part + 1;
      N = N - 10;
    }

    final String BankFilename = String.format("%03d.ti5b", CardId);
    if (Part > 2
        || ((N < 1 || N > 4)
            && (N != 0 || !InvState || CardId < 0)))
    {
      SetErrorState(true);
    } else if (InvState) {

      //  Read the bank data from the corresponding file. We do that only if the fractional
      //  part is non zero or the bank is not loaded. When a single number between 1 and 4
      //  is used and the bank has been initialized from a loaded program or library we do
      //  use it.

      if (CardId < 900) {
        Persistent.LoadBankFile(ctx, N, CardId, BankFilename, Global.Calc, Global.Disp, Global.Buttons);
      }

      if (Store.Contains(CardId)) {
        Store.Get (CardId).LoadCard(this, Part);
        // reset labels to take into account possible new ones in loaded card
        ResetInLabels();
      } else {
        // card not found
        SetErrorState(true);
      }
    } else {
      // write memory : 30 values for the given bank
      CardBankUsed[N-1] = true;

      for (int k = (4 - N) * BANK_MEM; k < (4 - N + 1) * BANK_MEM; k++) {
        if (k < MaxMemories) {
         CardMemory[k] = new Number(Memory[k]);
        }
      }
      System.arraycopy
         (
            Program,     (N - 1) * BANK_PROG,
            CardProgram, (N - 1) * BANK_PROG,
            BANK_PROG
         );

      Persistent.SaveBankFile(ctx, N, CardId, BankFilename, Global.Calc);
    }
  }

  private void StartLabelListing() {
    FillInLabels(0); /* because that's the one I list */
    StartTask(new LabelLister(), false);
  }

  void StartRegisterListing() {
    StartTask(new RegisterLister((int) Math.abs(X.getInt())), false);
  }

  void StartProgramListing() {
    StartTask(new ProgramLister(), false);
  }

  void SetSlowExecution
     (
        boolean Slow
     ) {
    if (AllowRunningSlowly && ProgRunningSlowly != Slow) {
      ProgRunningSlowly = Slow;
      SaveRunningSlowly = Slow;
      if (!ProgRunningSlowly) {
        SetShowingRunning();
      }
    }
  }

  void SetImport
     (
        ImportFeeder NewImport
     ) {
    if (Import != null) {
      throw new RuntimeException("attempt to queue multiple ImportFeeders");
    }
    Import = NewImport;
  }

  void ClearImport() {
    if (Import != null) {
      Import.End();
      Import = null;
    }
  }

  void ResetImport() {
    if (Import != null) {
      Import.Reset();
    }
  }

  void ResetExport() {
    if(Global.Export !=null) {
      Global.Export.Close();
    }
  }

  void ImportNew() {
      final int N = (int)Math.abs(X.getInt());
      final String DataFilename = String.format("%d.dat", N);
      final String SaveFile =
        Persistent.EnsureDirExists(ctx, Persistent.DataDir, DataFilename);
      ClearImport();
      try {
        Global.Import.ImportData(SaveFile);
      } catch (Persistent.DataFormatException Failed) {
        SetErrorState(true);
      }
  }

  void ExportNew
      (
          boolean Append
      )
  {
    final int N = (int)Math.abs(X.getInt());
    final String DataFilename = String.format("%d.dat", N);
    final String SaveFile =
        Persistent.EnsureDirExists(ctx, Persistent.DataDir, DataFilename);
    ResetExport();
    try {
      Global.Export.Open(SaveFile, Append, true);
    } catch (Persistent.DataFormatException Failed) {
      SetErrorState(true);
    }
  }

  void ResetReturns() {
    /* clears the subroutine return stack. */
    ReturnLast = -1;
  }

  void ResetProg() {
    if (InvState) /* extension! */ {
      ClearImport();
      ResetExport();
    } else {
      for (int i = 0; i < MaxFlags; ++i) {
        Flag[i] = false;
      }
      if (TaskRunning) {
        RunPC = 0;
      } else {
        PC = 0;
      }
      ResetReturns();
    }
  }

  private int GetProg
     (
        boolean Executing
     ) {
    /* returns the next program instruction byte, or -1 if run off the end. */
    byte Result;
    if (RunPC < Bank[RunBank].Program.length) {
      Result = Bank[RunBank].Program[RunPC++];
    } else {
      Result = -1;
      if (Executing) {
        RunPC = 0;
        StopProgram();
      }
    }
    return (int) Result;
  }

  private int GetLoc
     (
        boolean Executing,
        int BankNr /* for interpreting symbolic label */
     ) {
    /* fetches a program location from the instruction stream, or -1 on failure.
       Assumes Labels has been filled in. */
    int Result = -1;
    final int NextByte = GetProg(Executing);
    if (NextByte >= 0) {
      if (NextByte < 10) {
              /* 3-digit location */
        final int NextByte2 = GetProg(Executing);
        if (NextByte2 >= 0) {
          Result = NextByte * 100 + NextByte2;
        }
      } else if (NextByte == 40) /*Ind*/ {
        final int Reg = (GetProg(Executing) + RegOffset) % 100;
        if (Reg >= 0 && Reg < MaxMemories) {
          Result = (int) Memory[Reg].getInt();
          if (Result < 0 || Result >= Bank[RunBank].Program.length) {
            Result = -1;
          }
        }
      } else if (NextByte == 51) {
        /* 1-digit location. This is an hidden feature that cannot be enterred directly, to have it:
            - Key in DSZ 00 A, to put the keycodes [97 00 11] into program memory.
            - Then go back and delete the 00.
            - Insert two steps between the 97 and 11 and key in STO 36.
            - You will then have [97 42 36 11] as keycodes.
            - Go back and delete the 42. Then delete the 11.
            - You will now have [97 36]. Now key in after the 36 keycode STO 51.
            - You will then have [97 36 42 51]. Then delete the 42.
            - The final code is: [97 36 51]

           So "DSZ 36 51" which means decrement register 36 only (no jump).
         */
        Result = 9900 + NextByte;
      } else /* symbolic label */ {
        if (Bank[BankNr].Labels.indexOfKey(NextByte) >= 0) {
          Result = Bank[BankNr].Labels.get(NextByte);
        }
      }
    }
    return Result;
  }

  private int GetUnitOp
     (
        boolean Executing,
        boolean AllowLarge /* allow value > 9 */
     ) {
    /* fetches one or two bytes from the instruction stream; if the
       first (only) byte is < 10, then that's the value; otherwise
       a value of 40 indicates indirection through the register
       number in the next byte. Any other value is invalid. */
    int Result = -1;
    final int NextByte = GetProg(Executing);
    if (NextByte >= 0) {
      boolean OK;
      if (NextByte == 40) {
        final int Reg = (GetProg(Executing) + RegOffset) % 100;
        if (Reg >= 0 && Reg < MaxMemories) {
          Result = (int) Memory[Reg].getInt();
          OK = Result >= 0 && Result < MaxMemories;
          if (!OK) {
            Result = -1;
          }
        } else {
          OK = false;
        }
      } else if (AllowLarge || NextByte < 10) {
        Result = NextByte;
        OK = true;
      } else {
        OK = false;
      }
      if (Executing && !OK) {
        SetErrorState(true);
      }
    }
    return Result;
  }

  /* transfer types */
  static final int TRANSFER_TYPE_GTO = 1; /* goto that address */
  static final int TRANSFER_TYPE_CALL = 2; /* call that address, return to current address */
  static final int TRANSFER_TYPE_INTERACTIVE_CALL = 3;
  /* call that address, return to calculation mode */
  static final int TRANSFER_TYPE_LEA = 4; /* extension: load that address into X */

  /* location types */
  static final int TRANSFER_LOC_DIRECT = 1; /* Loc is integer address */
  static final int TRANSFER_LOC_SYMBOLIC = 2; /* Loc is label keycode */
  static final int TRANSFER_LOC_INDIRECT = 3; /* Loc is number of register containing address */

  void Transfer
     (
        int Type, /* one of the above TRANSFER_TYPE_xxx values */
        int BankNr,
        int Loc,
        int LocType /* one of the above TRANSFER_LOC_xxx values */
     ) {
    /* implements GTO and SBR. Also called from other functions to implement branches. */
    if (Loc >= 0) {
      boolean OK = false;
      do /*once*/ {
        if (LocType == TRANSFER_LOC_INDIRECT) {
          Loc = (Loc + RegOffset) % 100;
          if (Loc >= MaxMemories)
            break;
          Loc = (int) Memory[Loc].getInt();
        } else if (LocType == TRANSFER_LOC_SYMBOLIC) {
          FillInLabels(BankNr); /* if not already done */
          if (Bank[BankNr].Labels.indexOfKey(Loc) < 0)
            break;
          Loc = Bank[BankNr].Labels.get(Loc);
        }
        if (Loc < 0 || Loc >= Bank[BankNr].Program.length)
          break;
        if (Type == TRANSFER_TYPE_LEA) /* extension! */ {
          X.set(Loc);
          SetX(X, false);
        } else {
          if (Type == TRANSFER_TYPE_CALL || Type == TRANSFER_TYPE_INTERACTIVE_CALL) {
                          /* documentation in Appendix D (D-2) of the personnal programming manual
                             says that past the 6th call the return address is not recorded but the
                             call is done and it is not an error. */
            if (ReturnLast < MaxReturnStack - 1)
              ReturnStack[++ReturnLast] =
                 new ReturnStackEntry(RunBank, RunPC, Type == TRANSFER_TYPE_INTERACTIVE_CALL);
          }
          if (TaskRunning) {
            RunBank = BankNr;
            RunPC = Loc;
          } else {
            /* interactive, assert BankNr = CurBank */
            PC = Loc;
          }
          if (Type == TRANSFER_TYPE_INTERACTIVE_CALL) {
            StartProgram();
          }
        }
        /* all successfully done */
        OK = true;
      }
      while (false);
      if (!OK) {
        SetErrorState(true);
      }
    } else {
      // trying to transfer from an invalid loc
      RunPC--;
      SetErrorState(true);
    }
  }

  void MinMax() {
    Enter(75);
    if (X.compareTo(T) > 0) {
      SwapT();
    }
  }

  void Return() {
    /* returns to the last-saved location on the return stack. */
    boolean OK = false;
    do /*once*/ {
      if (ReturnLast < 0) {
        if (!InErrorState()) {
          Enter(92);
          CurState = ResultState;
        }
        StopProgram();
        OK = true;
        break;
      }
      final ReturnStackEntry ReturnTo = ReturnStack[ReturnLast--];
      if
         (
         ReturnTo.Addr < 0
            ||
            Bank[ReturnTo.BankNr] == null
            ||
            ReturnTo.Addr >= Bank[ReturnTo.BankNr].Program.length
         )
        break;
      if (ReturnTo.FromInteractive) {
        if (!InErrorState()) {
          Enter(92);
          CurState = ResultState;
        }
        StopProgram();
        OK = true;
        break;
      }
      if (TaskRunning) {
        RunBank = ReturnTo.BankNr;
        RunPC = ReturnTo.Addr;
      } else {
        CurBank = ReturnTo.BankNr;
        PC = ReturnTo.Addr;
      }
      /* all successfully done */
      OK = true;
    }
    while (false);
    if (!OK) {
      SetErrorState(true);
    }
  }

  void SetFlag
     (
        int FlagNr,
        boolean Ind,
        boolean Set
     ) {
    Enter(86);
    if (FlagNr >= 0) {
      if (Ind) {
        FlagNr = (FlagNr + RegOffset) % 100;
        if (FlagNr < MaxMemories) {
          FlagNr = (int) Memory[FlagNr].getInt();
        } else {
          FlagNr = -1;
        }
      }
      if (FlagNr >= 0 && FlagNr < MaxFlags) {
        Flag[FlagNr] = Set;
      } else {
        SetErrorState(true);
      }
    }
  }

  void BranchIfFlag
     (
        int FlagNr,
        boolean FlagNrInd,
        int BankNr,
        int Target,
        int TargetType /* one of the above TRANSFER_LOC_xxx values */
     ) {
    if (FlagNr >= 0) {
      if (FlagNrInd) {
        FlagNr = (FlagNr + RegOffset) % 100;
        if (FlagNr < MaxMemories) {
          FlagNr = (int) Memory[FlagNr].getInt();
        } else {
          FlagNr = -1;
        }
      }
      if (FlagNr >= 0 && FlagNr < MaxFlags) {
        if (InvState != Flag[FlagNr]) {
          Transfer
             (
                TRANSFER_TYPE_GTO,
                BankNr,
                Target,
                TargetType
             );
        }
      } else {
        SetErrorState(true);
      }

      // if the location we land is a number it must replace the current X

      byte Result = -1;
      if (RunPC < Bank[RunBank].Program.length) {
        Result = Bank[RunBank].Program[RunPC];
      }

      if (Result >= 0 && Result <= 9)
        ResetEntry();
    }
  }

  void CompareBranch
     (
        boolean Greater,
        int BankNr,
        int NewPC,
        boolean Ind
     ) {
    Enter((Greater ? (Ind ? 72 : 77) : (Ind ? 62 : 67)));
    if
       (
       InvState ?
          Greater ?
             X.compareTo(T) < 0
             :
             X.compareTo(T) != 0
          :
          Greater ?
             X.compareTo(T) >= 0
             :
             X.compareTo(T) == 0
       ) {
      if (NewPC > 0) {
        Transfer
           (
              TRANSFER_TYPE_GTO,
              BankNr,
              NewPC,
              Ind ? TRANSFER_LOC_INDIRECT : TRANSFER_LOC_DIRECT
           );
      } else {
        //  jump to unknown location
        SetErrorState(true);
        return;
      }
    }

    // if the location we land is a number it must replace the current X

    byte Result = -1;
    if (RunPC < Bank[RunBank].Program.length) {
      Result = Bank[RunBank].Program[RunPC];
    }

    if (Result >= 0 && Result <= 9)
      ResetEntry();
  }

  void DecrementSkip
     (
        int Reg,
        boolean RegInd,
        int Bank,
        int Target,
        int TargetType /* one of the above TRANSFER_LOC_xxx values */
     ) {
    if (Reg >= 0) {
      Reg = (Reg + RegOffset) % 100;
      if (RegInd) {
        if (Reg < MaxMemories) {
          Reg = ((int) Memory[Reg].getInt() + RegOffset) % 100;
        } else {
          Reg = -1;
        }
      }
      if (Reg >= 0 && Reg < MaxMemories) {
        Number N = new Number(Memory[Reg]);
        int Sign = N.getSignum();
        N.abs();
        N.sub(1);
        N.max(0);
        N.mult(Sign);
        Memory[Reg] = N;
        // do not jump if Target is on-byte value 51 (encoded as 9951)
        if (InvState == (Memory[Reg].getSignum() == 0) && Target != 9951) {
          Transfer
             (
                TRANSFER_TYPE_GTO,
                Bank,
                Target,
                TargetType
             );
        }
      } else {
        SetErrorState(true);
      }
    }
  }

  private void Interpret
     (
        boolean Execute /* false to just collect labels */
     ) {
    /* main program interpreter loop: interprets one instruction and
       advances/jumps RunPC accordingly. */
    final int Op = GetProg(Execute);
    if (Op >= 0) {
      boolean KeepModifier = false;
      if (Execute) {
        boolean BankSet = false;
        ProgRunningSlowly = SaveRunningSlowly; /* undo previous Pause, if any */
        switch (Op) {
          case 01:
          case 02:
          case 03:
          case 04:
          case 05:
          case 06:
          case 07:
          case 8:
          case 9:
          case 00:
            Digit((char) (Op + 48));
            break;
          case 11:
          case 12:
          case 13:
          case 14:
          case 15:
          case 16:
          case 17:
          case 18:
          case 19:
          case 10:
            Transfer(TRANSFER_TYPE_CALL, NextBank, Op, TRANSFER_LOC_SYMBOLIC);
            break;
          /* 21 invalid */
          case 22:
          case 27:
            InvState = !InvState;
            KeepModifier = true;
            break;
          case 23:
            Ln();
            break;
          case 24:
            ClearEntry();
            break;
          case 20:
            ClearAll();
            Enter(20);
            break;
          case 25:
            ClearAll();
            Enter(25);
            break;
          /* 26 invalid */
          /* 27 same as 22 */
          case 28:
            Log();
            break;
          case 29: /*CP*/
            Enter(29);
            T.set(Number.ZERO);
            break;
          /* 20 same as 25 */
          /* 31 invalid */
          case 32:
            SwapT();
            break;
          case 33:
            Square();
            break;
          case 34:
            Sqrt();
            break;
          case 35:
            Reciprocal();
            break;
          case 36: /*Pgm*/
            SelectProgram(GetProg(true), false);
            BankSet = true;
            break;
          case 37:
            Polar();
            break;
          case 38:
            Sin();
            break;
          case 39:
            Cos();
            break;
          case 30:
            Tan();
            break;
          /* 41 invalid */
          case 42:
            MemoryOp(MEMOP_STO, GetProg(true), false);
            break;
          case 43:
            MemoryOp(MEMOP_RCL, GetProg(true), false);
            break;
          case 44:
            MemoryOp(MEMOP_ADD, GetProg(true), false);
            break;
          case 45:
            Operator(STACKOP_EXP);
            break;
          /* 46 invalid */
          case 47:
            ClearMemories();
            break;
          case 48:
            MemoryOp(MEMOP_EXC, GetProg(true), false);
            break;
          case 49:
            MemoryOp(MEMOP_MUL, GetProg(true), false);
            break;
          case 51: /* extension % */
            Percent();
            break;
          case 52:
            EnterExponent();
            break;
          case 53:
            LParen();
            break;
          case 54:
            RParen();
            break;
          case 55:
            if (InvState) /* extension! */ {
              Operator(STACKOP_MOD);
            } else {
              Operator(STACKOP_DIV);
            }
            break;
          /* 56 invalid */
          case 57: /*Eng*/
            SetDisplayMode
               (
                  InvState ? FORMAT_FIXED : FORMAT_ENG,
                  CurNrDecimals
               );
            break;
          case 58: /*Fix*/
            if (InvState) {
              SetDisplayMode(CurFormat, -1);
            } else {
              SetDisplayMode(CurFormat, GetProg(true));
            }
            break;
          case 59:
            Int();
            break;
          case 50:
            Abs();
            break;
          case 61: /*GTO*/
            Transfer
               (
                  InvState ?
                     TRANSFER_TYPE_LEA /*extension!*/
                     :
                     TRANSFER_TYPE_GTO,
                  NextBank,
                  GetLoc(true, NextBank),
                  TRANSFER_LOC_DIRECT
               );
            break;
          case 62: /*Pgm Ind*/
            SelectProgram(GetProg(true), true);
            BankSet = true;
            break;
          case 63:
            MemoryOp(MEMOP_EXC, GetProg(true), true);
            break;
          case 64:
            MemoryOp(MEMOP_MUL, GetProg(true), true);
            break;
          case 65:
            Operator(STACKOP_MUL);
            break;
          case 66: /*Pause*/
            ProgRunningSlowly = true; /* will revert to SaveRunningSlowly next time */
            break;
          case 67: /*x=t*/
          case 77: /*x≥t*/
            final int BranchPC = RunPC;
            CompareBranch(Op == 77, RunBank, GetLoc(true, RunBank), false);
            if (InErrorState()) {
              // in case of error, go to the branch op
              RunPC = BranchPC;
              PC = BranchPC;
            }
            break;
          case 68: /*Nop*/
            KeepModifier = true;
            break;
          case 69:
            SpecialOp(GetProg(true), false);
            break;
          case 60:
            SetAngMode(ANG_DEG);
            break;
          case 71: /*SBR*/
            if (InvState) {
              Return();
            } else {
              Transfer(TRANSFER_TYPE_CALL, NextBank, GetLoc(true, NextBank), TRANSFER_LOC_DIRECT);
            }
            break;
          case 72:
            MemoryOp(MEMOP_STO, GetProg(true), true);
            break;
          case 73:
            MemoryOp(MEMOP_RCL, GetProg(true), true);
            break;
          case 74:
            MemoryOp(MEMOP_ADD, GetProg(true), true);
            break;
          case 75:
            if (InvState) /* extension! */ {
              MinMax();
              KeepModifier = true;
            } else {
              Operator(STACKOP_SUB);
            }
            break;
          case 76: /*Lbl*/
            GetProg(true); /* just skip label, assume Labels already filled in */
            KeepModifier = true;
            break;
          /* 77 handled above */
          case 78:
            StatsSum();
            break;
          case 79:
            StatsResult();
            break;
          case 70:
            SetAngMode(ANG_RAD);
            break;
          case 81:
            ResetProg();
            break;
          case 82: /* HIR non documented instructions */
            HirOp(GetProg(true));
            break;
          case 83: /*GTO Ind*/
            Transfer
               (
                  InvState ?
                     TRANSFER_TYPE_LEA /*extension!*/
                     :
                     TRANSFER_TYPE_GTO,
                  NextBank,
                  GetProg(true),
                  TRANSFER_LOC_INDIRECT
               );
            break;
          case 84:
            SpecialOp(GetProg(true), true);
            break;
          case 85:
            Operator(STACKOP_ADD);
            break;
          case 86: /*St flg*/
            SetFlag(GetUnitOp(true, false), false, !InvState);
            break;
          case 87: /*If flg*/ {
            final int FlagNr = GetUnitOp(true, false);
            final int Target = GetLoc(true, RunBank);
            BranchIfFlag(FlagNr, false, RunBank, Target, TRANSFER_LOC_DIRECT);
          }
          break;
          case 88:
            D_MS();
            break;
          case 89:
            Pi();
            break;
          case 80:
            SetAngMode(ANG_GRAD);
            break;
          case 91:
            StopProgram();
            break;
          case 96:
            WriteBank();
            break;
          case 92: /*INV SBR*/
            Return();
            break;
          case 93:
            DecimalPoint();
            break;
          case 94:
            ChangeSign();
            break;
          case 95:
            Equals();
            break;
          /* 96 same as 91 */
          case 97: /*Dsz*/ {
            final int Reg = GetUnitOp(true, true);
            final int Target = GetLoc(true, RunBank);
            DecrementSkip(Reg, false, RunBank, Target, TRANSFER_LOC_DIRECT);
          }
          break;
          case 98: /*Adv*/
            if (Global.Calc.InvState) /* extension! */ {
              if (Global.Export != null) {
                Global.Export.Close();
              }
            } else {
              if (Global.Print != null) {
                Global.Print.Advance();
              }
            }
            break;
          case 99: /*Prt*/
            Enter(99);
            if (InvState) /* extension! */ {
              GetNextImport();
            } else {
              if (Global.Export != null && Global.Export.NumbersOnly) {
                Global.Export.WriteNum(X);
              }
              PrintDisplay(true, false, "");
            }
            break;
          case 90: /*List*/
                  /* no-op in program? */
            break;
          default:
            SetErrorState(true);
            break;
        }
        if (!BankSet) {
          NextBank = RunBank;
        }
      } else {
        /* just advance RunPC past instruction and update Labels as appropriate */
        switch (Op) {
          case 22:
          case 27:
            InvState = !InvState; /* needed to correctly parse INV Fix and unmerged INV SBR */
            KeepModifier = true;
            break;
          case 36: /*Pgm*/
          case 42: /*STO*/
          case 43: /*RCL*/
          case 44: /*SUM*/
          case 48: /*Exc*/
          case 49: /*Prd*/
          case 62: /*Pgm Ind*/
          case 63: /*Exc Ind*/
          case 64: /*Prd Ind*/
          case 69: /*Op*/
          case 72: /*STO Ind*/
          case 73: /*RCL Ind*/
          case 74: /*SUM Ind*/
          case 83: /*GTO Ind*/
          case 84: /*Op Ind*/
                  /* one byte following */
            GetProg(false);
            break;
          case 57: /*Fix*/
            if (!InvState) {
              GetUnitOp(false, false);
            }
            break;
          case 61: /*GTO*/
            GetLoc(false, RunBank /* irrelevant */);
            break;
          case 67: /*x=t*/
          case 77: /*x≥t*/
            GetLoc(false, RunBank);
            break;
          case 71: /*SBR*/
            if (!InvState) /* in case it wasn't merged */ {
              GetLoc(false, RunBank /* irrelevant */);
            }
            break;
          case 76: /*Lbl*/ {
            final int TheLabel = GetProg(false);
            if (TheLabel >= 0 && RunPC >= 0 && Bank[RunBank].Labels.indexOfKey(TheLabel) < 0) {
              Bank[RunBank].Labels.put(TheLabel, RunPC);
            }
          }
          KeepModifier = true;
          break;
          case 68: /*Nop*/
            KeepModifier = true;
            break;
          case 86: /*St flg*/
            GetUnitOp(false, false);
            break;
          case 87: /*If flg*/
          case 97: /*Dsz*/
            GetUnitOp(false, true); /*register/flag*/
            GetLoc(false, RunBank); /*branch target*/
            break;
        }
      }
      if (!KeepModifier) {
        InvState = false;
      }
    }
    // For extension INV - we need to clear the PreviousOp
    // indeed this operator doesn't expect any other operand.
    if (Op == 75 && InvState == true) {
      PreviousOp = -1;
      InvState = false;
    } else {
      PreviousOp = Op;
    }
  }

  void FillInLabels
     (
        int BankNr
     ) {
    if (Bank[BankNr].Labels == null) {
      Bank[BankNr].Labels = new SparseIntArray();
      final boolean SaveInvState = InvState;
      final int SaveRunPC = RunPC;
      final int SaveRunBank = RunBank;
      InvState = false;
      RunPC = 0;
      RunBank = BankNr;
      do {
        Interpret(false);
      }
      while (RunPC != -1 && RunPC < Bank[BankNr].Program.length);
      InvState = SaveInvState;
      RunPC = SaveRunPC;
      RunBank = SaveRunBank;
    }
  }

  void FillInLabels() {
    FillInLabels(CurBank);
  }

  void ResetInLabels() {
    // This is only needed on user's bank 0 when loading a bank-card
    Bank[0].Labels = null;
    FillInLabels(0);
  }
}
