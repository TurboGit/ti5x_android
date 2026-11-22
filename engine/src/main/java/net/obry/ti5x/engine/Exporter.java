/*
    ti5x calculator emulator -- data exporter context

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

public class Exporter {
  private final Calculator Engine; /* for the file store and error reporting */
  private java.io.OutputStream Out;
  private java.io.PrintStream PrintOut;
  public boolean NumbersOnly = true; /* actually initial value irrelevant */

  public Exporter
     (
        Calculator Engine
     ) {
    this.Engine = Engine;
    Out = null;
    PrintOut = null;
  }

  public boolean IsOpen() {
    return Out != null;
  }

  public void Open
     (
        String FileName,
        boolean Append,
        boolean NumbersOnly
     )
     throws RuntimeException {

    try {
      Out = Engine.Files.getOutputStream(Constants.DataDir, FileName, Append);
      if (Out == null) {
        final String FullFilename = Engine.Files.GetAppDataExportFile(FileName);
        Out = new java.io.FileOutputStream(FullFilename, Append);
      }
      this.NumbersOnly = NumbersOnly;
      if (this.NumbersOnly) {
        PrintOut = new java.io.PrintStream(Out);
      }
    } catch (Exception DirErr) {
      throw new RuntimeException(DirErr.toString());
    }
  }

  public void Flush() {
    if (Out != null) {
      try {
        if (PrintOut != null) {
          PrintOut.flush();
        }
        Out.flush();
      } catch (java.io.IOException WriteErr) {
        Engine.Errors.ExportError(WriteErr.toString());
        PrintOut = null;
        Out = null;
      }
    }
  }

  public void Close() {
    if (Out != null) {
      try {
        if (PrintOut != null) {
          PrintOut.flush();
          PrintOut.close();
        }
        Out.flush();
        Out.close();
      } catch (java.io.IOException WriteErr) {
        Engine.Errors.ExportError(WriteErr.toString());
      }
      PrintOut = null;
      Out = null;
    }
  }

  public void WriteLine
     (
        String Line
     ) {
    /* writes another line to the export data file. */
    if (Out != null) {
      try {
        Out.write(Line.getBytes());
        Out.write("\n".getBytes());
      } catch (java.io.IOException WriteErr) {
        Engine.Errors.ExportError(WriteErr.toString());
        Out = null;
      }
    }
  }
}
