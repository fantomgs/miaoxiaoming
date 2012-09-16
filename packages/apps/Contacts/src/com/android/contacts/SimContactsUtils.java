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

import android.content.AsyncQueryHandler;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.TelephonyManager;

import com.android.contacts.model.SimSource;
import com.android.internal.telephony.UiccManager;
import com.android.internal.telephony.UiccConstants;

public class SimContactsUtils {

    private static final String TAG = "SimContactsUtils";
    private static final boolean DBG = true;
    private static Context mContext;
    private static UiccManager mUiccManager;
    private SimContactsUtils() {
    }


    public static int getSubscription(String accountType, String accountName){
        int subscription = -1;
        if (accountType == null || accountName == null)
            return subscription;
        if (accountType.equals(SimSource.ACCOUNT_TYPE)) {
            if (accountName.equals(SimContactsConstants.SIM_NAME))
                subscription = 0;
            else if(accountName.equals(SimContactsConstants.SIM_NAME_1))
                subscription = 0;
            else if (accountName.equals(SimContactsConstants.SIM_NAME_2))
                subscription = 1;
        }
        return subscription;
    }

    private static boolean  isMultiSimEnabled() {
        return TelephonyManager.isMultiSimEnabled();
    }

    private static void log(String msg) {
        if (DBG) Log.d(TAG,  msg);
    }
}

