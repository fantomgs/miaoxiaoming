/*
  * Copyright (C) 2011-2012, Code Aurora Forum. All rights reserved.
  * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.model;

import com.android.contacts.R;
import com.google.android.collect.Lists;
import com.android.contacts.SimContactsUtils;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.view.inputmethod.EditorInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimSource extends FallbackSource{
    public static final String ACCOUNT_TYPE = "com.android.sim";
    public static final String RES_PACKAGE_NAME = "com.android.contacts";
    public static final int FLAGS_PERSON_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS | EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME;
    public static final int FLAGS_PHONETIC = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_PHONETIC;
    protected static final int FLAGS_PHONE = EditorInfo.TYPE_CLASS_PHONE;

    String cardType;
    public SimSource() {
        this.accountType = ACCOUNT_TYPE;
        this.resPackageName = null;
        this.summaryResPackageName = RES_PACKAGE_NAME;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.drawable.ic_launcher_contacts;
    }

    @Override
    protected void inflate(Context context, int inflateLevel) {

        inflateStructuredName(context, inflateLevel);
        inflatePhone(context, inflateLevel);
        inflateEmail(context, inflateLevel);
        setInflatedLevel(inflateLevel);
    }
    protected DataKind inflateStructuredName(Context context, int inflateLevel) {
        DataKind kind = getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE);
        if (kind == null) {
            kind = addKind(new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                    R.string.nameLabelsGroup, -1, -1, true));
        }

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(StructuredName.DISPLAY_NAME, R.string.name_for_sim,
                    FLAGS_PERSON_NAME));
        }

        return kind;
    }
    protected DataKind inflatePhone(Context context, int inflateLevel) {
        DataKind kind = getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        if (kind == null) {
            //kind = addKind(new DataKind(Phone.CONTENT_ITEM_TYPE, R.string.phoneLabelsGroup,
            //        android.R.drawable.sym_action_call, 10, true));
            kind = addKind(new DataKind(Phone.CONTENT_ITEM_TYPE,
                    R.string.phoneLabelsGroup, android.R.drawable.sym_action_call, 110, true));
            kind.typeOverallMax = 2;
            kind.iconAltRes = R.drawable.sym_action_sms;    // sms icon behind the phone call icon
            kind.actionHeader = new PhoneActionInflater();
            kind.actionAltHeader = new PhoneActionAltInflater();
            kind.actionBody = new SimpleInflater(Phone.NUMBER);
            kind.isList = true;
        }

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeColumn = Phone.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE));
            kind.typeList.add(buildPhoneType(Phone.TYPE_HOME));// This is used to save ANR records
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));
        }
        return kind;
    }

    protected DataKind inflateEmail(Context context, int inflateLevel) {
        DataKind kind = getKindForMimetype(Email.CONTENT_ITEM_TYPE);
        if (kind == null) {
            kind = addKind(new DataKind(Email.CONTENT_ITEM_TYPE,
                    R.string.emailLabelsGroup, android.R.drawable.sym_action_email, 15, true));
            kind.actionHeader = new EmailActionInflater();
            kind.actionBody = new SimpleInflater(Email.ADDRESS);
            kind.isList = false;
        }

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeColumn = Email.TYPE;
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Email.ADDRESS, R.string.emailLabelsGroup, FLAGS_EMAIL));
        }

        return kind;
    }

    protected EditType buildPhoneType(int type) {
        return new EditType(type, Phone.getTypeLabelResource(type));
    }

    @Override
    public int getHeaderColor(Context context) {
        return 0xffd5ba96;
    }

    @Override
    public int getSideBarColor(Context context) {
        return 0xffb58e59;
    }
}
