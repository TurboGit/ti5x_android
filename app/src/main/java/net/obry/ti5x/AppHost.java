/*
    ti5x calculator emulator -- Android implementation of the engine host services

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

import android.app.AlertDialog;

import net.obry.ti5x.engine.BankStore;
import net.obry.ti5x.engine.ErrorSink;
import net.obry.ti5x.engine.Scheduler;

class AppHost implements Scheduler, ErrorSink, BankStore {
  /* engine services implemented with the Android UI thread, dialogs and Persistent. */

  private final android.content.Context ctx;
  private final android.os.Handler UIHandler;

  AppHost
     (
        android.content.Context ctx
     ) {
    this.ctx = ctx;
    UIHandler = new android.os.Handler();
  }

  /* Scheduler */

  @Override
  public boolean post(Runnable Task) {
    return UIHandler.post(Task);
  }

  @Override
  public boolean postDelayed(Runnable Task, long DelayMillis) {
    return UIHandler.postDelayed(Task, DelayMillis);
  }

  @Override
  public void removeCallbacks(Runnable Task) {
    UIHandler.removeCallbacks(Task);
  }

  /* ErrorSink */

  private void ShowError
     (
        int FormatId,
        String Detail
     ) {
    new AlertDialog.Builder(ctx)
        .setMessage(String.format(Global.StdLocale, ctx.getString(FormatId), Detail))
        .setPositiveButton("OK", null)
        .show();
  }

  @Override
  public void ImportError(String Detail) {
    ShowError(R.string.import_error, Detail);
  }

  @Override
  public void ExportError(String Detail) {
    ShowError(R.string.export_error, Detail);
  }

  /* BankStore */

  @Override
  public void LoadBankFile(int BankNr, int CardId, String FileName) {
    Persistent.LoadBankFile(ctx, BankNr, CardId, FileName, Global.Calc);
  }

  @Override
  public void SaveBankFile(int BankNr, int CardId, String FileName) {
    Persistent.SaveBankFile(ctx, BankNr, CardId, FileName, Global.Calc);
  }
}
