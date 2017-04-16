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
    private static boolean isVolteEnabled = false;;

    public ExodusSettingsObserver(Handler handler, Context context) {
        super(handler);
        resolver = context.getContentResolver();
    }

    private void observe() {
        resolver.registerContentObserver(Settings.System
                .getUriFor(Settings.System.SHOW_VOLTE),
                false, this, UserHandle.USER_ALL);
        setVolteLabelEnabled();
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
    }

    public void setVolteLabelEnabled() {
        isVolteEnabled = Settings.System.getIntForUser(resolver,
            Settings.System.SHOW_VOLTE, 0,
            UserHandle.USER_CURRENT) == 1;
    }

    public boolean isVolteLabelEnabled() {
        return isVolteEnabled;
    }

}
