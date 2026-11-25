/*
    In-app label card editor for the user program

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

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import net.obry.ti5x.engine.CardDef;
import net.obry.ti5x.engine.State;

/* Creates or edits the label card of the user program (bank 0). The card is
   saved as Ti5x/Card/inapp.png with its source in Ti5x/Card/inapp.xml, and kept
   in Bank[0] so that it is shown on the calculator and saved with the program.
   Any Ti5x/Card/*.png can also be selected as the user program card. */

public class CardEditor extends BaseActivity {
  static final int SelectCardRequest = 1;

  private EditText TitleField, IdField;
  private final EditText[] PrimeFields = new EditText[CardDef.NrCells];
  private final EditText[] BaseFields = new EditText[CardDef.NrCells];
  private ImageView Preview;
  private View Note;
  private boolean Filling = false; /* setting the fields, don't update the preview */

  @Override
  protected int getLayoutResId() {
    return R.layout.card_editor;
  }

  @Override
  protected int getRootViewId() {
    return R.id.card_editor;
  }

  static void Launch
     (
        android.content.Context ctx
     ) {
    ctx.startActivity(new Intent(ctx, CardEditor.class));
  }

  @Override
  protected void onCreate
     (
        Bundle SavedInstanceState
     ) {
    super.onCreate(SavedInstanceState);
    if (Global.Calc == null) {
      /* engine gone (process restarted on this activity) */
      finish();
      return;
    }
    Preview = (ImageView) findViewById(R.id.card_preview);
    Note = findViewById(R.id.card_note);
    TitleField = (EditText) findViewById(R.id.card_title);
    IdField = (EditText) findViewById(R.id.card_id);
    IdField.setFilters
       (
          new InputFilter[] {new InputFilter.AllCaps(), new InputFilter.LengthFilter(5)}
       );
    final int[] PrimeIds =
       {R.id.card_prime_a, R.id.card_prime_b, R.id.card_prime_c, R.id.card_prime_d, R.id.card_prime_e};
    final int[] BaseIds =
       {R.id.card_base_a, R.id.card_base_b, R.id.card_base_c, R.id.card_base_d, R.id.card_base_e};
    for (int i = 0; i < CardDef.NrCells; ++i) {
      PrimeFields[i] = (EditText) findViewById(PrimeIds[i]);
      BaseFields[i] = (EditText) findViewById(BaseIds[i]);
    }

    final TextWatcher Changed =
       new TextWatcher() {
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
         }

         public void onTextChanged(CharSequence s, int start, int before, int count) {
         }

         public void afterTextChanged
            (
               Editable s
            ) {
           if (!Filling) {
             IdField.setError(null);
             UpdatePreview();
           }
         }
       };
    TitleField.addTextChangedListener(Changed);
    IdField.addTextChangedListener(Changed);
    for (int i = 0; i < CardDef.NrCells; ++i) {
      PrimeFields[i].addTextChangedListener(Changed);
      BaseFields[i].addTextChangedListener(Changed);
    }

    findViewById(R.id.card_new).setOnClickListener
       (
          new View.OnClickListener() {
            public void onClick(View v) {
              SetFields(new CardDef());
              UpdatePreview();
            }
          }
       );
    findViewById(R.id.card_select).setOnClickListener
       (
          new View.OnClickListener() {
            public void onClick(View v) {
              SelectCard();
            }
          }
       );
    findViewById(R.id.card_remove).setOnClickListener
       (
          new View.OnClickListener() {
            public void onClick(View v) {
              SetUserCard(null, null);
              Toast.makeText(CardEditor.this, R.string.card_removed, Toast.LENGTH_SHORT).show();
              finish();
            }
          }
       );
    findViewById(R.id.card_save).setOnClickListener
       (
          new View.OnClickListener() {
            public void onClick(View v) {
              SaveCard();
            }
          }
       );

