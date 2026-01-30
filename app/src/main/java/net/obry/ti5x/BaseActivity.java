/*
    BaseActivity class to handled insets.

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

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.core.view.WindowCompat;

public abstract class BaseActivity extends Activity {

  // The layout for setContentView(...)
  @LayoutRes protected abstract int getLayoutResId();
  // The id of the root view (ex: R.id.keyboard_main_layout)
  @IdRes protected abstract int getRootViewId();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // 1) Config the EdgeToEdge
    WindowCompat.enableEdgeToEdge(getWindow());
    super.onCreate(savedInstanceState);

    // 2) Screen layout
    setContentView(getLayoutResId());

    // 3) Apply insets for the status bar
    View root = findViewById(getRootViewId());
    InsetsHelper.applySystemBarsInsets(root);
  }
}
