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

package net.obry.ti5x;

import android.app.AlertDialog;

public class Exporter {
  private final android.content.Context ctx;
  private java.io.OutputStream Out;
  private java.io.PrintStream PrintOut;
  boolean NumbersOnly = true; /* actually initial value irrelevant */

  Exporter
     (
        android.content.Context ctx
     ) {
    this.ctx = ctx;
    Out = null;
    PrintOut = null;
  }

  boolean IsOpen() {
    return Out != null;
  }

  void Open
     (
        String FileName,
        boolean Append,
        boolean NumbersOnly
     )
     throws RuntimeException {
    try {
      Out = new java.io.FileOutputStream(FileName, Append);
      this.NumbersOnly = NumbersOnly;
      if (this.NumbersOnly) {
        PrintOut = new java.io.PrintStream(Out);
      }
    } catch (java.io.FileNotFoundException DirErr) {
      throw new RuntimeException(DirErr.toString());
    } catch (SecurityException PermErr) {
      throw new RuntimeException(PermErr.toString());
    }
  }

  void Flush() {
    if (Out != null) {
      try {
        if (PrintOut != null) {
          PrintOut.flush();
        }
        Out.flush();
      } catch (java.io.IOException WriteErr) {
        new AlertDialog.Builder(ctx)
            .setMessage(String.format
                 (
                    Global.StdLocale,
                    ctx.getString(R.string.export_error),
                    WriteErr.toString()
                 ))
            .setPositiveButton("OK", null)
            .show();
        PrintOut = null;
        Out = null;
      }
    }
  }

  void Close() {
    if (Out != null) {
      try {
        if (PrintOut != null) {
          PrintOut.flush();
          PrintOut.close();
        }
        Out.flush();
        Out.close();
      } catch (java.io.IOException WriteErr) {
        new AlertDialog.Builder(ctx)
            .setMessage(String.format
                 (
                    Global.StdLocale,
                    ctx.getString(R.string.export_error),
                    WriteErr.toString()
                 ))
            .setPositiveButton("OK", null)
            .show();
      }
      PrintOut = null;
      Out = null;
    }
  }

  void WriteLine
     (
        String Line
     ) {
    /* writes another line to the export data file. */
    if (Out != null) {
      try {
        Out.write(Line.getBytes());
        Out.write("\n".getBytes());
      } catch (java.io.IOException WriteErr) {
        new AlertDialog.Builder(ctx)
            .setMessage(String.format
                 (
                    Global.StdLocale,
                    ctx.getString(R.string.export_error),
                    WriteErr.toString()
                 ))
            .setPositiveButton("OK", null)
            .show();
        Out = null;
      }
    }
  }

  void WriteNum
     (
        Number Num
     ) {
    /* writes a number to the export data file in a standard form that can
       be read in again by myself or other programs. */
    if (PrintOut != null) {
        PrintOut.printf("%s\n", Num.toString());
    }
  }
}
