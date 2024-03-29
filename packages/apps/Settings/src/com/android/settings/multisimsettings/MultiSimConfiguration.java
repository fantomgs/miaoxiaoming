/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings.multisimsettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceActivity;
import android.preference.ListPreference;
import android.preference.PreferenceCategory;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ProxyManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;

public class MultiSimConfiguration extends PreferenceActivity {
    private static final String LOG_TAG = "MultiSimConfiguration";

    public  static final String SUBSCRIPTION_ID = "SUBSCRIPTION_ID";

    private static final String KEY_SIM_NAME = "sim_name_key";
    private static final String KEY_SIM_ENABLER = "sim_enabler_key";
    private static final String KEY_NETWORK_SETTING = "mobile_network_key";
    private static final String KEY_CALL_SETTING = "call_setting_key";

    private static final int EVENT_SET_SUBSCRIPTION_DONE = 2;

    private PreferenceScreen mPrefScreen;
    private ProxyManager mProxyManager;
    private PreferenceScreen mNetworkSetting;
    private PreferenceScreen mCallSetting;

    private int mSubscription;
    private MultiSimNamePreference mNamePreference;
    private MultiSimEnabler mEnablerPreference;

    private IntentFilter mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logd("onReceive " + action);
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action) ||
                Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                setScreenState();
            }
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_DONE:
                    setScreenState();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.multi_sim_configuration);

        mPrefScreen = getPreferenceScreen();

        Intent intent = getIntent();
        mSubscription = intent.getIntExtra(SUBSCRIPTION_ID, 0);

        mProxyManager = ProxyManager.getInstance();

        mNamePreference = (MultiSimNamePreference)findPreference(KEY_SIM_NAME);
        mNamePreference.setSubscription(mSubscription);

        mEnablerPreference = (MultiSimEnabler)findPreference(KEY_SIM_ENABLER);
        mEnablerPreference.setSubscription(this, mSubscription);
        mNetworkSetting = (PreferenceScreen)findPreference(KEY_NETWORK_SETTING);
        mNetworkSetting.getIntent().putExtra(MultiSimSettingsConstants.TARGET_PACKAGE,
                               MultiSimSettingsConstants.NETWORK_PACKAGE)
                                    .putExtra(MultiSimSettingsConstants.TARGET_CLASS,
                               MultiSimSettingsConstants.NETWORK_CLASS)
                                    .putExtra(SUBSCRIPTION_ID, mSubscription);

        mCallSetting = (PreferenceScreen)findPreference(KEY_CALL_SETTING);
        mCallSetting.getIntent().putExtra(MultiSimSettingsConstants.TARGET_PACKAGE, 
                               MultiSimSettingsConstants.CALL_PACKAGE)
                                    .putExtra(MultiSimSettingsConstants.TARGET_CLASS, 
                               MultiSimSettingsConstants.CALL_CLASS)
                                    .putExtra(SUBSCRIPTION_ID, mSubscription);

        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    }

    protected void onResume() {
        super.onResume();

        registerReceiver(mReceiver, mIntentFilter);
        mNamePreference.resume();
        mEnablerPreference.resume();
        setScreenState();
        mProxyManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    protected void onPause() {
        super.onPause();

        unregisterReceiver(mReceiver);
        //mProxyManager.unRegisterForSetSubscriptionCompleted(mHandler);
        mNamePreference.pause();
        mEnablerPreference.pause();
    }

    private boolean isSubActivated() {
        return mProxyManager.isSubActive(mSubscription);
    }

    private boolean isAirplaneModeOn() {
        return (System.getInt(getContentResolver(), System.AIRPLANE_MODE_ON, 0) != 0);
    }

    // check whether SIM card is present
    private boolean hasIccCard() {
        TelephonyManager tm = TelephonyManager.getDefault();
        return tm.hasIccCard(mSubscription);
    }

    private void setScreenState() {
        if (isAirplaneModeOn()) {
            mNetworkSetting.setEnabled(false);
            mCallSetting.setEnabled(false);
            mEnablerPreference.setEnabled(false);
        } else {
            mNetworkSetting.setEnabled(isSubActivated());
            mCallSetting.setEnabled(isSubActivated());
            mEnablerPreference.setEnabled(hasIccCard());
        }
    }

    private void logd(String msg) {
        Log.d(LOG_TAG, "[" + LOG_TAG + "(" + mSubscription + ")] " + msg);
    }
}
