package org.woodley.antiface.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.Build;
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
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {
    private static final String    TAG                 = "MainActivity";

    private byte[] _pictureData;
    private int _pictureDataW;
    private int _pictureDataH;
    private Object processorLocker = new Object();
    private Camera _Camera = null;
    DrawingView _drawingView;
    private boolean _faceDetectionRunning = false;

    private SurfaceView _surfaceView;
    ImageView _imageView = null;
    //private Handler _handler;
    private PostUploadHandler _postUploadHandler;
    private Bitmap _postProcessedBmp;
    private boolean _firstTime = true;  // first time this instance.
    private boolean _backgroundThreadShouldRun = true;
    private MainActivity _that;
    private MenuItem _button;
    double _dontCutOffChinsFactor = .3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            _that = this;
            this.getActionBar().setIcon(R.drawable.round72);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getActionBar().setHomeButtonEnabled(true);
            }
            // landscape mode is the only thing that works out of the box for camera preview it seems.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            DisplayMetrics metrics = getResources().getDisplayMetrics();

            Handler handler = new Handler();
            _postUploadHandler = new PostUploadHandler(this, handler);

            _surfaceView = (SurfaceView) findViewById(R.id.cameraSurface);
            _surfaceView.getHolder().addCallback(_surfaceHolderCallback);
            //addContentView(new DrawingView(this), new ActionBar.LayoutParams(320,240));
            new Thread(_imageUploader).start();

            _imageView = (ImageView) findViewById(R.id.processedImage);
//            Log.w("onCreate", "imageView WxH = " + _imageView.getWidth() + "," + _imageView.getHeight());
            ViewTreeObserver vto = _imageView.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (_firstTime) {
                        // The screen is fully drawn at this point, do some initialization.

                        initBitmap();
                        FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout);

                        FrameLayout.LayoutParams layoutParamsDrawing
                                = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT);
                        _drawingView = new DrawingView(_that);
                        frmLayout.addView(_drawingView);

                        // now make imageView a greyscale.
                        // Note: this will display a greyscale, but won't allow you to extract a grayscale for later use.
                        // see toGreyscale() in Uploader.java for where I've done that.
                        ColorMatrix matrix = new ColorMatrix();
                        matrix.setSaturation(0);

                        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
                        _imageView.setColorFilter(filter);
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
//            Log.w("_surfaceView.getHolder()", "-----surfaceDestroyed");
            try {
                _Camera.stopPreview();
                _Camera.stopFaceDetection();
                _faceDetectionRunning = false;
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
//            Log.w("_surfaceView.getHolder()", "-----surfaceCreated");
            try {
                _Camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);

                _Camera.setFaceDetectionListener(faceDetectionListener);
                _Camera.setPreviewDisplay(holder);

                _Camera.startPreview();
                Log.w("surfaceCreated", "max num faces = " + _Camera.getParameters().getMaxNumDetectedFaces());
                if (_Camera.getParameters().getMaxNumDetectedFaces() <= 0) {
                    showErrorMess();
                    System.exit(1);
                }
                _Camera.startFaceDetection();
                if (_button != null) _button.setTitle(getString(R.string.ScanningLabel));

                //setTitle(getString(R.string.ScanningLabel));
                _faceDetectionRunning = true;
            } catch (IOException exception) {
                _Camera.release();
                _Camera = null;
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
//            Log.w("surfaceChanged", "-----surfaceChanged");

            _Camera.stopPreview();
            _faceDetectionRunning = false;

            // Size changes must be in surfaceChanged(), per: http://developer.android.com/guide/topics/media/camera.html#check-camera-features

            // from: http://stackoverflow.com/questions/17804309/android-camera-preview-wrong-aspect-ratio
            Camera.Size size = _Camera.getParameters().getPreviewSize();

            //landscape
            View imageview = (ImageView) findViewById(R.id.processedImage);
//            Log.w("surfaceChanged", "imageView - wxh = " + imageview.getWidth() + "x" + imageview.getHeight() );
            View preview = (FrameLayout) findViewById(R.id.frameLayout);
//            Log.w("surfaceChanged", "preview - wxh = " + preview.getWidth() + "x" + preview.getHeight() );

//            float ratio = (float)size.width/size.height;
//            int new_width=0, new_height=0;
//            new_width = preview.getWidth();
//            new_height = Math.round(preview.getWidth()/ratio);  // always adjust width.
//            preview.setLayoutParams(new LinearLayout.LayoutParams(new_width, new_height));

            Camera.Parameters params = _Camera.getParameters();
            params.set("orientation", "landscape");
            Camera.Size optimalSize=getOptimalPreviewSize(params.getSupportedPreviewSizes(),  preview.getWidth(), preview.getHeight());
            if (optimalSize != null) {
//                Log.w("surfaceChanged", "optimal - wxh = " + optimalSize.width + "x" + optimalSize.height);
                preview.setLayoutParams(new LinearLayout.LayoutParams(optimalSize.width, optimalSize.height));
            }
            else
                Log.w("surfaceChanged", "couldn't adjust aspect ratio. Sticking with default.");

            try {
                _Camera.setPreviewDisplay(holder);
            }
            catch (IOException e) {  }
            _Camera.startPreview();
//            Log.w("surfaceChanged", "max num faces = " + _Camera.getParameters().getMaxNumDetectedFaces());
            _Camera.startFaceDetection();
            if (_button != null) _button.setTitle(getString(R.string.ScanningLabel));
            _faceDetectionRunning = true;
            _Camera.takePicture(null, null, _PictureCallback);
        }
    };
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.2;
        double targetRatio = (double) w/h;

        if (sizes==null) return null;

        Camera.Size optimalSize = null;

        double minDiff = Double.MAX_VALUE;

        int targetWidth = w;

        // Find size
        minDiff = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
