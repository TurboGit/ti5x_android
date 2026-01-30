/*
    Help-page display

    Copyright 2011, 2012 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

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

public class Help extends BaseActivity {
  public static String ContentID = "net.obry.ti5x.HelpContent";
  android.webkit.WebView HelpView;
  /* for remembering scroll position of last page displayed: */
  static String LastContent;
  static android.graphics.Point LastScroll = null;
  @Override
  protected int getLayoutResId() { return R.layout.help; }

  @Override
  protected int getRootViewId() { return R.id.help_activity; }

  @Override
  public void onCreate
     (
        android.os.Bundle savedInstanceState
     ) {
    setContentView(R.layout.help);
    super.onCreate(savedInstanceState);
    HelpView = (android.webkit.WebView) findViewById(R.id.help_view);
    final android.content.Intent MyIntent = getIntent();
    final String NewContent = new String(MyIntent.getByteArrayExtra(ContentID));
    HelpView.loadDataWithBaseURL
       (
          null,
          NewContent,
          null, /* text/html */
          "utf-8",
          null
       );
    if (LastContent == null || !NewContent.equals(LastContent)) {
      LastScroll = null;
    }
    LastContent = NewContent;

    HelpView.setWebViewClient(new android.webkit.WebViewClient() {
      @Override
      public void onPageFinished(android.webkit.WebView view, String url) {
        if (LastScroll != null) {
          HelpView.scrollTo(LastScroll.x, LastScroll.y);
          LastScroll = null; /* only do once */
        }
      }
    });
  }

  @Override
  public void onDestroy() {
    LastScroll = new android.graphics.Point(HelpView.getScrollX(), HelpView.getScrollY());
    super.onDestroy();
  }
}
