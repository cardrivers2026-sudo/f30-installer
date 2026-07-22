package com.cool.appinstaller;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppInstaller {

    public interface InstallCallback {
        void onStatus(String status);
    }

    public static void downloadAndInstall(Context context, String appLink, String appName, InstallCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(appLink);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                if (connection.getResponseCode()!= HttpURLConnection.HTTP_OK) {
                    callback.onStatus("Server error: " + connection.getResponseCode());
                    return;
                }

                InputStream input = connection.getInputStream();
                File apkFile = new File(context.getExternalFilesDir(null), appName.replaceAll("[^a-zA-Z0-9]","_") + ".apk");
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int len;
                long total = 0;
                while ((len = input.read(buffer)) > 0) {
                    output.write(buffer, 0, len);
                    total += len;
                }
                output.close();
                input.close();
                connection.disconnect();

                callback.onStatus("Downloaded " + (total/1024) + "KB, opening installer...");

                // This triggers com.android.packageinstaller/.InstallStart
                installApk(context, apkFile);

            } catch (Exception e) {
                callback.onStatus("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private static void installApk(Context context, File file) {
        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".provider",
                file
        );
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
