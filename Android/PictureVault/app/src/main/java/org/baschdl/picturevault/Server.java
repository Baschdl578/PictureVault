package org.baschdl.picturevault;

import android.app.PendingIntent;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.baschdl.picturevault.model.Library;
import org.baschdl.picturevault.model.LibraryPicture;
import org.baschdl.picturevault.model.Media;
import org.baschdl.picturevault.settings.SettingsManager;
import org.baschdl.picturevault.sync.MediaSync;

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

/**
 * Connects to the Server
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class Server {

    public static boolean setLastSync(Context context, Long lastSync) {
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/lastsync/set";
        HttpURLConnection connection = connect(context, addr);
        if (connection == null) return false;

        try {
            IOUtils.write(lastSync.toString(), connection.getOutputStream());
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage());
        }

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
                connection.disconnect();
                return false;
            }
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

        }
        connection.disconnect();
        return true;
    }

    public static Long getLastSync(Context context) {
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/lastsync/get";
        HttpURLConnection connection = connect(context, addr);
        if (connection == null) return -1L;

        Long out = -1L;

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
            } else {
                InputStream in = connection.getInputStream();
                String line = "";
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                line = result.toString("UTF-8");

                if (line != null) {
                    if (line.endsWith("\n")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    out = Long.parseLong(line);
                }
            }

        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

        }
        connection.disconnect();
        return out;
    }

    public static LinkedList<Library> getLibraries(Context context) {
        LinkedList<Library> outList = new LinkedList<>();
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/library/all";
        HttpURLConnection connection = connect(context, addr);
        if (connection == null) return new LinkedList<>();

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
                connection.disconnect();
                return outList;

            }
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
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
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

        }
        connection.disconnect();
        return outList;
    }

    public static LibraryPicture[] getPictureIds(Context context, Long libId) {
        ArrayList<LibraryPicture> outList = new ArrayList<>();
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/library/media";
        HttpURLConnection connection = connect(context, addr);
        if (connection == null) return new LibraryPicture[0];

        try {
            IOUtils.write(libId.toString(), connection.getOutputStream());
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage());
        }

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
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
            }
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

        }
        connection.disconnect();
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
        HttpURLConnection connection = connect(context, addr);
        if (connection == null) return null;

        try {
            IOUtils.write(id.toString(), connection.getOutputStream());
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage());
        }

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
            } else {
                String sent = IOUtils.toString(connection.getInputStream(), (Charset) null);
                String[] values = sent.split("\n");
                if (values.length == 11) {
                    out = new Media(Long.parseLong(values[0]), values[1], values[2], Double.parseDouble(values[5]), Double.parseDouble(values[6]), Long.parseLong(values[3]), Long.parseLong(values[4]), Long.parseLong(values[7]), Long.parseLong(values[8]), Long.parseLong(values[9]), Long.parseLong(values[10]));
                } else {
                    Log.i("DEBUG", "Faulty image Info with length " + values.length + " " + sent);
                }
            }
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

        }
        connection.disconnect();

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
        HttpURLConnection connection = connect(context, addr);
        if (connection == null) return null;

        try {
            IOUtils.write(id.toString(), connection.getOutputStream());
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage());
        }

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                handleError(connection);
            } else {
                if (!cacheDir.mkdirs()) {
                    Log.i(Server.class.getName(), "Could not create cache dir");
                }
                if (!cacheFile.createNewFile()) {
                    Log.i(Server.class.getName(), "Could not create cache dir");
                }
                FileOutputStream fOut = new FileOutputStream(cacheFile);
                try {
                    IOUtils.copy(connection.getInputStream(), fOut);
                } catch (IOException e) {
                    Log.i(Server.class.getName(), "IOException while writing to file: " + e.getMessage());

                }
                out = cacheFile;
            }
        } catch (IOException e) {
            Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

        }
        connection.disconnect();
        return out;
    }

    public static File getMedia(Context context, Long id, String name) {
        return loadMedia(context, id, name, false);
    }

    public static File getThumbnail(Context context, Long id, String name) {
        return loadMedia(context, id, name, true);
    }


    public static Long uploadFile(Context context, File file, String bucket, Long created, Long modified, Double latitude, Double longitude, Long h_res, Long v_res, Long duration, Long filesize, long totalSize, long startProgress, int totalItems, int currentItem, PendingIntent intent) {
        if (!file.exists() || !file.isFile()) return -1L;
        FileLock lock = null;
        FileChannel channel = null;
        try {
            channel = new RandomAccessFile(file, "rw").getChannel();
            lock = channel.lock();
        } catch (IOException e) {
            return -1L;
        }
        String addr = "http://" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERPORT) + "/media/upload";
        HttpURLConnection connection = connect(context, addr);
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
                if (!MediaSync.getInstance(context).updateNotification(context, totalItems, totalSize, currentItem, size, intent)) {
                    return -1L;
                }
                output.write(buffer, 0, read);
                read = fIn.read(buffer);
            }
            lock.release();
            output.flush();
            output.close();
        } catch (IOException e) {
            String stacktrace = "";
            for (StackTraceElement ste : e.getStackTrace()) {
                String stackLine = "\n\tat " + ste.getClassName() + "." + ste.getMethodName() + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")";
                stacktrace = stacktrace + stackLine;
            }
            Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage() + "\tStackTrace: " + stacktrace);
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
    }
}
