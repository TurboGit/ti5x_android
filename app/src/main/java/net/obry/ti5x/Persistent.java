/*
    Saving/loading of programs, program libraries and calculator state

    Copyright 2011 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015 Pascal Obry <pascal@obry.net>.

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

import java.util.zip.ZipEntry;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.io.File;

class ZipComponentWriter
  /* convenient writing of components to a ZIP archive, with automatic
     calculation of size and CRC fields. */ {
  final private java.util.zip.ZipOutputStream Parent;
  final private ZipEntry Entry;
  java.io.ByteArrayOutputStream Out;

  ZipComponentWriter
     (
        java.util.zip.ZipOutputStream Parent,
        String Name,
        boolean Compressed
     ) {
    this.Parent = Parent;
    Entry = new ZipEntry(Name);
    Entry.setMethod
       (
          Compressed ?
             ZipEntry.DEFLATED
             :
             ZipEntry.STORED
       );
    Out = new java.io.ByteArrayOutputStream();
  }

  void write
     (
        byte[] buffer
     ) {
    Out.write(buffer, 0, buffer.length);
  }

  void write
     (
        String data
     ) {
    write(data.getBytes());
  }

  void close()
     throws java.io.IOException {
     /* finalizes output of this archive component. */
    Out.close();
    final byte[] TheData = Out.toByteArray();
    Entry.setSize(TheData.length);
    final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(TheData);
    Entry.setCrc(crc.getValue());
    Parent.putNextEntry(Entry);
    Parent.write(TheData, 0, TheData.length);
    Parent.closeEntry();
  }
}

public class Persistent {
  /* saving/loading of libraries and calculator state. */
  static final String CalcMimeType = "application/vnd.nz.gen.geek_central.ti5x";
  private static final String StateExt = ".ti5s"; /* for saved calculator state */
  static final String ProgExt = ".ti5p"; /* for saved user program */
  static final String LibExt = ".ti5l"; /* for library */
  static final String DataExt = ".dat"; /* for data export */
  static final String ProgramsDir = "Programs"; /* where to save user programs */
  static final String DataDir = "Download"; /* where to save exported data */
  static final String BankDir = "Banks"; /* where to save bank files */
  static final String StateDir = "State"; /* where to save state files */
  static final String[] ExternalCalcDirectories =
      /* where to load programs/libraries from */
     {
        ProgramsDir,
        DataDir,
     };
  static final String[] ExternalDataDirectories =
      /* where to load data files from */
     {
        DataDir,
     };
  static final String[] LikelyDataExts =
      /* most likely extensions for import data files */
     {
        ".dat",
        ".txt",
     };
  static final String SavedStateName = "state" + StateExt;
  /* for saved state other than loaded library */
  static final String SavedLibName = "module" + LibExt;
  /* loaded library saved separately so it doesn't have to be re-saved
     every time state changes */

  static class DataFormatException extends RuntimeException {
    /* indicates a problem parsing a saved state file. */

    DataFormatException
       (
          String Message
       ) {
      super(Message);
    }
  }
  public static String EnsureDirExists
     (
        android.content.Context ctx,
        String DirName,
        String FileName
     )
  {
    final String Dir =
        new java.io.File(ctx.getExternalFilesDir(null), DirName)
            .getAbsolutePath();
    new java.io.File(Dir).mkdirs();

    return Dir + (FileName == "" ? "" : "/" + FileName);
  }

  private static void SaveBool
     (
        java.io.PrintStream POut,
        String Name,
        boolean Value,
        int Indent
     ) {
    POut.printf(Global.StdLocale, String.format(Global.StdLocale, "%%%ds", Indent), " ");
    POut.printf(Global.StdLocale, "<param name=\"%s\" value=\"%s\"/>\n", Name, Value ? "1" : "0");
  }

  private static void SaveInt
     (
        java.io.PrintStream POut,
        String Name,
        int Value,
        int Indent
     ) {
    POut.printf(Global.StdLocale, String.format(Global.StdLocale, "%%%ds", Indent), " ");
    POut.printf(Global.StdLocale, "<param name=\"%s\" value=\"%d\"/>\n", Name, Value);
  }

  private static void SaveNumber
     (
        java.io.PrintStream POut,
        String Name,
        Number Value,
        int Indent
     ) {
    POut.printf(Global.StdLocale, String.format(Global.StdLocale, "%%%ds", Indent), " ");
    POut.printf
       ("<param name=\"%s\" value=\""
          + Value.formatString(Global.StdLocale, Global.NrSigFigures)
          + "\"/>\n", Name);
  }

  static boolean GetBool
     (
        String Value
     ) {
    boolean Result;
    int IntValue;
    try {
      IntValue = Integer.parseInt(Value);
    } catch (NumberFormatException Bad) {
      throw new DataFormatException(String.format(Global.StdLocale, "bad boolean value \"%s\"", Value));
    }
    if (IntValue == 0) {
      Result = false;
    } else if (IntValue == 1) {
      Result = true;
    } else {
      throw new DataFormatException(String.format(Global.StdLocale, "bad boolean value %d", IntValue));
    }
    return Result;
  }

  static int GetInt
     (
        String Value
     ) {
    int Result;
    try {
      Result = Integer.parseInt(Value);
    } catch (NumberFormatException Bad) {
      throw new DataFormatException(String.format(Global.StdLocale, "bad integer value \"%s\"", Value));
    }
    return Result;
  }

  static double GetDouble
     (
        String Value
     ) {
    double Result;
    try {
      Result = Double.parseDouble(Value);
    } catch (NumberFormatException Bad) {
      throw new DataFormatException(String.format(Global.StdLocale, "bad double value \"%s\"", Value));
    }
    return Result;
  }

  // read string content expected to have a set of values. This is designed to read
  // progs & memories as saved into an XML file.
  private static ArrayList<Double> parseNumbers(String content)
  {
    ArrayList<Double> opCodes = new ArrayList<Double>();
    StringReader In = new StringReader(content);
    StreamTokenizer sTokenizer = new StreamTokenizer(In);

    try {
      while(sTokenizer.nextToken() != StreamTokenizer.TT_EOF) {
        if(sTokenizer.ttype == StreamTokenizer.TT_NUMBER) {
          opCodes.add(new Double(sTokenizer.nval));
        }
      }
    }
    catch (Exception e) {
      e.getStackTrace();
    }

    return opCodes;
  }