    if (SavedInstanceState == null) {
      /* edit the current user program card if it was made here */
      final State.ProgBank Bank = Global.Calc.Bank[0];
      final CardDef Current = CardDef.Parse(Bank.CardSource);
      if (Current != null) {
        SetFields(Current);
        UpdatePreview();
      } else if (Bank.Card != null) {
        /* only the image is known, show it until a field is changed */
        Preview.setImageBitmap(BitmapFactory.decodeByteArray(Bank.Card, 0, Bank.Card.length));
        Note.setVisibility(View.VISIBLE);
      } else {
        UpdatePreview();
      }
    } else {
      /* fields are restored by the framework */
      UpdatePreview();
    }
  }

  private void SetFields
     (
        CardDef Def
     ) {
    Filling = true;
    TitleField.setText(Def.Title);
    IdField.setText(Def.Id);
    IdField.setError(null);
    for (int i = 0; i < CardDef.NrCells; ++i) {
      PrimeFields[i].setText(Def.Prime[i]);
      BaseFields[i].setText(Def.Base[i]);
    }
    Filling = false;
  }

  private CardDef GetFields() {
    final CardDef Def = new CardDef();
    Def.Title = TitleField.getText().toString().trim();
    Def.Id = IdField.getText().toString().trim();
    for (int i = 0; i < CardDef.NrCells; ++i) {
      Def.Prime[i] = PrimeFields[i].getText().toString().trim();
      Def.Base[i] = BaseFields[i].getText().toString().trim();
    }
    return Def;
  }

  private void UpdatePreview() {
    Note.setVisibility(View.GONE);
    Preview.setImageBitmap(CardRenderer.Render(GetFields()));
  }

  private static void SetUserCard
     (
        byte[] Card,
        byte[] CardSource
     ) {
    /* make this the card of the user program, shown when Pgm 00 is selected
       and saved with the program */
    final State Calc = Global.Calc;
    Calc.Bank[0].Card = Card;
    Calc.Bank[0].CardSource = CardSource;
    if (Calc.CurBank == 0 && Global.Label != null) {
      Global.Label.SetHelp(Card, Calc.Bank[0].Help);
    }
  }

  private static boolean WriteCardFile
     (
        String FileName,
        byte[] Contents
     ) {
    /* remove the old file first, some document providers don't truncate
       an existing file when writing it */
    Global.DataStorage.deleteFile(Persistent.CardDir, FileName);
    final java.io.OutputStream Out =
       Global.DataStorage.getOutputStream(Persistent.CardDir, FileName, false);
    if (Out == null)
      return false;
    try {
      Out.write(Contents);
      Out.close();
      return true;
    } catch (java.io.IOException Failed) {
      return false;
    }
  }

  private static byte[] ReadCardFile
     (
        String FileName
     ) {
    /* returns the contents of the file in Ti5x/Card, null if not found */
    final java.io.InputStream In = Global.DataStorage.getInputStream(Persistent.CardDir, FileName);
    if (In == null)
      return null;
    try {
      try {
        return net.obry.ti5x.engine.ProgramFiles.ReadAll(In);
      } finally {
        In.close();
      }
    } catch (java.io.IOException Failed) {
      return null;
    }
  }

  private void SaveCard() {
    final CardDef Def = GetFields();
    if (!CardDef.ValidId(Def.Id)) {
      IdField.setError(getString(R.string.card_invalid_id));
      IdField.requestFocus();
      return;
    }
    final byte[] Png = CardRenderer.ToPng(CardRenderer.Render(Def));
    final byte[] Source = Def.ToXml();
    SetUserCard(Png, Source);
    final String PngName = Persistent.InAppCard + Persistent.CardImageExt;
    final boolean Saved =
       WriteCardFile(PngName, Png)
          && WriteCardFile(Persistent.InAppCard + Persistent.CardSourceExt, Source);
    Toast.makeText
       (
          this,
          String.format
             (
                Global.StdLocale,
                getString(Saved ? R.string.card_saved : R.string.card_save_error),
                Persistent.CardDir + "/" + PngName
             ),
          Saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
       ).show();
    finish();
  }

  private void SelectCard() {
    Picker.Launch
       (
          this,
          getString(R.string.select_card),
          SelectCardRequest,
          null,
          new String[] {Persistent.CardDir},
          new Picker.PickerAltList[]
             {
                new Picker.PickerAltList
                   (
                      0,
                      getString(R.string.card_pick_prompt),
                      getString(R.string.no_cards),
                      new String[] {Persistent.CardImageExt},
                      null
                   ),
             }
       );
  }

  @Override
  public void onActivityResult
     (
        int RequestCode,
        int ResultCode,
        Intent Data
     ) {
    super.onActivityResult(RequestCode, ResultCode, Data);
    Picker.Cleanup();
    if (RequestCode != SelectCardRequest || ResultCode != RESULT_OK || Data == null
        || Data.getData() == null || Data.getData().getPath() == null)
      return;
    final String FileName = new java.io.File(Data.getData().getPath()).getName();
    final byte[] Png = ReadCardFile(FileName);
    final Bitmap Image = Png != null ? BitmapFactory.decodeByteArray(Png, 0, Png.length) : null;
    if (Image == null) {
      Toast.makeText
         (
            this,
            String.format(Global.StdLocale, getString(R.string.card_load_error), FileName),
            Toast.LENGTH_LONG
         ).show();
      return;
    }
    /* its source, if made by this editor, so it can be edited again */
    final String BaseName =
       FileName.substring(0, FileName.length() - Persistent.CardImageExt.length());
    byte[] Source = ReadCardFile(BaseName + Persistent.CardSourceExt);
    if (CardDef.Parse(Source) == null) {
      Source = null;
    }
    SetUserCard(Png, Source);
    Toast.makeText
       (
          this,
          String.format(Global.StdLocale, getString(R.string.card_loaded), BaseName),
          Toast.LENGTH_SHORT
       ).show();
    finish();
  }
}
