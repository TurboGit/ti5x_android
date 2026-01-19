/*
    Calculator display area

    Copyright 2011-2012 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
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

import android.graphics.PointF;
import android.graphics.Path;

class Display extends android.view.View {
  /* rendering parameters */
  private static final float DigitWidth = 0.5f; /* as fraction of size */
  private static final float SegmentHalfWidth = 0.05f; /* as fraction of size */
  private static final float SegmentMargin = 0.025f; /* as fraction of size */
  private static final float Slant = 0.1f; /* tangent of slant angle to right from vertical */
  private static final float DotScale = 1.5f; /* make dot bigger than line thickness */
  private static final int NrDigits = 12;
  private final int LEDLight, LEDDim, LEDOff;
  private int[] Showing;
  private int[] OtherShowing;
  private int ShowingColor, OtherColor;
  private float AnimDelay;
  private final android.os.Handler Idler;
  private Runnable IdleTask = null;

  public Display
     (
        android.content.Context TheContext,
        android.util.AttributeSet TheAttributes
     ) {
    super(TheContext, TheAttributes);
    final android.content.res.Resources Res = TheContext.getResources();
    LEDLight = Res.getColor(R.color.led_light);
    LEDDim = Res.getColor(R.color.led_dim);
    LEDOff = Res.getColor(R.color.led_off);
    Showing = new int[NrDigits];
    for (int i = 0; i < NrDigits; ++i) {
      Showing[i] = 0;
    }
    OtherShowing = Showing.clone();
    Idler = new android.os.Handler();
  }

  /*
      Segment masks are
           --- 0x001
      | 0x002    | 0x004
           --- 0x008
      | 0x010    | 0x020
           --- 0x040  . 0x100
  */
  private static int SegmentCode
  (
     char ForChar
  ) {
    /* generates a code indicating which LED segments to light up. */
    int TheCode;
    switch (ForChar) {
      case '0':
        TheCode = 0x077;
        break;
      case '1':
        TheCode = 0x024;
        break;
      case '2':
        TheCode = 0x05D;
        break;
      case '3':
        TheCode = 0x06D;
        break;
      case '4':
        TheCode = 0x02E;
        break;
      case '5':
        TheCode = 0x06B;
        break;
      case '6':
        TheCode = 0x07B;
        break;
      case '7':
        TheCode = 0x025;
        break;
      case '8':
        TheCode = 0x07F;
        break;
      case '9':
        TheCode = 0x06F;
        break;
      case '-':
        TheCode = 0x008;
        break;
      case '.':
        TheCode = 0x100;
        break;
      case ' ':
        TheCode = 0x000;
        break;
      case 'C':
        TheCode = 0x053;
        break;
      case 'E':
        TheCode = 0x05B;
        break;
      case 'c':
        TheCode = 0x058;
        break;
      case 'o':
        TheCode = 0x078;
        break;
      case 'r':
        TheCode = 0x018;
        break;
      default:
        TheCode = 0x05B; /* "E" for error */
        break;
    }
    return
       TheCode;
  }

  private void SetShowing
     (
        int[] ToShow /* sequence of segment codes */
     ) {
    ClearAnimShowing();
    final int NrToShow = Math.min(ToShow.length, NrDigits);
    final int Offset = NrDigits - NrToShow;
    for (int i = NrToShow; ; ) {
      if (i == 0)
        break;
      --i;
      Showing[i + Offset] = ToShow[i];
    }
    for (int i = Offset; ; ) {
      if (i == 0)
        break;
      --i;
      Showing[i] = SegmentCode(' ');
    }
    ShowingColor = LEDLight;
    invalidate();
  }

  public void SetShowing
     (
        String ToShow /* sequence of digits, possibly also minus signs and spaces */
     ) {
    ClearAnimShowing();
    int j = NrDigits;
    boolean Decrement = true;
    for (int i = ToShow.length(); ; ) {
      if (i == 0 || Decrement && j == 0)
        break;
      --i;
      final char ThisCh = ToShow.charAt(i);
      if (Decrement) {
        --j;
        Showing[j] = 0;
      }
      Decrement = ThisCh != '.';
      Showing[j] |= SegmentCode(ThisCh);
    }
    while (j > 0) {
      Showing[--j] = 0;
    }
    ShowingColor = LEDLight;
    invalidate();
  }

  public void SetStateShowing()
  {
    invalidate();
  }

  class Flashing implements Runnable {
    public void run() {
      final int[] SwapShowing = Showing;
      Showing = OtherShowing;
      OtherShowing = SwapShowing;
      final int SwapColor = ShowingColor;
      ShowingColor = OtherColor;
      OtherColor = SwapColor;
      invalidate();
      if (IdleTask != null) {
        Idler.postDelayed(IdleTask, (int) (AnimDelay * 1000.0f));
      }
    }
  }

  private void ClearAnimShowing() {
    if (IdleTask != null) {
      Idler.removeCallbacks(IdleTask);
      IdleTask = null;
    }
  }

  public void SetShowingRunning
     (
        char Ch
     ) {
    ClearAnimShowing();
    for (int i = 0; i < OtherShowing.length; ++i) {
      OtherShowing[i] = SegmentCode(i == 0 ? Ch : ' ');
    }
    SetShowing(OtherShowing);
    ShowingColor = LEDLight;
    OtherColor = LEDDim;
    AnimDelay = 0.25f;
    IdleTask = new Flashing();
    Idler.postDelayed(IdleTask, (int) (AnimDelay * 1000.0f));
  }

  public void SetShowingError
     (
        String ToShow
     ) {
    SetShowing(ToShow);
    for (int i = 0; i < OtherShowing.length; ++i) {
      OtherShowing[i] = 0;
    }
    ShowingColor = LEDLight;
    OtherColor = LEDLight;
    AnimDelay = 0.5f;
    IdleTask = new Flashing();
    Idler.postDelayed(IdleTask, (int) (AnimDelay * 1000.0f));
  }

  private static void RenderSegments
     (
        android.graphics.Canvas Draw,
        android.graphics.Paint UsePaint,
        int Segments,
        float XOrigin,
        float YOrigin,
        float Size,
        int Color
     ) {
    /* renders the specified combination of LED segments and dots at the specified
       size and position, in the specified colour, into the specified canvas. */
    XOrigin += Size * SegmentHalfWidth; /* so there is no overhang to the left */
    YOrigin -= Size * SegmentHalfWidth; /* so there is no overhang below */
    final PointF[] ControlPoint = new PointF[]
       {
           /* control points for positioning digit segments: */
          new PointF(XOrigin + Size * Slant, YOrigin - Size),
          new PointF(XOrigin + Size * (Slant + DigitWidth), YOrigin - Size),
          new PointF(XOrigin + Size * Slant / 2.0f, YOrigin - Size / 2.0f),
          new PointF(XOrigin + Size * (Slant / 2.0f + DigitWidth), YOrigin - Size / 2.0f),
          new PointF(XOrigin, YOrigin),
          new PointF(XOrigin + Size * DigitWidth, YOrigin),
           /* control point for positioning dot: */
          new PointF
             (
                XOrigin + Size * (DigitWidth * 1.2f + Slant * 0.25f),
                YOrigin
             ),
       };
    final float Margin = Size * SegmentMargin;
    final float Bevel = Size * SegmentHalfWidth;
    final float XOffset1 = Slant * SegmentMargin * Size;
    final float XOffset2 = Slant * SegmentHalfWidth * Size;
    for (int i = 1; i <= 3; ++i) {
      /* render horizontal segments */
      if ((Segments & 1 << 3 * (i - 1)) != 0) {
        final PointF Left = ControlPoint[2 * i - 2];
        final PointF Right = ControlPoint[2 * i - 1];
        final Path p = new Path();
        p.moveTo(Left.x + Margin, Left.y);
        p.lineTo(Left.x + Margin + Bevel + XOffset2, Left.y - Bevel);
        p.lineTo(Right.x - Margin - Bevel + XOffset2, Right.y - Bevel);
        p.lineTo(Right.x - Margin, Right.y);
        p.lineTo(Right.x - Margin - Bevel - XOffset2, Right.y + Bevel);
        p.lineTo(Left.x + Margin + Bevel - XOffset2, Left.y + Bevel);
        p.close();
        Draw.drawPath(p, UsePaint);
      }
    }
    for (int Row = 1; Row <= 2; ++Row) {
      /* render vertical segments */
      for (int Col = 1; Col <= 2; ++Col) {
        if ((Segments & 1 << 3 * Row + Col - 3) != 0) {
          final PointF Top = ControlPoint[2 * Row + Col - 3];
          final PointF Bottom = ControlPoint[2 * Row + Col - 1];
          final Path p = new Path();
          p.moveTo(Top.x - XOffset1, Top.y + Margin);
          p.lineTo(Top.x + Bevel - XOffset1 - XOffset2, Top.y + Margin + Bevel);
          p.lineTo(Bottom.x + Bevel + XOffset1 + XOffset2, Bottom.y - Margin - Bevel);
          p.lineTo(Bottom.x + XOffset1, Bottom.y - Margin);
          p.lineTo(Bottom.x - Bevel + XOffset1 + XOffset2, Bottom.y - Margin - Bevel);
          p.lineTo(Top.x - Bevel - XOffset1 - XOffset2, Top.y + Margin + Bevel);
          p.close();
          Draw.drawPath(p, UsePaint);
        }
      }
    }
    /* render dot */
    if ((Segments & 1 << 8) != 0) {
      final PointF Where = ControlPoint[6];
      final Path p = new Path();
      p.moveTo(Where.x - DotScale * Bevel, Where.y);
      p.lineTo(Where.x + XOffset1, Where.y - DotScale * Bevel);
      p.lineTo(Where.x + DotScale * Bevel, Where.y);
      p.lineTo(Where.x - XOffset1, Where.y + DotScale * Bevel);
      p.close();
      Draw.drawPath(p, UsePaint);
    }
  }

  @Override
  public void onDraw
     (
        android.graphics.Canvas Draw
     ) {
    super.onDraw(Draw);
    final android.graphics.RectF DisplayBounds =
       new android.graphics.RectF(0, 0, getWidth(), getHeight());
    final android.graphics.Paint UsePaint = GraphicsUseful.FillWithColor(ShowingColor);
    UsePaint.setTextSize(DisplayBounds.bottom * 0.2f);      // Set text size
    UsePaint.setAntiAlias(true);
    Draw.drawRect(DisplayBounds, GraphicsUseful.FillWithColor(LEDOff));
    for (int i = 0; i < Showing.length; ++i) {
      RenderSegments
         (
            Draw,
            UsePaint,
            Showing[i],
            i * DisplayBounds.right / NrDigits,
            DisplayBounds.bottom * 0.75f,
            DisplayBounds.bottom * 0.6f,
            ShowingColor
         );
    }

    //  Draw states [DGR][2][I]
    float sx = 10;
    final float sy = DisplayBounds.bottom * 0.95f;
    String StatStr = "";
    switch (Global.Calc.CurAng) {
      case State.ANG_GRAD:
        StatStr = "G";
        break;
      case State.ANG_RAD:
        StatStr = "R";
        break;
      case State.ANG_DEG:
        StatStr = " ";
      default: // Degrees
        StatStr = " ";
        break;
    }

    if (Global.Buttons.AltState) {
      StatStr = StatStr + "2";
    }
    else {
      StatStr = StatStr + " ";
    }
    if (Global.Calc.InvState) {
      StatStr = StatStr + "I";
    }
    else {
      StatStr = StatStr + " ";
    }
    Draw.drawText(StatStr, sx, sy, UsePaint);
  }
}
