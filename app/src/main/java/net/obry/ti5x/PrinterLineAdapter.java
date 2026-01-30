/*
    Support for line in printer view (RecyclerView)

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

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;

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
