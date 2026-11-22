/*
    ti5x calculator emulator -- command line files and error reporting

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

package net.obry.ti5x.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.obry.ti5x.engine.BankStore;
import net.obry.ti5x.engine.Calculator;
import net.obry.ti5x.engine.Constants;
import net.obry.ti5x.engine.ErrorSink;
import net.obry.ti5x.engine.FileStore;
import net.obry.ti5x.engine.ProgramFiles;

final class ConsoleHost implements FileStore, ErrorSink, BankStore {
  /* files are kept under a base directory with the same layout as the
     app: Data/ for Op 96/97 data files, Banks/ for card bank files. */

  private final File BaseDir;
  private Calculator Engine; /* set once the engine exists */

  ConsoleHost
     (
        File BaseDir
     ) {
    this.BaseDir = BaseDir;
  }

  void SetEngine
     (
        Calculator Engine
     ) {
    this.Engine = Engine;
  }

  private File FileOf
     (
        String Dir,
        String FileName
     ) {
    return new File(new File(BaseDir, Dir), FileName);
  }

  /* FileStore */

  @Override
  public InputStream getInputStream(String Dir, String FileName) {
    return getInputStreamForPath(FileOf(Dir, FileName).getPath());
  }

  @Override
  public InputStream getInputStreamForPath(String Path) {
    try {
      final File F = new File(Path);
      return F.isFile() ? new FileInputStream(F) : null;
    } catch (IOException Failed) {
      return null;
    }
  }

  @Override
  public OutputStream getOutputStream(String Dir, String FileName, boolean Append) {
    final File F = FileOf(Dir, FileName);
    F.getParentFile().mkdirs();
    try {
      return new FileOutputStream(F, Append);
    } catch (IOException Failed) {
      return null;
    }
  }

  @Override
  public boolean deleteFile(String Dir, String FileName) {
    return FileOf(Dir, FileName).delete();
  }

  @Override
  public String GetAppDataExportFile(String FileName) {
    return FileOf(Constants.DataDir, FileName).getPath();
  }

  /* ErrorSink */

  @Override
  public void ImportError(String Detail) {
    System.err.println("ti5x: import error: " + Detail);
  }

  @Override
  public void ExportError(String Detail) {
    System.err.println("ti5x: export error: " + Detail);
  }

  /* BankStore */

  @Override
  public void LoadBankFile(int BankNr, int CardId, String FileName) {
    final InputStream In = getInputStream(Constants.BankDir, FileName);
    if (In == null) {
      Engine.Calc.SetErrorState(true);
      return;
    }
    try (InputStream Bank = In) {
      ProgramFiles.LoadBank(Engine.Calc, Bank, BankNr, CardId);
    } catch (IOException Failed) {
      System.err.println("ti5x: bank file error: " + Failed);
    }
  }

  @Override
  public void SaveBankFile(int BankNr, int CardId, String FileName) {
    final OutputStream Out = getOutputStream(Constants.BankDir, FileName, false);
    if (Out == null) {
      System.err.println("ti5x: cannot create bank file " + FileOf(Constants.BankDir, FileName));
      return;
    }
    ProgramFiles.SaveBank(Engine.Calc, Out, BankNr, CardId);
  }
}
