package cn.ac.iscas.xlab.droidfacedog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.ac.iscas.xlab.droidfacedog.youtu.RecogResult;

/**
 * Created by Lazyparser on 10/19/16.
 */

// https://developer.android.com/reference/android/os/AsyncTask.html

public class PostImageForRecognitionAsync extends AsyncTask<Bitmap, Void, Integer> {
    public static final int RECOG_SUCCESS = 0;
    public static final int RECOG_REJECTED = 1;
    public static final int RECOG_TIMEOUT = 2;
    public static final int RECOG_INVALID_URL = 3;
    private static final int RECOG_SERVER_ERROR = 4;
    public static final String XLAB = "xxlab";
    public static final String SERVER_IP_ADDRESS = "server_ip_address";
    public static final String DEFAULT_IP = "192.168.0.111";
    public static final double RECOG_THRESHOLD = 0.40;
    public RecogResult mRecogResult;


    private String serverAddress;
    // http://stackoverflow.com/questions/3698034/validating-ip-in-android
    private static Pattern IP_ADDRESS = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))");
    private Context mContext;

    public PostImageForRecognitionAsync() {
        super();
    }

    protected Integer doInBackground(Bitmap... faceImages) {
        Log.w(XLAB, "doInBackground(Bitmap... faceImages)");

        if (serverAddress == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            serverAddress = prefs.getString(SERVER_IP_ADDRESS, DEFAULT_IP);
        }
        if (serverAddress.equals("")) {
            return RECOG_INVALID_URL;
        }
        // http://stackoverflow.com/questions/3698034/validating-ip-in-android
        Matcher matcher = IP_ADDRESS.matcher(serverAddress);
        if (!matcher.matches()) {
            Log.d(XLAB, "IP validation failed: " + serverAddress);
            return RECOG_INVALID_URL;
        }

        Log.w(XLAB, "IP is validated: " + serverAddress);

        // http://www.wikihow.com/Execute-HTTP-POST-Requests-in-Android
        // http://stackoverflow.com/questions/6218143/how-to-send-post-request-in-json-using-httpclient
        // http://stackoverflow.com/questions/13911993/sending-a-json-http-post-request-from-android
        HttpURLConnection client = null;
        try {
            BufferedOutputStream outputStream;
            // TODO: extract the url to youtu package.
            URL url = new URL("http://" + serverAddress + ":8000/recognition");
            client = (HttpURLConnection) url.openConnection();
            client.setRequestMethod("POST");
            client.setDoOutput(true);
            client.setDoInput(true);
            client.setUseCaches(false);

            //rosClient.setChunkedStreamingMode(0);

            // 3. build jsonObject
            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("Image", encodeToBase64(faceImages[0], Bitmap.CompressFormat.JPEG, 100));

            // 4. convert JSONObject to JSON to String
            String jsonString = jsonObject.toString();
            Log.d(XLAB, jsonString);

            client.setRequestProperty("Content-Length", Integer.toString(jsonString.length()));
            client.setRequestProperty("Content-Type","application/json");
            client.setRequestProperty("Connection", "close");
            client.setRequestProperty("Accept-Encoding", "identity");
            client.setRequestProperty("Accept", "text/plain");

            outputStream = new BufferedOutputStream(client.getOutputStream());
            outputStream.write(jsonString.getBytes());
            outputStream.flush();
            outputStream.close();

            int status = client.getResponseCode();
            Log.w(XLAB, "RESPONSE CODE: " + Integer.toString(status));
            Log.w(XLAB, "POST ERROR STRING: " + client.getResponseMessage());

            // Ref: HTTP Return Code 200-399 is ok. return code above 400 means error.
            if (status < 400) {
                InputStream in = client.getInputStream();
                JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));

                RecogResult recogResult = new RecogResult();
                if (!recogResult.parseFrom(reader))
                    return RECOG_SERVER_ERROR;

                mRecogResult = recogResult;
                return RECOG_SUCCESS;
            } else {
                // FIXME: 1024 has no means. It is just a buffer length.
                // this block read the errorStream and logcat it.
                byte[] buf = new byte[1024];
                BufferedInputStream errReader = new BufferedInputStream(client.getErrorStream());
                int l = errReader.read(buf);
                Log.w(XLAB, "RESPONSE ERROR: " + new String(buf, 0, l) + " len " + l);
                return RECOG_SERVER_ERROR;
            }

            //inputStream.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.e(XLAB, "oh no, catch (MalformedURLException e)");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(XLAB, "oh no, catch (IOException e)");
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(XLAB, "oh no, catch (JSONException e)");
        } finally {
            Log.e(XLAB, "FINALLY");
            if(client != null) // Make sure the connection is not null.
                client.disconnect();
        }
        Log.w(XLAB, "END OF doInBackground(Bitmap... faceImages)");

        return RECOG_TIMEOUT;
    }

    protected void onPostExecute(Integer result) {
        Log.w(XLAB, "PostImageForRecognitionAsync onPostExecute [" + result + "]");
        Toast.makeText(mContext, Integer.toString(result), Toast.LENGTH_SHORT).show();

        Log.w(XLAB, "mContext instanceof XBotFace");
        // FIXME: XBotFace Activity only
        if (!(mContext instanceof XBotFace))
            return;
        XBotFace activity = (XBotFace) mContext;

        // if youtu recognized the user, then try to TTS is ID(name).
        // otherwise, play the sound of "youke"
        String msg;
        if (result == RECOG_SUCCESS) {
            msg = "YOUTU: ret = " + Integer.toString(result) + ", confidence = " +
                    Double.toString(mRecogResult.getConfidence()) + ", id = '" +
                    mRecogResult.getId() + "'";
        } else {
            msg = "YOUTU: ret = " + Integer.toString(result)
                    + "RecogResult: null";
        }
        Log.w(XLAB, msg);
        Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();

        if (result == RECOG_SUCCESS && mRecogResult.getConfidence() >= RECOG_THRESHOLD) {
            activity.updateFaceState(XBotFace.IDENTIFIEDSTATE);
            MediaPlayer ttsUserId = activity.lookupNames(mRecogResult.getId());
            activity.prepareGreetingTTS(ttsUserId);
        } else {
            activity.prepareGreetingTTS();
        }
        Log.w(XLAB, "activity.startPlayTTS();");
        activity.startPlayTTS();
    }

    // http://stackoverflow.com/questions/16920942/getting-context-in-asynctask
    public void setContext(Context context) {
        mContext = context;
    }

    // http://stackoverflow.com/questions/9768611/encode-and-decode-bitmap-object-in-base64-string-in-android
    public static String encodeToBase64(Bitmap image, Bitmap.CompressFormat compressFormat, int quality)
    {
        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        image.compress(compressFormat, quality, byteArrayOS);
        return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT);
    }

    // http://stackoverflow.com/questions/9768611/encode-and-decode-bitmap-object-in-base64-string-in-android
    public static Bitmap decodeBase64(String input)
    {
        byte[] decodedBytes = Base64.decode(input, 0);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

}

