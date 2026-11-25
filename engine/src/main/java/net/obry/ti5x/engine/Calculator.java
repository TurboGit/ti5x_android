/*
    ti5x calculator emulator -- the engine: calculator parts and host services

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

public class Calculator {
  /* Wires the calculator parts together with the services provided by
     the host (Android app or command line). The engine parts reach each
     other and the host only through this object. */

  /* host services */
  public final Scheduler Sched;
  public final DisplaySink Disp;
  public final HelpSink Help;
  public final FileStore Files;
  public final ErrorSink Errors;
  public final BankStore Banks;

  /* calculator parts */
  public final Exporter Export;
  public final Printer Print;
  public final Importer Import;
  public final State Calc;
  public final Keypad Keys;
  public final Tester Test;

  public Calculator
     (
        Scheduler Sched,
        DisplaySink Disp,
        HelpSink Help, /* can be null */
        FileStore Files,
        ErrorSink Errors,
        BankStore Banks
     ) {
    this.Sched = Sched;
    this.Disp = Disp;
    this.Help = Help != null ? Help : (Card, HelpText) -> {};
    this.Files = Files;
    this.Errors = Errors;
    this.Banks = Banks;
    /* State must come after the parts it uses during its initial reset */
    Export = new Exporter(this);
    Print = new Printer(this);
    Import = new Importer(this);
    Calc = new State(this);
    Keys = new Keypad(this);
    Test = new Tester(this);
  }

  public int RunTests() {
    /* runs the built-in regression tests (as Op 99 does), returns the
       number of tests passed, or -n if test n failed. */
    return Test.Run();
  }
}
