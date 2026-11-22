/*
    ti5x calculator emulator -- engine-wide constants

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

public final class Constants {

  private Constants() {
  }

  /* locale for all number and text formatting, never locale-specific */
  public static final java.util.Locale StdLocale = java.util.Locale.US;

  /* file extensions */
  public static final String BankExt = ".ti5b"; /* for card bank files */
  public static final String DataExt = ".dat"; /* for data export */

  /* directories in the calculator file store */
  public static final String ProgramsDir = "Programs"; /* user programs */
  public static final String DataDir = "Data"; /* exported data */
  public static final String BankDir = "Banks"; /* card bank files */
}
