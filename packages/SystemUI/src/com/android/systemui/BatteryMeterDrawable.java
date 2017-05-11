/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.database.ContentObserver;
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
import android.graphics.drawable.StopMotionVectorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import com.android.systemui.statusbar.policy.BatteryController;

public class BatteryMeterDrawable extends Drawable implements
        BatteryController.BatteryStateChangeCallback {

    public static final String TAG = BatteryMeterDrawable.class.getSimpleName();
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
            Settings.Secure.STATUS_BAR_SHOW_BATTERY_PERCENT;
    private static final String FORCE_CHARGE_BATTERY_TEXT =
            Settings.Secure.FORCE_CHARGE_BATTERY_TEXT;
    private static final String TEXT_CHARGING_SYMBOL =
            Settings.Secure.TEXT_CHARGING_SYMBOL;

    // Values for the different battery styles
    public static final int BATTERY_STYLE_PORTRAIT  = 0;
    public static final int BATTERY_STYLE_CIRCLE    = 2;
    public static final int BATTERY_STYLE_HIDDEN    = 4;
    public static final int BATTERY_STYLE_LANDSCAPE = 5;
    public static final int BATTERY_STYLE_TEXT      = 6;
    public static final int BATTERY_STYLE_SOLID     = 7;

    private final int[] mColors;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;

    private int mShowPercent;
    private int mIconTint;
    private float mOldDarkIntensity = 0f;

    private int mHeight;
    private int mWidth;
    private String mWarningString;
    private final int mCriticalLevel;
    private int mStyle;
    private boolean mIsBatteryTile; //if true, we are in the tile, if false, we are in the statusbar icon

    private BatteryController mBatteryController;
    private boolean mPowerSaveEnabled;

    private int mDarkModeBackgroundColor;
    private int mDarkModeBatteryMeterFrameColor;
    private int mDarkModeFillColor;

    private int mLightModeBackgroundColor;
    private int mLightModeBatteryMeterFrameColor;
    private int mLightModeBatteryMeterFrameColorTile;
    private int mLightModeFillColor;

    private final SettingObserver mSettingObserver = new SettingObserver();

    private final Context mContext;
    private final Handler mHandler;

    private int mLevel = -1;
    private boolean mPluggedIn;
    private boolean mForceChargeBatteryText;
    private int  mTextChargingSymbol;
    private boolean mListening;

    private float mTextX, mTextY; // precalculated position for drawText() to appear centered

    private boolean mInitialized;

    private Paint mTextAndBoltPaint; //color for percentage, bolt, plus
    private Paint mClearPaint;

    private LayerDrawable mBatteryDrawable;
    private Drawable mFrameDrawable;
    private StopMotionVectorDrawable mLevelDrawable;
    private Drawable mBoltDrawable;
    private Drawable mPlusDrawable;
    private ValueAnimator mAnimator;

    private int mTextGravity;

    private int mCurrentBackgroundColor = 0;
    private int mCurrentFillColor = 0;
    private int mTileNormalLevelColor;
    private int mTileLowLevelColor;

    private boolean isPctToBeWhiteOrRed;

    public BatteryMeterDrawable(Context context, Handler handler) {
        // Portrait is the default drawable style
        this(context, handler, BATTERY_STYLE_PORTRAIT, false);
    }

    public BatteryMeterDrawable(Context context, Handler handler, int style) {
        this(context, handler, style, false);
    }

    public BatteryMeterDrawable(Context context, Handler handler, int style, boolean isBatteryTile) {
        mContext = context;
        mHandler = handler;
        mStyle = style;
        mIsBatteryTile = isBatteryTile;
        final Resources res = context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        mTileNormalLevelColor = context.getColor(R.color.battery_normal_charge_level_tile);
        mTileLowLevelColor = context.getColor(R.color.battery_low_charge_level_tile);

        final int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            mColors[2*i+1] = colors.getColor(i, 0);
        }
        /* the "for" above will give the following:
        mColors[0] = 15
        mColors[1] = white color
        mColors[2] = 100
        mColors[3] = red color
        */
        levels.recycle();
        colors.recycle();
        updateShowPercent();
        updateForceChargeBatteryText();
        updateCustomChargingSymbol();
        mWarningString = context.getString(R.string.battery_meter_very_low_overlay_symbol);
        mCriticalLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        //mIconTint is the level color, it will be handled by setDarkIntensity according to the darkintensity state
        mIconTint = mContext.getResources().getColor(R.color.battery_charge_level_on_normal_statusbar);

        loadBatteryDrawables(res, style);

        // Load text gravity
        final int[] attrs = new int[] { android.R.attr.gravity, R.attr.blendMode };
        final int resId = getBatteryDrawableStyleResourceForStyle(style);
        if (resId != 0) {
            TypedArray a = mContext.obtainStyledAttributes(resId, attrs);
            mTextGravity = a.getInt(0, Gravity.CENTER);
            a.recycle();
        } else {
            mTextGravity = Gravity.CENTER;
        }

        mTextAndBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        mTextAndBoltPaint.setTypeface(font);
        mTextAndBoltPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));
        mTextAndBoltPaint.setColor(mIsBatteryTile || mCurrentFillColor == 0 ? getBoltColor() : mCurrentFillColor);

        mClearPaint = new Paint();
        mClearPaint.setColor(0);

        mDarkModeFillColor = context.getColor(R.color.battery_charge_level_on_light_statusbar);
        mLightModeFillColor = context.getColor(R.color.battery_charge_level_on_normal_statusbar);

        mDarkModeBatteryMeterFrameColor =
                context.getColor(R.color.batterymeter_frame_color_darkintensity);
        mLightModeBatteryMeterFrameColor =
                context.getColor(R.color.batterymeter_frame_color);
        mLightModeBatteryMeterFrameColorTile =
                context.getColor(R.color.batterymeter_frame_color_tile);

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
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(STATUS_BAR_SHOW_BATTERY_PERCENT),
                false, mSettingObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(FORCE_CHARGE_BATTERY_TEXT),
                false, mSettingObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(TEXT_CHARGING_SYMBOL),
                false, mSettingObserver);
        updateShowPercent();
        updateForceChargeBatteryText();
        updateCustomChargingSymbol();
        mBatteryController.addStateChangedCallback(this);
    }

    public void stopListening() {
        mListening = false;
        mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
        mBatteryController.removeStateChangedCallback(this);
    }

    public void disableShowPercent() {
        mShowPercent = 0;
        postInvalidate();
    }

    private void postInvalidate() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidateSelf();
            }
        });
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mPowerSaveEnabled = mBatteryController.isPowerSave();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        mLevel = level;
        mPluggedIn = pluggedIn;
        if (level <31) {
            //on non circle battery styles, when level is <31, the level bar doesn't cover the level pct txt inside
            //the battery icon, so we'll set the pct txt color to white to have it more visible on the frame background
            isPctToBeWhiteOrRed = true;
        }

        if (mStyle == BATTERY_STYLE_SOLID) {
            animateSolidBattery(level, pluggedIn, charging);
        }

        postInvalidate();
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        mPowerSaveEnabled = isPowerSave;
        invalidateSelf();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        mHeight = bottom - top;
        mWidth = right - left;
    }

    private void updateShowPercent() {
        mShowPercent = Settings.Secure.getInt(mContext.getContentResolver(),
                STATUS_BAR_SHOW_BATTERY_PERCENT, 0);
    }

    private void updateForceChargeBatteryText() {
        mForceChargeBatteryText = Settings.Secure.getInt(mContext.getContentResolver(),
                FORCE_CHARGE_BATTERY_TEXT, 0) == 1;
    }

    private void updateCustomChargingSymbol() {
        mTextChargingSymbol = Settings.Secure.getInt(mContext.getContentResolver(),
                TEXT_CHARGING_SYMBOL, 0);
    }

    private int getColorForLevel(int percent) {
        return getColorForLevel(percent, false);
    }

    private int getColorForLevel(int percent, boolean isChargeLevel) {
        //tile battery levels color
        if (mIsBatteryTile) {
            if (mPowerSaveEnabled || percent > mColors[0] /*percent>15*/) {
                if (isChargeLevel) {
                    //isChargeLevel is the level color, not mTextAndBoltPaint
                    return mTileNormalLevelColor; //white
                } else { //mTextAndBoltPaint
                    return getBoltColor();
                }
            //no powersave and pct<15 and style circle
            } else if (mStyle == BATTERY_STYLE_CIRCLE) {
                if (!mPluggedIn) {
                    return mTileLowLevelColor; //red
                } else {
                    return getBoltColor();
                }
            }
        }
        //now statusbar battery levels color
        if (mPowerSaveEnabled || mPluggedIn) {
            return mColors[mColors.length - 1]; //mColors[3] (white)
        }
        //normal statusbar color
        int thresh = 0;
        int color = 0;
        for (int i = 0; i < mColors.length; i += 2) {
            thresh = mColors[i];  //mColors[0] (level 15) and mColors[2] (level 100)
            color = mColors[i+1]; //mColors[1] (red) and mColors[3] (white)
            if (percent <= thresh) {
                // Respect tinting for "normal" level
                //red if level pct<=15, otherwise white (mIconTint to respect darkintensity)
                if (i == mColors.length - 2 /*mColors[2] (100)*/) {
                    return mIconTint;
                } else {
                    return color; //mColors[1] (red)
                }
            }
        }
        return color; //mColors[3] (white)
    }

    public void animateSolidBattery(int level, boolean pluggedIn, boolean charging) {
        if (charging) {
            if (mAnimator != null) mAnimator.cancel();

            final int defaultAlpha = mLevelDrawable.getAlpha();
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
                    mLevelDrawable.setAlpha(defaultAlpha);
                    mAnimator = null;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mLevelDrawable.setAlpha(defaultAlpha);
                    mAnimator = null;
                }
            });
            mAnimator.setDuration(2000);
            mAnimator.start();
        }
    }

    public void setDarkIntensity(float darkIntensity) {
        if (darkIntensity == mOldDarkIntensity) {
            return;
        }
        mCurrentBackgroundColor = getBackgroundColor(darkIntensity);
        mCurrentFillColor = getFillColor(darkIntensity);
        mIconTint = mCurrentFillColor;
        if (mBoltDrawable != null) {
            //if we are in the tile we must avoid darkintensity. We are good about this because
            //it will be overridden thanks to the following updateBoltDrawableLayer call
            mBoltDrawable.setTint(0xff000000 | mIconTint);
        }
        mFrameDrawable.setTint(mIsBatteryTile ? mLightModeBatteryMeterFrameColorTile : mCurrentBackgroundColor);
        updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
        updatePlusDrawableLayer(mBatteryDrawable, mPlusDrawable);
        invalidateSelf();
        mOldDarkIntensity = darkIntensity;
    }

    private int getBackgroundColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeBatteryMeterFrameColor, mDarkModeBatteryMeterFrameColor);
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

    private final class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateShowPercent();
            updateForceChargeBatteryText();
            updateCustomChargingSymbol();
            postInvalidate();
        }
    }

    private void loadBatteryDrawables(Resources res, int style) {
        try {
            checkBatteryMeterDrawableValid(res, style);
        } catch (BatteryMeterDrawableException e) {
            Log.w(TAG, "Invalid themed battery meter drawable, falling back to system", e);
        }
        final int drawableResId = getBatteryDrawableResourceForStyle(style);
        mBatteryDrawable = (LayerDrawable) res.getDrawable(drawableResId, null);
        mFrameDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_frame);
        mFrameDrawable.setTint((mCurrentBackgroundColor != 0 && !mIsBatteryTile)
                ? mCurrentBackgroundColor : (!mIsBatteryTile ? res.getColor(R.color.batterymeter_frame_color)
                : res.getColor(R.color.batterymeter_frame_color_tile)));
        // Set the animated vector drawable we will be stop-animating
        final Drawable levelDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_fill);
        mLevelDrawable = new StopMotionVectorDrawable(levelDrawable);
        mBoltDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
        mBoltDrawable.setTint(getBoltColor());
        mPlusDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_powersave_indicator);
        mPlusDrawable.setTint(getPlusColor());
    }

    private void checkBatteryMeterDrawableValid(Resources res, int style) {
        final int resId = getBatteryDrawableResourceForStyle(style);
        final Drawable batteryDrawable;
        try {
            batteryDrawable = res.getDrawable(resId, null);
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
        final Drawable bolt = layerDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
        final Drawable plus = layerDrawable.findDrawableByLayerId(R.id.battery_powersave_indicator);
        // Now, check that the required layers exist and are of the correct type
        if (frame == null) {
            throw new BatteryMeterDrawableException("Missing battery_frame drawble");
        }
        if (bolt == null) {
            throw new BatteryMeterDrawableException(
                    "Missing battery_charge_indicator drawable");
        }
        if (plus == null) {
            throw new BatteryMeterDrawableException(
                    "Missing battery_powersave_indicator drawable");
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
            case BATTERY_STYLE_LANDSCAPE:
                return R.drawable.ic_battery_landscape;
            case BATTERY_STYLE_CIRCLE:
                return R.drawable.ic_battery_circle;
            case BATTERY_STYLE_PORTRAIT:
                return R.drawable.ic_battery_portrait;
            case BATTERY_STYLE_SOLID:
                return R.drawable.ic_battery_solid;
            default:
                return 0;
        }
    }

    private int getBatteryDrawableStyleResourceForStyle(final int style) {
        switch (style) {
            case BATTERY_STYLE_LANDSCAPE:
                return R.style.BatteryMeterViewDrawable_Landscape;
            case BATTERY_STYLE_CIRCLE:
                return R.style.BatteryMeterViewDrawable_Circle;
            case BATTERY_STYLE_PORTRAIT:
                return R.style.BatteryMeterViewDrawable_Portrait;
            case BATTERY_STYLE_SOLID:
                return R.style.BatteryMeterViewDrawable_Solid;
            default:
                return R.style.BatteryMeterViewDrawable;
        }
    }

    private int getBoltColor() {
        if (mIsBatteryTile) {
            if (mStyle == BATTERY_STYLE_CIRCLE) {
                return mContext.getResources().getColor(R.color.batterymeter_circle_tile_bolt_plus_color);
            }
            return (isPctToBeWhiteOrRed ? Color.WHITE : mContext.getResources().getColor(R.color.batterymeter_tile_bolt_plus_color));
        }
        //statusbar battery
        if (mStyle == BATTERY_STYLE_CIRCLE) {
            return mIconTint; //same color of the circle level
        }
        return (isPctToBeWhiteOrRed ? mIconTint : mContext.getResources().getColor(R.color.batterymeter_bolt_color));
    }

    private int getPlusColor() {
        if (mIsBatteryTile) {
            if (mStyle == BATTERY_STYLE_CIRCLE) {
                return mContext.getResources().getColor(R.color.batterymeter_circle_tile_bolt_plus_color);
            }
            return (isPctToBeWhiteOrRed ? Color.WHITE : mContext.getResources().getColor(R.color.batterymeter_tile_bolt_plus_color));
        }
        if (mStyle == BATTERY_STYLE_CIRCLE) {
            return mIconTint;
        }
        return (isPctToBeWhiteOrRed ? mIconTint : mContext.getResources().getColor(R.color.batterymeter_bolt_color));
    }

    /**
     * Initializes all size dependent variables
     */
    private void init() {
        // Not much we can do with zero width or height, we'll get another pass later
        if (mWidth <= 0 || mHeight <= 0) return;

        final float widthDiv2 = mWidth / 2f;
        final float textSize;
        switch(mStyle) {
            case BATTERY_STYLE_CIRCLE:
                textSize = widthDiv2 * 0.8f;
                break;
            case BATTERY_STYLE_LANDSCAPE:
                textSize = widthDiv2 * 1.0f;
                break;
            case BATTERY_STYLE_SOLID:
                textSize = widthDiv2 * 0.8f;
                break;
            default:
                textSize = widthDiv2 * 0.9f;
                break;
        }

        mTextAndBoltPaint.setTextSize(textSize);

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

        updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
        updatePlusDrawableLayer(mBatteryDrawable, mPlusDrawable);

        mInitialized = true;
    }

    // Creates a BitmapDrawable of the bolt so we can make use of
    // the XOR xfer mode with vector-based drawables
    private void updateBoltDrawableLayer(LayerDrawable batteryDrawable, Drawable boltDrawable) {
        BitmapDrawable newBoltDrawable;
        if (boltDrawable instanceof BitmapDrawable) {
            newBoltDrawable = (BitmapDrawable) boltDrawable.mutate();
        } else {
            Bitmap boltBitmap = createBoltPlusBitmap(boltDrawable);
            if (boltBitmap == null) {
                // Not much to do with a null bitmap so keep original bolt for now
                return;
            }
            Rect bounds = boltDrawable.getBounds();
            newBoltDrawable = new BitmapDrawable(mContext.getResources(), boltBitmap);
            newBoltDrawable.setBounds(bounds);
        }
        newBoltDrawable.getPaint().set(mTextAndBoltPaint);
        if (mIsBatteryTile) {
            newBoltDrawable.setTint(getBoltColor());
        }
        batteryDrawable.setDrawableByLayerId(R.id.battery_charge_indicator, newBoltDrawable);
    }

    private Bitmap createBoltPlusBitmap(Drawable boltDrawable) {
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

    private void updatePlusDrawableLayer(LayerDrawable batteryDrawable, Drawable plusDrawable) {
        BitmapDrawable newPlusDrawable;
        if (plusDrawable instanceof BitmapDrawable) {
            newPlusDrawable = (BitmapDrawable) plusDrawable.mutate();
        } else {
            Bitmap plusBitmap = createBoltPlusBitmap(plusDrawable);
            if (plusBitmap == null) {
                // Not much to do with a null bitmap so keep original plus for now
                return;
            }
            Rect bounds = plusDrawable.getBounds();
            newPlusDrawable = new BitmapDrawable(mContext.getResources(), plusBitmap);
            newPlusDrawable.setBounds(bounds);
        }
        newPlusDrawable.getPaint().set(mTextAndBoltPaint);
        if (mIsBatteryTile) {
            newPlusDrawable.setTint(getPlusColor());
        }
        batteryDrawable.setDrawableByLayerId(R.id.battery_powersave_indicator, newPlusDrawable);
    }

    private void updatePortDuffMode() {
        final int level = mLevel;
        if (level > mColors[0] /*15*/ && level <31 && mStyle != BATTERY_STYLE_CIRCLE) {
            mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
            mTextAndBoltPaint.setColor(mIsBatteryTile ? Color.WHITE : mIconTint); //mIconTint so when darkintensity enabled the pct is dark and more visible
        } else if (level <= mColors[0] /*15*/ && mStyle != BATTERY_STYLE_CIRCLE){
            mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
            mTextAndBoltPaint.setColor(getColorForLevel(level));
        } else {
            if (mIsBatteryTile) {
                mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
            } else  {
                //have to recreate the typedarray here otherwise the XOR mode doesn't apply well
                final int[] attrs = new int[] { android.R.attr.gravity, R.attr.blendMode };
                final int resId = getBatteryDrawableStyleResourceForStyle(mStyle);
                if (resId != 0) {
                    TypedArray a = mContext.obtainStyledAttributes(resId, attrs);
                    mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.intToMode(a.getInt(1, PorterDuff.modeToInt(PorterDuff.Mode.XOR)))));
                    a.recycle();
                }
            }
            mTextAndBoltPaint.setColor(getColorForLevel(level));
        }
    }

    private void drawBattery(Canvas canvas) {
        final int level = mLevel;
        updatePortDuffMode();
        handleBoltVisibility();
        handlePlusVisibility();
        // Now draw the level indicator
        // Set the level and tint color of the fill drawable
        mLevelDrawable.setCurrentFraction(level / 100f);
        mLevelDrawable.setTint(getColorForLevel(level, true));
        mBatteryDrawable.draw(canvas);

        // If chosen by options, draw percentage text in the middle
        // Always skip percentage when 100, so layout doesnt break
        if (!mPluggedIn || (mPluggedIn && !mForceChargeBatteryText)) {
            drawPercentageText(canvas);
        }
    }

    private void handleBoltVisibility() {
        final Drawable d = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
        if (d != null) {
            if (d instanceof BitmapDrawable) {
                // In case we are using a BitmapDrawable, which we should be unless something bad
                // happened, we need to change the paint rather than the alpha in case the blendMode
                // has been set to clear.  Clear always clears regardless of alpha level ;)
                final BitmapDrawable bd = (BitmapDrawable) d;
                bd.getPaint().set(!mPluggedIn || (mPluggedIn && mShowPercent == 1 && (!mForceChargeBatteryText
                        || (mForceChargeBatteryText && mTextChargingSymbol != 0 && !mIsBatteryTile)))
                        || (mPluggedIn && mShowPercent == 2 && mTextChargingSymbol != 0)
                        || (mPluggedIn && mShowPercent == 0 && (mForceChargeBatteryText && mTextChargingSymbol != 0))
                        ? mClearPaint : mTextAndBoltPaint);
                if (mIsBatteryTile) {
                    mBoltDrawable.setTint(getBoltColor());
                }
            } else {
                d.setAlpha(!mPluggedIn || (mPluggedIn && mShowPercent == 1 && (!mForceChargeBatteryText
                        || (mForceChargeBatteryText && mTextChargingSymbol != 0 && !mIsBatteryTile)))
                        || (mPluggedIn && mShowPercent == 2 && mTextChargingSymbol != 0)
                        || (mPluggedIn && mShowPercent == 0 && (mForceChargeBatteryText && mTextChargingSymbol != 0)) ? 0 : 255);
            }
        }
    }

    private void handlePlusVisibility() {
        final Drawable p = mBatteryDrawable.findDrawableByLayerId(R.id.battery_powersave_indicator);
        if (p instanceof BitmapDrawable) {
            final BitmapDrawable bpd = (BitmapDrawable) p;
            bpd.getPaint().set((mLevel <= mCriticalLevel) || !mPowerSaveEnabled || (mPowerSaveEnabled && mShowPercent == 1)
                    ? mClearPaint : mTextAndBoltPaint);
            if (mIsBatteryTile) {
                mPlusDrawable.setTint(getPlusColor());
            }
        } else {
            p.setAlpha((mLevel <= mCriticalLevel) || !mPowerSaveEnabled || (mPowerSaveEnabled && mShowPercent == 1) ? 0 : 255);
        }
    }

    private void drawPercentageText(Canvas canvas) {
        final int level = mLevel;
        if (level > mCriticalLevel && mShowPercent == 1 && level != 100) {
            // Draw the percentage text
            String pctText = String.valueOf(level);
            canvas.drawText(pctText, mTextX, mTextY, mTextAndBoltPaint);
            if (mIsBatteryTile) {
                mBoltDrawable.setTint(getBoltColor());
                mPlusDrawable.setTint(getPlusColor());
            }
        } else if (level <= mCriticalLevel) {
            // Draw the warning text
            canvas.drawText(mWarningString, mTextX, mTextY, mTextAndBoltPaint);
        }
    }

    private Paint.Align getPaintAlignmentFromGravity(int gravity) {
        final boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        switch ((gravity & Gravity.START)) {
            case Gravity.START:
                return isRtl ? Paint.Align.RIGHT : Paint.Align.LEFT;
        }
        switch ((gravity & Gravity.END)) {
            case Gravity.END:
                return isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT;
        }
        switch ((gravity & Gravity.LEFT)) {
            case Gravity.LEFT:
                return Paint.Align.LEFT;
        }
        switch ((gravity & Gravity.RIGHT)) {
            case Gravity.RIGHT:
                return Paint.Align.RIGHT;
        }

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
