/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.CountDownTimer;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.view.KeyEvent;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;


import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ProxyManager;
import com.android.internal.telephony.ProxyManager.SubscriptionData;
import com.android.internal.telephony.ProxyManager.Subscription;

import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.ServiceState;

/**
*  Multi Sim Dialer is for multi sim using and all the sim should be activated
*  Working flow: 1. check whether phone is in call
*                       2. check whether is an emergency call
*                       3. check whether to launch dialer if timer set to zero
*                       3. setup dialer screen -> add CountDownTimer if set
*                       4. check if callback is enabled to highlight
*/
public abstract class MultiSimDialerActivity extends Activity {
    private static final String TAG = "MultiSimDialerActivity";
    private static final boolean DBG = true;

    private Context mContext;
    private String mCallingNumber;
    private AlertDialog mChooseDialog = null;
    private CountDownTimer mCountDownTimer;
    private TextView mCountDownTimerText;
    private TextView mCallingNumberText;
    private Intent mIntent;
    private int mPhoneCount = 0;
    private int mTimer;
    //This depends on preferred sub in user setting and if callback priority enabled
    private int mDefaultVoice = 0;

    public static final String PHONE_SUBSCRIPTION = "Subscription";
    public static final int INVALID_SUB = 99;

/**
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = getApplicationContext();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIntent = getIntent();
        mDefaultVoice = getVoiceSubscription();
        mTimer = getCountdownTimer();
        mPhoneCount = TelephonyManager.getPhoneCount();

        if (DBG) Log.v(TAG, "Intent = " + mIntent);

        mCallingNumber = mIntent.getExtras().getString("phoneNumber");
        if (DBG) Log.v(TAG, "mCallingNumber " + mCallingNumber);
        if (mCallingNumber != null) {
            mCallingNumber = PhoneNumberUtils.convertKeypadLettersToDigits(mCallingNumber);
            mCallingNumber = PhoneNumberUtils.stripSeparators(mCallingNumber);
        }

        int subInCall = getInCallSubscription();
        if (INVALID_SUB != subInCall) {
            // use the sub which is already in call
            startOutgoingCall(subInCall);
        } else if (PhoneNumberUtils.isEmergencyNumber(mCallingNumber)) {
            startOutgoingCall(getSubscriptionForEmergencyCall());
        } else if (mTimer == 0) {
            // no need to lauch the dialer to choose for user
            startOutgoingCall(mDefaultVoice);
        } else {
            // if none in use, launch the MultiSimDialer
            LaunchMultiSimDialer();
        }
    }

    protected void onPause() {
        super.onPause();
        if(DBG) Log.v(TAG, "onPause : " + mIntent);
        if (mCountDownTimer!= null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
        if (mChooseDialog != null) {
            mChooseDialog.dismiss();
            mChooseDialog = null;
        }
    }
**/

