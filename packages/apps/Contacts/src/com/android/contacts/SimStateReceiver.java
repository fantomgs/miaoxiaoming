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

package com.android.contacts;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;


public class SimStateReceiver extends BroadcastReceiver {
    private static boolean DBG = true;
    private static String TAG = "SimStateReceiver";


    private static final int SUB1 = 0;
    private static final int SUB2 = 1;

    private Context mContext ;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        mContext = context;
        if (DBG) log("received broadcast " + action);
        if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
            final int subscription = intent.getIntExtra(IccCard.INTENT_KEY_SUBSCRIPTION, SUB1);
            final String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
            final int simState;
            if (DBG) log("ACTION_SIM_STATE_CHANGED intent received on sub = " + subscription
                + "SIM STATE IS " + stateExtra);
            if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                simState = SimContactsConstants.SIM_STATE_READY;
            } else if (IccCard.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
                simState = SimContactsConstants.SIM_STATE_READY;
            } else if (IccCard.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
                simState = SimContactsConstants.SIM_STATE_READY;
            } else {
                simState = SimContactsConstants.SIM_STATE_NOT_READY;
            }
            if (isMultiSimEnabled()) {
                sendSimState(subscription, simState);
            } else {
                sendSimState(simState);
            }


        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            sendPhoneBoot();
        }
    }

    private void sendPhoneBoot() {
        Bundle args = new Bundle();
        args.putInt(SimContactsService.OPERATION,SimContactsService.OP_PHONE);
        mContext.startService(new Intent(mContext, SimContactsService.class)
                .putExtras(args));
    }

    private void sendSimState(int subscription, int state) {
            Bundle args = new Bundle();
            args.putInt(SimContactsConstants.SUB,subscription);
            args.putInt(SimContactsService.OPERATION,SimContactsService.OP_SIM);
            args.putInt(SimContactsService.SIM_STATE,state);
            mContext.startService(new Intent(mContext, SimContactsService.class)
                    .putExtras(args));
    }

    private void sendSimState(int state) {
            Bundle args = new Bundle();
            args.putInt(SimContactsService.OPERATION,SimContactsService.OP_SIM);
            args.putInt(SimContactsService.SIM_STATE,state);
            mContext.startService(new Intent(mContext, SimContactsService.class)
                    .putExtras(args));
    }

    private boolean  isMultiSimEnabled() {
        return TelephonyManager.isMultiSimEnabled();
    }

    protected void log(String msg) {
        Log.d(TAG, msg);
    }


}

