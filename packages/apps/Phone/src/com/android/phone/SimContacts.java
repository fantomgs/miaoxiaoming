/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2010-2011, Code Aurora Forum. All rights reserved.
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

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * SIM Address Book UI for the Phone app.
 */
public class SimContacts extends ADNList {
    private static final String LOG_TAG = "SimContacts";

    static final ContentValues sEmptyContentValues = new ContentValues();

    private static final int MENU_IMPORT_ONE = 1;
    private static final int MENU_IMPORT_ALL = 2;
    private static final int SUB1 = 0;
    private static final int SUB2 = 1;
    private ProgressDialog mProgressDialog;

    private Account mAccount;

    private static class NamePhoneTypePair {
        final String name;
        final int phoneType;
        public NamePhoneTypePair(String nameWithPhoneType) {
            // Look for /W /H /M or /O at the end of the name signifying the type
            int nameLen = nameWithPhoneType.length();
            if (nameLen - 2 >= 0 && nameWithPhoneType.charAt(nameLen - 2) == '/') {
                char c = Character.toUpperCase(nameWithPhoneType.charAt(nameLen - 1));
                if (c == 'W') {
                    phoneType = Phone.TYPE_WORK;
                } else if (c == 'M' || c == 'O') {
                    phoneType = Phone.TYPE_MOBILE;
                } else if (c == 'H') {
                    phoneType = Phone.TYPE_HOME;
                } else {
                    phoneType = Phone.TYPE_OTHER;
                }
                name = nameWithPhoneType.substring(0, nameLen - 2);
            } else {
                phoneType = Phone.TYPE_OTHER;
                name = nameWithPhoneType;
            }
        }
    }

