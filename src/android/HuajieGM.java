package com.huajie.gm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

import org.apache.cordova.BuildHelper;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.device.PrinterManager;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.util.Base64;

public class HuajieGM extends CordovaPlugin {

    public static final String TAG = "HuajieGM";
    private PrinterManager printer;
    private String applicationId;

    /**
     * Constructor.
     */
    public HuajieGM() {
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        printer = new PrinterManager();
        // HuajieGM.uuid = getUuid();
    }

    /**
     * 分享照片
     * @param callbackContext
     * @param arg
     */
    public void shareImage(CallbackContext callbackContext, JSONObject arg) {
        new ShareImageTask(
            cordova,
            callbackContext,
            arg, applicationId
        ).execute();
    }

    /**
     * 保存照片
     * @param callbackContext
     * @param arg
     */
    public void saveImage(CallbackContext callbackContext, JSONObject arg) {
        new SaveImageTask(
                cordova,
                callbackContext,
                arg, applicationId
        ).execute();
    }

    private static class ShareImageTask extends AsyncTask<String, Void, String> {
        private CallbackContext callbackContext;
        private WeakReference<CordovaInterface> cordova;
        private JSONObject arg;
        private String applicationId;

        ShareImageTask(CordovaInterface cordova, CallbackContext callbackContext, JSONObject arg, String applicationId) {
            this.cordova = new WeakReference<>(cordova);
            this.arg = arg;
            this.callbackContext = callbackContext;
            this.applicationId = applicationId;
        }

        @Override
        protected String doInBackground(String... strings) {

            try {
                String src = arg.getString("src");
                String alt = arg.getString("alt");

                Bitmap bitmap = getBitmap(src);
                File file = saveTempFile(bitmap);

                Uri uri = FileProvider.getUriForFile(cordova.get().getContext(), applicationId + ".provider", file);
                Intent share = ShareCompat.IntentBuilder.from(cordova.get().getActivity())
                        .setStream(uri) // uri from FileProvider
                        .setType("text/html")
                        .getIntent()
                        .setAction(Intent.ACTION_SEND) //Change if needed
                        .setDataAndType(uri, "image/png")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                cordova.get().getContext()
                        .startActivity(Intent.createChooser(share, "分享照片"));

                callbackContext.success(file.getAbsolutePath());
                return "0";
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
                return "1";
            }
        }

        private Bitmap getBitmap(String src) throws IOException {
            InputStream inputStream = new URL(src).openStream();   // Download Image from URL
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);       // Decode Bitmap
            inputStream.close();
            return bitmap;
        }

        private File saveTempFile(Bitmap bitmap) throws IOException {
            //create a file to write bitmap data
            File f = new File(Environment.getExternalStorageDirectory() + "/share_image.png");

            //Convert bitmap to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            //write the bytes in file
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(bitmapdata);
                fos.flush();
            }
            return f;
        }
    }

    private static class SaveImageTask extends AsyncTask<String, Void, String> {
        private CallbackContext callbackContext;
        private WeakReference<CordovaInterface> cordova;
        private JSONObject arg;
        private String applicationId;

        SaveImageTask(CordovaInterface cordova, CallbackContext callbackContext, JSONObject arg, String applicationId) {
            this.cordova = new WeakReference<>(cordova);
            this.arg = arg;
            this.callbackContext = callbackContext;
            this.applicationId = applicationId;
        }

        @Override
        protected String doInBackground(String... strings) {

            try {
                String src = arg.getString("src");
                String alt = arg.getString("alt");

                Bitmap bitmap = getBitmap(src);
                String result = MediaStore.Images.Media.insertImage(
                    cordova.get().getContext().getContentResolver(), bitmap, alt , alt);
                callbackContext.success(result);
                return "0";
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
                return "1";
            }
        }

        private Bitmap getBitmap(String src) throws IOException {
            InputStream inputStream = new URL(src).openStream();   // Download Image from URL
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);       // Decode Bitmap
            inputStream.close();
            return bitmap;
        }
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.applicationId = (String) BuildHelper.getBuildConfigValue(cordova.getActivity(), "APPLICATION_ID");
        this.applicationId = preferences.getString("applicationId", this.applicationId);

        // 问候
        if ("hello".equals(action)) {
            JSONObject r = new JSONObject();
            r.put("data", "你好啊");
            callbackContext.success(r);
        }

        else if ("shareImage".equals(action)) {
            shareImage(callbackContext, args.getJSONObject(0));
        }

        else if ("saveImage".equals(action)) {
            saveImage(callbackContext, args.getJSONObject(0));
        }

        // 测试打印
        else if ("testPrint".equals(action)) {
            JSONObject arg = args.getJSONObject(0);
            JSONArray items = arg.getJSONArray("items");

            int x = 0;
            int y = 5;
            int ret = 0;
            int pageWidth = arg.has("pageWidth") ? arg.getInt("pageWidth") : 384;;
            int pageHeight = arg.has("pageHeight") ? arg.getInt("pageHeight") : -1;
            printer.setupPage(pageWidth, pageHeight);
            printer.setGrayLevel(4);

            for (int i = 0; i < items.length(); ++i) {

                // 循环打印项目
                JSONObject item = items.getJSONObject(i);
                String type = item.getString("type");

                if ("text".equals(type)) {
                    int fontSize = item.getInt("fontSize");
                    String content = item.getString("content");
                    ret = printer.drawTextEx(content, x, y, pageWidth, -1, "宋体", fontSize, 0, 0, 0);
                    y = y + ret + 5;
                }
                else if ("image".equals(type)) {
                    int height = item.getInt("height");
                    int width = item.getInt("width");
                    String mimeType = item.getString("mimeType");
                    String base64 = item.getString("base64");

                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    ret = printer.drawBitmap(bitmap, x + (pageWidth - width) / 2 - 5, y);
                    y = y + height + 5;
                }
            }

            // 最后, 输出一行横线作为结尾
            y = y + 200;
            printer.drawTextEx("----", x, y, pageWidth, -1, "宋体", 24, 0, 0, 0);

            // 完成打印.
            ret = printer.printPage(0);

            JSONObject r = new JSONObject();
            r.put("data", ret);
            callbackContext.success(r);
        }

        else {
            return false;
        }
        return true;
    }
}
