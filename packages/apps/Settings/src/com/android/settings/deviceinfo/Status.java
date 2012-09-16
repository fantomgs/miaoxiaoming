/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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
// HEXING123 MODIFY
package com.android.settings.deviceinfo;

import java.lang.ref.WeakReference;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.settings.R;
import com.android.settings.SelectSubscription;
import android.util.Log;

import android.os.SystemProperties;

import java.lang.ref.WeakReference;

/**
 * Display the following information
 * # Battery Strength  : TODO
 * # Uptime
 * # Awake Time
 * # XMPP/buzz/tickle status : TODO
 *
 */
public class Status extends PreferenceActivity {

    private static final String KEY_WIMAX_MAC_ADDRESS = "wimax_mac_address";
    private static final String KEY_WIFI_MAC_ADDRESS = "wifi_mac_address";
    private static final String KEY_BT_ADDRESS = "bt_address";


    private static final int EVENT_UPDATE_STATS = 500;
    private static final String BUTTON_SELECT_SUB_KEY = "button_aboutphone_msim_status";

    private final int mResources[] = {R.xml.device_info_status,
                                      R.xml.device_info_msim_status};

    private TelephonyManager mTelephonyManager;
    private PhoneStateListener[] mPhoneStateListener;
    private Resources mRes;
    private Preference mUptime;

    private static String sUnknown;
    private int mResId = 0;
    private int mNumPhones = 0;

    private Preference mBatteryStatus;
    private Preference mBatteryLevel;
    private int[] mDataState = new int[]{TelephonyManager.DATA_DISCONNECTED,TelephonyManager.DATA_DISCONNECTED};

    private Handler mHandler;
    
    private TelephonyManager telephonyManager;
    private Phone[] mPhone = new Phone[2];
    private Preference mSigStrength;
    SignalStrength[] mSignalStrength = new SignalStrength[2];
    ServiceState[] mServiceState = new ServiceState[2];
    private int mSub = 0;
    private PhoneStateListener[] phoneStateListener = new PhoneStateListener[2];

    private String[] esnNumberSummery = new String[2];
    private String[] meidNumberSummery = new String[2];
    private String[] minNumberSummery = new String[2];
    private String[] prlVersionSummery = new String[2];
    private String[] imeiSummery = new String[2];
    private String[] imeiSvSummery = new String[2];
    private String[] numberSummery = new String[2];
    private String[] serviceStateSummery = new String[2];
    private String[] roamingStateSummery = new String[2];
    private String[] operatorNameSummery = new String[2];
    private String[] mSigStrengthSummery = new String[2];
    private String[] dataStateSummery = new String[2];

    private String[] SIM = new String[2];

    private static class MyHandler extends Handler {
        private WeakReference<Status> mStatus;

