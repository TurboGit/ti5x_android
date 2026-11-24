/*
    ti5x calculator emulator -- console display and printer output

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

import java.io.PrintStream;

import net.obry.ti5x.engine.Calculator;
import net.obry.ti5x.engine.DisplaySink;
import net.obry.ti5x.engine.State;

final class ConsoleDisplay implements DisplaySink {
  /* Shows the 12-digit LED display as a line of text:

        |        3.14|          display, right aligned
        | 9.9999999 99| !       flashing: error state
        |C            |         running

     followed by the 2nd, Inv and angle (Rad/Grad) indicators, and ~ in
     classic mode (Op 95). In trace mode each change is printed on its
     own line, otherwise the line is redrawn in place. Printer output is shown as "PRT <line>". */

  private static final int NrDigits = 12;

  private final PrintStream Out;
  private final boolean Trace;
  private final boolean ShowPrinter;
  private Calculator Engine; /* set once the engine exists */

  private String Showing = Pad("0");
  private boolean Error = false;
  private String LastLine = null; /* last line printed */
  private int PrintedLines = 1; /* the printer paper starts with a blank line */
  private boolean LineOpen = false; /* in place display line not terminated */
  private boolean Started = false; /* nothing is shown before Start */

  ConsoleDisplay
     (
        PrintStream Out,
        boolean Trace,
        boolean ShowPrinter
     ) {
    this.Out = Out;
    this.Trace = Trace;
    this.ShowPrinter = ShowPrinter;
  }

  void SetEngine
     (
        Calculator Engine
     ) {
    this.Engine = Engine;
  }

  static String Pad
     (
        String ToShow
     ) {
    /* right aligns ToShow on the display positions: a decimal point
       shares the position of the digit before it. The result is always
       NrDigits + 1 characters wide: room is left for one decimal point. */
    int Positions = 0;
    int Points = 0;
    for (int i = 0; i < ToShow.length(); i++) {
      if (ToShow.charAt(i) != '.' || i == 0) {
        Positions++;
      } else {
        Points++;
      }
    }
    final StringBuilder Result = new StringBuilder();
    for (int i = Positions; i < NrDigits; i++) {
      Result.append(' ');
    }
    Result.append(ToShow);
    if (Points == 0) {
      Result.append(' ');
    }
    return Result.toString();
  }

  private String Status() {
    if (Engine == null)
      return "";
    final State Calc = Engine.Calc;
    final StringBuilder S = new StringBuilder();
    if (Engine.Keys.AltState)
      S.append(" 2nd");
    if (Calc.InvState)
      S.append(" Inv");
    if (Calc.CurAng == State.ANG_RAD)
      S.append(" Rad");
    else if (Calc.CurAng == State.ANG_GRAD)
      S.append(" Grad");
    if (Calc.ProgMode)
      S.append(" Lrn");
    if (!net.obry.ti5x.engine.Number.RomMode())
      S.append(" ~"); /* classic mode (Op 95) */
    return S.toString();
  }

  String Line() {
    return "|" + Showing + "|" + (Error ? " !" : "") + Status();
  }

  synchronized void Start() {
    /* starts showing the display, once the program is loaded. */
    Started = true;
    Refresh();
  }

  synchronized void Refresh() {
    /* shows the current display line if it changed. */
    if (!Started)
      return;
    final String L = Line();
    if (L.equals(LastLine))
      return;
    LastLine = L;
    if (Trace) {
      Out.println(L);
    } else {
      Out.print("\r\033[K" + L);
      LineOpen = true;
    }
    Out.flush();
  }

  synchronized void FlushPrinter() {
    /* shows the lines printed since last call. */
    if (Engine == null || !ShowPrinter || !Started)
      return;
    final java.util.List<byte[]> Paper = Engine.Print.Content;
    if (Paper.size() < PrintedLines) {
      PrintedLines = Paper.size(); /* paper was cleared */
    }
    if (Paper.size() == PrintedLines)
      return;
    if (LineOpen) {
      Out.print("\r\033[K");
    }
    for (; PrintedLines < Paper.size(); PrintedLines++) {
      Out.println("PRT " + Engine.Print.BackToText(Paper.get(PrintedLines)).replaceAll(" +$", ""));
    }
    LastLine = null; /* redraw the display after the printer output */
    LineOpen = false;
    Refresh();
  }

  synchronized void Finish() {
    /* terminates the in place display line. */
    FlushPrinter();
    Refresh();
    if (LineOpen) {
      Out.println();
      LineOpen = false;
    }
    Out.flush();
  }

  /* DisplaySink */

  @Override
  public synchronized void SetShowing(String ToShow) {
    Showing = Pad(ToShow);
    Error = false;
    Refresh();
  }

  @Override
  public synchronized void SetShowingError(String ToShow) {
    Showing = Pad(ToShow);
    Error = true;
    Refresh();
  }

  @Override
  public synchronized void SetShowingRunning(char Ch) {
    final StringBuilder S = new StringBuilder().append(Ch);
    while (S.length() < NrDigits + 1) {
      S.append(' ');
    }
    Showing = S.toString();
    Error = false;
    Refresh();
  }
}