    private class ImportAllSimContactsThread extends Thread
            implements OnCancelListener, OnClickListener {

        boolean mCanceled = false;

        public ImportAllSimContactsThread() {
            super("ImportAllSimContactsThread");
        }

        @Override
        public void run() {
            final ContentValues emptyContentValues = new ContentValues();
            final ContentResolver resolver = getContentResolver();

            mCursor.moveToPosition(-1);
            while (!mCanceled && mCursor.moveToNext()) {
                actuallyImportOneSimContact(mCursor, resolver, mAccount);
                mProgressDialog.incrementProgressBy(1);
            }

            mProgressDialog.dismiss();
            finish();
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            } else {
                Log.e(LOG_TAG, "Unknown button event has come: " + dialog.toString());
            }
        }
    }

    // From HardCodedSources.java in Contacts app.
    // TODO: fix this.
    private static final String ACCOUNT_TYPE_GOOGLE = "com.google";
    private static final String GOOGLE_MY_CONTACTS_GROUP = "System Group: My Contacts";

    private static void actuallyImportOneSimContact(
            final Cursor cursor, final ContentResolver resolver, Account account) {
        final NamePhoneTypePair namePhoneTypePair =
            new NamePhoneTypePair(cursor.getString(NAME_COLUMN));
        final String name = namePhoneTypePair.name;
        final int phoneType = namePhoneTypePair.phoneType;
        final String phoneNumber = cursor.getString(NUMBER_COLUMN);
        final String emailAddresses = cursor.getString(EMAILS_COLUMN);
        final String[] emailAddressArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }

        final ArrayList<ContentProviderOperation> operationList =
            new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        String myGroupsId = null;
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);

            // TODO: temporal fix for "My Groups" issue. Need to be refactored.
            if (ACCOUNT_TYPE_GOOGLE.equals(account.type)) {
                final Cursor tmpCursor = resolver.query(Groups.CONTENT_URI, new String[] {
                        Groups.SOURCE_ID },
                        Groups.TITLE + "=?", new String[] {
                        GOOGLE_MY_CONTACTS_GROUP }, null);
                try {
                    if (tmpCursor != null && tmpCursor.moveToFirst()) {
                        myGroupsId = tmpCursor.getString(0);
                    }
                } finally {
                    if (tmpCursor != null) {
                        tmpCursor.close();
                    }
                }
            }
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
        builder.withValue(Phone.TYPE, phoneType);
        builder.withValue(Phone.NUMBER, phoneNumber);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.DATA, emailAddress);
                operationList.add(builder.build());
            }
        }

        if (myGroupsId != null) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(GroupMembership.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
            builder.withValue(GroupMembership.GROUP_SOURCE_ID, myGroupsId);
            operationList.add(builder.build());
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private void importOneSimContact(int position) {
        final ContentResolver resolver = getContentResolver();
        if (mCursor.moveToPosition(position)) {
            actuallyImportOneSimContact(mCursor, resolver, mAccount);
            Toast.makeText(this, R.string.import_finish, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(LOG_TAG, "Failed to move the cursor to the position \"" + position + "\"");
        }
    }

    /* Followings are overridden methods */

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        if (intent != null) {
            final String accountName = intent.getStringExtra("account_name");
            final String accountType = intent.getStringExtra("account_type");
            if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                mAccount = new Account(accountName, accountType);
            }
        }

        registerForContextMenu(getListView());
    }

    @Override
    protected CursorAdapter newAdapter() {
        return new SimpleCursorAdapter(this, R.layout.sim_import_list_entry, mCursor,
                new String[] { "name" }, new int[] { android.R.id.text1 });
    }

    @Override
    protected void onDestroy() {
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected Uri resolveIntent() {
        Intent intent = getIntent();
        // We have got mSubscription form intent in ADNList.onCreate().
        //mSubscription = TelephonyManager.getPreferredVoiceSubscription();
        Log.d(TAG, "resolveIntent: mSubscription: " + mSubscription);
        if (mSubscription == SUB1) {
            intent.setData(Uri.parse("content://icc/adn_sub1"));
        } else if (mSubscription == SUB2) {
            intent.setData(Uri.parse("content://icc/adn_sub2"));
        } else {
            Log.d(TAG, "resolveIntent:Invalid subcription");
        }
        if (Intent.ACTION_PICK.equals(intent.getAction())) {
            // "index" is 1-based
            mInitialSelection = intent.getIntExtra("index", 0) - 1;
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            mInitialSelection = 0;
        }
        return intent.getData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_IMPORT_ALL, 0, R.string.importAllSimEntries);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_IMPORT_ALL);
        if (item != null) {
            item.setVisible(mCursor != null && mCursor.getCount() > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT_ALL:
                CharSequence title = getString(R.string.importAllSimEntries);
                CharSequence message = getString(R.string.importingSimContacts);

                ImportAllSimContactsThread thread = new ImportAllSimContactsThread();

                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setOnCancelListener(thread);
                mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                        getString(R.string.cancel), thread);
                mProgressDialog.setProgress(0);
                if (mCursor != null) {
                    mProgressDialog.setMax(mCursor.getCount());
                }
                mProgressDialog.show();

                thread.start();

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT_ONE:
                ContextMenu.ContextMenuInfo menuInfo = item.getMenuInfo();
                if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
                    int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
                    importOneSimContact(position);
                    return true;
                }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo itemInfo =
                    (AdapterView.AdapterContextMenuInfo) menuInfo;
            TextView textView = (TextView) itemInfo.targetView.findViewById(android.R.id.text1);
            if (textView != null) {
                menu.setHeaderTitle(textView.getText());
            }
            menu.add(0, MENU_IMPORT_ONE, 0, R.string.importSimEntry);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        importOneSimContact(position);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (mCursor != null && mCursor.moveToPosition(getSelectedItemPosition())) {
                    String phoneNumber = mCursor.getString(NUMBER_COLUMN);
                    if (phoneNumber == null || !TextUtils.isGraphic(phoneNumber)) {
                        // There is no number entered.
                        //TODO play error sound or something...
                        return true;
                    }
                    Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", phoneNumber, null));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                          | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    startActivity(intent);
                    finish();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
