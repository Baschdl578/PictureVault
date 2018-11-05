package de.smschindler.picturevault;

import android.app.PendingIntent;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.IOUtils;
import de.smschindler.picturevault.model.Library;
import de.smschindler.picturevault.model.LibraryPicture;
import de.smschindler.picturevault.model.Media;
import de.smschindler.picturevault.settings.SettingsManager;
import de.smschindler.picturevault.sync.MediaSync;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Connects to the Server
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class Server {
    private static OkHttpClient client = null;
    private static Call lastCall = null;

    public static boolean setLastSync(Context context, Long lastSync) {
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/lastsync/set";

        OkHttpClient client = getClient(context);
        RequestBody formBody = new FormBody.Builder().add("time", lastSync.toString()).build();
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            if (!response.isSuccessful())
                Log.i(Server.class.getName(),"Unexpected code " + response);
            response.close();
            return true;
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
            return false;
        }
    }

    public static Long getLastSync(Context context) {
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/lastsync/get";
        OkHttpClient client = getClient(context);
        RequestBody formBody = RequestBody.create(null, "");
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            if (!response.isSuccessful())
                Log.i(Server.class.getName(),"Unexpected code " + response);
            response.close();
            return Long.parseLong(response.body().string());
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
            return 0L;
        }
    }

    public static LinkedList<Library> getLibraries(Context context) {
        LinkedList<Library> outList = new LinkedList<>();
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/library/all";

        OkHttpClient client = getClient(context);
        RequestBody formBody = RequestBody.create(null, "");
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            if (!response.isSuccessful())
                Log.i(Server.class.getName(),"Unexpected code " + response);
            BufferedReader in = new BufferedReader(response.body().charStream());
            String line = null;

            while ((line = in.readLine()) != null) {
                if (line.endsWith("\n")) {
                    line = line.substring(0, line.length() - 1);
                }
                String[] values = line.split(";");
                if (values.length == 7) {
                    Library library = new Library(Long.parseLong(values[0]), values[1], Integer.parseInt(values[2]), Long.parseLong(values[3]), Long.parseLong(values[4]), Long.parseLong(values[5]), Long.parseLong(values[6]));
                    outList.add(library);
                }
            }
            response.close();
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
        }
        return outList;
    }

    public static LibraryPicture[] getPictureIds(Context context, Long libId) {
        ArrayList<LibraryPicture> outList = new ArrayList<>();
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/library/media";

        OkHttpClient client = getClient(context);
        RequestBody formBody = new FormBody.Builder().add("id", libId.toString()).build();
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            if (!response.isSuccessful())
                Log.i(Server.class.getName(),"Unexpected code " + response);
            BufferedReader in = new BufferedReader(response.body().charStream());
            String line = null;

            while ((line = in.readLine()) != null) {
                if (line.endsWith("\n")) {
                    line = line.substring(0, line.length() - 1);
                }
                String[] values = line.split(";");
                if (values.length != 4) continue;

                LibraryPicture pic = new LibraryPicture();
                pic.id = Long.parseLong(values[0]);
                pic.name = values[1];
                pic.duration = Long.parseLong(values[2]);
                pic.size = Long.parseLong(values[3]);
                outList.add(pic);
            }
            response.close();
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
        }
        LibraryPicture[] out = new LibraryPicture[outList.size()];
        outList.toArray(out);
        return out;
    }

    public static boolean checkPulse(Context context) {
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/pulse";
        HttpURLConnection connection = connect(context, addr, true);
        if (connection == null) return false;
        String time = Long.toString(System.currentTimeMillis());
        boolean out = false;

        try {
            IOUtils.write(time, connection.getOutputStream());
        } catch (IOException e) {
        }
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                String sent = IOUtils.toString(connection.getInputStream(), (Charset) null);
                while (sent.endsWith("\n")) {
                    sent = sent.substring(0, sent.length() - 1);
                }
                out = time.equals(sent);
            }
        } catch (IOException e) {
        }
        connection.disconnect();
        return out;
    }

    public static Media getPictureInfo(Context context, Long id) {
        Media out = null;
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/media/info";

        OkHttpClient client = getClient(context);
        RequestBody formBody = new FormBody.Builder().add("id", id.toString()).build();
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            if (!response.isSuccessful())
                Log.i(Server.class.getName(),"Unexpected code " + response);
            String sent = response.body().string();
            String[] values = sent.split("\n");
            if (values.length == 11) {
                out = new Media(Long.parseLong(values[0]), values[1], values[2], Double.parseDouble(values[5]), Double.parseDouble(values[6]), Long.parseLong(values[3]), Long.parseLong(values[4]), Long.parseLong(values[7]), Long.parseLong(values[8]), Long.parseLong(values[9]), Long.parseLong(values[10]));
            } else {
                Log.i("DEBUG", "Faulty image Info with length " + values.length + " " + sent);
            }
            response.close();
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
        }
        return out;
    }


    public static File loadMedia(Context context, Long id, String name, boolean thumb) {
        File out = null;
        String filename = id.toString() + "/";
        if (thumb) filename = "thumb/" + id.toString() + "/";
        File cacheDir = new File(context.getCacheDir(), filename);
        File cacheFile = null;
        if (!thumb) {
            cacheFile = new File(cacheDir, name);
        } else {
            cacheFile = new File(cacheDir, "thumb.jpg");
        }
        if (cacheFile.isFile() && cacheFile.exists()) {
            return cacheFile;
        }

        String addrpart = "/media/load";
        if (thumb) addrpart = "/media/thumb";
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + addrpart;

        OkHttpClient client = getClient(context);
        RequestBody formBody = new FormBody.Builder().add("id", id.toString()).build();
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            if (!response.isSuccessful())
                Log.i(Server.class.getName(),"Unexpected code " + response);
            if (!cacheDir.mkdirs()) {
                Log.i(Server.class.getName(), "Could not create cache dir");
            }
            if (!cacheFile.createNewFile()) {
                Log.i(Server.class.getName(), "Could not create cache dir");
            }
            FileOutputStream fOut = new FileOutputStream(cacheFile);
            try {
                IOUtils.copy(response.body().byteStream(), fOut);
            } catch (IOException e) {
                Log.i(Server.class.getName(), "IOException while writing to file: " + e.getMessage());
            }
            out = cacheFile;
            response.close();
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
        }
        return out;
    }

    public static File getMedia(Context context, Long id, String name) {
        return loadMedia(context, id, name, false);
    }

    public static File getThumbnail(Context context, Long id, String name) {
        return loadMedia(context, id, name, true);
    }

    public static Long uploadFile(Context context, File file, String bucket, Long created, Long modified, Double latitude, Double longitude, Long h_res, Long v_res, Long duration, Long filesize, long totalSize, long startProgress, int totalItems, int currentItem, PendingIntent intent) {
        return uploadFile(context, file, bucket, created, modified, latitude, longitude, h_res, v_res, duration, filesize, totalSize, startProgress, totalItems, currentItem, intent, true);
    }


    public static Long uploadFile(Context context, File file, String bucket, Long created, Long modified, Double latitude, Double longitude, Long h_res, Long v_res, Long duration, Long filesize, long totalSize, long startProgress, int totalItems, int currentItem, PendingIntent intent, boolean reportSize) {
        Log.i("Server", "Upload File: " + file.getAbsolutePath());
        if (!file.exists() || !file.isFile()) {
            Log.i("Server", "Could not find file");
            return -1L;
        }
        FileLock lock = null;
        FileChannel channel = null;
        boolean locked = true;
        try {
            channel = new RandomAccessFile(file, "rw").getChannel();
            lock = channel.lock();
        } catch (IOException e) {
            Log.i("Server", "Could not get file lock");
            locked = false;
        }
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/media/upload";
        /*HttpURLConnection connection = connect(context, addr);
        if (connection == null) return -1L;
        Long out = -1L;

        try {
            DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            output.write(file.getName().getBytes());
            output.write("\n".getBytes());
            output.write(bucket.getBytes());
            output.write("\n".getBytes());
            output.write(latitude.toString().getBytes());
            output.write("\n".getBytes());
            output.write(longitude.toString().getBytes());
            output.write("\n".getBytes());
            output.write(created.toString().getBytes());
            output.write("\n".getBytes());
            output.write(modified.toString().getBytes());
            output.write("\n".getBytes());
            output.write(h_res.toString().getBytes());
            output.write("\n".getBytes());
            output.write(v_res.toString().getBytes());
            output.write("\n".getBytes());
            output.write(duration.toString().getBytes());
            output.write("\n".getBytes());
            output.write(filesize.toString().getBytes());
            output.write("\n".getBytes());
            FileInputStream fIn = new FileInputStream(file);
            long size = startProgress;
            byte[] buffer = new byte[4096];
            int read = fIn.read(buffer);
            while (read > 0 && !MediaSync.getInstance(context).isCanceled()) {
                size += read;
                if (reportSize) {
                    if (!MediaSync.getInstance(context).updateNotification(context, totalItems, totalSize, currentItem, size, intent)) {
                        return -1L;
                    }
                }
                output.write(buffer, 0, read);
                read = fIn.read(buffer);
            }
            if (locked) lock.release();
            output.flush();
            output.close();
        } catch (IOException e) {
            String stacktrace = "";
            for (StackTraceElement ste : e.getStackTrace()) {
                String stackLine = "\n\tat " + ste.getClassName() + "." + ste.getMethodName() + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")";
                stacktrace = stacktrace + stackLine;
            }
            Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage() + "\tStackTrace: " + stacktrace);
            Log.i("Server", "IOEcxeption while writing into connection", e);
        }
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
            } else {
                String stringId = IOUtils.toString(connection.getInputStream(), (Charset) null);
                out = Long.parseLong(stringId);
            }
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
        }
        connection.disconnect();
        */
        Long out = -1L;
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(file.toURI()
                .toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                fileExtension.toLowerCase());
        OkHttpClient client = getClient(context);
        RequestBody formBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("bucket", bucket)
                .addFormDataPart("filename", file.getName())
                .addFormDataPart("latitude", latitude.toString())
                .addFormDataPart("longitude", longitude.toString())
                .addFormDataPart("created", created.toString())
                .addFormDataPart("modified", modified.toString())
                .addFormDataPart("duration", duration.toString())
                .addFormDataPart("h_res", h_res.toString())
                .addFormDataPart("v_res", v_res.toString())
                .addFormDataPart("size", filesize.toString())
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(MediaType.parse(mimeType), file))
                .build();
        Request request = new Request.Builder().url(addr).post(formBody).build();
        try {
            Server.lastCall = client.newCall(request);
            Response response = Server.lastCall.execute();
            out = Long.parseLong(response.body().string());
            if (locked) lock.release();
            response.close();
            return out;
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());
        }
        return out;
    }

    public static HttpURLConnection connect(Context context, String addr) {
        return connect(context, addr, false);
    }


    public static HttpURLConnection connect(Context context, String addr, boolean pulse) {
        System.setProperty("jsse.enableSNIExtension", "false");
        System.setProperty("http.keepAlive", "false");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(addr).openConnection();
            String userCredentials = SettingsManager.getStringValue(context, SettingsManager.SETTING_USERNAME) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_PASS);
            String basicAuth = "Basic " + Base64.encodeToString(userCredentials.getBytes(), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            if (pulse) {
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
            } else {
                connection.setConnectTimeout(1800000);
                connection.setReadTimeout(3600000);
            }
            connection.setChunkedStreamingMode(0);
            connection.setUseCaches(false);

            try {
                connection.connect();
            } catch (IOException e) {
                if (!pulse)
                    Log.i(Server.class.getName(), "IOException while connecting : " + e.getMessage());
                return null;

            }
        } catch (ProtocolException e) {
            if (!pulse)
                Log.i(Server.class.getName(), "Protocol Exception : " + e.getMessage());
        } catch (MalformedURLException e) {
            if (!pulse)
                Log.i(Server.class.getName(), "Malformed URL Exception with URL: " + addr + " and message: " + e.getMessage());
        } catch (IOException e) {
            if (!pulse)
                Log.i(Server.class.getName(), "IO Exception : " + e.getMessage());
        }
        return connection;
    }
