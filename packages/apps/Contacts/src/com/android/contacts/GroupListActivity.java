/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Data;
import android.provider.LocalGroups;
import android.provider.LocalGroups.Group;
import android.provider.LocalGroups.GroupColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.GroupListActivity.AddGroupDialog.AddGroupListener;

public class GroupListActivity extends ListActivity {

    private static final String TAG = "GroupListActivity";

    private static final boolean DEBUG = false;

    private GroupListAdapter mAdapter;

    private QueryHandler queryHandler;

    private static final int TOKEN_QUERY_GROUP = 1;

    private static final int INDEX_MENU_ADD = 3;

    private static final int INDEX_MENU_SETTINGS = 1;

    private static final int INDEX_MENU_DELETE = 2;

    private static final int DIALOG_ADD_GROUP = 1;

    private Group currentGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_list);
        addGroupsHeader();
        mAdapter = new GroupListAdapter(this);
        getListView().setAdapter(mAdapter);
        queryHandler = new QueryHandler(this);
        registerForContextMenu(getListView());
        getContentResolver().registerContentObserver(LocalGroups.CONTENT_URI, true, observer);
        startQuery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(observer);
    }

    private ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mAdapter.refresh();
        }

    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(0, INDEX_MENU_ADD, 0, R.string.menu_option_add);
        item.setIcon(android.R.drawable.ic_menu_add);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case INDEX_MENU_ADD:
                showDialog(DIALOG_ADD_GROUP);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case INDEX_MENU_SETTINGS:
                goToGroupEdit(getSelectUri(currentGroup.getId()));
                break;
            case INDEX_MENU_DELETE:
                deleteGroup();
                break;
        }
        return super.onContextItemSelected(item);
    }

    private void deleteGroup() {
        currentGroup.delete(getContentResolver());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        currentGroup = (Group) ((AdapterView.AdapterContextMenuInfo) menuInfo).targetView.getTag();
        menu.setHeaderTitle(currentGroup.getTitle());
        menu.add(0, INDEX_MENU_SETTINGS, 0, R.string.menu_context_setting);
        menu.add(0, INDEX_MENU_DELETE, 0, R.string.menu_context_delete);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    private void addGroupsHeader() {
        TextView header = (TextView) findViewById(R.id.groups_head);
        header.setText(R.string.group_list_head);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        goToGroupEdit(getSelectUri(((Group) v.getTag()).getId()));
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ADD_GROUP:
                return new AddGroupDialog(this, new AddGroupListener() {
                    @Override
                    public void onAddGroup(String name) {
                        Group group = new Group();
                        group.setTitle(name);
                        group.save(getContentResolver());
                    }
                });
        }
        return super.onCreateDialog(id);
    }

    private void goToGroupEdit(Uri data) {
        Intent intent = new Intent(Intent.ACTION_EDIT, data);
        intent.putExtra("data", data);
        intent.setType("vnd.android.cursor.item/local-groups");
        this.startActivity(intent);
    }

    private Uri getSelectUri(long id) {
        return ContentUris.withAppendedId(LocalGroups.CONTENT_URI, id);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new QueryTask(this).execute();
    }

    private static class QueryTask extends AsyncTask<Object, Object, Object> {

        private Activity target;

        QueryTask(Activity target) {
            this.target = target;
        }

        @Override
        protected Object doInBackground(Object... params) {
            ((GroupListActivity)target).countMemebers();
            return null;
        }
    }

    private void countMemebers() {
        if (DEBUG)
            Log.d(TAG, "start count members of groups");
        Cursor groups = null;
        final ArrayList<ContentProviderOperation> updateList = new ArrayList<ContentProviderOperation>();
        try {
            groups = getContentResolver().query(LocalGroups.CONTENT_URI, new String[] {
                GroupColumns._ID
            }, null, null, null);
            while (groups.moveToNext()) {
                countMemebersById(updateList, groups.getLong(0));
            }
        } finally {
            if (groups != null) {
                groups.close();
            }
        }

        if (DEBUG)
            Log.d(TAG, "count of update list:" + updateList.size());
        if (updateList.size() > 0) {
            try {
                getContentResolver().applyBatch(LocalGroups.AUTHORITY, updateList);
            } catch (Exception e) {
            }
        }
    }

    private void countMemebersById(ArrayList<ContentProviderOperation> updateList, long groupId) {
        Cursor datas = null;
        try {
            datas = getContentResolver().query(Data.CONTENT_URI, null,
                    Data.MIMETYPE + "=? and " + LocalGroup.DATA1 + "=?", new String[] {
                            LocalGroup.CONTENT_ITEM_TYPE, String.valueOf(groupId)
                    }, null);
            int count = datas == null ? 0 : datas.getCount();
            if (DEBUG)
                Log.d(TAG, "count of members:" + count + " in group:" + groupId);
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(LocalGroups.CONTENT_URI);
            builder.withValue(GroupColumns.COUNT, count);
            builder.withSelection(GroupColumns._ID + "=?", new String[] {
                String.valueOf(groupId)
            });
            updateList.add(builder.build());
        } finally {
            if (datas != null) {
                datas.close();
            }
        }
    }

    private void startQuery() {
        queryHandler.cancelOperation(TOKEN_QUERY_GROUP);
        queryHandler.startQuery(TOKEN_QUERY_GROUP, null, LocalGroups.CONTENT_URI, null, null, null,
                null);
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<GroupListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<GroupListActivity>((GroupListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final GroupListActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
        }
    }

    public static class AddGroupDialog extends AlertDialog implements OnClickListener, TextWatcher {

        public static interface AddGroupListener {
            void onAddGroup(String name);
        }

        public static final int GROUP_NAME_MAX_LENGTH = 20;

        private EditText groupSettings;

        private AddGroupListener addGroupListener;

        protected AddGroupDialog(Context context, AddGroupListener addGroupListener) {
            super(context);
            this.addGroupListener = addGroupListener;
            groupSettings = new EditText(context);
            groupSettings.setHint(R.string.title_group_name);
            groupSettings.addTextChangedListener(this);
            setTitle(R.string.title_group_add);
            setView(groupSettings);
            setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                    this);
            setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
            setOnShowListener(new OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                            groupSettings.getText().length() > 0);
                }
            });
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    String name = groupSettings.getText().toString();
                    addGroupListener.onAddGroup(name);
                    break;
            }
            groupSettings.setText(null);
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
            limitTextSize(s.toString());
            getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(groupSettings.getText().length() > 0);
        }

        private void limitTextSize(String s) {
            int len = 0;
            for (int i = 0; i < s.length(); i++) {
                int ch = Character.codePointAt(s, i);
                // to make sure no matter the language is English or Chinese the
                // group name is displayed in single line
                if (ch >= 0x00 && ch <= 0xFF)
                    len++;
                else
                    len += 2;
                if (len > GROUP_NAME_MAX_LENGTH || ch == 10 || ch == 32) {
                    s = s.substring(0, i);
                    groupSettings.setText(s);
                    groupSettings.setSelection(s.length(), s.length());
                    break;
                }
            }
        }

    }

    private class GroupListAdapter extends CursorAdapter {

        public GroupListAdapter(Context context) {
            super(context, null);
        }

        private void refresh() {
            super.onContentChanged();
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Group group = Group.restoreGroup(cursor);
            TextView groupNameView = (TextView) view.findViewById(R.id.group_name);
            TextView groupSizeView = (TextView) view.findViewById(R.id.group_size);
            groupNameView.setText(group.getTitle());
            groupSizeView.setText("(" + group.getCount() + ")");
            view.setTag(group);

        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = LayoutInflater.from(context).inflate(R.layout.group_item, null);
            return view;
        }
    }

}
