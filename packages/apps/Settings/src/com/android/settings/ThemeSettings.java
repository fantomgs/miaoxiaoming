/*
 * Copyright (C) 2011, Code Aurora Forum. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of Code Aurora Forum, Inc. nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.
 
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


package com.android.settings;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import android.util.Log;
import android.widget.Toast;

public class ThemeSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener, OnCancelListener {
    private static final String TAG="ThemeSettings";

    private static final String PROPERTY_THEME = "persist.sys.theme";
    private static final String PROPERTY_THEME_CONFIG = "persist.sys.theme_config";
    private static final String SETTINGS_THEME_RELOAD = "theme_reload";

    private static final String ACTION_THEME_SAVE = "com.android.launcher.action.SAVE_THEME";
    private static final String ACTION_THEME_SAVE_OK = "com.android.launcher.action.SAVE_THEME_OK";

    private ProgressDialog mProgressDialog;
    private String mSelectedThemeConfig;
    private ThemePreference mSelectedTheme;

    // We use static array here for demo.
    private static final String[] themeKeys = {
        "google",
        "qrd"
    };
    
    private static final int[] themeTitlesId = {
        R.string.google_theme,
        R.string.qrd_theme
    };

    private static final String[] themeConfigs = {
        "config",       // default google theme
        "config_new"    // qrd theme
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.theme_settings);
        getListView().setItemsCanFocus(true);
        fillList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    private void fillList() {
        PreferenceGroup themeList = (PreferenceGroup) findPreference("theme_list");
        themeList.removeAll();

        String mSelectedThemeConfig = SystemProperties.get(PROPERTY_THEME_CONFIG, "config");
        for (int i = 0; i < themeKeys.length; i++) {
            ThemePreference pref = new ThemePreference(this);
            pref.setKey(themeKeys[i]);
            pref.setTitle(getString(themeTitlesId[i]));
            pref.setConfig(themeConfigs[i]);
            pref.setOnPreferenceChangeListener(this);

            if (mSelectedThemeConfig.equals(themeConfigs[i])) {
                pref.setChecked();
                mSelectedTheme = pref;
            }

            themeList.addPreference(pref);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        Log.d(TAG, "preference is " + preference.getKey());
        mSelectedThemeConfig = ((ThemePreference) preference).getConfig();
        //mSelectedTheme = preference;
        displayAlertDialog(preference.getTitle());

        // Don't update UI to opposite state until we're sure
        return true;
    }

    public void onCancel(DialogInterface dialog) {
        fillList();
    }
    
    void displayAlertDialog(CharSequence msg) {
        Log.d(TAG, "displayAlertDialog!" + msg);
        new AlertDialog.Builder(this).setMessage(msg)
               .setTitle(android.R.string.dialog_alert_title)
               .setIcon(android.R.drawable.ic_dialog_alert)
               .setPositiveButton(android.R.string.yes, changeThemeOnClickListener)
               .setNegativeButton(android.R.string.no, changeThemeOnClickListener)
               .setOnCancelListener(this)
               .show();
    }

    void displaySaveThemeDialog() {
        Log.d(TAG, "displaySaveThemeDialog!");
        new AlertDialog.Builder(this).setMessage(getString(R.string.save_theme_message))
               .setTitle(android.R.string.dialog_alert_title)
               .setIcon(android.R.drawable.ic_dialog_alert)
               .setPositiveButton(android.R.string.yes, saveThemeOnClickListener)
               .setNegativeButton(android.R.string.no, saveThemeOnClickListener)
               .show();
    }

    void displayProgressDialog () {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle(R.string.save_theme);
            mProgressDialog.setMessage(getString(R.string.saving_theme));
        }

        mProgressDialog.show();
    }

    private OnClickListener changeThemeOnClickListener = new OnClickListener() {
        // This is a method implemented for DialogInterface.OnClickListener.
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "onClick!");
            if (which == DialogInterface.BUTTON_POSITIVE) {
                dialog.dismiss();
                displaySaveThemeDialog();
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                Log.d(TAG, "on cancel click");
                dialog.cancel();
            }
        }
    };

    private OnClickListener saveThemeOnClickListener = new OnClickListener() {
        // This is a method implemented for DialogInterface.OnClickListener.
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "onClick!");
            if (which == DialogInterface.BUTTON_POSITIVE) {
                dialog.dismiss();
                displayProgressDialog();
                doThemeSave();
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                Log.d(TAG, "on cancel click");
                dialog.dismiss();
                changeTheme();
            }
        }
    };

    private void doThemeSave() {
        Intent intent = new Intent(ACTION_THEME_SAVE);
        sendBroadcast(intent);  // make launcher save current theme
        IntentFilter filter = new IntentFilter(ACTION_THEME_SAVE_OK);
        registerReceiver(mReceiver, filter);    // register receiver to receive the response of saving theme action from launcher
    }

    private void changeTheme() {
        SystemProperties.set(PROPERTY_THEME_CONFIG, mSelectedThemeConfig);
        Settings.System.putInt(getContentResolver(),
            SETTINGS_THEME_RELOAD, 1);
        String toastText = getString(R.string.apply_theme);
        Toast.makeText(ThemeSettings.this,
            toastText, Toast.LENGTH_LONG).show();
        restartLauncher();
    }

    private void restartLauncher() {
        ActivityManager acm = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        acm.restartPackage("com.android.launcher");
        Intent homeIntent =  new Intent(Intent.ACTION_MAIN, null);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(homeIntent);
        finish();
    }
    
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            final String action = intent.getAction();
            Log.i(TAG, "action " + action);
            if (ACTION_THEME_SAVE_OK.equals(action)) {
                unregisterReceiver(this);
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                changeTheme();
            }
        }
    };
}
