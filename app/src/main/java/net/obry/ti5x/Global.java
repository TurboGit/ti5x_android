/*
    ti5x calculator emulator -- global data

    Copyright 2011 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015-2018 Pascal Obry <pascal@obry.net>.

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

import android.view.MenuItem;

final class Global {
  static Display Disp;
  static LabelCard Label;
  static ButtonGrid Buttons;
  static State Calc;
  static Importer Import;
  static Exporter Export;
  static Printer Print;
  static Tester Test;

  static MenuItem Status;

  static final int NrSigFigures = 16;
  static final int MaxPrinterLineSaved = 10000;

  /* for formatting reals */
  static final java.util.Locale StdLocale = java.util.Locale.US;
  /* for all those places I don't want formatting to be locale-specific */

  static android.view.View ProgressWidgets;
  static android.widget.TextView ProgressMessage;
  static android.os.Handler UIRun;

  static public void setStatusDisplay() {
    final String SP = " ";
    String StatStr = "";
    switch (Global.Calc.CurAng) {
      case State.ANG_GRAD:
        StatStr = "Grad";
        break;
      case State.ANG_RAD:
        StatStr = "Rad ";
        break;
      default: // Degrees, do not display
        break;
    }

    StatStr = StatStr + " ";

    if (Global.Buttons.AltState) {
      StatStr = StatStr + "2nd";
    } else {
      StatStr = StatStr + SP.repeat(3);
    }
    StatStr = StatStr + " ";

    if (Global.Calc.InvState) {
      StatStr = StatStr + "Inv";
    }

    if (Status != null) {
      Status.setTitle(StatStr);
    }
  }
  static class Task {
    int TaskStatus = 0;
    Throwable TaskFailure = null;

    void SetStatus
       (
          int NewStatus,
          Throwable NewFailure
       ) {
      TaskStatus = NewStatus;
      TaskFailure = NewFailure;
    }

    boolean PreRun() {
      /* to be run before BGRun in calling thread, return false to abort */
      return true;
    }

    void BGRun() {
    }
    /* to be run in a background thread */

    void PostRun() {
    }
    /* to be run on UI thread after BGRun has finished */
  }

  private static class BGTask extends Thread {
    private final Task RunWhat;

    BGTask
       (
          Task RunWhat
       ) {
      super();
      this.RunWhat = RunWhat;
    }

    public void run() {
      RunWhat.BGRun();
      if (CurrentBGTask == this) {
        UIRun.post
           (
              new Runnable() {
                public void run() {
                  CurrentBGTask = null;
                  RunWhat.PostRun();
                  if (CurrentBGTask == null) {
                    ProgressWidgets.setVisibility(android.view.View.INVISIBLE);
                  }
                } /*run*/
              }
           );
      }
    }
  }

  private static BGTask CurrentBGTask;

  static void StartBGTask
     (
        Task RunWhat,
        String ProgressMessage /* null to leave unchanged */
     ) {
    if (CurrentBGTask == null) {
      if (ProgressMessage != null) {
        Global.ProgressMessage.setText(ProgressMessage);
      }
      CurrentBGTask = new BGTask(RunWhat);
      if (RunWhat.PreRun()) {
        if (ProgressWidgets.getVisibility() != android.view.View.VISIBLE) {
          /* short delay before making progress widget visible so that user
             doesn't see anything if task finishes quickly enough */
          UIRun.postDelayed
             (
                new Runnable() {
                  public void run() {
                    if (CurrentBGTask != null) {
                      ProgressWidgets.setVisibility(android.view.View.VISIBLE);
                    }
                  } /*run*/
                } /*Runnable*/,
                1000
             );
        }
        CurrentBGTask.start();
      } else {
        CurrentBGTask = null;
      }
    } else {
      throw new RuntimeException("Trying to start second background task");
    }
  }

  static boolean BGTaskInProgress() {
    return CurrentBGTask != null;
  }

  static void KillBGTask() {
    CurrentBGTask = null; /* can't actually kill it, just orphan it */
  }
}
