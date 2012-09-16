/*
 * Copyright (C) 2011-2012, Code Aurora Forum. All rights reserved.
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

package com.android.providers.contacts;

import java.util.ArrayList;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Data;
import android.provider.LocalGroups;
import android.provider.LocalGroups.GroupColumns;
import android.util.Log;

public class LocalGroupsProvider extends ContentProvider {

    private static final String TAG = "LocalGroupsProvider";

    private static final boolean DEBUG = false;

    private static final String DATABASES = "local_groups.db";

    private static final int VERSION = 1;

    private static final int GROUPS = 0;

    private static final int GROUPS_ID = 1;

    private DatabaseHelper mOpenHelper;

    private static final UriMatcher urlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        urlMatcher.addURI(LocalGroups.AUTHORITY, "local-groups", GROUPS);
        urlMatcher.addURI(LocalGroups.AUTHORITY, "local-groups/#", GROUPS_ID);
    }

    /*
    private ContentObserver DataObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            countMemebers();
        }

    };

    private void countMemebers() {
        if (DEBUG)
            Log.d(TAG, "start count members of groups");
        Cursor groups = null;
        final ArrayList<ContentProviderOperation> updateList = new ArrayList<ContentProviderOperation>();
        try {
            groups = getContext().getContentResolver().query(LocalGroups.CONTENT_URI, new String[] {
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
                getContext().getContentResolver().applyBatch(LocalGroups.AUTHORITY, updateList);
            } catch (Exception e) {
            }
        }
    }

    private void countMemebersById(ArrayList<ContentProviderOperation> updateList, long groupId) {
        Cursor datas = null;
        try {
            datas = getContext().getContentResolver().query(Data.CONTENT_URI, null,
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
    */

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = urlMatcher.match(uri);
        switch (match) {
            case GROUPS:
                count = db.delete("local_groups", selection, selectionArgs);
                break;
        }
        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri result = null;
        long rowId = -1;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = urlMatcher.match(uri);
        switch (match) {
            case GROUPS:
                rowId = db.insert("local_groups", null, values);
                break;
        }
        if (rowId != -1) {
            result = ContentUris.withAppendedId(uri, rowId);
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return result;
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        /*
        getContext().getContentResolver().registerContentObserver(Data.CONTENT_URI, true,
                DataObserver);
                */
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        int match = urlMatcher.match(uri);
        Cursor ret = null;
        switch (match) {
            case GROUPS: {
                ret = db.query("local_groups", projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            }
            case GROUPS_ID: {
                ret = db.query("local_groups", projection, LocalGroups.QUERY_ID, new String[] {
                    uri.getLastPathSegment()
                }, null, null, sortOrder);
                break;
            }
        }
        return ret;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = urlMatcher.match(uri);
        switch (match) {
            case GROUPS: {
                count = db.update("local_groups", values, selection, selectionArgs);
                break;
            }
        }
        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private Context context;

        public DatabaseHelper(Context context) {
            this(context, DATABASES, null, VERSION);
            this.context = context;
        }

        public DatabaseHelper(Context context, String name, CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        /**
         * init database
         */
        public void onCreate(SQLiteDatabase db) {

            db.execSQL("CREATE TABLE local_groups (" + LocalGroups.GroupColumns._ID
                    + " INTEGER PRIMARY KEY," + LocalGroups.GroupColumns.TITLE + " text,"
                    + LocalGroups.GroupColumns.COUNT + " INTEGER);");

            db.execSQL("insert into local_groups (" + LocalGroups.GroupColumns.TITLE
                    + ") values ('" + context.getString(R.string.group_family) + "')");
            db.execSQL("insert into local_groups (" + LocalGroups.GroupColumns.TITLE
                    + ") values ('" + context.getString(R.string.group_friend) + "')");
            db.execSQL("insert into local_groups (" + LocalGroups.GroupColumns.TITLE
                    + ") values ('" + context.getString(R.string.group_work) + "')");

        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

    }

}
