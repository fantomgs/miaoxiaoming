/*
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

import com.android.settings.R;

import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.ScanResult;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

/* ATH_WAPI +++ */
import android.os.SystemProperties;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Arrays;
/* ATH_WAPI ---*/

class AccessPoint extends Preference {
    private static final int[] STATE_SECURED = {R.attr.state_encrypted};
    private static final int[] STATE_NONE = {};

    static final int SECURITY_NONE = 0;
    static final int SECURITY_WEP = 1;
    static final int SECURITY_PSK = 2;
    static final int SECURITY_EAP = 3;
    /* ATH_WAPI +++ */
    static int SECURITY_WAPI_PSK = -1;
    static int SECURITY_WAPI_EAP = -1;
    static int SECURITY_CCKM = -1;
    /* ATH_WAPI ---*/

    final String ssid;
    final int security;
    final int networkId;

    private WifiConfiguration mConfig;
    private int mRssi;
    private WifiInfo mInfo;
    private DetailedState mState;
    private ImageView mSignal;

    /* ATH_WAPI +++ */
    private static ArrayList extraSecurities;

    public static String[] getExtraEapMethod(String[] type) {
        if (getSysCckmSupported()) {
            ArrayList al = new ArrayList(Arrays.asList(type));
            al.add("LEAP");
            type = (String[])al.toArray(type);
        }
        return type;
    }

    public static String[] getExtraSecurity(String[] type)
    {
        if (extraSecurities==null || extraSecurities.isEmpty()) {
            return type;
        } 
        ArrayList al = new ArrayList(Arrays.asList(type));
        al.addAll(extraSecurities);
        type = (String[])al.toArray(type);
        return type;
    }

    private static void setupExtraSecurity()
    {        
        if (SECURITY_CCKM==-1) {
            int idx = SECURITY_EAP+1;
            boolean wapiEnabled = SystemProperties.get("wlan.driver.wapi_supported", "false").equals("true");
            boolean ccxEnabled = SystemProperties.get("wlan.driver.ccx_supported", "false").equals("true");
            extraSecurities = new ArrayList();
            for (int i=0; i<KeyMgmt.strings.length; ++i) {
                if (ccxEnabled && KeyMgmt.strings[i].equals("CCKM")) {
                    SECURITY_CCKM = idx++;
                    extraSecurities.add("CCKM");
                } else if (wapiEnabled && KeyMgmt.strings[i].equals("WAPI_PSK")) {
                    SECURITY_WAPI_PSK = idx++;
                    extraSecurities.add("WAPI PSK");
                } else if (wapiEnabled && KeyMgmt.strings[i].equals("WAPI_CERT")) {
                    SECURITY_WAPI_EAP = idx++;
                    extraSecurities.add("WAPI CERT");
                }
            }
            if (SECURITY_CCKM==-1) {
                SECURITY_CCKM=-2;
            }
        }
    }

    public static boolean getSysCckmSupported() {
        setupExtraSecurity();
        return (SECURITY_CCKM > 0);
    }

    public static boolean getSysWapiSupported() {
        setupExtraSecurity();
        return (SECURITY_WAPI_PSK > 0);
    }

    public static void setConfBitSet(BitSet bits, String[] strings, String desc) {
        int i;
        for (i=0; i<strings.length; ++i) {
            if (desc.equals(strings[i])) {
                bits.set(i);
                return;
            }
        }
    }

    public static boolean getConfBitSet(BitSet bits, String[] strings, String desc) {
        int i;
        for (i=0; i<strings.length; ++i) {
            if (desc.equals(strings[i])) {
                return bits.get(i);
            }
        }
        return false;
    }
    /* ATH_WAPI --- */

