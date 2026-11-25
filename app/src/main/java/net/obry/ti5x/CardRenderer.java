/*
    Draws a simple user label card

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

package net.obry.ti5x;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import net.obry.ti5x.engine.CardDef;

/* Renders a CardDef the same way as util/rendercard does for the "user" style:
   a 112 x 18 mm card at 144 dpi, split in 4 rows. The top row holds the fixed
   "TEXAS INSTRUMENTS" text of the blank TI cards, the second the title and the
   program id, the third the A' to E' labels and the last the A to E labels. */

class CardRenderer {
  private static final float Scale = 144.0f / 72.0f; /* pixels per point */

  private static float mm
     (
        float Dist
     ) {
    /* returns the number of pixels for Dist in millimetres */
    return Dist * 72.0f / 25.4f * Scale;
  }

  /* 112 x 18 mm, same size in pixels as the cards made by util/rendercard */
  static final int Width = 634;
  static final int Height = 102;

  private static final float CornerRadius = mm(1.5f);
  private static final float LabelMargin = mm(4.0f);
  private static final float FontSize = 10.5f * Scale;
  private static final float LineWidth = 0.5f * Scale;
  private static final String TopText = "TEXAS INSTRUMENTS";

  /* "dorange" and "black" of util/rendercard */
  private static final int Background =
     Color.rgb(0xfc * 255 / 315, 0xd1 * 255 / 315, 0x71 * 255 / 315);
  private static final int Foreground = Color.rgb(5, 5, 5);

  private static float CellEdge
     (
        int Col
     ) {
    return Width * Col / 5.0f;
  }

  private static float Baseline
     (
        int Row /* 0 .. 3 from the top */
     ) {
    return Height * (Row + 0.75f) / 4.0f;
  }

  private static void DrawText
     (
        Canvas Draw,
        Paint TextPaint,
        String Text,
        float Left,
        float Right,
        Paint.Align Align,
        int Row
     ) {
    /* draws Text on Row between Left and Right, reducing the font size if it
       does not fit */
    if (Text.isEmpty())
      return;
    final float Available = Right - Left;
    TextPaint.setTextSize(FontSize);
    final float TextWidth = TextPaint.measureText(Text);
    if (TextWidth > Available) {
      TextPaint.setTextSize(FontSize * Available / TextWidth);
    }
    TextPaint.setTextAlign(Align);
    final float X =
       Align == Paint.Align.LEFT ? Left : Align == Paint.Align.RIGHT ? Right : (Left + Right) / 2.0f;
    Draw.drawText(Text, X, Baseline(Row), TextPaint);
  }

  static Bitmap Render
     (
        CardDef Def
     ) {
    final Bitmap Result = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888);
    final Canvas Draw = new Canvas(Result);
    final Paint Fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    Fill.setStyle(Paint.Style.FILL);
    Fill.setColor(Background);
    Draw.drawRoundRect(new RectF(0, 0, Width, Height), CornerRadius, CornerRadius, Fill);

    final Paint Lines = new Paint(Paint.ANTI_ALIAS_FLAG);
    Lines.setStyle(Paint.Style.STROKE);
    Lines.setStrokeWidth(LineWidth);
    Lines.setColor(Foreground);
    for (int Row = 1; Row < 4; ++Row) {
      Draw.drawLine(0, Height * Row / 4.0f, Width, Height * Row / 4.0f, Lines);
    }
    for (int Col = 1; Col < 5; ++Col) {
      Draw.drawLine(CellEdge(Col), Height / 2.0f, CellEdge(Col), Height, Lines);
    }

    final Paint TextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    TextPaint.setColor(Foreground);
    TextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

    DrawText(Draw, TextPaint, TopText, 0, Width, Paint.Align.CENTER, 0);

    /* program id on the right, the title takes the rest of the row */
    float TitleRight = Width - LabelMargin;
    if (!Def.Id.isEmpty()) {
      TextPaint.setTextSize(FontSize);
      TitleRight -= TextPaint.measureText(Def.Id) + LabelMargin;
      DrawText(Draw, TextPaint, Def.Id, LabelMargin, Width - LabelMargin, Paint.Align.RIGHT, 1);
    }
    DrawText(Draw, TextPaint, Def.Title, LabelMargin, TitleRight, Paint.Align.LEFT, 1);

    final float CellPad = LineWidth * 4;
    for (int Col = 0; Col < CardDef.NrCells; ++Col) {
      final float Left = CellEdge(Col) + CellPad;
      final float Right = CellEdge(Col + 1) - CellPad;
      DrawText(Draw, TextPaint, Def.Prime[Col], Left, Right, Paint.Align.CENTER, 2);
      DrawText(Draw, TextPaint, Def.Base[Col], Left, Right, Paint.Align.CENTER, 3);
    }
    return Result;
  }

  static byte[] ToPng
     (
        Bitmap Image
     ) {
    final java.io.ByteArrayOutputStream Out = new java.io.ByteArrayOutputStream();
    Image.compress(Bitmap.CompressFormat.PNG, 100, Out);
    return Out.toByteArray();
  }
}
