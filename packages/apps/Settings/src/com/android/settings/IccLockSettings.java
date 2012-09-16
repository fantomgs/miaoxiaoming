/*
 * Copyright (C) 2008 The Android Open Source Project
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
// HEXING123 MODIFY
package com.android.settings;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.content.Intent;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import android.os.SystemProperties;


import android.telephony.TelephonyManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.CommandException;

/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends PreferenceActivity 
        implements EditPinPreference.OnPinEnteredListener {

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;
    
    // Keys in xml file
    private static final String PIN_DIALOG = "icc_pin";
    private static final String PIN_TOGGLE = "icc_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";
    
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;
    
    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;
    
    private Phone mPhone;
    
    private EditPinPreference mPinDialog;
    private CheckBoxPreference mPinToggle;
    
    private Resources mRes;

    // For async handler to identify request type
    private static final int ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int CHANGE_ICC_PIN_COMPLETE = 101;

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case ENABLE_ICC_PIN_COMPLETE:
                    iccLockChanged(ar);
                    break;
                case CHANGE_ICC_PIN_COMPLETE:
                    iccPinChanged(ar);
                    break;
            }

            return;
        }
    };
    
    // For top-level settings screen to query
    static boolean isIccLockEnabled() {
        return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
    }
    
    static String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled() 
                ? res.getString(R.string.icc_lock_on)
                : res.getString(R.string.icc_lock_off);
        return summary;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }
		if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
		{
			addPreferencesFromResource(R.xml.icc_lock_settings);
		}
		else
		{
        		addPreferencesFromResource(R.xml.icc_sim_lock_settings);
		}

        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (CheckBoxPreference) findPreference(PIN_TOGGLE);
        if (savedInstanceState != null && savedInstanceState.containsKey(DIALOG_STATE)) {
            mDialogState = savedInstanceState.getInt(DIALOG_STATE);
            mPin = savedInstanceState.getString(DIALOG_PIN);
            mError = savedInstanceState.getString(DIALOG_ERROR);
            mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);
        }

        mPinDialog.setOnPinEnteredListener(this);
        
        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);
        
        Intent intent = getIntent();
        int subscription = intent.getIntExtra(SelectSubscription.SUBSCRIPTION_ID, PhoneFactory.getDefaultSubscription());
        // Use the right phone based on the subscription selected.
        mPhone = PhoneFactory.getPhone(subscription);
        mRes = getResources();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (!mPhone.getIccCard().hasIccCard()){
          mPinToggle.setChecked(false);
          mPinToggle.setEnabled(false);
          mPinDialog.setEnabled(false);
        }else{
          mPinToggle.setEnabled(true);
          mPinDialog.setEnabled(true);
          mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
        }
        
        if (mDialogState != OFF_MODE) {
            showPinDialog();
        } else {
            // Prep for standard click on "Change PIN"
            resetDialogState();
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);
        } else {
            super.onSaveInstanceState(out);
        }
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        setDialogValues();
        
        mPinDialog.showPinDialog();
    }
    
    private void setDialogValues() {
        mPinDialog.setText(mPin);
        String message = "";
        switch (mDialogState) {
            case ICC_LOCK_MODE:
				if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
				{
					message = mRes.getString(R.string.icc_uim_enter_pin);
                	mPinDialog.setDialogTitle(mToState 
                        ? mRes.getString(R.string.icc_uim_enable_icc_lock)
                        : mRes.getString(R.string.icc_uim_disable_icc_lock));
				}
				else
				{
                	message = mRes.getString(R.string.icc_enter_pin);
                	mPinDialog.setDialogTitle(mToState 
                        ? mRes.getString(R.string.icc_enable_icc_lock)
                        : mRes.getString(R.string.icc_disable_icc_lock));
				}
                break;
            case ICC_OLD_MODE:
				if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
				{
					message = mRes.getString(R.string.icc_uim_enter_old);
                	mPinDialog.setDialogTitle(mRes.getString(R.string.icc_uim_change_pin));
				}
				else
				{
                	message = mRes.getString(R.string.icc_enter_old);
                	mPinDialog.setDialogTitle(mRes.getString(R.string.icc_change_pin));
				}
                break;
            case ICC_NEW_MODE:
				if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
				{
					message = mRes.getString(R.string.icc_uim_enter_new);
                	mPinDialog.setDialogTitle(mRes.getString(R.string.icc_uim_change_pin));
				}
				else
				{
                	message = mRes.getString(R.string.icc_enter_new);
                	mPinDialog.setDialogTitle(mRes.getString(R.string.icc_change_pin));
				}
                break;
            case ICC_REENTER_MODE:
				if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
				{
					message = mRes.getString(R.string.icc_reenter_new);
                	mPinDialog.setDialogTitle(mRes.getString(R.string.icc_uim_change_pin));
				}
				else
				{
                	message = mRes.getString(R.string.icc_reenter_new);
                	mPinDialog.setDialogTitle(mRes.getString(R.string.icc_change_pin));
				}
                break;
        }
        if (mError != null) {
            message = mError + "\n" + message;
            mError = null;
        }
        mPinDialog.setDialogMessage(message);
    }

    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            resetDialogState();
            return;
        }
        
        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.icc_bad_pin);
            showPinDialog();
            return;
        }
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                tryChangeIccLockState();
                break;
            case ICC_OLD_MODE:
                mOldPin = mPin;
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
                break;
            case ICC_NEW_MODE:
                mNewPin = mPin;
                mDialogState = ICC_REENTER_MODE;
                mPin = null;
                showPinDialog();
                break;
            case ICC_REENTER_MODE:
                if (!mPin.equals(mNewPin)) {
                    mError = mRes.getString(R.string.icc_pins_dont_match);
                    mDialogState = ICC_NEW_MODE;
                    mPin = null;
                    showPinDialog();
                } else {
                    mError = null;
                    tryChangePin();
                }
                break;
        }
    }
    
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog  
            mPinToggle.setChecked(!mToState);  
            mDialogState = ICC_LOCK_MODE;
            showPinDialog();
        } else if (preference == mPinDialog) {
            mDialogState = ICC_OLD_MODE;
            return false;
        }
        return true;
    }

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        Message callback = Message.obtain(mHandler, ENABLE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);

    }

    private void iccLockChanged(AsyncResult ar) {
        if (ar.exception == null) {
            if (mToState) {
                Toast.makeText(this, mRes.getString(R.string.icc_pin_enabled), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, mRes.getString(R.string.icc_pin_disabled), Toast.LENGTH_SHORT).show();
            }
            mPinToggle.setChecked(mToState);
        } else {
            if (ar.exception instanceof CommandException) {
                if (((CommandException) (ar.exception)).getCommandError()
                            == CommandException.Error.REQUEST_NOT_SUPPORTED) 
                {
                	if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
					{
						Toast.makeText(this, mRes.getString(R.string.icc_uim_lock_change_not_supported),
                            Toast.LENGTH_SHORT)
                            .show();
                	}
					else
					{
                    	Toast.makeText(this, mRes.getString(R.string.icc_lock_change_not_supported),
                            Toast.LENGTH_SHORT)
                            .show();
					}
                } else {
                    displayRetryCounter(mRes.getString(R.string.icc_change_failed));
                }
             } else {
                 //check for the icc card presence for the default phone
                 if (!(mPhone.getIccCard().hasIccCard())) {
				 	 if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
					 {
					 	Toast.makeText(this, mRes.getString(R.string.icc_uim_absent),
                             Toast.LENGTH_SHORT)
                             .show();
				 	 }
					 else
					 {
                     	Toast.makeText(this, mRes.getString(R.string.icc_sim_absent),
                             Toast.LENGTH_SHORT)
                             .show();
					 }
                 }
             }
        }
        resetDialogState();
    }

    private void iccPinChanged(AsyncResult ar) {
        if (ar.exception != null) {
            if (ar.exception instanceof CommandException) {
                if(((CommandException) (ar.exception)).getCommandError()
                            == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                    // If the exception is REQUEST_NOT_SUPPORTED then change pin couldn't
                    // happen because SIM lock is not enabled.
                    Toast.makeText(this, mRes.getString(R.string.icc_change_failed_enable_icc_lock),
                            Toast.LENGTH_SHORT)
                            .show();
                } else {
                    displayRetryCounter(mRes.getString(R.string.icc_change_failed));
                }
            } else {
                //check for the icc card presence for the default phone
                if (!(mPhone.getIccCard().hasIccCard())) {
					if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
					{
						Toast.makeText(this, mRes.getString(R.string.icc_uim_absent),
                            Toast.LENGTH_SHORT)
                            .show();
					}
					else
					{
                    	Toast.makeText(this, mRes.getString(R.string.icc_sim_absent),
                            Toast.LENGTH_SHORT)
                            .show();
					}
                }
            }
        }
		else 
        {
        	if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
			{
				Toast.makeText(this, mRes.getString(R.string.icc_uim_change_succeeded),
                    Toast.LENGTH_SHORT)
                    .show();
        	}
			else
			{
            	Toast.makeText(this, mRes.getString(R.string.icc_change_succeeded),
                    Toast.LENGTH_SHORT)
                    .show();
			}

        }
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, CHANGE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    private void displayRetryCounter(String s) {
        int attempts = mPhone.getIccCard().getIccPin1RetryCount();
        if (attempts >= 0) {
            String displayMsg = s + mRes.getString(R.string.icc_pin_attempts) + attempts;
            Toast.makeText(this, displayMsg, Toast.LENGTH_SHORT).show();
        } else {
        if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
		{
			Toast.makeText(this, mRes.getString(R.string.icc_uim_lock_failed), Toast.LENGTH_SHORT)
            .show();
        }
		else
		{
            Toast.makeText(this, mRes.getString(R.string.icc_lock_failed), Toast.LENGTH_SHORT)
            .show();
		}
        }
    }
}