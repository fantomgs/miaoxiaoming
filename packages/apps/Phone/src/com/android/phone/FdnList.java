/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.os.Bundle;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;

import android.view.View;
import android.widget.ListView;
import android.database.Cursor;

/**
 * FDN List UI for the Phone app.
 */
public class FdnList extends ADNList {
    private static final int MENU_ADD = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_DELETE = 3;
    private static final int SUB1 = 0;
    private static final int SUB2 = 1;

    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";

    private static final boolean DBG = true;

    @Override
	protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        registerForContextMenu(getListView());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterForContextMenu(getListView());
    }

    @Override
    protected Uri resolveIntent() {
        Intent intent = getIntent();
        if (mSubscription == SUB1) {
            intent.setData(Uri.parse("content://icc/fdn_sub1"));
        } else if (mSubscription == SUB2) {
            intent.setData(Uri.parse("content://icc/fdn_sub2"));
        } else {
            // we should never reach here.
            if (DBG) log("invalid mSubscription");
        }

        return intent.getData();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        log("onCreateContextMenu");

        Cursor cursor = mCursorAdapter.getCursor();
        if ((cursor != null) && (cursor.getPosition() >= 0)) {
            menu.setHeaderTitle(cursor.getString(NAME_COLUMN) +
                " [" + cursor.getString(NUMBER_COLUMN) + "]");

            // Added the icons to the context menu
            menu.add(0, MENU_EDIT, 0, R.string.menu_edit)
                    .setIcon(android.R.drawable.ic_menu_edit);
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete)
                    .setIcon(android.R.drawable.ic_menu_delete);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_EDIT:
            editSelected();
            return true;
        case MENU_DELETE:
            deleteSelected();
            return true;
        default:
           return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Resources r = getResources();

        // Added the icons to the context menu
        menu.add(0, MENU_ADD, 0, r.getString(R.string.menu_add))
                .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, MENU_EDIT, 0, r.getString(R.string.menu_edit))
                .setIcon(android.R.drawable.ic_menu_edit);
        menu.add(0, MENU_DELETE, 0, r.getString(R.string.menu_delete))
                .setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean hasSelection = (getSelectedItemPosition() >= 0);

        menu.findItem(MENU_ADD).setVisible(true);
        menu.findItem(MENU_EDIT).setVisible(hasSelection);
        menu.findItem(MENU_DELETE).setVisible(hasSelection);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD:
                addContact();
                return true;

            case MENU_EDIT:
                editSelected();
                return true;

            case MENU_DELETE:
                deleteSelected();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // TODO: is this what we really want?
        editSelected();
    }

    private void addContact() {
        // if we don't put extras "name" when starting this activity, then
        // EditFdnContactScreen treats it like add contact.
        Intent intent = new Intent();
        intent.putExtra(SUBSCRIPTION, mSubscription);
        intent.setClass(this, EditFdnContactScreen.class);
        startActivity(intent);
    }

    /**
     * Edit the item at the selected position in the list.
     */
    private void editSelected() {
        Cursor cursor = mCursorAdapter.getCursor();
        if ((cursor != null) && (cursor.getPosition() >= 0)) {
            log("editSelected " + cursor.getPosition());

            String name = cursor.getString(NAME_COLUMN);
            String number = cursor.getString(NUMBER_COLUMN);

            Intent intent = new Intent();
            intent.putExtra(SUBSCRIPTION, mSubscription);
            intent.setClass(this, EditFdnContactScreen.class);
            intent.putExtra(INTENT_EXTRA_NAME, name);
            intent.putExtra(INTENT_EXTRA_NUMBER, number);
            startActivity(intent);
        }
    }

    private void deleteSelected() {
        Cursor cursor = mCursorAdapter.getCursor();
        if ((cursor != null) && (cursor.getPosition() >= 0)) {
            log("deleteSelected " + cursor.getPosition());

            String name = cursor.getString(NAME_COLUMN);
            String number = cursor.getString(NUMBER_COLUMN);

            Intent intent = new Intent();
            intent.putExtra(SUBSCRIPTION, mSubscription);
            intent.setClass(this, DeleteFdnContactScreen.class);
            intent.putExtra(INTENT_EXTRA_NAME, name);
            intent.putExtra(INTENT_EXTRA_NUMBER, number);
            startActivity(intent);
        }
    }
}
