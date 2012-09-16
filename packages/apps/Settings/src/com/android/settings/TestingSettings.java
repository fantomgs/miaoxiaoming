/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright 2010-2011, Code Aurora Forum. All rights reserved.
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


import com.android.internal.telephony.TelephonyProperties;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;

public class TestingSettings extends PreferenceActivity {

    private final int PHONE_INFO = 1;

    private final String SUBSCRIPTION = "SUBSCRIPTION";
    private final String MODEL_MSM7627A_SKU3 = "msm7627a_sku3";

    private CheckBoxPreference mOMHEnabler = null;
    private PreferenceScreen mRadioInfo = null;
    private CheckBoxPreference mFmcSetProp = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.testing_settings);

        mRadioInfo = (PreferenceScreen)findPreference("testing_phone_info");

        mOMHEnabler = (CheckBoxPreference) findPreference("omh_enabled_key");
        /**
         * OMH feature is removed in MSM7627A_SKU3
         */
        String model = Build.MODEL;
        if(model.equalsIgnoreCase(MODEL_MSM7627A_SKU3)){
            if(null != mOMHEnabler){
                this.getPreferenceScreen().removePreference(mOMHEnabler);
            }
        }

        boolean enable = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_OMH_ENABLED, false);
        mOMHEnabler.setChecked(enable);
        if (null != mOMHEnabler) {
            mOMHEnabler.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(
                            Preference preference, Object value) {

                        boolean enable = (Boolean) value;
                        String strEnabled = enable ? "true" : "false";
                        SystemProperties.set(
                                TelephonyProperties.PROPERTY_OMH_ENABLED,
                                strEnabled);
                        if (enable && mFmcSetProp != null ) {
                            mFmcSetProp.setChecked(false);
                            setFmcProp(false);
                        }
                        Log.d("TESTING", "preference changed,value="+strEnabled);
                        return true;
                    }
                });
        }

        mFmcSetProp = (CheckBoxPreference)findPreference("set_fmc_properties_key");
        if (mFmcSetProp != null)
            mFmcSetProp.setOnPreferenceChangeListener(fmcListener);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFmcSetProp != null)
            mFmcSetProp.setChecked(isFmcPorpSet());
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if ((preference == mRadioInfo) &&
            (TelephonyManager.isMultiSimEnabled())){
            showDialog(PHONE_INFO);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case PHONE_INFO:
            return new AlertDialog.Builder(TestingSettings.this)
                    .setTitle(R.string.testing_slot_choose)
                    .setItems(R.array.select_slot_items,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = mRadioInfo.getIntent();
                                intent.putExtra(SUBSCRIPTION, which);
                                startActivity(intent);
                            }
                        }).create();
        }
        return null;
    }

    private OnPreferenceChangeListener fmcListener = new OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean enable = (Boolean) newValue;
            setFmcProp(enable);
            return true;
        }
    };

    private boolean isFmcPorpSet() {
        /*
         * persist.dsds.enabled=false
         * persist.data.ds_fmc_app.mode=1
         * persist.ims.regmanager.mode=1
         * persist.cne.fmc.mode=true
         * persist.cne.UseCne=vendor
         * persist.sys.omh.enabled=false
         */

        if ( (SystemProperties.getInt("persist.data.ds_fmc_app.mode",0)==1) &&
                (SystemProperties.getInt("persist.ims.regmanager.mode",0)==1) &&
                (SystemProperties.getBoolean("persist.cne.fmc.mode",false)==true) &&
                ("vendor".equals(SystemProperties.get("persist.cne.UseCne","none"))) &&
                (SystemProperties.getBoolean("persist.sys.omh.enabled",false)==false)
                )
            return true;
        else
            return false;
    }

    private void setFmcProp(boolean enable) {
        int dsFmcAppMode = 1;
        int imsMode = 1;
        boolean cneMode = true;
        String useCne = "vendor";
        int ctSpecFmc = 1;
        if (enable) {
            SystemProperties.set("persist.sys.omh.enabled",String.valueOf(false));
            mOMHEnabler.setChecked(false);
        } else {
            dsFmcAppMode = 0;
            imsMode = 0;
            cneMode = false;
            useCne = "none";
            ctSpecFmc = 0;
        }
        SystemProperties.set("persist.data.ds_fmc_app.mode",String.valueOf(dsFmcAppMode));
        SystemProperties.set("persist.ims.regmanager.mode",String.valueOf(imsMode));
        SystemProperties.set("persist.cne.fmc.mode",String.valueOf(cneMode));
        SystemProperties.set("persist.cne.UseCne",useCne);
        // Note: ro.* property once set, can never been changed!
        // SystemProperties.set("ro.config.cwenable",String.valueOf(ctSpecFmc));
    }

}
