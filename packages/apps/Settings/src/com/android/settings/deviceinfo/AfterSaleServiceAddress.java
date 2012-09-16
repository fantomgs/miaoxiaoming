/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
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
 
//WATERWORLD_12_MODIFY
package com.android.settings;

import android.net.sip.SipManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.telephony.TelephonyManager;
import com.android.settings.R;
import android.util.Log;
import android.preference.PreferenceScreen;
import android.content.Intent;

import android.view.KeyEvent;


public class AfterSaleServiceAddress extends PreferenceActivity {

	private static final String TAG = "AfterSaleServiceAddress";
    private static final String KEY_PARENT = "parent";
    private static final String KEY_CALL_SETTINGS = "call_settings";
	private static final String KEY_SOUND_SETTINGS = "sound_settings";
    private static final String KEY_SYNC_SETTINGS = "sync_settings";
    private static final String KEY_DOCK_SETTINGS = "dock_settings";
    private static final String KEY_MULTI_SETTINGS = "multi_sim_settings";
    private static final String KEY_WIRELESS_SETTINGS = "wireless_settings";
    private static final String KEY_ABOUT_PHONE = "about_settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.after_sale_service_address);

        int activePhoneType = TelephonyManager.getDefault().getPhoneType();
        int settingsPrefScreenIndex;

       /* PreferenceGroup parent = (PreferenceGroup) findPreference(KEY_PARENT);
        if (!TelephonyManager.isMultiSimEnabled()) {
            parent.removePreference(findPreference(
                    KEY_MULTI_SETTINGS));
            settingsPrefScreenIndex = 0;
        } else {
            settingsPrefScreenIndex = 1;
        }
        findPreference(KEY_CALL_SETTINGS).getIntent().putExtra("RESOURCE_INDEX", settingsPrefScreenIndex);
		findPreference(KEY_SOUND_SETTINGS).getIntent().putExtra("RESOURCE_INDEX", settingsPrefScreenIndex);
        findPreference(KEY_WIRELESS_SETTINGS).getIntent().putExtra("RESOURCE_INDEX", settingsPrefScreenIndex);
        findPreference(KEY_ABOUT_PHONE).getIntent().putExtra("RESOURCE_INDEX", settingsPrefScreenIndex);

        Utils.updatePreferenceToSpecificActivityOrRemove(this, parent, KEY_SYNC_SETTINGS, 0);

        Preference dockSettings = parent.findPreference(KEY_DOCK_SETTINGS);
        if (getResources().getBoolean(R.bool.has_dock_settings) == false && dockSettings != null) {
            parent.removePreference(dockSettings);
        }*/
    }
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

		Intent intent = new Intent(this, after_sale_service_address_info.class);
		 Log.v(TAG, "onPreferenceTreeClick City= "+preference.getKey());	
		intent.putExtra("City", preference.getKey());
		startActivity(intent);	
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

	

	//Add By zzg 2012_05_16
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {		
		if (event.getRepeatCount() > 0 && event.getKeyCode() == KeyEvent.KEYCODE_MENU) 
		{			
			return true;
		}
		return super.dispatchKeyEvent(event);
	}
	//Add End

    @Override
    protected void onResume() {
        super.onResume();
    }

}
