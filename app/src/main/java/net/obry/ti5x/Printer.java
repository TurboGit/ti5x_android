/*
    ti5x calculator emulator -- virtual printer rendering

    Copyright 2011      Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2016-2018 Steven Zoppi <about-ti5x@zoppi.org>.
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

package net.obry.ti5x;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

class Printer {

  ArrayList<byte[]> Content = new ArrayList<>();
  private android.content.Context PrtContext;

  private final static int DotSize = 2;
  private final static int DotGap = 1;
  private final static int CharWidth = 5;
  private final static int CharHeight = 7;
  private final static int CharHorGap = 1;
  private final static int CharVertGap = 1;
  public final static int PaddingWidth = 10;
  final static int CharColumns = 20;
  private final static int CharLines = 125; /* make this configurable? */

  public final int LineHeight = (CharHeight + CharVertGap) * (DotSize + DotGap);
  private final int ColumnWidth = (CharWidth + CharHorGap) * (DotSize + DotGap);

  public final int PaperWidth = ColumnWidth * CharColumns + (PaddingWidth * 2);

  private final int PaperColor;
  private final int InkColor;

  private static final int[][] Chars =
      {
          {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, /*00*/
          {0x0e, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0e}, /*01*/
          {0x04, 0x06, 0x04, 0x04, 0x04, 0x04, 0x0e}, /*02*/
          {0x0e, 0x11, 0x10, 0x0c, 0x02, 0x01, 0x1f}, /*03*/
          {0x0e, 0x11, 0x10, 0x0c, 0x10, 0x11, 0x0e}, /*04*/
          {0x08, 0x0c, 0x0a, 0x09, 0x1f, 0x08, 0x08}, /*05*/
          {0x1f, 0x01, 0x0f, 0x10, 0x10, 0x11, 0x0e}, /*06*/
          {0x0c, 0x02, 0x01, 0x0f, 0x11, 0x11, 0x0e}, /*07*/
          {0x1f, 0x10, 0x08, 0x04, 0x02, 0x02, 0x02}, /*10*/
          {0x0e, 0x11, 0x11, 0x0e, 0x11, 0x11, 0x0e}, /*11*/
          {0x0e, 0x11, 0x11, 0x1e, 0x10, 0x08, 0x06}, /*12*/
          {0x0e, 0x11, 0x11, 0x1f, 0x11, 0x11, 0x11}, /*13*/
          {0x0f, 0x11, 0x11, 0x0f, 0x11, 0x11, 0x0f}, /*14*/
          {0x0e, 0x11, 0x01, 0x01, 0x01, 0x11, 0x0e}, /*15*/
          {0x0f, 0x12, 0x12, 0x12, 0x12, 0x12, 0x0f}, /*16*/
          {0x1f, 0x01, 0x01, 0x0f, 0x01, 0x01, 0x1f}, /*17*/
          {0x00, 0x00, 0x00, 0x1f, 0x00, 0x00, 0x00}, /*20*/
          {0x1f, 0x01, 0x01, 0x0f, 0x01, 0x01, 0x01}, /*21*/
          {0x0e, 0x11, 0x01, 0x01, 0x19, 0x11, 0x1e}, /*22*/
          {0x11, 0x11, 0x11, 0x1f, 0x11, 0x11, 0x11}, /*23*/
          {0x0e, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0e}, /*24*/
          {0x10, 0x10, 0x10, 0x10, 0x10, 0x11, 0x0e}, /*25*/
          {0x11, 0x09, 0x05, 0x03, 0x05, 0x09, 0x11}, /*26*/
          {0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x1f}, /*27*/
          {0x11, 0x1b, 0x15, 0x15, 0x11, 0x11, 0x11}, /*30*/
          {0x11, 0x11, 0x13, 0x15, 0x19, 0x11, 0x11}, /*31*/
          {0x1f, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1f}, /*32*/
          {0x0f, 0x11, 0x11, 0x0f, 0x01, 0x01, 0x01}, /*33*/
          {0x0e, 0x11, 0x11, 0x11, 0x15, 0x19, 0x1e}, /*34*/
          {0x0f, 0x11, 0x11, 0x0f, 0x05, 0x09, 0x11}, /*35*/
          {0x0e, 0x11, 0x01, 0x0e, 0x10, 0x11, 0x0e}, /*36*/
          {0x1f, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04}, /*37*/
          {0x00, 0x00, 0x00, 0x00, 0x00, 0x06, 0x06}, /*40*/
          {0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0e}, /*41*/
          {0x11, 0x11, 0x11, 0x0a, 0x0a, 0x04, 0x04}, /*42*/
          {0x11, 0x11, 0x11, 0x15, 0x15, 0x15, 0x0a}, /*43*/
          {0x11, 0x11, 0x0a, 0x04, 0x0a, 0x11, 0x11}, /*44*/
          {0x11, 0x11, 0x0a, 0x04, 0x04, 0x04, 0x04}, /*45*/
          {0x1f, 0x10, 0x08, 0x04, 0x02, 0x01, 0x1f}, /*46*/
          {0x00, 0x04, 0x04, 0x1f, 0x04, 0x04, 0x00}, /*47*/
          {0x00, 0x11, 0x0a, 0x04, 0x0a, 0x11, 0x00}, /*50*/
          {0x00, 0x0a, 0x04, 0x1f, 0x04, 0x0a, 0x00}, /*51*/
          {0x1e, 0x02, 0x02, 0x02, 0x02, 0x03, 0x02}, /*52*/
          {0x00, 0x10, 0x0e, 0x0b, 0x0a, 0x0a, 0x0a}, /*53*/
          {0x00, 0x00, 0x0e, 0x11, 0x1f, 0x01, 0x0e}, /*54*/
          {0x10, 0x08, 0x04, 0x04, 0x04, 0x08, 0x10}, /*55*/
          {0x01, 0x02, 0x04, 0x04, 0x04, 0x02, 0x01}, /*56*/
          {0x00, 0x00, 0x00, 0x03, 0x03, 0x02, 0x01}, /*57*/
          {0x04, 0x0e, 0x15, 0x04, 0x04, 0x04, 0x04}, /*60*/
          {0x03, 0x13, 0x08, 0x04, 0x02, 0x19, 0x18}, /*61*/
          {0x04, 0x0c, 0x04, 0x00, 0x04, 0x06, 0x04}, /*62*/
          {0x00, 0x10, 0x08, 0x04, 0x02, 0x01, 0x00}, /*63*/
          {0x00, 0x00, 0x1f, 0x00, 0x1f, 0x00, 0x00}, /*64*/
          {0x0c, 0x0c, 0x0c, 0x00, 0x00, 0x00, 0x00}, /*65*/
          {0x11, 0x0a, 0x04, 0x0a, 0x11, 0x00, 0x00}, /*66*/
          {0x1f, 0x00, 0x11, 0x0a, 0x04, 0x0a, 0x11}, /*67*/
          {0x07, 0x08, 0x06, 0x01, 0x0f, 0x00, 0x00}, /*70*/
          {0x0e, 0x11, 0x11, 0x08, 0x04, 0x00, 0x04}, /*71*/
          {0x00, 0x04, 0x00, 0x1f, 0x00, 0x04, 0x00}, /*72*/
          {0x04, 0x0a, 0x0a, 0x0a, 0x04, 0x00, 0x04}, /*73*/
          {0x1f, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x1f}, /*74*/
          {0x00, 0x00, 0x00, 0x00, 0x04, 0x0a, 0x15}, /*75*/
          {0x1f, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a}, /*76*/
          {0x1f, 0x02, 0x04, 0x08, 0x04, 0x02, 0x1f}, /*77*/
      };

  private static final String[] PrintMnemonics =
      {
          /* all are 3 characters */
          /*01*/ " 01",
          /*02*/ " 02",
          /*03*/ " 03",
          /*04*/ " 04",
          /*05*/ " 05",
          /*06*/ " 06",
          /*07*/ " 07",
          /*08*/ " 08",
          /*09*/ " 09",
          /*00*/ " 00",
          /* note ones above need to be treated specially */
          /*11*/ " A ",
          /*12*/ " B ",
          /*13*/ " C ",
          /*14*/ " D ",
          /*15*/ " E ",
          /*16*/ "A' ",
          /*17*/ "B' ",
          /*18*/ "C' ",
          /*19*/ "D' ",
          /*10*/ "E' ",
          /*21*/ "2ND",
          /*22*/ "INV",
          /*23*/ "LNX",
          /*24*/ "CE ",
          /*25*/ "CLR",
          /*26*/ "2ND",
          /*27*/ "INV", /* guess */
          /*28*/ "LOG",
          /*29*/ "CP ",
          /*20*/ " % ", /* extended % */
          /*31*/ "LRN",
          /*32*/ "X⇌T",
          /*33*/ "X² ",
          /*34*/ "√X ",
          /*35*/ "1/X",
          /*36*/ "PGM",
          /*37*/ "P/R",
          /*38*/ "SIN",
          /*39*/ "COS",
          /*30*/ "TAN",
          /*41*/ "SST",
          /*42*/ "STO",
          /*43*/ "RCL",
          /*44*/ "SUM",
          /*45*/ "Yⓧ ", /* standing in for y-superscript-x */
          /*46*/ "INS", /* guess */
          /*47*/ "CMS",
          /*48*/ "EXC",
          /*49*/ "PRD",
          /*40*/ "IND",
          /*51*/ "BST",
          /*52*/ "EE ",
          /*53*/ " ( ",
          /*54*/ " ) ",
          /*55*/ " ÷ ",
          /*56*/ "DEL", /* guess */
          /*57*/ "ENG",
          /*58*/ "FIX",
          /*59*/ "INT",
          /*50*/ "I×I",
          /*61*/ "GTO",
          /*62*/ "PG*",
          /*63*/ "EX*",
          /*64*/ "PD*",
          /*65*/ " × ",
          /*66*/ "PAU",
          /*67*/ " EQ",
          /*68*/ "NOP",
          /*69*/ "OP ",
          /*60*/ "DEG",
          /*71*/ "SBR",
          /*72*/ "ST*",
          /*73*/ "RC*",
          /*74*/ "SM*",
          /*75*/ " - ",
          /*76*/ "LBL",
          /*77*/ " GE",
          /*78*/ "∑+ ",
          /*79*/ " ẍ ", /* closest I can get in Unicode to an x with a bar over it */
          /*70*/ "RAD",
          /*81*/ "RST",
          /*82*/ "HIR",
          /*83*/ "GO*",
          /*84*/ "OP*",
          /*85*/ " + ",
          /*86*/ "STF",
          /*87*/ "IFF",
          /*88*/ "DMS",
          /*89*/ " π ",
          /*80*/ "GRD",
          /*91*/ "R/S",
          /*92*/ "RTN",
          /*93*/ " . ",
          /*94*/ "+/-",
          /*95*/ " = ",
          /*96*/ "WRT",
          /*97*/ "DSZ",
          /*98*/ "ADV",
          /*99*/ "PRT",
          /*90*/ "LST",
      };

  static String KeyCodeSym
      (
          int KeyCode
      ) {
    /* returns the PrintMnemonics symbol for the specified keycode (assumed in [00 .. 99]). */
    final int CodeIndex =
        KeyCode / 10 * 10
            +
            (KeyCode % 10 == 0 ? 9 : KeyCode % 10 - 1);
    return PrintMnemonics[CodeIndex];
  }

  public Printer
      (
          android.content.Context ctx
      ) {
    final android.content.res.Resources Res = ctx.getResources();
    //  This context is needed so we can call SavePaper (and other functions) properly
    PrtContext = ctx;
    PaperColor = Res.getColor(R.color.paper);
    InkColor = Res.getColor(R.color.ink);
    // skip first line
    Content.add(new byte[0]);
  }

  boolean SavePaper(Bitmap Paper, StringBuilder mes) {

    // Clear message
    mes.setLength(0);

    String extStorageDirectory = Environment.getExternalStorageState();

    if (Environment.MEDIA_MOUNTED.equals(extStorageDirectory)) {
      extStorageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
    } else {
      extStorageDirectory = Environment.getDownloadCacheDirectory().toString();
    }

    OutputStream outputStream = null;
    @SuppressLint("SimpleDateFormat") /* we want a specific format without slash as separator */
        String outString = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss.SSS").format(new Date());
    File file = new File(extStorageDirectory, "ti5x " + outString + ".png");

    try {
      outputStream = new FileOutputStream(file);
      boolean ok = Paper.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
    } catch (FileNotFoundException ex) {
      //exception handling code
      ex.printStackTrace();
      mes.append("Can't Create. " + extStorageDirectory +
          "/ti5x " + outString + ".png");
      return false;
    } finally {
      try {
        if (outputStream != null) {
          outputStream.flush();
          outputStream.close();
          mes.append("Saved to " + extStorageDirectory +
              "/ti5x " + outString + ".png");
          return true;
        }
      } catch (java.io.IOException ex) {
        ex.printStackTrace();
        mes.append("Error Occurred.");
        return false;
      }
    }
    return false;
  }

  void Advance() {
    // advances the paper to the next line.
    Content.add(new byte[0]);

    if (Global.Export != null && !Global.Export.NumbersOnly) {
      Global.Export.WriteLine("");
    }
  }

  int[] renderLine(byte[] PrintReg) {
    final int[] Line = new int[PaperWidth * LineHeight];
    for (int i = 0; i < Line.length; ++i) {
      /* initialize background */
      Line[i] = PaperColor;
    }
    for (int CharCol = 0; CharCol < Math.min(PrintReg.length, CharColumns); ++CharCol) {
      final int GlyphRow = (int) PrintReg[CharCol] / 10 % 10;
      final int GlyphCol = (int) PrintReg[CharCol] % 10;
      if (GlyphRow >= 0 && GlyphRow < 8 && GlyphCol >= 0 && GlyphCol < 8) {
        final int[] Glyph = Chars[GlyphRow * 8 + GlyphCol];
        for (int Row = 0; Row < CharHeight; ++Row) {
          for (int PixCol = 0; PixCol < CharWidth; ++PixCol) {
            if ((1 << PixCol & Glyph[Row]) != 0) {
              /* place a dot */
              final int Origin =
                  (
                      CharCol * (CharWidth + CharHorGap)
                          +
                          CharHorGap
                          +
                          PixCol
                          +
                          Row * PaperWidth
                  )
                      *
                      (DotSize + DotGap);
              for (int i = 0; i < DotSize; ++i) {
                for (int j = 0; j < DotSize; ++j) {
                  Line[Origin + i * PaperWidth + j] = InkColor;
                }
              }
            }
          }
        }
      }
    }
    return Line;
  }
  public int[] getLineContent(int line)
  {
    if(Content.size() > line) {
      return renderLine(Content.get(line));
    }
    else {
      return new int[0];
    }
  }

  void Render
     (
        byte[] PrintReg
     ) {
    if (Global.Export != null && !Global.Export.NumbersOnly) {
      Global.Export.WriteLine(BackToText(PrintReg));
    }
    Content.add(PrintReg.clone());
  }

  void Translate
     (
        String Chars,
        byte[] Translated
     ) {
    for (int i = 0; i < Math.min(Chars.length(), Translated.length); ++i) {
      final char ch = Chars.charAt(i);
      int glyph = 0;
      if (ch == ' ') {
        glyph = 0;
      } else if (ch >= 'A' && ch <= 'E') {
        glyph = 13 + (int) ch - (int) 'A';
      } else if (ch >= 'F' && ch <= 'L') {
        glyph = 21 + (int) ch - (int) 'F';
      } else if (ch >= 'M' && ch <= 'T') {
        glyph = 30 + (int) ch - (int) 'M';
      } else if (ch >= 'U' && ch <= 'Z') {
        glyph = 41 + (int) ch - (int) 'U';
      } else if (ch >= '0' && ch <= '6') {
        glyph = 1 + (int) ch - (int) '0';
      } else if (ch >= '7' && ch <= '9') {
        glyph = 10 + (int) ch - (int) '7';
      } else if (ch == '.') {
        glyph = 40;
      } else if (ch == '+') {
        glyph = 47;
      } else if (ch == '-') {
        glyph = 20;
      } else if (ch == '*') {
        glyph = 51;
      } else if (ch == '×') {
        glyph = 50;
      } else if (ch == '÷') {
        glyph = 72;
      } else if (ch == '√') {
        glyph = 52;
      } else if (ch == 'π') {
        glyph = 53;
      } else if (ch == 'e') {
        glyph = 54;
      } else if (ch == '(') {
        glyph = 55;
      } else if (ch == ')') {
        glyph = 56;
      } else if (ch == ',') {
        glyph = 57;
      } else if (ch == '↑') {
        glyph = 60;
      } else if (ch == '%') {
        glyph = 61;
      } else if (ch == '⇌') {
        glyph = 62;
      } else if (ch == '/') {
        glyph = 63;
      } else if (ch == '=') {
        glyph = 64;
      } else if (ch == '\'') {
        glyph = 65;
      } else if (ch == 'ⓧ') /* standing in for superscript x */ {
        glyph = 66;
      } else if (ch == 'ẍ') /* closest I can get in Unicode to an x with a bar over it */ {
        glyph = 67;
      } else if (ch == '²') {
        glyph = 70;
      } else if (ch == '?') {
        glyph = 71;
      } else if (ch == '!') /*?*/ {
        glyph = 73;
      } else if (ch == '♊') /* Gemini! */ {
        glyph = 74; /* looks like pi with extra bar across bottom */
      } else if (ch == '_') {
        glyph = 75; /* bottom triangle symbol? */
      } else if (ch == 'Π') {
        glyph = 76;
      } else if (ch == '∑') {
        glyph = 77;
      }
      Translated[i] = (byte) glyph;
    }
    for (int i = Chars.length() + 1; i < Translated.length; ++i) {
      Translated[i] = 0;
    }
  }

  String BackToText
     (
        byte[] PrintReg
     ) {
    StringBuilder Result = new StringBuilder();
    for (final int b : PrintReg) {
      int ch = 0;
      if (b == 0) {
        ch = ' ';
      } else if (b >= 1 && b <= 7) /* '0' .. '6' */ {
        ch = (char) (47 + b);
      } else if (b >= 10 && b <= 12) /* '7' .. '9' */ {
        ch = (char) (45 + b);
      } else if (b >= 13 && b <= 17) /* 'A' .. 'E' */ {
        ch = (char) (52 + b);
      } else if (b == 20) /* '-' */ {
        ch = '-';
      } else if (b >= 21 && b <= 27) /* 'F' .. 'L' */ {
        ch = (char) (49 + b);
      } else if (b >= 30 && b <= 37) /* 'M' .. 'T' */ {
        ch = (char) (47 + b);
      } else if (b == 40) /* '.' */ {
        ch = '.';
      } else if (b >= 41 && b <= 46) /* 'U' .. 'Z' */ {
        ch = (char) (44 + b);
      } else if (b == 47) /* '+' */ {
        ch = '+';
      } else if (b == 50) /* '×' */ {
        ch = '×';
      } else if (b == 51) /* '*' */ {
        ch = '*';
      } else if (b == 52) /* '√' */ {
        ch = '√';
      } else if (b == 53) /* 'π' */ {
        ch = 'π';
      } else if (b == 54) /* 'e' */ {
        ch = 'e';
      } else if (b == 55) /* '(' */ {
        ch = '(';
      } else if (b == 56) /* ')' */ {
        ch = ')';
      } else if (b == 57) /* ',' */ {
        ch = ',';
      } else if (b == 60) /* '↑' */ {
        ch = '↑';
      } else if (b == 61) /* '%' */ {
        ch = '%';
      } else if (b == 62) /* double arrow */ {
        ch = '⇌';
      } else if (b == 63) /* '/' */ {
        ch = '/';
      } else if (b == 64) /* '=' */ {
        ch = '=';
      } else if (b == 65) /* '\'' */ {
        ch = '\'';
      } else if (b == 66) {
        ch = 'ⓧ'; /* standing in for superscript x */
      } else if (b == 67) {
        ch = 'ẍ'; /* closest I can get in Unicode to an x with a bar over it */
      } else if (b == 70) /* '²' */ {
        ch = '²';
      } else if (b == 71) /* '?' */ {
        ch = '?';
      } else if (b == 72) /* '÷' */ {
        ch = '÷';
      } else if (b == 73) /* '!'? */ {
        ch = '!'; /*?*/
      } else if (b == 74) /* looks like pi with extra bar across bottom */ {
        ch = '♊'; /* Gemini! */
      } else if (b == 75) /* bottom triangle symbol? */ {
        ch = '_';
      } else if (b == 76) /* 'Π' */ {
        ch = 'Π';
      } else if (b == 77) /* '∑' */ {
        ch = '∑';
      }
      if (ch != 0) {
        Result.appendCodePoint(ch);
      }
    }
    return Result.toString();
  }
}
