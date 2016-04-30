package com.scascketta.bustracker;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.LocationResult;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TrackerService extends Service {
    private final static String LOG_TAG = "TrackerService";
    private final static String API_GATEWAY_URL = "<YOUR-API-GATEWAY-URL>";
    private final static String API_KEY = "<YOUR-API-GATEWAY-ACCESS-KEY>";

    private String intentMsg;
    private static String busId = "test";

    public TrackerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter intentFilter = new IntentFilter(Constants.BROADCAST_BUS_ID_ACTION);
        BusIdReceiver receiver = new BusIdReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "Received an intent");
        handleLocationUpdate(intent);
        return START_NOT_STICKY;
    }

    private void handleLocationUpdate(Intent intent) {
        if (LocationResult.hasResult(intent)) {
            Log.d(LOG_TAG, "Location update has a location result");

            LocationResult result = LocationResult.extractResult(intent);
            Location location = result.getLastLocation();

            String updateTime = DateFormat.getTimeInstance().format(new Date());
            intentMsg = "Location update as of " + updateTime;
            intentMsg += " , coords = " + String.valueOf(location.getLatitude()) + ", " + String.valueOf(location.getLongitude());
            Log.d(LOG_TAG, intentMsg);

            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                Log.d(LOG_TAG, "Connected to network, starting async task to ping monitor...");
                new PostUpdateTask().execute(location);
            } else {
                Log.d(LOG_TAG, "NOT connected to network, skip pinging monitor");
                stopSelf();
            }
        } else {
            Log.d(LOG_TAG, "Intent has no location result - stopping");
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void replyWithStatus(String status) {
        Intent localIntent = new Intent(Constants.BROADCAST_TRACKER_SERVICE_ACTION)
                .putExtra(Constants.TRACKER_SERVICE_STATUS_KEY, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private boolean updateLocation(Location location) throws IOException {
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");

        String unixUpdateTime = String.valueOf(location.getTime() / 1000);
        String jsonString = String.format("{\"data\":{\"bus_id\":\"%s\",\"latitude\":%f,\"longitude\":%f,\"timestamp\":\"%s\"}}",
                busId, location.getLatitude(), location.getLongitude(), unixUpdateTime);

        Log.d(LOG_TAG, "JSON String body: " + jsonString);

        RequestBody body = RequestBody.create(mediaType, jsonString);
        Request req = new Request.Builder()
                .url(API_GATEWAY_URL)
                .post(body)
                .addHeader("content-type", "application/json")
                .addHeader("x-api-key", API_KEY)
                .build();

        Response res = client.newCall(req).execute();
        res.body().close();
        return res.code() == 200;
    }

    private class PostUpdateTask extends AsyncTask<Location, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Location... location) {
            try {
                return updateLocation(location[0]);
            } catch (IOException err) {
                String errMsg = "Raised exception while updating location: " + err.toString();
                replyWithStatus(errMsg);
                Log.d(LOG_TAG, errMsg);
                return false;
            }
        }
        @Override
        protected void onPostExecute(Boolean success) {
            intentMsg += ", location update status is " + success;
            replyWithStatus(intentMsg);
            Log.d(LOG_TAG, "Location update status is " + success);
            stopSelf();
        }
    }

    private class BusIdReceiver extends BroadcastReceiver {
        private BusIdReceiver() { }

        @Override
        public void onReceive(Context context, Intent intent) {
            String newBusId = intent.getStringExtra(Constants.BUS_ID_KEY);
            Log.d(LOG_TAG, String.format("Updating bus ID - old='%s' new='%s\n", busId, newBusId));
            busId = newBusId;
        }
    }
}
