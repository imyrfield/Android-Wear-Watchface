package com.example.android.sunshine.app.sync;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by Ian on 5/16/2016.
 */
public class WatchFaceListenerService extends WearableListenerService implements
                                                                      GoogleApiClient
                                                                              .ConnectionCallbacks{

    public final String LOG_TAG = WatchFaceListenerService.class.getSimpleName();

    private static final String NO_WEATHER = "/no_data";
    private static final String DATA_REQUEST = "dataRequest";

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private GoogleApiClient mGoogleApiClient;
    private static final String WEATHER_DATA_PATH = "/weather_data";
    private static final String HIGH_TEMP_KEY = "highTemp";
    private static final String LOW_TEMP_KEY = "lowTemp";
    private static final String WEATHER_ID_KEY = "weatherID";

    @Override
    public void onCreate () {
        super.onCreate();

        // Initialize Google Api Client
        mGoogleApiClient = new GoogleApiClient.Builder( this )
                .addApi( Wearable.API )
                .build();

        ConnectionResult result = mGoogleApiClient.blockingConnect( 30, TimeUnit
                .SECONDS );

        // If connection unsuccessful break!
        if(!result.isSuccess()){
            Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
            return;
        }
    }

    /**
     * Retrieves data for WatchFace, when signaled that none exists yet.
    * */
    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {
        Log.d(LOG_TAG, "onDataChanged: " + dataEvents);

        boolean requestData = false;

        for(DataEvent event : dataEvents){
            // DataItem changed
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataMap map = DataMapItem.fromDataItem( event.getDataItem() )
                                         .getDataMap();
                if (event.getDataItem().getUri().getPath().equals( NO_WEATHER )) {

                    if(map.containsKey( DATA_REQUEST )) {
                        requestData = map.getBoolean( DATA_REQUEST );
                    }
                }
            }
        }

        if(requestData) {
            Context context = getApplicationContext();
            String locationQuery = Utility.getPreferredLocation( context );
            Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                    locationQuery,
                    System.currentTimeMillis() );
            Cursor cursor = context.getContentResolver().query( weatherUri,
                                                                NOTIFY_WEATHER_PROJECTION,
                                                                null,
                                                                null,
                                                                null );

            if (cursor.moveToFirst()) {
                int    weatherId = cursor.getInt( INDEX_WEATHER_ID );
                double high      = cursor.getDouble( INDEX_MAX_TEMP );
                double low       = cursor.getDouble( INDEX_MIN_TEMP );

                updateWatchFace( high, low, weatherId );
            }
            cursor.close();
        }
    }

    @Override
    public void onConnected (@Nullable Bundle bundle) {
       Log.d( LOG_TAG, "Google API connected");
        Wearable.DataApi.addListener( mGoogleApiClient, this );
    }

    @Override
    public void onConnectionSuspended (int i) {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {

            Wearable.DataApi.removeListener( mGoogleApiClient, this );
        }
    }

    /**
     * Sends retrieved data to watchface
     * */
    private void updateWatchFace(double high, double low, int weatherID) {
        Log.d( LOG_TAG, "Updating WatchFace: High=" + high
                + " Low=" + low
                + " WeatherID=" + weatherID );

        // Return if GoogleApiClient isn't initialized.
        if (mGoogleApiClient == null){
            return;
        }

        // Connect to GoogleAPIClient, so long as it isn't already connected or trying
        // to connect.
        if (!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.blockingConnect( 30, TimeUnit.SECONDS );
        }

        // Create DataMap
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create( WEATHER_DATA_PATH);
        putDataMapRequest.getDataMap().putString( HIGH_TEMP_KEY, Utility
                .formatTemperature( getApplicationContext(),high ));
        putDataMapRequest.getDataMap().putString( LOW_TEMP_KEY, Utility
                .formatTemperature( getApplicationContext(), low ) );
        putDataMapRequest.getDataMap().putInt( WEATHER_ID_KEY, weatherID );

        PutDataRequest request = putDataMapRequest.asPutDataRequest().setUrgent();

        Wearable.DataApi.putDataItem( mGoogleApiClient, request )
                        .setResultCallback( new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult (@NonNull DataApi.DataItemResult dataItemResult) {
                                if(!dataItemResult.getStatus().isSuccess()){
                                    Log.d( LOG_TAG, "FAILURE - sending data to wearable failed" );
                                } else {
                                    Log.d( LOG_TAG, "SUCCESS - data sent to wearable" );
                                }
                            }
                        } );
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        if (mGoogleApiClient != null){
            mGoogleApiClient.disconnect();
        }
    }
}
