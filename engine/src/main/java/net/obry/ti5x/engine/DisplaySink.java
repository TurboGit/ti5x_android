/*
    ti5x calculator emulator -- host display interface

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

public interface DisplaySink {
  /* the calculator LED display. */

  void SetShowing
     (
        String ToShow /* sequence of digits, possibly also minus signs and spaces */
     );

  void SetShowingError
     (
        String ToShow /* same, shown flashing */
     );

  void SetShowingRunning
     (
        char Ch /* 'C' while running, 'c' while running with import/export active */
     );
}