/*
    public static void handleError(HttpURLConnection connection) {
        try {
            String code = "" + connection.getResponseCode();
            String message = connection.getResponseMessage();
            String error;
            String body;
            try {
                body = IOUtils.toString(connection.getInputStream(), (Charset) null);
            } catch (Exception e) {
                body = e.getMessage();
            }
            try {
                error = IOUtils.toString(connection.getErrorStream(), (Charset) null);
            } catch (NullPointerException e) {
                error = e.getMessage();
            }
            Log.i(Server.class.getName(), "Response Code: " + code);
            Log.i(Server.class.getName(), "Response Message: " + message);
            if (error != null) {
                Log.i(Server.class.getName(), "Error Stream: " + error);
            }
            Log.i(Server.class.getName(), "Response Body: " + body);
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading error response : " + e.getMessage());
        }
    } */

    private static OkHttpClient getClient(Context context) {
        if (Server.client == null) {
            Server.client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(new BasicAuthInterceptor(SettingsManager.getStringValue(context, SettingsManager.SETTING_USERNAME), SettingsManager.getStringValue(context, SettingsManager.SETTING_PASS)))
                    .build();
        }
        return Server.client;
    }



    private static class BasicAuthInterceptor implements Interceptor {

        private String credentials;

        BasicAuthInterceptor(String user, String password) {
            this.credentials = Credentials.basic(user, password);
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Request authenticatedRequest = request.newBuilder()
                    .header("Authorization", credentials).build();
            return chain.proceed(authenticatedRequest);
        }

    }

    public static void cancel() {
        if (lastCall != null) lastCall.cancel();
    }
}