  public static void LoadBankFile
      (
          android.content.Context ctx,
          int N,
          int CardId,
          String FileName,
          State Calc,
          Display Disp,
          ButtonGrid Buttons
      ) {

    // create BankDir
    String ToFile = EnsureDirExists(ctx, BankDir, FileName);

    if(!new File(ToFile).isFile()) {
      Calc.SetErrorState(true);
      return;
    }

    // open file
    java.io.FileInputStream In;
    try {
      In = new java.io.FileInputStream(ToFile);
      javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse
            (
                In,
                new CalcStateLoader(Disp, Buttons, Calc, N, CardId,false)
            );
      } catch (javax.xml.parsers.ParserConfigurationException Bug) {
        throw new RuntimeException("SAX parser error: " + Bug.toString());
      } catch (org.xml.sax.SAXException Bad) {
        throw new DataFormatException("SAX parser error: " + Bad.toString());
      } catch (java.io.FileNotFoundException Failed) {
        throw new RuntimeException
          (
              "ti5x.Persistent.LoadBankFile open error " + Failed.toString()
          );
      }  catch (java.io.IOException Failed) {
        throw new RuntimeException
          (
            "ti5x.Persistent.LoadBankFile open error " + Failed.toString()
          );
      }
  }

  public static void SaveBankFile
      (
          android.content.Context ctx,
          int N,
          int Id,
          String FileName,
          State Calc
      ) {

    // create BankDir
    String ToFile = EnsureDirExists(ctx, BankDir, FileName);

    java.io.FileOutputStream Out;
    try {
      Out = new java.io.FileOutputStream(ToFile);
    } catch (java.io.FileNotFoundException Failed) {
      throw new RuntimeException
          (
              "ti5x.Persistent.SaveBankFile create error " + Failed.toString()
          );
    }

    java.io.PrintStream POut = new java.io.PrintStream(Out);
    POut.println("<state>");
    POut.println("    <calc>");
    // save now
    SaveBank(N, Id, Calc, Calc.Program, POut, 8);

    POut.println("    </calc>");
    POut.println("</state>");

    try {
      POut.flush();
      Out.close();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException("ti5x.Persistent.SaveBankFile error " + Failed.toString());
    }
  }

  private static void SaveBank
          (
                  int N, /* bank number 1 to 4 */
                  int Id,
                  State Calc,
                  byte[] Program,
                  java.io.PrintStream POut,
                  int Indent
          ) {

    POut.print(String.format(Global.StdLocale,
        String.format(Global.StdLocale,
            "%%%ds<bankprog n='%01d' id='%03d'>\n", Indent, N, Id), ""));
    int Cols = 0;

    for (int k = (N - 1) * 240; k < N * 240; k++) {
      if (Cols == 24) {
        POut.print("\n");
        Cols = 0;
      }
      if (Cols != 0) {
        POut.print(" ");
      } else {
        POut.print
          (
            String.format
              (
                 Global.StdLocale,
                 String.format(Global.StdLocale, "%%%ds", Indent + 4),
                ""
              )
          );
      }
      POut.printf(Global.StdLocale, "%02d", Program[k]);
      Cols++;
    }
    POut.print
      (
        String.format(Global.StdLocale,
            String.format(Global.StdLocale,
                "\n%%%ds</bankprog>\n", Indent), "")
      );

    POut.print(String.format(Global.StdLocale, String.format(Global.StdLocale, "%%%ds<bankmem n='%01d' id='%03d'>\n", Indent, N, Id), ""));

    for (int k = (4 - N) * 30; k < (4 - N + 1) * 30; k++) {
      if (k < Calc.MaxMemories) {
        POut.printf("            %s\n", Calc.Memory[k].formatString(Global.StdLocale, 16));
      }
    }

    POut.print
      (
        String.format(Global.StdLocale, String.format(Global.StdLocale, "%%%ds</bankmem>\n", Indent, N), "")
      );
  }

  private static void SaveProg
     (
        byte[] Program,
        java.io.PrintStream POut,
        int Indent
     ) {
    /* outputs a <prog> section to POut containing the contents of Program. Trailing
       zeroes are omitted. */
    POut.print(String.format(Global.StdLocale, String.format(Global.StdLocale, "%%%ds<prog>\n", Indent), ""));
    int Cols = 0;
    int i = 0;
    int LastNonzero = -1;
    for (; ; ) {
      if (i == Program.length) {
        if (Cols != 0) {
          POut.println();
        }
        break;
      }
      if (Program[i] != 0) {
        for (int j = LastNonzero + 1; j <= i; ++j) {
          if (Cols == 24) {
            POut.print("\n");
            Cols = 0;
          }
          if (Cols != 0) {
            POut.print(" ");
          } else {
            POut.print
               (
                  String.format
                     (
                        Global.StdLocale,
                        String.format(Global.StdLocale, "%%%ds", Indent + 4),
                        ""
                     )
               );
          }
          POut.printf(Global.StdLocale, "%02d", Program[j]);
          ++Cols;
        }
        LastNonzero = i;
      }
      ++i;
    }
    POut.print
       (
          String.format(Global.StdLocale, String.format(Global.StdLocale, "%%%ds</prog>\n", Indent), "")
       );
  }

  /* Note Importer and Exporter states are NOT saved/restored */

