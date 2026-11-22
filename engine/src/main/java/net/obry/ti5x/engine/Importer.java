/*
    ti5x calculator emulator -- data importer context

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

package net.obry.ti5x.engine;


import java.io.IOException;

public class Importer {

  private final Calculator Engine; /* for the file store and the calculator state */

  public Importer
     (
        Calculator Engine
     ) {
    this.Engine = Engine;
  }

  public class ImportDataFeeder extends State.ImportFeeder {
    public java.io.InputStream Data;
    public String FileName;
    public int LineNr, ColNr; /* for reporting locations of errors */
    public boolean WasNL, WasCR, EOF;

    public ImportDataFeeder
       (
          java.io.InputStream Data,
          String FileName
       ) {
      this.Data = Data;
      this.FileName = FileName;
      LineNr = 0;
      ColNr = 0;
      WasNL = true; /* so LineNr gets incremented to 1 on first character */
      WasCR = false;
      EOF = false;
    }

    public void Reset() {
      if (Data != null) {
        try {
          // reset() is not supported, so we close and reopen the file
          Data.close();
          Data = Engine.Files.getInputStreamForPath(this.FileName);
          if (Data == null) {
            throw new java.io.FileNotFoundException("File not found: " + this.FileName);
          }
          LineNr = 0;
          ColNr = 0;
          WasNL = true;
          WasCR = false;
          EOF = false;
        } catch (IOException Failed) {
          throw new DataFormatException
              (
                  String.format
                      (
                          Constants.StdLocale,
                          "Couldn't reset import data file: %s",
                          Failed.toString()
                      )
              );        }
      }
    }

    @Override
    public Number Next()
       throws State.ImportEOFException {
      Number Result;
      StringBuilder LastNum = null;
      for (; ; ) {
        int ch;
        if (EOF) {
          ch = '\n';
        } else {
          try {
            ch = Data.read();
          } catch (java.io.IOException Failed) {
            throw new DataFormatException
               (
                  String.format
                     (
                        Constants.StdLocale,
                        "Couldn't read input file at line %d, col %d: %s",
                        LineNr,
                        ColNr,
                        Failed.toString()
                     )
               );
          }
          if (ch < 0) {
            EOF = true;
            ch = '\n';
          }
          if (WasNL) {
            ++LineNr;
            ColNr = 0;
          }
          if (ch != '\n' && ch != '\015') {
            ++ColNr;
          }
        }
        WasNL = ch == '\n' && !WasCR || ch == '\015';
        WasCR = ch == '\015';
        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\015' || ch == ',' || ch == ';') {
          if (LastNum != null) {
            try {
              Result = new Number(LastNum.toString());
            } catch (NumberFormatException Bad) {
              throw new DataFormatException
                 (
                    String.format
                       (
                          Constants.StdLocale,
                          "Bad number \"%s\" on line %d, col %d",
                          LastNum.toString(),
                          LineNr,
                          ColNr
                       )
                 );
            }
            break;
          } else if (EOF) {
            if (Data != null) {
              try {
                Data.close();
              } catch (java.io.IOException WhoCares) {
                              /* I mean, really? */
              }
              Data = null;
            }
            throw new State.ImportEOFException("no more numbers");
          }
        } else if
           (
           ch >= '0' && ch <= '9'
              ||
              ch == '.'
              ||
              ch == '-'
              ||
              ch == '+'
              ||
              ch == 'e'
              ||
              ch == 'E'
           ) {
          if (LastNum == null) {
            LastNum = new StringBuilder();
          }
          LastNum.appendCodePoint(ch);
        } else {
          throw new DataFormatException
             (
                String.format
                   (
                      Constants.StdLocale,
                      "Bad character \"%s\" at line %d, col %d",
                      new String(new byte[]{(byte) ch}),
                      LineNr,
                      ColNr
                   )
             );
        }
      }
      return Result;
    }
  }

  public void ImportData
     (
        String FileName
     )
     throws DataFormatException {
    /* starts importing numbers from the specified file. */
    java.io.InputStream Data;
    try {
      Data = Engine.Files.getInputStreamForPath(FileName);
      if (Data == null) {
        throw new java.io.FileNotFoundException("File not found: " + FileName);
      }
    } catch (Exception NotFound) {
      throw new DataFormatException
         (
            String.format
               (
                  Constants.StdLocale,
                  "Couldn't open input file \"%s\": %s",
                  FileName,
                  NotFound.toString()
               )
         );
    }
    Engine.Calc.SetImport(new ImportDataFeeder(Data, FileName));
  }
}