        public MyHandler(Status activity) {
            mStatus = new WeakReference<Status>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Status status = mStatus.get();
            if (status == null) {
                return;
            }

            switch (msg.what) {
                case EVENT_UPDATE_STATS:
                    status.updateTimes();
                    sendEmptyMessageDelayed(EVENT_UPDATE_STATS, 1000);
                    break;
            }
        }
    }

    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {

                int level = intent.getIntExtra("level", 0);
                int scale = intent.getIntExtra("scale", 100);

                mBatteryLevel.setSummary(String.valueOf(level * 100 / scale) + "%");

                int plugType = intent.getIntExtra("plugged", 0);
                int status = intent.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
                String statusString;
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    statusString = getString(R.string.battery_info_status_charging);
                    if (plugType > 0) {
                        statusString = statusString + " " + getString(
                                (plugType == BatteryManager.BATTERY_PLUGGED_AC)
                                        ? R.string.battery_info_status_charging_ac
                                        : R.string.battery_info_status_charging_usb);
                    }
                } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                    statusString = getString(R.string.battery_info_status_discharging);
                } else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                    statusString = getString(R.string.battery_info_status_not_charging);
                } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
                    statusString = getString(R.string.battery_info_status_full);
                } else {
                    statusString = getString(R.string.battery_info_status_unknown);
                }
                mBatteryStatus.setSummary(statusString);
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Preference removablePref;
        Phone phone;
        mHandler = new MyHandler(this);

        mTelephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);

        mResId = getIntent().getIntExtra("RESOURCE_INDEX", 0);
        if (mResId < mResources.length) {
            addPreferencesFromResource(mResources[mResId]);
        }
        
        phone = PhoneFactory.getDefaultPhone();
        
        //NOTE "imei" is the "Device ID" since it represents the IMEI in GSM and the MEID in CDMA
        if (phone.getPhoneName().equals("CDMA")) {
            setSummaryText("meid_number", phone.getMeid());
            setSummaryText("min_number", phone.getCdmaMin());
            if (getResources().getBoolean(R.bool.config_msid_enable)) {
                findPreference("min_number").setTitle(R.string.status_msid_number);
            }
            setSummaryText("prl_version", phone.getCdmaPrlVersion());

            // device is not GSM/UMTS, do not display GSM/UMTS features
            // check Null in case no specified preference in overlay xml
            removablePref = findPreference("imei");
            if (removablePref != null) {
                getPreferenceScreen().removePreference(removablePref);
            }
            removablePref = findPreference("imei_sv");
            if (removablePref != null) {
                getPreferenceScreen().removePreference(removablePref);
            }
        } else {
            setSummaryText("imei", phone.getDeviceId());
       }
        mNumPhones = TelephonyManager.getPhoneCount();
        mPhoneStateListener = new PhoneStateListener[mNumPhones];

        for (int i=0; i < mNumPhones; i++) {
            mPhoneStateListener[i] = getPhoneStateListener(i);
            mTelephonyManager.listen(mPhoneStateListener[i],
                            PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
        }

        mBatteryLevel = findPreference("battery_level");
        mBatteryStatus = findPreference("battery_status");

        PreferenceScreen selectSub = (PreferenceScreen) findPreference(BUTTON_SELECT_SUB_KEY);
        if (selectSub != null) {
            Intent intent = selectSub.getIntent();
            intent.putExtra(SelectSubscription.PACKAGE, "com.android.settings");
            intent.putExtra(SelectSubscription.TARGET_CLASS, "com.android.settings.deviceinfo.MSimStatus");
        }

        mRes = getResources();
        if (sUnknown == null) {
            sUnknown = mRes.getString(R.string.device_info_default);
        }

        mUptime = findPreference("up_time");

        /* TODO: align with 2.3.4_r1
        setWimaxStatus();
        */
        // do setWifiStatus in the onResume
        // setWifiStatus();
       	if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
       	{
       		;
       	}
		else
		{
        	setBtStatus();
		}

        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        //addPreferencesFromResource(R.xml.device_info_subscription_status);

        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            SIM[i] = getMultiSimName(i);
            phoneStateListener[i] = getPhoneStateListener(i);
            telephonyManager.listen(phoneStateListener[i],
                    PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS|PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
            mPhone[i] = PhoneFactory.getPhone(i);
        }

        initStr(esnNumberSummery);
        initStr(meidNumberSummery);
        initStr(minNumberSummery);
        initStr(prlVersionSummery);
        initStr(imeiSummery);
        initStr(imeiSvSummery);
        initStr(numberSummery);
        initStr(serviceStateSummery);
        initStr(roamingStateSummery);
        initStr(operatorNameSummery);
        initStr(mSigStrengthSummery);
        initStr(dataStateSummery);

        // Note - missing in zaku build, be careful later...
        mSigStrength = findPreference("signal_strength");

        // NOTE "imei" is the "Device ID" since it represents the IMEI in GSM
        // and the MEID in CDMA

        for (int i = 0; i < 2; i++) {
            if (mPhone[i] != null) {
                if (mPhone[i].getPhoneName().equals("CDMA")) {
                    esnNumberSummery[i] = SIM[i] + ": " + mPhone[i].getEsn();
                    meidNumberSummery[i] = SIM[i] + ": " + mPhone[i].getMeid();
                    minNumberSummery[i] = SIM[i] + ": "
                            + mPhone[i].getCdmaMin();
                    prlVersionSummery[i] = SIM[i] + ": "
                            + mPhone[i].getCdmaPrlVersion();

                    // device is not GSM/UMTS, do not display GSM/UMTS features
                    // check Null in case no specified preference in overlay xml
                    imeiSummery[i] = SIM[i] + ": " + sUnknown;
                    imeiSvSummery[i] = SIM[i] + ": " + sUnknown;
                } else {

                    esnNumberSummery[i] = SIM[i] + ": " + sUnknown;
                    meidNumberSummery[i] = SIM[i] + ": " + sUnknown;
                    minNumberSummery[i] = SIM[i] + ": " + sUnknown;
                    prlVersionSummery[i] = SIM[i] + ": " + sUnknown;

                    // device is not GSM/UMTS, do not display GSM/UMTS features
                    // check Null in case no specified preference in overlay xml
                    if (mPhone[i].getDeviceId() != null) {
                        imeiSummery[i] = SIM[i] + ": "
                                + mPhone[i].getDeviceId();
                    }
                    if (telephonyManager.getDeviceSoftwareVersion(i) != null) {
                        imeiSvSummery[i] = SIM[i] + ": "
                                + telephonyManager.getDeviceSoftwareVersion(i);
                    }
                }

                String rawNumber = mPhone[i].getLine1Number(); // may be null or
                                                                // empty
                String formattedNumber = null;
                if (!TextUtils.isEmpty(rawNumber)) {
                    formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
                }
                if (formattedNumber != null && !formattedNumber.equals("null")) {
                    numberSummery[i] = SIM[i] + ": " + formattedNumber;
                }
            }
        }

		//Add By zzg 2012_06_25
		if(SystemProperties.get("ro.product.customer_id").equals("S600")
			||SystemProperties.get("ro.product.customer_id").equals("S600_C"))
		{
			//Do nothing
		}
		else
		{
		//Add End
	        setSummaryText("esn_number", esnNumberSummery[0] + "\n"
	                + esnNumberSummery[1]);
	        setSummaryText("meid_number", meidNumberSummery[0] + "\n"
	                + meidNumberSummery[1]);
	        setSummaryText("min_number", minNumberSummery[0] + "\n"
	                + minNumberSummery[1]);
	        setSummaryText("prl_version", prlVersionSummery[0] + "\n"
	                + prlVersionSummery[1]);
		//Add By zzg 2012_06_25	
		}
		//Add End	
		
        setSummaryText("imei", imeiSummery[0] + "\n" + imeiSummery[1]);
        setSummaryText("imei_sv", imeiSvSummery[0] + "\n" + imeiSvSummery[1]);
        setSummaryText("number", numberSummery[0] + "\n" + numberSummery[1]);
        setSummaryText("baseband_version", TelephonyManager.getTelephonyProperty(
                                "gsm.version.baseband", 0, ""));
		
    }

    @Override
    protected void onResume() {
        super.onResume();
        setWifiStatus();

        for (int i = 0; i < 2; i++) {
            if (mPhone[i] != null) {
                telephonyManager.listen(phoneStateListener[i],
                        PhoneStateListener.LISTEN_SERVICE_STATE
                                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS|PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
                updateSignalStrength(i);
                updateServiceState(i);
                updateDataState(i);
            }
        }
        
        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                
        mHandler.sendEmptyMessage(EVENT_UPDATE_STATS);
    }

    @Override
    public void onPause() {
        super.onPause();

        for (int i = 0; i < 2; i++) {
            if (mPhone[i] != null) {
                telephonyManager.listen(phoneStateListener[i],
                        PhoneStateListener.LISTEN_NONE);
            }
        }
        
        unregisterReceiver(mBatteryInfoReceiver);
        mHandler.removeMessages(EVENT_UPDATE_STATS);
    }

    
    private PhoneStateListener getPhoneStateListener(final int subscription) {
        PhoneStateListener phoneStateListener = new PhoneStateListener(
                subscription) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                mSignalStrength[subscription] = signalStrength;
                updateSignalStrength(subscription);
            }

            @Override
            public void onServiceStateChanged(ServiceState state) {
                mServiceState[subscription] = state;
                updateServiceState(subscription);
            }

            public void onDataConnectionStateChanged(int state) {
                mDataState[subscription] = state;
                updateDataState(subscription);
                updateNetworkType();
            }
        };
        return phoneStateListener;
    }

    /**
     * @param preference The key for the Preference item
     * @param property The system property to fetch
     * @param alt The default value, if the property doesn't exist
     */
    private void setSummary(String preference, String property, String alt) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property, alt));
        } catch (RuntimeException e) {

        }
    }

    private void setSummaryText(String preference, String text) {
            if (TextUtils.isEmpty(text)) {
               text = sUnknown;
             }
             // some preferences may be missing
             if (findPreference(preference) != null) {
                 findPreference(preference).setSummary(text);
             }
    }

    private void updateNetworkType() {
        // Whether EDGE, UMTS, etc...
        setSummary("network_type", TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE, sUnknown);
    }

    private void updateDataState(int i) {
        String display = mRes.getString(R.string.radioInfo_unknown);

        switch (mDataState[i]) {
            case TelephonyManager.DATA_CONNECTED:
                display = mRes.getString(R.string.radioInfo_data_connected);
                break;
            case TelephonyManager.DATA_SUSPENDED:
                display = mRes.getString(R.string.radioInfo_data_suspended);
                break;
            case TelephonyManager.DATA_CONNECTING:
                display = mRes.getString(R.string.radioInfo_data_connecting);
                break;
            case TelephonyManager.DATA_DISCONNECTED:
                display = mRes.getString(R.string.radioInfo_data_disconnected);
                break;
        }

        dataStateSummery[i] = SIM[i] + ": " +  display;

        if (!TelephonyManager.isMultiSimEnabled()) {
            setSummaryText("data_state", display);
        }else{
            setSummaryText("data_state", dataStateSummery[0] + "\n" + dataStateSummery[1]);
        }
    }

    /* TODO: align with 2.3.4_r1 */
    private void setWimaxStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);

        if (ni == null) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = (Preference) findPreference(KEY_WIMAX_MAC_ADDRESS);
            if (ps != null)
                root.removePreference(ps);
        } else {
            Preference wimaxMacAddressPref = findPreference(KEY_WIMAX_MAC_ADDRESS);
            String macAddress = SystemProperties.get("net.wimax.mac.address",
                    getString(R.string.status_unavailable));
            wimaxMacAddressPref.setSummary(macAddress);
        }
    }

    private void setWifiStatus() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_WIFI_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        // because getConnectionInfo still return the last connection info after wifi is diabled,
        // so here we need to check wifi state to avoid showing incorrect mac address
        if (!wifiManager.isWifiEnabled())
            macAddress=null;
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : getString(R.string.status_unavailable));
    }

    private void setBtStatus() {
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
		
        Preference btAddressPref = findPreference(KEY_BT_ADDRESS);

        if (bluetooth == null) {
            // device not BT capable
            getPreferenceScreen().removePreference(btAddressPref);
        } else {
            String address = bluetooth.isEnabled() ? bluetooth.getAddress() : null;
            btAddressPref.setSummary(!TextUtils.isEmpty(address) ? address
                    : getString(R.string.status_unavailable));
        }
    }

    void updateTimes() {
        long at = SystemClock.uptimeMillis() / 1000;
        long ut = SystemClock.elapsedRealtime() / 1000;

        if (ut == 0) {
            ut = 1;
        }

        mUptime.setSummary(convert(ut));
    }

    private String pad(int n) {
        if (n >= 10) {
            return String.valueOf(n);
        } else {
            return "0" + String.valueOf(n);
        }
    }

    private String convert(long t) {
        int s = (int)(t % 60);
        int m = (int)((t / 60) % 60);
        int h = (int)((t / 3600));

        return h + ":" + pad(m) + ":" + pad(s);
    }


    private void updateServiceState(int i) {
        String display = mRes.getString(R.string.radioInfo_unknown);

        if (mServiceState[i] != null) {
            int state = mServiceState[i].getState();

            switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                serviceStateSummery[i] = SIM[i] + ": "
                        + mRes.getString(R.string.radioInfo_service_in);
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
            case ServiceState.STATE_EMERGENCY_ONLY:
                serviceStateSummery[i] = SIM[i] + ": "
                        + mRes.getString(R.string.radioInfo_service_out);
                break;
            case ServiceState.STATE_POWER_OFF:
                serviceStateSummery[i] = SIM[i] + ": "
                        + mRes.getString(R.string.radioInfo_service_off);
                break;
            }

            setSummaryText("service_state", serviceStateSummery[0] + "\n"
                    + serviceStateSummery[1]);

            if (mServiceState[i].getRoaming()) {
                roamingStateSummery[i] = SIM[i] + ": "
                        + mRes.getString(R.string.radioInfo_roaming_in);
            } else {
                roamingStateSummery[i] = SIM[i] + ": "
                        + mRes.getString(R.string.radioInfo_roaming_not);
            }
            setSummaryText("roaming_state", roamingStateSummery[0] + "\n"
                    + roamingStateSummery[1]);

            if(mServiceState[i].getOperatorAlphaLong() !=null){
                operatorNameSummery[i] = SIM[i] + ": "
                        + mServiceState[i].getOperatorAlphaLong();
            }
            setSummaryText("operator_name", operatorNameSummery[0] + "\n"
                    + operatorNameSummery[1]);
        }
    }

    void updateSignalStrength(int i) {
        // not loaded in some versions of the code (e.g., zaku)
        int signalDbm = 0;

        if (mSignalStrength[i] != null) {
            int state = mServiceState[i].getState();
            Resources r = getResources();

            if ((ServiceState.STATE_OUT_OF_SERVICE == state)
                    || (ServiceState.STATE_POWER_OFF == state)) {
                mSigStrength.setSummary("0");
            }

            if (!mSignalStrength[i].isGsm()) {
                signalDbm = mSignalStrength[i].getCdmaDbm();
            } else {
                int gsmSignalStrength = mSignalStrength[i]
                        .getGsmSignalStrength();
                int asu = (gsmSignalStrength == 99 ? -1 : gsmSignalStrength);
                if (asu != -1) {
                    signalDbm = -113 + 2 * asu;
                }
            }
            if (-1 == signalDbm)
                signalDbm = 0;

            int signalAsu = mSignalStrength[i].getGsmSignalStrength();
            if (-1 == signalAsu)
                signalAsu = 0;

            mSigStrengthSummery[i] = SIM[i] + ": " + String.valueOf(signalDbm)
                    + " " + r.getString(R.string.radioInfo_display_dbm) + "   "
                    + String.valueOf(signalAsu) + " "
                    + r.getString(R.string.radioInfo_display_asu);
            mSigStrength.setSummary(mSigStrengthSummery[0] + "\n"
                    + mSigStrengthSummery[1]);
        }
    }

    private void initStr(String[] str) {
        for (int i = 0; i < 2; i++) {
            if (str[i] == null) {
                    str[i] = SIM[i] + ": " + sUnknown;
            }
        }
    }

    private String getMultiSimName(int subscription) {
        return Settings.System.getString(getContentResolver(),
            Settings.System.MULTI_SIM_NAME[subscription]);
    }
}
