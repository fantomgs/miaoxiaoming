/*
 * Copyright (C) 2011-2012, Code Aurora Forum. All rights reserved.

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
// HEXING123 MODIFY
package com.android.contacts;

import android.app.Service;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Settings;
import android.text.TextUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.contacts.util.WeakAsyncTask;
import com.android.internal.telephony.TelephonyIntents;
import java.util.ArrayList;
import android.os.SystemProperties;		//Add By ydc 2012_07_02

public class SimContactsService extends Service {
    private static final String TAG = "SimContactsService";
    private static boolean DBG = true;

    private static final String[] COLUMN_NAMES = new String[] {
        "name",
        "number",
        "emails",
        "anrs"
    };

    protected static final int NAME_COLUMN = 0;
    protected static final int NUMBER_COLUMN = 1;
    protected static final int EMAILS_COLUMN = 2;
    protected static final int ANRS_COLUMN = 3;


    static final String[] CONTACTS_ID_PROJECTION = new String[] {
        RawContacts._ID,
        RawContacts.CONTACT_ID,
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE,
    };

    private static final int RAW_CONTACT_ID_COLUMN = 0;
    private static final int CONTACT_ID_COLUMN_COLUMN = 1;

    static final String SIM_DATABASE_SELECTION= RawContacts.ACCOUNT_TYPE + "=?" + " AND " +
                                       RawContacts.ACCOUNT_NAME + "=?" +
                                       " AND " + RawContacts.DELETED + "=?" ;

    static final String [] SIM_DATABASE_SELECTARGS = {SimContactsConstants.ACCOUNT_TYPE_SIM,
                                    SimContactsConstants.SIM_NAME,"0"};

    static final String [] SIM_DATABASE_SELECTARGS_SUB1 = {SimContactsConstants.ACCOUNT_TYPE_SIM,
                              SimContactsConstants.SIM_NAME_1,"0"};

    static final String [] SIM_DATABASE_SELECTARGS_SUB2 = {SimContactsConstants.ACCOUNT_TYPE_SIM,
                              SimContactsConstants.SIM_NAME_2,"0"};

    public static final String SUBSCRIPTION = "sub_id";
    public static final String OPERATION = "operation";
    public static final String SIM_STATE = "sim_state";

    public static final int OP_PHONE = 1;
    public static final int OP_SIM = 2;

    protected static final int SUB1 = 0;
    protected static final int SUB2 = 1;
    private static final int DEFAULT_SUB = 0;


    protected static final int QUERY_TOKEN = 0;
    protected static final int INSERT_TOKEN = 1;
    protected static final int UPDATE_TOKEN = 2;
    protected static final int DELETE_TOKEN = 3;

    protected static final int QUERY_TOKEN_DATABASE = 10;
    protected static final int QUERY_TOKEN_DATABASE_SUB1= 11;
    protected static final int QUERY_TOKEN_DATABASE_SUB2 = 12;
    protected static final int INSERT_TOKEN_DATABASE = 20;
    protected static final int UPDATE_TOKEN_DATABASE = 30;
    protected static final int DELETE_TOKEN_DATABASE = 40;

    private static final String ICCID[]={"iccid_sub1", "iccid_sub2"};

    static final ContentValues sEmptyContentValues = new ContentValues();
    private Context mContext ;

    private static int mPhoneNumber=0;
    protected Cursor [] mSimCursor;
    protected Cursor [] mDatabaseCursor;
    private boolean [] isNewCard;
    private AccountManager accountManager;
    private TelephonyManager mTelephonyManager;
    private SharedPreferences mPrefs;
    private volatile Handler mServiceHandler;

    private int[] mSimState;

    @Override
    public void onCreate() {
        Log.d(TAG, "service onCreate!");
        mPhoneNumber = getPhoneCount();
        mContext = getApplicationContext();
        //mUiccManager=UiccManager.getInstance();
        accountManager = AccountManager.get(mContext);
        mTelephonyManager = (TelephonyManager) getSystemService(Service.TELEPHONY_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSimCursor = new Cursor[mPhoneNumber];
        mDatabaseCursor = new Cursor[mPhoneNumber];
        isNewCard = new boolean[mPhoneNumber];
        mSimState = new int[mPhoneNumber];
        mServiceHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle args = (Bundle)msg.obj;
            switch(msg.what) {
                case OP_PHONE:
                    createPhoneAccount();
                    for (int i = 0; i<getPhoneCount();i++) {
                        if (!hasIccCard(i)) {
                            handleNoSim(i);
                        } else {
                            createSimAccountIfNotExist(i);
                        }
                    }
                    break;
                case OP_SIM:
                    final int state = args.getInt(SimContactsService.SIM_STATE);
                    if (isMultiSimEnabled()) {
                        int subscription = args.getInt(SimContactsConstants.SUB,-1);

                        if (state != mSimState[subscription]) {
                            log(" new sim state is " + (state==1 ?"Ready" :"Not Ready") + " at sub: "
                            + subscription + ", original sim state is "
                            + (mSimState[subscription]==1 ?"Ready" :"Not Ready"));

                            mSimState[subscription] = state;
                            if (state == SimContactsConstants.SIM_STATE_READY) {
                                handleSimOp(subscription);
                            }
                        }

                    } else {
                        if (state != mSimState[DEFAULT_SUB]) {
                            log(" new sim state is " + (state==1 ?"Ready" :"Not Ready")
                                + ", original sim state is "
                                + (mSimState[DEFAULT_SUB]==1 ?"Ready" :"Not Ready"));
                            mSimState[DEFAULT_SUB] = state;
                            if (state == SimContactsConstants.SIM_STATE_READY) {
                                handleSimOp();
                            }
                        }
                    }
                    break;
                }
            }
        };

        for (int i = 0; i < mPhoneNumber; i++) {
            isNewCard[i] = true;
            mSimState[i] = SimContactsConstants.SIM_STATE_NOT_READY;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bind. Action: " + intent.getAction());
            return null;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "service onStart!"  );
        if (intent == null ){
            Log.d(TAG, "service onStart! intent is null"  );
            return;
        }
        Bundle args = intent.getExtras();

        if (args == null) {
            Log.d(TAG, "service onStart! args is null"  );
            return;
        }
        Message msg = mServiceHandler.obtainMessage();

        msg.what = args.getInt(SimContactsService.OPERATION,-1);
        msg.obj = args;
        mServiceHandler.sendMessage(msg);


    }

    private void handleSimOp(){
        log("handleSimOp()");
        compareAndSaveIccid(DEFAULT_SUB);
        if (isNewCardInserted(DEFAULT_SUB)) {
            deleteDatabaseSimContacts();
            createSimAccountIfNotExist(DEFAULT_SUB);
            querySimContacts();
        } else {
            deleteDatabaseSimContacts();
            createSimAccountIfNotExist(DEFAULT_SUB);
            querySimContacts();
        }
    }

    private void handleSimOp(int subscription){
        log("handleSimOp() at sub " + subscription);
        compareAndSaveIccid(subscription);
        if (isNewCardInserted(subscription)) {
            log("This is a new card at sub: " + subscription);
            deleteDatabaseSimContacts(subscription);
            createSimAccountIfNotExist(subscription);
            querySimContacts(subscription);

        } else {
            deleteDatabaseSimContacts(subscription);
            createSimAccountIfNotExist(subscription);
            querySimContacts(subscription);
        }
    }

    private void handleNoSim(int subscription) {
        log("handle no sim on sub : " + subscription);
        if (isMultiSimEnabled()) {
            deleteDatabaseSimContacts(subscription);
        } else {
            deleteDatabaseSimContacts();
        }
        deleteSimAccount(subscription);
    }

    private int getPhoneCount() {
        return TelephonyManager.getPhoneCount();
    }

    private static boolean  isMultiSimEnabled() {
        return TelephonyManager.isMultiSimEnabled();
    }

    private boolean hasIccCard(int subscription) {
        return TelephonyManager.getDefault().hasIccCard(subscription);
    }

    private boolean hasIccCard() {
        return TelephonyManager.getDefault().hasIccCard();
    }

    private void compareAndSaveIccid(int subscription) {
        String oldIccid = mPrefs.getString(ICCID[subscription],"");
        String newIccid = mTelephonyManager.getSimSerialNumber(subscription);
        if (!oldIccid.equals(newIccid)) {
            Editor editor = mPrefs.edit();
            editor.putString(ICCID[subscription], newIccid);
            editor.apply();
            isNewCard[subscription] = true;
        } else {
            isNewCard[subscription] = false;
        }

    }

    private boolean  isNewCardInserted(int subscription) {
        return isNewCard[subscription];
    }


    private String getStringFrom(String str) {
        if ((str == null) || (str != null && str.equals(" "))) {
            return null;
        }
        return str;
    }

    private void createPhoneAccount() {
        final String type = SimContactsConstants.ACCOUNT_TYPE_PHONE;
        final String name = SimContactsConstants.PHONE_NAME;
        if ( !hasLocalAccount(name,type)) {
            Bundle args = new Bundle();
            args.putString(SimContactsConstants.ACCOUNT_TYPE,type);
            mContext.startService(
                new Intent(mContext, PhoneAuthenticateService.class).putExtras(args));

            new UpdateAccountTask(this).execute(
                new Account(name, type));
            log("createPhoneAccount");
         }
    }
    private void createSimAccount(int subscription) {
        startAuthenticatorService(subscription,
                                      SimContactsConstants.ACCOUNT_TYPE_SIM);
        final String type = SimContactsConstants.ACCOUNT_TYPE_SIM;
        String name = null;
        if (!isMultiSimEnabled()) {
            name = SimContactsConstants.SIM_NAME;
        } else {
            if (subscription == 0)
                name = SimContactsConstants.SIM_NAME_1;
            else if (subscription == 1)
                name = SimContactsConstants.SIM_NAME_2;
        }
        new UpdateAccountTask(this).execute(
                new Account(name,type));
    }

    private void createSimAccountIfNotExist(int subscription) {
        if (isMultiSimEnabled()) {
            if (subscription== SUB1 &&
                !hasLocalAccount(SimContactsConstants.SIM_NAME_1,
                                   SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                createSimAccount(subscription);
                log("createSimAccount for sub 0");
            }
            if (subscription== SUB2 &&
                !hasLocalAccount(SimContactsConstants.SIM_NAME_2,
                                   SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                createSimAccount(subscription);
                log("createSimAccount for sub 1");
            }
        } else {
            if (!hasLocalAccount(SimContactsConstants.SIM_NAME,
                                SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                createSimAccount(-1);
                log("createSimAccount");
            }
        }
    }

    private void deleteSimAccount(int subscription) {
        String accountName = null;
        if (isMultiSimEnabled()) {
            if(subscription == SUB1)
                accountName = SimContactsConstants.SIM_NAME_1;
            else if (subscription == SUB2)
                accountName = SimContactsConstants.SIM_NAME_2;
        } else {
            accountName = SimContactsConstants.SIM_NAME;
        }
        Account accounts[] = getSimAccounts();
        for (Account account: accounts){
            if (account.name.equals(accountName)){
                accountManager.removeAccount(account,null,null);
                log("deleteSimAccount account is " +account );
            }
        }
    }


        /**
     * Background task that persists changes to {@link Groups#GROUP_VISIBLE},
     */
    private static class UpdateAccountTask extends
            WeakAsyncTask<Account, Void, Void, Service> {

        public UpdateAccountTask(Service target) {
            super(target);
            log("UpdateAccountTask");
        }

        /** {@inheritDoc} */
        @Override
        protected Void doInBackground(Service target, Account... params) {
            final Context context = target;
            final ContentValues values = new ContentValues();
            final ContentResolver resolver = context.getContentResolver();
            final Account account = params[0];
            log("UpdateAccountTask doInBackground" );
            updateAccountVisible(resolver, account);
            return null;
        }

    }

    private static void updateAccountVisible(ContentResolver resolver, Account account) {
        final Uri settingsUri = Settings.CONTENT_URI.buildUpon()
                .appendQueryParameter(Settings.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Settings.ACCOUNT_TYPE, account.type).build();
        final Cursor cursor = resolver.query(settingsUri, new String[] {
                Settings.SHOULD_SYNC, Settings.UNGROUPED_VISIBLE
        }, null, null, null);
        log("updateAccountVisible account is " + account);
        if (cursor != null) log("cursor.getCount() = " + cursor.getCount());
            // only do this when create
        if (cursor == null || cursor.getCount() == 0) {
            final ContentValues values = new ContentValues();
            values.put(Settings.ACCOUNT_NAME, account.name);
            values.put(Settings.ACCOUNT_TYPE, account.type);
            values.put(Settings.SHOULD_SYNC, 1);
            values.put(Settings.UNGROUPED_VISIBLE, 1);
            final ArrayList<ContentProviderOperation> operationList =
                    new ArrayList<ContentProviderOperation>();
            final Builder builder = ContentProviderOperation.newInsert(Settings.CONTENT_URI);
            builder.withValues(values);
            operationList.add(builder.build());
            try {
                resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                log("update account name : " + account.name +" to visible" );
            } catch (RemoteException e) {
                Log.e(TAG,String.format("%s: %s", e.toString(), e.getMessage()));
            } catch (OperationApplicationException e) {
                Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            }
        } else if (cursor != null) {
            cursor.close();
        }

    }

    protected  Account[] getSimAccounts() {
        return accountManager
                .getAccountsByType(SimContactsConstants.ACCOUNT_TYPE_SIM);
    }

    private  Account getSimAccount(int subscription) {
        Account[] accounts = getSimAccounts();
            if (isMultiSimEnabled()){
                for (Account account: accounts) {
                    if (subscription == SUB1 &&
                        SimContactsConstants.SIM_NAME_1.equals(account.name)) {
                        return account;
                    } else if (subscription == SUB2 &&
                        SimContactsConstants.SIM_NAME_2.equals(account.name)) {
                        return account;
                    }
                }
            } else {
                for (Account account: accounts) {
                    if(SimContactsConstants.SIM_NAME.equals(account.name)){
                        return account;
                    }
                }
            }
            log("cannot get sim account of sub " + subscription);
            return null;
    }

    protected  Account[] getPhoneAccounts() {
        return accountManager
                .getAccountsByType(SimContactsConstants.ACCOUNT_TYPE_PHONE);
    }

    private void startAuthenticatorService(int subscription, String accoutType) {
        Bundle args = new Bundle();
        args.putInt(SimContactsConstants.SUB,subscription);
        args.putString(SimContactsConstants.ACCOUNT_TYPE,accoutType);
        mContext.startService(new Intent(mContext, AuthenticateService.class).putExtras(args));
    }

    private boolean hasLocalAccount(String accoutName, String accountType ) {
        if (SimContactsConstants.ACCOUNT_TYPE_PHONE.equals(accountType)) {
            log("account type is " + SimContactsConstants.ACCOUNT_TYPE_PHONE);
            Account accounts[] = getPhoneAccounts();
            for (Account account: accounts){
                if(account.name.equals(accoutName)){
                    log("hasLocalAccount() account is " +account );
                    return true;
                }
            }
            return false;
        } else if (SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accountType)) {
            Account accounts[] = getSimAccounts();
            for (Account account: accounts){
                if(account.name.equals(accoutName)){
                    log("hasLocalAccount() account is " +account );
                    return true;
                }
            }
            return false;
        }
        return false;

    }

     private void querySimContacts() {
        querySimContacts(SUB1);
    }

    private void querySimContacts(int subscription) {
        final QuerySimTask task = new QuerySimTask(this,subscription);
        task.execute();
    }

    private void queryDatabaseSimContactsID() {
        queryDatabaseSimContactsID(DEFAULT_SUB);
    }

    private void queryDatabaseSimContactsID(int subscription) {
        new QueryDatabaseTask(this, subscription).execute();
    }


    private void deleteDatabaseSimContacts() {
        deleteDatabaseSimContacts(DEFAULT_SUB);
    }

    private void deleteDatabaseSimContacts(int subscription) {
        new DeleteDatabaseTask(this, subscription).execute();
    }

    private void addAllSimContactsIntoDatabase(int subscription) {
        ImportAllSimContactsThread thread = new ImportAllSimContactsThread(subscription);
        thread.start();

    }

    private void addAllSimContactsIntoDatabase() {
        ImportAllSimContactsThread thread = new ImportAllSimContactsThread();
        thread.start();

    }

    private void UpdateSimDatabaseInPhone(int subscription) {
        UpdateContactsThread thread =
            new UpdateContactsThread(subscription);
        thread.start();
    }

    protected static void log(String msg) {
        if (DBG)  Log.d(TAG, msg);
    }

    private class ImportAllSimContactsThread extends Thread {
        private int mSubscription = 0;

        private Account mAccount;
        public ImportAllSimContactsThread(int subscription) {
            super("ImportAllSimContactsThread");
            mSubscription = subscription;
            mAccount = getSimAccount(mSubscription);
        }

        public ImportAllSimContactsThread() {
            super("ImportAllSimContactsThread");
            mAccount = getSimAccount(mSubscription);
        }

        @Override
        public void run() {
            final ContentValues emptyContentValues = new ContentValues();
            final ContentResolver resolver = mContext.getContentResolver();
			if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
			{
        		String accountName = "SIM";
        		String accountType = SimContactsConstants.ACCOUNT_TYPE_SIM;
        		mAccount = new Account(accountName,accountType);
        	}
            log("import sim contact to account: " + mAccount);
            mSimCursor[mSubscription].moveToPosition(-1);
            while (mSimCursor[mSubscription].moveToNext()) {
                actuallyImportOneSimContact(mSimCursor[mSubscription], resolver, mAccount);
            }
            mSimCursor[mSubscription].close();
        }

    }

    private static void actuallyImportOneSimContact(
            final Cursor cursor, final ContentResolver resolver, Account account) {

        final String name = cursor.getString(NAME_COLUMN);
        final String phoneNumber = cursor.getString(NUMBER_COLUMN);
        final String emailAddresses = cursor.getString(EMAILS_COLUMN);
        final String anrs = cursor.getString(ANRS_COLUMN);
        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(",");
        } else {
            anrArray = null;
        }
        log(" actuallyImportOneSimContact: name= " + name +
            ", phoneNumber= " + phoneNumber +", emails= "+ emailAddresses
            +", anrs= "+ anrs + ", account is " + account);

        final ArrayList<ContentProviderOperation> operationList =
            new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, phoneNumber);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (anrArray != null) {
            for (String anr :anrArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                builder.withValue(Phone.NUMBER, anr);
                //builder.withValue(Data.IS_PRIMARY, 1);
                operationList.add(builder.build());
            }
        }

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.ADDRESS, emailAddress);
                operationList.add(builder.build());
            }
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG,String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private class UpdateContactsThread extends Thread {
        private int mSubscription = 0;
        private Account mAccount;
        private SimContactsOperation mSimContactsOperation;
        public UpdateContactsThread(int subscription) {
            super("LoadSimContactsInPhone");
            mSubscription = subscription;
            mAccount = getSimAccount(mSubscription);
            mSimContactsOperation = new SimContactsOperation(mContext);
        }

        @Override
        public void run() {
            final ContentResolver resolver = mContext.getContentResolver();
            ContentValues mAfter = new ContentValues();
            mDatabaseCursor[mSubscription].moveToPosition(-1);
            mSimCursor[mSubscription].moveToPosition(-1);
            while ((!mSimCursor[mSubscription].isLast()) && (!mDatabaseCursor[mSubscription].isLast())
                    && mSimCursor[mSubscription].moveToNext() && mDatabaseCursor[mSubscription].moveToNext()) {
                final long contactId = mDatabaseCursor[mSubscription].getLong(CONTACT_ID_COLUMN_COLUMN);
                final long rawContactId = mDatabaseCursor[mSubscription].getLong(RAW_CONTACT_ID_COLUMN);
                ContentValues mBefore = mSimContactsOperation.getSimAccountValues(contactId);
                final String simName = mSimCursor[mSubscription].getString(NAME_COLUMN);
                final String simNumber = mSimCursor[mSubscription].getString(NUMBER_COLUMN);
                final String simEmails = mSimCursor[mSubscription].getString(EMAILS_COLUMN);
                final String simAnrs = mSimCursor[mSubscription].getString(ANRS_COLUMN);
                final String[] simEmailArray = simEmails == null? null: simEmails.split(",");
                final String[] simAnrArray = simAnrs == null? null: simAnrs.split(",");
                mAfter.clear();
                mAfter.put(SimContactsConstants.STR_TAG,simName);
                mAfter.put(SimContactsConstants.STR_NUMBER,simNumber);
                if (SimContactsConstants.USIM.equals(mSimContactsOperation.getCardType(mSubscription))) {
                    if (simAnrArray != null && simAnrArray.length > 0) {
                        mAfter.put(SimContactsConstants.STR_ANRS,simAnrArray[0]);
                    } else {
                        mAfter.putNull(SimContactsConstants.STR_ANRS);
                    }
                    if (simEmailArray != null && simEmailArray.length > 0) {
                        mAfter.put(SimContactsConstants.STR_EMAILS,simEmailArray[0]);
                    } else {
                        mAfter.putNull(SimContactsConstants.STR_EMAILS);
                    }
                }
                log(" UpdateContactsThread mAfter is : " + mAfter + " mBefore is: " +mBefore
                     + " rawContactId is: " + rawContactId );

                if (!mBefore.equals(mAfter)) {

                    actuallyUpdateOneSimContact(resolver,mBefore,mAfter,rawContactId);
                }
            }
            while(mDatabaseCursor[mSubscription].moveToNext()) {
                deleteOneSimContactFromDatabase(
                    mDatabaseCursor[mSubscription],resolver,mSubscription);
            }
            if (mDatabaseCursor[mSubscription].getCount() == 0) {
                mSimCursor[mSubscription].moveToPosition(-1);
            }
            while (mSimCursor[mSubscription].moveToNext()) {
                actuallyImportOneSimContact(
                    mSimCursor[mSubscription],resolver,mAccount);
            }

            mSimCursor[mSubscription].close();
            mDatabaseCursor[mSubscription].close();
        }
    }


    private void actuallyUpdateOneSimContact(
        final ContentResolver resolver,final ContentValues before, final ContentValues after, long rawContactId) {

        final ArrayList<ContentProviderOperation> operationList =
                    new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder;
        builder = buildDiff(before, after, SimContactsConstants.STR_TAG, rawContactId);
        if (builder != null ) {
            operationList.add(builder.build());
        }
        builder = buildDiff(before, after, SimContactsConstants.STR_NUMBER, rawContactId);
        if (builder != null ) {
            operationList.add(builder.build());
        }
        builder = buildDiff(before, after, SimContactsConstants.STR_ANRS, rawContactId);
        if (builder != null ) {
            operationList.add(builder.build());
        }
        builder = buildDiff(before, after, SimContactsConstants.STR_EMAILS, rawContactId);
        if (builder != null ) {
            operationList.add(builder.build());
        }

        log(" actuallyUpdateOneSimContact : update new values " + after.toString());
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG,String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private boolean isDeleted(final ContentValues before, final ContentValues after, String key) {
        return !TextUtils.isEmpty(before.getAsString(key))&& TextUtils.isEmpty(after.getAsString(key));
    }

    private boolean isInserted(final ContentValues before, final ContentValues after, String key) {
        return TextUtils.isEmpty(before.getAsString(key))&& !TextUtils.isEmpty(after.getAsString(key));
    }

    private boolean isUpdated(final ContentValues before, final ContentValues after, String key) {
        return before.getAsString(key) != null && after.getAsString(key) != null &&
            !before.getAsString(key).equals(after.getAsString(key));
    }

    private Builder buildUpdatedName(final ContentValues after, final long rawContactId) {
        Builder builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
        String nameSelection = StructuredName.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=?";
        String [] nameSelectionArg =
                new String [] {String.valueOf(rawContactId), StructuredName.CONTENT_ITEM_TYPE};
        builder.withSelection(nameSelection, nameSelectionArg);
        builder.withValue(StructuredName.GIVEN_NAME, null);
        builder.withValue(StructuredName.FAMILY_NAME, null);
        builder.withValue(StructuredName.PREFIX, null);
        builder.withValue(StructuredName.MIDDLE_NAME, null);
        builder.withValue(StructuredName.SUFFIX, null);
        builder.withValue(StructuredName.DISPLAY_NAME, after.getAsString(SimContactsConstants.STR_TAG));
        return builder;
    }

    private Builder buildDeletedName(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
        String nameSelection = StructuredName.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=?";
        String [] nameSelectionArg =
                new String [] {String.valueOf(rawContactId), StructuredName.CONTENT_ITEM_TYPE};
        builder.withSelection(nameSelection, nameSelectionArg);
        return builder;
    }

    private Builder buildInsertedName(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        String nameSelection = StructuredName.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=?";
        builder.withValue(StructuredName.RAW_CONTACT_ID, rawContactId);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, after.getAsString(SimContactsConstants.STR_TAG));
        return builder;
    }

    private Builder buildUpdatedNumber(final ContentValues after, final long rawContactId) {
        Builder builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
        String selection = Phone.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=? AND "
                               +Phone.TYPE + "=?";
        String [] selectionArg =
                new String [] {String.valueOf(rawContactId), Phone.CONTENT_ITEM_TYPE,
                String.valueOf(Phone.TYPE_MOBILE)};
        builder.withSelection(selection, selectionArg);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, after.getAsString(SimContactsConstants.STR_NUMBER));
        return builder;
    }

    private Builder buildDeletedNumber(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
        String selection = Phone.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=? AND "
                               +Phone.TYPE + "=?";
        String [] selectionArg =
                new String [] {String.valueOf(rawContactId), Phone.CONTENT_ITEM_TYPE,
                String.valueOf(Phone.TYPE_MOBILE)};
        builder.withSelection(selection, selectionArg);
        return builder;
    }

    private Builder buildInsertedNumber(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValue(Phone.RAW_CONTACT_ID, rawContactId);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Data.IS_PRIMARY, 1);
        builder.withValue(Phone.NUMBER, after.getAsString(SimContactsConstants.STR_NUMBER));
        return builder;
    }

    private Builder buildUpdatedAnr(final ContentValues after, final long rawContactId) {
        Builder builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
        String selection = Phone.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=? AND "
                               +Phone.TYPE + "=?";
        String [] selectionArg =
                new String [] {String.valueOf(rawContactId), Phone.CONTENT_ITEM_TYPE, String.valueOf(Phone.TYPE_HOME)};
        builder.withSelection(selection, selectionArg);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, after.getAsString(SimContactsConstants.STR_ANRS));
        return builder;
    }

    private Builder buildDeletedAnr(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
        String selection = Phone.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=? AND "
                               +Phone.TYPE + "=?";
        String [] selectionArg =
                new String [] {String.valueOf(rawContactId), Phone.CONTENT_ITEM_TYPE,String.valueOf(Phone.TYPE_HOME)};
        builder.withSelection(selection, selectionArg);
        return builder;
    }

    private Builder buildInsertedAnr(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValue(Phone.RAW_CONTACT_ID, rawContactId);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
        builder.withValue(Phone.NUMBER, after.getAsString(SimContactsConstants.STR_ANRS));
        return builder;
    }

    private Builder buildUpdatedEmail(final ContentValues after, final long rawContactId) {
        Builder builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
        String selection = Email.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=?";
        String [] selectionArg =
                new String [] {String.valueOf(rawContactId), Email.CONTENT_ITEM_TYPE};
        builder.withSelection(selection, selectionArg);
        builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
        builder.withValue(Email.ADDRESS, after.getAsString(SimContactsConstants.STR_EMAILS));
        return builder;
    }

    private Builder buildDeletedEmail(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
        String selection = Email.RAW_CONTACT_ID + "=? AND " +Data.MIMETYPE + "=?";
        String [] selectionArg =
                new String [] {String.valueOf(rawContactId), Email.CONTENT_ITEM_TYPE};
        builder.withSelection(selection, selectionArg);
        return builder;
    }

    private Builder buildInsertedEmail(final ContentValues after,final long rawContactId) {
        Builder builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValue(Email.RAW_CONTACT_ID, rawContactId);
        builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
        builder.withValue(Email.ADDRESS, after.getAsString(SimContactsConstants.STR_EMAILS));
        return builder;
    }

    public ContentProviderOperation.Builder buildDiff(ContentValues before,
        ContentValues after, String key, long rawContactId) {
            Builder builder = null;
            if (isInserted(before, after, key)) {
                if (SimContactsConstants.STR_TAG.equals(key)) {
                    builder=buildInsertedName(after,rawContactId);
                } else if (SimContactsConstants.STR_NUMBER.equals(key)) {
                    builder=buildInsertedNumber(after,rawContactId);
                } else if (SimContactsConstants.STR_ANRS.equals(key)) {
                    builder=buildInsertedAnr(after,rawContactId);
                } else if (SimContactsConstants.STR_EMAILS.equals(key)) {
                    builder=buildInsertedEmail(after,rawContactId);
                }
            } else if (isDeleted(before, after, key)) {
               if (SimContactsConstants.STR_TAG.equals(key)) {
                    builder=buildDeletedName(after,rawContactId);
                } else if (SimContactsConstants.STR_NUMBER.equals(key)) {
                    builder=buildDeletedNumber(after,rawContactId);
                } else if (SimContactsConstants.STR_ANRS.equals(key)) {
                    builder=buildDeletedAnr(after,rawContactId);
                } else if (SimContactsConstants.STR_EMAILS.equals(key)) {
                    builder=buildDeletedEmail(after,rawContactId);
                }
            } else if (isUpdated(before, after, key)) {
               if (SimContactsConstants.STR_TAG.equals(key)) {
                    builder=buildUpdatedName(after,rawContactId);
                } else if (SimContactsConstants.STR_NUMBER.equals(key)) {
                    builder=buildUpdatedNumber(after,rawContactId);
                } else if (SimContactsConstants.STR_ANRS.equals(key)) {
                    builder=buildUpdatedAnr(after,rawContactId);
                } else if (SimContactsConstants.STR_EMAILS.equals(key)) {
                    builder=buildUpdatedEmail(after,rawContactId);
                }
            }
            return builder;
        }

    private void deleteOneSimContactFromDatabase(
        final Cursor cursor, final ContentResolver resolver,int subscription) {

        String id;
        id = String.valueOf(mDatabaseCursor[subscription].getLong(CONTACT_ID_COLUMN_COLUMN));
        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
        log("delete uri is " + uri);
        resolver.delete(uri, null, null);
    }



    public static class QuerySimTask extends
                WeakAsyncTask<Void, Void, Cursor, SimContactsService> {

            private int mSubscription;

            public QuerySimTask(SimContactsService target, int subscription) {
                super(target);
                mSubscription = subscription;
            }

            /** {@inheritDoc} */
            @Override
            protected Cursor doInBackground(SimContactsService target, Void... v) {
                final Context context = target;
                final ContentResolver resolver = context.getContentResolver();
                Cursor result;

                Intent intent = new Intent();
                if(mSubscription != SUB1 && mSubscription != SUB2)
                    return null;
                if (mSubscription == SUB1) {
                        intent.setData(Uri.parse("content://icc/adn_sub1"));
                } else if (mSubscription == SUB2) {
                        intent.setData(Uri.parse("content://icc/adn_sub2"));
                }
                Uri uri = intent.getData();
                if (DBG) log("QuerySimTask: starting  query sim card");

                result = resolver.query(uri,COLUMN_NAMES,null,null,null);

                return result;
            }

            @Override
            protected  void onPostExecute(SimContactsService target, Cursor result) {
                final Context context = target;
                target.onQuerySimCompleted(result, mSubscription);
            }
    }



    public static class QueryDatabaseTask extends
                WeakAsyncTask<Void, Void, Cursor, SimContactsService> {

            private int mSubscription;

            public QueryDatabaseTask(SimContactsService target, int subscription) {
                super(target);
                mSubscription = subscription;
            }

            /** {@inheritDoc} */
            @Override
            protected Cursor doInBackground(SimContactsService target, Void... params) {
                final Context context = target;
                final ContentResolver resolver = context.getContentResolver();
                Cursor result = null;

                Uri uri = RawContacts.CONTENT_URI;
                String orderBy = "_ID asc";
                if (DBG) log("QueryDatabaseTask: starting query database");

                if (!isMultiSimEnabled()) {
                    result = resolver.query(uri,CONTACTS_ID_PROJECTION,
                        SIM_DATABASE_SELECTION,SIM_DATABASE_SELECTARGS,orderBy);
                }
                else {
                    if (mSubscription == 0) {
                        result = resolver.query(uri,CONTACTS_ID_PROJECTION,
                            SIM_DATABASE_SELECTION,SIM_DATABASE_SELECTARGS_SUB1,orderBy);

                    } else if (mSubscription == 1) {
                        result = resolver.query(uri,CONTACTS_ID_PROJECTION,
                            SIM_DATABASE_SELECTION,SIM_DATABASE_SELECTARGS_SUB2,orderBy);
                    }
                }
                return result;
            }

            @Override
            protected  void onPostExecute(SimContactsService target, Cursor result) {
                final Context context = target;
                target.onQueryDatabaseCompleted(result, mSubscription);
            }
    }

    public static class DeleteDatabaseTask extends
                WeakAsyncTask<Void, Void, Integer, SimContactsService> {

            private int mSubscription;

            public DeleteDatabaseTask(SimContactsService target, int subscription) {
                super(target);
                mSubscription = subscription;
            }

            /** {@inheritDoc} */
            @Override
            protected Integer doInBackground(SimContactsService target, Void... params) {
                final Context context = target;
                final ContentResolver resolver = context.getContentResolver();
                Integer result = null;

                Uri uri = RawContacts.CONTENT_URI;
                String orderBy = "_ID asc";
                if (DBG) log("QueryDatabaseTask: start delete database sim contacts in sub " + mSubscription);

                if (!isMultiSimEnabled()) {
                        result = resolver.delete(uri,
                            SIM_DATABASE_SELECTION,SIM_DATABASE_SELECTARGS);
                }
                else {
                    if (mSubscription == 0) {
                        result = resolver.delete(uri,
                            SIM_DATABASE_SELECTION,SIM_DATABASE_SELECTARGS_SUB1);

                    } else if (mSubscription == 1) {
                        result = resolver.delete(uri,
                            SIM_DATABASE_SELECTION,SIM_DATABASE_SELECTARGS_SUB2);
                    }
                }
                return result;
            }

            @Override
            protected  void onPostExecute(SimContactsService target, Integer result) {
                final Context context = target;
                target.onDeleteDatabaseCompleted(result, mSubscription);
            }
    }



    private void onQuerySimCompleted(Cursor cursor, int subscription) {
        if (cursor == null) {
            log("onQuerySimCompleted: cursor is null");
            return;
        }
        log(" onQuerySimCompleted: cursor.count=" + cursor.getCount()
            + " at sub " +  subscription);
        mSimCursor[subscription] = cursor;
        if (isNewCardInserted(subscription)){
                if (isMultiSimEnabled()) {
                    addAllSimContactsIntoDatabase(subscription);
                } else {
                    addAllSimContactsIntoDatabase();
                }
            } else {
                if (isMultiSimEnabled()) {
                    queryDatabaseSimContactsID(subscription);
                } else {
                    queryDatabaseSimContactsID();
                }
            }
    }

    private void onQueryDatabaseCompleted(Cursor cursor, int subscription) {
        if (cursor == null) {
            log("onQueryDatabaseCompleted: cursor is null");
            return;
        }
        log(" onQueryDatabaseCompleted: cursor.count=" + cursor.getCount()
            + " at sub " + subscription);
        mDatabaseCursor[subscription]= cursor;
        UpdateSimDatabaseInPhone(subscription);
    }

    private void onDeleteDatabaseCompleted(int result, int subscription) {
        log("onDeleteDatabaseCompleted  sub is " + subscription);
    }

}

