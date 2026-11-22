/*
    ti5x calculator emulator -- program, library and state files (ZIP + XML)

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

import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.zip.ZipEntry;

final class ZipComponentWriter
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

public final class ProgramFiles {
  /* loading and saving of programs, libraries, card banks and calculator state. */

  /* file layout, see file_format.txt */
  public static final String CalcMimeType = "application/vnd.nz.gen.geek_central.ti5x";

  private ProgramFiles() {
  }

  private static javax.xml.parsers.SAXParser NewParser() {
    /* namespace aware so that localName is set on every platform */
    try {
      final javax.xml.parsers.SAXParserFactory Factory =
         javax.xml.parsers.SAXParserFactory.newInstance();
      Factory.setNamespaceAware(true);
      return Factory.newSAXParser();
    } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException Bug) {
      throw new RuntimeException("SAX parser error: " + Bug.toString());
    }
  }

  private static void Parse
     (
        java.io.InputStream In,
        CalcStateLoader Loader
     )
     throws java.io.IOException {
    try {
      NewParser().parse(In, Loader);
    } catch (org.xml.sax.SAXException Bad) {
      throw new DataFormatException("SAX parser error: " + Bad.toString());
    }
  }

  public static void Load
     (
        State Calc,
        UiState Ui, /* can be null */
        java.io.InputStream From, /* the archive, closed on return */
        boolean Libs, /* true to load nonzero program banks, false to load bank 0 */
        boolean CalcState /* true to load all state (including all available program banks) */
     ) {
    /* loads a program (.ti5p), library (.ti5l) or saved state archive into Calc.
       The caller is responsible for resetting the state beforehand. */
    final java.util.Map<String, byte[]> Entries = new java.util.HashMap<>();
    String FirstName = null;
    int FirstMethod = -1;
    try {
      try (java.util.zip.ZipInputStream In = new java.util.zip.ZipInputStream(From)) {
        for (; ; ) {
          final ZipEntry Entry = In.getNextEntry();
          if (Entry == null)
            break;
          if (FirstName == null) {
            FirstName = Entry.getName();
            FirstMethod = Entry.getMethod();
          }
          Entries.put(Entry.getName(), ReadAll(In));
        }
      }
      if (!Entries.containsKey("mimetype")) {
        throw new DataFormatException
           (
              "missing mandatory archive component: mimetype"
           );
      }
      if (!"mimetype".equals(FirstName) || FirstMethod != ZipEntry.STORED) {
        throw new DataFormatException("mimetype must be uncompressed and first in archive");
      }
      if (!new String(Entries.get("mimetype")).equals(CalcMimeType)) {
        throw new DataFormatException("wrong MIME type");
      }
      if (Libs) {
        final byte[] LibHelp = Entries.get("help");
        if (LibHelp != null) {
          Calc.ModuleHelp = LibHelp;
        }
      }
      for (int BankNr = 0; ; ) {
        if (BankNr == Calc.MaxBanks)
          break;
        if (BankNr != 0 ? Libs : !Libs || CalcState) {
          final byte[] StateEntry =
             Entries.get(String.format(Constants.StdLocale, "prog%02d", BankNr));
          if (StateEntry != null) {
            final byte[] CardImage =
               Entries.get(String.format(Constants.StdLocale, "card%02d", BankNr));
            final byte[] BankHelp =
               Entries.get(String.format(Constants.StdLocale, "help%02d", BankNr));
            if (BankNr != 0) {
              Calc.Bank[BankNr] =
                 new State.ProgBank(null /* filled in below */, CardImage, BankHelp);
            } else {
              /* don't overwrite Calc.Bank[0].Program */
              Calc.Bank[0].Card = CardImage;
              Calc.Bank[0].Help = BankHelp;
            }
            Parse
               (
                  new java.io.ByteArrayInputStream(StateEntry),
                  new CalcStateLoader(Ui, Calc, BankNr, 0, CalcState)
               );
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
  }

  public static void LoadBank
     (
        State Calc,
        java.io.InputStream In, /* the bank file (.ti5b) */
        int BankNr, /* 1 .. 4 */
        int CardId
     ) {
    /* loads a card bank file into the card store. */
    try {
      Parse(In, new CalcStateLoader(null, Calc, BankNr, CardId, false));
    } catch (java.io.IOException Failed) {
      throw new RuntimeException("ti5x.ProgramFiles.LoadBank read error " + Failed.toString());
    }
  }

  public static void SaveBank
     (
        State Calc,
        java.io.OutputStream Out, /* the bank file (.ti5b), closed on return */
        int BankNr, /* 1 .. 4 */
        int CardId
     ) {
    /* saves the program and memories of a card bank. */
    java.io.PrintStream POut = new java.io.PrintStream(Out);
    POut.println("<state>");
    POut.println("    <calc>");
    SaveBank(BankNr, CardId, Calc, Calc.Program, POut, 8);
    POut.println("    </calc>");
    POut.println("</state>");

    try {
      POut.flush();
      Out.close();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException("ti5x.ProgramFiles.SaveBank error " + Failed.toString());
    }
  }

  private static void SaveBool
     (
        java.io.PrintStream POut,
        String Name,
        boolean Value,
        int Indent
     ) {
    POut.printf(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds", Indent), " ");
    POut.printf(Constants.StdLocale, "<param name=\"%s\" value=\"%s\"/>\n", Name, Value ? "1" : "0");
  }

  private static void SaveInt
     (
        java.io.PrintStream POut,
        String Name,
        int Value,
        int Indent
     ) {
    POut.printf(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds", Indent), " ");
    POut.printf(Constants.StdLocale, "<param name=\"%s\" value=\"%d\"/>\n", Name, Value);
  }

  private static void SaveNumber
     (
        java.io.PrintStream POut,
        String Name,
        Number Value,
        int Indent
     ) {
    POut.printf(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds", Indent), " ");
    POut.printf
       ("<param name=\"%s\" value=\""
          + Value.toStateString()
          + "\"/>\n", Name);
  }

  private static boolean GetBool
     (
        String Value
     ) {
    boolean Result;
    int IntValue;
    try {
      IntValue = Integer.parseInt(Value);
    } catch (NumberFormatException Bad) {
      throw new DataFormatException(String.format(Constants.StdLocale, "bad boolean value \"%s\"", Value));
    }
    if (IntValue == 0) {
      Result = false;
    } else if (IntValue == 1) {
      Result = true;
    } else {
      throw new DataFormatException(String.format(Constants.StdLocale, "bad boolean value %d", IntValue));
    }
    return Result;
  }

  private static int GetInt
     (
        String Value
     ) {
    int Result;
    try {
      Result = Integer.parseInt(Value);
    } catch (NumberFormatException Bad) {
      throw new DataFormatException(String.format(Constants.StdLocale, "bad integer value \"%s\"", Value));
    }
    return Result;
  }

  private static double GetDouble
     (
        String Value
     ) {
    double Result;
    try {
      Result = Double.parseDouble(Value);
    } catch (NumberFormatException Bad) {
      throw new DataFormatException(String.format(Constants.StdLocale, "bad double value \"%s\"", Value));
    }
    return Result;
  }

  private static ArrayList<Number> ParseNumberList
     (
        String Content
     ) {
    /* reads memory values: numbers in fixed or scientific notation
       separated by blanks. */
    final ArrayList<Number> Result = new ArrayList<>();
    for (String Token : Content.trim().split("\\s+")) {
      if (Token.isEmpty())
        continue;
      try {
        Result.add(new Number(Token));
      } catch (NumberFormatException Bad) {
        throw new DataFormatException(String.format(Constants.StdLocale, "bad number \"%s\"", Token));
      }
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

  private static void SaveBank
          (
                  int N, /* bank number 1 to 4 */
                  int Id,
                  State Calc,
                  byte[] Program,
                  java.io.PrintStream POut,
                  int Indent
          ) {

    POut.print(String.format(Constants.StdLocale,
        String.format(Constants.StdLocale,
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
                 Constants.StdLocale,
                 String.format(Constants.StdLocale, "%%%ds", Indent + 4),
                ""
              )
          );
      }
      POut.printf(Constants.StdLocale, "%02d", Program[k]);
      Cols++;
    }
    POut.print
      (
        String.format(Constants.StdLocale,
            String.format(Constants.StdLocale,
                "\n%%%ds</bankprog>\n", Indent), "")
      );

    POut.print(String.format(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds<bankmem n='%01d' id='%03d'>\n", Indent, N, Id), ""));

    for (int k = (4 - N) * 30; k < (4 - N + 1) * 30; k++) {
      if (k < Calc.MaxMemories) {
        POut.printf("            %s\n", Calc.Memory[k].toStateString());
      }
    }

    POut.print
      (
        String.format(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds</bankmem>\n", Indent, N), "")
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
    POut.print(String.format(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds<prog>\n", Indent), ""));
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
                        Constants.StdLocale,
                        String.format(Constants.StdLocale, "%%%ds", Indent + 4),
                        ""
                     )
               );
          }
          POut.printf(Constants.StdLocale, "%02d", Program[j]);
          ++Cols;
        }
        LastNonzero = i;
      }
      ++i;
    }
    POut.print
       (
          String.format(Constants.StdLocale, String.format(Constants.StdLocale, "%%%ds</prog>\n", Indent), "")
       );
  }

  /* Note Importer and Exporter states are NOT saved/restored */

  public static void Save
     (
        State Calc,
        UiState Ui, /* ignored unless CalcState, can be null */
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
                     String.format(Constants.StdLocale, "card%02d", BankNr),
                     true
                  );
            CardOut.Out.write(Bank.Card);
            CardOut.close();
          }
          if (BankNr != 0 && Bank.Program != null)
                      /* bank 0 program written out later */ {
            final ZipComponentWriter ProgOut =
               new ZipComponentWriter
                  (
                     Out,
                     String.format(Constants.StdLocale, "prog%02d", BankNr),
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
                  String.format(Constants.StdLocale, "help%02d", BankNr),
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
          SaveBool(POut, "alt", Calc.Engine.Keys.AltState, 8);
          SaveBool(POut, "overlay", Ui != null && Ui.GetOverlayVisible(), 8);
          SaveInt(POut, "selected_button", Ui != null ? Ui.GetSelectedButton() : -1, 8);
          SaveInt(POut, "digits_needed", Calc.Engine.Keys.DigitsNeeded, 8);
          SaveBool(POut, "accept_symbolic", Calc.Engine.Keys.AcceptSymbolic, 8);
          SaveBool(POut, "accept_ind", Calc.Engine.Keys.AcceptInd, 8);
          SaveBool(POut, "next_literal", Calc.Engine.Keys.NextLiteral, 8);
          SaveInt(POut, "accum_digits", Calc.Engine.Keys.AccumDigits, 8);
          SaveInt(POut, "first_operand", Calc.Engine.Keys.FirstOperand, 8);
          SaveBool(POut, "got_first_operand", Calc.Engine.Keys.GotFirstOperand, 8);
          SaveBool(POut, "got_first_ind", Calc.Engine.Keys.GotFirstInd, 8);
          SaveBool(POut, "is_symbolic", Calc.Engine.Keys.IsSymbolic, 8);
          SaveBool(POut, "got_ind", Calc.Engine.Keys.GotInd, 8);
          SaveInt(POut, "collecting_for_function", Calc.Engine.Keys.CollectingForFunction, 8);
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
                            Constants.StdLocale,
                            "unrecognized Calc.CurState = %d",
                            Calc.CurState
                         )
                   );
            }
            POut.printf
               (
                  Constants.StdLocale,
                  "        <param name=\"state\" value=\"%s\"/>\n",
                  StateName
               );
          }
          SaveBool(POut, "exponent_entered", Calc.ExponentEntered, 8);
          SaveBool(POut, "error_state", State.inError, 8);
          if (Calc.CurState != State.ResultState) {
            POut.printf
               (
                  Constants.StdLocale,
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
                      String.format(Constants.StdLocale, "unrecognized Calc.CurFormat = %d", Calc.CurFormat)
                   );
            }
            POut.printf(Constants.StdLocale, "        <param name=\"format\" value=\"%s\"/>\n", FmtName);
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
                      String.format(Constants.StdLocale, "unrecognized Calc.Ang = %d", Calc.CurAng)
                   );
            }
            POut.printf
               (
                  Constants.StdLocale,
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
                      String.format(Constants.StdLocale, "unrecognized stacked op %d at pos %d", Op.Operator, i)
                   );
            }
            POut.printf
               (
                  Constants.StdLocale,
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
            POut.printf("            %s\n", Calc.Memory[i].toStateString());
          }
          POut.println("        </mem>");
          POut.print("        <feedback kind=\"");
          POut.print(Ui != null ? Ui.GetFeedback() : UiState.FEEDBACK_KIND_CLICK);
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
        final Printer Print = Calc.Engine.Print;
        final int LineEnd = Print.Content.size();
        if(LineEnd > Constants.MaxPrinterLineSaved) {
          LineStart = LineEnd - Constants.MaxPrinterLineSaved;
        }
        for(int i = LineStart; i < LineEnd; i++) {
          final byte[] line = Print.Content.get(i);
          POut.printf("      <line>%s</line>", Print.BackToText(line));
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

  public static byte[] ReadAll
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

  static class CalcStateLoader extends org.xml.sax.helpers.DefaultHandler {
    UiState Ui; /* can be null */
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
          UiState Ui,
          State Calc,
          int BankNr,
          int UserCardId,
          boolean CalcState
       ) {
      super();
      this.Ui = Ui;
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
          if (CalcState) {
            /* the saved paper replaces the current one (a new printer starts
               with a blank line, the saved paper too) */
            Calc.Engine.Print.Content.clear();
          }
          ParseState = DoingPrinter;
          Handled = true;
        }
      } else if (ParseState == DoingButtons) {
        if (localName.equals("param")) {
          final String Name = attributes.getValue("name").intern();
          final String Value = attributes.getValue("value");
          if (Name.equals("alt")) {
            Calc.Engine.Keys.AltState = GetBool(Value);
          } else if (Name.equals("overlay")) {
            final boolean Overlay = GetBool(Value);
            if (Ui != null) {
              Ui.SetOverlayVisible(Overlay);
            }
          } else if (Name.equals("selected_button")) {
            final int Selected = GetInt(Value);
            if (Ui != null) {
              Ui.SetSelectedButton(Selected);
            }
          } else if (Name.equals("digits_needed")) {
            Calc.Engine.Keys.DigitsNeeded = GetInt(Value);
          } else if (Name.equals("accept_symbolic")) {
            Calc.Engine.Keys.AcceptSymbolic = GetBool(Value);
          } else if (Name.equals("accept_ind")) {
            Calc.Engine.Keys.AcceptInd = GetBool(Value);
          } else if (Name.equals("next_literal")) {
            Calc.Engine.Keys.NextLiteral = GetBool(Value);
          } else if (Name.equals("accum_digits")) {
            Calc.Engine.Keys.AccumDigits = GetInt(Value);
          } else if (Name.equals("first_operand")) {
            Calc.Engine.Keys.FirstOperand = GetInt(Value);
          } else if (Name.equals("got_first_operand")) {
            Calc.Engine.Keys.GotFirstOperand = GetBool(Value);
          } else if (Name.equals("got_first_ind")) {
            Calc.Engine.Keys.GotFirstInd = GetBool(Value);
          } else if (Name.equals("is_symbolic")) {
            Calc.Engine.Keys.IsSymbolic = GetBool(Value);
          } else if (Name.equals("got_ind")) {
            Calc.Engine.Keys.GotInd = GetBool(Value);
          } else if (Name.equals("collecting_for_function")) {
            Calc.Engine.Keys.CollectingForFunction = GetInt(Value);
          } else {
            throw new DataFormatException
               (
                  String.format(Constants.StdLocale, "unrecognized <buttons> param \"%s\"", Name)
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
                    String.format(Constants.StdLocale, "unrecognized calc state \"%s\"", Value)
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
                    String.format(Constants.StdLocale, "unrecognized calc format \"%s\"", Value)
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
                    String.format(Constants.StdLocale, "unrecognized calc angle_units \"%s\"", Value)
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
                  String.format(Constants.StdLocale, "unrecognized <calc> param \"%s\"", Name)
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
          if (Ui != null) {
            Ui.SetFeedback
               (
                  Kind.equals(UiState.FEEDBACK_KIND_NONE)
                     || Kind.equals(UiState.FEEDBACK_KIND_VIBRATE)
                     || Kind.equals(UiState.FEEDBACK_KIND_BOTH)
                  ? Kind : UiState.FEEDBACK_KIND_CLICK
               );
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
                  String.format(Constants.StdLocale, "unrecognized <op> operator \"%s\"", OpName)
               );
          }
          if (Calc.OpStackNext == Calc.MaxOpStack) {
            throw new DataFormatException
               (
                  String.format(Constants.StdLocale, "too many <op> entries -- only %d allowed", Calc.MaxOpStack)
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
                  String.format(Constants.StdLocale, "too many <ret> entries -- only %d allowed", Calc.MaxReturnStack)
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
                              Constants.StdLocale,
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
            Calc.Engine.Print.Translate(ContentStr, line);
            Calc.Engine.Print.Content.add(line);
          } else if (localName.equals("printer")) {
            ParseState = DoingCalc;
          }
          break;
        case DoingMem:
          if (localName.equals("mem")) {

            ArrayList<Number> m = ParseNumberList(ContentStr);

            if (m.size() > Calc.MaxMemories) {
              throw new DataFormatException
                  (
                      String.format(Constants.StdLocale, "too many memories, only %d allowed", Calc.MaxMemories)
                  );
            }
            for (int reg = 0; reg < m.size(); ++reg) {
              Calc.Memory[reg] = m.get(reg);
            }
            ParseState = DoingCalc;
          }
          break;
        case DoingBankMem:
          if (localName.equals("bankmem")) {
            ArrayList<Number> m = ParseNumberList(ContentStr);

            if (m.size() > 30) {
              throw new DataFormatException
                  (
                      String.format(Constants.StdLocale, "too many mem, only 30 allowed for a bank")
                  );
            }

            if (BankNr != 0 && CardBankNr != BankNr) {
              Calc.SetErrorState(true);
            } else {
              final Number[] CardMem = m.toArray(new Number[0]);
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
                      String.format(Constants.StdLocale, "too many memories, only %d allowed", Calc.MaxMemories)
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
                      String.format(Constants.StdLocale, "too many opcodes, only 240 allowed for a bank")
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
                        String.format(Constants.StdLocale, "too many flags, only %d allowed", Calc.MaxFlags)
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
}
