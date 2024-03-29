/*
 * Copyright (C) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.LocalGroups.Group;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import com.android.contacts.GroupListActivity.AddGroupDialog;
import com.android.contacts.ui.MultiPickContactActivity;

public class GroupEditActivity extends PreferenceActivity implements OnPreferenceChangeListener,
        OnPreferenceClickListener, TextWatcher {

    private static final String KEY_TITLE = "group_title";

    private static final String KEY_MEMBER = "group_member";

    private EditTextPreference titleView;

    private Group group;

    private PreferenceScreen addMemberView;

    private static final int CODE_PICK_MEMBER = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(R.xml.group_edit);
        titleView = (EditTextPreference) this.findPreference(KEY_TITLE);
        titleView.getEditText().addTextChangedListener(this);
        titleView.setOnPreferenceChangeListener(this);
        addMemberView = (PreferenceScreen) this.findPreference(KEY_MEMBER);
        addMemberView.setOnPreferenceClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        group = Group.restoreGroupById(getContentResolver(),
                Long.parseLong(getIntent().getData().getLastPathSegment()));
        initView();
    }

    private void initView() {
        titleView.setTitle(group.getTitle());
        titleView.setText(group.getTitle());
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {
        if (titleView == arg0 && !group.getTitle().equals(arg1) && ((String) arg1).length() > 0) {
            group.setTitle((String) arg1);
            if (group.update(getContentResolver()))
                initView();
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (addMemberView == preference) {
            pickMembers();
        }
        return false;
    }

    private void pickMembers() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setClass(this, MultiPickContactActivity.class);
        this.startActivityForResult(intent, CODE_PICK_MEMBER);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK)
            switch (requestCode) {
                case CODE_PICK_MEMBER:
                    Bundle result = data.getExtras().getBundle("result");
                    new AddMembersTask(result).execute();
            }
    }

    class AddMembersTask extends AsyncTask<Object, Object, Object> {
        private ProgressDialog mProgressDialog;

        private Handler handler;

        private Handler alertHandler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                Toast.makeText(GroupEditActivity.this, R.string.toast_not_add, Toast.LENGTH_LONG)
                        .show();
            }
        };

        private Bundle result;

        private int size;

        AddMembersTask(Bundle result) {
            size = result.size();
            this.result = result;
            HandlerThread thread = new HandlerThread("DownloadTask");
            thread.start();
            handler = new Handler(thread.getLooper()) {
                public void dispatchMessage(Message msg) {
                    if (mProgressDialog != null && msg.what > 0) {
                        mProgressDialog.incrementProgressBy(msg.what);
                    } else if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                }
            };
        }

        protected void onPostExecute(Object result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(GroupEditActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(size);
            mProgressDialog.show();
        }

        @Override
        protected Bundle doInBackground(Object... params) {
            proccess();
            return null;
        }

        public void proccess() {
            boolean hasInvalide = false;
            ContentValues values = new ContentValues();
            values.put(LocalGroup.DATA1, group.getId());

            Set<String> keySet = result.keySet();
            Iterator<String> it = keySet.iterator();
            final ArrayList<ContentProviderOperation> insertList = new ArrayList<ContentProviderOperation>();
            final ArrayList<ContentProviderOperation> removeList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;
            while (it.hasNext()) {
                handler.obtainMessage(1).sendToTarget();
                String id = it.next();
                Cursor c = null;
                try {
                    c = getContentResolver().query(RawContacts.CONTENT_URI, new String[] {
                            RawContacts._ID, RawContacts.ACCOUNT_TYPE
                    }, RawContacts.CONTACT_ID + "=?", new String[] {
                        id
                    }, null);
                    if (c.moveToNext()) {
                        String rawId = String.valueOf(c.getLong(0));

                        if (!SimContactsConstants.ACCOUNT_TYPE_PHONE.equals(c.getString(1))) {
                            hasInvalide = true;
                            continue;
                        }

                        builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                        builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                                + "=?", new String[] {
                                rawId, LocalGroup.CONTENT_ITEM_TYPE
                        });
                        removeList.add(builder.build());

                        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                        builder.withValue(LocalGroup.DATA1, group.getId());
                        builder.withValue(Data.RAW_CONTACT_ID, rawId);
                        builder.withValue(Data.MIMETYPE, LocalGroup.CONTENT_ITEM_TYPE);
                        insertList.add(builder.build());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }

            if (removeList.size() > 0) {
                try {
                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, removeList);
                } catch (Exception e) {
                }
            }

            if (insertList.size() > 0) {
                try {
                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, insertList);
                } catch (Exception e) {
                }
            }

            if (hasInvalide) {
                alertHandler.sendEmptyMessage(0);
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // TODO Auto-generated method stub

    }

    @Override
    public void afterTextChanged(Editable s) {
        String name = s.toString();
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            int ch = Character.codePointAt(name, i);
            // to make sure no matter the language is English or Chinese the
            // group name is displayed in single line
            if (ch >= 0x00 && ch <= 0xFF)
                len++;
            else
                len += 2;
            if (len > AddGroupDialog.GROUP_NAME_MAX_LENGTH || ch == 10 || ch == 32) {
                name = name.substring(0, i);
                titleView.getEditText().setText(name);
                titleView.getEditText().setSelection(name.length(), name.length());
                break;
            }
        }

    }

}
