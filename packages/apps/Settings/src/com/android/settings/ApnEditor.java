/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
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

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.telephony.TelephonyManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.RILConstants;


public class ApnEditor extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
                    Preference.OnPreferenceChangeListener {

    private final static String TAG = ApnEditor.class.getSimpleName();

    private final static String SAVED_POS = "pos";
    private final static String KEY_AUTH_TYPE = "auth_type";
    private final static String KEY_IP = "ip_version";

    private static final int MENU_DELETE = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private static final int MENU_CANCEL = Menu.FIRST + 2;

    private static String sNotSet;
    private EditTextPreference mName;
    private EditTextPreference mApn;
    private EditTextPreference mProxy;
    private EditTextPreference mPort;
    private EditTextPreference mUser;
    private EditTextPreference mServer;
    private EditTextPreference mPassword;
    private EditTextPreference mMmsc;
    private EditTextPreference mMcc;
    private EditTextPreference mMnc;
    private EditTextPreference mMmsProxy;
    private EditTextPreference mMmsPort;
    private ListPreference mAuthType;
    private EditTextPreference mApnType;
    private ListPreference mIp;

    private String mCurMnc;
    private String mCurMcc;
    private int mSubscription = 0;
    private boolean mDisableEditor = false;

    private Uri mUri;
    private Cursor mCursor;
    private boolean mNewApn;
    private boolean mFirstTime;
    private Resources mRes;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static final String[] sProjection = new String[] {
            Telephony.Carriers._ID,     // 0
            Telephony.Carriers.NAME,    // 1
            Telephony.Carriers.APN,     // 2
            Telephony.Carriers.PROXY,   // 3
            Telephony.Carriers.PORT,    // 4
            Telephony.Carriers.USER,    // 5
            Telephony.Carriers.SERVER,  // 6
            Telephony.Carriers.PASSWORD, // 7
            Telephony.Carriers.MMSC, // 8
            Telephony.Carriers.MCC, // 9
            Telephony.Carriers.MNC, // 10
            Telephony.Carriers.NUMERIC, // 11
            Telephony.Carriers.MMSPROXY,// 12
            Telephony.Carriers.MMSPORT, // 13
            Telephony.Carriers.AUTH_TYPE, // 14
            Telephony.Carriers.TYPE, // 15
            Telephony.Carriers.IPVERSION //16
    };

    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int PROXY_INDEX = 3;
    private static final int PORT_INDEX = 4;
    private static final int USER_INDEX = 5;
    private static final int SERVER_INDEX = 6;
    private static final int PASSWORD_INDEX = 7;
    private static final int MMSC_INDEX = 8;
    private static final int MCC_INDEX = 9;
    private static final int MNC_INDEX = 10;
    private static final int MMSPROXY_INDEX = 12;
    private static final int MMSPORT_INDEX = 13;
    private static final int AUTH_TYPE_INDEX = 14;
    private static final int TYPE_INDEX = 15;
    private static final int IP_INDEX = 16;


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.apn_editor);

        sNotSet = getResources().getString(R.string.apn_not_set);
        mName = (EditTextPreference) findPreference("apn_name");
        mApn = (EditTextPreference) findPreference("apn_apn");
        mProxy = (EditTextPreference) findPreference("apn_http_proxy");
        mPort = (EditTextPreference) findPreference("apn_http_port");
        mUser = (EditTextPreference) findPreference("apn_user");
        mServer = (EditTextPreference) findPreference("apn_server");
        mPassword = (EditTextPreference) findPreference("apn_password");
        mMmsProxy = (EditTextPreference) findPreference("apn_mms_proxy");
        mMmsPort = (EditTextPreference) findPreference("apn_mms_port");
        mMmsc = (EditTextPreference) findPreference("apn_mmsc");
        mMcc = (EditTextPreference) findPreference("apn_mcc");
        mMnc = (EditTextPreference) findPreference("apn_mnc");
        mApnType = (EditTextPreference) findPreference("apn_type");

        mIp = (ListPreference) findPreference("ip_version");
        mIp.setOnPreferenceChangeListener(this);
        mAuthType = (ListPreference) findPreference(KEY_AUTH_TYPE);
        mAuthType.setOnPreferenceChangeListener(this);        

        /* TODO: align with 2.3.4_r1
        mProtocol = (ListPreference) findPreference(KEY_PROTOCOL);
        mProtocol.setOnPreferenceChangeListener(this);
        */

        mRes = getResources();

        final Intent intent = getIntent();
        final String action = intent.getAction();
        mDisableEditor = intent.getBooleanExtra("DISABLE_EDITOR",false);
        if (mDisableEditor) getPreferenceScreen().setEnabled(false);
        // Read the subscription received from Phone settings.
        mSubscription = intent.getIntExtra(SelectSubscription.SUBSCRIPTION_ID, TelephonyManager.getDefaultSubscription());
        Log.d(TAG,"ApnEditor onCreate received sub: " + mSubscription);
        mFirstTime = icicle == null;

        if (action.equals(Intent.ACTION_EDIT)) {
            mUri = intent.getData();
        } else if (action.equals(Intent.ACTION_INSERT)) {
            if (mFirstTime || icicle.getInt(SAVED_POS) == 0) {
                mUri = getContentResolver().insert(intent.getData(), new ContentValues());
            } else {
                mUri = ContentUris.withAppendedId(Telephony.Carriers.CONTENT_URI,
                        icicle.getInt(SAVED_POS));
            }
            mNewApn = true;
            // If we were unable to create a new note, then just finish
            // this activity.  A RESULT_CANCELED will be sent back to the
            // original activity if they requested a result.
            if (mUri == null) {
                Log.w(TAG, "Failed to insert new telephony provider into "
                        + getIntent().getData());
                finish();
                return;
            }

            // The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {
            finish();
            return;
        }

        mCursor = managedQuery(mUri, sProjection, null, null);
        mCursor.moveToFirst();

        fillUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    private static final String[] ipVersionString = {
            "IP", "IPV6", "IPV4V6"
    };

    private int getIpVersionIndex(String ver) {
        for (int i = 0; i < ipVersionString.length; i++) {
            if (ipVersionString[i].equals(ver))
                return i;
        }
        return 0;
    }

    private String getOperatorNumeric() {
        String numeric;
        numeric = TelephonyManager.getTelephonyProperty
                    (TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, mSubscription, null);
        if (TextUtils.isEmpty(numeric)) {
            numeric = TelephonyManager.getTelephonyProperty
                    (TelephonyProperties.PROPERTY_OPERATOR_NUMERIC, mSubscription, null);
        }
        return numeric;
    }

    private void fillUi() {
        if (mFirstTime) {
            mFirstTime = false;
            // Fill in all the values from the db in both text editor and summary
            mName.setText(mCursor.getString(NAME_INDEX));
            mApn.setText(mCursor.getString(APN_INDEX));
            mProxy.setText(mCursor.getString(PROXY_INDEX));
            mPort.setText(mCursor.getString(PORT_INDEX));
            mUser.setText(mCursor.getString(USER_INDEX));
            mServer.setText(mCursor.getString(SERVER_INDEX));
            mPassword.setText(mCursor.getString(PASSWORD_INDEX));
            mMmsProxy.setText(mCursor.getString(MMSPROXY_INDEX));
            mMmsPort.setText(mCursor.getString(MMSPORT_INDEX));
            mMmsc.setText(mCursor.getString(MMSC_INDEX));
            mMcc.setText(mCursor.getString(MCC_INDEX));
            mMnc.setText(mCursor.getString(MNC_INDEX));
            mApnType.setText(mCursor.getString(TYPE_INDEX));
            if (mNewApn) {
                String numeric = getOperatorNumeric();
                // MCC is first 3 chars and then in 2 - 3 chars of MNC
                if (numeric != null && numeric.length() > 4) {
                    // Country code
                    String mcc = numeric.substring(0, 3);
                    // Network code
                    String mnc = numeric.substring(3);
                    // Auto populate MNC and MCC for new entries, based on what SIM reports
                    mMcc.setText(mcc);
                    mMnc.setText(mnc);
                    mCurMnc = mnc;
                    mCurMcc = mcc;
                }
            }
            int authVal = mCursor.getInt(AUTH_TYPE_INDEX);
            if (authVal != -1) {
                mAuthType.setValueIndex(authVal);
            }

            int verIndex = getIpVersionIndex(mCursor.getString(IP_INDEX));
            mIp.setValueIndex(verIndex);

        }

        mName.setSummary(checkNull(mName.getText()));
        mApn.setSummary(checkNull(mApn.getText()));
        mProxy.setSummary(checkNull(mProxy.getText()));
        mPort.setSummary(checkNull(mPort.getText()));
        mUser.setSummary(checkNull(mUser.getText()));
        mServer.setSummary(checkNull(mServer.getText()));
        mPassword.setSummary(starify(mPassword.getText()));
        mMmsProxy.setSummary(checkNull(mMmsProxy.getText()));
        mMmsPort.setSummary(checkNull(mMmsPort.getText()));
        mMmsc.setSummary(checkNull(mMmsc.getText()));
        mMcc.setSummary(checkNull(mMcc.getText()));
        mMnc.setSummary(checkNull(mMnc.getText()));
        mApnType.setSummary(checkNull(mApnType.getText()));

        String authVal = mAuthType.getValue();
        if (authVal != null) {
            setListPreferenceSummary(mAuthType, R.array.apn_auth_entries, authVal);
        } else {
            mAuthType.setSummary(sNotSet);
        }

        String version = mIp.getValue();
        if (version != null) {
            setListPreferenceSummary(mIp, R.array.ip_version_entries, version);
        } else {
            mIp.setSummary(sNotSet);
        }
    }

    private void setListPreferenceSummary(ListPreference pref, int array, Object newValue) {
        int index = Integer.parseInt((String) newValue);
        pref.setValueIndex(index);
        String[] values = mRes.getStringArray(array);
        pref.setSummary(values[index]);
   }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (KEY_AUTH_TYPE.equals(key)) {
            try {
                setListPreferenceSummary(mAuthType, R.array.apn_auth_entries, newValue);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (KEY_IP.equals(key)) {
            try {
                setListPreferenceSummary(mIp, R.array.ip_version_entries, newValue);
            } catch (NumberFormatException e) {
                return false;
            }
            return true;
        }
        /* TODO: align with 2.3.4_r1 
        if (KEY_PROTOCOL.equals(key)) {
            String protocol = protocolDescription((String) newValue);
            if (protocol == null) {
                return false;
            }
            mProtocol.setSummary(protocol);
            mProtocol.setValue((String) newValue);
        }
        */
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (mDisableEditor) return true;
        // If it's a new APN, then cancel will delete the new entry in onPause
        if (!mNewApn) {
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete)
                .setIcon(android.R.drawable.ic_menu_delete);
        }
        menu.add(0, MENU_SAVE, 0, R.string.menu_save)
            .setIcon(android.R.drawable.ic_menu_save);
        menu.add(0, MENU_CANCEL, 0, R.string.menu_cancel)
            .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DELETE:
            deleteApn();
            return true;
        case MENU_SAVE:
            if (validateAndSave(false)) {
                finish();
            }
            return true;
        case MENU_CANCEL:
            if (mNewApn) {
                getContentResolver().delete(mUri, null, null);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (validateAndSave(false)) {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        if (validateAndSave(true)) {
            icicle.putInt(SAVED_POS, mCursor.getInt(ID_INDEX));
        }
    }

    /**
     * Check the key fields' validity and save if valid.
     * @param force save even if the fields are not valid, if the app is
     *        being suspended
     * @return true if the data was saved
     */
    private boolean validateAndSave(boolean force) {
        String name = checkNotSet(mName.getText());
        String apn = checkNotSet(mApn.getText());
        String mcc = checkNotSet(mMcc.getText());
        String mnc = checkNotSet(mMnc.getText());
        int dataSub = 0;

        String errorMsg = null;
        if (name.length() < 1) {
            errorMsg = mRes.getString(R.string.error_name_empty);
        } else if (apn.length() < 1) {
            errorMsg = mRes.getString(R.string.error_apn_empty);
        } else if (mcc.length() != 3) {
            errorMsg = mRes.getString(R.string.error_mcc_not3);
        } else if ((mnc.length() & 0xFFFE) != 2) {
            errorMsg = mRes.getString(R.string.error_mnc_not23);
        }

        if (errorMsg != null && !force) {
            showErrorMessage(errorMsg);
            return false;
        }

        if (!mCursor.moveToFirst()) {
            Log.w(TAG,
                    "Could not go to the first row in the Cursor when saving data.");
            return false;
        }

        // If it's a new APN and a name or apn haven't been entered, then erase the entry
        if (force && mNewApn && name.length() < 1 && apn.length() < 1) {
            getContentResolver().delete(mUri, null, null);
            return false;
        }

        ContentValues values = new ContentValues();

        // Add a dummy name "Untitled", if the user exits the screen without adding a name but
        // entered other information worth keeping.
        values.put(Telephony.Carriers.NAME,
                name.length() < 1 ? getResources().getString(R.string.untitled_apn) : name);
        values.put(Telephony.Carriers.APN, apn);
        values.put(Telephony.Carriers.PROXY, checkNotSet(mProxy.getText()));
        values.put(Telephony.Carriers.PORT, checkNotSet(mPort.getText()));
        values.put(Telephony.Carriers.MMSPROXY, checkNotSet(mMmsProxy.getText()));
        values.put(Telephony.Carriers.MMSPORT, checkNotSet(mMmsPort.getText()));
        values.put(Telephony.Carriers.USER, checkNotSet(mUser.getText()));
        values.put(Telephony.Carriers.SERVER, checkNotSet(mServer.getText()));
        values.put(Telephony.Carriers.PASSWORD, checkNotSet(mPassword.getText()));
        values.put(Telephony.Carriers.MMSC, checkNotSet(mMmsc.getText()));

        String authVal = mAuthType.getValue();
        if (authVal != null) {
            values.put(Telephony.Carriers.AUTH_TYPE, Integer.parseInt(authVal));
        }

        String version = mIp.getValue();
        if (version != null) {
            int index = Integer.parseInt(version);
            if (index >= ipVersionString.length || index < 0) {
                index = 0;
            }
            values.put(Telephony.Carriers.IPVERSION, ipVersionString[index]);
        }

        values.put(Telephony.Carriers.TYPE, checkNotSet(mApnType.getText()));

        values.put(Telephony.Carriers.MCC, mcc);
        values.put(Telephony.Carriers.MNC, mnc);

        values.put(Telephony.Carriers.NUMERIC, mcc + mnc);


        //QRD Enhancement for user perferred sub
        //need not get dds from system settings
        dataSub = TelephonyManager.getPreferredDataSubscription();
        /*try {
            dataSub = Settings.System.getInt(getContentResolver(),Settings.System.MULTI_SIM_DATA_CALL);
        } catch (SettingNotFoundException snfe) {
            Log.e(TAG, "Exception Reading Multi Sim Data Subscription Value.", snfe);
        }*/
        if (mCurMnc != null && mCurMcc != null) {
            if (mCurMnc.equals(mnc) && mCurMcc.equals(mcc) &&
                mSubscription == dataSub ) {
                values.put(Telephony.Carriers.CURRENT, 1);
            }
        }

        getContentResolver().update(mUri, values, null, null);

        return true;
    }

    private void showErrorMessage(String message) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void deleteApn() {
        getContentResolver().delete(mUri, null, null);
        finish();
    }

    private String starify(String value) {
        if (value == null || value.length() == 0) {
            return sNotSet;
        } else {
            char[] password = new char[value.length()];
            for (int i = 0; i < password.length; i++) {
                password[i] = '*';
            }
            return new String(password);
        }
    }

    private String checkNull(String value) {
        if (value == null || value.length() == 0) {
            return sNotSet;
        } else {
            return value;
        }
    }

    private String checkNotSet(String value) {
        if (value == null || value.equals(sNotSet)) {
            return "";
        } else {
            return value;
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            if (pref.equals(mPassword)){
                pref.setSummary(starify(sharedPreferences.getString(key, "")));
            } else {
                pref.setSummary(checkNull(sharedPreferences.getString(key, "")));
            }
        }
    }
}
