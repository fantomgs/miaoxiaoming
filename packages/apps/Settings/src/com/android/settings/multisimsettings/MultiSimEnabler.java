/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.app.Activity;
import android.content.res.Resources;

import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Message;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.AsyncResult;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.ProxyManager;
import com.android.internal.telephony.ProxyManager.SubscriptionData;
import com.android.internal.telephony.ProxyManager.Subscription;

import com.android.settings.R;


/**
 * SimEnabler is a helper to manage the slot on/off checkbox
 * preference. It is turns on/off slot and ensures the summary of the
 * preference reflects the current state.
 */
public class MultiSimEnabler extends CheckBoxPreference implements Preference.OnPreferenceChangeListener{
    private final Context mContext;

    private String LOG_TAG = "MultiSimEnabler";
    private final String INTENT_SIM_DISABLED = "com.android.sim.INTENT_SIM_DISABLED";
    private static final boolean DBG = true; //(PhoneApp.DBG_LEVEL >= 2);
    public static final int SUBSCRIPTION_INDEX_INVALID = 99999;

    private static final int EVENT_SIM_STATE_CHANGED = 1;

    private static final int EVENT_SET_SUBSCRIPTION_DONE = 2;

    private final int MAX_SUBSCRIPTIONS = 2;

    private ProxyManager mProxyManager;

    private int mSubscriptionId;
    private String mSummary;
    private boolean mState;

    private boolean mRequest;
    private Subscription mSubscription = ProxyManager.getInstance().new Subscription();

    private Activity mForegroundActivity;

