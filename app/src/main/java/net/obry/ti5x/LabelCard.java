/*
    Label-card display area

    Copyright 2011 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

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

import android.graphics.RectF;
import android.support.design.widget.Snackbar;

class LabelCard extends android.view.View {
  private android.graphics.Bitmap CardImage, NewCardImage;
  private byte[] Help;
  private final int Dark, LEDOff;

  private final float SlideDuration = 0.5f; /* seconds */

  public LabelCard
     (
        android.content.Context TheContext,
        android.util.AttributeSet TheAttributes
     ) {
    super(TheContext, TheAttributes);
    final android.content.Context ctx = TheContext;
    final android.content.res.Resources Res = ctx.getResources();
    Dark = Res.getColor(R.color.dark);
    LEDOff = Res.getColor(R.color.led_off);
    CardImage = null;
    Help = null;
    setOnTouchListener
       (
          new android.view.View.OnTouchListener() {
            public boolean onTouch
               (
                  android.view.View TheView,
                  android.view.MotionEvent TheEvent
               ) {
              boolean Handled = false;
              if (!Global.BGTaskInProgress()) {
                switch (TheEvent.getAction()) {
                  case android.view.MotionEvent.ACTION_DOWN:
                    TheView.performClick();
                  case android.view.MotionEvent.ACTION_MOVE:
                    if (Help != null) {
                      final android.content.Intent ShowHelp =
                         new android.content.Intent(android.content.Intent.ACTION_VIEW);
                      ShowHelp.putExtra(net.obry.ti5x.Help.ContentID, Help);
                      ShowHelp.setClass(ctx, Help.class);
                      ctx.startActivity(ShowHelp);
                    } else {
                      Snackbar.make(
                            TheView,
                            ctx.getString(R.string.no_prog_help),
                            Snackbar.LENGTH_SHORT
                         ).show();
                    }
                    Handled = true;
                    break;
                  case android.view.MotionEvent.ACTION_UP:
                    TheView.performClick();
                  case android.view.MotionEvent.ACTION_CANCEL:
                    Handled = true;
                    break;
                }
              }
              return Handled;
            }
          }
       );
  }

  private void SlideInNewCard() {
    clearAnimation();
    CardImage = NewCardImage;
    if (CardImage != null) {
      final android.view.animation.Animation SlideIn =
         new android.view.animation.TranslateAnimation
            (
               getWidth(),
               0.0f,
               0.0f,
               0.0f
            );
      SlideIn.setDuration((int) (SlideDuration * 1000));
      startAnimation(SlideIn);
    } else {
      invalidate(); /* make sure red overlap is properly drawn */
    }
  }

  private void SlideOutOldCard() {
    clearAnimation(); /* if any */
    final android.view.animation.Animation SlideOut =
       new android.view.animation.TranslateAnimation
          (
             0.0f,
             getWidth(),
             0.0f,
             0.0f
          );
    SlideOut.setDuration((int) (SlideDuration * 1000));
    SlideOut.setAnimationListener
       (
          new android.view.animation.Animation.AnimationListener() {

            public void onAnimationStart
               (
                  android.view.animation.Animation TheAnimation
               ) {
            }

            public void onAnimationEnd
               (
                  android.view.animation.Animation TheAnimation
               ) {
              SlideInNewCard();
            }

            public void onAnimationRepeat
               (
                  android.view.animation.Animation TheAnimation
               ) {
            }
          }
       );
    startAnimation(SlideOut);
  }

  public void SetHelp
     (
        android.graphics.Bitmap NewCardImage,
        byte[] NewHelp
     ) {
    this.NewCardImage = NewCardImage;
    if (CardImage != null) {
      SlideOutOldCard();
    } else if (NewCardImage != null) {
      SlideInNewCard();
    }
    Help = NewHelp;
    /* invalidate(); */ /* leave it to animation */
  }

  @Override
  public void onDraw
     (
        android.graphics.Canvas Draw
     ) {
    super.onDraw(Draw);
    final android.graphics.PointF CardSize =
       new android.graphics.PointF(getWidth(), getHeight());
    Draw.drawRect
       (
          new RectF(0.0f, 0.0f, CardSize.x, CardSize.y),
          GraphicsUseful.FillWithColor(Dark)
       );
    if (CardImage != null) {
      final android.graphics.Matrix ImageMap = new android.graphics.Matrix();
      ImageMap.setRectToRect
         (
            new RectF
               (
                  0,
                  0,
                  CardImage.getWidth() * 70.0f / 72.0f,
                      /* right-hand edge of card disappears in entry slot */
                  CardImage.getHeight()
               ),
            new RectF(0, 0, CardSize.x, CardSize.y),
            android.graphics.Matrix.ScaleToFit.CENTER
         );
      final android.graphics.Paint DrawBits = new android.graphics.Paint();
      DrawBits.setFilterBitmap(true);
      Draw.drawBitmap
         (
            CardImage,
            ImageMap,
            DrawBits
         );
    }
    Draw.drawRect /* on top of CardImage */
       (
          new RectF(-CardSize.x, 0.0f, CardSize.x, CardSize.y * 0.25f),
          /* extend red overlap for slide animation--note this also
             requires clipChildren=false in parent layout */
          GraphicsUseful.FillWithColor(LEDOff)
       );
  }
}