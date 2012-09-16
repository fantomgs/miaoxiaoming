/*
 * 
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
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


import android.content.Context;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;




/**
 * CallbackEnabler is a helper to manage the callback on/off checkbox
 * preference. It is turns on/off callback feature and ensures the summary of the
 * preference reflects the current state.
 */
public class CallbackEnabler extends CheckBoxPreference
        implements Preference.OnPreferenceChangeListener {

    private String LOG_TAG = "CallbackEnabler";
    private static final boolean DBG = false;

    public CallbackEnabler(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.i(LOG_TAG, "CallbackEnabler");

        int flag;
        
        try {
            flag = Settings.System.getInt(getContext().getContentResolver(), 
                Settings.System.CALLBACK_PRIORITY_ENABLED);
        } catch (SettingNotFoundException snfe) {
            flag = 1;
        }
        setChecked(flag != 0);
    }
    
    public boolean onPreferenceChange(Preference preference, Object value) {
        // Don't update UI to opposite state until we're sure
        int flag = (Boolean)value == true ? 1 : 0;
        if (Settings.System.putInt(getContext().getContentResolver(), 
                Settings.System.CALLBACK_PRIORITY_ENABLED, flag)) {
            return true;
        }

        return false;
    }
    
    public void resume() {
        setOnPreferenceChangeListener(this);
    }
    
    public void pause() {
        setOnPreferenceChangeListener(this);
    }

}

