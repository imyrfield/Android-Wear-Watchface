package impactdevs.net.watchface;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by Ian on 5/16/2016.
 */
public class WatchFaceListenerService extends WearableListenerService {

    private static final String LOG_TAG      = "WatchListenerService";
    private static final String WEATHER_DATA_PATH = "/weather_data";
    private static final String HIGH_TEMP_KEY = "highTemp";
    private static final String LOW_TEMP_KEY = "lowTemp";
    private static final String WEATHER_ID_KEY = "weatherID";

    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {

        String hTemp = "";
        String lTemp = "";
        int weatherId = 0;

        if (Log.isLoggable( LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onDataChanged: " + dataEvents);
        }

        // Initialize Google Api Client
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder( this )
                        .addApi( Wearable.API )
                        .build();

        ConnectionResult result = mGoogleApiClient.blockingConnect( 30, TimeUnit
                .SECONDS );

        // If connection unsuccessful break!
        if(!result.isSuccess()){
            Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        for(DataEvent event : dataEvents){
            // DataItem changed
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataMap map = DataMapItem.fromDataItem( event.getDataItem() )
                                         .getDataMap();
                if (event.getDataItem().getUri().getPath().equals( WEATHER_DATA_PATH )) {

                    if(map.containsKey( HIGH_TEMP_KEY )){
                        hTemp = map.getString( HIGH_TEMP_KEY );
                    } else { Log.d( LOG_TAG, "no high temp found" );}

                    if(map.containsKey( LOW_TEMP_KEY )){
                        lTemp = map.getString( LOW_TEMP_KEY );
                    } else { Log.d( LOG_TAG, "no low temp found" ); }

                    if(map.containsKey( WEATHER_ID_KEY )){
                        weatherId = map.getInt( WEATHER_ID_KEY );
                    } else { Log.d( LOG_TAG, "no weather id found" ); }
                }
            }
        }

        // Send data to SunshineWatchFace and update screen
        // SunshineWatchFace.updateWatchFace( hTemp, lTemp, weatherId);
    }

    //    private GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder( this )
//            .addApi( Wearable.API )
//            .addOnConnectionFailedListener( this )
//            .addConnectionCallbacks( this )
//            .build();
//
//    @Override
//    public void onCreate () {
//        super.onCreate();
//        if (mGoogleApiClient != null && ( !mGoogleApiClient.isConnecting() || !mGoogleApiClient.isConnected())) {
//            mGoogleApiClient.connect();
//        }
//    }
//
//    @Override
//    public void onDestroy () {
//        super.onDestroy();
//        if (mGoogleApiClient != null){
//            mGoogleApiClient.disconnect();
//        }
//    }
//
//    @Override
//    public void onConnected (@Nullable Bundle bundle) {
//        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
//            Log.d(LOG_TAG, "onConnected: " + bundle);
//        }
//    }
//
//    @Override
//    public void onConnectionSuspended (int cause) {
//        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
//            Log.d(LOG_TAG, "onConnectionSuspended: " + cause);
//        }
//    }
//
//    @Override
//    public void onConnectionFailed (@NonNull ConnectionResult connectionResult) {
//        if (Log.isLoggable( LOG_TAG, Log.DEBUG)) {
//            Log.d(LOG_TAG, "onConnectionFailed: " + connectionResult);
//        }
//    }
}