//            Log.w("getOptimalPreviewSize", "w,h,tolerance = " + size.width + "x" + size.height + ", " + Math.abs(ratio - targetRatio));
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (size.width <= targetWidth) {
//                Log.w("getOptimalPreviewSize", "Found! " + size.width + "x" + size.height);
                if (minDiff > targetWidth - size.width) {
                    minDiff = targetWidth - size.width;
                    optimalSize = size;
                }
            }
        }
        return optimalSize;
    }
    private static boolean computeBounds(Rect in, Rect outr, int width, int height, double scaleFactor) {
        int x = (int) (((float) in.left + 1000.0)/2000.0 * width);
        int w = (int) ((float) in.width()/2000.0 * width);
        int y = (int) (((float) in.left + 1000.0)/2000.0 * height);
        int h = (int) ((float) in.height()/2000.0 * height);
        // make it 30% larger so we don't cut off chins
        int deltaw = (int) (w *scaleFactor);
        int deltah = (int) (h *scaleFactor);
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
//            Log.w("onFaceDetection", "Found " + faces.length + " faces.");
            if (faces.length == 0) {
                _drawingView.setHaveFace(false);
                return;
            }
            Bitmap cameraBmp = getBitmapFromPictureData();
            if (cameraBmp == null) {
                _drawingView.setHaveFace(false);
                Log.w("onFaceDetection", "null bitmap");
                return;
            }
            Face face = faces[0];

            _drawingView.setFaceRect(face.rect);
            _drawingView.invalidate();

            Rect outRect = new Rect();
            boolean stat = computeBounds(face.rect, outRect, _pictureDataW, _pictureDataH, _dontCutOffChinsFactor);
             if (!stat) return;
//            Log.w("foundFace", "before x,y,w,h = " + face.rect.left +", " + face.rect.top +", " + face.rect.width() +", " + face.rect.height());
//            Log.w("foundFace", "after x,y,w,h = " + outRect.left + "," + outRect.top + "," + outRect.width() + "," + outRect.height());

            Matrix m = new Matrix();
            m.preScale(-1, 1);
//            Bitmap dst = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);

            Bitmap bmp = Bitmap.createBitmap(cameraBmp, outRect.left, outRect.top, outRect.width(), outRect.height(), m, false);

            _imageView.setImageBitmap(bmp);
            if (_button != null && _button.getTitle() != getString(R.string.UploadingLabel))
                _button.setTitle(getString(R.string.UploadFaceLabel));
       }
    };
    private void initBitmap() {
        Bitmap postProcessedBmp = Bitmap.createBitmap(_imageView.getWidth(), _imageView.getHeight(), Bitmap.Config.RGB_565);
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
    }
    public void onStop() {
        super.onStop();  // Always call the superclass method first
//        Log.w(TAG, "---onStop() called");

        _backgroundThreadShouldRun = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        _button = menu.getItem(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
//        if (id == R.id.action_settings) {
//            return true;
//        }
        if (item.getTitle().equals(getString(R.string.UploadFaceLabel))) {
            confirmAndUpload(item);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void showErrorMess() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(
                "Your device does not have the necessary Face Detection APIs required to run this App."
        ).setTitle("Error")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int id) {} }  );
        AlertDialog alert = builder.create();
        alert.show();
    }
    private void confirmAndUpload(final MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(
                "By clicking 'I agree' you consent to upload this photo to Facefield.org which will use it for art projects such as this one. By submitting a photograph, you (and any other individual depicted in the photograph) consent to such usage. All uploads are anonymous - we only upload your image and do not collect other identifying information."

        ).setTitle("Agree Terms")
                .setCancelable(false)
                .setPositiveButton("I Agree",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {
                                initiateUpload(item);
                            }
                        }
                )
                .setNegativeButton("I Don't Agree",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {        }
                        }
                );
        AlertDialog alert = builder.create();
        alert.show();
    }
    private void initiateUpload(MenuItem item) {
        _postUploadHandler.setUploadButton(item);
        Bitmap bitmap = ((BitmapDrawable)_imageView.getDrawable()).getBitmap();
        _uploadableBitmap = bitmap.copy(bitmap.getConfig(), false);
        item.setTitle(getString(R.string.UploadingLabel));

    }
    private Camera.PictureCallback _PictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
