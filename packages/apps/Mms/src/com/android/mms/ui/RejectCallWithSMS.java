/*
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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


package com.android.mms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.mms.R;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/*
 * Acitivity for Reject Call With SMS function.
 */
public class RejectCallWithSMS extends Activity implements ListView.OnItemClickListener,
    TextView.OnEditorActionListener{
    
    final static boolean DBG = true;
    final static String TAG = "RejectCallWithSMS";

    final static String strSmsTempUri = 
            "content://com.android.mms.MessageTemplateProvider/messages";
    
    final static String ACTION_REJECTCALL_SMS = "com.android.phone.ACTION_REJECTCALL_SMS";
    // for ASCII character, single SMS' length can be 160
    final static int ASCII_SMS_LENGTH = 160;
    // for multi-byte character, single SMS' length can be 70
    final static int MULTIBYTE_SMS_LENGTH = 70;
    
    private ListView mListView;
    private EditText messageToSend;
    private Button   mSendButton;
    private TextView mCounter;
    private ArrayList<String> mMessages = new ArrayList<String>();
    
    private String mPhoneNumber = null;
    private int mSubscription = -1;
    
    private SmsManager sms = null;
    
    
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = null;
            switch (getResultCode()) {
            case Activity.RESULT_OK:
                message = RejectCallWithSMS.this.getString(R.string.send_sms_ok);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                message = RejectCallWithSMS.this.getString(R.string.send_sms_generic_err);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                message = RejectCallWithSMS.this.getString(R.string.send_sms_no_service);
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                message = RejectCallWithSMS.this.getString(R.string.send_sms_nul_pdu);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                message = RejectCallWithSMS.this.getString(R.string.send_sms_radio_off);
                break;
            }
            if (DBG)
                Log.d(TAG,message);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            finish();
        }
        
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.reject_call_with_sms);
        mListView = (ListView) findViewById(R.id.listViewContent);
        messageToSend = (EditText)findViewById(R.id.embedded_text_editor);
        messageToSend.setOnEditorActionListener(this);
        messageToSend.addTextChangedListener(mTextEditorWatcher);
        messageToSend.setFilters(new InputFilter [] {inputFilter});

        mSendButton = (Button)findViewById(R.id.send_button);
        mSendButton.setOnClickListener(mSendButtonListener);
        mCounter = (TextView)findViewById(R.id.text_counter);
        mCounter.setVisibility(View.GONE);

        registerReceiver(receiver, new IntentFilter(ACTION_REJECTCALL_SMS));
    }

    @Override
    public void onResume() {
        super.onResume();

        mPhoneNumber = getIntent().getStringExtra("IncallPhoneNumber");
        mSubscription = getIntent().getIntExtra("Subsciption", -1);

        if ( QueryMessateTemplate()) {
            ViewAdapter adapter = new ViewAdapter(this);
            mListView.setAdapter(adapter);
        } else {
            if (DBG)
                Log.d(TAG, "Query Message Template failed! cursor is null!");
        }
        mListView.setOnItemClickListener(this);
        
        sms = SmsManager.getDefault();
    }
    
    @Override
    public void onStop() {
        super.onStop();    
    };
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(receiver);
    }
    

    @Override
    protected void onNewIntent(Intent intent) {
       super.onNewIntent(intent);
       setIntent(intent);
       mPhoneNumber = getIntent().getStringExtra("IncallPhoneNumber");
       mSubscription = getIntent().getIntExtra("Subsciption", -1);
    }
    
    private OnClickListener mSendButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            // hide virtual keyboard
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

            if (mPhoneNumber == null) {
                String info = RejectCallWithSMS.this.getString(
                    R.string.send_sms_no_incoming_number);
                Toast.makeText(RejectCallWithSMS.this, info, Toast.LENGTH_SHORT).show();
                finish();
           }
            String message = messageToSend.getText().toString();
            if (message.trim().length()==0)
                return;
            sendTextMessage(message);           
            moveTaskToBack(true);            
        }      
    };
    
    private boolean QueryMessateTemplate() {
        mMessages.clear();
        Uri uri = Uri.parse(strSmsTempUri);
        Cursor cur = managedQuery(uri, null, null, null, null);
        if (cur == null)
            return false;
        if (cur.moveToFirst()) {
            do {
                int index = cur.getColumnIndex("message");
                String strMessage = cur.getString(index);
                mMessages.add(strMessage);
            } while (cur.moveToNext());
        }       
        return true;       
    }    
    
    static final class ViewData {
        public TextView  message;
        public ImageView actionSend;
    } 
    
    final class ViewAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private Context mContext;
        
        public ViewAdapter(Context context) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mMessages.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewData viewData = null;
            if (convertView != null) {
                viewData = (ViewData)convertView.getTag();
            } else {
                viewData = new ViewData();
                convertView = mInflater.inflate(R.layout.reject_call_with_sms_listitem, null);
                viewData.message = (TextView)convertView.findViewById(R.id.message_content);
                viewData.actionSend = (ImageView)convertView.findViewById(R.id.img_send);
                convertView.setTag(viewData);
            }
            
            Drawable drawable = mContext.getResources().getDrawable(R.drawable.sym_action_sms);
            viewData.actionSend.setImageDrawable(drawable);
            viewData.message.setText(mMessages.get(position));
            viewData.actionSend.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (mPhoneNumber == null) {
                        String info = RejectCallWithSMS.this.getString(
                            R.string.send_sms_no_incoming_number);
                        Toast.makeText(mContext, info, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        // send message directly here
                        int index = 0;
                        String message = null;
                        for (; index < mListView.getChildCount(); index++) {
                            if (mListView.getChildAt(index).findViewById(R.id.img_send) == v) {
                                TextView tv = (TextView)(mListView.getChildAt(index).
                                    findViewById(R.id.message_content));
                                message = (String)tv.getText();
                                break;
                            }
                        }
                        
                        if (message == null) {
                            // something wrong
                            Log.e(TAG,"Error: not match message found!");
                            Toast.makeText(RejectCallWithSMS.this, R.string.send_sms_generic_err,
                                    Toast.LENGTH_SHORT).show();
                            finish();
                        }


                        if (message.trim().length()==0)
                            return;
                        sendTextMessage(message);                  
                        moveTaskToBack(true);
                    }
                }              
            });
            return convertView;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {     
        int index = messageToSend.getSelectionStart();
        messageToSend.getText().insert(index,mMessages.get(arg2));
    }
    
    private void sendTextMessage(String message) {
        try {
            int[] params = SmsMessage.calculateLength(message, false);
            int maxL = (params[3]==SmsMessage.ENCODING_7BIT) ? ASCII_SMS_LENGTH : MULTIBYTE_SMS_LENGTH;
            if (message.length() > maxL) {
                message = message.substring(0, maxL);
            }
            if (mSubscription!=-1) {
                sms.sendTextMessage(mPhoneNumber, null, message, PendingIntent.getBroadcast(
                        RejectCallWithSMS.this, 0, new Intent(ACTION_REJECTCALL_SMS), 0),
                        null,mSubscription);
            } else {
                sms.sendTextMessage(mPhoneNumber, null, message, PendingIntent.getBroadcast(
                        RejectCallWithSMS.this, 0, new Intent(ACTION_REJECTCALL_SMS), 0),
                        null);
            }
        } catch (Exception e) {
            Log.e(TAG, "sendTextMessage exception:" + e.toString());
            e.printStackTrace();
            finish();
        }

    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        // hide virtual keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

        if (mPhoneNumber == null) {
            String info = RejectCallWithSMS.this.getString(
                R.string.send_sms_no_incoming_number);
            Toast.makeText(this, info, Toast.LENGTH_SHORT).show();
            finish();
       }

        String message = messageToSend.getText().toString();
        if (message.trim().length()==0)
            return true;

        if (event != null) {
            // if shift key is down, then we want to insert the '\n' char in the TextView;
            // otherwise, the default action is to send the message.
            if (!event.isShiftPressed() && event.getAction() == KeyEvent.ACTION_DOWN) {
                sendTextMessage(message);
                moveTaskToBack(true);
                return true;
            }
            return false;
        }

        sendTextMessage(message);
        moveTaskToBack(true);
        return true;

    }

    private int maxLength = 0;

    private InputFilter inputFilter = new InputFilter() {

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            try {
                CharSequence s = messageToSend.getText().toString();
                int[] paramsSrc = SmsMessage.calculateLength(s, false);
                int[] paramsToadd = SmsMessage.calculateLength(source.subSequence(start, end), false);
                maxLength = (paramsToadd[3]==SmsMessage.ENCODING_16BIT)? MULTIBYTE_SMS_LENGTH :
                    (paramsSrc[3]==SmsMessage.ENCODING_16BIT? MULTIBYTE_SMS_LENGTH : ASCII_SMS_LENGTH);
                CharSequence ss = source.subSequence(start, end);
                if (s.length()+ss.length() <= maxLength)
                    return null;
                int left = maxLength - s.length();
                if (left < 0 )
                    return "";
                return source.subSequence(start, start+left);
            } catch (Exception e) {
                Log.e(TAG,e.toString());
                return null;
            }
        }

    };

    private final TextWatcher mTextEditorWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable s) {
            // TODO Auto-generated method stub

        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                int after) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                int count) {
            int[] params = SmsMessage.calculateLength(s, false);
            if (s.length() <= maxLength ) {
                mCounter.setText(String.valueOf(params[2]));
                mCounter.setVisibility(messageToSend.getLineCount()>1 ?
                        View.VISIBLE : View.GONE);
            }
        }

    };
}
