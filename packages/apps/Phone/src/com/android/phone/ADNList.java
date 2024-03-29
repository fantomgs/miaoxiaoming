/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All Rights Reserved.
 * Copyright (C) 2007 The Android Open Source Project
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

import static android.view.Window.PROGRESS_VISIBILITY_OFF;
import static android.view.Window.PROGRESS_VISIBILITY_ON;

import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Window;
import android.widget.CursorAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.os.SystemProperties;

/**
 * ADN List activity for the Phone app.
 */
public class ADNList extends ListActivity {
    protected String TAG = "ADNList";
    protected static final boolean DBG = true;

    private static final String[] COLUMN_NAMES = new String[] {
        "name",
        "number",
        "emails"
    };

    protected static final int NAME_COLUMN = 0;
    protected static final int NUMBER_COLUMN = 1;
    protected static final int EMAILS_COLUMN = 2;

    protected static final String SUBSCRIPTION = "sub_id";
    private static final int SUB1 = 0;
    private static final int SUB2 = 1;

    private static final int[] VIEW_NAMES = new int[] {
        android.R.id.text1,
        android.R.id.text2
    };

    protected static final int QUERY_TOKEN = 0;
    protected static final int INSERT_TOKEN = 1;
    protected static final int UPDATE_TOKEN = 2;
    protected static final int DELETE_TOKEN = 3;


    protected QueryHandler mQueryHandler;
    protected CursorAdapter mCursorAdapter;
    protected Cursor mCursor = null;

    private TextView mEmptyText;

    protected int mInitialSelection = -1;
    protected int mSubscription = 0;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        mSubscription = getIntent().getIntExtra(SUBSCRIPTION, 0);
        TAG += mSubscription;

        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.adn_list);
        mEmptyText = (TextView) findViewById(android.R.id.empty);
        mQueryHandler = new QueryHandler(getContentResolver());

        query();
    }

    @Override
    protected void onResume() {
        super.onResume();
        query();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCursor != null) {
            mCursor.deactivate();
        }
    }

    protected Uri resolveIntent() {
        Intent intent = getIntent();
        // should get preferred voice sub?
        //mSubscription = TelephonyManager.getPreferredVoiceSubscription();
        if (intent.getData() == null) {
            if (mSubscription == SUB1) {
                intent.setData(Uri.parse("content://icc/adn_sub1"));
            } else if (mSubscription == SUB2) {
                intent.setData(Uri.parse("content://icc/adn_sub2"));
            } else {
                if (DBG) log("resolveIntent: invalid subscription");
            }
        }

        return intent.getData();
    }

    private void query() {
        Uri uri = resolveIntent();
        if (DBG) log("query: starting an async query");
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, COLUMN_NAMES,
                null, null, null);
        displayProgress(true);
    }

    private void reQuery() {
        query();
    }

    private void setAdapter() {
        // NOTE:
        // As it it written, the positioning code below is NOT working.
        // However, this current non-working state is in compliance with
        // the UI paradigm, so we can't really do much to change it.

        // In the future, if we wish to get this "positioning" correct,
        // we'll need to do the following:
        //   1. Change the layout to in the cursor adapter to:
        //     android.R.layout.simple_list_item_checked
        //   2. replace the selection / focus code with:
        //     getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        //     getListView().setItemChecked(mInitialSelection, true);

        // Since the positioning is really only useful for the dialer's
        // SpecialCharSequence case (dialing '2#' to get to the 2nd
        // contact for instance), it doesn't make sense to mess with
        // the usability of the activity just for this case.

        // These artifacts include:
        //  1. UI artifacts (checkbox and highlight at the same time)
        //  2. Allowing the user to edit / create new SIM contacts when
        //    the user is simply trying to retrieve a number into the d
        //    dialer.

        if (mCursorAdapter == null) {
            mCursorAdapter = newAdapter();

            setListAdapter(mCursorAdapter);
        } else {
            mCursorAdapter.changeCursor(mCursor);
        }

        if (mInitialSelection >=0 && mInitialSelection < mCursorAdapter.getCount()) {
            setSelection(mInitialSelection);
            getListView().setFocusableInTouchMode(true);
            boolean gotfocus = getListView().requestFocus();
        }
    }

    protected CursorAdapter newAdapter() {
        return new SimpleCursorAdapter(this,
                    android.R.layout.simple_list_item_2,
                    mCursor, COLUMN_NAMES, VIEW_NAMES);
    }

    private void displayProgress(boolean flag) {
        if (DBG) log("displayProgress: " + flag);
		if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
		{
			mEmptyText.setText(flag ? R.string.simContacts_emptyLoading: R.string.uimContacts_empty);
		}
		else
		{
        	mEmptyText.setText(flag ? R.string.simContacts_emptyLoading: R.string.simContacts_empty);
		}
        getWindow().setFeatureInt(
                Window.FEATURE_INDETERMINATE_PROGRESS,
                flag ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
            if (DBG) {
                if (c != null) {
                    log("onQueryComplete: cursor.count=" + c.getCount());
                } else {
                    log("onQueryComplete: cursor is null");
                }
            }
			try
			{
            	mCursor = c;
            	setAdapter();
            	displayProgress(false);
			}
			catch(Exception e)
			{
				log("verifyUnlockNWT Error:"+e);
			}
        }

        @Override
        protected void onInsertComplete(int token, Object cookie,
                                        Uri uri) {
            if (DBG) log("onInsertComplete: requery");
            reQuery();
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            if (DBG) log("onUpdateComplete: requery");
            reQuery();
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            if (DBG) log("onDeleteComplete: requery");
            reQuery();
        }
    }

    protected void log(String msg) {
        Log.d(TAG, "[ADNList] " + msg);
    }
}
