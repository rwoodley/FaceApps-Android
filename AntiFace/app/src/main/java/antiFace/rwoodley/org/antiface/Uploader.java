package antiFace.rwoodley.org.antiface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by robertwoodley on 8/28/14.
 */
public class Uploader {
    public static void SavePicToMedia(Context con, Bitmap bitmap) {
        String path = Environment.getExternalStorageDirectory().toString();
        OutputStream fOut = null;
        File file = new File(path, "Face.jpg");
        try {
            fOut = new FileOutputStream(file);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
            fOut.flush();
            fOut.close();

            MediaStore.Images.Media.insertImage(con.getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
        }
        catch (IOException e) {
            Log.e("SavePicToMedia", "Error: " + e.getMessage());
            e.printStackTrace();
        }

    }
    private static Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }
    public static String Upload(Context con, Bitmap colorbitmap) {
        Bitmap bitmap = toGrayscale(colorbitmap);

        //SavePicToMedia(con, bitmap);    // for debugging
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;

        String urlServer = "http://facefield.org/iosUpload.aspx?devID=android";
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "---------------------------14737809831466499882746641449";

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1*1024*1024;

        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] imageInByte = stream.toByteArray();
            System.out.println("........length......"+imageInByte);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageInByte);

//            FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile) );

            URL url = new URL(urlServer);
            connection = (HttpURLConnection) url.openConnection();

            // Allow Inputs &amp; Outputs.
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            // Set HTTP method to POST.
            connection.setRequestMethod("POST");

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);
            //outputStream.writeBytes("Content-Disposition: form-data; " + lineEnd);

            outputStream = new DataOutputStream( connection.getOutputStream() );
//            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
//            outputStream.writeBytes(lineEnd);

            bytesAvailable = bis.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // Read file
            bytesRead = bis.read(buffer, 0, bufferSize);

            while (bytesRead > 0)
            {
                outputStream.write(buffer, 0, bufferSize);
                bytesAvailable = bis.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = bis.read(buffer, 0, bufferSize);
            }

//            outputStream.writeBytes(lineEnd);
//            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            int serverResponseCode = connection.getResponseCode();
            String serverResponseMessage = connection.getResponseMessage();
            Log.w("uploader", "Uploaded with code: " + serverResponseCode + ", message = " + serverResponseMessage
                    + ", url = " + connection.getURL() );

            bis.close();
            outputStream.flush();
            outputStream.close();

            Log.w("uploader", "Uploaded with code: " + serverResponseCode + ", message = " + serverResponseMessage
                    + ", url = " + connection.getURL() );
            return connection.getURL().toString();
        }
        catch (Exception ex)
        {
            Log.e("uploader", "Error in upload()", ex);
            return null;
        }
    }
}
