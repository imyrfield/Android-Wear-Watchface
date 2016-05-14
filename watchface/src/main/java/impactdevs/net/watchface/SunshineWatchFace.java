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

package impactdevs.net.watchface;

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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;

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
            extends CanvasWatchFaceService.Engine {

        final Handler mUpdateTimeHandler = new EngineHandler( this );

        static final String COLON_STRING = ":";

        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;
        boolean mShouldDrawColons;

        Paint mBackgroundPaint;
        Paint mTextPaint;

        String mAmString;
        String mPmString;

        Calendar mCalendar;
        Date mDate;
        Time    mTime;
        // TODO: ***FIX*** Timezones
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive (Context context, Intent intent) {
                mTime.clear( intent.getStringExtra( "time-zone" ) );
                mTime.setToNow();
            }
        };

        float mXOffset;
        float mYOffset;
        float mColonWidth;


        private Bitmap mBackgroundBitmap;
        private int mPaletteLightColor;
        private int mPaletteDarkColor;
        private int mPaletteVibrantColor;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate (SurfaceHolder holder) {
            super.onCreate( holder );

            setWatchFaceStyle( new WatchFaceStyle.Builder( SunshineWatchFace.this ).setCardPeekMode(
                    WatchFaceStyle.PEEK_MODE_VARIABLE )
                                                                                   .setBackgroundVisibility(
                                                                                           WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE )
                                                                                   .setShowSystemUiTime(
                                                                                           false )
                                                                                   .setStatusBarGravity(
                                                                                           Gravity.RIGHT )
                                                                                   .setHotwordIndicatorGravity( Gravity.BOTTOM )
                                                                                   .setViewProtectionMode( WatchFaceStyle.PROTECT_STATUS_BAR | WatchFaceStyle.PROTECT_HOTWORD_INDICATOR )
                                                                                   .build() );
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension( R.dimen.digital_y_offset );

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor( resources.getColor( R.color.background ) );

            mTextPaint = new Paint();
            mTextPaint = createTextPaint( resources.getColor( R.color.digital_text ) );

            // TODO: ***FIX*** Generate Background based on Actual Weather
            mBackgroundBitmap = BitmapFactory.decodeResource( getResources(), R
                    .drawable.storm );

            /* Extract colors from background image to improve watchface style. */
            Palette.generateAsync(
                    mBackgroundBitmap,
                    new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {
                            if (palette != null) {
                                if (Log.isLoggable( TAG, Log.DEBUG)) {
                                    Log.d(TAG, "Palette: " + palette);
                                }

                                mPaletteVibrantColor = palette.getVibrantColor(Color.BLACK);
                                mPaletteLightColor = palette.getLightVibrantColor(Color.BLACK);
                                mPaletteDarkColor = palette.getDarkMutedColor(getResources().getColor( R.color.background ));
                            }
                        }
                    });

            mCalendar = Calendar.getInstance();
            mDate = new Date(  );
            // Todo: initFormats(); ?
        }

        @Override
        public void onDestroy () {
            mUpdateTimeHandler.removeMessages( MSG_UPDATE_TIME );
            super.onDestroy();
        }

        private Paint createTextPaint (int textColor) {
            Paint paint = new Paint();
            paint.setColor( textColor );
            paint.setTypeface( NORMAL_TYPEFACE );
            paint.setAntiAlias( true );
            return paint;
        }

        @Override
        public void onVisibilityChanged (boolean visible) {
            super.onVisibilityChanged( visible );

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
//                mTime.clear( TimeZone.getDefault().getID() );
//                mTime.setToNow();
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
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor( Color.BLACK );
                mTextPaint.setColor( Color.WHITE );
            } else {
                // TODO: ***IDEA*** Shift Background so 1/2 off screen ?
                canvas.drawColor( mPaletteDarkColor );
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
                mTextPaint.setColor( mPaletteVibrantColor );

            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis( now );
            mDate.setTime( now );
            boolean is24Hour = DateFormat.is24HourFormat( SunshineWatchFace.this );

            // Shows colons for first 1/2 second
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw Hours
            // TODO: ***FIX*** Center text properly
            float x = mXOffset + 35;
            String hourString;
            if (is24Hour ){
                hourString = formatTwoDigitNumber( mCalendar.get( Calendar.HOUR_OF_DAY
                ) );
            } else {
                int hour = mCalendar.get( Calendar.HOUR_OF_DAY );
                if(hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf( hour );
            }
            canvas.drawText( hourString, x, mYOffset, mTextPaint);
            x += mTextPaint.measureText( hourString );

            // Draw Colons
            if (isInAmbientMode() || mShouldDrawColons ){
                canvas.drawText( COLON_STRING, x, mYOffset, mTextPaint);
            }
            x += mColonWidth;

            // Draw Minutes
            String minuteString = formatTwoDigitNumber( mCalendar.get( Calendar.MINUTE ));
            canvas.drawText( minuteString, x, mYOffset, mTextPaint );
            x += mTextPaint.measureText( minuteString );

            // TODO: ***FEATURE***  AM/PM - Currently Crashes (why?)
//            if (!is24Hour) {
//                x += mColonWidth;
//                canvas.drawText( getAmPmString( mCalendar.get( Calendar.AM_PM ) ), x,
//                                 mYOffset, mTextPaint );
//            }
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
            if (mBackgroundBitmap == null
                    || mBackgroundBitmap.getWidth() != width
                    || mBackgroundBitmap.getHeight() != height) {
                mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                                                                    width, height, true /* filter */);
            }
            super.onSurfaceChanged(holder, format, width, height);
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
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
