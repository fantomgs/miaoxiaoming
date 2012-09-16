/*
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

package com.android.settings;

import android.os.Bundle;
import android.content.Intent;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import android.util.Log;

import com.android.settings.R;
import com.android.settings.multisimsettings.*;


public class MultiSimSettings extends PreferenceActivity {
    private static final String TAG = "MultiSimSettings";

    private static final String[] KEY_PREFERRED_SUBSCRIPTION_LIST = {"voice_list", "data_list", "sms_list"};

    private static final String KEY_COUNTDOWN_TIMER = "multi_sim_countdown";
    private static final String KEY_CALLBACK_TOGGLE = "callback_enable_key";
    private static final String KEY_CONFIG_SUB = "config_sub";

    private PreferredSubscriptionListPreference[] mPreferredSubLists;
    private CountDownPreference mCountDown;
    private CallbackEnabler mCallbackToggle;

    private PreferenceScreen mConfigSub;


    private void initPreferences() {
        mPreferredSubLists = new PreferredSubscriptionListPreference[KEY_PREFERRED_SUBSCRIPTION_LIST.length];

        for (int i = 0; i < KEY_PREFERRED_SUBSCRIPTION_LIST.length; i++) {
             mPreferredSubLists[i] = (PreferredSubscriptionListPreference)findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST[i]);
             mPreferredSubLists[i].setType(MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[i]);
        }

        mCountDown = (CountDownPreference)findPreference(KEY_COUNTDOWN_TIMER);
        mCountDown.updateSummary();
        
        mCallbackToggle = (CallbackEnabler)findPreference(KEY_CALLBACK_TOGGLE);

        mConfigSub = (PreferenceScreen) findPreference(KEY_CONFIG_SUB);
        //mConfigSub.getIntent().putExtra(CONFIG_SUB, true);
        if (mConfigSub != null) {
            Intent intent = mConfigSub.getIntent();
            intent.putExtra(MultiSimSettingsConstants.TARGET_PACKAGE, MultiSimSettingsConstants.CONFIG_PACKAGE);
            intent.putExtra(MultiSimSettingsConstants.TARGET_CLASS, MultiSimSettingsConstants.CONFIG_CLASS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.multi_sim_settings);
        LocalMultiSimSettingsManager.getInstance(getApplicationContext()).setForegroundActivity(this);
        initPreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (PreferredSubscriptionListPreference subPref : mPreferredSubLists) {
             subPref.resume();
        }
        mCallbackToggle.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (PreferredSubscriptionListPreference subPref : mPreferredSubLists) {
             subPref.pause();
        }
        mCallbackToggle.pause();
    }
}

