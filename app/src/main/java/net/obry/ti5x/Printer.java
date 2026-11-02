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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

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

  private static final Map<Character, Byte> TranslateTable = new TreeMap<>();
  private static final char[] BackToTextTable = new char[78];

  static {
    // Initialize TranslateTable using a self-balancing binary search tree (TreeMap)
    TranslateTable.put(' ', (byte) 0);
    for (char ch = '0'; ch <= '6'; ch++) TranslateTable.put(ch, (byte) (1 + ch - '0'));
    for (char ch = '7'; ch <= '9'; ch++) TranslateTable.put(ch, (byte) (10 + ch - '7'));
    for (char ch = 'A'; ch <= 'E'; ch++) TranslateTable.put(ch, (byte) (13 + ch - 'A'));
    for (char ch = 'F'; ch <= 'L'; ch++) TranslateTable.put(ch, (byte) (21 + ch - 'F'));
    for (char ch = 'M'; ch <= 'T'; ch++) TranslateTable.put(ch, (byte) (30 + ch - 'M'));
    for (char ch = 'U'; ch <= 'Z'; ch++) TranslateTable.put(ch, (byte) (41 + ch - 'U'));
    TranslateTable.put('.', (byte) 40);
    TranslateTable.put('+', (byte) 47);
    TranslateTable.put('-', (byte) 20);
    TranslateTable.put('*', (byte) 51);
    TranslateTable.put('×', (byte) 50);
    TranslateTable.put('÷', (byte) 72);
    TranslateTable.put('√', (byte) 52);
    TranslateTable.put('π', (byte) 53);
    TranslateTable.put('e', (byte) 54);
    TranslateTable.put('(', (byte) 55);
    TranslateTable.put(')', (byte) 56);
    TranslateTable.put(',', (byte) 57);
    TranslateTable.put('↑', (byte) 60);
    TranslateTable.put('%', (byte) 61);
    TranslateTable.put('⇌', (byte) 62);
    TranslateTable.put('/', (byte) 63);
    TranslateTable.put('=', (byte) 64);
    TranslateTable.put('\'', (byte) 65);
    TranslateTable.put('ⓧ', (byte) 66);
    TranslateTable.put('ẍ', (byte) 67);
    TranslateTable.put('²', (byte) 70);
    TranslateTable.put('?', (byte) 71);
    TranslateTable.put('!', (byte) 73);
    TranslateTable.put('♊', (byte) 74);
    TranslateTable.put('_', (byte) 75);
    TranslateTable.put('Π', (byte) 76);
    TranslateTable.put('∑', (byte) 77);

    // Initialize BackToTextTable
    BackToTextTable[0] = ' ';
    for (int b = 1; b <= 7; b++) BackToTextTable[b] = (char) (47 + b);
    for (int b = 10; b <= 12; b++) BackToTextTable[b] = (char) (45 + b);
    for (int b = 13; b <= 17; b++) BackToTextTable[b] = (char) (52 + b);
    BackToTextTable[20] = '-';
    for (int b = 21; b <= 27; b++) BackToTextTable[b] = (char) (49 + b);
    for (int b = 30; b <= 37; b++) BackToTextTable[b] = (char) (47 + b);
    BackToTextTable[40] = '.';
    for (int b = 41; b <= 46; b++) BackToTextTable[b] = (char) (44 + b);
    BackToTextTable[47] = '+';
    BackToTextTable[50] = '×';
    BackToTextTable[51] = '*';
    BackToTextTable[52] = '√';
    BackToTextTable[53] = 'π';
    BackToTextTable[54] = 'e';
    BackToTextTable[55] = '(';
    BackToTextTable[56] = ')';
    BackToTextTable[57] = ',';
    BackToTextTable[60] = '↑';
    BackToTextTable[61] = '%';
    BackToTextTable[62] = '⇌';
    BackToTextTable[63] = '/';
    BackToTextTable[64] = '=';
    BackToTextTable[65] = '\'';
    BackToTextTable[66] = 'ⓧ';
    BackToTextTable[67] = 'ẍ';
    BackToTextTable[70] = '²';
    BackToTextTable[71] = '?';
    BackToTextTable[72] = '÷';
    BackToTextTable[73] = '!';
    BackToTextTable[74] = '♊';
    BackToTextTable[75] = '_';
    BackToTextTable[76] = 'Π';
    BackToTextTable[77] = '∑';
  }

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

    OutputStream outputStream = null;
    @SuppressLint("SimpleDateFormat") /* we want a specific format without slash as separator */
        String outString = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss.SSS").format(new Date());
    String fileName = outString + ".png";

    try {
      outputStream = Global.DataStorage.getOutputStream(Persistent.PrinterDir, fileName, false);
      if (outputStream == null) {
        File file = new File(Global.DataStorage.GetAppDataPrinterFile(fileName));
        outputStream = new FileOutputStream(file);
      }
      boolean ok = Paper.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
    } catch (Exception ex) {
      //exception handling code
      ex.printStackTrace();
      mes.append("Can't Create. " + fileName);
      return false;
    } finally {
      try {
        if (outputStream != null) {
          outputStream.flush();
          outputStream.close();
          mes.append("Saved to " + fileName);
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

  void Advance(boolean ForceFullExport) {
    // advances the paper to the next line.
    Content.add(new byte[0]);

    if (ForceFullExport
        || (Global.Export != null && !Global.Export.NumbersOnly)) {
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
        String Line,
        byte[] PrintReg,
        boolean ForceFullExport
     ) {
    if ((Global.Export != null && ForceFullExport)
        || !Global.Export.NumbersOnly) {
      final String Data = Line.isEmpty() ? BackToText(PrintReg) : Line;
      Global.Export.WriteLine(Data);
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
      final Byte glyph = TranslateTable.get(ch);
      Translated[i] = (glyph != null) ? glyph : 0;
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
    for (final byte bByte : PrintReg) {
      final int b = bByte & 0xFF;
      if (b < BackToTextTable.length) {
        Result.appendCodePoint(BackToTextTable[b]);
      }
    }
    return Result.toString();
  }
}
