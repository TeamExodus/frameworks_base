/*
<<<<<<< HEAD
 * Copyright (C) 2014-2016 The CyanogenMod Project
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

import android.content.Context;
import android.icu.text.NumberFormat;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.tuner.TunerService;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback, TunerService.Tunable {

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
            "system:" + Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT;

    private static final String STATUS_BAR_BATTERY_STYLE =
            "system:" + Settings.System.STATUS_BAR_BATTERY_STYLE;

    private BatteryController mBatteryController;

    private boolean mRequestedVisibility;

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
=======
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.android.systemui;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.ArraySet;
import android.view.View;
import android.widget.TextView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import android.database.ContentObserver;
import android.net.Uri;
import android.util.Log;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback, TunerService.Tunable{

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";

    private BatteryController mBatteryController;
    private boolean mShow;
    private boolean mBatteryCharging = false;
    private boolean mBatteryEnabled;
    private boolean mBatteryPct;
    private final String mSlotBattery;


    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            loadShowBatteryTextSetting();
            setBatteryVisibility();
        }
    };

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSlotBattery = context.getString(
                com.android.internal.R.string.status_bar_battery);
        mBatteryPct = context.getResources().getBoolean(
                R.bool.config_showBatteryPercentage);
        loadShowBatteryTextSetting();
        setBatteryVisibility();
    }

    private void loadShowBatteryTextSetting() {
        mShow = 0 != Settings.System.getInt(getContext().getContentResolver(),
            STATUS_BAR_SHOW_BATTERY_PERCENT, 0);
    }

    private void setBatteryVisibility() {
        setVisibility( mBatteryEnabled
            && (mBatteryCharging || (mBatteryPct && mShow)) ? View.VISIBLE : View.GONE);
    }

    public void setBatteryCharging(boolean isCharging){
        mBatteryCharging = isCharging;
        setBatteryVisibility();
>>>>>>> 9f8c8663ff0ed5477930608b0aca871ecc883d66
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
<<<<<<< HEAD
        setText(NumberFormat.getPercentInstance().format((double) level / 100.0));
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mBatteryController.addStateChangedCallback(this);
        TunerService.get(getContext()).addTunable(this, true,
                STATUS_BAR_SHOW_BATTERY_PERCENT, STATUS_BAR_BATTERY_STYLE);
=======
        setText(getResources().getString(R.string.battery_level_template, level));
    }

    public void setBatteryController(BatteryController batteryController) {
        if(batteryController != null){
            mBatteryController = batteryController;
            mBatteryController.addStateChangedCallback(this);
        }
>>>>>>> 9f8c8663ff0ed5477930608b0aca871ecc883d66
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
<<<<<<< HEAD
        // Unused
=======

    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (StatusBarIconController.ICON_BLACKLIST.equals(key)) {
            ArraySet<String> icons = StatusBarIconController.getIconBlacklist(newValue);
            mBatteryEnabled = !icons.contains(mSlotBattery);
            setBatteryVisibility();
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                STATUS_BAR_SHOW_BATTERY_PERCENT), false, mObserver);
        TunerService.get(getContext()).addTunable(this, StatusBarIconController.ICON_BLACKLIST);
>>>>>>> 9f8c8663ff0ed5477930608b0aca871ecc883d66
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

<<<<<<< HEAD
        TunerService.get(getContext()).removeTunable(this);
        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_SHOW_BATTERY_PERCENT:
                mRequestedVisibility = newValue != null && Integer.parseInt(newValue) == 2;
                setVisibility(mRequestedVisibility ? View.VISIBLE : View.GONE);
                break;
            case STATUS_BAR_BATTERY_STYLE:
                final int value = newValue == null ?
                        BatteryMeterDrawable.BATTERY_STYLE_PORTRAIT : Integer.parseInt(newValue);
                switch (value) {
                    case BatteryMeterDrawable.BATTERY_STYLE_TEXT:
                        setVisibility(View.VISIBLE);
                        break;
                    case BatteryMeterDrawable.BATTERY_STYLE_HIDDEN:
                        setVisibility(View.GONE);
                        break;
                    default:
                        setVisibility(mRequestedVisibility ? View.VISIBLE : View.GONE);
                        break;
                }
                break;
            default:
                break;
        }
=======
        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
        TunerService.get(getContext()).removeTunable(this);
>>>>>>> 9f8c8663ff0ed5477930608b0aca871ecc883d66
    }
}
