/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
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

import static android.view.Window.PROGRESS_VISIBILITY_OFF;
import static android.view.Window.PROGRESS_VISIBILITY_ON;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.PhonesColumns;
import android.telephony.PhoneNumberUtils;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.Phone;

/**
 * Activity to let the user add or edit an FDN contact.
 */
public class EditFdnContactScreen extends Activity {
    private static final String LOG_TAG = PhoneApp.LOG_TAG;
    private static final boolean DBG = false;

    // Menu item codes
    private static final int MENU_IMPORT = 1;
    private static final int MENU_DELETE = 2;

    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";

    private static final int PIN2_REQUEST_CODE = 100;
    private static final int SUB1 = 0;
    private static final int SUB2 = 1;
    private static int mSubscription = 0;

    private String mName;
    private String mNumber;
    private String mPin2;
    private boolean mAddContact;
    private QueryHandler mQueryHandler;

    private EditText mNameField;
    private EditText mNumberField;
    private LinearLayout mPinFieldContainer;
    private Button mButton;

    private Handler mHandler = new Handler();

    /**
     * Constants used in importing from contacts
     */
    /** request code when invoking subactivity */
    private static final int CONTACTS_PICKER_CODE = 200;
    /** projection for phone number query */
    private static final String NUM_PROJECTION[] = {PeopleColumns.DISPLAY_NAME,
        PhonesColumns.NUMBER};
    /** static intent to invoke phone number picker */
    private static final Intent CONTACT_IMPORT_INTENT;
    static {
        CONTACT_IMPORT_INTENT = new Intent(Intent.ACTION_GET_CONTENT);
        CONTACT_IMPORT_INTENT.setType(android.provider.Contacts.Phones.CONTENT_ITEM_TYPE);
    }
    /** flag to track saving state */
    private boolean mDataBusy;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        resolveIntent();

        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.edit_fdn_contact_screen);
        setupView();
        setTitle(mAddContact ?
                R.string.add_fdn_contact : R.string.edit_fdn_contact);

        mDataBusy = false;
    }

    /**
     * We now want to bring up the pin request screen AFTER the
     * contact information is displayed, to help with user
     * experience.
     *
     * Also, process the results from the contact picker.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        if (DBG) log("onActivityResult request:" + requestCode + " result:" + resultCode);
        mSubscription = getIntent().getIntExtra("sub_id", 0);

        switch (requestCode) {
            case PIN2_REQUEST_CODE:
                Bundle extras = (intent != null) ? intent.getExtras() : null;
                if (extras != null) {
                    mPin2 = extras.getString("pin2");
                    if (mAddContact) {
                        addContact();
                    } else {
                        updateContact();
                    }
                } else if (resultCode != RESULT_OK) {
                    // if they cancelled, then we just cancel too.
                    if (DBG) log("onActivityResult: cancelled.");
                    finish();
                }
                break;

            // look for the data associated with this number, and update
            // the display with it.
            case CONTACTS_PICKER_CODE:
                if (resultCode != RESULT_OK) {
                    if (DBG) log("onActivityResult: cancelled.");
                    return;
                }
                Cursor cursor = getContentResolver().query(intent.getData(),
                        NUM_PROJECTION, null, null, null);
                if ((cursor == null) || (!cursor.moveToFirst())) {
                    Log.w(LOG_TAG,"onActivityResult: bad contact data, no results found.");
                    return;
                }
                mNameField.setText(cursor.getString(0));
                mNumberField.setText(cursor.getString(1));
                break;
        }
    }

    /**
     * Overridden to display the import and delete commands.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Resources r = getResources();

        // Added the icons to the context menu
        menu.add(0, MENU_IMPORT, 0, r.getString(R.string.importToFDNfromContacts))
                .setIcon(R.drawable.ic_menu_contact);
        menu.add(0, MENU_DELETE, 0, r.getString(R.string.menu_delete))
                .setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    /**
     * Allow the menu to be opened ONLY if we're not busy.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        return mDataBusy ? false : result;
    }

    /**
     * Overridden to allow for handling of delete and import.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT:
                startActivityForResult(CONTACT_IMPORT_INTENT, CONTACTS_PICKER_CODE);
                return true;

            case MENU_DELETE:
                deleteSelected();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void resolveIntent() {
        Intent intent = getIntent();

        mSubscription = intent.getIntExtra("sub_id", 0);
        mName =  intent.getStringExtra(INTENT_EXTRA_NAME);
        mNumber =  intent.getStringExtra(INTENT_EXTRA_NUMBER);

        mAddContact = TextUtils.isEmpty(mNumber);
    }

    /**
     * We have multiple layouts, one to indicate that the user needs to
     * open the keyboard to enter information (if the keybord is hidden).
     * So, we need to make sure that the layout here matches that in the
     * layout file.
     */
    private void setupView() {
        mNameField = (EditText) findViewById(R.id.fdn_name);
        if (mNameField != null) {
            // Don't use this effect that select all text on focus
            //mNameField.setOnFocusChangeListener(mOnFocusChangeHandler);
            //mNameField.setOnClickListener(mClicked);
        }

        mNumberField = (EditText) findViewById(R.id.fdn_number);
        if (mNumberField != null) {
            mNumberField.setKeyListener(DialerKeyListener.getInstance());
            // Don't use this effect that select all text on focus
            //mNumberField.setOnFocusChangeListener(mOnFocusChangeHandler);
            //mNumberField.setOnClickListener(mClicked);
        }

        if (!mAddContact) {
            if (mNameField != null) {
                mNameField.setText(mName);
            }
            if (mNumberField != null) {
                mNumberField.setText(mNumber);
            }
        }

        mButton = (Button) findViewById(R.id.button);
        if (mButton != null) {
            mButton.setOnClickListener(mClicked);
        }

        mPinFieldContainer = (LinearLayout) findViewById(R.id.pinc);

    }

    private String getNameFromTextField() {
        return mNameField.getText().toString();
    }

    private String getNumberFromTextField() {
        return mNumberField.getText().toString();
    }

    private Uri getContentURI() {
        if (mSubscription == SUB1) {
            return Uri.parse("content://icc/fdn_sub1");
        } else if (mSubscription == SUB2) {
            return Uri.parse("content://icc/fdn_sub2");
        } else {
            // we should never reach here.
            if (DBG) log("invalid mSubscription");
            return null;
        }
    }

    /**
      * @param number is voice mail number
      * @return true if number length is less than 20-digit limit
      */
     private boolean isValidNumber(String number) {
         return (number.length() <= 20);
     }


    private void addContact() {
        if (DBG) log("addContact");

        if (!isValidNumber(getNumberFromTextField())) {
            handleResult(false, true);
            return;
        }

        Uri uri = getContentURI();

        ContentValues bundle = new ContentValues(4);
        bundle.put("tag", getNameFromTextField());
        bundle.put("number", getNumberFromTextField());
        bundle.put("pin2", mPin2);
        bundle.put("sub_id", mSubscription);


        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startInsert(0, null, uri, bundle);
        displayProgress(true);
        showStatus(getResources().getText(R.string.adding_fdn_contact));
    }

    private void updateContact() {
        if (DBG) log("updateContact");

        if (!isValidNumber(getNumberFromTextField())) {
            handleResult(false, true);
            return;
        }
        Uri uri = getContentURI();

        ContentValues bundle = new ContentValues();
        bundle.put("tag", mName);
        bundle.put("number", mNumber);
        bundle.put("newTag", getNameFromTextField());
        bundle.put("newNumber", getNumberFromTextField());
        bundle.put("pin2", mPin2);
        bundle.put("sub_id", mSubscription);

        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startUpdate(0, null, uri, bundle, null, null);
        displayProgress(true);
        showStatus(getResources().getText(R.string.updating_fdn_contact));
    }

    /**
     * Handle the delete command, based upon the state of the Activity.
     */
    private void deleteSelected() {
        // delete ONLY if this is NOT a new contact.
        if (!mAddContact) {
            Intent intent = new Intent();
            intent.putExtra("sub_id", mSubscription);
            intent.setClass(this, DeleteFdnContactScreen.class);
            intent.putExtra(INTENT_EXTRA_NAME, mName);
            intent.putExtra(INTENT_EXTRA_NUMBER, mNumber);
            startActivity(intent);
        }
        finish();
    }

    private void authenticatePin2() {
        Intent intent = new Intent();
        intent.setClass(this, GetPin2Screen.class);
        startActivityForResult(intent, PIN2_REQUEST_CODE);
    }

    private void displayProgress(boolean flag) {
        // indicate we are busy.
        mDataBusy = flag;
        getWindow().setFeatureInt(
                Window.FEATURE_INDETERMINATE_PROGRESS,
                mDataBusy ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
        // make sure we don't allow calls to save when we're
        // not ready for them.
        mButton.setClickable(!mDataBusy);
    }

    /**
     * Removed the status field, with preference to displaying a toast
     * to match the rest of settings UI.
     */
    private void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT)
            .show();
        }
    }

    private void handleResult(boolean success, boolean invalidNumber) {
        Phone mPhone;

        if (success) {
            if (DBG) log("handleResult: success!");
            showStatus(getResources().getText(mAddContact ?
                    R.string.fdn_contact_added : R.string.fdn_contact_updated));
        } else {
            if (DBG) log("handleResult: failed!");
            if (invalidNumber) {
                showStatus(getResources().getText(R.string.fdn_invalid_number));
            } else {
                mPhone = PhoneApp.getPhone(mSubscription);
                if (mPhone.getIccCard().getIccPin2Blocked()) {
                    showStatus(getResources().getText(R.string.fdn_enable_puk2_requested));
                } else if (mPhone.getIccCard().getIccPuk2Blocked()) {
                    showStatus(getResources().getText(R.string.puk2_blocked));
                } else {
                    showStatus(getResources().getText(R.string.pin2_invalid));
                }
            }
        }

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 2000);

    }

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (mPinFieldContainer.getVisibility() != View.VISIBLE) {
                return;
            }

            String name, number;
            if (v == mNameField) {
                name = mNameField.getText().toString().trim();
                mNameField.setText(name);
                mNumberField.requestFocus();
            } else if (v == mNumberField) {
                number = mNumberField.getText().toString().replace(" ", "");
                mNumberField.setText(number);
                mButton.requestFocus();
            } else if (v == mButton) {
                name = mNameField.getText().toString().trim();
                mNameField.setText(name);
                number = mNumberField.getText().toString();
                number = PhoneNumberUtils.stripSeparators(number);
                mNumberField.setText(number);
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(number)) {
                    Toast.makeText(EditFdnContactScreen.this, R.string.fdn_empty_number,
                        Toast.LENGTH_SHORT).show();
                    return;
                }

                // Authenticate the pin AFTER the contact information
                // is entered, and if we're not busy.
                if (!mDataBusy) {
                    authenticatePin2();
                }
            }
        }
    };

    View.OnFocusChangeListener mOnFocusChangeHandler =
            new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                TextView textView = (TextView) v;
                Selection.selectAll((Spannable) textView.getText());
            }
        }
    };

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
        }

        @Override
        protected void onInsertComplete(int token, Object cookie,
                                        Uri uri) {
            if (DBG) log("onInsertComplete");
            displayProgress(false);
            handleResult(uri != null, false);
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            if (DBG) log("onUpdateComplete");
            displayProgress(false);
            handleResult(result > 0, false);
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[EditFdnContact" + mSubscription +"] " + msg);
    }
}
