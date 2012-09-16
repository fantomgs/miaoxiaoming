/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.contacts.ui;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.RecentCallsListActivity;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.SimContactsOperation;
import com.android.contacts.model.SimSource;

public class MultiPickContactActivity extends ListActivity implements
        View.OnClickListener, TextView.OnEditorActionListener,
        OnTouchListener, TextWatcher {
    private final static String TAG = "MultiPickContactActivity";
    private final static boolean DEBUG = true;

    public static final String RESULT_KEY = "result";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
        Contacts.IN_VISIBLE_GROUP,
    };

    static final String CONTACTS_SELECTION = Contacts.IN_VISIBLE_GROUP + "=1" ;

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, // 0
        Phone.TYPE, // 1
        Phone.LABEL, // 2
        Phone.NUMBER, // 3
        Phone.DISPLAY_NAME, // 4
        Phone.CONTACT_ID, // 5
        Phone.PRESENCE, // 6
        Phone.PHOTO_ID, // 7
        Phone.LOOKUP_KEY, // 8
        RawContacts.ACCOUNT_TYPE,
        RawContacts.ACCOUNT_NAME
    };

    static final String PHONES_SELECTION = RawContacts.ACCOUNT_TYPE + "<>?" ;

    static final String[] PHONES_SELECTION_ARGS = {SimContactsConstants.ACCOUNT_TYPE_SIM};

    static final String[] EMAILS_PROJECTION = new String[] {
        Email._ID,
        Phone.DISPLAY_NAME,
        Email.ADDRESS,
    };

    static final int SUMMARY_ID_COLUMN_INDEX = 0;
    static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    static final int SUMMARY_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    static final int SUMMARY_STARRED_COLUMN_INDEX = 4;
    static final int SUMMARY_TIMES_CONTACTED_COLUMN_INDEX = 5;
    static final int SUMMARY_PRESENCE_STATUS_COLUMN_INDEX = 6;
    static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 7;
    static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;
    static final int SUMMARY_PHONETIC_NAME_COLUMN_INDEX = 9;
    static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 10;

    private static final int PHONE_COLUMN_ID = 0;
    private static final int PHONE_COLUMN_TYPE = 1;
    private static final int PHONE_COLUMN_LABEL = 2;
    private static final int PHONE_COLUMN_NUMBER = 3;
    private static final int PHONE_COLUMN_DISPLAY_NAME = 4;
    private static final int PHONE_COLUMN_CONTACT_ID = 5;
    private static final int PHONE_COLUMN_PRESENCE = 6;
    private static final int PHONE_COLUMN_PHOTO_ID = 7;
    private static final int PHONE_COLUMN_LOOKUP_KEY = 8;

    private static final int EMAIL_COLUMN_ID = 0;
    private static final int EMAIL_COLUMN_DISPLAY_NAME = 1;
    private static final int EMAIL_COLUMN_ADDRESS = 2;

    private static final int ACCOUNT_COLUMN_ID = 0;
    private static final int ACCOUNT_COLUMN_NAME = 1;
    private static final int ACCOUNT_COLUMN_TYPE = 2;

    private static final int QUERY_TOKEN = 42;
    private static final int MODE_MASK_SEARCH = 0x80000000;

    private static final int MODE_DEFAULT_CONTACT = 0;
    private static final int MODE_DEFAULT_PHONE = 1;
    private static final int MODE_DEFAULT_EMAIL = 1 << 1;
    private static final int MODE_DEFAULT_CALL = 1 << 1 << 1;
    private static final int MODE_SEARCH_CONTACT = MODE_DEFAULT_CONTACT | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_PHONE = MODE_DEFAULT_PHONE | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_EMAIL = MODE_DEFAULT_EMAIL | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_CALL = MODE_DEFAULT_CALL | MODE_MASK_SEARCH;

    static final String ACTION_MULTI_PICK = "com.android.contacts.action.MULTI_PICK";
    static final String ACTION_MULTI_PICK_EMAIL = "com.android.contacts.action.MULTI_PICK_EMAIL";
    static final String ACTION_MULTI_PICK_CALL = "com.android.contacts.action.MULTI_PICK_CALL";

    private ContactItemListAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Bundle mChoiceSet;
    private Bundle mBackupChoiceSet;
    private EditText mSearchEditor;
    private Button mOKButton;
    private Button mCancelButton;
    private TextView mSelectAllLabel;
    private CheckBox mSelectAllCheckBox;
    private int mMode;

    private ProgressDialog mProgressDialog;
    private Drawable mDrawableIncoming;
    private Drawable mDrawableOutgoing;
    private Drawable mDrawableMissed;
    private CharSequence[] mLabelArray;
    private SimContactsOperation mSimContactsOperation;

    /**
     * control of whether show the contacts in SIM card, if intent has this
     * flag,not show.
     */
    public static final String EXT_NOT_SHOW_SIM_FLAG = "not_sim_show";

    private static final int DIALOG_DEL_CALL = 1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_DELETE.equals(action)) {
            mMode = MODE_DEFAULT_CONTACT;
            setTitle(R.string.menu_deleteContact);
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            mMode = MODE_DEFAULT_CONTACT;
        } else if (ACTION_MULTI_PICK.equals(action)) {
            mMode = MODE_DEFAULT_PHONE;
        } else if (ACTION_MULTI_PICK_EMAIL.equals(action)) {
            mMode = MODE_DEFAULT_EMAIL;
        } else if (ACTION_MULTI_PICK_CALL.equals(action)) {
            mMode = MODE_DEFAULT_CALL;
            setTitle(R.string.delete_call_title);
            mDrawableIncoming = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            mDrawableOutgoing = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            mDrawableMissed = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            mLabelArray = getResources().getTextArray(com.android.internal.R.array.phoneTypes);
        }

        this.setContentView(R.layout.pick_contact);
        mChoiceSet = new Bundle();
        mAdapter = new ContactItemListAdapter(this);
        getListView().setAdapter(mAdapter);
        mQueryHandler = new QueryHandler(this);
        mSimContactsOperation = new SimContactsOperation(this);
        initResource();
        startQuery();
    }

    private boolean isSearchMode() {
        return (mMode & MODE_MASK_SEARCH) == MODE_MASK_SEARCH;
    }

    private void initResource() {
        mOKButton = (Button) findViewById(R.id.btn_ok);
        mOKButton.setOnClickListener(this);
        mOKButton.setText(getOKString());
        mCancelButton = (Button) findViewById(R.id.btn_cancel);
        mCancelButton.setOnClickListener(this);
        mSearchEditor = ((EditText) findViewById(R.id.search_field));
        mSearchEditor.addTextChangedListener(this);
        if(isPickCall()){
            mSearchEditor.setVisibility(View.INVISIBLE);
        }
        mSearchEditor.setOnClickListener(this);
        mSearchEditor.setOnTouchListener(this);
        mSearchEditor.setOnEditorActionListener(this);
        mSelectAllCheckBox = (CheckBox) findViewById(R.id.select_all_check);
        mSelectAllCheckBox.setOnClickListener(this);
        mSelectAllLabel = (TextView) findViewById(R.id.select_all_label);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        hideSoftKeyboard();
        CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
        boolean isChecked = checkBox.isChecked() ? false : true;
        checkBox.setChecked(isChecked);
        if (isChecked) {
            String[] value = null;
            ContactItemCache cache = (ContactItemCache) v.getTag();
            if (isPickContact()) {
                value = new String[] {cache.lookupKey};
            } else if (isPickPhone()) {
                value = new String[] {cache.name, cache.number, cache.type, cache.label, cache.contact_id};
            } else if (isPickEmail()) {
                value = new String[] {cache.name, cache.email};
            }
            mChoiceSet.putStringArray(String.valueOf(id), value);
            if (!isSearchMode()) {
                if (mChoiceSet.size() == mAdapter.getCount()) {
                    mSelectAllCheckBox.setChecked(true);
                }
            }
        } else {
            mChoiceSet.remove(String.valueOf(id));
            mSelectAllCheckBox.setChecked(false);
        }
        mOKButton.setText(getOKString());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (isSearchMode()) {
                    exitSearchMode(false);
                    return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private String getOKString() {
        return getString(R.string.btn_ok) + "(" + mChoiceSet.size() + ")";
    }

    private void backupChoiceSet() {
        mBackupChoiceSet = (Bundle) mChoiceSet.clone();
    }

    private void restoreChoiceSet() {
        mChoiceSet = mBackupChoiceSet;
    }

    private void enterSearchMode() {
        mMode |= MODE_MASK_SEARCH;
        mSelectAllLabel.setVisibility(View.GONE);
        mSelectAllCheckBox.setVisibility(View.GONE);
        backupChoiceSet();
    }

    private void exitSearchMode(boolean isConfirmed) {
        mMode &= ~MODE_MASK_SEARCH;
        hideSoftKeyboard();
        mSelectAllLabel.setVisibility(View.VISIBLE);
        mSelectAllCheckBox.setVisibility(View.VISIBLE);
        if (!isConfirmed) restoreChoiceSet();
        mSearchEditor.setText("");
        mOKButton.setText(getOKString());
    }

    public void onClick(View v) {
        int id = v.getId();
        switch(id) {
        case R.id.btn_ok:
            if (!isSearchMode()) {
                if (mMode == MODE_DEFAULT_CONTACT) {
                    if (Intent.ACTION_GET_CONTENT.equals(getIntent().getAction())){
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putBundle(RESULT_KEY, mChoiceSet);
                        intent.putExtras(bundle);
                        this.setResult(RESULT_OK, intent);
                        finish();
                    }else if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_delete_contact_confirmation);
                    }
                } else if (mMode == MODE_DEFAULT_PHONE) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(RESULT_KEY, mChoiceSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                } else if (mMode == MODE_DEFAULT_EMAIL) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(RESULT_KEY, mChoiceSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                } else if (mMode == MODE_DEFAULT_CALL) {
                    if (mChoiceSet.size() > 0) {
                        showDialog(DIALOG_DEL_CALL);
                    }
                }
            } else {
                exitSearchMode(true);
            }
            break;
        case R.id.btn_cancel:
            if (!isSearchMode()) {
                this.setResult(this.RESULT_CANCELED);
                finish();
            } else {
                exitSearchMode(false);
            }
            break;
        case R.id.select_all_check:
            if (mSelectAllCheckBox.isChecked()) {
                selectAll(true);
            } else {
                selectAll(false);
            }
            break;
        case R.id.search_field:
            enterSearchMode();
            break;
        }
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);
        if (mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }

        super.onDestroy();
    }

    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch(id) {
           case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.ContactMultiDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
           case DIALOG_DEL_CALL:{
                return new AlertDialog.Builder(this).setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert).setMessage(
                                R.string.delete_call_alert).setNegativeButton(
                                android.R.string.cancel, null).setPositiveButton(
                                android.R.string.ok, new DeleteClickListener()).create();
           }
        }

        return super.onCreateDialog(id, bundle);
    }

    private class DeleteContactsThread extends Thread
            implements OnCancelListener, OnClickListener {

        boolean mCanceled = false;
        private String name = null;
        private String number = null;
        final String[] PROJECTION = new String[] {
            Phone.CONTACT_ID,
            Phone.NUMBER,
            Phone.DISPLAY_NAME
        };
        private final int COLUMN_NUMBER = 1;
        private final int COLUMN_NAME = 2;

        public DeleteContactsThread() {
            super("DeleteContactsThread");
        }

        @Override
        public void run() {
            final ContentResolver resolver = getContentResolver();

            Set<String> keySet = mChoiceSet.keySet();
            Iterator<String> it = keySet.iterator();
            while (!mCanceled  && it.hasNext()) {
                String id = it.next();
                Uri uri = null;
                if(isPickCall()){
                    uri = Uri.withAppendedPath(Calls.CONTENT_URI, id);
                }else{
                    uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
                    long longId = Long.parseLong(id);
                    int subscription =
                        mSimContactsOperation.getSimSubscription(longId);
                    if (subscription ==0 || subscription ==1 ) {
                        ContentValues values =
                            mSimContactsOperation.getSimAccountValues(longId);
                        Log.d(TAG, "values is : "+ values + "; sub is "+ subscription);
                        int result = mSimContactsOperation.delete(values,subscription);
                        if (result == 0)
                            continue;
                    }
                }
                resolver.delete(uri, null, null);
                mProgressDialog.incrementProgressBy(1);
            }
            Log.d(TAG, "DeleteContactsThread run, progress:" + mProgressDialog.getProgress());
            mProgressDialog.dismiss();
            finish();
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            Log.d(TAG, "DeleteContactsThread onCancel, progress:" + mProgressDialog.getProgress());
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            } else {
                Log.e(TAG, "Unknown button event has come: " + dialog.toString());
            }
        }


    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            CharSequence title = null;
            CharSequence message = null;

            if(isPickCall()){
                title = getString(R.string.delete_call_title);
                message = getString(R.string.delete_call_message);
            }else{
                title = getString(R.string.delete_contacts_title);
                message = getString(R.string.delete_contacts_message);
            }

            DeleteContactsThread thread = new DeleteContactsThread();

            DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_SEARCH:
                    case KeyEvent.KEYCODE_CALL:
                        return true;
                    default:
                        return false;
                    }
                }
            };

            mProgressDialog = new ProgressDialog(MultiPickContactActivity.this);
            mProgressDialog.setTitle(title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.btn_cancel), thread);
            mProgressDialog.setOnCancelListener(thread);
            mProgressDialog.setOnKeyListener(keyListener);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(mChoiceSet.size());
            mProgressDialog.show();

            thread.start();
        }
    }

    private Uri getUriToQuery() {
        switch (mMode) {
            case MODE_DEFAULT_CONTACT:
            case MODE_SEARCH_CONTACT:
                return Contacts.CONTENT_URI;
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
                return Email.CONTENT_URI;
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                return Phone.CONTENT_URI;
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return Calls.CONTENT_URI;
            default:
                Log.w(TAG, "getUriToQuery: Incorrect mode: " + mMode);
        }
        return Contacts.CONTENT_URI;
    }

    private Uri getFilterUri() {
        switch (mMode) {
            case MODE_SEARCH_CONTACT:
                return Contacts.CONTENT_FILTER_URI;
            case MODE_SEARCH_PHONE:
                return Phone.CONTENT_FILTER_URI;
            case MODE_SEARCH_EMAIL:
                return Email.CONTENT_FILTER_URI;
            default:
                Log.w(TAG, "getFilterUri: Incorrect mode: " + mMode);
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    private String[] getProjectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_CONTACT:
            case MODE_SEARCH_CONTACT:
                return CONTACTS_SUMMARY_PROJECTION;
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                return PHONES_PROJECTION;
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
                return EMAILS_PROJECTION;
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return RecentCallsListActivity.CALL_LOG_PROJECTION;
            default:
                Log.w(TAG, "getProjectionForQuery: Incorrect mode: " + mMode);
        }
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private String getSortOrder(String[] projection) {
        switch (mMode) {
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return RecentCallsListActivity.CALL_LOG_PROJECTION[2] + " desc";
        }
        return "sort_key";
    }

    private String getSelectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_PHONE:
                if (isShowSIM())
                    return null;
                else
                    return PHONES_SELECTION;
            case MODE_DEFAULT_CONTACT:
                return CONTACTS_SELECTION;
            default:
                return null;
        }
    }

    private String[] getSelectionArgsForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_PHONE:
                if (isShowSIM())
                    return null;
                else
                    return PHONES_SELECTION_ARGS;
            default:
                return null;
        }
    }

    private boolean isShowSIM(){
        return !getIntent().hasExtra(EXT_NOT_SHOW_SIM_FLAG);
    }

    public void startQuery() {
        Uri uri = getUriToQuery();
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection,
                selectionArgs, getSortOrder(projection));
    }

    public void afterTextChanged(Editable s) {
        if (!TextUtils.isEmpty(s)) {
            if (!isSearchMode()) {
                enterSearchMode();
            }
        }else if(isSearchMode()){
            exitSearchMode(true);
        }
        doFilter(s);
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void doFilter(Editable s) {
        if (TextUtils.isEmpty(s)) {
            startQuery();
            return;
        }
        Uri uri = Uri.withAppendedPath(getFilterUri(), Uri.encode(s.toString()));
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection,
                selectionArgs, getSortOrder(projection));
    }

    public void updateContent() {
        if (isSearchMode()) {
            doFilter(mSearchEditor.getText());
        } else {
            startQuery();
        }
    }

    private String getMultiSimName(int subscription) {
        return Settings.System.getString(getContentResolver(),
                Settings.System.MULTI_SIM_NAME[subscription]);
    }

    private boolean isPickContact() {
        return mMode == MODE_DEFAULT_CONTACT || mMode == MODE_SEARCH_CONTACT;
    }

    private boolean isPickPhone() {
        return mMode == MODE_DEFAULT_PHONE || mMode == MODE_SEARCH_PHONE;
    }

    private boolean isPickEmail() {
        return mMode == MODE_DEFAULT_EMAIL || mMode == MODE_SEARCH_EMAIL;
    }

    private boolean isPickCall() {
        return mMode == MODE_DEFAULT_CALL || mMode == MODE_SEARCH_CALL;
    }

    private void selectAll(boolean isSelected) {
        // update mChoiceSet.
        // TODO: make it more efficient
        Cursor cursor = mAdapter.getCursor();
        if (cursor == null) {
        	Log.w(TAG, "cursor is null.");
        	return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String id = null;
            String[] value = null;
            if (isPickContact()) {
                id = String.valueOf(cursor.getLong(SUMMARY_ID_COLUMN_INDEX));
                value = new String[] {cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX)};
            } else if (isPickPhone()) {
                id = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                String name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                String number = cursor.getString(PHONE_COLUMN_NUMBER);
                String type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));
                String label = cursor.getString(PHONE_COLUMN_LABEL);
                String contact_id = String.valueOf(cursor.getLong(PHONE_COLUMN_CONTACT_ID));
                value = new String[] {name, number, type, label, contact_id};
            } else if (isPickEmail()) {
                id = String.valueOf(cursor.getLong(EMAIL_COLUMN_ID));
                String name = cursor.getString(EMAIL_COLUMN_DISPLAY_NAME);
                String email = cursor.getString(EMAIL_COLUMN_ADDRESS);
                value = new String[] {name, email, id};
            } else if (isPickCall()) {
                id = String.valueOf(cursor.getLong(RecentCallsListActivity.ID_COLUMN_INDEX));
                value = new String[] {id};
            }
            if (DEBUG) Log.d(TAG, "isSelected: " + isSelected + ", id: " + id);
            if (isSelected) {
                mChoiceSet.putStringArray(id, value);
            } else {
                mChoiceSet.remove(id);
            }
        }

        // update UI items.
        mOKButton.setText(getOKString());

        int count = mList.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mList.getChildAt(i);
            CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
            checkBox.setChecked(isSelected);
        }
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<MultiPickContactActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MultiPickContactActivity>((MultiPickContactActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final MultiPickContactActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
        }
    }

    private final class ContactItemCache {
        long id;
        String name;
        String number;
        String lookupKey;
        String type;
        String label;
        String contact_id;
        String email;
    }

    private final class ContactItemListAdapter extends CursorAdapter {
        Context mContext;
        protected LayoutInflater mInflater;

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            //TODO
            mContext = context;
            mInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            if (isPickContact()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? getString(R.string.unknown) : cache.name);
                view.findViewById(R.id.pick_contact_number).setVisibility(View.GONE);
            } else if (isPickPhone()) {
                cache.id = cursor.getLong(PHONE_COLUMN_ID);
                cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                cache.type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));
                ((TextView) view.findViewById(R.id.pick_contact_name)).setText(cache.name);
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);
            } else if (isPickEmail()) {
                cache.id = cursor.getLong(EMAIL_COLUMN_ID);
                cache.name = cursor.getString(EMAIL_COLUMN_DISPLAY_NAME);
                cache.email = cursor.getString(EMAIL_COLUMN_ADDRESS);
                ((TextView) view.findViewById(R.id.pick_contact_name)).setText(cache.name);
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.email);
            } else if (isPickCall()) {
                cache.id = cursor.getLong(RecentCallsListActivity.ID_COLUMN_INDEX);
                String number = cursor.getString(RecentCallsListActivity.NUMBER_COLUMN_INDEX);
                String callerName = cursor.getString(RecentCallsListActivity.CALLER_NAME_COLUMN_INDEX);
                int callerNumberType = cursor.getInt(RecentCallsListActivity.CALLER_NUMBERTYPE_COLUMN_INDEX);
                String callerNumberLabel = cursor.getString(RecentCallsListActivity.CALLER_NUMBERLABEL_COLUMN_INDEX);
                int subscription = cursor.getInt(RecentCallsListActivity.PHONE_SUBSCRIPTION_COLUMN_INDEX);
                long date = cursor.getLong(RecentCallsListActivity.DATE_COLUMN_INDEX);
                long duration = cursor.getLong(RecentCallsListActivity.DURATION_COLUMN_INDEX);
                int type = cursor.getInt(RecentCallsListActivity.CALL_TYPE_COLUMN_INDEX);

                ImageView callType = (ImageView)view.findViewById(R.id.call_type_icon);
                TextView dateText = (TextView)view.findViewById(R.id.date);
                TextView durationText = (TextView)view.findViewById(R.id.duration);
                TextView subSlotText = (TextView)view.findViewById(R.id.subscription);
                TextView numberLableText = (TextView)view.findViewById(R.id.label);
                TextView numberText = (TextView)view.findViewById(R.id.number);
                TextView callerNameText = (TextView)view.findViewById(R.id.line1);

                // Set the icon
                switch (type) {
                    case Calls.INCOMING_TYPE:
                        callType.setImageDrawable(mDrawableIncoming);
                        break;

                    case Calls.OUTGOING_TYPE:
                        callType.setImageDrawable(mDrawableOutgoing);
                        break;

                    case Calls.MISSED_TYPE:
                        callType.setImageDrawable(mDrawableMissed);
                        break;
                }

                // set the number
                if(!TextUtils.isEmpty(callerName)){
                    callerNameText.setText(callerName);
                    callerNameText.setVisibility(View.VISIBLE);

                    numberText.setVisibility(View.GONE);
                    numberText.setText(null);
                }else{
                    callerNameText.setVisibility(View.GONE);
                    callerNameText.setText(null);

                    numberText.setVisibility(View.VISIBLE);
                    numberText.setText(number);
                }

                CharSequence numberLabel = null;
                if (!PhoneNumberUtils.isUriNumber(number)) {
                    numberLabel = Phone.getDisplayLabel(context, callerNumberType, callerNumberLabel,
                            mLabelArray);
                }
                if (!TextUtils.isEmpty(numberLabel)) {
                    numberLableText.setText(numberLabel);
                    numberLableText.setVisibility(View.VISIBLE);
                }else{
                    numberLableText.setText(null);
                    numberLableText.setVisibility(View.INVISIBLE);
                }

                // set date
                dateText.setText(DateUtils.getRelativeTimeSpanString(date,
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));

                // set duration
                durationText.setText(DateUtils.formatElapsedTime(duration));

                // set slot
                subSlotText.setText(getMultiSimName(subscription));
            }

            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);
            if (mChoiceSet.containsKey(String.valueOf(cache.id))) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = null;
            if(isPickCall())
                v = mInflater.inflate(R.layout.pick_calls_item, parent, false);
            else
                v = mInflater.inflate(R.layout.pick_contact_item, parent, false);
            ContactItemCache cache = new ContactItemCache();
            v.setTag(cache);
            return v;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;

            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException(
                        "couldn't move cursor to position " + position);
            }
            if (convertView != null && convertView.getTag() != null) {
                v = convertView;
            } else {
                v = newView(mContext, mCursor, parent);
            }
            bindView(v, mContext, mCursor);
            return v;
        }

        @Override
        protected void onContentChanged() {
            updateContent();
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            if (!isSearchMode()) {
                if (cursor != null && cursor.getCount() == mChoiceSet.size()) {
                    mSelectAllCheckBox.setChecked(true);
                } else {
                    mSelectAllCheckBox.setChecked(false);
                }
            }
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == getListView()) {
            hideSoftKeyboard();
        }
        return false;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            Log.d(TAG, "onEditorAction:hide soft keyboard");
            return true;
        }
        Log.d(TAG, "onEditorAction");
        return false;
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchEditor.getWindowToken(), 0);
    }

    private String getTextFilter() {
        if (mSearchEditor != null) {
            return mSearchEditor.getText().toString();
        }
        return null;
    }
}

