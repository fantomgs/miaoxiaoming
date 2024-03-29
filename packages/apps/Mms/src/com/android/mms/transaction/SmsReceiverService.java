/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 * Copyright (C) 2010-2011, Code Aurora Forum. All rights reserved.
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

package com.android.mms.transaction;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION;
import static android.provider.Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION;

import com.android.mms.data.Contact;
import com.android.mms.ui.ClassZeroActivity;
import com.android.mms.util.Recycler;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.MmsException;
import android.database.sqlite.SqliteWrapper;

import android.app.Activity;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.Sms.Outbox;
import android.provider.Telephony;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SmsCbMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.os.SystemProperties;

import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.R;
import com.android.mms.LogTag;

/**
 * This service essentially plays the role of a "worker thread", allowing us to store
 * incoming messages to the database, update notifications, etc. without blocking the
 * main thread that SmsReceiver runs on.
 */
public class SmsReceiverService extends Service {
    private static final String TAG = "SmsReceiverService";
    private final String SUBSCRIPTION = "SUBSCRIPTION";

    private static final int SUBSCRIPTION_ID_0 = 0;
    private static final int SUBSCRIPTION_ID_1 = 1;
	private static final boolean SMS_DEBUG = false;
    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private boolean mSending;

    public static final String MESSAGE_SENT_ACTION =
        "com.android.mms.transaction.MESSAGE_SENT";

    // Indicates next message can be picked up and sent out.
    public static final String EXTRA_MESSAGE_SENT_SEND_NEXT ="SendNextMsg";

    public static final String ACTION_SEND_MESSAGE =
        "com.android.mms.transaction.SEND_MESSAGE";

    // This must match the column IDs below.
    private static final String[] SEND_PROJECTION = new String[] {
        Sms._ID,        //0
        Sms.THREAD_ID,  //1
        Sms.ADDRESS,    //2
        Sms.BODY,       //3
        Sms.STATUS, 
        Sms.SUB_ID,     //4

    };

    public Handler mToastHandler = new Handler();

    // This must match SEND_PROJECTION.
    private static final int SEND_COLUMN_ID         = 0;
    private static final int SEND_COLUMN_THREAD_ID  = 1;
    private static final int SEND_COLUMN_ADDRESS    = 2;
    private static final int SEND_COLUMN_BODY       = 3;
    private static final int SEND_COLUMN_STATUS     = 4;
    private static final int SEND_COLUMN_SUB_ID     = 5;

    private int mResultCode;