    static int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            return SECURITY_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
            return SECURITY_EAP;
        }
        /* ATH_WAPI +++ */
        if (getSysWapiSupported() && getConfBitSet(config.allowedKeyManagement, KeyMgmt.strings, "WAPI_PSK")) {
            return SECURITY_WAPI_PSK;
        }
        if (getSysWapiSupported() && getConfBitSet(config.allowedKeyManagement, KeyMgmt.strings, "WAPI_CERT")) {
            return SECURITY_WAPI_EAP;
        }
        if (getSysCckmSupported() && getConfBitSet(config.allowedKeyManagement, KeyMgmt.strings, "CCKM")) {
            return SECURITY_EAP;
        }
        /* ATH_WAPI --- */
        return (config.wepKeys[0] != null) ? SECURITY_WEP : SECURITY_NONE;
    }

    private static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        /* ATH_WAPI +++ */
        } else if (getSysWapiSupported() && result.capabilities.contains("WAPI-PSK")) {
            return SECURITY_WAPI_PSK;
        } else if (getSysWapiSupported() && result.capabilities.contains("WAPI-CERT")) {
            return SECURITY_WAPI_EAP;
        } else if (getSysCckmSupported() && result.capabilities.contains("CCKM")) {
            return SECURITY_EAP;
        /* ATH_WAPI --- */
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        }
        return SECURITY_NONE;
    }

    AccessPoint(Context context, WifiConfiguration config) {
        super(context);
        setWidgetLayoutResource(R.layout.preference_widget_wifi_signal);
        ssid = (config.SSID == null ? "" : removeDoubleQuotes(config.SSID));
        security = getSecurity(config);
        networkId = config.networkId;
        mConfig = config;
        mRssi = Integer.MAX_VALUE;
    }

    AccessPoint(Context context, ScanResult result) {
        super(context);
        setWidgetLayoutResource(R.layout.preference_widget_wifi_signal);
        ssid = result.SSID;
        security = getSecurity(result);
        networkId = -1;
        mRssi = result.level;
    }

    @Override
    protected void onBindView(View view) {
        setTitle(ssid);
        mSignal = (ImageView) view.findViewById(R.id.signal);
        if (mRssi == Integer.MAX_VALUE) {
            mSignal.setImageDrawable(null);
        } else {
            mSignal.setImageResource(R.drawable.wifi_signal);
            mSignal.setImageState((security != SECURITY_NONE) ?
                    STATE_SECURED : STATE_NONE, true);
        }
        refresh();
        super.onBindView(view);
    }

    @Override
    public int compareTo(Preference preference) {
        if (!(preference instanceof AccessPoint)) {
            return 1;
        }
        AccessPoint other = (AccessPoint) preference;
        // Active one goes first.
        if (mInfo != other.mInfo) {
            return (mInfo != null) ? -1 : 1;
        }
        // Reachable one goes before unreachable one.
        if ((mRssi ^ other.mRssi) < 0) {
            return (mRssi != Integer.MAX_VALUE) ? -1 : 1;
        }
        // Configured one goes before unconfigured one.
        if ((networkId ^ other.networkId) < 0) {
            return (networkId != -1) ? -1 : 1;
        }
        // Sort by signal strength.
        int difference = WifiManager.compareSignalLevel(other.mRssi, mRssi);
        if (difference != 0) {
            return difference;
        }
        // Sort by ssid.
        return ssid.compareToIgnoreCase(other.ssid);
    }

    boolean update(ScanResult result) {
        // We do not call refresh() since this is called before onBindView().
        if (ssid.equals(result.SSID) && security == getSecurity(result)) {
            if (WifiManager.compareSignalLevel(result.level, mRssi) > 0) {
                mRssi = result.level;
            }
            return true;
        }
        return false;
    }

    void update(WifiInfo info, DetailedState state) {
        boolean reorder = false;
        if (info != null && networkId != -1 && networkId == info.getNetworkId()) {
            reorder = (mInfo == null);
            mRssi = info.getRssi();
            mInfo = info;
            mState = state;
            refresh();
        } else if (mInfo != null) {
            reorder = true;
            mInfo = null;
            mState = null;
            refresh();
        }
        if (reorder) {
            notifyHierarchyChanged();
        }
    }

    int getLevel() {
        if (mRssi == Integer.MAX_VALUE) {
            return -1;
        }
        return WifiManager.calculateSignalLevel(mRssi, 4);
    }

    WifiConfiguration getConfig() {
        return mConfig;
    }

    WifiInfo getInfo() {
        return mInfo;
    }

    DetailedState getState() {
        return mState;
    }

    static String removeDoubleQuotes(String string) {
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    static String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    private void refresh() {
        if (mSignal == null) {
            return;
        }
        Context context = getContext();
        mSignal.setImageLevel(getLevel());

        if (mState != null) {
            setSummary(Summary.get(context, mState));
        } else {
            String status = null;
            if (mRssi == Integer.MAX_VALUE) {
                status = context.getString(R.string.wifi_not_in_range);
            } else if (mConfig != null) {
                status = context.getString((mConfig.status == WifiConfiguration.Status.DISABLED) ?
                        R.string.wifi_disabled : R.string.wifi_remembered);
            }

            if (security == SECURITY_NONE) {
                setSummary(status);
            } else {
                String format = context.getString((status == null) ?
                        R.string.wifi_secured : R.string.wifi_secured_with_status);
                String[] type = context.getResources().getStringArray(R.array.wifi_security);
                type = AccessPoint.getExtraSecurity(type); /* ATH_WAPI  */
                setSummary(String.format(format, type[security], status));
            }
        }
    }
}
