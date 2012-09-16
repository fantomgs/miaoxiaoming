
/*
 *
 * Copyright (C) 2011, Code Aurora Forum. All rights reserved.
 *
 *
 *Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of Code Aurora Forum, Inc. nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.android.phone;

import android.util.Log;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.SimpleAdapter;

import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ADDED_ECC_LIST;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_RUIM_IS_OMH_CARD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;



public class EmergencyCallItem extends ListActivity {

    static final String TAG = "EmergencyCallItem";
    static final boolean DBG = true;

    static final int MODIFY_ECC_NUMBER = 0;
    static final int DELETE_ECC_NUMBER = 1;
    private String number = null;
    private AlertDialog mDialog;
    private ArrayList<HashMap<String, Object>> listItem = new ArrayList<HashMap<String, Object>>();
    private SimpleAdapter listItemAdapter;
    private int position = 0;
    private ArrayList<String> addEccNumList = new ArrayList<String>();

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(DBG) Log.d(TAG, "onCreate");
        setContentView(R.layout.emergency_call_item_main);
        Intent intent = getIntent();
        number = intent.getStringExtra(EmergencyCallList.NUMBER);
        position = intent.getIntExtra(EmergencyCallList.POSITION,0);
        if(DBG) Log.d(TAG, "call number position :" + position);
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("ItemImage", R.drawable.ic_dialog_call);
        map.put("ItemText", number);
        listItem.add(map);
        listItemAdapter = new SimpleAdapter(this,listItem,
                R.layout.emergency_call_item,
                new String[] {"ItemImage", "ItemText"},
                new int[] {R.id.ItemImage, R.id.ItemText}
        );
        String addNumbers = SystemProperties.get(PROPERTY_ADDED_ECC_LIST);
        for (String eccNum : addNumbers.split(",")) {
            if ( !addEccNumList.contains(eccNum)) {
                addEccNumList.add(eccNum);
            }
        }
        setListAdapter(listItemAdapter);

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        if (number != null) {
            Intent intent = new Intent(Intent.ACTION_CALL_EMERGENCY);
            intent.setData(Uri.fromParts("tel", number, null));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (position >= EmergencyCallList.defaultNumbersLength) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.item, menu);
            return true;
        }
        return false;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ecc_delete:
                showDialog(DELETE_ECC_NUMBER);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DELETE_ECC_NUMBER:
                deleteEmergencyNumber();
                return null;
            default:
        }

        return null;
    }

    private void deleteEmergencyNumber() {
        Log.d(TAG,"deleteEmergencyNumber ");
        addEccNumList.remove(number);
        Log.d(TAG,"after delete, added emergency number is " + addEccNumListToString());

        SystemProperties.set(PROPERTY_ADDED_ECC_LIST,addEccNumListToString());
        this.finish();
    }

    private String addEccNumListToString() {
        String str = null;
        String string = Arrays.toString(addEccNumList.toArray());
        string = string.replace("[", "");
        string = string.replace("]", "");
        string = string.trim();
        String[] stringArray = string.split(", ");
        for (int i = 0; i<stringArray.length; i++) {
            if (i == 0) str = stringArray[i];
            else {
                str = str + "," + stringArray[i];
            }
        }
        return str;
    }

}