  private static void Save
     (
        ButtonGrid Buttons, /* ignored unless CalcState */
        State Calc,
        boolean Libs, /* save currently-loaded library */
        boolean CalcState,
        /* true to save entire calculator state (apart from library),
           false to only save user-entered program */
        java.io.OutputStream RawOut
     ) {
    try {
      java.util.zip.ZipOutputStream Out = new java.util.zip.ZipOutputStream(RawOut);
      {
        /* Follow ODF convention of an uncompressed "mimetype" entry at known offset
           to allow magic-number sniffing. Must be first. */
        final ZipComponentWriter MimeType = new ZipComponentWriter(Out, "mimetype", false);
        MimeType.write(CalcMimeType);
        MimeType.close();
      }
      if (Libs && Calc.ModuleHelp != null) {
        final ZipComponentWriter LibHelp = new ZipComponentWriter(Out, "help", true);
        LibHelp.write(Calc.ModuleHelp);
        LibHelp.close();
      }
      for (int BankNr = 0; ; ) {
        if (BankNr == Calc.MaxBanks)
          break;
        if (BankNr != 0 ? Libs && Calc.Bank[BankNr] != null : !Libs || CalcState) {
          final State.ProgBank Bank = Calc.Bank[BankNr];
          if (Bank.Card != null) {
            final ZipComponentWriter CardOut =
               new ZipComponentWriter
                  (
                     Out,
                     String.format(Global.StdLocale, "card%02d", BankNr),
                     true
                  );
            Bank.Card.compress
               (
                  android.graphics.Bitmap.CompressFormat.PNG,
                              /* good enough, won't be large */
                  100, /* ignored */
                  CardOut.Out
               );
            CardOut.close();
          }
          if (BankNr != 0 && Bank.Program != null)
                      /* bank 0 program written out later */ {
            final ZipComponentWriter ProgOut =
               new ZipComponentWriter
                  (
                     Out,
                     String.format(Global.StdLocale, "prog%02d", BankNr),
                     true
                  );
            java.io.PrintStream POut = new java.io.PrintStream(ProgOut.Out);
            POut.println("<state>");
            POut.println("    <calc>");
            SaveProg(Bank.Program, POut, 8);
            POut.println("    </calc>");
            POut.println("</state>");
            POut.flush();
            ProgOut.close();
          }
          if (Bank.Help != null) {
            final ZipComponentWriter BankHelp = new ZipComponentWriter
               (
                  Out,
                  String.format(Global.StdLocale, "help%02d", BankNr),
                  true
               );
            BankHelp.write(Bank.Help);
            BankHelp.close();
          }
        }
        if (!Libs) /* only bank 0 for programs */
          break;
        ++BankNr;
      }
      if (CalcState || !Libs) {
        final ZipComponentWriter StateOut = new ZipComponentWriter(Out, "prog00", true);
        java.io.PrintStream POut = new java.io.PrintStream(StateOut.Out);
        POut.println("<state>");
        if (CalcState) {
          POut.println("    <buttons>");
          SaveBool(POut, "alt", Buttons.AltState, 8);
          SaveBool(POut, "overlay", Buttons.OverlayVisible, 8);
          SaveInt(POut, "selected_button", Buttons.SelectedButton, 8);
          SaveInt(POut, "digits_needed", Buttons.DigitsNeeded, 8);
          SaveBool(POut, "accept_symbolic", Buttons.AcceptSymbolic, 8);
          SaveBool(POut, "accept_ind", Buttons.AcceptInd, 8);
          SaveBool(POut, "next_literal", Buttons.NextLiteral, 8);
          SaveInt(POut, "accum_digits", Buttons.AccumDigits, 8);
          SaveInt(POut, "first_operand", Buttons.FirstOperand, 8);
          SaveBool(POut, "got_first_operand", Buttons.GotFirstOperand, 8);
          SaveBool(POut, "got_first_ind", Buttons.GotFirstInd, 8);
          SaveBool(POut, "is_symbolic", Buttons.IsSymbolic, 8);
          SaveBool(POut, "got_ind", Buttons.GotInd, 8);
          SaveInt(POut, "collecting_for_function", Buttons.CollectingForFunction, 8);
          POut.println("    </buttons>");
        }
        POut.println("    <calc>");
        if (CalcState) {
          {
            String StateName;
            switch (Calc.CurState) {
              case State.EntryState:
                StateName = "entry";
                break;
              case State.DecimalEntryState:
                StateName = "decimal_entry";
                break;
              case State.ExponentEntryState:
                StateName = "exponent_entry";
                break;
              case State.ResultState:
                StateName = "result";
                break;
              default:
                throw new RuntimeException
                   (
                      String.format
                         (
                            Global.StdLocale,
                            "unrecognized Calc.CurState = %d",
                            Calc.CurState
                         )
                   );
            }
            POut.printf
               (
                  Global.StdLocale,
                  "        <param name=\"state\" value=\"%s\"/>\n",
                  StateName
               );
          }
          SaveBool(POut, "exponent_entered", Calc.ExponentEntered, 8);
          SaveBool(POut, "error_state", State.inError, 8);
          if (Calc.CurState != State.ResultState) {
            POut.printf
               (
                  Global.StdLocale,
                  "        <param name=\"display\" value=\"%s\"/>\n",
                  Calc.CurDisplay
               );
          }
          SaveBool(POut, "inv", Calc.InvState, 8);
          {
            String FmtName;
            switch (Calc.CurFormat) {
              case State.FORMAT_FIXED:
                FmtName = "fixed";
                break;
              case State.FORMAT_FLOAT:
                FmtName = "float";
                break;
              case State.FORMAT_ENG:
                FmtName = "eng";
                break;
              default:
                throw new RuntimeException
                   (
                      String.format(Global.StdLocale, "unrecognized Calc.CurFormat = %d", Calc.CurFormat)
                   );
            }
            POut.printf(Global.StdLocale, "        <param name=\"format\" value=\"%s\"/>\n", FmtName);
          }
          SaveInt(POut, "nr_decimals", Calc.CurNrDecimals, 8);
          {
            String Name;
            switch (Calc.CurAng) {
              case State.ANG_RAD:
                Name = "radians";
                break;
              case State.ANG_DEG:
                Name = "degrees";
                break;
              case State.ANG_GRAD:
                Name = "gradians";
                break;
              default:
                throw new RuntimeException
                   (
                      String.format(Global.StdLocale, "unrecognized Calc.Ang = %d", Calc.CurAng)
                   );
            }
            POut.printf
               (
                  Global.StdLocale,
                  "        <param name=\"angle_units\" value=\"%s\"/>\n",
                  Name
               );
          }
          POut.println("        <opstack>");
          for (int i = 0; i < Calc.OpStackNext; ++i) {
            final State.OpStackEntry Op = Calc.OpStack[i];
            String OpName;
            switch (Op.Operator) {
              case State.STACKOP_ADD:
                OpName = "add";
                break;
              case State.STACKOP_SUB:
                OpName = "sub";
                break;
              case State.STACKOP_MUL:
                OpName = "mul";
                break;
              case State.STACKOP_DIV:
                OpName = "div";
                break;
              case State.STACKOP_MOD:
                OpName = "mod";
                break;
              case State.STACKOP_EXP:
                OpName = "exp";
                break;
              case State.STACKOP_ROOT:
                OpName = "root";
                break;
              default:
                throw new RuntimeException
                   (
                      String.format(Global.StdLocale, "unrecognized stacked op %d at pos %d", Op.Operator, i)
                   );
            }
            POut.printf
               (
                  Global.StdLocale,
                  "            <op name=\"%s\" opnd=\"%.16e\" parens=\"%d\"/>\n",
                  OpName,
                  Op.Operand.get(),
                  Op.ParenFollows
               );
          }
          POut.println("        </opstack>");
          SaveNumber(POut, "X", Calc.X, 8);
          SaveNumber(POut, "T", Calc.T, 8);
          SaveBool(POut, "learn_mode", Calc.ProgMode, 8);
          POut.println("        <mem>");
          for (int i = 0; i < Calc.Memory.length; ++i) {
            POut.printf("            %s\n", Calc.Memory[i].formatString(Global.StdLocale, 16));
          }
          POut.println("        </mem>");
          POut.print("        <feedback kind=\"");
          switch (Buttons.FeedbackType) {
            default:
            case ButtonGrid.FEEDBACK_CLICK:
              POut.print("click");
              break;
            case ButtonGrid.FEEDBACK_VIBRATE:
              POut.print("vibrate");
              break;
            case ButtonGrid.FEEDBACK_BOTH:
              POut.print("both");
              break;
            case ButtonGrid.FEEDBACK_NONE:
              POut.print("none");
              break;
          }
          POut.println("\"/>");
        }

        SaveProg(Calc.Program, POut, 8);

        for (int k = 1; k <= Calc.BankNumber; k++)
        {
          if (Calc.CardBankUsed[k-1])
          {
            SaveBank(k, 0, Calc, Calc.CardProgram, POut,8);
          }
        }

        if (CalcState) {
          POut.print("        <flags>\n            ");
          for (int i = 0; i < Calc.Flag.length; ++i) {
            if (i != 0) {
              POut.print(" ");
            }
            POut.print(Calc.Flag[i] ? "1" : "0");
          }
          POut.print("\n        </flags>\n");
          SaveInt(POut, "PC", Calc.PC, 8);
          SaveInt(POut, "bank", Calc.CurBank, 8);
          SaveInt(POut, "regoffset", Calc.RegOffset, 8);
          POut.println("        <retstack>");
          for (int i = 0; i <= Calc.ReturnLast; ++i) {
            final State.ReturnStackEntry Ret = Calc.ReturnStack[i];
            POut.printf
               (
                  "            <ret bank=\"%d\" addr=\"%d\" from_interactive=\"%s\"/>\n",
                  Ret.BankNr,
                  Ret.Addr,
                  Ret.FromInteractive ? "1" : "0"
               );
          }
          POut.println("        </retstack>");
          POut.print("        <printreg>\n            ");
          for (int i = 0; i < Calc.PrintRegister.length; ++i) {
            if (i != 0) {
              POut.print(" ");
            }
            POut.printf("%d", Calc.PrintRegister[i]);
          }
          POut.println("\n        </printreg>");
        }
        POut.println("    </calc>");

        //  Save printer status
        POut.println("    <printer>");
        int LineStart = 0;
        final int LineEnd = Global.Print.Content.size();
        if(LineEnd > Global.MaxPrinterLineSaved) {
          LineStart = LineEnd - Global.MaxPrinterLineSaved;
        }
        for(int i = LineStart; i < LineEnd; i++) {
          final byte[] line = Global.Print.Content.get(i);
          POut.printf("      <line>%s</line>", Global.Print.BackToText(line));
        }
        POut.println("    </printer>");

        POut.println("</state>");
        POut.flush();
        StateOut.close();
      }
      Out.finish();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException("ti5x.Persistent.Save error " + Failed.toString());
    }
  }