//            Log.e("onPictureTaken", "!!!!!Picture Taken!!!!!");
            if (!_backgroundThreadShouldRun) return;
            synchronized (processorLocker) {
                _pictureData = data;
                _Camera.stopPreview();
                try {
                    _Camera.stopFaceDetection();
                }
                catch (RuntimeException e) { }
                _drawingView.setHaveFace(false);

                _Camera.setFaceDetectionListener(faceDetectionListener);
                _Camera.startPreview();
                _Camera.startFaceDetection();
//                if (_button != null) _button.setTitle(getString(R.string.ScanningLabel));
                _faceDetectionRunning = true;
                _faceDetectionRunning = true;
            }
        }
    };
    public ImageView getimageView() {
        return _imageView;
    }
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
//        Log.w("getRotation()", mess);
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
            cameraBmp = getBitmapFromPictureData(_pictureData);
            _pictureData = null;
        }
        _Camera.takePicture(null, null, _PictureCallback);
        return cameraBmp;
    }
    private Bitmap getBitmapFromPictureData(byte[] pictureData) {
        Bitmap cameraBmp;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(pictureData, 0, pictureData.length, opts);
            _pictureDataW = opts.outWidth;
            _pictureDataH = opts.outHeight;

            opts.inJustDecodeBounds = false;

            cameraBmp = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.length, opts);

        return cameraBmp;
    }
    Bitmap _uploadableBitmap = null;
    private Runnable _imageUploader = new Runnable() {

        @Override
        public void run() {
            boolean firstTime = true;

            try {
                Thread.currentThread().setName("ImageUploader" + android.os.Process.myTid());
                while (_backgroundThreadShouldRun) {
                    if (_uploadableBitmap != null) {
                        String url = Uploader.Upload(_that, _uploadableBitmap);
                        _uploadableBitmap = null;
                        _postUploadHandler.handlePostUploadTasks(getString(R.string.UploadFaceLabel), url);
                    }

                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                Log.w("imageUploaderThread", "Ended background thread id:" + android.os.Process.myTid());
            }
        }
    };

    // from: http://android-er.blogspot.com/2012/04/android-4-face-detection-display.html
    private class DrawingView extends View {

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
        public void setFaceRect(Rect rect) {
            Rect outRect = new Rect();
            _haveFace = MainActivity.computeBounds(rect, outRect, _surfaceView.getWidth(), _surfaceView.getHeight(), _dontCutOffChinsFactor);
            if (_haveFace) {
                _haveFace = true;
                _x = _surfaceView.getWidth() - outRect.left - outRect.width();
                _y = outRect.top;
                _w = outRect.width();
                _h = outRect.height();

                MainActivity.computeBounds(rect, outRect, _surfaceView.getWidth(), _surfaceView.getHeight(), 0);
                _x1 = _surfaceView.getWidth() - outRect.left - outRect.width();
                _y1 = outRect.top;
                _w1 = outRect.width();
                _h1 = outRect.height();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!_haveFace) return;
            canvas.drawRect(_x, _y, _x + _w, _y + _h, _greenPaint);
            canvas.drawRect(_x1, _y1, _x1 + _w1, _y1 + _h1, _redPaint);
        }
    }
}
