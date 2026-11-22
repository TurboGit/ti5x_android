/*
    ti5x calculator emulator -- keypad: key entry state machine

    Copyright 2011-2014 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
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

public class Keypad {
  /* The calculator key entry: 2nd and Inv modifiers, collection of the
     operands following a key (register numbers, labels, Ind...) and
     storing instructions in learn mode. Keys are identified by their
     key code (row and column, 11 .. 95) as printed on the key. */

  private final Calculator Engine;

  /* global modifier state */
  public boolean AltState;

  /* operand collection state */
  public int DigitsNeeded;
  public boolean AcceptSymbolic, AcceptInd, NextLiteral;
  public int AccumDigits, FirstOperand;
  public boolean GotFirstOperand, GotFirstInd, IsSymbolic, GotInd;
  public int CollectingForFunction;
  private int ButtonCode;

  /* host hooks, can be null */
  public Runnable OnNop; /* Nop key pressed, a good time to save the state */
  public Runnable OnStatusChange; /* after each key: 2nd, Inv or angle mode may have changed */

  Keypad
     (
        Calculator Engine
     ) {
    this.Engine = Engine;
    Reset();
  }

  public void Reset() {
    /* resets to power-up state. */
    AltState = false;
    ResetOperands();
  }

  public void Release
     (
        int KeyCode
     ) {
    /* processes a key release: releasing GTO (Pause) ends the slow run. */
    if (KeyCode == 61 && Engine.Calc.TaskRunning) {
      Engine.Calc.SetSlowExecution(false);
    }
  }

  public void ResetOperands() {
    DigitsNeeded = 0;
    AcceptSymbolic = false;
    AcceptInd = false;
    NextLiteral = false;
    AccumDigits = -1;
    GotFirstOperand = false;
    IsSymbolic = false;
    GotInd = false;
    CollectingForFunction = -1;
  }

  private void StoreOperand
     (
        int NrDigits, /* 1, 2 or 3 */
        boolean SeparateInd
     )
    /* stores operand for a program instruction. */ {
    if (SeparateInd && GotInd) {
      Engine.Calc.StoreInstr(40);
    }
    if (IsSymbolic) {
      Engine.Calc.StoreInstr(ButtonCode);
    } else {
      if (NrDigits < 3 || GotInd) {
        Engine.Calc.StoreInstr(AccumDigits);
      } else {
        Engine.Calc.StoreInstr(AccumDigits / 100);
        Engine.Calc.StoreInstr(AccumDigits % 100);
      }
    }
  }

  public void Press
     (
        int KeyCode /* key row and column, 11 .. 95, as printed on the key (not the 2nd function) */
     ) {
    /* processes a key press. */
    final State Calc = Engine.Calc; /* shorten references */

    if (KeyCode > 0) {
      boolean WasModifier = false;
      boolean Handled = false;
      if (AltState) {
        ButtonCode = KeyCode / 10 * 10 + (KeyCode % 10 + 5) % 10;
      } else {
        ButtonCode = KeyCode;
      }
      if (Calc.TaskRunning) {
        switch (ButtonCode) {
          case 61:
          case 66: /*Pause, actually will always be 61 (GTO)*/
            Calc.SetSlowExecution(true);
            break;
          case 91: /*R/S*/
            Calc.StopProgram();
            break;
          case 96: /* write */
            Calc.WriteBank();
            break;
        }
        Handled = true; /* ignore everything else */
      } else if (CollectingForFunction != -1) {
        int Digit = -1;
        Handled = true; /* next assumption */
        switch (ButtonCode) /* collect digits */ {
          case 40: /*Ind*/
            if (AcceptInd && AccumDigits < 0) {
              GotInd = !GotInd;
            } else if (NextLiteral) {
              /* note I can't use Ind as label because I can't goto/gosub it */
              Calc.SetErrorState(true);
            } else {
              Handled = false;
            }
            break;
          case 21:
          case 26:
            /* needed for Ind and Lbl */
            AltState = !AltState;
            WasModifier = true;
            break;
          case 62: /*digit 7*/
          case 63: /*digit 8*/
          case 64: /*digit 9*/
            if (NextLiteral) {
              Calc.SetErrorState(true);
            } else {
              Digit = ButtonCode - 55;
            }
            break;
          case 72: /*digit 4*/
          case 73: /*digit 5*/
          case 74: /*digit 6*/
            if (NextLiteral) {
              Calc.SetErrorState(true);
            } else {
              Digit = ButtonCode - 68;
            }
            break;
          case 82: /*digit 1*/
          case 83: /*digit 2*/
          case 84: /*digit 3*/
            if (NextLiteral) {
              Calc.SetErrorState(true);
            } else {
              Digit = ButtonCode - 81;
            }
            break;
          case 92: /*digit 0*/
            if (NextLiteral) {
              Calc.SetErrorState(true);
            } else {
              Digit = 0;
            }
            break;
          default:
            if (AcceptSymbolic || NextLiteral) {
              IsSymbolic = true;
            } else {
              Handled = false;
            }
            break;
        }

        if (Digit >= 0) {
          if (AccumDigits < 0) {
            AccumDigits = 0;
            AcceptSymbolic = false;
            if (GotInd) {
              DigitsNeeded = 2; /* for register number */
            }
          }
          AccumDigits = AccumDigits * 10 + Digit;
        }

        if (Handled) {
          if (Digit >= 0) {
            --DigitsNeeded;
          }
        } else {
          DigitsNeeded = 0; /* non-digit cuts short digit entry */
        }

        if (!WasModifier && (DigitsNeeded == 0 || IsSymbolic)) {
          boolean Finished = true; /* to begin with */
          if (IsSymbolic || AccumDigits >= 0) {
            switch (CollectingForFunction) {
              case 36: /*Pgm*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 62 : 36);
                  StoreOperand(2, false);
                } else {
                  Calc.SelectProgram(AccumDigits, GotInd);
                }
                break;
              case 42: /*STO*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 72 : 42);
                  StoreOperand(2, false);
                } else {
                  Calc.MemoryOp(State.MEMOP_STO, AccumDigits, GotInd);
                  /* special case, if pgm is 01 then store mm in 00 is selecting an
                     alternate program to have printout. In this case we change the
                     actual card/help to correspond to the target program. */
                  if (Calc.CurBank == 1 && AccumDigits == 0) {
                    int ProgNr = (int) Calc.X.getInt();
                    if (ProgNr >= 1 && ProgNr < Calc.MaxBanks && Calc.Bank[ProgNr] != null) {
                      Calc.FillInLabels(ProgNr); // if not done already
                      Engine.Help.SetHelp
                         (Calc.Bank[ProgNr].Card, Calc.Bank[ProgNr].Help);
                    }
                  }
                }
                break;
              case 43: /*RCL*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 73 : 43);
                  StoreOperand(2, false);
                } else {
                  Calc.MemoryOp(State.MEMOP_RCL, AccumDigits, GotInd);
                }
                break;
              case 44: /*SUM*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 74 : 44);
                  StoreOperand(2, false);
                } else {
                  Calc.MemoryOp(State.MEMOP_ADD, AccumDigits, GotInd);
                }
                break;
              case 48: /*Exc*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 63 : 48);
                  StoreOperand(2, false);
                } else {
                  Calc.MemoryOp(State.MEMOP_EXC, AccumDigits, GotInd);
                }
                break;
              case 49: /*Prd*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 64 : 49);
                  StoreOperand(2, false);
                } else {
                  Calc.MemoryOp(State.MEMOP_MUL, AccumDigits, GotInd);
                }
                break;
              case 58: /*Fix*/
                          /* assert not Calc.InvState */
                if (Calc.ProgMode) {
                  Calc.StoreInstr(58);
                  StoreOperand(1, true);
                } else {
                  Calc.SetDisplayMode(Calc.CurFormat, AccumDigits);
                }
                break;
              case 67: /*x=t*/
              case 77: /*x≥t*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(CollectingForFunction);
                  StoreOperand(3, true);
                } else {
                  Calc.CompareBranch
                     (
                        CollectingForFunction == 77,
                        Calc.CurBank, AccumDigits, GotInd
                     );
                }
                break;
              case 69: /*Op*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 84 : 69);
                  StoreOperand(2, false);
                } else {
                  Calc.SpecialOp(AccumDigits, GotInd);
                }
                break;
              case 61: /*GTO*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(GotInd ? 83 : 61);
                  StoreOperand(3, false);
                } else {
                  Calc.FillInLabels();
                  Calc.Transfer
                     (
                        Calc.InvState ?
                           State.TRANSFER_TYPE_LEA /*extension!*/
                           :
                           State.TRANSFER_TYPE_GTO,
                        Calc.CurBank,
                        IsSymbolic ? ButtonCode : AccumDigits,
                        IsSymbolic ?
                           State.TRANSFER_LOC_SYMBOLIC
                           : GotInd ?
                           State.TRANSFER_LOC_INDIRECT
                           :
                           State.TRANSFER_LOC_DIRECT
                     );
                }
                break;
              case 71: /*SBR*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(71);
                  StoreOperand(3, true);
                } else {
                  Calc.ResetReturns(); /* seems to be what the real calculator does */
                  Calc.FillInLabels();
                  Calc.Transfer
                     (
                        State.TRANSFER_TYPE_INTERACTIVE_CALL,
                        Calc.CurBank,
                        IsSymbolic ? ButtonCode : AccumDigits,
                        IsSymbolic ?
                           State.TRANSFER_LOC_SYMBOLIC
                           : GotInd ?
                           State.TRANSFER_LOC_INDIRECT
                           :
                           State.TRANSFER_LOC_DIRECT
                     );
                }
                break;
              case 76: /*Lbl*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(76);
                  Calc.StoreInstr(ButtonCode); /* always symbolic */
                }
                break;
              case 86: /*St flg*/
                if (Calc.ProgMode) {
                  Calc.StoreInstr(86);
                  StoreOperand(1, true);
                } else {
                  Calc.SetFlag(AccumDigits, GotInd, !Calc.InvState);
                }
                break;
              case 87: /*If flg*/
              case 97: /*Dsz*/
                if (GotFirstOperand) {
                  if (Calc.ProgMode) {
                    Calc.StoreInstr(CollectingForFunction);
                    if (GotFirstInd) {
                      Calc.StoreInstr(40);
                    }
                    Calc.StoreInstr(FirstOperand);
                    StoreOperand(3, true);
                  } else {
                    if (CollectingForFunction == 87) /*If flg*/ {
                      Calc.BranchIfFlag
                         (
                            FirstOperand, GotFirstInd,
                            Calc.CurBank, AccumDigits,
                            IsSymbolic ?
                               State.TRANSFER_LOC_SYMBOLIC
                               : GotInd ?
                               State.TRANSFER_LOC_INDIRECT
                               :
                               State.TRANSFER_LOC_DIRECT
                         );
                    } else /*Dsz*/ {
                      Calc.DecrementSkip
                         (
                            FirstOperand, GotFirstInd,
                            Calc.CurBank, AccumDigits,
                            IsSymbolic ?
                               State.TRANSFER_LOC_SYMBOLIC
                               : GotInd ?
                               State.TRANSFER_LOC_INDIRECT
                               :
                               State.TRANSFER_LOC_DIRECT
                         );
                    }
                  }
                } else {
                  GotFirstInd = GotInd;
                  GotInd = false;
                  AcceptInd = true;
                  FirstOperand = AccumDigits;
                  AccumDigits = -1;
                  GotFirstOperand = true;
                  DigitsNeeded = 3;
                  AcceptSymbolic = true;
                  Finished = false;
                }
                break;
              default:
                /* shouldn't occur */
                throw new RuntimeException("unhandled collected function " + CollectingForFunction);
            }
            Calc.PreviousOp = CollectingForFunction;
          } else {
            Calc.SetErrorState(true);
            Handled = true;
          }
          if (Finished) {
            CollectingForFunction = -1;
            ResetOperands();
          }
        }
      }

      if (!Handled) {
        /* check for functions needing further entry */
        if (!Calc.ProgMode || Calc.ProgramWritable()) {
          Handled = true; /* next assumption */
          switch (ButtonCode) {
            case 36: /* Pgm */
            case 42: /* STO */
            case 43: /* RCL */
            case 44: /* SUM */
            case 48: /* Exc */
            case 49: /* Prd */
            case 69: /* Op */
              DigitsNeeded = 2;
              AcceptInd = true;
              AcceptSymbolic = true;
              break;
            case 58: /* Fix */
              if (!Calc.InvState) {
                DigitsNeeded = 1;
                AcceptInd = true;
              } else {
                Handled = false; /* no special handling required */
              }
              break;
            case 67: /* x = t */
            case 77: /* x ≥ t */
              DigitsNeeded = 3;
              AcceptInd = true;
              AcceptSymbolic = true;
              break;
            case 61: /* GTO */
            case 71: /* SBR */
              if (ButtonCode == 71 && Calc.InvState) {
                Handled = false; /* special handling for INV SBR happens below */
              } else {
                DigitsNeeded = 3;
                AcceptInd = true;
                AcceptSymbolic = true;
              }
              break;
            case 76: /* Lbl */
              NextLiteral = true;
              break;
            case 86: /* St flg */
              DigitsNeeded = 1;
              AcceptInd = true;
              break;
            case 87: /* If flg */
            case 97: /* Dsz */
              DigitsNeeded = 1;
              AcceptInd = true;
              AcceptSymbolic = false;
              GotFirstOperand = false;
              break;
            default:
              /* wasn't one of these after all */
              Handled = false;
              break;
          }
          if (Handled) {
            CollectingForFunction = ButtonCode;
          }
        }
      }

      if (!Handled && ButtonCode == 40 /*Ind*/) {
        /* ignore? */
        Handled = true;
      }

      if (!Handled) {
        /* deal with everything not already handled */
        if (Calc.ProgMode) {
          if
             (
             Calc.ProgramWritable()
                ||
                ButtonCode == 31 /*LRN*/
                ||
                ButtonCode == 41 /*SST*/
                ||
                ButtonCode == 51 /*BST*/
             ) {
            switch (ButtonCode) /* undo effect of 2nd key on buttons with no alt function */ {
              case 27: /* INV */
                ButtonCode = 22;
                break;
            }
            switch (ButtonCode) {
              /* special handling of program-editing/viewing functions and number entry */
              case 21:
              case 26:
                AltState = !AltState;
                WasModifier = true;
                break;
              case 22:
                Calc.StoreInstr(22);
                Calc.InvState = !Calc.InvState;
                WasModifier = true;
                break;
              case 31: /*LRN*/
                Calc.SetProgMode(false);
                ResetOperands();
                break;
              /* 40 handled above */
              case 41: /*SST*/
                Calc.StepPC(true);
                ResetOperands();
                break;
              case 46: /*Ins*/
                Calc.InsertAtCurInstr();
                ResetOperands();
                break;
              case 51: /*BST*/
                Calc.StepPC(false);
                ResetOperands();
                break;
              case 56: /*Del*/
                Calc.DeleteCurInstr();
                ResetOperands();
                break;
              case 62: /*digit 7*/
              case 63: /*digit 8*/
              case 64: /*digit 9*/
                Calc.StoreInstr(ButtonCode - 55);
                break;
              case 71: /*SBR*/
                if (Calc.InvState) {
                  Calc.StorePrevInstr(92);
                }
                /* else handled above */
                break;
              case 72: /*digit 4*/
              case 73: /*digit 5*/
              case 74: /*digit 6*/
                Calc.StoreInstr(ButtonCode - 68);
                break;
              /* 76 handled above */
              case 82: /*digit 1*/
              case 83: /*digit 2*/
              case 84: /*digit 3*/
                Calc.StoreInstr(ButtonCode - 81);
                break;
              case 92: /*digit 0*/
                Calc.StoreInstr(0);
                break;
              case 20: /* extension %, store as code 51 */
                Calc.StoreInstr(51);
                break;
              default:
                Calc.StoreInstr(ButtonCode);
                break;
            }
          }
        } else /* calculation mode */ {
          switch (ButtonCode) {
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
              Calc.ResetReturns(); /* seems to be what the real calculator does */
              Calc.FillInLabels();
              Calc.Transfer
                 (
                    State.TRANSFER_TYPE_INTERACTIVE_CALL,
                    Calc.CurBank,
                    ButtonCode,
                    State.TRANSFER_LOC_SYMBOLIC
                 );
              break;
            case 20:
              Calc.Percent();
              break;
            case 21:
            case 26:
              AltState = !AltState;
              WasModifier = true;
              break;
            case 22:
            case 27:
              Calc.InvState = !Calc.InvState;
              WasModifier = true;
              break;
            case 23:
              Calc.Ln();
              break;
            case 24:
              Calc.ClearEntry();
              break;
            case 25:
              Calc.ClearAll();
              break;
            /* 26 same as 21 */
            /* 27 same as 22 */
            case 28:
              Calc.Log();
              break;
            case 29:
              Calc.ClearProgram();
              break;
            case 31: /*LRN*/
              Calc.SetProgMode(true);
              ResetOperands();
              break;
            case 32:
              Calc.SwapT();
              break;
            case 33:
              Calc.Square();
              break;
            case 34:
              Calc.Sqrt();
              break;
            case 35:
              Calc.Reciprocal();
              break;
            /* 36 handled above */
            case 37:
              Calc.Polar();
              break;
            case 38:
              Calc.Sin();
              break;
            case 39:
              Calc.Cos();
              break;
            case 30:
              Calc.Tan();
              break;
            case 41: {
              final boolean SaveInvState = Calc.InvState;
              Calc.StepProgram();
              WasModifier = Calc.InvState != SaveInvState; /* just did an INV */
            }
            break;
            /* 42, 43, 44 handled above */
            case 45:
              Calc.Operator(State.STACKOP_EXP);
              break;
            case 46:
              break;
            case 47:
              Calc.ClearMemories();
              break;
            /* 48, 49, 40 handled above */
            case 51:
              break;
            case 52:
              Calc.EnterExponent();
              break;
            case 53:
              Calc.LParen();
              break;
            case 54:
              Calc.RParen();
              break;
            case 55:
              if (Calc.InvState) /* extension! */ {
                Calc.Operator(State.STACKOP_MOD);
              } else {
                Calc.Operator(State.STACKOP_DIV);
              }
              break;
            case 56:
              break;
            case 57:
              Calc.SetDisplayMode
                 (
                    Calc.InvState ? State.FORMAT_FIXED : State.FORMAT_ENG,
                    Calc.CurNrDecimals
                 );
              break;
            case 58:
              /* assert Calc.InvState */
              Calc.SetDisplayMode(Calc.CurFormat, -1);
              break;
            case 59:
              Calc.Int();
              break;
            case 50:
              Calc.Abs();
              break;
            /* 61 handled above */
            case 62:
              Calc.Digit('7');
              break;
            case 63:
              Calc.Digit('8');
              break;
            case 64:
              Calc.Digit('9');
              break;
            case 65:
              Calc.Operator(State.STACKOP_MUL);
              break;
            case 66: /*Pause*/
              break;
            /* 67 handled above */
            case 68: /*Nop*/
              /* No semantic effect, but why not do some saving of
                 volatile state, just in case */
              if (OnNop != null) {
                OnNop.run();
              }
              Engine.Export.Flush();
              break;
            /* 69 handled above */
            case 60:
              Calc.SetAngMode(State.ANG_DEG);
              break;
            case 71:
              if (Calc.InvState) {
                Calc.Return();
              }
              break;
            case 72:
              Calc.Digit('4');
              break;
            case 73:
              Calc.Digit('5');
              break;
            case 74:
              Calc.Digit('6');
              break;
            case 75:
              if (Calc.InvState) /* extension! */ {
                Calc.MinMax();
              } else {
                Calc.Operator(State.STACKOP_SUB);
              }
              break;
            case 76:
              break;
            /* 77 handled above */
            case 78:
              Calc.StatsSum();
              break;
            case 79:
              Calc.StatsResult();
              break;
            case 70:
              Calc.SetAngMode(State.ANG_RAD);
              break;
            case 81:
              Calc.ResetProg();
              break;
            case 82:
              Calc.Digit('1');
              break;
            case 83:
              Calc.Digit('2');
              break;
            case 84:
              Calc.Digit('3');
              break;
            case 85:
              Calc.Operator(State.STACKOP_ADD);
              break;
            /* 86, 87 handled above */
            case 88:
              Calc.D_MS();
              break;
            case 89:
              Calc.Pi();
              break;
            case 80:
              Calc.SetAngMode(State.ANG_GRAD);
              break;
            case 91:
              Calc.StartProgram();
              break;
            case 92:
              Calc.Digit('0');
              break;
            case 93:
              Calc.DecimalPoint();
              break;
            case 94:
              Calc.ChangeSign();
              break;
            case 95:
              Calc.Equals();
              break;
            case 96: /* write */
              Calc.WriteBank();
              break;
            /* 97 handled above */
            case 98: /*Adv*/
              if (Calc.InvState) /* extension! */ {
                Engine.Export.Close();
              } else {
                Engine.Print.Advance(true);
              }
              break;
            case 99: /*Prt*/
              Calc.Enter(99);
              if (Calc.InvState) /* extension! */ {
                Calc.GetNextImport();
              } else {
                Calc.PrintDisplay(true, false, true, "");
              }
              break;
            case 90: /*List*/
              Calc.Enter(90);
              if (Calc.InvState) {
                Calc.StartRegisterListing();
              } else {
                Calc.StartProgramListing();
              }
              break;
          }

          // For extension INV - we need to clear the PreviousOp
          // indeed this operator doesn't expect any other operand.
          if(ButtonCode == 75 && Calc.InvState == true) {
            Calc.PreviousOp = -1;
          } else {
            Calc.PreviousOp = ButtonCode;
          }
        }
      }

      if (!WasModifier) {
        AltState = false;
      }
      if (Calc.InErrorState()) {
        ResetOperands(); /* abandon */
      }
      if (!WasModifier && CollectingForFunction < 0) {
        Calc.InvState = false;
      }
    }
    if (OnStatusChange != null) {
      OnStatusChange.run();
    }
  }
}