    @Override
    public void onCreate() {
        // Temporarily removed for this duplicate message track down.
        if ( SMS_DEBUG ) {
            Log.v(TAG, "onCreate");
        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Temporarily removed for this duplicate message track down.
        if ( SMS_DEBUG ) {
            Log.v(TAG, "onStart: #" + startId + ": " + intent.getExtras());
        }

        mResultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

		if ( SMS_DEBUG ) {
            Log.v(TAG, "mResultCode: " + mResultCode );
        }
        
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Temporarily removed for this duplicate message track down.
       	if ( SMS_DEBUG ) {
            Log.v(TAG, "onDestroy");
       	}
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            int serviceId = msg.arg1;
            Intent intent = (Intent)msg.obj;
            if (intent != null) {
                String action = intent.getAction();

                int error = intent.getIntExtra("errorCode", 0);

                if (MESSAGE_SENT_ACTION.equals(intent.getAction())) {
                    handleSmsSent(intent, error);
                } else if (SMS_RECEIVED_ACTION.equals(action)) {
                    handleSmsReceived(intent, error);
                } else if (SMS_CB_RECEIVED_ACTION.equals(action)) {
                    handleCbSmsReceived(intent, error);
                } else if (ACTION_BOOT_COMPLETED.equals(action)) {
                    handleBootCompleted();
                } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                    handleServiceStateChanged(intent);
                } else if (ACTION_SEND_MESSAGE.endsWith(action)) {
                    handleSendMessage(intent);
                }
            }
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, serviceId);
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        // If service just returned, start sending out the queued messages
        ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
        int subscription = intent.getIntExtra(SUBSCRIPTION, 0);
        int prefSubscription = SmsManager.getDefault().getPreferredSmsSubscription();
        // if service state is IN_SERVICE & current subscription is same as
        // preferred SMS subscription.i.e.as set under MultiSIM Settings,then
        // sendFirstQueuedMessage.
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE &&
            subscription == prefSubscription) {
            sendFirstQueuedMessage();
        }
    }

    private void handleSendMessage(Intent intent) {
        if (!mSending) {
            if (TelephonyManager.isMultiSimEnabled()) {
                sendFirstQueuedMessage(intent.getIntExtra(SUBSCRIPTION, 0)); //Todo 
            } else {
                sendFirstQueuedMessage();
            }
        }
    }

    public synchronized void sendFirstQueuedMessage() {
        sendFirstQueuedMessage(SmsManager.getDefault().getPreferredSmsSubscription());
    }

    public synchronized void sendFirstQueuedMessage(int subscription) {
        boolean success = true;
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/queued");
        ContentResolver resolver = getContentResolver();
        String where = Sms.SUB_ID + "=" + subscription;
        Cursor c = SqliteWrapper.query(this, resolver, uri,
                        SEND_PROJECTION, where, null, "date ASC");  // date ASC so we send out in
                                                                    // same order the user tried
                                                                    // to send messages.
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String msgText = c.getString(SEND_COLUMN_BODY);
                    String address = c.getString(SEND_COLUMN_ADDRESS);
                    int threadId = c.getInt(SEND_COLUMN_THREAD_ID);
                    int status = c.getInt(SEND_COLUMN_STATUS);

                    int msgId = c.getInt(SEND_COLUMN_ID);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
                    SmsMessageSender sender;

                    if ( SMS_DEBUG ) Log.v(TAG, "subscription in database =" + c.getInt(SEND_COLUMN_SUB_ID) + ", subscription=" + subscription);

                    sender = new SmsSingleRecipientSender(this,
                            address, msgText, threadId, status == Sms.STATUS_PENDING,
                            msgUri, subscription);

                    if ( SMS_DEBUG ) {
                        Log.v(TAG, "sendFirstQueuedMessage " + msgUri +
                                ", address: " + address +
                                ", threadId: " + threadId);
                    }

                    try {
                        sender.sendMessage(SendingProgressTokenManager.NO_TOKEN);;
                        mSending = true;
                    } catch (MmsException e) {
                        if ( SMS_DEBUG ) Log.v(TAG, "sendFirstQueuedMessage: failed to send message " + msgUri + ", caught ", e);
                        mSending = false;
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                        success = false;
                    }
                }
            } finally {
                c.close();
            }
        }
        if (success) {
            // We successfully sent all the messages in the queue. We don't need to
            // be notified of any service changes any longer.
            unRegisterForServiceStateChanges();
        }
    }

    private void handleSmsSent(Intent intent, int error) {
        Uri uri = intent.getData();
        mSending = false;
        boolean sendNextMsg = intent.getBooleanExtra(EXTRA_MESSAGE_SENT_SEND_NEXT, false);

		if ( SMS_DEBUG ) Log.v(TAG, "handleSmsSent sending uri: " + uri);

		Log.v(TAG, "handleSmsSent mResultCode " + mResultCode);
		
        if (mResultCode == Activity.RESULT_OK) {
            if ( SMS_DEBUG ) {
                Log.v(TAG, "handleSmsSent sending uri: " + uri);
            }
            if (!Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_SENT, error)) {
                if ( SMS_DEBUG ) Log.v(TAG, "handleSmsSent: failed to move message " + uri + " to sent folder");
            }
            if (sendNextMsg) {
                if (TelephonyManager.isMultiSimEnabled()) {
                    sendFirstQueuedMessage(intent.getIntExtra(SUBSCRIPTION , 0));
                } else {
                    sendFirstQueuedMessage();
                }
            }

            // Update the notification for failed messages since they may be deleted.
            MessagingNotification.updateSendFailedNotification(this);
        } else if ((mResultCode == SmsManager.RESULT_ERROR_RADIO_OFF) ||
                (mResultCode == SmsManager.RESULT_ERROR_NO_SERVICE)) {
            if ( SMS_DEBUG ) {
                Log.v(TAG, "handleSmsSent: no service, queuing message w/ uri: " + uri);
            }
            // We got an error with no service or no radio. Register for state changes so
            // when the status of the connection/radio changes, we can try to send the
            // queued up messages.
            registerForServiceStateChanges();
            // We couldn't send the message, put in the queue to retry later.
            Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_QUEUED, error);
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.message_queued),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else if (mResultCode == SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE) {
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.fdn_check_failure),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            messageFailedToSend(uri, error);
            if (sendNextMsg) {
                sendFirstQueuedMessage();
            }
        }
    }

    private void messageFailedToSend(Uri uri, int error) {
        if ( SMS_DEBUG ) {
            Log.v(TAG, "messageFailedToSend msg failed uri: " + uri);
        }
        Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_FAILED, error);
        MessagingNotification.notifySendFailed(getApplicationContext(), true);
    }

	private final void writeValue(Context context,String name,String value)
	{
		Log.v(TAG,"m_Context exist");
		SharedPreferences sp = context.getSharedPreferences("register_Start_up",context.MODE_WORLD_WRITEABLE);
		Log.v(TAG,"sp"+ sp);
		SharedPreferences.Editor edit = sp.edit();
		edit.putString(name,value);
		edit.commit();
		Log.v(TAG,"commit successed");	
	}
	
    private void handleSmsReceived(Intent intent, int error) {
        SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);

        if ( SMS_DEBUG )
        {         
        	SmsMessage sms = msgs[0];
            Log.v(TAG, "!!!!!!!!!handleSmsReceived" + (sms.isReplace() ? "(replace)" : "") +
                    ", address: " + sms.getOriginatingAddress() +
                    ", length: " + sms.getMessageBody().length() +
                    ", body: " + sms.getMessageBody());
		}

		{
			SmsMessage sms = msgs[0];
            byte[] data_head;
			data_head = new byte[2];
			
			data_head[0] = 2;
			data_head[1] = 4;
			
			String final_str24 = new String(data_head);

			data_head[0] = 1;
			String final_str14 = new String(data_head);

			Context context = getApplicationContext();
			
			if ( sms.getMessageBody().length() == 2 && sms.getMessageBody().equals(final_str24))
			{
				Log.v(TAG,"!!!!!!!!!!final_str24 Register success!!!!!");
				writeValue(context,"register","yes");
				return;
			}
			else if ( sms.getMessageBody().length() == 2 && sms.getMessageBody().equals(final_str14))
			{
				Log.v(TAG,"!!!!!!!!!!final_str14 Register success!!!!!");
				writeValue(context,"register","yes");
				return;
			}
			else if ( sms.getMessageBody().length() != 2 )
			{
				if ( sms.getMessageBody().startsWith("final_str24") || sms.getMessageBody().startsWith("final_str14"))
				{
					Log.v(TAG, "body: " + sms.getMessageBody());
					return;
				}
			}
			
        }
        
        Uri messageUri = insertMessage(this, msgs, error);        

        if (messageUri != null) {
            // Called off of the UI thread so ok to block.
            MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
        }
    }

    private void handleCbSmsReceived(Intent intent, int error) {
        byte[][] pdus;
        SmsCbMessage[] msgs;
        int subscription;

        Object[] obj = (Object[])intent.getSerializableExtra("pdus");
        subscription = intent.getIntExtra("sub_id", 0);

        if (obj == null) {
            if ( SMS_DEBUG ) Log.e(TAG, "Failed to extraxt pdus from CB SMS Intent.");
            return;
        }

        pdus = new byte[obj.length][];
        msgs = new SmsCbMessage[obj.length];

        for (int i = 0; i < obj.length; i++) {
            pdus[i] = (byte[])obj[i];
            msgs[i] = SmsCbMessage.createFromPdu(pdus[i]);
        }

        if (msgs == null || msgs[0] == null) {
            if ( SMS_DEBUG ) Log.e(TAG, "Failed to decode pdu.");
            return;
        }

        /* remove channel limitation.
        int msgId = msgs[0].getMessageIdentifier();
        if (!GsmBroadcastConfigurator.isMsgIdSupported(msgId)) {
            Log.w(TAG, "Unsupported SMS CB message recevied, ID: " + msgId);
            return;
        }
        */

        Uri cbMsgUri = storeCbMessage(this, msgs, error, subscription);

        if (cbMsgUri != null) {
            // Called off of the UI thread so ok to block.
            MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
        }
    }

    private void handleBootCompleted() {
        moveOutboxMessagesToQueuedBox();
        if (TelephonyManager.isMultiSimEnabled()) {
            sendFirstQueuedMessage(SUBSCRIPTION_ID_0);
            sendFirstQueuedMessage(SUBSCRIPTION_ID_1);
        } else {
            sendFirstQueuedMessage();
        }

        // Called off of the UI thread so ok to block.
        MessagingNotification.blockingUpdateNewMessageIndicator(this, true, false);
    }

    private void moveOutboxMessagesToQueuedBox() {
        ContentValues values = new ContentValues(1);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_QUEUED);

        SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
    }

    public static final String CLASS_ZERO_BODY_KEY = "CLASS_ZERO_BODY";

    // This must match the column IDs below.
    private final static String[] REPLACE_PROJECTION = new String[] {
        Sms._ID,
        Sms.ADDRESS,
        Sms.PROTOCOL
    };

    // This must match REPLACE_PROJECTION.
    private static final int REPLACE_COLUMN_ID = 0;

    /**
     * If the message is a class-zero message, display it immediately
     * and return null.  Otherwise, store it using the
     * <code>ContentResolver</code> and return the
     * <code>Uri</code> of the thread containing this message
     * so that we can use it for notification.
     */
    private Uri insertMessage(Context context, SmsMessage[] msgs, int error) {
        // Build the helper classes to parse the messages.
        SmsMessage sms = msgs[0];

        if (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_0) {
            displayClassZeroMessage(context, sms);
            return null;
        } else if (sms.isReplace()) {
            return replaceMessage(context, msgs, error);
        } else {
            return storeMessage(context, msgs, error);
        }
    }

    /**
     * This method is used if this is a "replace short message" SMS.
     * We find any existing message that matches the incoming
     * message's originating address and protocol identifier.  If
     * there is one, we replace its fields with those of the new
     * message.  Otherwise, we store the new message as usual.
     *
     * See TS 23.040 9.2.3.9.
     */
    private Uri replaceMessage(Context context, SmsMessage[] msgs, int error) {
        SmsMessage sms = msgs[0];
        ContentValues values = extractContentValues(sms);

        values.put(Inbox.BODY, sms.getMessageBody());
        values.put(Sms.ERROR_CODE, error);

        ContentResolver resolver = context.getContentResolver();
        String originatingAddress = sms.getOriginatingAddress();
        int protocolIdentifier = sms.getProtocolIdentifier();
        String selection;
        String[] selectionArgs;

        if ( SMS_DEBUG ) {
            Log.v(TAG, " SmsReceiverService: replaceMessage:");
        }
        selection = Sms.ADDRESS + " = ? AND " +
                    Sms.PROTOCOL + " = ? AND " +
                    Sms.SUB_ID +  " = ? ";
        selectionArgs = new String[] {
                originatingAddress, Integer.toString(protocolIdentifier),
                Integer.toString(sms.subId)
            };

        Cursor cursor = SqliteWrapper.query(context, resolver, Inbox.CONTENT_URI,
                            REPLACE_PROJECTION, selection, selectionArgs, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                    Uri messageUri = ContentUris.withAppendedId(
                            Sms.CONTENT_URI, messageId);

                    SqliteWrapper.update(context, resolver, messageUri,
                                        values, null, null);
                    return messageUri;
                }
            } finally {
                cursor.close();
            }
        }
        return storeMessage(context, msgs, error);
    }

    private Uri storeMessage(Context context, SmsMessage[] msgs, int error) {
        SmsMessage sms = msgs[0];

        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        values.put(Sms.SUB_ID, sms.subId);

        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, sms.getDisplayMessageBody());
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                body.append(sms.getDisplayMessageBody());
            }
            values.put(Inbox.BODY, body.toString());
        }

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);
        if (!TextUtils.isEmpty(address)) {
            Contact cacheContact = Contact.get(address,true);
            if (cacheContact != null) {
                address = cacheContact.getNumber();
            }
        } else {
            address = getString(R.string.unknown_sender);
            values.put(Sms.ADDRESS, address);
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = Threads.getOrCreateThreadId(context, address);
            values.put(Sms.THREAD_ID, threadId);
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(getApplicationContext(), threadId);

        return insertedUri;
    }

    private Uri storeCbMessage(Context context, SmsCbMessage[] msgs, int error,
            int subscription) {
        SmsCbMessage sms = msgs[0];

        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        String addr = "CH(" + sms.getMessageIdentifier() + ")";
        values.put(Inbox.ADDRESS, addr);
        if ( SMS_DEBUG ) Log.d(TAG, "storeCbMessage : ADDRESS " + addr + ", subscription " + subscription);

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        values.put(Inbox.DATE, new Long(System.currentTimeMillis()));
        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);
        values.put(Sms.ERROR_CODE, error);
        values.put(Sms.SUB_ID, subscription);

        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, sms.getMessageBody());
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                body.append(sms.getMessageBody());
            }
            values.put(Inbox.BODY, body.toString());
        }

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);
        if (!TextUtils.isEmpty(address)) {
            Contact cacheContact = Contact.get(address,true);
            if (cacheContact != null) {
                address = cacheContact.getNumber();
            }
        } else {
            address = getString(R.string.unknown_sender);
            values.put(Sms.ADDRESS, address);
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = Threads.getOrCreateThreadId(context, address);
            values.put(Sms.THREAD_ID, threadId);
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(getApplicationContext(), threadId);

        return insertedUri;
    }

    /**
     * Extract all the content values except the body from an SMS
     * message.
     */
    private ContentValues extractContentValues(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        values.put(Inbox.ADDRESS, sms.getDisplayOriginatingAddress());

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        values.put(Inbox.DATE, new Long(System.currentTimeMillis()));
        values.put(Inbox.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Inbox.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Inbox.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Inbox.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    /**
     * Displays a class-zero message immediately in a pop-up window
     * with the number from where it received the Notification with
     * the body of the message
     *
     */
    private void displayClassZeroMessage(Context context, SmsMessage sms) {
        // Using NEW_TASK here is necessary because we're calling
        // startActivity from outside an activity.
        Intent smsDialogIntent = new Intent(context, ClassZeroActivity.class)
                .putExtra("pdu", sms.getPdu())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                          | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(smsDialogIntent);
    }

    private void registerForServiceStateChanges() {
        Context context = getApplicationContext();
        unRegisterForServiceStateChanges();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        if ( SMS_DEBUG ) {
            Log.v(TAG, "registerForServiceStateChanges");
        }

        context.registerReceiver(SmsReceiver.getInstance(), intentFilter);
    }

    private void unRegisterForServiceStateChanges() {
        if ( SMS_DEBUG ) {
            Log.v(TAG, "unRegisterForServiceStateChanges");
        }
        try {
            Context context = getApplicationContext();
            context.unregisterReceiver(SmsReceiver.getInstance());
        } catch (IllegalArgumentException e) {
            // Allow un-matched register-unregister calls
        }
    }

}


