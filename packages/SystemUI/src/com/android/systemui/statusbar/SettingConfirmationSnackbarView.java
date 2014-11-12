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

package com.android.systemui.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.settings.SettingConfirmationHelper;

/**
 * Snackbar view for use with OnTheSpot.
 */
public class SettingConfirmationSnackbarView extends RelativeLayout {

    /** Whether to output debugging information to logs. */
    private static final boolean DEBUG = SettingConfirmationHelper.DEBUG;
    /** Log output tag. */
    private static final String LOG_TAG = SettingConfirmationHelper.LOG_TAG;

    /** Amount of milliseconds for how long the animations should last. */
    private static final int ANIMATION_DURATION = 250;
    /** Amount of milliseconds for how long to show the snackbar. */
    private static final int TIMEOUT_DURATION = 30000;

    /** Listener for user choice buttons. */
    private final View.OnClickListener mButtonListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            if (v.equals(mConfirmButton)) {
                if (mCallbackHandler != null) {
                    mCallbackHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            if (mCallback != null && mSettingName != null) {
                                mCallback.onSettingConfirm(mSettingName);
                            }
                            mCallback = null;
                            mSettingName = null;
                        }

                    });
                    mCallbackHandler = null;
                }

                hide();
            }

            if (v.equals(mDenyButton)) {
                if (mCallbackHandler != null) {
                    mCallbackHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            if (mCallback != null && mSettingName != null) {
                                mCallback.onSettingDeny(mSettingName);
                            }
                            mCallback = null;
                            mSettingName = null;
                        }

                    });
                    mCallbackHandler = null;
                }

                hide();
            }
        }

    };

    /** Runnable for scheduling hiding. */
    private final Runnable mHideRunnable = new Runnable() {

        @Override
        public void run() {
            hide();
        }

    };

    /** Main handler to do any serious main work in. Fallback for callbacks. */
    private final Handler mMainHandler;

    /** Description text view for informing the user. */
    private TextView mDescription = null;

    /** User choice button for confirming the action. */
    private View mConfirmButton = null;

    /** User choice button for denying the action. */
    private View mDenyButton = null;

    /** Setting name that is being confirmed. To be passed back to the callback. */
    private String mSettingName = null;

    /** Callback to call upon user choice. */
    private SettingConfirmationHelper.OnSettingChoiceListener mCallback = null;

    /** Handler to send callbacks on. */
    private Handler mCallbackHandler = null;

    /**
     * Constructs the snackbar view object.
     *
     * @param context  {@link Context} the view is going to run in
     * @param attrs  {@link AttributeSet} of the XML tag that is inflating the view
     */
    public SettingConfirmationSnackbarView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        setTranslationY(getHeight());
        setVisibility(View.GONE);

        mDescription = (TextView) findViewById(R.id.description);

        mConfirmButton = findViewById(R.id.action_confirm);
        mConfirmButton.setOnClickListener(mButtonListener);
        mConfirmButton.setClickable(true);

        mDenyButton = findViewById(R.id.action_deny);
        mDenyButton.setOnClickListener(mButtonListener);
        mDenyButton.setClickable(true);
    }

    /**
     * Shows the snackbar.
     *
     * @param settingName  {@link String} name of the {@link Settings.Secure} being changed
     * @param message  {@link String} message to display to the user
     * @param listener  {@link SettingConfirmationHelper.OnSettingChoiceListener} to notify
     *                  about the choice
     * @param handler  {@link Handler} to notify the listener on,
     *                 or null to notify it on the UI thread instead
     */
    public void show(final String settingName, final String message,
            final SettingConfirmationHelper.OnSettingChoiceListener callback,
            Handler handler) {
        if (settingName == null) {
            throw new IllegalArgumentException("settingName == null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message == null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        if (handler == null) {
            handler = mMainHandler;
        }

        if (DEBUG) Log.d(LOG_TAG, "Showing the snackbar view");

        mSettingName = settingName;
        final boolean shouldAnimate = getTranslationY() != 0
                || !mDescription.getText().toString().equals(message);
        mDescription.setText(message);
        mCallback = callback;
        mCallbackHandler = handler;

        if (shouldAnimate) {
            animate().translationY(getHeight())
                    .setInterpolator(AnimationUtils.loadInterpolator(getContext(),
                            android.R.interpolator.fast_out_slow_in))
                    .setDuration(getTranslationY() == 0 ? 0 : ANIMATION_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(final Animator animation) {
                            setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(final Animator animation) {
                            animate().translationY(0f)
                                    .setInterpolator(AnimationUtils.loadInterpolator(getContext(),
                                            android.R.interpolator.fast_out_slow_in))
                                    .setDuration(ANIMATION_DURATION)
                                    .start();
                        }
                    }).start();
        }

        mMainHandler.removeCallbacks(mHideRunnable);
        mMainHandler.postDelayed(mHideRunnable, TIMEOUT_DURATION);
    }

    /**
     * Hides the snackbar.
     */
    public void hide() {
        if (DEBUG) Log.d(LOG_TAG, "Hiding the snackbar view");

        animate().translationY(getHeight())
                .setInterpolator(AnimationUtils.loadInterpolator(getContext(),
                        android.R.interpolator.fast_out_slow_in))
                .setDuration(ANIMATION_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        setVisibility(View.GONE);
                    }
                }).start();
    }

}
