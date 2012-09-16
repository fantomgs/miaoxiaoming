/*
 * Copyright (C) 2008, 2011 The Android Open Source Project
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
package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;

//cust edition
import android.text.method.TextKeyListener;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import android.os.SystemProperties;

/**
 * "SIM network unlock" PIN entry screen.
 *
 * @see PhoneApp.EVENT_SIM_NETWORK_LOCKED
 *
 * TODO: This UI should be part of the lock screen, not the
 * phone app (see bug 1804111).
 */
public class IccNetworkDepersonalizationPanel extends IccPanel {

    //debug constants
    private static final boolean DBG = false;

    //events
    private static final int EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT = 100;

    private Phone mPhone;

    //UI elements
    private EditText     mPinEntry;
    private LinearLayout mEntryPanel;
    private LinearLayout mStatusPanel;
    private TextView     mStatusText;
    private TextView     mTitleText;
	private TextView     mImsiText;

    private Button       mUnlockButton;
    private Button       mDismissButton;

    //private textwatcher to control text entry.
    private TextWatcher mPinEntryWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence buffer, int start, int olen, int nlen) {
        }

        public void onTextChanged(CharSequence buffer, int start, int olen, int nlen) {
        }

        public void afterTextChanged(Editable buffer) {
            if (SpecialCharSequenceMgr.handleChars(
                    getContext(), buffer.toString())) {
                mPinEntry.getText().clear();
            }
        }
    };

    //handler for unlock function results
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT) {
                AsyncResult res = (AsyncResult) msg.obj;
                if (res.exception != null) {
                    if (DBG) log("network depersonalization request failure.");
                    indicateError();
                    postDelayed(new Runnable() {
                                    public void run() {
                                        hideAlert();
                                        mPinEntry.getText().clear();
                                        mPinEntry.requestFocus();
                                    }
                                }, 3000);
                } else {
                    if (DBG) log("network depersonalization success.");
                    indicateSuccess();
                    postDelayed(new Runnable() {
                                    public void run() {
                                        dismiss();
                                    }
                                }, 3000);
                }
            }
        }
    };

// ifdef CUST_EDITION
    //constructor
    public IccNetworkDepersonalizationPanel(Context context, Phone phone) {
        super(context);
        mPhone = phone;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.sim_nwl);


        // PIN entry text field
        mPinEntry = (EditText) findViewById(R.id.nwl_pin_entry);
        mPinEntry.setKeyListener(TextKeyListener.getInstance());
        mPinEntry.setOnClickListener(mUnlockListener);

        // Attach the textwatcher
        CharSequence text = mPinEntry.getText();
        Spannable span = (Spannable) text;
        span.setSpan(mPinEntryWatcher, 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

        mEntryPanel = (LinearLayout) findViewById(R.id.nwl_entry_panel);

        mUnlockButton = (Button) findViewById(R.id.nwl_ndp_unlock);
        mUnlockButton.setOnClickListener(mUnlockListener);

        // The "Dismiss" button is present in some (but not all) products,
        // based on the "sim_network_unlock_allow_dismiss" resource.
        mDismissButton = (Button) findViewById(R.id.nwl_ndp_dismiss);
        if (getContext().getResources().getBoolean(R.bool.sim_network_unlock_allow_dismiss)) {
            if (DBG) log("Enabling 'Dismiss' button...");
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setOnClickListener(mDismissListener);
        } else {
            if (DBG) log("Removing 'Dismiss' button...");
            mDismissButton.setVisibility(View.GONE);
        }

		//imsi 
		if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
		{
			
			String	Imsi =   TelephonyManager.getDefault().getSubscriberId(0);
			mImsiText = (TextView) findViewById(R.id.nwl_imsi_subtype_text);
			String displayText = "imsi:"+Imsi;
			if (DBG) log("Removing 'Dismiss' Imsi..."+Imsi);
			mImsiText.setText(displayText);
			
		}
		

        //status panel is used since we're having problems with the alert dialog.
        mStatusPanel = (LinearLayout) findViewById(R.id.nwl_status_panel);
        mStatusText = (TextView) findViewById(R.id.nwl_status_text);

        if (TelephonyManager.isMultiSimEnabled()) {
            mTitleText = (TextView) findViewById(R.id.nwl_perso_subtype_text);
			if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
			{
				String displayText = getContext().getString(R.string.label_ndp_uim) + " " +
                    getContext().getString(R.string.label_subscription) + (mPhone.getSubscription() + 1);
				mTitleText.setText(displayText);
			}
			else
			{
            	String displayText = getContext().getString(R.string.label_ndp) + " " +
                    getContext().getString(R.string.label_subscription) + (mPhone.getSubscription() + 1);
				mTitleText.setText(displayText);
			}
            
        }
    }
// endif
    @Override
    protected void onStart() {
        super.onStart();
    }

    //Mirrors IccPinUnlockPanel.onKeyDown().
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    View.OnClickListener mUnlockListener = new View.OnClickListener() {
        public void onClick(View v) {
            String pin = mPinEntry.getText().toString();

            if (TextUtils.isEmpty(pin)) {
                return;
            }

            if (DBG) log("requesting network depersonalization with code " + pin);
            log("supplyNetworkDepersonalization for subscription = " + mPhone.getSubscription());
            mPhone.getIccCard().supplyNetworkDepersonalization(pin,
                    Message.obtain(mHandler, EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT));
            indicateBusy();
        }
    };

    private void indicateBusy() {
        mStatusText.setText(R.string.requesting_unlock);
        mEntryPanel.setVisibility(View.GONE);
        mStatusPanel.setVisibility(View.VISIBLE);
    }

    private void indicateError() {
        mStatusText.setText(R.string.unlock_failed);
        mEntryPanel.setVisibility(View.GONE);
        mStatusPanel.setVisibility(View.VISIBLE);
    }

    private void indicateSuccess() {
        mStatusText.setText(R.string.unlock_success);
        mEntryPanel.setVisibility(View.GONE);
        mStatusPanel.setVisibility(View.VISIBLE);
    }

    private void hideAlert() {
        mEntryPanel.setVisibility(View.VISIBLE);
        mStatusPanel.setVisibility(View.GONE);
    }

    View.OnClickListener mDismissListener = new View.OnClickListener() {
            public void onClick(View v) {
                if (DBG) log("mDismissListener: skipping depersonalization...");
                dismiss();
            }
        };

    private void log(String msg) {
        Log.v(TAG, "[IccNetworkDepersonalizationPanel] " + msg);
    }
}