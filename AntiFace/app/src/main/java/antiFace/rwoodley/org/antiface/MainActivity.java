package antiFace.rwoodley.org.antiface;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;



public class MainActivity extends Activity {
    private static final String    TAG                 = "MainActivity";

    private byte[] _pictureData;
    private int _pictureDataW;
    private int _pictureDataH;
    private Object processorLocker = new Object();
    private Camera _Camera = null;
    DrawingView _drawingView;

    private SurfaceView _surfaceView;
    ImageView _imageView = null;
    private Handler _processImageHandler;
    private Bitmap _postProcessedBmp;
    private boolean _firstTime = true;  // first time this instance.
    private boolean _backgroundThreadShouldRun = true;
    private MainActivity _that;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            _that = this;

            // landscape mode is the only thing that works out of the box for camera preview it seems.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            Log.e("onCreate", "Density is " + metrics.density);

            _processImageHandler = new Handler();

            _surfaceView = (SurfaceView) findViewById(R.id.cameraSurface);
            _surfaceView.getHolder().addCallback(_surfaceHolderCallback);
            //addContentView(new DrawingView(this), new ActionBar.LayoutParams(320,240));

            _imageView = (ImageView) findViewById(R.id.processedImage);
            Log.w("onCreate", "imageView WxH = " + _imageView.getWidth() + "," + _imageView.getHeight());
            ViewTreeObserver vto = _imageView.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (_firstTime) {
                        Log.w("onCreate", "_imageView WxH = " + _imageView.getWidth() + "," + _imageView.getHeight());
                        initBitmap();
                        _drawingView = new DrawingView(_that);
                        FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout);
                        frmLayout.addView(_drawingView);
                        Log.e(TAG, "drawing view = " + _drawingView.getLeft() + "," + _drawingView.getTop());
                    }
                    _firstTime = false;
                }
            });
        } catch (Exception exception) {
            Log.e(TAG, "Error in onCreate()", exception);
        }
    }
    SurfaceHolder.Callback _surfaceHolderCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.w("_surfaceView.getHolder()", "-----surfaceDestroyed");
            try {
                _Camera.stopPreview();
                _Camera.stopFaceDetection();
            }
            catch (Exception e) {
            }
            finally {
                _Camera.release();
                _Camera = null;
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.w("_surfaceView.getHolder()", "-----surfaceCreated");
            try {
                _Camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                _Camera.setPreviewDisplay(holder);
            } catch (IOException exception) {
                _Camera.release();
                _Camera = null;
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            Log.w("_surfaceView.getHolder()", "-----surfaceChanged");
            _Camera.setFaceDetectionListener(faceDetectionListener);
            _Camera.startPreview();
            _Camera.startFaceDetection();
            _Camera.takePicture(null, null, _PictureCallback);
        }
    };
    private static boolean computeBounds(Rect in, Rect outr, int width, int height) {
        int x = (int) (((float) in.left + 1000.0)/2000.0 * width);
        int w = (int) ((float) in.width()/2000.0 * width);
        int y = (int) (((float) in.left + 1000.0)/2000.0 * height);
        int h = (int) ((float) in.height()/2000.0 * height);
        // make it 30% larger so we don't cut off chins
        int deltaw = (int) (w *.3);
        int deltah = (int) (h *.3);
        w += deltaw;
        h += deltah;
        x -= deltaw/2;
        y -= deltah/2;
        if (x < 0 || y < 0 || x+w > width || y + h > height) return false;
        outr.left = x;
        outr.top = y;
        outr.right = x+w;
        outr.bottom = y+h;
        return true;
    }
    private Camera.FaceDetectionListener faceDetectionListener = new Camera.FaceDetectionListener() {

        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            Log.w("onFaceDetection", "Found " + faces.length + " faces.");
            if (faces.length == 0) return;
            Bitmap cameraBmp = getBitmapFromPictureData();
            if (cameraBmp == null) {
                Log.e("onFaceDetection", "null bitmap");
                return;
            }
            Face face = faces[0];

            _drawingView.setFaceRect(face.rect);
            _drawingView.invalidate();

            Rect outRect = new Rect();
            boolean stat = computeBounds(face.rect, outRect, _pictureDataW, _pictureDataH);
            if (!stat) return;
            Log.w("foundFace", "before x,y,w,h = " + face.rect.left +", " + face.rect.top +", " + face.rect.width() +", " + face.rect.height());
            Log.w("foundFace", "after x,y,w,h = " + outRect.left + "," + outRect.top + "," + outRect.width() + "," + outRect.height());

            Matrix m = new Matrix();
            m.preScale(-1, 1);
//            Bitmap dst = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);

            Bitmap bmp = Bitmap.createBitmap(cameraBmp, outRect.left, outRect.top, outRect.width(), outRect.height(), m, false);

            _imageView.setImageBitmap(bmp);
        }
    };
    private void initBitmap() {
        Log.w("initBitmap 1", "_imageView WxH = " + _imageView.getWidth() + "," + _imageView.getHeight());
        Bitmap postProcessedBmp = Bitmap.createBitmap(_imageView.getWidth(), _imageView.getHeight(), Bitmap.Config.RGB_565);
        Log.w(TAG, "******in initBitmap(),w = " + _imageView.getWidth() + ", h = " + _imageView.getHeight());
        for(int i = 0; i < postProcessedBmp.getHeight(); i++){
            for(int j = 0; j < postProcessedBmp.getWidth(); j++){
                int pixel = postProcessedBmp.getPixel(j, i);
                if(i*j%5==0){
                    postProcessedBmp.setPixel(j, i, Color.WHITE);
                }else{
                    postProcessedBmp.setPixel(j, i, Color.BLACK);
                }
            }
        }
        _imageView.setImageBitmap(postProcessedBmp);
        Log.w("initBitmap 2", "_imageView WxH = " + _imageView.getWidth() + "," + _imageView.getHeight());
        Log.w(TAG, "******in initBitmap(),w = " + _imageView.getWidth() + ", h = " + _imageView.getHeight());
    }
    public void onStop() {
        super.onStop();  // Always call the superclass method first
        Log.w(TAG, "---onStop() called");

        _backgroundThreadShouldRun = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
//        if (id == R.id.action_settings) {
//            return true;
//        }
//        if (item.getTitle().equals("Choose Color")) {
//            new ColorPickerDialog(this, this, "BLAH", _color1, _color2)
//                    .show();
//            return true;
//        }
        return super.onOptionsItemSelected(item);
    }
    private Camera.PictureCallback _PictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.e("onPictureTaken", "!!!!!in here!!!!!");
            if (!_backgroundThreadShouldRun) return;
            synchronized (processorLocker) {
                _pictureData = data;
//                _Camera.setFaceDetectionListener(faceDetectionListener);
                _Camera.startPreview();
                _Camera.startFaceDetection();
            }
        }
    };
    public ImageView getimageView() {
        return _imageView;
    }
    private Runnable postProcessedBinaryImage = new Runnable() {
        @Override
        public void run() {
            if (_backgroundThreadShouldRun) {
                Log.w(TAG, "******setting new bitmap(), w = " + getimageView().getWidth() + ", h = " + getimageView().getHeight());
                getimageView().setImageBitmap(_postProcessedBmp);
            }
        }
    };
    private int getRotation() {
        int rotation = _imageView.getDisplay().getRotation();
        String mess = "";
        if (rotation == Surface.ROTATION_0)
            mess = "Surface.ROTATION_0";
        else if (rotation == Surface.ROTATION_90)
            mess = "Surface.ROTATION_90";
        else if (rotation == Surface.ROTATION_180)
            mess = "Surface.ROTATION_180";
        else if (rotation == Surface.ROTATION_270)
            mess = "Surface.ROTATION_270";
        Log.w("getRotation()", mess);
        return rotation;
    }
    private double getRotationAsDegrees() {
        int rotation = _imageView.getDisplay().getRotation();
        if (rotation == Surface.ROTATION_0)
            return 0;
        else if (rotation == Surface.ROTATION_90)
            return 90.0;
        else if (rotation == Surface.ROTATION_180)
            return 180.0;
        else if (rotation == Surface.ROTATION_270)
            return 270.0;
        return rotation;
    }
    private Bitmap getBitmapFromPictureData() {
        Bitmap cameraBmp;
        synchronized (processorLocker) {
            if (_pictureData == null) return null;
            Log.w(TAG, "==== got picture data");
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(_pictureData, 0, _pictureData.length, opts);
            Log.w("binarizeImage", opts.outWidth + ", " + opts.outHeight);
            _pictureDataW = opts.outWidth;
            _pictureDataH = opts.outHeight;
            //opts.inSampleSize = opts.outWidth/_imageView.getWidth();
            opts.inJustDecodeBounds = false;
            Log.w(TAG, "inSampleSize = " + opts.inSampleSize);
            cameraBmp = BitmapFactory.decodeByteArray(_pictureData, 0, _pictureData.length, opts);
            Log.w(TAG, "==== begin process, w = " + cameraBmp.getWidth() + ", h = " + cameraBmp.getHeight());

            _pictureData = null;
            _Camera.takePicture(null, null, _PictureCallback);
//            takePic();
        }
        return cameraBmp;
    }
    // from: http://android-er.blogspot.com/2012/04/android-4-face-detection-display.html
    private class DrawingView extends View {

        boolean _haveFace;
        Paint _drawingPaint;
        int _x, _y, _w, _h;

        public DrawingView(Context context) {
            super(context);
            _haveFace = false;
            _drawingPaint = new Paint();
            _drawingPaint.setColor(Color.GREEN);
            _drawingPaint.setStyle(Paint.Style.STROKE);
            _drawingPaint.setStrokeWidth(2);
        }

        public void setHaveFace(boolean h){
            _haveFace = h;
        }
        public void setFaceRect(Rect rect) {
            Rect outRect = new Rect();
            _haveFace = MainActivity.computeBounds(rect, outRect, _surfaceView.getWidth(), _surfaceView.getHeight());
            if (_haveFace) {
                _haveFace = true;
                _x = _surfaceView.getWidth() - outRect.left - outRect.width();
                _y = outRect.top;
                _w = outRect.width();
                _h = outRect.height();
                Log.w("setFaceRect", "green box= x,y,w,h = " + _x + "," + _y + "," + _w + "," + _h);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!_haveFace) return;
            canvas.drawRect(_x, _y, _x + _w, _y + _h, _drawingPaint);
            Log.e("onDraw", "---drawing view = " + getLeft() + "," + getTop());

        }
    }
}
