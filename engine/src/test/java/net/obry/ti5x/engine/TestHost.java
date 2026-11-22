/*
    ti5x calculator emulator -- in-memory host services for unit tests

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

package net.obry.ti5x.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TestHost implements Scheduler, DisplaySink, FileStore, ErrorSink, BankStore {
  /* host services kept in memory; scheduled tasks only run on RunPending */

  final ArrayDeque<Runnable> Pending = new ArrayDeque<>();
  final List<String> Shown = new ArrayList<>();
  final List<String> Errors = new ArrayList<>();
  final Map<String, ByteArrayOutputStream> Files = new HashMap<>();

  Calculator NewCalculator() {
    return new Calculator(this, this, null, this, this, this);
  }

  void RunPending
     (
        int MaxTasks
     ) {
    for (int i = 0; i < MaxTasks && !Pending.isEmpty(); i++) {
      Pending.poll().run();
    }
  }

  /* Scheduler */
  @Override
  public boolean post(Runnable Task) {
    Pending.add(Task);
    return true;
  }

  @Override
  public boolean postDelayed(Runnable Task, long DelayMillis) {
    Pending.add(Task);
    return true;
  }

  @Override
  public void removeCallbacks(Runnable Task) {
    Pending.removeIf(t -> t == Task);
  }

  /* DisplaySink */
  @Override
  public void SetShowing(String ToShow) {
    Shown.add(ToShow);
  }

  @Override
  public void SetShowingError(String ToShow) {
    Shown.add(ToShow + " !");
  }

  @Override
  public void SetShowingRunning(char Ch) {
    Shown.add(String.valueOf(Ch));
  }

  /* FileStore */
  private static String Key(String Dir, String FileName) {
    return Dir + "/" + FileName;
  }

  @Override
  public InputStream getInputStream(String Dir, String FileName) {
    return getInputStreamForPath(Key(Dir, FileName));
  }

  @Override
  public InputStream getInputStreamForPath(String Path) {
    final ByteArrayOutputStream Data = Files.get(Path);
    return Data == null ? null : new ByteArrayInputStream(Data.toByteArray());
  }

  @Override
  public OutputStream getOutputStream(String Dir, String FileName, boolean Append) {
    final String K = Key(Dir, FileName);
    ByteArrayOutputStream Data = Files.get(K);
    if (Data == null || !Append) {
      Data = new ByteArrayOutputStream();
      Files.put(K, Data);
    }
    return Data;
  }

  @Override
  public boolean deleteFile(String Dir, String FileName) {
    return Files.remove(Key(Dir, FileName)) != null;
  }

  @Override
  public String GetAppDataExportFile(String FileName) {
    return Key(Constants.DataDir, FileName);
  }

  /* ErrorSink */
  @Override
  public void ImportError(String Detail) {
    Errors.add("import: " + Detail);
  }

  @Override
  public void ExportError(String Detail) {
    Errors.add("export: " + Detail);
  }

  /* BankStore */
  @Override
  public void LoadBankFile(int BankNr, int CardId, String FileName) {
  }

  @Override
  public void SaveBankFile(int BankNr, int CardId, String FileName) {
  }
}
