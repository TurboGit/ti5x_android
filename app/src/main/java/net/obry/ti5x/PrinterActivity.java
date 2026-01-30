package net.obry.ti5x;

import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.Button;
import android.widget.ToggleButton;
import android.support.design.widget.Snackbar;

public class PrinterActivity extends android.app.Activity {

  private PrinterLineAdapter adapter;
  private Button btnTop;
  private Button btnClear;
  private Button btnTearOff;
  private ToggleButton btnTrace;
  private Button btnBottom;
  private RecyclerView recycler;
  private static int savedScrollPosition = -1;

  private void clearPrinterView() {
    adapter.setLineCount(0);
    updatePrinterView(false);
  }
  private void savePaperPart(int fromLine, int toLine)
  {
    int width = recycler.getWidth(); // full width of printer view
    int totalHeight = 0;

    // Calculate total height by summing all line bitmaps
    for (int i = fromLine; i < toLine; i++) {
      Bitmap lineBitmap = renderPrinterLine(i);
      totalHeight += lineBitmap.getHeight();
    }

    Bitmap finalBitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(finalBitmap);
    canvas.drawColor(Color.WHITE);

    int y = 0;

    for (int i = fromLine; i < toLine; i++) {
      Bitmap lineBitmap = renderPrinterLine(i);
      canvas.drawBitmap(lineBitmap, 0, y, null);
      y += lineBitmap.getHeight();
    }

    StringBuilder mes = new StringBuilder();
    boolean ok = Global.Print.SavePaper(finalBitmap, mes);
    if(ok) {
      Snackbar.make(
          findViewById(android.R.id.content),
          mes.toString(),
          Snackbar.LENGTH_LONG
      ).show();
    } else {
      new AlertDialog.Builder(PrinterActivity.this)
          .setMessage(mes.toString())
          .setPositiveButton("OK", null)
          .show();
    }
  }
  private void tearOffPaper() {
    int lineCount = adapter.getItemCount();
    if (lineCount == 0) return;

    final int maxLine = 200;
    // tear off paper of 200 lines each

    int fromLine = 1;
    // first line is empty
    int toLine = 0;

    while(toLine < lineCount) {
      toLine = fromLine + maxLine;
      if(toLine > lineCount) {
        toLine = lineCount;
      }
      savePaperPart(fromLine, toLine);
      fromLine = toLine;
    }
  }

  private void updatePrinterView(boolean scrollToPos) {
    final int lineCount = adapter.getItemCount();

    adapter.notifyDataSetChanged();

    boolean hasData = lineCount > 0;
    btnTop.setEnabled(hasData);
    btnBottom.setEnabled(hasData);
    btnClear.setEnabled(hasData);
    btnTearOff.setEnabled(hasData);
    btnTrace.setChecked(Global.Calc.TracePrintActivated);

    if (scrollToPos && hasData) {
      if (savedScrollPosition == -1) {
        recycler.scrollToPosition(lineCount - 1);
      } else {
        recycler.scrollToPosition(savedScrollPosition);
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_printer);

    recycler = findViewById(R.id.printerRecycler);
    recycler.setLayoutManager(new LinearLayoutManager(this));
    recycler.setItemAnimator(null); // disable animations

    btnTop = findViewById(R.id.btnTop);
    btnClear = findViewById(R.id.btnClear);
    btnBottom = findViewById(R.id.btnBottom);
    btnTearOff = findViewById(R.id.btnTearOff);
    btnTrace = findViewById(R.id.btnTrace);

    btnTop.setOnClickListener(v -> {
      if (adapter.getItemCount() > 0) {
         recycler.scrollToPosition(0);
     }
    });

    btnClear.setOnClickListener(v -> {
      clearPrinterView();
    });

    btnTearOff.setOnClickListener(v -> {
      tearOffPaper();
    });

    btnTrace.setOnClickListener(v -> {
      Global.Calc.TracePrintActivated = !Global.Calc.TracePrintActivated;
      btnTrace.setChecked(Global.Calc.TracePrintActivated);
    });

    btnBottom.setOnClickListener(v -> {
        final int count = adapter.getItemCount();
        if (count > 0) {
            recycler.scrollToPosition(count - 1);
        }
    });

    // Initialize adapter with simple line renderer
    adapter = new PrinterLineAdapter(this::renderPrinterLine);
    recycler.setAdapter(adapter);

    adapter.setLineCount(Global.Print.Content.size()); // for testing: 500 lines
    updatePrinterView(true);
  }
  private boolean isAtBottom() {
    LinearLayoutManager lm = (LinearLayoutManager) recycler.getLayoutManager();
    if (lm == null) return true;
    final int lastVisible = lm.findLastCompletelyVisibleItemPosition();
    final int itemCount = adapter.getItemCount();
    return itemCount == 0 || lastVisible >= itemCount - 1;
  }
  @Override
  public void onPause() {
    super.onPause();
    LinearLayoutManager lm = (LinearLayoutManager) recycler.getLayoutManager();

    if (lm != null && !isAtBottom()) {
      savedScrollPosition = lm.findFirstVisibleItemPosition();
    } else {
      savedScrollPosition = -1;
    }
  }
  @Override
  protected void onResume() {
    super.onResume();
    if(savedScrollPosition != -1) {
      recycler.scrollToPosition(savedScrollPosition);
    }
  }

  private Bitmap renderPrinterLine(int line) {
    Bitmap bitmap = Bitmap.createBitmap(
        Global.Print.PaperWidth,
        Global.Print.LineHeight,
        Bitmap.Config.ALPHA_8);
    bitmap.prepareToDraw();

    final int[] lineContent = Global.Print.getLineContent(line);
    if(lineContent.length > 0) {
      bitmap.setPixels(
          lineContent,
          0,
          Global.Print.PaperWidth,
          Global.Print.PaddingWidth,
          0,
          Global.Print.PaperWidth - Global.Print.PaddingWidth,
          Global.Print.LineHeight);
    }
    return bitmap;
  }
}
