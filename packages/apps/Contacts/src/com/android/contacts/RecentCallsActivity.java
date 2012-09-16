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
// HEXING123 MODIFY
package com.android.contacts;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.CallLog.Calls;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TabHost.OnTabChangeListener;
import android.os.SystemProperties;
//add by yangdecai 2012-07-12

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;
import android.os.RemoteException;
import android.os.ServiceManager;


public class RecentCallsActivity extends TabActivity implements OnCheckedChangeListener,
        OnClickListener, OnTabChangeListener {

    private static final String TAG = "RecentCallsActivity";

    private static final boolean DEBUG = true;

    private TabHost mTabHost;

    private String allCallTag;

    private String incomingCallTag;

    private String outgoingCallTag;

    private String missCallTag;

    private ImageView slotList;

    private ImageView slotSelect;

    static int currentSlot = -1;

    private RadioButton allSelect;

    private RadioButton inSelect;

    private RadioButton outSelect;

    private RadioButton missSelect;

    private RadioButton[] selects;

    private String[] tags;

    private static final int DIALOG_SLOT_LIST = 1;

    static final String ACTION_SLOT_CHANGE = "com.qualcomm.slot.change";

    protected void onCreate(Bundle state) {
        super.onCreate(state);

        if (DEBUG)
            Log.d(TAG, "create call log tab");

        setContentView(R.layout.call_log_tab);

        allCallTag = getString(R.string.tab_call_log_all);
        incomingCallTag = getString(R.string.tab_call_log_in);
        outgoingCallTag = getString(R.string.tab_call_log_out);
        missCallTag = getString(R.string.tab_call_log_miss);
        tags = new String[] {
                allCallTag, incomingCallTag, outgoingCallTag, missCallTag
        };

        slotList = (ImageView) findViewById(R.id.slot_list);
        slotSelect = (ImageView) findViewById(R.id.slot_select);
        slotList.setOnTouchListener(touchListener);
        slotSelect.setOnTouchListener(touchListener);
        slotList.setOnClickListener(this);
        slotSelect.setOnClickListener(this);

        allSelect = (RadioButton) findViewById(R.id.call_all);
        inSelect = (RadioButton) findViewById(R.id.call_in);
        outSelect = (RadioButton) findViewById(R.id.call_out);
        missSelect = (RadioButton) findViewById(R.id.call_miss);
        selects = new RadioButton[] {
                allSelect, inSelect, outSelect, missSelect
        };

        allSelect.setOnCheckedChangeListener(this);
        inSelect.setOnCheckedChangeListener(this);
        outSelect.setOnCheckedChangeListener(this);
        missSelect.setOnCheckedChangeListener(this);

        mTabHost = getTabHost();
        mTabHost.setOnTabChangedListener(this);

        addAllCallTab();
        addIncomingCallTab();
        addOutgoingCallTab();
        addMissCallTab();

        registerReceiver(slotChangeReceiver, new IntentFilter(ACTION_SLOT_CHANGE));
    }

    private BroadcastReceiver slotChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SLOT_CHANGE.equals(intent.getAction())) {
                updateSlotStaus();
            }
        }
    };

    protected void onResume() {
        super.onResume();
        currentSlot = getSlot();
        Intent intent = new Intent(ACTION_SLOT_CHANGE);
        RecentCallsActivity.this.sendBroadcast(intent);
    }

    private void updateSlotStaus() {
        int slot = RecentCallsActivity.currentSlot;
        if (slotList != null)
            switch (slot) {
                case 0:
                    slotList.setImageResource(R.drawable.ic_tab_sim1);
                    break;
                case 1:
                    slotList.setImageResource(R.drawable.ic_tab_sim2);
                    break;
                case -1:
                    slotList.setImageResource(R.drawable.ic_tab_sim12);
                    break;
            }

        if (TelephonyManager.getPhoneCount() < 2) {
            slotList.setEnabled(false);
            slotSelect.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(slotChangeReceiver);
    }

    private void addAllCallTab() {
        mTabHost.addTab(buildTabSpec(allCallTag, allCallTag,
                R.drawable.ic_call_log_list_incoming_call, getCallLogIntent(0)));
    }

    private void addIncomingCallTab() {
        mTabHost.addTab(buildTabSpec(incomingCallTag, incomingCallTag,
                R.drawable.ic_call_log_list_incoming_call, getCallLogIntent(Calls.INCOMING_TYPE)));
    }

    private void addOutgoingCallTab() {
        mTabHost.addTab(buildTabSpec(outgoingCallTag, outgoingCallTag,
                R.drawable.ic_call_log_list_outgoing_call, getCallLogIntent(Calls.OUTGOING_TYPE)));
    }

    private void addMissCallTab() {
        mTabHost.addTab(buildTabSpec(missCallTag, missCallTag,
                R.drawable.ic_call_log_list_missed_call, getCallLogIntent(Calls.MISSED_TYPE)));
    }

    private TabHost.TabSpec buildTabSpec(String tag, String label, int resIcon, final Intent intent) {
        return mTabHost.newTabSpec(tag).setIndicator(label, getResources().getDrawable(resIcon))
                .setContent(intent);
    }

    private Intent getCallLogIntent(int type) {
        Intent intent = new Intent(this, RecentCallsListActivity.class);
        intent.putExtra(RecentCallsListActivity.EXTRA_QUERY_TYPE, type);
        intent.putExtra("Subscription", currentSlot);
        return intent;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            switch (buttonView.getId()) {
                case R.id.call_all:
                    mTabHost.setCurrentTabByTag(allCallTag);
                    break;
                case R.id.call_in:
                    mTabHost.setCurrentTabByTag(incomingCallTag);
                    break;
                case R.id.call_out:
                    mTabHost.setCurrentTabByTag(outgoingCallTag);
                    break;
                case R.id.call_miss:
                    mTabHost.setCurrentTabByTag(missCallTag);
                    break;
            }
        }
    }

	//add by yangdecai 2012-07-12
	
	public void onWindowFocusChanged(boolean hasFocus) {
		   super.onWindowFocusChanged(hasFocus);
	
		   // Clear notifications only when window gains focus.  This activity won't
		   // immediately receive focus if the keyguard screen is above it.
		   if (hasFocus) {
			   try {
				   ITelephony iTelephony =
						   ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
				   if (iTelephony != null) {
					   iTelephony.cancelMissedCallsNotification();
				   } else {
					   Log.w(TAG, "Telephony service is null, can't call " +
							   "cancelMissedCallsNotification");
				   }
			   } catch (RemoteException e) {
				   Log.e(TAG, "Failed to clear missed calls notification due to remote exception");
			   }
		   }
	   }

    private OnTouchListener touchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    slotSelect.setImageResource(R.drawable.ic_tab_sim_select_touch);
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    slotSelect.setImageResource(R.drawable.ic_tab_sim_select);
                    break;
            }
            return false;
        }
    };

    @Override
    public void onClick(View v) {
        if (slotList == v || slotSelect == v) {
            showDialog(DIALOG_SLOT_LIST);
        }
    }

    private DialogInterface.OnClickListener slotListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which >= TelephonyManager.getPhoneCount())
                currentSlot = -1;
            else
                currentSlot = which;
            saveSlot(currentSlot);
            Intent intent = new Intent(ACTION_SLOT_CHANGE);
            RecentCallsActivity.this.sendBroadcast(intent);
            dialog.dismiss();
        }
    };

    private void saveSlot(int slot) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putInt("Subscription", slot);
        edit.commit();
    }

    private int getSlot() {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt("Subscription", -1);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_SLOT_LIST:
                ((MultiSlotAdapter) ((AlertDialog) dialog).getListView().getAdapter()).initSlots();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_SLOT_LIST:
				{
					if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
					{
						return new AlertDialog.Builder(this).setSingleChoiceItems(
                        	new MultiSlotAdapter(this), 0, slotListener).setTitle(
                        	R.string.title_slot_change_uim).create();
					}
					else
					{
                		return new AlertDialog.Builder(this).setSingleChoiceItems(
                        	new MultiSlotAdapter(this), 0, slotListener).setTitle(
                        	R.string.title_slot_change).create();
					}
            	}
        }
        return null;
    }

    public class MultiSlotAdapter extends BaseAdapter {

        private Context mContext;

        private int[] icons = new int[] {
                R.drawable.ic_tab_sim1, R.drawable.ic_tab_sim2, R.drawable.ic_tab_sim12
        };

        private String[] slots = new String[3];

        public MultiSlotAdapter(Context context) {
            mContext = context;
            slots[2] = mContext.getString(R.string.all_call_log);
            initSlots();
        }

        private void initSlots() {
            slots[0] = getMultiSimName(0);
            slots[1] = getMultiSimName(1);
        }

        private String getMultiSimName(int subscription) {
            return Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.MULTI_SIM_NAME[subscription]);
        }

        public int getCount() {
            return slots.length;
        }

        public Object getItem(int position) {
            return slots[position];
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null)
                view = LayoutInflater.from(mContext).inflate(R.layout.item_slot, null);
            ImageView icon = (ImageView) view.findViewById(R.id.icon);
            icon.setImageResource(icons[position]);
            TextView msg = (TextView) view.findViewById(R.id.msg);
            msg.setText(slots[position]);
            return view;
        }
    }

    @Override
    public void onTabChanged(String tabId) {
        for (int index = 0; index < tags.length; index++) {
            if (tabId.equals(tags[index])) {
                selects[index].setBackgroundResource(R.drawable.call_log_bottom_selcet);
            } else {
                selects[index].setBackgroundResource(0);
            }
        }

    }

}
