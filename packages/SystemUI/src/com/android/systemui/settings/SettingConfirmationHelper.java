/*
 * Copyright 2016 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.settings;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.systemui.statusbar.SettingConfirmationSnackbarView;

import static android.provider.Settings.Secure.getIntForUser;
import static android.provider.Settings.Secure.putIntForUser;

/**
 * Helper to manage user choices for OnTheSpot settings.
 */
public final class SettingConfirmationHelper {

    /** Whether to output debugging information to logs. */
    public static final boolean DEBUG = false;
    /** Log output tag. */
    public static final String LOG_TAG = "SettingConfirmation";

    /** Awaits for user input. Default value, when no user choice is known. */
    private static final int ASK = 0;
    /** Accepts the setting automatically. */
    private static final int ACCEPT = 1;
    /** Denies the setting automatically. */
    private static final int DENY = 2;

    /** No-op. Should not be used. */
    private SettingConfirmationHelper() {
    }

    /**
     * Gets a setting value. Internal convenience wrapper.
     *
     * @param resolver  {@link ContentResolver} for reading the setting
     * @param settingName  {@link String} name of the {@link Settings.Secure} to read
     */
    private static int getSetting(final ContentResolver resolver, final String settingName) {
        return getIntForUser(resolver, settingName, ASK, ActivityManager.getCurrentUser());
    }

    /**
     * Sets a setting value. Internal convenience wrapper.
     *
     * @param resolver  {@link ContentResolver} for writing the setting
     * @param settingName  {@link String} name of the {@link Settings.Secure} to write
     */
    private static void setSetting(final ContentResolver resolver, final String settingName,
            final int settingValue) {
        putIntForUser(resolver, settingName, settingValue, ActivityManager.getCurrentUser());
    }

    /**
     * Gets whether the user has chosen to allow the specific action. This call returns
     * without prompting the user. If no prerecorded user preference is known and the user
     * has to be prompted, then the fallback value passed in is returned instead.
     *
     * @param resolver  {@link ContentResolver} for reading the setting
     * @param settingName  {@link String} name of the {@link Settings.Secure} to read
     * @param fallback  default value to return if the setting is not set
     */
    public static boolean get(final ContentResolver resolver, final String settingName,
            final boolean fallback) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver == null");
        }
        if (settingName == null) {
            throw new IllegalArgumentException("settingName == null");
        }

        switch (getSetting(resolver, settingName)) {
            case ACCEPT:
                return true;
            case DENY:
                return false;
            case ASK:
            default:
                return fallback;
        }
    }

    /**
     * Sets whether the user has chosen to allow the specific action.
     *
     * @param resolver  {@link ContentResolver} for writing the setting
     * @param settingName  {@link String} name of the {@link Settings.Secure} to write
     * @param confirmed  true if the associated action should be automatically confirmed,
     *                   false if it should be automatically denied
     */
    public static void set(final ContentResolver resolver, final String settingName,
            final boolean confirmed) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver == null");
        }
        if (settingName == null) {
            throw new IllegalArgumentException("settingName == null");
        }

        setSetting(resolver, settingName, confirmed ? ACCEPT : DENY);
    }

    /**
     * Resets the prerecorded user preference for the specific action.
     *
     * @param resolver  {@link ContentResolver} for resetting the setting
     * @param settingName  {@link String} name of the {@link Settings.Secure} to reset
     */
    public static void reset(final ContentResolver resolver, final String settingName) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver == null");
        }
        if (settingName == null) {
            throw new IllegalArgumentException("settingName == null");
        }

        setSetting(resolver, settingName, ASK);
    }

    /**
     * Gets whether the user wants to allow the specific action. The callback may be
     * called twice - first with the default action and for a second time with the
     * user choice, but it implementations should not expect a set time of calls to
     * the callback.
     *
     * @param snackbar  {@link SettingConfirmationSnackbarView} being shown to the user
     * @param settingName  {@link String} name of the {@link Settings.Secure} to read and write
     * @param defaultValue  true if the associated action should be confirmed by default,
     *                      false if it should be denied by default
     * @param message  {@link String} message to display to the user
     * @param listener  {@link OnSettingChoiceListener} to notify about the choice
     * @param handler  {@link Handler} to notify the listener on,
     *                 or null to notify it on the UI thread instead
     */
    public static void prompt(final SettingConfirmationSnackbarView snackbar,
            final String settingName, final boolean defaultValue, final String message,
            final OnSettingChoiceListener listener, Handler handler) {
        if (snackbar == null) {
            throw new IllegalArgumentException("snackbar == null");
        }
        if (settingName == null) {
            throw new IllegalArgumentException("settingName == null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }

        final ContentResolver resolver = snackbar.getContext().getContentResolver();

        switch (getSetting(resolver, settingName)) {
            case ACCEPT:
                if (DEBUG) Log.d(LOG_TAG, settingName + ": Automatically confirming");
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        listener.onSettingConfirm(settingName);
                    }

                });
                break;
            case DENY:
                if (DEBUG) Log.d(LOG_TAG, settingName + ": Automatically denying");
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        listener.onSettingDeny(settingName);
                    }

                });
                break;
            case ASK:
            default:
                if (DEBUG) Log.d(LOG_TAG, settingName + ": Displaying confirmation request");

                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        if (defaultValue) {
                            listener.onSettingConfirm(settingName);
                        } else {
                            listener.onSettingDeny(settingName);
                        }
                    }

                });

                snackbar.show(settingName, message, new OnSettingChoiceListener() {

                    @Override
                    public void onSettingConfirm(final String settingNameTest) {
                        if (!settingNameTest.equals(settingName)) {
                            Log.w(LOG_TAG, settingName + ": Ignoring unexpected confirmation");
                            return;
                        }
                        if (DEBUG) Log.d(LOG_TAG, settingName + ": Confirming");
                        setSetting(resolver, settingName, ACCEPT);
                        if (defaultValue) {
                            // No need to touch the handler - this is already a callback.
                            listener.onSettingConfirm(settingName);
                        }
                    }

                    @Override
                    public void onSettingDeny(final String settingNameTest) {
                        if (!settingNameTest.equals(settingName)) {
                            Log.w(LOG_TAG, settingName + ": Ignoring unexpected denial");
                            return;
                        }
                        if (DEBUG) Log.d(LOG_TAG, settingName + ": Denying");
                        setSetting(resolver, settingName, DENY);
                        if (defaultValue) {
                            // No need to touch the handler - this is already a callback.
                            listener.onSettingDeny(settingName);
                        }
                    }

                }, handler);
                break;
        }
    }

    /**
     * User choice callback listener.
     */
    public interface OnSettingChoiceListener {
        /**
          * Handles confirmation for the requested action by the user.
          *
          * @param settingName  {@link String} name of the {@link Settings.Secure}
          *                     for which a choice was requested
          */
        void onSettingConfirm(String settingName);

        /**
          * Handles denial for the requested action by the user.
          *
          * @param settingName  {@link String} name of the {@link Settings.Secure}
          *                     for which a choice was requested
          */
        void onSettingDeny(String settingName);
    }

}
