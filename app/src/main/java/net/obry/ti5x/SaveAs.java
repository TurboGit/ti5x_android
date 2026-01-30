/*
    Let the user enter a name for saving a new program file.

    Copyright 2011      Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015-2026 Pascal Obry <pascal@obry.net>.

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

import android.os.Environment;
import com.google.android.material.snackbar.Snackbar;

public class SaveAs extends BaseActivity {
  private static android.view.View Extra = null;
  private static String SaveWhat = null;
  private static String SaveWhere = null;
  private static String FileExt = null;

  private static boolean Reentered = false; /* sanity check */
  public static SaveAs Current = null;

  private android.view.ViewGroup MainViewGroup;
  private android.widget.EditText SaveAsText;
  private String TheCleanedText;
  @Override
  protected int getLayoutResId() { return R.layout.save_as; }

  @Override
  protected int getRootViewId() { return R.id.details_root; }
  class OverwriteConfirm
     extends android.app.AlertDialog
     implements android.content.DialogInterface.OnClickListener {
    OverwriteConfirm
       (
          android.content.Context ctx,
          String FileName
       ) {
      super(ctx);
      setIcon(android.R.drawable.ic_dialog_alert); /* doesn't work? */
      setMessage
         (
            String.format(Global.StdLocale, ctx.getString(R.string.file_exists), FileName)
         );
      setButton
         (
            android.content.DialogInterface.BUTTON_POSITIVE,
            ctx.getString(R.string.overwrite),
            this
         );
      setButton
         (
            android.content.DialogInterface.BUTTON_NEGATIVE,
            ctx.getString(R.string.cancel),
            this
         );
    }

    @Override
    public void onClick
       (
          android.content.DialogInterface TheDialog,
          int WhichButton
       ) {
      if (WhichButton == android.content.DialogInterface.BUTTON_POSITIVE) {
        ReturnResult();
      }
      dismiss();
    }
  }

  private void ReturnResult() {
    setResult
       (
          android.app.Activity.RESULT_OK,
          new android.content.Intent().setData
             (
                android.net.Uri.fromFile(new java.io.File(TheCleanedText))
             )
       );
    finish();
  }

  @Override
  public void onCreate
     (
        android.os.Bundle savedInstanceState
     ) {
    SaveAs.Current = this;
    if (Environment.getExternalStorageState().intern().equals(Environment.MEDIA_MOUNTED)) {
      MainViewGroup =
         (android.view.ViewGroup) getLayoutInflater().inflate(R.layout.save_as, null);
      setContentView(MainViewGroup);
      super.onCreate(savedInstanceState);
      final android.widget.TextView SaveAsPrompt = (android.widget.TextView) findViewById(R.id.save_as_prompt);
      SaveAsPrompt.setText
         (
            String.format(Global.StdLocale, getString(R.string.save_as_prompt), SaveWhat)
         );
      SaveAsText = (android.widget.EditText) findViewById(R.id.save_as_text);
      findViewById(R.id.save_as_confirm).setOnClickListener
         (
            new android.view.View.OnClickListener() {
              public void onClick
                 (
                    android.view.View TheView
                 ) {
                final String TheOrigText =
                   ((android.widget.TextView) SaveAsText).getText().toString();
                /* I can't seem to easily set a key listener to filter keystrokes as they
                   are entered into SaveAsText. So I filter illegal characters here instead. */
                StringBuilder Clean = new StringBuilder();
                boolean HadFunnies = false;
                for (int i = 0; i < TheOrigText.length(); ++i) {
                  char c = TheOrigText.charAt(i);
                  if (c == '.' && i == 0) {
                    HadFunnies = true;
                  } else {
                    if (c == '/') {
                      /* replace *nix path separator with harmless lookalike */
                      c = '\u2215';
                      /* u+2215 division slash looks closer to u+002f solidus
                         than u+2044 fraction slash  */
                    }
                    Clean.append(c);
                  }
                }
                TheCleanedText = Clean.toString();
                if (!HadFunnies) {
                  if (TheCleanedText.length() != 0) {
                    final String SaveFile =
                        Persistent.EnsureDirExists(getApplicationContext(), SaveWhere, TheCleanedText + FileExt);
                    if (new java.io.File (SaveFile).exists()) {
                      new OverwriteConfirm(SaveAs.this, TheCleanedText).show();
                    } else {
                      ReturnResult();
                    }
                  } else {
                    Snackbar.make(
                          findViewById(android.R.id.content),
                          String.format
                             (
                                Global.StdLocale,
                                getString(R.string.please_enter_name),
                                SaveWhat
                             ),
                          Snackbar.LENGTH_SHORT
                       ).show();
                  }
                } else {
                  /* show user cleaned text */
                  SaveAsText.setText(TheCleanedText);
                }
              } /*onClick*/
            }
         );
    } else {
      Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.no_external_storage),
            Snackbar.LENGTH_SHORT
         ).show();
      setResult(android.app.Activity.RESULT_CANCELED);
      finish();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    SaveAs.Current = null;
  } /*onDestroy*/

  @Override
  public void onPause() {
    super.onPause();
    if (Extra != null) {
      /* so it can be properly added again should the orientation change */
      MainViewGroup.removeView(Extra);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Extra != null) {
      MainViewGroup.addView
         (
            Extra,
            new android.view.ViewGroup.LayoutParams
               (
                  android.view.ViewGroup.LayoutParams.FILL_PARENT,
                  android.view.ViewGroup.LayoutParams.WRAP_CONTENT
               )
         );
    }
  }

  public static void Launch
     (
        android.app.Activity Acting,
        int RequestCode,
        String SaveWhat,
        String SaveWhere, /* directory within external storage, for overwrite checking */
        android.view.View Extra,
        String FileExt
     ) {
    if (!Reentered) {
      Reentered = true; /* until SaveAs activity terminates */
      SaveAs.SaveWhat = SaveWhat;
      SaveAs.SaveWhere = SaveWhere;
      SaveAs.Extra = Extra;
      SaveAs.FileExt = FileExt;
      Acting.startActivityForResult
         (
            new android.content.Intent(android.content.Intent.ACTION_PICK)
               .setClass(Acting, SaveAs.class),
            RequestCode
         );
    }
  }

  public static void Cleanup() {
    /* Client must call this to do explicit cleanup; I tried doing it in
       onDestroy, but of course that gets called when user rotates screen,
       which means activity context is lost. */
    SaveWhat = null;
    SaveWhere = null;
    Extra = null;
    FileExt = null;
    Reentered = false;
  }
}