  static void Save
     (
        ButtonGrid Buttons, /* ignored unless CalcState */
        State Calc,
        boolean Libs, /* save currently-loaded library */
        boolean CalcState,
          /* true to save entire calculator state (apart from library),
             false to only save program */
        String ToFile
     ) {
    java.io.FileOutputStream Out;
    try {
      Out = new java.io.FileOutputStream(ToFile);
    } catch (java.io.FileNotFoundException Failed) {
      throw new RuntimeException
         (
            "ti5x.Persistent.Save create error " + Failed.toString()
         );
    }
    Save(Buttons, Calc, Libs, CalcState, Out);
    try {
      Out.flush();
      Out.close();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException("ti5x.Persistent.Save error " + Failed.toString());
    }
  }

  static byte[] ReadAll
     (
        java.io.InputStream From
     )
     throws java.io.IOException {
     /* reads all available data from From. */
    java.io.ByteArrayOutputStream Result = new java.io.ByteArrayOutputStream();
    final byte[] Buf = new byte[256]; /* just to reduce number of I/O operations */
    for (; ; ) {
      final int BytesRead = From.read(Buf);
      if (BytesRead < 0)
        break;
      Result.write(Buf, 0, BytesRead);
    }
    return Result.toByteArray();
  }

  public static class CalcStateLoader extends org.xml.sax.helpers.DefaultHandler {
    Display Disp;
    ButtonGrid Buttons;
    protected State Calc;
    int BankNr = -1;
    boolean CalcState;
    int CardBankNr = -1;
    int CardId = -1;
    int UserCardId = -1;

    private final int AtTopLevel = 0;
    private final int DoingState = 1;
    private final int DoingButtons = 2;
    private final int DoingCalc = 3;
    private final int DoingOpStack = 10;
    private final int DoingMem = 11;
    private final int DoingProg = 12;
    private final int DoingFlags = 13;
    private final int DoingRetStack = 14;
    private final int DoingPrintReg = 15;
    private final int DoingEmptyTag = 19;
    private final int DoingBankProg = 20;
    private final int DoingBankMem = 21;
    private final int DoingPrinter = 22;
    private int ParseState = AtTopLevel;
    private boolean DoneState = false;
    private boolean AllowContent;
    java.io.ByteArrayOutputStream Content = null;

    CalcStateLoader
       (
          Display Disp,
          ButtonGrid Buttons,
          State Calc,
          int BankNr,
          int UserCardId,
          boolean CalcState
       ) {
      super();
      this.Disp = Disp;
      this.Buttons = Buttons;
      this.Calc = Calc;
      this.BankNr = BankNr;
      this.CalcState = CalcState;
      this.UserCardId = UserCardId;
    } /*CalcStateLoader*/

    private void StartContent() {
      Content = new java.io.ByteArrayOutputStream();
      AllowContent = true;
    }

