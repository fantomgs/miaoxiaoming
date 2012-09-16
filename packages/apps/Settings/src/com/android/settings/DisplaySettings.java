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
// HEXING123 MODIFY
package com.android.settings;

import com.qrd.plugin.feature_query.FeatureQuery;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import java.util.ArrayList;
import android.os.SystemProperties;

import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;
import android.os.PowerManager;

public class DisplaySettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ANIMATIONS = "animations";
    private static final String KEY_ACCELEROMETER = "accelerometer";
  //  private static final String KEY_PROXIMITY_SCREEN_OFF = "proximity_screen_off";
    private static final String KEY_BRIGHTNESS_SETTINGS = "brightness_settings";
    private static final String KEY_BRIGHTNESS = "brightness";

 private BrightnessPreference mBrightnessPreference;
    private Preference mBrightnessSettingsPreference;
    private ListPreference mAnimations;
    private CheckBoxPreference mAccelerometer;
//    private CheckBoxPreference mProximityScreenOff;
    private float[] mAnimationScales;

    private IWindowManager mWindowManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getContentResolver();
        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        addPreferencesFromResource(R.xml.display_settings);

        mBrightnessPreference = (BrightnessPreference)findPreference(KEY_BRIGHTNESS);
        mBrightnessSettingsPreference = (Preference)findPreference(KEY_BRIGHTNESS_SETTINGS);
        if (FeatureQuery.FEATURE_SETTINGS_USE_CONTENT_ADAPTIVE_BACKLIGHT) {
            this.getPreferenceScreen().removePreference(mBrightnessPreference);
        }else{
            this.getPreferenceScreen().removePreference(mBrightnessSettingsPreference);
        }
        mAnimations = (ListPreference) findPreference(KEY_ANIMATIONS);
        mAnimations.setOnPreferenceChangeListener(this);
        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
		
		CharSequence[] aents = mAnimations.getEntryValues();
		aents[2] = aents[1];
		mAnimations.setEntryValues(aents);
		mAnimations.filterRemoveItem(1);

   //     mProximityScreenOff = (CheckBoxPreference) findPreference(KEY_PROXIMITY_SCREEN_OFF);
   //     mProximityScreenOff.setPersistent(false);
//#ifdef CUST_EDITION
	//	if(SystemProperties.get("ro.product.model").equals("S500"))
	//	{
	//		mProximityScreenOff.setEnabled(false);
	//		mProximityScreenOff.setChecked(false);
	//	}

        ListPreference screenTimeoutPreference =
            (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        screenTimeoutPreference.setValue(String.valueOf(Settings.System.getInt(
                resolver, SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE)));
        screenTimeoutPreference.setOnPreferenceChangeListener(this);
        screenTimeoutPreference.setSummary(screenTimeoutPreference.getEntry());
        disableUnusableTimeouts(screenTimeoutPreference);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
            (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        Log.d(TAG, "maxTimeout " + maxTimeout);
        if (maxTimeout <= 0) {
            return; // policy not enforced
        }

        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.valueOf(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateState(true);
    }

    private void updateState(boolean force) {
        int animations = 0;
        try {
            mAnimationScales = mWindowManager.getAnimationScales();
        } catch (RemoteException e) {
        }
        if (mAnimationScales != null) {
            if (mAnimationScales.length >= 1) {
                animations = ((int)(mAnimationScales[0]+.5f)) % 10;
            }
            if (mAnimationScales.length >= 2) {
                animations += (((int)(mAnimationScales[1]+.5f)) & 0x7) * 10;
            }
        }
        int idx = 0;
        int best = 0;
        CharSequence[] aents = mAnimations.getEntryValues();
        for (int i=0; i<aents.length; i++) {
            int val = Integer.parseInt(aents[i].toString());
            if (val <= animations && val > best) {
                best = val;
                idx = i;
            }
        }
        mAnimations.setValueIndex(idx);
        updateAnimationsSummary(mAnimations.getValue());
        mAccelerometer.setChecked(Settings.System.getInt(
                getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0);
     //   mProximityScreenOff.setChecked(Settings.System.getInt(
     //           getContentResolver(),
     //           Settings.System.PROXIMITY_SCREEN_OFF, 0) != 0);
    }

    private void updateAnimationsSummary(Object value) {
        CharSequence[] summaries = getResources().getTextArray(R.array.animations_summaries);
        CharSequence[] values = mAnimations.getEntryValues();
		
		Log.d(TAG, "updateAnimationsSummary:length"+(values.length));
        for (int i=0; i<values.length; i++) {
            //Log.i("foo", "Comparing entry "+ values[i] + " to current "
            //        + mAnimations.getValue());
            if(i == 1)
            {
            	if (values[i].equals(value)) {
                mAnimations.setSummary(summaries[i+1]);
                break;
            	}
            }
            if (values[i].equals(value)) {
                mAnimations.setSummary(summaries[i]);
                break;
            }
        }
    }

    private void updateScreenTimeoutSummary(Object value) {
        CharSequence[] entries = getResources().getTextArray(R.array.screen_timeout_entries);
        ListPreference preference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        int index = preference.findIndexOfValue((String)value);
        preference.setSummary(entries[index]);
    }


    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
     Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION,
                    mAccelerometer.isChecked() ? 1 : 0);
        } /*else if (preference == mProximityScreenOff) {
            Settings.System.putInt(getContentResolver(), Settings.System.PROXIMITY_SCREEN_OFF,
                    mProximityScreenOff.isChecked() ? 1 : 0);
			
        } */else if (preference == mBrightnessSettingsPreference) {
            return false;
        }
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_ANIMATIONS.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                if (mAnimationScales.length >= 1) {
                    mAnimationScales[0] = value%10;
                }
                if (mAnimationScales.length >= 2) {
                    mAnimationScales[1] = (value/10)%10;
                }
                try {
                    mWindowManager.setAnimationScales(mAnimationScales);
                } catch (RemoteException e) {
                }
                updateAnimationsSummary(objValue);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist animation setting", e);
            }

        } else if (KEY_SCREEN_TIMEOUT.equals(key)) {
            if (Utils.isMonkeyRunning()) {
                return false;
            }
            int value = Integer.parseInt((String) objValue);
            int oldvalue = Integer.parseInt(((ListPreference)preference).getValue());
            Log.d(TAG, "update screen timeout from "+ oldvalue + " to " + value);
            if (value != oldvalue) {
                try {
                    Settings.System.putInt(getContentResolver(),
                            SCREEN_OFF_TIMEOUT, value);
                    updateScreenTimeoutSummary(objValue);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "could not persist screen timeout setting", e);
                }
            }
        }

        return true;
    }
}
