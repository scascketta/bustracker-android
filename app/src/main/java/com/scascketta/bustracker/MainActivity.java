package com.scascketta.bustracker;

import android.Manifest.permission;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final String LOG_TAG = "MainActivity";

    private GoogleApiClient gapiClient;
    private String busId;
    private boolean receivingUpdates;
    private boolean connectedToGoogleApi;

    private TextView intentStatusText;
    private TextView gapiStatusText;
    private EditText busIdText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        intentStatusText = (TextView) findViewById(R.id.intent_status);
        gapiStatusText = (TextView) findViewById(R.id.gapi_status);
        busIdText = (EditText) findViewById(R.id.bus_id);

        busId = "test";
        receivingUpdates = false;
        connectedToGoogleApi = false;

        IntentFilter intentFilter = new IntentFilter(Constants.BROADCAST_TRACKER_SERVICE_ACTION);
        TrackerStatusReceiver receiver = new TrackerStatusReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);

        Intent localIntent = new Intent(Constants.BROADCAST_BUS_ID_ACTION)
                .putExtra(Constants.BUS_ID_KEY, busId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

        buildGoogleApiClient();
    }

    private void buildGoogleApiClient() {
        gapiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        connectedToGoogleApi = true;

        LocationRequest locReq = buildLocationRequest();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locReq);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi
                .checkLocationSettings(gapiClient, builder.build());

        result.setResultCallback(new LocationSettingsResultCallback(this));

        Intent service = new Intent(this, TrackerService.class);
        PendingIntent intent = PendingIntent.getService(this, 0, service, 0);
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG, "Starting location updates");
            PendingResult<Status> updateResult = LocationServices.FusedLocationApi.requestLocationUpdates(gapiClient, locReq, intent);
            updateResult.setResultCallback(new LocationUpdatesResultCallback(true));
        } else {
            Log.d(LOG_TAG, "Don't have fine location permission - can't start updates");
        }
    }

    private LocationRequest buildLocationRequest() {
        LocationRequest locReq = new LocationRequest();
        locReq.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locReq.setFastestInterval(5000);
        locReq.setInterval(5000);
        return locReq;
    }

    private class LocationSettingsResultCallback implements ResultCallback<LocationSettingsResult> {
        private AppCompatActivity activity;
        public LocationSettingsResultCallback(AppCompatActivity mainActivity) {
            super();
            activity = mainActivity;
        }

        @Override
        public void onResult(@NonNull LocationSettingsResult result) {
            final Status status = result.getStatus();
            String msg;

            switch (status.getStatusCode()) {
                case LocationSettingsStatusCodes.SUCCESS:
                    msg = "Connected successfully and location settings are correct";
                    gapiStatusText.setText(msg);
                    Log.d(LOG_TAG, msg);
                    break;
                case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                    try {
                        msg = "Connected successfully, but location settings should be fixed.";
                        gapiStatusText.setText(msg);
                        Log.d(LOG_TAG, msg);
                        status.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException e) {
                        // ignore the error
                    }
                    break;
                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                    // Location settings not satisfied, but we have no way to fix it
                    msg = "Connected successfully, but can't fix location settings.";
                    Log.d(LOG_TAG, msg);
                    gapiStatusText.setText(msg);
                    break;
            }
        }
    }

    private class LocationUpdatesResultCallback implements ResultCallback<Status> {
        private boolean startingUpdates;

        public LocationUpdatesResultCallback(boolean isStartingUpdates) {
            startingUpdates = isStartingUpdates;
        }

        @Override
        public void onResult(@NonNull Status status) {
            boolean success = status.isSuccess();
            if (startingUpdates) {
                receivingUpdates = success;
            } else {
                receivingUpdates = !success;
            }
            Log.d(LOG_TAG, "Receiving updates: " + receivingUpdates);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        String errorMsg;
        switch (connectionResult.getErrorCode()) {
            case ConnectionResult.SERVICE_MISSING: errorMsg = "Google Play Services is missing";
                break;
            case ConnectionResult.SIGN_IN_FAILED: errorMsg = "Failed to sign into Google Play Services";
                break;
            default: errorMsg = "Failed to connect to Google API for unknown reason";
                break;
        }
        gapiStatusText.setText(errorMsg);
    }

    private class TrackerStatusReceiver extends BroadcastReceiver {
        private TrackerStatusReceiver() {

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(Constants.TRACKER_SERVICE_STATUS_KEY);
            intentStatusText.setText(status);
        }
    }

    public void updateBusId(View view) {
        Log.d(LOG_TAG, "Updating bus ID");
        busId = busIdText.getText().toString();
        Intent localIntent = new Intent(Constants.BROADCAST_BUS_ID_ACTION)
                .putExtra(Constants.BUS_ID_KEY, busId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    public void startLocationUpdates(View view) {
        if (!connectedToGoogleApi) {
            gapiClient.connect();
            return;
        }

        Intent service = new Intent(this, TrackerService.class);
        PendingIntent intent = PendingIntent.getService(this, 0, service, 0);
        LocationRequest locReq = buildLocationRequest();

        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG, "Starting location updates");
            PendingResult<Status> updateResult = LocationServices.FusedLocationApi.requestLocationUpdates(gapiClient, locReq, intent);
            updateResult.setResultCallback(new LocationUpdatesResultCallback(true));
        } else {
            Log.d(LOG_TAG, "Don't have fine location permission - can't start updates");
        }
    }

    public void stopLocationUpdates(View view) {
        Intent service = new Intent(this, TrackerService.class);
        PendingIntent intent = PendingIntent.getService(this, 0, service, 0);
        PendingResult<Status> updateResult  = LocationServices.FusedLocationApi.removeLocationUpdates(gapiClient, intent);
        updateResult.setResultCallback(new LocationUpdatesResultCallback(false));
    }
}
