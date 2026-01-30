/*
    Insets support for API Level > 33

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

import android.os.Build;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class InsetsHelper {

  private InsetsHelper() {}

  public static void applySystemBarsInsets(View root, boolean topBottomOnly) {
    ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
              WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

      final int left   = topBottomOnly ? 0 : bars.left;
      final int top    = bars.top;
      final int right  = topBottomOnly ? 0 : bars.right;
      final int bottom = bars.bottom;

      // On older APIs, we might get spurious zero-inset events.
      // Only apply padding if we have some insets.
      if (Build.VERSION.SDK_INT > 30 || (left != 0 || top != 0 || right != 0 || bottom != 0)) {
          v.setPadding(left, top, right, bottom);
      }

      return WindowInsetsCompat.CONSUMED;
    });
  }

  public static void applySystemBarsInsets(View root) {
      applySystemBarsInsets(root, false);
  }
}