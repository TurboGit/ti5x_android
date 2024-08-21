/*
    Display and interaction with calculator buttons

    Copyright 2011-2014 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

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

import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

class ButtonGrid extends android.view.View {
  private static final int NrButtonRows = 9;
  private static final int NrButtonCols = 5;
  private final android.graphics.Typeface MFont =
     Typeface.createFromAsset(getContext().getAssets(),
        "fonts/dejavusans-bold.ttf");
  private final android.graphics.Typeface MFontL =
          Typeface.createFromAsset(getContext().getAssets(),
                  "fonts/dejavusans.ttf");

  private final android.media.SoundPool MakeNoise;
  private final android.os.Vibrator Vibrate;
  /* it appears SoundPool allocates loaded sound IDs starting from 1 */
  private int ButtonDown = 0;

  private final int Dark;
  private final int White;
  private final int ButtonBrown;
  private final int ButtonYellow;
  private final int AltButtonColor;
  private final int OverlayBlue;

  class ButtonDef {
    /* defines appearance of a button */
    int BaseCode;
    final String Text, AltText, MergedText;
    final int TextColor, ButtonColor, AltTextColor, OverlayColor, BGColor;

    ButtonDef
       (
          String Text,
          String AltText,
          String MergedText, /* may be null */
          int TextColor,
          int ButtonColor
       ) {
      this.Text = Text;
      this.AltText = AltText;
      this.MergedText = MergedText;
      this.TextColor = TextColor;
      this.ButtonColor = ButtonColor;
      this.AltTextColor = AltButtonColor;
      this.OverlayColor = OverlayBlue;
      this.BGColor = Dark;
    }

    ButtonDef
       (
          String Text,
          String AltText,
          int TextColor,
          int ButtonColor
       ) {
      this(Text, AltText, null, TextColor, ButtonColor);
    }
  }

  private ButtonDef[][] ButtonDefs;

  private void MakeButtonDefs() {
    ButtonDefs = new ButtonDef[][]
       {
          new ButtonDef[]
             {
                new ButtonDef("A", "A´", White, ButtonBrown),
                new ButtonDef("B", "B´", White, ButtonBrown),
                new ButtonDef("C", "C´", White, ButtonBrown),
                new ButtonDef("D", "D´", White, ButtonBrown),
                new ButtonDef("E", "E´", White, ButtonBrown),
             },
          new ButtonDef[]
             {
                new ButtonDef("2nd", "", Dark, ButtonYellow),
                new ButtonDef("INV", "", White, ButtonBrown),
                new ButtonDef("lnx", "log", White, ButtonBrown),
                new ButtonDef("CE", "CP", White, ButtonBrown),
                new ButtonDef("CLR", "%", Dark, ButtonYellow),
             },
          new ButtonDef[]
             {
                new ButtonDef("LRN", "Pgm", White, ButtonBrown),
                new ButtonDef("x⇌t", "P→R", White, ButtonBrown),
                new ButtonDef("x²", "sin", White, ButtonBrown),
                new ButtonDef("√x", "cos", White, ButtonBrown),
                new ButtonDef("1/x", "tan", White, ButtonBrown),
             },
          new ButtonDef[]
             {
                new ButtonDef("SST", "Ins", White, ButtonBrown),
                new ButtonDef("STO", "CMs", White, ButtonBrown),
                new ButtonDef("RCL", "Exc", White, ButtonBrown),
                new ButtonDef("SUM", "Prd", White, ButtonBrown),
                new ButtonDef("yˣ", "Ind", White, ButtonBrown),
             },
          new ButtonDef[]
             {
                new ButtonDef("BST", "Del", White, ButtonBrown),
                new ButtonDef("EE", "Eng", White, ButtonBrown),
                new ButtonDef("(", "Fix", White, ButtonBrown),
                new ButtonDef(")", "Int", White, ButtonBrown),
                new ButtonDef("÷", "|x|", Dark, ButtonYellow),
             },
          new ButtonDef[]
             {
                new ButtonDef("GTO", "Pause", White, ButtonBrown),
                new ButtonDef("7", "x=t", "Pg*", Dark, White),
                new ButtonDef("8", "Nop", "Ex*", Dark, White),
                new ButtonDef("9", "Op", "PD*", Dark, White),
                new ButtonDef("×", "Deg", Dark, ButtonYellow),
             },
          new ButtonDef[]
             {
                new ButtonDef("SBR", "Lbl", White, ButtonBrown),
                new ButtonDef("4", "x≥t", "ST*", Dark, White),
                new ButtonDef("5", "∑+", "RC*", Dark, White),
                new ButtonDef("6", "x̅", "SM*", Dark, White),
                new ButtonDef("-", "Rad", Dark, ButtonYellow),
             },
          new ButtonDef[]
             {
                new ButtonDef("RST", "St flg", White, ButtonBrown),
                new ButtonDef("1", "If flg", Dark, White),
                new ButtonDef("2", "D.MS", "GT*", Dark, White),
                new ButtonDef("3", "π", "Op*", Dark, White),
                new ButtonDef("+", "Grad", Dark, ButtonYellow),
             },
          new ButtonDef[]
             {
                new ButtonDef("R/S", "Write", White, ButtonBrown),
                new ButtonDef("0", "Dsz", "RTN", Dark, White),
                new ButtonDef(".", "Adv", Dark, White),
                new ButtonDef("+/-", "Prt", Dark, White),
                new ButtonDef("=", "List", Dark, ButtonYellow),
             },
       };
    for (int Row = 0; Row < NrButtonRows; ++Row) {
      for (int Col = 0; Col < NrButtonCols; ++Col) {
        ButtonDefs[Row][Col].BaseCode = (Row + 1) * 10 + Col + 1;
      }
    }
  }

  public int FeedbackType;
  public static final int FEEDBACK_NONE = 0;
  public static final int FEEDBACK_CLICK = 1;
  public static final int FEEDBACK_VIBRATE = 2;
  public static final int FEEDBACK_BOTH = 3;

  public boolean OverlayVisible;

  private final RectF ButtonRelDisplayMargins = new RectF(0.175f, 0.5f, 0.175f, 0.05f);
  /* relative bounds of button within grid cell */
  private final RectF ButtonRelTouchMargins = new RectF(0.0f, 0.25f, 0.0f, 0.0f);

  /* global modifier state */
  public boolean AltState;

  public int SelectedButton = -1;
  private boolean SecondPress = false;

  public int DigitsNeeded;
  public boolean AcceptSymbolic, AcceptInd, NextLiteral;
  public int AccumDigits, FirstOperand;
  public boolean GotFirstOperand, GotFirstInd, IsSymbolic, GotInd;
  public int CollectingForFunction;
  private int ButtonCode;

  private long LastClick = 0;

  public void Reset() {
    /* resets to power-up state. */
    AltState = false;
    ResetOperands();
    OverlayVisible = false;
    invalidate();
  }

  public void SetFeedbackType
     (
        int NewFeedbackType
     ) {
    FeedbackType = NewFeedbackType;
    /* everything else has to be set up in constructor which has access to Context object */
  }

  private void DoFeedback() {
    if (FeedbackType == FEEDBACK_CLICK || FeedbackType == FEEDBACK_BOTH) {
      if (MakeNoise != null && ButtonDown != 0) {
        MakeNoise.play(ButtonDown, 1.0f, 1.0f, 0, 0, 1.0f);
      }
    }
    if (FeedbackType == FEEDBACK_VIBRATE || FeedbackType == FEEDBACK_BOTH) {
      if (Vibrate != null) {
        Vibrate.vibrate(50);
      }
    }
  }

  public ButtonGrid
     (
        android.content.Context TheContext,
        android.util.AttributeSet TheAttributes
     ) {
    super(TheContext, TheAttributes);
    final android.content.res.Resources Res = TheContext.getResources();
    Dark = Res.getColor(R.color.dark);
    White = Res.getColor(R.color.white);
    ButtonBrown = Res.getColor(R.color.button_brown);
    ButtonYellow = Res.getColor(R.color.button_yellow);
    OverlayBlue = Res.getColor(R.color.overlay_blue);
    AltButtonColor = Res.getColor(R.color.alt_button_color);
    MakeButtonDefs();
    MakeNoise = new android.media.SoundPool(1, android.media.AudioManager.STREAM_MUSIC, 0);
    ButtonDown = MakeNoise.load(TheContext, R.raw.button_down, 1);
    Vibrate = (android.os.Vibrator) TheContext.getSystemService(android.content.Context.VIBRATOR_SERVICE);

    setOnTouchListener
       (
          new android.view.View.OnTouchListener() {
            public boolean onTouch
               (
                  android.view.View TheView,
                  MotionEvent TheEvent
               ) {
              boolean Handled = false;
              if (!Global.BGTaskInProgress()) {
                final int EventAction = TheEvent.getAction() & (1 << MotionEvent.ACTION_POINTER_ID_SHIFT) - 1;
                switch (EventAction) {
                  case MotionEvent.ACTION_DOWN:
                    TheView.performClick();
                  case MotionEvent.ACTION_POINTER_DOWN:
                  case MotionEvent.ACTION_MOVE:
                    final long ThisClick = java.lang.System.currentTimeMillis();
                    if
                       (
                       (
                          ThisClick - LastClick > 100 /* debounce */
                             ||
                             !SecondPress && EventAction == MotionEvent.ACTION_POINTER_DOWN
                          /* no need to debounce second touch */
                       )
                          &&
                          (EventAction != MotionEvent.ACTION_MOVE || !SecondPress)
                          /* ignore move events once second finger goes down */
                       ) {
                      if (EventAction == MotionEvent.ACTION_POINTER_DOWN) {
                        SecondPress = true;
                      }
                      final RectF GridBounds =
                         new RectF(0.0f, 0.0f, TheView.getWidth(), TheView.getHeight());
                      final int PointerIndex =
                         EventAction == MotionEvent.ACTION_POINTER_DOWN ?
                            (TheEvent.getAction() & MotionEvent.ACTION_POINTER_ID_MASK)
                               >>
                               MotionEvent.ACTION_POINTER_ID_SHIFT
                            :
                            -1;
                      android.graphics.PointF ClickWhere =
                         EventAction == MotionEvent.ACTION_POINTER_DOWN ?
                            new android.graphics.PointF
                               (
                                  TheEvent.getX(PointerIndex),
                                  TheEvent.getY(PointerIndex)
                               )
                            :
                            new android.graphics.PointF(TheEvent.getX(), TheEvent.getY());
                      final float CellWidth = (float) TheView.getWidth() / NrButtonCols;
                      final float CellHeight = (float) TheView.getHeight() / NrButtonRows;
                      final android.graphics.Point ClickedCell =
                         new android.graphics.Point
                            (
                               Math.max(0, Math.min((int) Math.floor(ClickWhere.x / CellWidth), NrButtonCols - 1)),
                               Math.max(0, Math.min((int) Math.floor(ClickWhere.y / CellHeight), NrButtonRows - 1))
                            );
                      final RectF CellBounds = new RectF
                         (
                            GridBounds.left + CellWidth * ClickedCell.x,
                            GridBounds.top + CellHeight * ClickedCell.y,
                            GridBounds.left + CellWidth * (ClickedCell.x + 1),
                            GridBounds.top + CellHeight * (ClickedCell.y + 1)
                         );
                      final RectF ButtonBounds = new RectF
                         (
                            CellBounds.left + (CellBounds.right - CellBounds.left) * ButtonRelTouchMargins.left,
                            CellBounds.top + (CellBounds.bottom - CellBounds.top) * ButtonRelTouchMargins.top,
                            CellBounds.right + (CellBounds.left - CellBounds.right) * ButtonRelTouchMargins.right,
                            CellBounds.bottom + (CellBounds.top - CellBounds.bottom) * ButtonRelTouchMargins.bottom
                         );
                      int NewSelectedButton;
                      if (ButtonBounds.contains(ClickWhere.x, ClickWhere.y)) {
                        NewSelectedButton = ButtonDefs[ClickedCell.y][ClickedCell.x].BaseCode;
                      } else {
                        NewSelectedButton = -1;
                      }
                      if (SelectedButton != NewSelectedButton) {
                        SelectedButton = NewSelectedButton;
                        if (SelectedButton != -1) {
                          DoFeedback();
                          Invoke();
                        } else {
                          SecondPress = false;
                        }
                        TheView.invalidate();
                      }
                      Handled = true;
                      LastClick = ThisClick;
                    }
                    break;
                  case MotionEvent.ACTION_UP:
                    TheView.performClick();
                  case MotionEvent.ACTION_CANCEL:
                    if (SelectedButton != -1) {
                      if
                         (
                         SelectedButton == 61
                            &&
                            Global.Calc != null
                            &&
                            Global.Calc.TaskRunning
                         ) {
                        Global.Calc.SetSlowExecution(false);
                      }
                      SelectedButton = -1;
                      SecondPress = false;
                      TheView.invalidate();
                    }
                    Handled = true;
                    break;
                }
              }
              return
                 Handled;
            } /*onClick*/
          }
       );
    /* need to set myself focusable because volume up/down buttons are keys */
    setFocusable(true);
    setFocusableInTouchMode(true);
    setOnKeyListener
       (
          new android.view.View.OnKeyListener() {
            public boolean onKey
               (
                  android.view.View TheView,
                  int KeyCode,
                  android.view.KeyEvent TheEvent
               ) {
              boolean Handled = false;
              switch (TheEvent.getAction()) {
                case android.view.KeyEvent.ACTION_DOWN:
                  switch (KeyCode) {
                    case android.view.KeyEvent.KEYCODE_VOLUME_DOWN:
                      if (FeedbackType != FEEDBACK_NONE) {
                        SetFeedbackType
                           (
                              FeedbackType == FEEDBACK_CLICK ?
                                 FEEDBACK_VIBRATE
                                 :
                                 FEEDBACK_NONE
                           );
                        DoFeedback();
                        Handled = true;
                      }
                      break;
                    case android.view.KeyEvent.KEYCODE_VOLUME_UP:
                      if (FeedbackType != FEEDBACK_CLICK) {
                        SetFeedbackType
                           (
                              FeedbackType == FEEDBACK_NONE ?
                                 FEEDBACK_VIBRATE
                                 :
                                 FEEDBACK_CLICK
                           );
                        DoFeedback();
                        Handled = true;
                      }
                      break;
                  }
                  break;
                case android.view.KeyEvent.ACTION_UP:
                  switch (KeyCode) {
                    case android.view.KeyEvent.KEYCODE_VOLUME_DOWN:
                    case android.view.KeyEvent.KEYCODE_VOLUME_UP:
                      Handled = true;
                      break;
                  }
                  break;
              }
              return
                 Handled;
            } /*onKey*/
          }
       );
    Reset();
  }

  @Override
  public void onDraw
     (
        android.graphics.Canvas Draw
     ) {
    super.onDraw(Draw);
    final RectF GridBounds = new RectF(0.0f, 0.0f, getWidth(), getHeight());
    final float CellWidth = GridBounds.right / NrButtonCols;
    final float CellHeight = GridBounds.bottom / NrButtonRows;

    for (int Row = 0; Row < NrButtonRows; ++Row) {
      for (int Col = 0; Col < NrButtonCols; ++Col) {
        final ButtonDef ThisButton = ButtonDefs[Row][Col];
        final RectF CellBounds = new RectF
           (
              GridBounds.left + CellWidth * Col,
              GridBounds.top + CellHeight * Row,
              GridBounds.left + CellWidth * (Col + 1),
              GridBounds.top + CellHeight * (Row + 1)
           );
        final RectF ButtonBounds = new RectF
           (
              CellBounds.left + (CellBounds.right - CellBounds.left) * ButtonRelDisplayMargins.left,
              CellBounds.top + (CellBounds.bottom - CellBounds.top) * ButtonRelDisplayMargins.top,
              CellBounds.right + (CellBounds.left - CellBounds.right) * ButtonRelDisplayMargins.right,
              CellBounds.bottom + (CellBounds.top - CellBounds.bottom) * ButtonRelDisplayMargins.bottom
           );

        if (ThisButton.BaseCode == SelectedButton) {
          ButtonBounds.offset(2.0f, 2.0f);
        }

        Draw.drawRect(CellBounds, GraphicsUseful.FillWithColor(ThisButton.BGColor));
        final android.graphics.Paint TextPaint = new android.graphics.Paint();
        TextPaint.setStyle(android.graphics.Paint.Style.FILL);
        TextPaint.setColor(ThisButton.AltTextColor);
        TextPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        TextPaint.setAntiAlias(true);
        final float BaseTextSize = CellHeight * 0.33f;
        // alt text
        TextPaint.setTextSize(BaseTextSize * 0.75f);
        TextPaint.setTypeface(MFontL);
        GraphicsUseful.DrawCenteredText
           (
              Draw,
              ThisButton.AltText,
              (CellBounds.left + CellBounds.right) / 2.0f,
              CellBounds.top + (CellBounds.bottom - CellBounds.top) * ButtonRelDisplayMargins.top / 2.0f,
              TextPaint
           );
        {
          RectF DrawBounds;
          final GraphicsUseful.HSVA ButtonColor =
             new GraphicsUseful.HSVA(ThisButton.ButtonColor);
          TextPaint.setColor
             (
                new GraphicsUseful.HSVA
                   (
                      ButtonColor.H,
                      ButtonColor.S,
                      1.0f - (1.0f - ButtonColor.V) * 0.75f, /* lighten */
                      ButtonColor.A
                   ).ToRGB()
             );
          DrawBounds = new RectF(ButtonBounds);
          DrawBounds.offset(-1.0f, -1.0f);
          float cornerRoundness = 1.5f;
          Draw.drawRoundRect
             (
                DrawBounds,
                cornerRoundness,
                cornerRoundness,
                TextPaint
             );

          if (ThisButton.BaseCode != SelectedButton) {
            final GraphicsUseful.HSVA Darken = new GraphicsUseful.HSVA(Dark);
            TextPaint.setColor
               (
                  new GraphicsUseful.HSVA
                     (
                        Darken.H,
                        Darken.S,
                        Darken.V / 2.0f, /* darken */
                        Darken.A
                     ).ToRGB()
               );
            DrawBounds = new RectF(ButtonBounds);
            DrawBounds.offset(2.0f, 2.0f);
            Draw.drawRoundRect
               (
                  DrawBounds,
                  cornerRoundness,
                  cornerRoundness,
                  TextPaint
               );
          }
          TextPaint.setColor(ButtonColor.ToRGB());
          Draw.drawRoundRect
             (
                ButtonBounds,
                cornerRoundness,
                cornerRoundness,
                TextPaint
             );
        }

        if (OverlayVisible) {
          TextPaint.setTextAlign(android.graphics.Paint.Align.LEFT);
          final boolean HasBaseOverlay =
             ThisButton.BaseCode != 21
                &&
                ThisButton.BaseCode != 31
                &&
                ThisButton.BaseCode != 41
                &&
                ThisButton.BaseCode != 51;
          final boolean HasAltOverlay =
             ThisButton.BaseCode != 21
                &&
                ThisButton.BaseCode != 41
                &&
                ThisButton.BaseCode != 51;
          final boolean HasMergedOverlay = ThisButton.MergedText != null;

          if (HasBaseOverlay || HasAltOverlay || HasMergedOverlay) {
            float offs = -0.1f;
            if(Col == 0) offs = 0.015f;
            final float Left = CellBounds.left + (CellBounds.right - CellBounds.left) * offs;
            /* not quite authentic position, but what the hey */
            TextPaint.setTextSize(BaseTextSize * 0.6f);
            TextPaint.setColor(ThisButton.OverlayColor);

            if (HasBaseOverlay) {
              int BaseCode = ThisButton.BaseCode;
              switch (BaseCode) {
                case 62: /*digit 7*/
                case 63: /*digit 8*/
                case 64: /*digit 9*/
                  BaseCode -= 55;
                  break;
                case 72: /*digit 4*/
                case 73: /*digit 5*/
                case 74: /*digit 6*/
                  BaseCode -= 68;
                  break;
                case 82: /*digit 1*/
                case 83: /*digit 2*/
                case 84: /*digit 3*/
                  BaseCode -= 81;
                  break;
                case 92: /*digit 0*/
                  BaseCode = 0;
                  break;
              }
              Draw.drawText
                 (
                    String.format(Global.StdLocale, "%02d", BaseCode),
                    Left,
                    CellBounds.bottom + (ButtonBounds.top - ButtonBounds.bottom) * 0.2f,
                    TextPaint
                 );
            }

            if (HasMergedOverlay) {
              Draw.drawText
                 (
                    String.format
                       (
                          Global.StdLocale,
                          "%02d  %s",
                          ThisButton.BaseCode,
                          ThisButton.MergedText
                       ),
                    Left,
                    CellBounds.bottom + (ButtonBounds.top - ButtonBounds.bottom) * 0.8f,
                    TextPaint
                 );
            }

            if (HasAltOverlay) {
              int oCode = ThisButton.BaseCode / 10 * 10
                  +
                  (ThisButton.BaseCode % 10 + 5) % 10;
              if (oCode == 20) { /* extension % is code 51 */
                oCode = 51;
              }
              Draw.drawText
                 (
                    String.format
                       (
                          Global.StdLocale,
                          "%02d",
                          oCode
                       ),
                    Left,
                    CellBounds.bottom + (ButtonBounds.top - ButtonBounds.bottom) * 1.4f,
                    TextPaint
                 );
            }
          }
        }
        // label on buttons
        TextPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        TextPaint.setColor(ThisButton.TextColor);
        TextPaint.setTypeface(MFont);
        TextPaint.setTextSize(BaseTextSize * 0.9f);
        GraphicsUseful.DrawCenteredText
           (
              Draw,
              ThisButton.Text,
              (ButtonBounds.left + ButtonBounds.right) / 2.0f,
              (ButtonBounds.bottom + ButtonBounds.top) / 2.0f,
              TextPaint
           );
      }
    }
  }

  private void ResetOperands() {
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
      Global.Calc.StoreInstr(40);
    }
    if (IsSymbolic) {
      Global.Calc.StoreInstr(ButtonCode);
    } else {
      if (NrDigits < 3 || GotInd) {
        Global.Calc.StoreInstr(AccumDigits);
      } else {
        Global.Calc.StoreInstr(AccumDigits / 100);
        Global.Calc.StoreInstr(AccumDigits % 100);
      }
    }
  }

  private void Invoke() {
    final State Calc = Global.Calc; /* shorten references */

    if (Calc != null && SelectedButton > 0) {
      boolean WasModifier = false;
      boolean Handled = false;
      if (AltState) {
        ButtonCode = SelectedButton / 10 * 10 + (SelectedButton % 10 + 5) % 10;
      } else {
        ButtonCode = SelectedButton;
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
                  if (Calc.CurBank == 1 && AccumDigits == 0 && Global.Label != null) {
                    int ProgNr = (int) Calc.X.getInt();
                    if (ProgNr >= 1 && ProgNr < Calc.MaxBanks && Calc.Bank[ProgNr] != null) {
                      Calc.FillInLabels(ProgNr); // if not done already
                      Global.Label.SetHelp
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
              Persistent.SaveState(getContext(), false);
              if (Global.Export != null) {
                Global.Export.Flush();
              }
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
              Calc.Enter(99);
              if (Calc.InvState) /* extension! */ {
                Calc.GetNextImport();
              } else {
                if (Global.Export != null && Global.Export.NumbersOnly) {
                  Global.Export.WriteNum(Calc.X);
                }
                Calc.PrintDisplay(true, false, "");
              }
              break;
            case 90: /*List*/
              Calc.Enter(90);
              if (Global.Print != null) {
                if (Calc.InvState) {
                  Calc.StartRegisterListing();
                } else {
                  Calc.StartProgramListing();
                }
              }
              break;
          }
          Calc.PreviousOp = ButtonCode;
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
  }
}
