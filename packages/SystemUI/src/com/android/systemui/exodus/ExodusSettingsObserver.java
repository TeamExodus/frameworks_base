/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.exodus;

import libcore.icu.LocaleData;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * ExodusSettingsObserver
 */
public class ExodusSettingsObserver extends ContentObserver {

    private ContentResolver resolver = null;
    private static boolean isVolteEnabled = false;
    private static boolean isStatusBarDoubleTapEnabled = false;
    private static boolean isKeyguardDoubleTapEnabled = false;
    private static boolean isQuickPullEnabled = false;
    private static boolean hasReadSettings = false;

    public ExodusSettingsObserver(Handler handler, Context context) {
        super(handler);
        resolver = context.getContentResolver();
    }

    private void observe() {
        resolver.registerContentObserver(Settings.System
                .getUriFor(Settings.System.SHOW_VOLTE),
                true, this, UserHandle.USER_ALL);

        resolver.registerContentObserver(Settings.System
                .getUriFor(Settings.System.DOUBLE_TAP_SLEEP_GESTURE),
                false, this, UserHandle.USER_ALL);

        resolver.registerContentObserver(Settings.System
                .getUriFor(Settings.System.DOUBLE_TAP_SLEEP_ANYWHERE),
                false, this, UserHandle.USER_ALL);

        resolver.registerContentObserver(Settings.Secure
                .getUriFor(Settings.Secure.QUICK_SETTINGS_QUICK_PULL_DOWN),
                false, this, UserHandle.USER_ALL);
        if(!hasReadSettings) {
            setVolteLabelEnabled();
            readSettings();
            hasReadSettings = true;
        }
    }


    public void registerClass() {
        observe();
    }

    public void unRegisterClass() {
        resolver.unregisterContentObserver(this);
    }

    /*
     *  @hide
     */
    @Override
    public void onChange(boolean selfChange) {
        setVolteLabelEnabled();
        readSettings();
    }

    public void readSettings() {
        
        isStatusBarDoubleTapEnabled = Settings.System.getIntForUser(resolver,
            Settings.System.DOUBLE_TAP_SLEEP_GESTURE, 0,
            UserHandle.USER_CURRENT) == 1;

        isKeyguardDoubleTapEnabled = Settings.System.getIntForUser(resolver,
            Settings.System.DOUBLE_TAP_SLEEP_ANYWHERE, 0,
            UserHandle.USER_CURRENT) == 1;

        isQuickPullEnabled = Settings.Secure.getIntForUser(resolver,
            Settings.Secure.QUICK_SETTINGS_QUICK_PULL_DOWN, 0,
            UserHandle.USER_CURRENT) == 1;

    }

    public void setVolteLabelEnabled() {
        isVolteEnabled = Settings.System.getIntForUser(resolver,
            Settings.System.SHOW_VOLTE, 1,
            UserHandle.USER_CURRENT) == 1;
    }

    public static boolean isVolteLabelEnabled() {
        return isVolteEnabled;
    }

    public static boolean isStatusBarDoubleTapEnabled() {
        return isStatusBarDoubleTapEnabled;
    }

    public static boolean isKeyguardDoubleTapEnabled() {
        return isKeyguardDoubleTapEnabled;
    }

    public static boolean isQuickPullEnabled() {
        return isQuickPullEnabled;
    }
}
