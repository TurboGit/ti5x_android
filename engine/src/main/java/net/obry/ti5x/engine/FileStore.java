/*
    ti5x calculator emulator -- host file storage interface

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

public interface FileStore {
  /* access to the calculator files. Dir is one of the Constants.*Dir names. */

  java.io.InputStream getInputStream
     (
        String Dir,
        String FileName
     ); /* returns null if not found */

  java.io.InputStream getInputStreamForPath
     (
        String Path
     ); /* returns null if not found */

  java.io.OutputStream getOutputStream
     (
        String Dir,
        String FileName,
        boolean Append
     ); /* returns null on failure */

  boolean deleteFile
     (
        String Dir,
        String FileName
     );

  String GetAppDataExportFile
     (
        String FileName
     ); /* full path of the named data export file */
}
