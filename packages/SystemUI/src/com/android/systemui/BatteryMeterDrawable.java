/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2016-2017 The ParanoidAndroid Project
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

package com.android.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import com.android.systemui.statusbar.policy.BatteryController;

public class BatteryMeterDrawable extends Drawable implements
        BatteryController.BatteryStateChangeCallback {

    private static final float ASPECT_RATIO = 9.5f / 14.5f;
    public static final String TAG = BatteryMeterDrawable.class.getSimpleName();
    public static final String SHOW_PERCENT_SETTING = "status_bar_show_battery_percent";

    private static final boolean SINGLE_DIGIT_PERCENT = false;

    // Values for the different battery styles
    public static final int BATTERY_STYLE_PORTRAIT  = 0;
    public static final int BATTERY_STYLE_CIRCLE    = 2;
    public static final int BATTERY_STYLE_HIDDEN    = 4;
    public static final int BATTERY_STYLE_TEXT      = 5;

    private final Context mContext;
    private final Handler mHandler;

    private final int[] mColors;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;

    private final int mCriticalLevel;
    private final String mWarningString;

    private final int mDarkModeBackgroundColor;
    private final int mDarkModeFillColor;

    private final int mLightModeBackgroundColor;
    private final int mLightModeFillColor;

    private final Runnable mInvalidate = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };

    private BatteryController mBatteryController;

    private boolean mCharging;
    private boolean mChargingAnimationsEnabled;
    private boolean mPluggedIn;

    private int mHeight;
    private int mWidth;

    private boolean mShowPercent;
    private boolean mPowerSaveEnabled;
    private int mIconTint = Color.WHITE;
    private float mOldDarkIntensity = 0f;

    private int mLevel = -1;
    private boolean mListening;

    private boolean mIsAnimating; // stores charge-animation status to remove callbacks

    private float mTextX, mTextY; // precalculated position for drawText() to appear centered

    private boolean mInitialized;

    private Paint mTextAndBoltPaint;
    private Paint mWarningTextPaint;
    private Paint mClearPaint;

    private LayerDrawable mBatteryDrawable;
    private Drawable mFrameDrawable;
    private StopMotionVectorDrawable mLevelDrawable;
    private Drawable mBoltDrawable;

    private int mTextGravity;

    private int mCurrentBackgroundColor = 0;
    private int mCurrentFillColor = 0;

    private int mLevelAlpha;
    private int mCurrentLevel;
    private int mStyle;
    private ValueAnimator mAnimator;

    public BatteryMeterDrawable(Context context, Handler handler, int frameColor) {
        // Portrait is the default drawable style
        this(context, handler, frameColor, BATTERY_STYLE_PORTRAIT);
    }

    public BatteryMeterDrawable(Context context, Handler handler, int frameColor, int style) {
        mContext = context;
        mHandler = handler;
        mStyle = style;
        final Resources res = context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        mColors = new int[2 * N];
        for (int i = 0; i < N; i++) {
            mColors[2 * i] = levels.getInt(i, 0);
            mColors[2 * i + 1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();
        updateShowPercent();
        mWarningString = context.getString(R.string.battery_meter_very_low_overlay_symbol);
        mCriticalLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);

        loadBatteryDrawables(res, style);

        // Load text gravity and blend mode
        final int[] attrs = new int[] { android.R.attr.gravity, R.attr.blendMode };
        final int resId = getBatteryDrawableStyleResourceForStyle(style);
        PorterDuff.Mode xferMode = PorterDuff.Mode.XOR;
        if (resId != 0) {
            TypedArray a = mContext.obtainStyledAttributes(resId, attrs);
            mTextGravity = a.getInt(0, Gravity.CENTER);
            xferMode = PorterDuff.intToMode(a.getInt(1, PorterDuff.modeToInt(PorterDuff.Mode.XOR)));
            a.recycle();
        } else {
            mTextGravity = Gravity.CENTER;
        }
        mTextAndBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        mTextAndBoltPaint.setTypeface(font);
        mTextAndBoltPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));
        mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(xferMode));
        mTextAndBoltPaint.setColor(mCurrentFillColor != 0
                ? mCurrentFillColor : res.getColor(R.color.batterymeter_bolt_color));

        mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWarningTextPaint.setColor(mColors[1]);
        font = Typeface.create("sans-serif", Typeface.BOLD);
        mWarningTextPaint.setTypeface(font);
        mWarningTextPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));

        mClearPaint = new Paint();
        mClearPaint.setColor(0);

        mDarkModeBackgroundColor =
                context.getColor(R.color.dark_mode_icon_color_dual_tone_background);
        mDarkModeFillColor = context.getColor(R.color.dark_mode_icon_color_dual_tone_fill);
        mLightModeBackgroundColor =
                context.getColor(R.color.light_mode_icon_color_dual_tone_background);
        mLightModeFillColor = context.getColor(R.color.light_mode_icon_color_dual_tone_fill);

        mIntrinsicWidth = context.getResources().getDimensionPixelSize(R.dimen.battery_width);
        mIntrinsicHeight = context.getResources().getDimensionPixelSize(R.dimen.battery_height);
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    public void startListening() {
        mListening = true;
        updateShowPercent();
        mBatteryController.addStateChangedCallback(this);
    }

    public void stopListening() {
        mListening = false;
        mBatteryController.removeStateChangedCallback(this);
    }

    public void disableShowPercent() {
        mShowPercent = false;
        postInvalidate();
    }

    private void postInvalidate() {
        mHandler.post(mInvalidate);
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mPowerSaveEnabled = mBatteryController.isPowerSave();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        if (pluggedIn && !mChargingAnimationsEnabled && mCurrentLevel != level) {
            startChargingAnimation(mCurrentLevel == 0 ? 3 : 1);
            mCurrentLevel = level;
        } else if (!pluggedIn) {
            mCurrentLevel = 0;
            cancelChargingAnimation();
        }
        mLevel = level;
        mPluggedIn = pluggedIn;
        mCharging = charging;
        postInvalidate();
    }

    private void startChargingAnimation(final int repeat) {
        if (mLevelAlpha == 0 || mAnimator != null
                || mStyle != BATTERY_STYLE_CIRCLE) {
            return;
        }
        final int defaultAlpha = mLevelAlpha;
        mAnimator = ValueAnimator.ofInt(defaultAlpha, 0, defaultAlpha);
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mLevelDrawable.setAlpha((int) animation.getAnimatedValue());
                invalidateSelf();
            }
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                animation.removeAllListeners();
                mLevelDrawable.setAlpha(defaultAlpha);
                mAnimator = null;
                invalidateSelf();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mLevelDrawable.setAlpha(defaultAlpha);
                mAnimator = null;
                if (repeat <= 0) {
                    startChargingAnimation(0);
                } else if (repeat != 1) {
                    startChargingAnimation(repeat - 1);
                }
            }
        });
        mAnimator.setDuration(2000);
        mAnimator.setStartDelay(500);
        mAnimator.start();
    }

    private void cancelChargingAnimation() {
       if (mAnimator != null) {
           mAnimator.cancel();
           mAnimator = null;
        }
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        mPowerSaveEnabled = isPowerSave;
        invalidateSelf();
    }

    public void setChargingAnimationsEnabled(boolean enabled) {
        if (mChargingAnimationsEnabled != enabled) {
            mChargingAnimationsEnabled = enabled;
            invalidateSelf();
        }
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        mHeight = bottom - top;
        mWidth = right - left;
        mWarningTextPaint.setTextSize(mHeight * 0.75f);
    }

    protected void updatePercent() {
        updateShowPercent();
        postInvalidate();
    }

    private void updateShowPercent() {
        mShowPercent = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0) == 1;
    }

    private int getColorForLevel(int percent) {

        // If we are in power save mode, always use the normal color.
        if (mPowerSaveEnabled) {
            return mColors[mColors.length - 1];
        }
        int thresh = 0;
        int color = 0;
        for (int i = 0; i < mColors.length; i += 2) {
            thresh = mColors[i];
            color = mColors[i + 1];
            if (percent <= thresh) {

                // Respect tinting for "normal" level
                if (i == mColors.length - 2) {
                    return mIconTint;
                } else {
                    return color;
                }
            }
        }
        return color;
    }

    public void setDarkIntensity(float darkIntensity) {
        if (darkIntensity == mOldDarkIntensity) {
            return;
        }
        mCurrentBackgroundColor = getBackgroundColor(darkIntensity);
        mCurrentFillColor = getFillColor(darkIntensity);
        mIconTint = mCurrentFillColor;
        // Make bolt fully opaque for increased visibility
        mFrameDrawable.setTint(mCurrentBackgroundColor);
        if (mBoltDrawable != null) {
            mBoltDrawable.setTint(0xff000000 | mCurrentFillColor);
            updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
        }
        invalidateSelf();
        mOldDarkIntensity = darkIntensity;
    }

    private int getBackgroundColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeBackgroundColor, mDarkModeBackgroundColor);
    }

    private int getFillColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeFillColor, mDarkModeFillColor);
    }

    private int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, lightColor, darkColor);
    }

    @Override
    public void draw(Canvas c) {
        if (!mInitialized) {
            init();
        }

        drawBattery(c);
        if (mChargingAnimationsEnabled) {
            if (mLevel < 100 && mPluggedIn) {
                startChargingAnimation(0);
            } else {
                cancelChargingAnimation();
            }
        }
    }

    // Some stuff required by Drawable.
    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    private void loadBatteryDrawables(Resources res, int style) {
        try {
            checkBatteryMeterDrawableValid(res, style);
        } catch (BatteryMeterDrawableException e) {
            Log.w(TAG, "Invalid themed battery meter drawable, falling back to system", e);
        }

        final int drawableResId = getBatteryDrawableResourceForStyle(style);
        mBatteryDrawable = (LayerDrawable) mContext.getDrawable(drawableResId);
        mFrameDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_frame);
        mFrameDrawable.setTint(mCurrentBackgroundColor != 0
                ? mCurrentBackgroundColor : res.getColor(R.color.batterymeter_frame_color));
        // Set the animated vector drawable we will be stop-animating
        final Drawable levelDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_fill);
        mLevelDrawable = new StopMotionVectorDrawable(levelDrawable);
        mBoltDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
    }

    private void checkBatteryMeterDrawableValid(Resources res, int style) {
        final int resId = getBatteryDrawableResourceForStyle(style);
        final Drawable batteryDrawable;
        try {
            batteryDrawable = mContext.getDrawable(resId);
        } catch (Resources.NotFoundException e) {
            throw new BatteryMeterDrawableException(res.getResourceName(resId) + " is an " +
                    "invalid drawable", e);
        }

        // Check that the drawable is a LayerDrawable
        if (!(batteryDrawable instanceof LayerDrawable)) {
            throw new BatteryMeterDrawableException("Expected a LayerDrawable but received a " +
                    batteryDrawable.getClass().getSimpleName());
        }

        final LayerDrawable layerDrawable = (LayerDrawable) batteryDrawable;
        final Drawable frame = layerDrawable.findDrawableByLayerId(R.id.battery_frame);
        final Drawable level = layerDrawable.findDrawableByLayerId(R.id.battery_fill);
        // Now, check that the required layers exist and are of the correct type
        if (frame == null) {
            throw new BatteryMeterDrawableException("Missing battery_frame drawble");
        }
        if (level != null) {
            // Check that the level drawable is an AnimatedVectorDrawable
            if (!(level instanceof AnimatedVectorDrawable)) {
                throw new BatteryMeterDrawableException("Expected a AnimatedVectorDrawable " +
                        "but received a " + level.getClass().getSimpleName());
            }
            // Make sure we can stop-motion animate the level drawable
            try {
                StopMotionVectorDrawable smvd = new StopMotionVectorDrawable(level);
                smvd.setCurrentFraction(0.5f);
            } catch (Exception e) {
                throw new BatteryMeterDrawableException("Unable to perform stop motion on " +
                        "battery_fill drawable", e);
            }
        } else {
            throw new BatteryMeterDrawableException("Missing battery_fill drawable");
        }
    }

    private int getBatteryDrawableResourceForStyle(final int style) {
        switch (style) {
            case BATTERY_STYLE_CIRCLE:
                return R.drawable.ic_battery_circle;
            case BATTERY_STYLE_PORTRAIT:
                return R.drawable.ic_battery_portrait;
            default:
                return 0;
        }
    }

    private int getBatteryDrawableStyleResourceForStyle(final int style) {
        switch (style) {
            case BATTERY_STYLE_CIRCLE:
                return R.style.BatteryMeterViewDrawable_Circle;
            case BATTERY_STYLE_PORTRAIT:
                return R.style.BatteryMeterViewDrawable_Portrait;
            default:
                return R.style.BatteryMeterViewDrawable;
        }
    }

    /**
     * Initializes all size dependent variables
     */
    private void init() {
        // Not much we can do with zero width or height, we'll get another pass later
        if (mWidth <= 0 || mHeight <= 0) return;

        final float widthDiv2 = mWidth / 2f;
        // text size is width / 2 - 2dp for wiggle room
        final float textSize = widthDiv2 - mContext.getResources().getDisplayMetrics().density * 2;
        mTextAndBoltPaint.setTextSize(textSize);
        mWarningTextPaint.setTextSize(textSize);

        Rect iconBounds = new Rect(0, 0, mWidth, mHeight);
        mBatteryDrawable.setBounds(iconBounds);

        // Calculate text position
        Rect bounds = new Rect();
        mTextAndBoltPaint.getTextBounds("99", 0, "99".length(), bounds);
        final boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        // Compute mTextX based on text gravity
        if ((mTextGravity & Gravity.START) == Gravity.START) {
            mTextX = isRtl ? mWidth : 0;
        } else if ((mTextGravity & Gravity.END) == Gravity.END) {
            mTextX = isRtl ? 0 : mWidth;
        } else if ((mTextGravity & Gravity.LEFT) == Gravity.LEFT) {
            mTextX = 0;
        } else if ((mTextGravity & Gravity.RIGHT) == Gravity.RIGHT) {
            mTextX = mWidth;
        } else {
            mTextX = widthDiv2;
        }

        // Compute mTextY based on text gravity
        if ((mTextGravity & Gravity.TOP) == Gravity.TOP) {
            mTextY = bounds.height();
        } else if ((mTextGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            mTextY = mHeight;
        } else {
            mTextY = widthDiv2 + bounds.height() / 2.0f;
        }

        if (mBoltDrawable != null) {
            updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
        }

        mInitialized = true;
    }

    // Creates a BitmapDrawable of the bolt so we can make use of
    // the XOR xfer mode with vector-based drawables
    private void updateBoltDrawableLayer(LayerDrawable batteryDrawable, Drawable boltDrawable) {
        BitmapDrawable newBoltDrawable;
        if (boltDrawable instanceof BitmapDrawable) {
            newBoltDrawable = (BitmapDrawable) boltDrawable.mutate();
        } else {
            Bitmap boltBitmap = createBoltBitmap(boltDrawable);
            if (boltBitmap == null) {
                // Not much to do with a null bitmap so keep original bolt for now
                return;
            }
            Rect bounds = boltDrawable.getBounds();
            newBoltDrawable = new BitmapDrawable(mContext.getResources(), boltBitmap);
            newBoltDrawable.setBounds(bounds);
        }
        newBoltDrawable.getPaint().set(mTextAndBoltPaint);
        batteryDrawable.setDrawableByLayerId(R.id.battery_charge_indicator, newBoltDrawable);
    }

    private Bitmap createBoltBitmap(Drawable boltDrawable) {
        // Not much we can do with zero width or height, we'll get another pass later
        if (mWidth <= 0 || mHeight <= 0) return null;

        Bitmap bolt;
        if (!(boltDrawable instanceof BitmapDrawable)) {
            Rect iconBounds = new Rect(0, 0, mWidth, mHeight);
            bolt = Bitmap.createBitmap(iconBounds.width(), iconBounds.height(),
                    Bitmap.Config.ARGB_8888);
            if (bolt != null) {
                Canvas c = new Canvas(bolt);
                c.drawColor(-1, PorterDuff.Mode.CLEAR);
                boltDrawable.draw(c);
            }
        } else {
            bolt = ((BitmapDrawable) boltDrawable).getBitmap();
        }

        return bolt;
    }

    private void drawBattery(Canvas canvas) {
        final int level = mLevel;

        mTextAndBoltPaint.setColor(getColorForLevel(level));

        // Make sure we don't draw the charge indicator if not plugged in
        final Drawable d = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
        if (d != null) {
            if (d instanceof BitmapDrawable) {
                // In case we are using a BitmapDrawable, which we should be unless something bad
                // happened, we need to change the paint rather than the alpha in case the blendMode
                // has been set to clear.  Clear always clears regardless of alpha level ;)
                BitmapDrawable bd = (BitmapDrawable) d;
                bd.getPaint().set(mPluggedIn ? mTextAndBoltPaint : mClearPaint);
            } else {
                d.setAlpha(mPluggedIn ? 255 : 0);
            }
        }

        // Now draw the level indicator
        // Set the level and tint color of the fill drawable
        int levelColor = getColorForLevel(level);
        mLevelAlpha = Color.alpha(levelColor);
        mLevelDrawable.setCurrentFraction(level / 100f);
        mLevelDrawable.setTint(getColorForLevel(level));
        mBatteryDrawable.draw(canvas);

        // If chosen by options, draw percentage text in the middle
        // Always skip percentage when 100, so layout doesnt break
        if (!mPluggedIn) {
            drawPercentageText(canvas);
        }
    }

    private void drawPercentageText(Canvas canvas) {
        final int level = mLevel;
        if (level > mCriticalLevel && mShowPercent && level != 100) {
            // Draw the percentage text
            String pctText = String.valueOf(SINGLE_DIGIT_PERCENT ? (level / 10) : level);
            mTextAndBoltPaint.setColor(getColorForLevel(level));
            canvas.drawText(pctText, mTextX, mTextY, mTextAndBoltPaint);
        } else if (level <= mCriticalLevel) {
            // Draw the warning text
            canvas.drawText(mWarningString, mTextX, mTextY, mWarningTextPaint);
        }
    }

    private Paint.Align getPaintAlignmentFromGravity(int gravity) {
        final boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if ((gravity & Gravity.START) == Gravity.START) {
            return isRtl ? Paint.Align.RIGHT : Paint.Align.LEFT;
        }
        if ((gravity & Gravity.END) == Gravity.END) {
            return isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT;
        }
        if ((gravity & Gravity.LEFT) == Gravity.LEFT) return Paint.Align.LEFT;
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) return Paint.Align.RIGHT;

        // Default to center
        return Paint.Align.CENTER;
    }

    private class BatteryMeterDrawableException extends RuntimeException {
        public BatteryMeterDrawableException(String detailMessage) {
            super(detailMessage);
        }

        public BatteryMeterDrawableException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}