    @Override
    public void startElement
       (
          String uri,
          String localName,
          String qName,
          org.xml.sax.Attributes attributes
       ) {
      localName = localName.intern();
      boolean Handled = false;
      AllowContent = false; /* to begin with */
      if (ParseState == AtTopLevel) {
        if (localName.equals("state") && !DoneState) {
          ParseState = DoingState;
          Handled = true;
        }
      } else if (ParseState == DoingState) {
        if (CalcState && localName.equals("buttons")) {
          ParseState = DoingButtons;
          Handled = true;
        } else if (localName.equals("calc")) {
          ParseState = DoingCalc;
          Handled = true;
        } else if (localName.equals("printer")) {
          ParseState = DoingPrinter;
          Handled = true;
        }
      } else if (ParseState == DoingButtons) {
        if (localName.equals("param")) {
          final String Name = attributes.getValue("name").intern();
          final String Value = attributes.getValue("value");
          if (Name.equals("alt")) {
            Buttons.AltState = GetBool(Value);
          } else if (Name.equals("overlay")) {
            Buttons.OverlayVisible = GetBool(Value);
          } else if (Name.equals("selected_button")) {
            Buttons.SelectedButton = GetInt(Value);
          } else if (Name.equals("digits_needed")) {
            Buttons.DigitsNeeded = GetInt(Value);
          } else if (Name.equals("accept_symbolic")) {
            Buttons.AcceptSymbolic = GetBool(Value);
          } else if (Name.equals("accept_ind")) {
            Buttons.AcceptInd = GetBool(Value);
          } else if (Name.equals("next_literal")) {
            Buttons.NextLiteral = GetBool(Value);
          } else if (Name.equals("accum_digits")) {
            Buttons.AccumDigits = GetInt(Value);
          } else if (Name.equals("first_operand")) {
            Buttons.FirstOperand = GetInt(Value);
          } else if (Name.equals("got_first_operand")) {
            Buttons.GotFirstOperand = GetBool(Value);
          } else if (Name.equals("got_first_ind")) {
            Buttons.GotFirstInd = GetBool(Value);
          } else if (Name.equals("is_symbolic")) {
            Buttons.IsSymbolic = GetBool(Value);
          } else if (Name.equals("got_ind")) {
            Buttons.GotInd = GetBool(Value);
          } else if (Name.equals("collecting_for_function")) {
            Buttons.CollectingForFunction = GetInt(Value);
          } else {
            throw new DataFormatException
               (
                  String.format(Global.StdLocale, "unrecognized <buttons> param \"%s\"", Name)
               );
          }
          Handled = true;
        }
      } else if (ParseState == DoingCalc) {
        if (CalcState && localName.equals("param")) {
          final String Name = attributes.getValue("name").intern();
          final String Value = attributes.getValue("value").intern();
          if (Name.equals("state")) {
            if (Value.equals("entry")) {
              Calc.CurState = State.EntryState;
            } else if (Value.equals("decimal_entry")) {
              Calc.CurState = State.DecimalEntryState;
            } else if (Value.equals("exponent_entry")) {
              Calc.CurState = State.ExponentEntryState;
            } else if (Value.equals("result")) {
              Calc.CurState = State.ResultState;
            } else if (Value.equals("error")) {
              // for compatibility with previous version
              State.inError = true;
            } else {
              throw new RuntimeException
                 (
                    String.format(Global.StdLocale, "unrecognized calc state \"%s\"", Value)
                 );
            }
          } else if (Name.equals("exponent_entered")) {
            Calc.ExponentEntered = GetBool(Value);
          } else if (Name.equals("error_state")) {
            State.inError = GetBool(Value);
          } else if (Name.equals("display")) {
            Calc.CurDisplay = Value;
          } else if (Name.equals("inv")) {
            Calc.InvState = GetBool(Value);
          } else if (Name.equals("format")) {
            if (Value.equals("fixed")) {
              Calc.CurFormat = State.FORMAT_FIXED;
            } else if (Value.equals("float")) {
              Calc.CurFormat = State.FORMAT_FLOAT;
            } else if (Value.equals("eng")) {
              Calc.CurFormat = State.FORMAT_ENG;
            } else {
              throw new RuntimeException
                 (
                    String.format(Global.StdLocale, "unrecognized calc format \"%s\"", Value)
                 );
            }
          } else if (Name.equals("nr_decimals")) {
            Calc.CurNrDecimals = GetInt(Value);
          } else if (Name.equals("angle_units")) {
            if (Value.equals("radians")) {
              Calc.CurAng = State.ANG_RAD;
            } else if (Value.equals("degrees")) {
              Calc.CurAng = State.ANG_DEG;
            } else if (Value.equals("gradians")) {
              Calc.CurAng = State.ANG_GRAD;
            } else {
              throw new RuntimeException
                 (
                    String.format(Global.StdLocale, "unrecognized calc angle_units \"%s\"", Value)
                 );
            }
          } else if (Name.equals("X")) {
            Calc.X = new Number(Value);
          } else if (Name.equals("T")) {
            Calc.T = new Number(Value);
          } else if (Name.equals("learn_mode")) {
            Calc.ProgMode = GetBool(Value);
          } else if (Name.equals("PC")) {
            Calc.PC = GetInt(Value);
          } else if (Name.equals("bank")) {
            Calc.CurBank = GetInt(Value);
          } else if (Name.equals("regoffset")) {
            Calc.RegOffset = GetInt(Value);
          } else {
            throw new DataFormatException
               (
                  String.format(Global.StdLocale, "unrecognized <calc> param \"%s\"", Name)
               );
          }
          Handled = true;
        } else if (CalcState && localName.equals("opstack")) {
          Calc.OpStackNext = 0;
          ParseState = DoingOpStack;
          Handled = true;
        } else if (CalcState && localName.equals("mem")) {
          ParseState = DoingMem;
          StartContent();
          Handled = true;
        } else if (localName.equals("bankmem")) {
          ParseState = DoingBankMem;
          CardBankNr = Integer.parseInt(attributes.getValue("n").intern());
          CardId = Integer.parseInt(attributes.getValue("id").intern());
          StartContent();
          Handled = true;
        } else if (localName.equals("feedback")) {
          final String Kind = attributes.getValue("kind").intern();
          if (Kind.equals("none")) {
            Buttons.FeedbackType = ButtonGrid.FEEDBACK_NONE;
          } else if (Kind.equals("vibrate")) {
            Buttons.FeedbackType = ButtonGrid.FEEDBACK_VIBRATE;
          } else if (Kind.equals("both")) {
            Buttons.FeedbackType = ButtonGrid.FEEDBACK_BOTH;
          } else {
            Buttons.FeedbackType = ButtonGrid.FEEDBACK_CLICK;
          }
          ParseState = DoingEmptyTag;
          Handled = true;
        } else if (localName.equals("prog")) /* only one allowed if not CalcState */ {
          ParseState = DoingProg;
          StartContent();
          Handled = true;
        } else if (localName.equals("bankprog")) {
          ParseState = DoingBankProg;
          CardBankNr = Integer.parseInt(attributes.getValue("n").intern());
          CardId = Integer.parseInt(attributes.getValue("id").intern());
          StartContent();
          Handled = true;
        } else if (CalcState && localName.equals("flags")) {
          ParseState = DoingFlags;
          StartContent();
          Handled = true;
        } else if (CalcState && localName.equals("retstack")) {
          Calc.ReturnLast = -1;
          ParseState = DoingRetStack;
          Handled = true;
        } else if (CalcState && localName.equals("printreg")) {
          ParseState = DoingPrintReg;
          StartContent();
          Handled = true;
        }
      } else if (ParseState == DoingOpStack) {
        if (localName.equals("op")) {
          final String OpName = attributes.getValue("name").intern();
          int Op;
          if (OpName.equals("add")) {
            Op = State.STACKOP_ADD;
          } else if (OpName.equals("sub")) {
            Op = State.STACKOP_SUB;
          } else if (OpName.equals("mul")) {
            Op = State.STACKOP_MUL;
          } else if (OpName.equals("div")) {
            Op = State.STACKOP_DIV;
          } else if (OpName.equals("mod")) {
            Op = State.STACKOP_MOD;
          } else if (OpName.equals("exp")) {
            Op = State.STACKOP_EXP;
          } else if (OpName.equals("root")) {
            Op = State.STACKOP_ROOT;
          } else {
            throw new DataFormatException
               (
                  String.format(Global.StdLocale, "unrecognized <op> operator \"%s\"", OpName)
               );
          }
          if (Calc.OpStackNext == Calc.MaxOpStack) {
            throw new DataFormatException
               (
                  String.format(Global.StdLocale, "too many <op> entries -- only %d allowed", Calc.MaxOpStack)
               );
          }
          Calc.OpStack[Calc.OpStackNext++] = new State.OpStackEntry
             (
                new Number(attributes.getValue("opnd")),
                Op,
                GetInt(attributes.getValue("parens"))
             );
          Handled = true;
        }
      } else if (ParseState == DoingRetStack) {
        if (localName.equals("ret")) {
          if (Calc.ReturnLast == Calc.MaxReturnStack - 1) {
            throw new DataFormatException
               (
                  String.format(Global.StdLocale, "too many <ret> entries -- only %d allowed", Calc.MaxReturnStack)
               );
          }
          Calc.ReturnStack[++Calc.ReturnLast] = new State.ReturnStackEntry
             (
                GetInt(attributes.getValue("bank")),
                GetInt(attributes.getValue("addr")),
                GetBool(attributes.getValue("from_interactive"))
             );
          Handled = true;
        }
      } else if (ParseState == DoingPrinter) {
        if (localName.equals ("line")) {
          StartContent();
        }
        Handled = true;
      }
      if (!Handled) {
        throw new DataFormatException("unexpected XML tag " + localName + " in state " + ParseState);
      }
    }

