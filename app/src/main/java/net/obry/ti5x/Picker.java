/*
    let the user choose a program or library to load

    Copyright 2011      Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2016-2018 Steven Zoppi <about-ti5x@zoppi.org>.

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

// import android.support.annotation.NonNull;
// import android.support.design.widget.Snackbar;
import android.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;
import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;

public class Picker extends BaseActivity {

  // index for the selection either prog or libraries in the menu
  public static final String AltIndexID = "net.obry.ti5x.PickedIndex";

  // index of the SpecialItem, in this case the selected library 0:Master, 1:xyz
  public static final String SpeIndexID = "net.obry.ti5x.SpecialIndex";

  // index of the first Builtin item in the list
  public static final String BuiltinIndexID = "net.obry.ti5x.BuiltinIndex";

  // index of the first user's item in the list
  public static final String UserProgIndexID = "net.obry.ti5x.UserProgIndex";

  private static boolean Reentered = false; /* sanity check */
  public static Picker Current = null;

  @Override
  protected int getLayoutResId() { return R.layout.picker; }

  @Override
  protected int getRootViewId() { return R.id.keyboard_main_layout; }

  static class PickerAltList {
    /* defining alternative lists of files for picker to display */
    final int RadioButtonID;
    final String Prompt;
    final String NoneFound;
    final String[] FileExts; /* list of extensions to match, or null to match all files */
    final String[] SpecialItem; /* special item to add to list, null for none */

    PickerAltList
       (
          int RadioButtonID,
          String Prompt,
          String NoneFound,
          String[] FileExts,
          String[] SpecialItem
       ) {
      this.RadioButtonID = RadioButtonID;
      this.Prompt = Prompt;
      this.NoneFound = NoneFound;
      this.FileExts = FileExts;
      this.SpecialItem = SpecialItem;
    }
  }

  private static String SelectLabel = null;
  private static android.view.View Extra = null;
  private static String[] LookIn;
  private static PickerAltList[] AltLists = null;

  private android.view.ViewGroup MainViewGroup;
  private android.widget.TextView PromptView;
  private android.widget.ListView PickerListView;
  private SelectedItemAdapter PickerList;
  private int SelectedAlt; /* index into AltLists */
  private int FirstBuiltinIdx = 0;
  private int FirstUserProgIdx = 0;

  public static class PickerItem {
    final String FullPath;
    final String DisplayName;
    boolean Selected;

    PickerItem
       (
          String FullPath,
          String DisplayName
       ) {
      this.FullPath = FullPath;
      this.DisplayName = DisplayName;
      this.Selected = false;
    }

    public String toString() {
      return
         DisplayName != null ?
            DisplayName
            :
            new java.io.File(FullPath).getName();
    }
  }

  class DeleteConfirm
     extends android.app.AlertDialog
     implements android.content.DialogInterface.OnClickListener {
    final PickerItem TheFile;

    DeleteConfirm
       (
          android.content.Context ctx,
          PickerItem TheFile
       ) {
      super(ctx);
      this.TheFile = TheFile;
      setIcon(android.R.drawable.ic_delete); /* doesn't work? */
      setMessage
         (
            String.format
               (
                  Global.StdLocale,
                  ctx.getString(R.string.query_delete),
                  TheFile.toString()
               )
         );
      setButton
         (
            android.content.DialogInterface.BUTTON_POSITIVE,
            ctx.getString(R.string.delete),
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
        boolean Deleted;
        try {
          new java.io.File(TheFile.FullPath).delete();
          Deleted = true;
        } catch (SecurityException AccessDenied) {
          Snackbar.make(
               findViewById(android.R.id.content),
                String.format
                   (
                      Global.StdLocale,
                      getString(R.string.file_delete_error),
                      AccessDenied.toString()
                   ),
                Snackbar.LENGTH_LONG
             ).show();
          Deleted = false;
        }
        if (Deleted) {
          Snackbar.make(
               findViewById(android.R.id.content),
                String.format
                   (
                      Global.StdLocale,
                      getString(R.string.file_deleted),
                      TheFile.toString()
                   ),
                Snackbar.LENGTH_SHORT
             ).show();
          PopulatePickerList(SelectedAlt);
        }
      }
      dismiss();
    }
  }

  class SelectedItemAdapter extends android.widget.ArrayAdapter<PickerItem> {
    final int ResID;
    final android.view.LayoutInflater TemplateInflater;
    PickerItem CurSelected;
    android.widget.RadioButton LastChecked;

    class OnSetCheck implements android.view.View.OnClickListener {
      final PickerItem MyItem;

      OnSetCheck
         (
            PickerItem TheItem
         ) {
        MyItem = TheItem;
      }

      public void onClick
         (
            android.view.View TheView
         ) {
        if (MyItem != CurSelected) {
                  /* only allow one item to be selected at a time */
          if (CurSelected != null) {
            CurSelected.Selected = false;
            LastChecked.setChecked(false);
          }
          LastChecked =
             TheView instanceof android.widget.RadioButton ?
                (android.widget.RadioButton) TheView
                :
                (android.widget.RadioButton)
                   TheView.findViewById(R.id.file_item_checked);
          CurSelected = MyItem;
          MyItem.Selected = true;
          LastChecked.setChecked(true);
        }
      }
    }

    SelectedItemAdapter
       (
          android.content.Context TheContext,
          int ResID,
          android.view.LayoutInflater TemplateInflater
       ) {
      super(TheContext, ResID);
      this.ResID = ResID;
      this.TemplateInflater = TemplateInflater;
      CurSelected = null;
      LastChecked = null;
    }

    @NonNull
    @Override
    public android.view.View getView
       (
          int Position,
          android.view.View ReuseView,
          @NonNull android.view.ViewGroup Parent
       ) {
      android.view.View TheView = ReuseView;
      if (TheView == null) {
        TheView = TemplateInflater.inflate(ResID, null);
      }
      final PickerItem ThisItem = this.getItem(Position);
      ((android.widget.TextView) TheView.findViewById(R.id.select_file_name))
         .setText(ThisItem.toString());
      android.widget.RadioButton ThisChecked =
         (android.widget.RadioButton) TheView.findViewById(R.id.file_item_checked);
      ThisChecked.setChecked(ThisItem.Selected);
      final OnSetCheck ThisSetCheck = new OnSetCheck(ThisItem);
      ThisChecked.setOnClickListener(ThisSetCheck);
      /* otherwise radio button can get checked but I don't notice */
      TheView.setOnClickListener(ThisSetCheck);
      TheView.setOnLongClickListener
         (
            new android.view.View.OnLongClickListener() {
              public boolean onLongClick
                 (
                    android.view.View TheView
                 ) {
                if (ThisItem.FullPath != null) {
                  /* cannot delete built-in item */
                  new DeleteConfirm(Picker.this, ThisItem).show();
                }
                return true;
              } /*onLongClick*/
            }
         );
      return TheView;
    }
  }

  class OnSelectCategory implements android.view.View.OnClickListener {
    /* handler for radio buttons selecting which category of files to display */
    final int SelectAlt;

    OnSelectCategory
       (
          int SelectAlt
       ) {
      this.SelectAlt = SelectAlt;
    }

    public void onClick
       (
          android.view.View TheView
       ) {
      PopulatePickerList(SelectAlt);
    }
  }

  private void PopulatePickerList
     (
        int NewAlt /* index into AltLists */
     ) {
    SelectedAlt = NewAlt;
    final PickerAltList Alt = AltLists[SelectedAlt];
    String InaccessibleFolders = "";
    PickerList.clear();

    try {
      for (String Here : LookIn) {
        final String ThisDirName = Persistent.EnsureDirExists(this, Here, "");
        final java.io.File ThisDir = new java.io.File(ThisDirName);
        /*
         * We need to ensure that the lack of permissions doesn't harm us
         * if the user didn't grant them ... this logic ensures that no error
         * is thrown due to incorrect permissions.
         */
        if (!ThisDir.canRead()) {
          if (InaccessibleFolders.length() > 0) {
            InaccessibleFolders = InaccessibleFolders.concat(", ");
          }
          InaccessibleFolders = InaccessibleFolders.concat(ThisDirName);
        } else {
          /*
           * This segment iterates on all of the files contained
           * withing the folder context.
           */
          File ourFiles[] = ThisDir.listFiles();
          Arrays.sort(ourFiles);

          for (java.io.File Item : ourFiles) {
            boolean MatchesExt;
            String DisplayName = "";
            final String ItemName = Item.getName();
            DisplayName = ItemName;

            if (Alt.FileExts != null) {
              for (int i = 0; ; ) {
                if (i == Alt.FileExts.length) {
                  MatchesExt = false;
                  break;
                }
                if (ItemName.endsWith(Alt.FileExts[i])) {
                  MatchesExt = true;
                  DisplayName = ItemName.substring(0,
                     ItemName.length() - Alt.FileExts[i].length());
                  break;
                }
                ++i;
              }
            } else {
              /* match all files */
              MatchesExt = true;
            }
            if (MatchesExt) {
              PickerList.add(new PickerItem(Item.getAbsolutePath(), DisplayName));
            }
          }
        }
      }

      FirstBuiltinIdx = 0; // PickerList.getCount();

      if (Alt.SpecialItem == null) {
        FirstUserProgIdx = 0;
      }
      else {
        FirstUserProgIdx = Alt.SpecialItem.length;
      }

      if (Alt.SpecialItem != null) {
        for (int i = Alt.SpecialItem.length - 1; i >= 0; i--)
          PickerList.insert(new PickerItem(null, Alt.SpecialItem[i]),0);
      }

      PromptView.setText
         (
            PickerList.getCount() != 0 ?
               Alt.Prompt
               :
               Alt.NoneFound
         );
      PickerList.notifyDataSetChanged();
    } catch (RuntimeException Failed) {
      Snackbar.make(
            findViewById(android.R.id.content),
            String.format
               (
                  Global.StdLocale,
                  getString(R.string.application_error),
                  Failed.toString()
               ),
            Snackbar.LENGTH_LONG
         ).show();
    }
    if (InaccessibleFolders.length() > 0) {
      Snackbar.make(
            findViewById(android.R.id.content),
            String.format
               (
                  Global.StdLocale,
                  getString(R.string.folder_unreadable),
                  InaccessibleFolders
               ),
            Snackbar.LENGTH_SHORT
         ).show();
    }
  }

  @Override
  public void onCreate
     (
        android.os.Bundle savedInstanceState
     ) {
    super.onCreate(savedInstanceState);
    Picker.Current = this;
    MainViewGroup = (android.view.ViewGroup) findViewById(R.id.keyboard_main_layout);
    /* ExtraViewGroup = (android.view.ViewGroup)MainViewGroup.findViewById(R.id.picker_extra); */
    /* doesn't work -- things added here don't show up */
    PromptView = (android.widget.TextView) findViewById(R.id.picker_prompt);
    PickerList = new SelectedItemAdapter(this, R.layout.picker_item, getLayoutInflater());
    PickerListView = (android.widget.ListView) findViewById(R.id.prog_list);
    PickerListView.setAdapter(PickerList);
    final android.widget.Button SelectButton =
       (android.widget.Button) findViewById(R.id.prog_select);
    SelectButton.setText(SelectLabel);

    SelectButton.setOnClickListener
       (
          new android.view.View.OnClickListener() {
            public void onClick
               (
                  android.view.View TheView
               ) {
              PickerItem Selected = null;
              int AltIdx = 0;
              for (int i = 0; ; ) {
                if (i == PickerList.getCount())
                  break;
                final PickerItem ThisItem =
                   (PickerItem) PickerListView.getItemAtPosition(i);
                if (ThisItem.Selected) {
                  Selected = ThisItem;
                  AltIdx = i;
                  break;
                }
                ++i;
              }
              if (Selected != null) {
                setResult
                   (
                      android.app.Activity.RESULT_OK,
                      new android.content.Intent()
                         .setData
                            (
                               android.net.Uri.fromFile
                                  (
                                     new java.io.File
                                        (
                                           Selected.FullPath != null ?
                                              Selected.FullPath
                                              :
                                              ""
                                        )
                                  )
                            )
                         .putExtra(AltIndexID, SelectedAlt)
                         .putExtra(SpeIndexID, AltIdx)
                         .putExtra(BuiltinIndexID, FirstBuiltinIdx)
                         .putExtra(UserProgIndexID, FirstUserProgIdx)
                   );
                finish();
              }
            } /*onClick*/
          }
       );
    SelectedAlt = 0;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Picker.Current = null;
  }

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
      for (int i = 0; i < AltLists.length; ++i) {
        final PickerAltList ThisAlt = AltLists[i];
        if (ThisAlt.RadioButtonID != 0) {
          final android.widget.RadioButton SelectThis =
             (android.widget.RadioButton) Extra.findViewById(ThisAlt.RadioButtonID);
          SelectThis.setChecked(i == SelectedAlt);
          SelectThis.setOnClickListener(new OnSelectCategory(i));
        }
      }
    }
    PopulatePickerList(SelectedAlt);
  }

  public static void Launch
     (
        android.app.Activity Acting,
        String SelectLabel,
        int RequestCode,
        android.view.View Extra,
        String[] LookIn, /* array of names of subdirectories within external storage */
        PickerAltList[] AltLists
     ) {
    if (!Reentered) {
      Reentered = true; /* until Picker activity terminates */
      Picker.SelectLabel = SelectLabel;
      Picker.Extra = Extra;
      Picker.LookIn = LookIn;
      Picker.AltLists = AltLists;
      Acting.startActivityForResult
         (
            new android.content.Intent(android.content.Intent.ACTION_PICK)
               .setClass(Acting, Picker.class),
            RequestCode
         );
    } else {
      /* can happen if user gets impatient and selects from menu twice, just ignore */
    }
  }

  public static void Cleanup() {
    /* Client must call this to do explicit cleanup; I tried doing it in
       onDestroy, but of course that gets called when user rotates screen,
       which means picker context is lost. */
    Extra = null;
    LookIn = null;
    AltLists = null;
    Reentered = false;
  }
}