    protected void closeMultiSimDialer() {
        super.onPause();
        if(DBG) Log.v(TAG, "onPause : " + mIntent);
        if (mCountDownTimer!= null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
        if (mChooseDialog != null) {
            mChooseDialog.dismiss();
            mChooseDialog = null;
        }
    }

    protected void initMultiSimDialer(Intent intent) {
        mContext = getApplicationContext();
        mIntent = intent;
        mDefaultVoice = getVoiceSubscription();
        mTimer = getCountdownTimer();
        mPhoneCount = TelephonyManager.getPhoneCount();

        if (DBG) Log.v(TAG, "Intent = " + mIntent);

        mCallingNumber = mIntent.getExtras().getString("phoneNumber");
        if (DBG) Log.v(TAG, "mCallingNumber " + mCallingNumber);
        if (mCallingNumber != null) {
            mCallingNumber = PhoneNumberUtils.convertKeypadLettersToDigits(mCallingNumber);
            mCallingNumber = PhoneNumberUtils.stripSeparators(mCallingNumber);
        }

        int subInCall = getInCallSubscription();
        if (INVALID_SUB != subInCall) {
            // use the sub which is already in call
            startOutgoingCall(subInCall);
        } else if (PhoneNumberUtils.isEmergencyNumber(mCallingNumber)) {
            startOutgoingCall(getSubscriptionForEmergencyCall());
        } else if (mTimer == 0) {
            // no need to lauch the dialer to choose for user
            startOutgoingCall(mDefaultVoice);
        } else {
            // if none in use, launch the MultiSimDialer
            LaunchMultiSimDialer();
        }
    }

    private int getInCallSubscription() {
        Phone phone = null;
        boolean phoneInCall = false;
        int subscription = INVALID_SUB;
        //checking if any of the phones are in use
        for (int i = 0; i < mPhoneCount; i++) {
             phone = PhoneFactory.getPhone(i);
             boolean inCall = isInCall(phone);
             if ((phone != null) && (inCall)) {
                 subscription = phone.getSubscription();
                 break;
             }
        }
        return subscription;
    }

    private int getSubscriptionForEmergencyCall(){
       Log.d(TAG,"emergency call, getVoiceSubscriptionInService");
       int sub = PhoneApp.getInstance().getVoiceSubscriptionInService();
       return sub;
    }

    private int getCountdownTimer() {
        int timer = 0;
        try {
            timer = Settings.System.getInt(getContentResolver(),
                Settings.System.MULTI_SIM_COUNTDOWN);
        } catch (SettingNotFoundException snfe) {
            Log.d(TAG, Settings.System.MULTI_SIM_COUNTDOWN + " setting does not exist");
        }
        return timer;
    }

    private void setUpDialerScreen(boolean addTimer) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.multi_sim_dialer, (ViewGroup)findViewById(R.id.layout_root));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                Log.d(TAG, "key code is :" + keyCode);
                switch (keyCode) {
                case KeyEvent.KEYCODE_BACK: {
                    dismissChooseDialogAndStartCall(INVALID_SUB);
                    return true;
                }
                case KeyEvent.KEYCODE_CALL: {
                    Log.d(TAG, "event is" + event.getAction());
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        return true;
                    } else {
                        dismissChooseDialogAndStartCall(mDefaultVoice);
                        return true;
                    }
                }
                case KeyEvent.KEYCODE_SEARCH:
                    return true;
                default:
                    return false;
                }
            }
        });

        mChooseDialog = builder.create();

        mCallingNumberText = (TextView)layout.findViewById(R.id.CallingNumber);
        String vm =  mIntent.getExtras().getString("voicemail");
        if ((vm != null) && (vm.equals("voicemail"))) {
            mCallingNumberText.setText(getResources().getString(R.string.calling_number) + "VoiceMail" );
            Log.d(TAG, "its voicemail!!!");
        } else {
            mCallingNumberText.setText(getResources().getString(R.string.calling_number) + mCallingNumber);
        }

        Button btnCancel = (Button)layout.findViewById(R.id.BtnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismissChooseDialogAndStartCall(INVALID_SUB);
            }
        });

        int[] dialerBtnIds = {R.id.BtnSubOne, R.id.BtnSubTwo};
        Button[] dialerBtns = new Button[TelephonyManager.getPhoneCount()];

        for (int i = 0; i < dialerBtnIds.length; i++) {
            final int subscription = i;
            dialerBtns[i] = (Button) layout.findViewById(dialerBtnIds[i]);
            dialerBtns[i].setText(getMultiSimName(i));
            dialerBtns[i].setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                       dismissChooseDialogAndStartCall(subscription);
                }
            });
            if (i == mDefaultVoice) {
                dialerBtns[i].setBackgroundResource(R.drawable.btn_highlight_multi_sim_dial);
            }
        }

        mCountDownTimerText = (TextView)layout.findViewById(R.id.CountDownTimer);
        if (addTimer) {
            mCountDownTimer = new CountDownTimer(mTimer * 1000, 500) {
                public void onTick(long millisUntilFinished) {
                    if(1 == (millisUntilFinished / 500) % 2) {
                       mCountDownTimerText.setText(getResources().getString(R.string.count_down_timer) + (millisUntilFinished / 1000 + 1));
                    }
                }
                public void onFinish() {
                    mChooseDialog.dismiss();
                    startOutgoingCall(mDefaultVoice);
                }
            };
        } else {
            mCountDownTimerText.setVisibility(View.GONE);
            mCountDownTimer = null;
        }
    }

    private String getMultiSimName(int subscription) {
        return Settings.System.getString(mContext.getContentResolver(),
                         Settings.System.MULTI_SIM_NAME[subscription]);
    }

    private void dismissChooseDialogAndStartCall(int subscription) {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }
        if (mChooseDialog != null) {
            mChooseDialog.dismiss();
        }
        startOutgoingCall(subscription);
    }

    private void LaunchMultiSimDialer() {
        setUpDialerScreen(isAddTimer());
        if (isAddTimer()) {
            mCountDownTimer.start();
        }
        mChooseDialog.show();
    }

    private boolean isAddTimer() {
        if (mTimer == -1) {
            return false;
        } else {
            return true;
        }
    }

    boolean isInCall(Phone phone) {
        if (phone != null) {
            if ((phone.getForegroundCall().getState().isAlive()) ||
                   (phone.getBackgroundCall().getState().isAlive()) ||
                   (phone.getRingingCall().getState().isAlive()))
                return true;
        }
        return false;
    }

    /* Return preferred voice subscription or callback subscription if set*/
    private int getVoiceSubscription() {
        int voiceSub = PhoneFactory.getVoiceSubscription();

        if (isCallbackPriorityEnabled()) {
            voiceSub = mIntent.getIntExtra(PHONE_SUBSCRIPTION, voiceSub);
            Log.i(TAG, "Preferred callback enabled");
        }
        return voiceSub;
    }

    private boolean isCallbackPriorityEnabled() {
        int enabled;
        try {
            enabled = Settings.System.getInt(getContentResolver(),
                Settings.System.CALLBACK_PRIORITY_ENABLED);
        } catch (SettingNotFoundException snfe) {
            enabled = 1;
        }
        return (enabled == 1);
    }

    private void startOutgoingCall(int subscription) {
         mIntent.putExtra(PHONE_SUBSCRIPTION, subscription);
         mIntent.setClass(MultiSimDialerActivity.this, OutgoingCallBroadcaster.class);
         if (DBG) Log.v(TAG, "startOutgoingCall for sub " +subscription + " from intent: "+ mIntent);
         if (subscription < mPhoneCount) {
             //setResult(RESULT_OK, mIntent);
             onBack(RESULT_OK, mIntent);
         } else {
             //setResult(RESULT_CANCELED, mIntent);
             onBack(RESULT_CANCELED, mIntent);
             Log.d(TAG, "call cancelled");
         }
         //finish();
    }

    protected abstract void onBack(int resultCode,Intent data);
}