    @Override
    public void characters
       (
          char[] ch,
          int start,
          int length
       ) {
      if (AllowContent) {
        try {
          Content.write(new String(ch, start, length).getBytes());
        } catch (java.io.IOException Failed) {
          throw new RuntimeException("ti5x XML content parse error " + Failed.toString());
        }
      }
    }

    @Override
    public void endElement
       (
          String uri,
          String localName,
          String qName
       ) {
      localName = localName.intern();
      final String ContentStr = Content != null ? Content.toString() : null;
      final int ContentStrLen = ContentStr == null ? 0 : ContentStr.length();
      Content = null;
      AllowContent = false;
      switch (ParseState) {
        case DoingState:
          if (localName.equals("state")) {
            ParseState = AtTopLevel;
            DoneState = true;
          }
          break;
        case DoingButtons:
          if (localName.equals("buttons")) {
            ParseState = DoingState;
          }
          break;
        case DoingCalc:
          if (localName.equals("calc")) {
            ParseState = DoingState;
          }
          break;
        case DoingOpStack:
          if (localName.equals("opstack")) {
            ParseState = DoingCalc;
          }
          break;
        case DoingRetStack:
          if (localName.equals("retstack")) {
            ParseState = DoingCalc;
          }
          break;
        case DoingPrintReg:
          if (localName.equals("printreg")) {

            ArrayList<Double> r = parseNumbers(ContentStr);

            if (r.size() > Calc.PrintRegister.length) {
              throw new DataFormatException
                  (
                      String.format
                          (
                              Global.StdLocale,
                              "too many columns in print register, only %d allowed",
                              Calc.PrintRegister.length
                          ));
            }
            for (int Place = 0; Place < r.size(); ++Place) {
              Calc.PrintRegister[Place] = r.get(Place).byteValue();
            }

            ParseState = DoingCalc;
          }
          break;
        case DoingPrinter:
          if (localName.equals("line")) {
            byte[] line = new byte[Printer.CharColumns];
            Global.Print.Translate(ContentStr, line);
            Global.Print.Content.add(line);
          } else if (localName.equals("printer")) {
            ParseState = DoingCalc;
          }
        case DoingMem:
          if (localName.equals("mem")) {

            ArrayList<Double> m = parseNumbers(ContentStr);

            if (m.size() > Calc.MaxMemories) {
              throw new DataFormatException
                  (
                      String.format(Global.StdLocale, "too many memories, only %d allowed", Calc.MaxMemories)
                  );
            }
            for (int reg = 0; reg < m.size(); ++reg) {
              Calc.Memory[reg] = new Number(m.get(reg));
            }
            ParseState = DoingCalc;
          }
          break;
        case DoingBankMem:
          if (localName.equals("bankmem")) {
            ArrayList<Double> m = parseNumbers(ContentStr);

            if (m.size() > 30) {
              throw new DataFormatException
                  (
                      String.format(Global.StdLocale, "too many mem, only 30 allowed for a bank")
                  );
            }

            if (BankNr != 0 && CardBankNr != BankNr) {
              Calc.SetErrorState(true);
            } else {
              Number[] CardMem = new Number[m.size()];

              for (int addr = 0; addr < m.size(); ++addr) {
                double val = m.get(addr);
                CardMem[addr] = new Number(val);
              }
              Calc.Store.SetMem(CardId, CardBankNr, CardMem);
            }

            ParseState = DoingCalc;
          }

          break;
        case DoingProg:
          if (localName.equals("prog")) {

            if (BankNr == 0) {
              for (int i = 0; i < Calc.MaxProgramSteps; ++i) {
                Calc.Program[i] = (byte) 0;
              }
            }
            ArrayList<Double> p = parseNumbers(ContentStr);

            if (BankNr == 0 ? p.size() > Calc.MaxProgramSteps : p.size() >= Calc.MaxCardProgramSteps) {
              throw new DataFormatException
                  (
                      String.format(Global.StdLocale, "too many memories, only %d allowed", Calc.MaxMemories)
                  );
            }
            if (BankNr != 0) {
              Calc.Bank[BankNr].Program = new byte[p.size()];
            }

            for (int addr = 0; addr < p.size(); ++addr) {
              byte val = p.get(addr).byteValue();
              if (BankNr != 0) {
                Calc.Bank[BankNr].Program[addr] = val;
              } else {
                Calc.Program[addr] = val;
              }
            }
            ParseState = DoingCalc;
          }
          break;
        case DoingBankProg:
          if (localName.equals("bankprog")) {

            ArrayList<Double> p = parseNumbers(ContentStr);

            if (p.size() > 240) {
              throw new DataFormatException
                  (
                      String.format(Global.StdLocale, "too many opcodes, only 240 allowed for a bank")
                  );
            }

            if (BankNr != 0 && CardBankNr != BankNr) {
              Calc.SetErrorState(true);
            } else {
              byte[] CardProg = new byte[p.size()];

              for (int addr = 0; addr < p.size(); ++addr) {
                byte val = p.get(addr).byteValue();
                CardProg[addr] = val;
              }

              Calc.Store.SetProg(CardId, CardBankNr, CardProg);
            }

            ParseState = DoingCalc;
          }
          break;
        case DoingFlags:
          if (localName.equals("flags")) {
            int Flag = 0;
            int i = 0;
            for (; ; ) {
              for (; ; ) {
                if (i == ContentStrLen)
                  break;
                if (ContentStr.charAt(i) > ' ')
                  break;
                ++i;
              }
              final int Start = i;
              for (; ; ) {
                if (i == ContentStrLen)
                  break;
                if (ContentStr.charAt(i) <= ' ')
                  break;
                ++i;
              }
              if (i > Start) {
                if (Flag == Calc.MaxFlags) {
                  throw new DataFormatException
                     (
                        String.format(Global.StdLocale, "too many flags, only %d allowed", Calc.MaxFlags)
                     );
                }
                Calc.Flag[Flag++] = GetBool(ContentStr.substring(Start, i));
              }
              if (i == ContentStrLen)
                break;
            }
            ParseState = DoingCalc;
          }
          break;
        case DoingEmptyTag:
          ParseState = DoingCalc;
          break;
      }
    }
  }

