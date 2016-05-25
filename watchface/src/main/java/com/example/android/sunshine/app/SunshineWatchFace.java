/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

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

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace
        extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE = Typeface.create( Typeface.SANS_SERIF,
                                                                     Typeface.NORMAL );
    private static final String TAG = "SunshineWatchFace";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine () {
        return new Engine();
    }

    private class Engine
            extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks{

        final Handler mUpdateTimeHandler = new EngineHandler( this );

        static final String COLON_STRING = ":";

        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;
        boolean mShouldDrawColons;

        int   mBackgroundColor;
        Paint mTextPaint;
        Paint mTemperaturePaint;

        Calendar mCalendar;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive (Context context, Intent intent) {

                if(intent.hasExtra( "time-zone" )){
                    mCalendar.getTimeZone();
                }
                mCalendar.setTimeZone( TimeZone.getDefault());
                invalidate();
            }
        };

        float mXOffset;
        float mYOffset;
        float mColonWidth;

        private static final String WEATHER_DATA_PATH = "/weather_data";
        private static final String HIGH_TEMP_KEY = "highTemp";
        private static final String LOW_TEMP_KEY = "lowTemp";
        private static final String WEATHER_ID_KEY = "weatherID";

        private static final String NO_WEATHER = "/no_data";
        private static final String DATA_REQUEST = "dataRequest";

        private String hTemp;
        private String lTemp;
        private int weatherId = -1;

        private Bitmap mBackgroundBitmap;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean resizeBitmap;
        boolean noData;

        Resources resources;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder( SunshineWatchFace.this )
                .addConnectionCallbacks( this )
                .addApi( Wearable.API )
                .build();

        @Override
        public void onCreate (SurfaceHolder holder) {
            super.onCreate( holder );

            setWatchFaceStyle(
                    new WatchFaceStyle.Builder( SunshineWatchFace.this )
                            .setCardPeekMode( WatchFaceStyle.PEEK_MODE_VARIABLE )
                            .setBackgroundVisibility( WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE )
                            .setShowSystemUiTime( false )
                            .setStatusBarGravity( Gravity.RIGHT )
                            .setHotwordIndicatorGravity( Gravity.BOTTOM )
                            .setViewProtectionMode( WatchFaceStyle.PROTECT_STATUS_BAR | WatchFaceStyle.PROTECT_HOTWORD_INDICATOR )
                            .build() );

            resources = SunshineWatchFace.this.getResources();

            mBackgroundColor = resources.getColor( R.color.background ) ;

            mTextPaint = new Paint();
            mTextPaint = createTextPaint( R.color.text, -1 );

            mTemperaturePaint = new Paint();
            mTemperaturePaint = createTextPaint( R.color.text, 40 );

            mCalendar = Calendar.getInstance();

            mGoogleApiClient.connect();
            resizeBitmap = true;

            // Default Values
            noData = true;
            weatherId = 800;
            hTemp = String.format(SunshineWatchFace.this.getString(R.string.format_temperature), 18f);
            lTemp = String.format(SunshineWatchFace.this.getString(R.string.format_temperature), 16f);
            getBitmap( weatherId );
        }

        @Override
        public void onDestroy () {

            mUpdateTimeHandler.removeMessages( MSG_UPDATE_TIME );
            if (mGoogleApiClient != null) mGoogleApiClient.disconnect();

            super.onDestroy();
        }

        private Paint createTextPaint (int textColor, float size) {
            Paint paint = new Paint();
            paint.setColor( textColor );
            paint.setTypeface( NORMAL_TYPEFACE );
            paint.setAntiAlias( true );

            if (size > 0) {
                paint.setTextSize( size );
            } else {
                paint.setTextAlign( Paint.Align.RIGHT );
            }

            return paint;
        }

        @Override
        public void onVisibilityChanged (boolean visible) {
            super.onVisibilityChanged( visible );

            if (visible) {
                registerReceiver();

            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver () {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter( Intent.ACTION_TIMEZONE_CHANGED );
            SunshineWatchFace.this.registerReceiver( mTimeZoneReceiver, filter );
        }

        private void unregisterReceiver () {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver( mTimeZoneReceiver );
        }

        @Override
        public void onApplyWindowInsets (WindowInsets insets) {
            super.onApplyWindowInsets( insets );

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean   isRound   = insets.isRound();
            mXOffset = resources.getDimension( isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset );
            float textSize = resources.getDimension( isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size );

            mTextPaint.setTextSize( textSize );

            mColonWidth = mTextPaint.measureText( COLON_STRING );
        }

        @Override
        public void onPropertiesChanged (Bundle properties) {
            super.onPropertiesChanged( properties );
            mLowBitAmbient = properties.getBoolean( PROPERTY_LOW_BIT_AMBIENT, false );
        }

        @Override
        public void onTimeTick () {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged (boolean inAmbientMode) {
            super.onAmbientModeChanged( inAmbientMode );
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias( !inAmbientMode );
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw (Canvas canvas, Rect bounds) {

            // Retrieve time
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis( now );
            boolean is24Hour = DateFormat.is24HourFormat( SunshineWatchFace.this );

            // If noData has been retrieved yet, fetch weather data from phone.
            // Fires once every 30 seconds (actually twice as OnDraw triggers twice a
            // second)
            if (noData &&
                    (mCalendar.get(Calendar.SECOND) == 1 || mCalendar.get(Calendar.SECOND) == 30)) {
                fetchWeather();
            }

            mTemperaturePaint.setColor( resources.getColor( R.color.text ));
            mTextPaint.setColor( resources.getColor( R.color.text ));

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor( Color.BLACK );

            } else {
                canvas.drawColor( mBackgroundColor );

                // Resizes bitmap when a new bitmap has been loaded in onDataChanged()
                if (resizeBitmap){
                    onSurfaceChanged( getSurfaceHolder(),
                                      PixelFormat.OPAQUE,
                                      bounds.width(),
                                      bounds.height() );
                }
                if(mBackgroundBitmap != null) {
                    // Shifts Background 1/2 off screen
                    canvas.drawBitmap( mBackgroundBitmap, -bounds.exactCenterX(), 0, null );
                }
            }

            //float x = bounds.exactCenterX()- mXOffset;
            float x = bounds.width()-mXOffset;

            // Draw Minutes
            String minuteString = formatTwoDigitNumber( mCalendar.get( Calendar.MINUTE ));
            canvas.drawText( minuteString, x, mYOffset, mTextPaint );

            x -= mTextPaint.measureText( minuteString );

            // Draw Colons
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500; // Shows colons for first 1/2 second

            if (isInAmbientMode() || mShouldDrawColons ){
                canvas.drawText( COLON_STRING, x, mYOffset, mTextPaint);
            }

            // Set y to align the temperatures on either side of the colon
            float y = x;

            x -= mColonWidth;

            // Draw Hours
            String hourString;

            if (is24Hour){
                hourString = formatTwoDigitNumber( mCalendar.get( Calendar.HOUR_OF_DAY
                ) );
            } else {
                int hour = mCalendar.get( Calendar.HOUR_OF_DAY );
                if(hour == 0) {
                    hour = 12;
                }else if (hour >= 13){
                    hour -= 12;
                }

                hourString = String.valueOf( hour );
            }
            canvas.drawText( hourString, x, mYOffset, mTextPaint);

            // Draw temperatures if not in Ambient Mode
            if(!isInAmbientMode()) {
                if (hTemp == null){
                    hTemp = "na ";
                }
                if (lTemp == null) {
                    lTemp = " na";
                }

                mTemperaturePaint.setAlpha( 150 ); // Reduce Alpha to make Low Temp slightly faded
                mTemperaturePaint.setTextAlign( Paint.Align.LEFT ); // Align Low Temp to the Left of the time's colon
                canvas.drawText( lTemp, y, mYOffset + 50, mTemperaturePaint );


                mTemperaturePaint.setAlpha( 255 ); // Reset Alpha to normal for High Temp
                mTemperaturePaint.setTextAlign( Paint.Align.RIGHT ); // Align High temp to the Right of the time's colon
                canvas.drawText( hTemp, y - mColonWidth, mYOffset + 50, mTemperaturePaint );
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer () {
            mUpdateTimeHandler.removeMessages( MSG_UPDATE_TIME );
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage( MSG_UPDATE_TIME );
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning () {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage () {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - ( timeMs % INTERACTIVE_UPDATE_RATE_MS );
                mUpdateTimeHandler.sendEmptyMessageDelayed( MSG_UPDATE_TIME, delayMs );
            }
        }

        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder, int format, int width, int height) {

            resizeBitmap = false;

            if (mBackgroundBitmap == null
                    || mBackgroundBitmap.getWidth() != width
                    || mBackgroundBitmap.getHeight() != height) {
                if (mBackgroundBitmap == null) return;
                mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                                                              width,
                                                              height,
                                                              true
                                                              /* filter */);
            }

            mYOffset = height/2;
            super.onSurfaceChanged(holder, format, width, height);
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        /**
         * Generates bitmap based on weatherId
         */
        private void getBitmap (int weatherId){

            // Return if weatherId invalid
            if (weatherId < 0) {return;}

            mBackgroundBitmap = BitmapFactory.decodeResource( getResources(),
                                                              Utility.getBackgroundForWeatherID( weatherId ));

        }

        @Override
        public void onDataChanged (DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged: " + dataEvents);

            // Trigger resize of new bitmap
            resizeBitmap = true;
            noData = false;

            for(DataEvent event : dataEvents){
                // DataItem changed
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap map = DataMapItem.fromDataItem( event.getDataItem() )
                                             .getDataMap();
                    if (event.getDataItem().getUri().getPath().equals( WEATHER_DATA_PATH )) {

                        if(map.containsKey( HIGH_TEMP_KEY )){
                            hTemp = map.getString( HIGH_TEMP_KEY );
                        } else { Log.d( TAG, "no high temp found" );}

                        if(map.containsKey( LOW_TEMP_KEY )){
                            lTemp = map.getString( LOW_TEMP_KEY );
                        } else { Log.d( TAG, "no low temp found" ); }

                        if(map.containsKey( WEATHER_ID_KEY )){
                            weatherId = map.getInt( WEATHER_ID_KEY );
                        } else { Log.d( TAG, "no weather id found" ); }
                    }
                }
            }
            getBitmap( weatherId );
            invalidate();
        }

        @Override
        public void onConnected (@Nullable Bundle bundle) {
            Log.d(TAG, "Connected to Google Api Service");
            Wearable.DataApi.addListener( mGoogleApiClient, this );
        }

        @Override
        public void onConnectionSuspended (int i) {
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {

                Wearable.DataApi.removeListener( mGoogleApiClient, this );
            }
        }

        /**
         * Fetches weather upon watchface init
         * */
        private void fetchWeather(){
            if (mGoogleApiClient.isConnected()){

                PutDataMapRequest mapRequest = PutDataMapRequest.create( NO_WEATHER );
                mapRequest.getDataMap().putBoolean( DATA_REQUEST, noData );

                PutDataRequest request = mapRequest.asPutDataRequest().setUrgent();

                Wearable.DataApi.putDataItem( mGoogleApiClient, request )
                                .setResultCallback( new ResultCallback<DataApi.DataItemResult>() {
                                    @Override
                                    public void onResult (@NonNull DataApi.DataItemResult dataItemResult) {
                                        if(!dataItemResult.getStatus().isSuccess()){
                                            Log.d( TAG, "FAILURE - sending request for data failed" );
                                        } else {
                                            Log.d( TAG, "SUCCESS - request for Data sent successfully");
                                        }
                                    }
                                } );
            }

        }
    }

    private static class EngineHandler
            extends Handler {

        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler (SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>( reference );
        }

        @Override
        public void handleMessage (Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