    private AlertDialog mErrorDialog = null;
    private AlertDialog mAlertDialog = null;
    private ProgressDialog mProgressDialog = null;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_SIM_STATE_CHANGED:
                    handleSimStateChanged();
                    break;
                case EVENT_SET_SUBSCRIPTION_DONE:
                    handleSetSubscriptionDone((AsyncResult) msg.obj);
                    // To notify CarrierLabel 
                    if (!MultiSimEnabler.this.isChecked() && mForegroundActivity!=null) {
                        logd("Broadcast INTENT_SIM_DISABLED");
                        Intent intent = new Intent(INTENT_SIM_DISABLED);
                        intent.putExtra("Subscription", mSubscriptionId);
                        mForegroundActivity.sendBroadcast(intent);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void handleSimStateChanged() {
        logd("EVENT_SIM_STATE_CHANGED");
        mSubscription = mProxyManager.new Subscription();
        SubscriptionData[] cardSubsInfo = mProxyManager.getCardSubscriptions();
        for(SubscriptionData cardSub : cardSubsInfo) {
            if (cardSub != null) {
                for (int i = 0; i < cardSub.getLength(); i++) {
                    Subscription sub = cardSub.subscription[i];
                    if (sub.subId == mSubscriptionId) {
                        mSubscription.copyFrom(sub);
                        break;
                    }
                }
            }
        }
        if (mSubscription.subStatus == ProxyManager.SUB_ACTIVATED
            || mSubscription.subStatus == ProxyManager.SUB_DEACTIVATED) {
            setEnabled(true);
        }
    }

    private void handleSetSubscriptionDone(AsyncResult ar) {
        logd("EVENT_SET_SUBSCRIPTION_DONE");

        if (mProgressDialog != null){
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        //set subscription is done, can set check state and summary at here
        updateSummary(mProxyManager.getSubscriptionStatus(mSubscriptionId));

        mSubscription.copyFrom(mProxyManager.getCurrentSubscriptions().subscription[mSubscriptionId]);

        String result[] = (String[]) ar.result;
        if (result != null){
            displayAlertDialog(resultToMsg(result[mSubscriptionId]));
        }

    }

    private String resultToMsg(String result){
        if(result.equals(ProxyManager.SUB_ACTIVATE_SUCCESS)){
            return mContext.getString(R.string.sub_activate_success);
        }
        if (result.equals(ProxyManager.SUB_ACTIVATE_FAILED)){
            return mContext.getString(R.string.sub_activate_failed);
        }
        if (result.equals(ProxyManager.SUB_DEACTIVATE_SUCCESS)){
            return mContext.getString(R.string.sub_deactivate_success);
        }
        if (result.equals(ProxyManager.SUB_DEACTIVATE_FAILED)){
            return mContext.getString(R.string.sub_deactivate_failed);
        }
        if (result.equals(ProxyManager.SUB_DEACTIVATE_NOT_SUPPORTED)){
            return mContext.getString(R.string.sub_deactivate_not_supported);
        }
        if (result.equals(ProxyManager.SUB_ACTIVATE_NOT_SUPPORTED)){
            return mContext.getString(R.string.sub_activate_not_supported);
        }
        if (result.equals(ProxyManager.SUB_NOT_CHANGED)){
            return mContext.getString(R.string.sub_not_changed);
        }
        return mContext.getString(R.string.sub_not_changed);
    }

    public MultiSimEnabler(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mProxyManager = ProxyManager.getInstance();
    }

    public MultiSimEnabler(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public MultiSimEnabler(Context context) {
        this(context, null);
    }

    public void setSubscription(Activity activity, int subscription) {
        mSubscriptionId = subscription;

        String alpha = ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .getSimOperatorName(subscription);
        if (alpha != null && !"".equals(alpha))
            setTitle(alpha);

        mForegroundActivity = activity;
        if (mForegroundActivity == null) logd("error! mForegroundActivity is null!");

        if (mProxyManager.getCardSubscriptions() == null){
            logd("card info is not available.");
            setEnabled(false);
        }else{
            mSubscription.copyFrom(mProxyManager.getCurrentSubscriptions().subscription[mSubscriptionId]);
            if (mSubscription.subStatus == ProxyManager.SUB_ACTIVATED
                || mSubscription.subStatus == ProxyManager.SUB_DEACTIVATED) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        }
    }

    public void resume() {
        setOnPreferenceChangeListener(this);

        updateSummary(mProxyManager.getSubscriptionStatus(mSubscriptionId));

        mProxyManager.registerForSimStateChanged(mHandler, EVENT_SIM_STATE_CHANGED, null);
        mProxyManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    public void pause() {
        setOnPreferenceChangeListener(null);

        //dismiss all dialogs: alert and progress dialogs
        if (mAlertDialog != null) {
            logd("pause: dismiss alert dialog");
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }

        if (mErrorDialog != null) {
            logd("pause: dismiss error dialog");
            mErrorDialog.dismiss();
            mErrorDialog = null;
        }

        if (mProgressDialog != null){
            logd("pause: dismiss progress dialog");
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        mProxyManager.unRegisterForSetSubscriptionCompleted(mHandler);
        mProxyManager.unRegisterForSimStateChanged(mHandler);
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        mRequest = ((Boolean)value).booleanValue();
        displayConfirmDialog();

        // Don't update UI to opposite state until we're sure
        return false;
    }

    private void displayConfirmDialog() {
        if (mForegroundActivity == null){
            logd("can not display alert dialog,no foreground activity");
            return;
        }
        String message = mContext.getString(mRequest?R.string.sim_enabler_need_enable_sim:R.string.sim_enabler_need_disable_sim);
        // Need an activity context to show a dialog
        mAlertDialog = new AlertDialog.Builder(mForegroundActivity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, mDialogClickListener)
                .setNegativeButton(android.R.string.no, mDialogClickListener)
                .show();

    }


    private DialogInterface.OnClickListener mDialogClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                logd("onClick: " + mRequest);

                if (Settings.System.getInt(mContext.getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0) {
                    // do nothing but warning
                    logd("airplane is on, show error!");
                    displayAlertDialog(mContext.getString(R.string.sim_enabler_airplane_on));
                    return;
                }

                for (int i=0; i<TelephonyManager.getPhoneCount(); i++) {
                    if (TelephonyManager.getDefault().getCallState(i) != TelephonyManager.CALL_STATE_IDLE) {
                        // do nothing but warning
                        if (DBG) logd("call state " + i + " is not idle, show error!");
                        displayAlertDialog(mContext.getString(R.string.sim_enabler_in_call));
                        return;
                    }
                }

                if (!mRequest){
                    if (mProxyManager.numSubsActive() > 1){
                        if(DBG) logd("disable, both are active,can do");
                        setEnabled(false);
                        sendCommand(mRequest);
                    }else{
                        if (DBG) logd("only one is active,can not do");
                        displayAlertDialog(mContext.getString(R.string.sim_enabler_both_inactive));
                        return;
                    }
                }else{
                    if (DBG) logd("enable, do it");
                    setEnabled(false);
                    sendCommand(mRequest);
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                if (DBG) logd("onClick Cancel, revert checkbox status");
            }
        }
    };

    private void sendCommand(boolean enabled){
        SubscriptionData currentSubData = mProxyManager.new SubscriptionData(MAX_SUBSCRIPTIONS);
        currentSubData.copyFrom(mProxyManager.getCurrentSubscriptions());
        if (enabled){
            currentSubData.subscription[mSubscriptionId].copyFrom(mProxyManager.getSubsriptioInfoToActivate(mSubscriptionId));
            currentSubData.subscription[mSubscriptionId].subId = mSubscriptionId;
            currentSubData.subscription[mSubscriptionId].subStatus = ProxyManager.SUB_ACTIVATE;
        }else{
            currentSubData.subscription[mSubscriptionId].slotId = SUBSCRIPTION_INDEX_INVALID;
            currentSubData.subscription[mSubscriptionId].m3gppIndex = SUBSCRIPTION_INDEX_INVALID;
            currentSubData.subscription[mSubscriptionId].m3gpp2Index = SUBSCRIPTION_INDEX_INVALID;
            currentSubData.subscription[mSubscriptionId].subId = mSubscriptionId;
            currentSubData.subscription[mSubscriptionId].subStatus = ProxyManager.SUB_DEACTIVATE;
        }
        displayProgressDialog(enabled);
        ProxyManager.getInstance().setSubscription(currentSubData);
    }

    private void displayProgressDialog(boolean enabled){
        String title = Settings.System.getString(mContext.getContentResolver(),Settings.System.MULTI_SIM_NAME[mSubscriptionId]);
        String msg = mContext.getString(enabled?R.string.sim_enabler_enabling:R.string.sim_enabler_disabling);
        mProgressDialog = new ProgressDialog(mForegroundActivity);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(msg);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        
    }

    private void displayAlertDialog(String msg) {
        mErrorDialog = new AlertDialog.Builder(mForegroundActivity)
             .setTitle(android.R.string.dialog_alert_title)
             .setMessage(msg)
             .setCancelable(false)
             .setNeutralButton(R.string.close_dialog, null)
             .show();
    }

    private void updateSummary(int state) {
        Resources res = mContext.getResources();

        if (state == ProxyManager.SUB_ACTIVATED) {
            mState = true;
            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(R.string.sim_enabled));
        } else {
            mState = false;
            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(TelephonyManager.getDefault().hasIccCard(mSubscriptionId) ?
                R.string.sim_disabled :R.string.sim_missing));
        }

        setSummary(mSummary);
        setChecked(mState);
    }

    private void logd(String msg) {
        Log.d(LOG_TAG, "[" + LOG_TAG + "(" + mSubscription + ")] " + msg);
    }


}