  public static class Load extends Global.Task {
    private final String FromFile;
    private final boolean Libs;
    private final boolean CalcState;
    private final Display Disp;
    private final ButtonGrid Buttons;
    private final State Calc;

    public Load
       (
          String FromFile,
          boolean Libs, /* true to load nonzero program banks, false to load bank 0 */
          boolean CalcState, /* true to load all state (including all available program banks) */
          Display Disp,
          ButtonGrid Buttons,
          State Calc
       ) {
      this.FromFile = FromFile;
      this.Libs = Libs;
      this.CalcState = CalcState;
      this.Disp = Disp;
      this.Buttons = Buttons;
      this.Calc = Calc;
    }

    @Override
    public boolean PreRun() {
      if (CalcState) {
        Buttons.Reset();
        Calc.Reset(Libs);
      } else if (Libs) {
        Calc.ResetLibs();
      } else {
        Calc.ResetLabels();
      }
      return true;
    }

    @Override
    public void BGRun() {
      try {
        try {
          final java.util.zip.ZipFile In = new java.util.zip.ZipFile
             (
                new java.io.File(FromFile),
                java.util.zip.ZipFile.OPEN_READ
             );
          final ZipEntry MimeType = In.getEntry("mimetype");
          if (MimeType == null) {
            throw new DataFormatException
               (
                  "missing mandatory archive component: mimetype"
               );
          }
          if
             (
              !In.entries().nextElement().getName().intern().equals("mimetype")
                ||
                MimeType.getMethod() != ZipEntry.STORED
             ) {
            throw new DataFormatException("mimetype must be uncompressed and first in archive");
          }
          if (!new String(ReadAll(In.getInputStream(MimeType))).intern().equals(CalcMimeType)) {
            throw new DataFormatException("wrong MIME type");
          }
          if (Libs) {
            final ZipEntry LibHelpEntry = In.getEntry("help");
            if (LibHelpEntry != null) {
              Calc.ModuleHelp = ReadAll(In.getInputStream(LibHelpEntry));
            }
          }
          for (int BankNr = 0; ; ) {
            if (BankNr == Calc.MaxBanks)
              break;
            if (BankNr != 0 ? Libs : !Libs || CalcState) {
              final ZipEntry StateEntry =
                 In.getEntry(String.format(Global.StdLocale, "prog%02d", BankNr));
              if (StateEntry != null) {
                final ZipEntry CardEntry =
                   In.getEntry(String.format(Global.StdLocale, "card%02d", BankNr));
                final ZipEntry HelpEntry =
                   In.getEntry(String.format(Global.StdLocale, "help%02d", BankNr));
                android.graphics.Bitmap CardImage = null;
                byte[] BankHelp = null;
                if (CardEntry != null) {
                  CardImage = android.graphics.BitmapFactory.decodeStream
                     (
                        In.getInputStream(CardEntry)
                     );
                }
                if (HelpEntry != null) {
                  BankHelp = ReadAll(In.getInputStream(HelpEntry));
                }
                if (BankNr != 0) {
                  Calc.Bank[BankNr] =
                     new State.ProgBank(null /* filled in below */, CardImage, BankHelp);
                } else {
                                  /* don't overwrite Calc.Bank[0].Program */
                  Calc.Bank[0].Card = CardImage;
                  Calc.Bank[0].Help = BankHelp;
                }
                try {
                  javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse
                     (
                        In.getInputStream(StateEntry),
                        new CalcStateLoader(Disp, Buttons, Calc, BankNr, 0, CalcState)
                     );
                } catch (javax.xml.parsers.ParserConfigurationException Bug) {
                  throw new RuntimeException("SAX parser error: " + Bug.toString());
                } catch (org.xml.sax.SAXException Bad) {
                  throw new DataFormatException("SAX parser error: " + Bad.toString());
                }
              } else if (BankNr == 0) {
                throw new DataFormatException
                   (
                      "missing mandatory archive component: prog00"
                   );
              }
            }
            if (!Libs) /* only bank 0 for programs */
              break;
            ++BankNr;
          }
        } catch (java.io.IOException IOError) {
          throw new DataFormatException("I/O error: " + IOError.toString());
        }
      } catch (DataFormatException Failure) {
        SetStatus(-1, Failure);
      }
    }

    @Override
    public void PostRun() {
      if (TaskFailure == null) {
        if (Calc != null) {
          if (Calc.Bank[Calc.CurBank] == null)
            Calc.CurBank = 1;
          Calc.SelectProgram(Calc.CurBank, false);
          switch (Calc.CurState) {
            case State.ResultState:
              Calc.SetX(Calc.X, false);
              break;
          }
          if (State.inError) {
            Calc.SetX(Calc.X, false);
            Calc.SetErrorState(false);
          }
          Calc.SetProgMode(Calc.ProgMode);
        }
        if (Buttons != null) {
          if (CalcState) {
            Buttons.SetFeedbackType(Buttons.FeedbackType);
          }
          Buttons.invalidate();
        }
        // And reset status to current one
        Global.setStatusDisplay();
      }
    }
  }

