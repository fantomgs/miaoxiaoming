/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.wifi;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.RILConstants;
import com.android.settings.R;
import com.android.settings.WirelessSettings;
import com.qrd.plugin.feature_query.FeatureQuery;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class WifiApEnabler implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "WifiApEnabler";
    private final Context mContext;
    private final CheckBoxPreference mCheckBox;
    private final CharSequence mOriginalSummary;

    private WifiManager mWifiManager;
    private final IntentFilter mIntentFilter;

    ConnectivityManager mCm;
    private String[] mWifiRegexs;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "WifiApEnabler mReceiver");
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED));
            } else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateTetherState(available.toArray(), active.toArray(), errored.toArray());
            }

        }
    };

    public WifiApEnabler(Context context, CheckBoxPreference checkBox) {
        mContext = context;
        mCheckBox = checkBox;
        mOriginalSummary = checkBox.getSummary();
        checkBox.setPersistent(false);

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);

        updateCTARes();
    }



    private void updateCTARes(){
        if (FeatureQuery.FEATURE_SETTINGS_CTA_REQUIREMENTS) {
            mCheckBox.setTitle(R.string.wifi_tether_checkbox_text_cta);
        }
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        enableWifiCheckBox();
        mCheckBox.setOnPreferenceChangeListener(this);
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
        mCheckBox.setOnPreferenceChangeListener(null);
    }

    private void enableWifiCheckBox() {
        boolean isAirplaneMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        if(!isAirplaneMode) {
            mCheckBox.setEnabled(true);
        } else {
            mCheckBox.setEnabled(false);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {

        final ContentResolver cr = mContext.getContentResolver();
        boolean enable = (Boolean)value;

        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        if (enable && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Secure.putInt(cr, Settings.Secure.WIFI_SAVED_STATE, 1);
        }

        if (mWifiManager.setWifiApEnabled(null, enable)) {
            /* Disable here, enabled on receiving success broadcast */
            mCheckBox.setEnabled(false);
        } else {
            mCheckBox.setSummary(R.string.wifi_error);
        }

        /**
         *  If needed, restore Wifi on tether disable
         */
        if (!enable) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Secure.getInt(cr, Settings.Secure.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                ;
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Secure.putInt(cr, Settings.Secure.WIFI_SAVED_STATE, 0);
            }
        }

        return false;
    }

    void updateConfigSummary(WifiConfiguration wifiConfig) {
        String s = mContext.getString(
                com.android.internal.R.string.wifi_tether_configure_ssid_default);
        mCheckBox.setSummary(String.format(
                    mContext.getString(R.string.wifi_tether_enabled_subtext),
                    (wifiConfig == null) ? s : wifiConfig.SSID));
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) wifiTethered = true;
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) wifiErrored = true;
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
        } else if (wifiErrored) {
            mCheckBox.setSummary(R.string.wifi_error);
        }
    }

    private void handleWifiApStateChanged(int state) {
        Log.d(TAG, "handleWifiApStateChanged state="+state);
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mCheckBox.setSummary(R.string.wifi_starting);
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                /**
                 * Summary on enable is handled by tether
                 * broadcast notice
                 */
                mCheckBox.setChecked(true);
                /* Doesnt need the airplane check */
                mCheckBox.setEnabled(true);

                // Request modem to reduce the transmit power when
                // hotspot is enabled
                setTransmitPower(RILConstants.TRANSMIT_POWER_WIFI_HOTSPOT);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mCheckBox.setSummary(R.string.wifi_stopping);
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mCheckBox.setChecked(false);
                mCheckBox.setSummary(mOriginalSummary);
                enableWifiCheckBox();

                // Request modem to restore the tranmsit power to default values
                // when hotspot is disabled
                setTransmitPower(RILConstants.TRANSMIT_POWER_DEFAULT);
                break;
            default:
                mCheckBox.setChecked(false);
                mCheckBox.setSummary(R.string.wifi_error);
                enableWifiCheckBox();
        }
    }

    /**
     * Sets the transmit power level
     *
     * @param powerLevel
     */
    private void setTransmitPower(int powerLevel) {
        ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
        if (phone == null) {
            Log.e(TAG, "ITelephony interface is null, can not set transmit power");
            return;
        }

        // Request modem to change the transmit power
        try {
            Log.d(TAG, "Setting transmit power to " + powerLevel);
            phone.setTransmitPower(powerLevel);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException during setting max transmit power", ex);
        }
    }
}
