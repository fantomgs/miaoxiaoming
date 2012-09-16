/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import com.android.settings.wifi.WifiApEnabler;
import com.qrd.plugin.feature_query.FeatureQuery;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.FmcNotifier;
import android.net.FmcProvider;
import android.os.Environment;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends PreferenceActivity {
    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private static final String WIFI_AP_SETTINGS = "wifi_ap_settings";
    private static final String TETHERING_HELP = "tethering_help";
    private static final String USB_HELP_MODIFIER = "usb_";
    private static final String WIFI_HELP_MODIFIER = "wifi_";
    private static final String HELP_URL = "file:///android_asset/html/%y%z/tethering_%xhelp.html";
    private static final String HELP_PATH = "html/%y%z/tethering_help.html";
    private static final String TAG = "TetherSettings";
    private static final String FMC_STATE_CHANGED_ACTION = "android.fmc.FMC_STATE_CHANGED_ACTION";

    private static final int DIALOG_TETHER_HELP = 1;
    private static final int UPDATE_USB_TETHER = 10;

    private FmcProvider mFmcProvider = null;
    private WebView mView;
    private CheckBoxPreference mUsbTether;

    private CheckBoxPreference mEnableWifiAp;
    private PreferenceScreen mWifiApSettings;
    private WifiApEnabler mWifiApEnabler;
    private PreferenceScreen mTetherHelp;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;
    private ArrayList mUsbIfaces;

    private String[] mWifiRegexs;
    private boolean mAllowTetherUms = SystemProperties.getBoolean("ro.usb.allow.tether.ums",false);

    private Handler myHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case UPDATE_USB_TETHER:
                boolean enable = (msg.arg1 == 0) ? false : true;
                mEnableWifiAp.setEnabled(enable);
                mWifiApSettings.setEnabled(enable);
                Log.d(TAG, "UPDATE_USB_TETHER enable=" + enable);
                break;
            default:
                break;
            }
        }
    };

    private BroadcastReceiver fmcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (FMC_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int status = intent.getIntExtra("FmcStatus", -1);
                Log.d(TAG, "fmcReceiver status ="+status);
                if (status == FmcNotifier.FMC_STATUS_ENABLED ||
                    status == FmcNotifier.FMC_STATUS_REGISTRATION_SUCCESS ) {
                    myHandler.obtainMessage(UPDATE_USB_TETHER, 0, 0).sendToTarget();
                } else {
                    myHandler.obtainMessage(UPDATE_USB_TETHER, 1, 0).sendToTarget();
                }
            }
        }
    };

    private IntentFilter fmcFilter = new IntentFilter(FMC_STATE_CHANGED_ACTION);

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.tether_prefs);

        mEnableWifiAp = (CheckBoxPreference) findPreference(ENABLE_WIFI_AP);
        mWifiApSettings = (PreferenceScreen) findPreference(WIFI_AP_SETTINGS);
        mUsbTether = (CheckBoxPreference) findPreference(USB_TETHER_SETTINGS);
        mTetherHelp = (PreferenceScreen) findPreference(TETHERING_HELP);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        mUsbRegexs = cm.getTetherableUsbRegexs();
        if (mUsbRegexs.length == 0 || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(mUsbTether);

            setTitle(R.string.tether_settings_title_wifi);
        }

        mWifiRegexs = cm.getTetherableWifiRegexs();
        if (mWifiRegexs.length == 0) {
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(mWifiApSettings);

            setTitle(R.string.tether_settings_title_usb);
        } else if (mUsbRegexs.length != 0) {
            // have both
            setTitle(R.string.tether_settings_title_both);
        }
        mWifiApEnabler = new WifiApEnabler(this, mEnableWifiAp);
        mView = new WebView(this);

        updateCTARes();
    }

    private void updateCTARes(){
        if (FeatureQuery.FEATURE_SETTINGS_CTA_REQUIREMENTS) {
            mWifiApSettings.setTitle(R.string.wifi_tether_settings_title_cta);
            mWifiApSettings.setSummary(R.string.wifi_tether_settings_subtext_cta);
        }
    }

    @Override
    public void onDestroy() {
        mView.stopLoading();
        mView.destroy();
        mView = null;

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_TETHER_HELP) {
            Locale locale = Locale.getDefault();

            // check for the full language + country resource, if not there, try just language
            AssetManager am = getAssets();
            String path = HELP_PATH.replace("%y", locale.getLanguage().toLowerCase());
            path = path.replace("%z", "_"+locale.getCountry().toLowerCase());
            boolean useCountry = true;
            InputStream is = null;
            try {
                is = am.open(path);
            } catch (Exception e) {
                useCountry = false;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception e) {}
                }
            }
            String url = HELP_URL.replace("%y", locale.getLanguage().toLowerCase());
            url = url.replace("%z", (useCountry ? "_"+locale.getCountry().toLowerCase() : ""));
            if ((mUsbRegexs.length != 0) && (mWifiRegexs.length == 0)) {
                url = url.replace("%x", USB_HELP_MODIFIER);
            } else if ((mWifiRegexs.length != 0) && (mUsbRegexs.length == 0)) {
                url = url.replace("%x", WIFI_HELP_MODIFIER);
            } else {
                // could assert that both wifi and usb have regexs, but the default
                // is to use this anyway so no check is needed
                url = url.replace("%x", "");
            }

            mView.loadUrl(url);

            return new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(R.string.tethering_help_button_text)
                .setView(mView)
                .create();
        }
        return null;
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateState(available.toArray(), active.toArray(), errored.toArray());
            } else if (intent.getAction().equals(Intent.ACTION_MEDIA_SHARED) ||
                       intent.getAction().equals(Intent.ACTION_MEDIA_UNSHARED)) {
                updateState();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mTetherChangeReceiver = new TetherChangeReceiver();
        Intent intent = registerReceiver(mTetherChangeReceiver, filter);

        if (!mAllowTetherUms) {
            filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_SHARED);
            filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
            filter.addDataScheme("file");
            registerReceiver(mTetherChangeReceiver, filter);
        }

        if (intent != null) mTetherChangeReceiver.onReceive(this, intent);

        registerReceiver(fmcReceiver, fmcFilter);
        mWifiApEnabler.resume();

        updateState();

        // if FMC is enabled, USB tethering will be unavailable. So we need to
        // dim this item.
        if (SystemProperties.getInt("ro.config.cwenable", 0) == 1) {
            int status = android.provider.Settings.System.getInt(this.getContentResolver(),
                    Settings.System.FMC_STATUS, -1);
            Log.d(TAG, "onResume Fmc Status = "+status);
            if (status == FmcNotifier.FMC_STATUS_ENABLED ||
                status == FmcNotifier.FMC_STATUS_REGISTRATION_SUCCESS) {
                myHandler.obtainMessage(UPDATE_USB_TETHER, 0, 0).sendToTarget();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mTetherChangeReceiver);
        unregisterReceiver(fmcReceiver);
        mTetherChangeReceiver = null;
        mWifiApEnabler.pause();
    }

    private void updateState() {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        String[] available = cm.getTetherableIfaces();
        String[] tethered = cm.getTetheredIfaces();
        String[] errored = cm.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }

    private void updateState(Object[] available, Object[] tethered,
            Object[] errored) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean usbTethered = false;
        boolean usbAvailable = false;
        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        boolean usbErrored = false;
        boolean usbTetherDisabled = false;

        /*
         * The flag 'usbTetherDisabled' is used to disable tethering when USB mass storage is on.
         * If 'mAllowTetherUms' flag is defined we allow mass storage along with tethering, so for
         * targets where mass storage + tethering is supported usbTetherDisabled need not be updated
         */
        if (!mAllowTetherUms) {
            usbTetherDisabled = Environment.MEDIA_SHARED.equals(Environment.getExternalStorageState());
        }

        for (Object o : available) {
            String s = (String)o;
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    usbAvailable = true;
                    if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        usbError = cm.getLastTetherError(s);
                    }
                }
            }
        }
        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbTethered = true;
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbErrored = true;
            }
        }

        if (usbTethered) {
            mUsbTether.setSummary(R.string.usb_tethering_active_subtext);
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(true);
        } else if (usbAvailable) {
            if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
            } else {
                mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            }
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(false);
        } else if (usbErrored) {
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
        } else if (usbTetherDisabled) {
            mUsbTether.setSummary(R.string.usb_tethering_storage_active_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
        } else {
            mUsbTether.setSummary(R.string.usb_tethering_unavailable_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mUsbTether) {
            boolean newState = mUsbTether.isChecked();

            ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

            if (newState) {
                String[] available = cm.getTetherableIfaces();

                String usbIface = findIface(available, mUsbRegexs);
                if (usbIface == null) {
                    updateState();
                    return true;
                }
                if (cm.tether(usbIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    mUsbTether.setChecked(false);
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                    return true;
                }
                mUsbTether.setSummary("");
            } else {
                String [] tethered = cm.getTetheredIfaces();

                String usbIface = findIface(tethered, mUsbRegexs);
                if (usbIface == null) {
                    updateState();
                    return true;
                }
                if (cm.untether(usbIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                    return true;
                }
                mUsbTether.setSummary("");
            }
        } else if (preference == mTetherHelp) {

            showDialog(DIALOG_TETHER_HELP);
        }
        return false;
    }

    private String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            for (String regex : regexes) {
                if (iface.matches(regex)) {
                    return iface;
                }
            }
        }
        return null;
    }
}
