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

import android.net.Uri;
import android.util.Log;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
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
import android.widget.SimpleAdapter;
import android.widget.Toast;

import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ADDED_ECC_LIST;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_RUIM_IS_OMH_CARD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public class EmergencyCallList extends ListActivity {

    static final String TAG = "EmergencyCallList";
    static final boolean DBG = true;
    static final String NUMBER = "number";
    static final String POSITION = "position";
    static final int ADD_ECC_NUMBER = 0;
    protected static int defaultNumbersLength;
    private AlertDialog mDialog;
    private StringBuilder eccAddList = null;
    private SimpleAdapter listItemAdapter;
    private ArrayList<HashMap<String, Object>> listItem = new ArrayList<HashMap<String, Object>>();

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(DBG) Log.d(TAG, "onCreate");
        setContentView(R.layout.emergency_call_list);
        String defaultNumbers = SystemProperties.get("ril.ecclist");

        int length = defaultNumbers.split(",").length;

        if(DBG) Log.d(TAG, "default ecc Numbers " +defaultNumbers +
                            "defaultNumbersLength " +defaultNumbersLength);
        for (String eccNum : defaultNumbers.split(",")) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("ItemImage", R.drawable.ic_dialog_call);
            map.put("ItemText", eccNum);
            if (listItem.contains(map)) {
                length --;
            } else {
                listItem.add(map);
            }
        }
        defaultNumbersLength = length;
        listItemAdapter = new SimpleAdapter(this,listItem,
                R.layout.emergency_call_list_item,
                new String[] {"ItemImage", "ItemText"},
                new int[] {R.id.ItemImage, R.id.ItemText}
        );
        setListAdapter(listItemAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        int size = listItem.size();
        int addedNumersLength = size - defaultNumbersLength;
        int i = addedNumersLength;
        while ( i>0 ) {
            listItem.remove(defaultNumbersLength);
            i--;
        }
        String addNumbers = SystemProperties.get(PROPERTY_ADDED_ECC_LIST);
        if (addNumbers != null && addNumbers.length() > 0) {
            eccAddList = new StringBuilder(addNumbers);
            if(DBG) Log.d(TAG, "add  ecc Numbers " +addNumbers);
            for (String eccNum : addNumbers.split(",")) {

                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("ItemImage", R.drawable.ic_dialog_call);
                map.put("ItemText", eccNum);
                if (!listItem.contains(map)) {
                    listItem.add(map);
                }
            }
        }
        listItemAdapter.notifyDataSetChanged();

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        String number = (String) listItem.get(position).get("ItemText");
        Intent intent = new Intent(this, EmergencyCallItem.class);
            intent.putExtra(EmergencyCallList.NUMBER, number);
            intent.putExtra(EmergencyCallList.POSITION, position);
            //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            //finish();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.ecc_add:
            showDialog(ADD_ECC_NUMBER);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case ADD_ECC_NUMBER:
                LayoutInflater inflater = LayoutInflater.from(this);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                View view = inflater.inflate(R.layout.new_ecc, null);
                final EditText edit = (EditText) view.findViewById(R.id.edit);
                Button okBtn =     (Button) view.findViewById(R.id.button_ok);
                Button cancelBtn = (Button) view.findViewById(R.id.button_cancel);
                okBtn.setOnClickListener(new Button.OnClickListener() {
                    public void onClick(View v) {
                        String number = edit.getText().toString();
                        addEmergencyNumber(number);
                        edit.setText("");
                        mDialog.cancel();
                    }
                });
                cancelBtn.setOnClickListener(new Button.OnClickListener() {
                    public void onClick(View v) {
                        edit.setText("");
                        mDialog.cancel();
                    }
                });

                mDialog = builder.setView(view).create();
                return mDialog;
            default:
        }

        return null;
    }

    private void addEmergencyNumber(String number) {

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("ItemImage", R.drawable.ic_dialog_call);
        map.put("ItemText", number);
        if (!listItem.contains(map)) {
            listItem.add(map);
            listItemAdapter.notifyDataSetChanged();
            if (eccAddList == null) {
                eccAddList = new StringBuilder(number);
            }
            else {
                eccAddList.append(",");
                eccAddList.append(number);
            }
            SystemProperties.set(PROPERTY_ADDED_ECC_LIST,eccAddList.toString());
        }
        else {
            Toast toast = Toast.makeText(this,R.string.emergency_number_exist,Toast.LENGTH_LONG);
            toast.show();
        }

    }


}

