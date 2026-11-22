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

  public int SelectedButton = -1; /* key code of the button being pressed */
  private boolean SecondPress = false;

  private long LastClick = 0;

  public void Reset() {
    /* resets to power-up state. */
    if (Global.Engine != null) {
      Global.Engine.Keys.Reset();
    }
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
                      if (Global.Engine != null) {
                        Global.Engine.Keys.Release(SelectedButton);
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
    OverlayVisible = false; /* the keypad state is reset by the engine */
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

  private void Invoke() {
    if (Global.Engine != null && SelectedButton > 0) {
      Global.Engine.Keys.Press(SelectedButton);
    }
  }
}
