package net.obry.ti5x;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

public class PrinterLineAdapter extends RecyclerView.Adapter<PrinterLineAdapter.LineViewHolder> {

  public interface LineRenderer {
    Bitmap renderLine(int line);
  }

  private int lineCount;
  private final LineRenderer renderer;

  public PrinterLineAdapter(LineRenderer renderer) {
    this.renderer = renderer;
  }

  public void setLineCount(int count) {
    lineCount = count;
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ImageView imageView = (ImageView) LayoutInflater.from(parent.getContext())
        .inflate(R.layout.item_printer_line, parent, false);
    return new LineViewHolder(imageView);
  }

  @Override
  public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
    holder.image.setImageBitmap(renderer.renderLine(position));
  }

  @Override
  public void onViewRecycled(@NonNull LineViewHolder holder) {
    holder.image.setImageBitmap(null); // free bitmap reference
    super.onViewRecycled(holder);
  }

  @Override
  public int getItemCount() {
    return lineCount;
  }

  static class LineViewHolder extends RecyclerView.ViewHolder {
    final ImageView image;

    LineViewHolder(@NonNull ImageView itemView) {
      super(itemView);
      image = itemView;
    }
  }
}
