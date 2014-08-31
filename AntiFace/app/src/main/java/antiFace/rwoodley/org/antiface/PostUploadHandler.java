package antiFace.rwoodley.org.antiface;

import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;
import android.os.Handler;

public class PostUploadHandler implements  Runnable {
    public Context _guiThreadContext;

    private String _redirectURL = null;
    private Object _redirectURLLocker = new Object();
    private String _buttonTitle = "";
    private Object _buttonTitleLocker = new Object();
    MenuItem _uploadButton = null;
    private Handler _handler;

    public PostUploadHandler(Context context, Handler handler) {
        _guiThreadContext = context; _handler = handler;
    }
    public void setUploadButton(MenuItem butt) {
        _uploadButton = butt;
    }
    public void handlePostUploadTasks(String title, String url) {
        synchronized (_buttonTitleLocker) { _buttonTitle = title; }
        synchronized (_redirectURLLocker) { _redirectURL = url; }
        _handler.post(this);   // run on GUI thread.
    }
    @Override
    public void run() {
        synchronized (_buttonTitleLocker) {
            if (_uploadButton != null) _uploadButton.setTitle(_buttonTitle);
        }
        synchronized (_redirectURLLocker) {
            if (_redirectURL != null) {
                Intent myIntent = new Intent(_guiThreadContext, WebActivity.class);
                myIntent.putExtra("url", _redirectURL);
                _guiThreadContext.startActivity(myIntent);
            }
        }
    }
}
