/*
    ti5x calculator emulator -- program label map

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

public class LabelMap {
  /* mapping from label key codes to program locations, iterated in
     ascending key order (same contract as android.util.SparseIntArray
     for the methods used). */

  private static final int MaxKey = 256;
  private final int[] Loc = new int[MaxKey];
  private int Count = 0;

  public LabelMap() {
    java.util.Arrays.fill(Loc, -1);
  }

  public int size() {
    return Count;
  }

  public int indexOfKey
     (
        int Key
     ) {
    /* returns >= 0 if Key is present, a negative value otherwise. */
    if (Key < 0 || Key >= MaxKey || Loc[Key] < 0) {
      return -1;
    }
    int Index = 0;
    for (int k = 0; k < Key; k++) {
      if (Loc[k] >= 0) {
        Index++;
      }
    }
    return Index;
  }

  public int get
     (
        int Key
     ) {
    /* returns the location for Key, 0 if absent. */
    return Key >= 0 && Key < MaxKey && Loc[Key] >= 0 ? Loc[Key] : 0;
  }

  public void put
     (
        int Key,
        int Location
     ) {
    if (Key < 0 || Key >= MaxKey || Location < 0) {
      throw new IllegalArgumentException("bad label " + Key + " at " + Location);
    }
    if (Loc[Key] < 0) {
      Count++;
    }
    Loc[Key] = Location;
  }

  private int KeyIndex
     (
        int Index
     ) {
    for (int k = 0; k < MaxKey; k++) {
      if (Loc[k] >= 0 && Index-- == 0) {
        return k;
      }
    }
    throw new IndexOutOfBoundsException("label index " + Index);
  }

  public int keyAt
     (
        int Index
     ) {
    return KeyIndex(Index);
  }

  public int valueAt
     (
        int Index
     ) {
    return Loc[KeyIndex(Index)];
  }
}
