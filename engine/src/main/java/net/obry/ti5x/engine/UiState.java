/*
    ti5x calculator emulator -- host user interface state saved with the calculator state

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

public interface UiState {
  /* keypad display state kept in the saved calculator state (see ProgramFiles).
     A host without such state passes null: defaults are saved and loaded
     values are ignored. */

  String FEEDBACK_KIND_CLICK = "click";
  String FEEDBACK_KIND_VIBRATE = "vibrate";
  String FEEDBACK_KIND_BOTH = "both";
  String FEEDBACK_KIND_NONE = "none";

  boolean GetOverlayVisible();

  void SetOverlayVisible(boolean Visible);

  int GetSelectedButton(); /* key code of the button being pressed, -1 if none */

  void SetSelectedButton(int KeyCode);

  String GetFeedback(); /* one of the FEEDBACK_KIND_* values */

  void SetFeedback(String Kind);
}
