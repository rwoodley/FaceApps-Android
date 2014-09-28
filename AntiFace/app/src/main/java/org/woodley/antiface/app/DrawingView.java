package org.woodley.antiface.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

// from: http://android-er.blogspot.com/2012/04/android-4-face-detection-display.html
// draws green square on surfaceView.
public class DrawingView extends View {

    boolean _haveFace;
    Paint _greenPaint;
    Paint _redPaint;
    int _x, _y, _w, _h;
    int _x1, _y1, _w1, _h1;

    public DrawingView(Context context) {
        super(context);
        _haveFace = false;
        _greenPaint = new Paint();
        _greenPaint.setColor(Color.GREEN);
        _greenPaint.setStyle(Paint.Style.STROKE);
        _greenPaint.setStrokeWidth(2);
        _redPaint = new Paint();
        _redPaint.setColor(Color.RED);
        _redPaint.setStyle(Paint.Style.STROKE);
        _redPaint.setStrokeWidth(2);
    }

    public void setHaveFace(boolean h){
        _haveFace = h;
    }
    public void setFaceRect(Rect rect, boolean haveFace, Rect outRect, int surfaceViewWidth) {
        _haveFace = haveFace;
//        _haveFace = MainActivity.computeBounds(rect, outRect, _surfaceView.getWidth(), _surfaceView.getHeight(), _dontCutOffChinsFactor);
        if (_haveFace) {
            _haveFace = true;
            _x = surfaceViewWidth - outRect.left - outRect.width();
            _y = outRect.top;
            _w = outRect.width();
            _h = outRect.height();

//            MainActivity.computeBounds(rect, outRect, _surfaceView.getWidth(), _surfaceView.getHeight(), 0);
//            _x1 = _surfaceView.getWidth() - outRect.left - outRect.width();
//            _y1 = outRect.top;
//            _w1 = outRect.width();
//            _h1 = outRect.height();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!_haveFace) return;
        canvas.drawRect(_x, _y, _x + _w, _y + _h, _greenPaint);
        canvas.drawRect(_x1, _y1, _x1 + _w1, _y1 + _h1, _redPaint);
    }
}