  public static class LoadBuiltin extends Global.Task {
    /* loads the included Master Library module into the calculator state. */
    private final Load DoLoad;
    private final android.content.Context ctx;
    private final int SelId;
    private final boolean IsLib;
    /* Unfortunately java.util.zip.ZipFile can't read from an arbitrary InputStream,
      so I need to make a temporary copy of the master library out of my raw resources. */
    private final String TempLibName = "temp.ti5x"; /* name for temporary copy */

    LoadBuiltin
       (
          android.content.Context ctx,
          boolean IsLib,
          int Id // Id number for the built-in program to load
       ) {
      this.ctx = ctx;
      this.IsLib = IsLib;
      this.SelId = Id;
      final String TempLibFile = ctx.getFilesDir().getAbsolutePath() + "/" + TempLibName;
      DoLoad = new Load
         (
            TempLibFile,
            IsLib,
            false,
            Global.Disp,
            Global.Buttons,
            Global.Calc
         );
    }

    @Override
    public boolean PreRun() {
      return DoLoad.PreRun();
    }

    @Override
    public void BGRun() {
      BuiltinLibrary Prog = IsLib ? Main.BuiltinLibraries[SelId] : Main.BuiltinPrograms[SelId];
      try {
        final java.io.InputStream LibFile = Prog.getInputStream(ctx);
        final java.io.OutputStream TempLib =
           ctx.openFileOutput(TempLibName, android.content.Context.MODE_PRIVATE);
        {
          byte[] Buffer = new byte[2048]; /* some convenient size */
          for (; ; ) {
            final int NrBytes = LibFile.read(Buffer);
            if (NrBytes <= 0)
              break;
            TempLib.write(Buffer, 0, NrBytes);
          }
        }
        TempLib.flush();
        TempLib.close();
      } catch (java.io.FileNotFoundException Failed) {
        throw new RuntimeException("ti5x " + Prog.getName(ctx)
           + " load failed: " + Failed.toString());
      } catch (java.io.IOException Failed) {
        throw new RuntimeException("ti5x " + Prog.getName(ctx)
           + " load failed: " + Failed.toString());
      }
      DoLoad.BGRun();
      ctx.deleteFile(TempLibName);
    }

    @Override
    public void PostRun() {
      DoLoad.PostRun();
      // we have loaded a new built-in library, select its first program
      Global.Calc.SelectProgram(1, false);
    }
  }

  static void SaveState
     (
        android.content.Context ctx,
        boolean SaveLib /* true to save loaded library, false to save rest of state */
     ) {
    /* saves the current calculator state for later restoration. */
    java.io.FileOutputStream CurSave;
    try {
      final String FileName = SaveLib ? SavedLibName : SavedStateName;
      final String StateName = EnsureDirExists(ctx, StateDir, FileName);

      CurSave = new java.io.FileOutputStream(StateName);
    } catch (java.io.FileNotFoundException Eh) {
      throw new RuntimeException("ti5x save-state create error " + Eh.toString());
    }
    Save
       (
          Global.Buttons,
          Global.Calc,
          SaveLib,
          !SaveLib,
          CurSave
       );
    try {
      CurSave.flush();
      CurSave.close();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException
         (
            "ti5x state save error " + Failed.toString()
         );
    }
  }

  public static class RestoreState extends Global.Task {
    /* restores the entire calculator state, using the previously-saved
       state if available, otherwise (re)initializes to default state. */
    private final android.content.Context ctx;

    private static final int LOAD_LIB = 0;
    private static final int LOAD_STATE = 1;
    private static final int LOAD_MASTER = 2;
    private static final int SAVE_STATE = 3;
    private static final int RESTORE_DONE = 4;
    private int Restoring;

    private boolean RestoredLib;
    String StateFile = null;
    private Global.Task Subtask;

    private RestoreState
       (
          android.content.Context ctx,
          int Restoring,
          boolean RestoredLib
       ) {
      this.ctx = ctx;
      this.Restoring = Restoring;
      this.RestoredLib = RestoredLib;
      Subtask = null;
    }

    RestoreState
       (
          android.content.Context ctx
       ) {
      this(ctx, LOAD_LIB, false);
    }

    @Override
    public boolean PreRun() {
      for (; ; ) {
        switch (Restoring) {
          case LOAD_LIB:
          case LOAD_STATE:
            /* load library before rest of state, otherwise Calc.SelectProgram(Calc.CurBank)
               call (above) will trigger error on nonexistent bank */

            final boolean LoadingLib = Restoring == LOAD_LIB;
            final String FileName = LoadingLib ? SavedLibName : SavedStateName;
            final String StateFile = EnsureDirExists(ctx, StateDir, FileName);

            if (new java.io.File(StateFile).exists()) {
              Subtask = new Load
                 (
                    StateFile,
                    LoadingLib,
                    !LoadingLib,
                    Global.Disp,
                    Global.Buttons,
                    Global.Calc
                 );
            }
            break;
          case LOAD_MASTER:
            if (!RestoredLib) {
              /* initialize state to include Master Library */
              Subtask = new LoadBuiltin(ctx, true, Main.BUILTIN_MASTER_LIBRARY_INDEX);
            }
            break;
          case SAVE_STATE:
            /* save newly-initialized state */
            Subtask = new Global.Task() {
              @Override
              public void BGRun() {
                SaveState(ctx, true);
              } /*BGRun*/
            } /*Global.Task*/;
            break;
        }
        if (Subtask != null || Restoring >= LOAD_MASTER)
          break;
        ++Restoring;
      }
      return Subtask != null && Subtask.PreRun();
    }

    @Override
    public void BGRun() {
      if (Subtask != null) {
        Subtask.BGRun();
        if (Subtask.TaskFailure == null && Restoring == LOAD_LIB) {
          RestoredLib = true;
        }
      }
    }

    @Override
    public void PostRun() {
      if (Subtask != null) {
        if (Subtask.TaskFailure != null && StateFile != null) {
          System.err.printf
             (
                "ti5x failure to reload state from file \"%s\": %s\n",
                StateFile,
                Subtask.TaskFailure.toString()
             );
        }
        Subtask.PostRun();
        if (Restoring != RESTORE_DONE) {
          /* run the next stage */
          Global.StartBGTask(new RestoreState(ctx, Restoring + 1, RestoredLib), null);
        }
      }
    }
  }

  static void ResetState
     (
        android.content.Context ctx
     ) {
    /* wipes saved state. */
    ctx.deleteFile(SavedStateName);
    ctx.deleteFile(SavedLibName);
  }
}
