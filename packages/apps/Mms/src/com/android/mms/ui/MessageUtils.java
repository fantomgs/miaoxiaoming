/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2010-2012, Code Aurora Forum. All rights reserved.
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
package com.android.mms.ui;

import static android.telephony.SmsMessage.ENCODING_7BIT;
import static android.telephony.SmsMessage.ENCODING_8BIT;
import static android.telephony.SmsMessage.ENCODING_16BIT;
import static android.telephony.SmsMessage.ENCODING_UNKNOWN;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES_WITH_HEADER;
import static android.telephony.SmsMessage.MAX_USER_DATA_SEPTETS;

import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.SmsHeader;

import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.LogTag;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.util.AddressUtils;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.pdu.SendReq;
import android.database.sqlite.SqliteWrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.URLSpan;
import android.util.Log;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;

/**
 * An utility class for managing messages.
 */
public class MessageUtils {
    interface ResizeImageResultCallback {
        void onResizeResult(PduPart part, boolean append);
    }

    private static final int RIL_CDMA_SMS_ADDRESS_MAX    =  36;
    private static final int RIL_CDMA_SMS_SUBADDRESS_MAX   = 36;
    private static final int RIL_CDMA_SMS_BEARER_DATA_MAX  = 255;

    private static final int TIMESTAMP_LENGTH = 7;  // See TS 23.040 9.2.3.11
    private static final String TAG = LogTag.TAG;
    private static String sLocalNumber;

    private static final String[] ID_PROJECTION = { BaseColumns._ID };

    // Cache of both groups of space-separated ids to their full
    // comma-separated display names, as well as individual ids to
    // display names.
    // TODO: is it possible for canonical address ID keys to be
    // re-used?  SQLite does reuse IDs on NULL id_ insert, but does
    // anything ever delete from the mmssms.db canonical_addresses
    // table?  Nothing that I could find.
    private static final Map<String, String> sRecipientAddress =
            new ConcurrentHashMap<String, String>(20 /* initial capacity */);


    /**
     * MMS address parsing data structures
     */
    // allowable phone number separators
    private static final char[] NUMERIC_CHARS_SUGAR = {
        '-', '.', ',', '(', ')', ' ', '/', '\\', '*', '#', '+'
    };

    private static HashMap numericSugarMap = new HashMap (NUMERIC_CHARS_SUGAR.length);

    public static final int ALL_RECIPIENTS_INVALID = -1;
    public static final int ALL_RECIPIENTS_VALID = 0;

    static {
        for (int i = 0; i < NUMERIC_CHARS_SUGAR.length; i++) {
            numericSugarMap.put(NUMERIC_CHARS_SUGAR[i], NUMERIC_CHARS_SUGAR[i]);
        }
    }


    private MessageUtils() {
        // Forbidden being instantiated.
    }

    public static String getMessageDetails(Context context, Cursor cursor, int size) {
        if (cursor == null) {
            return null;
        }

        if ("mms".equals(cursor.getString(MessageListAdapter.COLUMN_MSG_TYPE))) {
            int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
            switch (type) {
                case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                    return getNotificationIndDetails(context, cursor);
                case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                    return getMultimediaMessageDetails(context, cursor, size);
                default:
                    Log.w(TAG, "No details could be retrieved.");
                    return "";
            }
        } else {
            return getTextMessageDetails(context, cursor);
        }
    }

    private static String getNotificationIndDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        NotificationInd nInd;

        try {
            nInd = (NotificationInd) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Mms Notification.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_notification));

        // From: ***
        String from = extractEncStr(context, nInd.getFrom());
        details.append('\n');
        details.append(res.getString(R.string.from_label));
        details.append(!TextUtils.isEmpty(from)? from:
                                 res.getString(R.string.hidden_sender_address));

        // Date: ***
        details.append('\n');
        details.append(res.getString(
                                R.string.expire_on,
                                MessageUtils.formatTimeStampString(
                                        context, nInd.getExpiry() * 1000L, true)));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = nInd.getSubject();
        if (subject != null) {
            details.append(subject.getString());
        }

        // Message class: Personal/Advertisement/Infomational/Auto
        details.append('\n');
        details.append(res.getString(R.string.message_class_label));
        details.append(new String(nInd.getMessageClass()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(String.valueOf((nInd.getMessageSize() + 1023) / 1024));
        details.append(context.getString(R.string.kilobyte));

        return details.toString();
    }

    private static String getMultimediaMessageDetails(
            Context context, Cursor cursor, int size) {
        int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
        if (type == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            return getNotificationIndDetails(context, cursor);
        }

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        MultimediaMessagePdu msg;

        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_message));

        if (msg instanceof RetrieveConf) {
            // From: ***
            String from = extractEncStr(context, ((RetrieveConf) msg).getFrom());
            details.append('\n');
            details.append(res.getString(R.string.from_label));
            details.append(!TextUtils.isEmpty(from)? from:
                                  res.getString(R.string.hidden_sender_address));
        }

        // To: ***
        details.append('\n');
        details.append(res.getString(R.string.to_address_label));
        EncodedStringValue[] to = msg.getTo();
        if (to != null) {
            details.append(EncodedStringValue.concat(to));
        }
        else {
            Log.w(TAG, "recipient list is empty!");
        }


        // Bcc: ***
        if (msg instanceof SendReq) {
            EncodedStringValue[] values = ((SendReq) msg).getBcc();
            if ((values != null) && (values.length > 0)) {
                details.append('\n');
                details.append(res.getString(R.string.bcc_label));
                details.append(EncodedStringValue.concat(values));
            }
        }

        // Date: ***
        details.append('\n');
        int msgBox = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_BOX);
        if (msgBox == Mms.MESSAGE_BOX_DRAFTS) {
            details.append(res.getString(R.string.saved_label));
        } else if (msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        details.append(MessageUtils.formatTimeStampString(
                context, msg.getDate() * 1000L, true));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = msg.getSubject();
        if (subject != null) {
            String subStr = subject.getString();
            // Message size should include size of subject.
            size += subStr.length();
            details.append(subStr);
        }

        // Priority: High/Normal/Low
        details.append('\n');
        details.append(res.getString(R.string.priority_label));
        details.append(getPriorityDescription(context, msg.getPriority()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append((size - 1)/1000 + 1);
        details.append(" KB");

        return details.toString();
    }

    private static String getTextMessageDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.text_message));

        // Address: ***
        details.append('\n');
        int smsType = cursor.getInt(MessageListAdapter.COLUMN_SMS_TYPE);
        if (Sms.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        } else {
            details.append(res.getString(R.string.from_label));
        }
        details.append(cursor.getString(MessageListAdapter.COLUMN_SMS_ADDRESS));

        // Date: ***
        details.append('\n');
        if (smsType == Sms.MESSAGE_TYPE_DRAFT) {
            details.append(res.getString(R.string.saved_label));
        } else if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        long date = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE);
        details.append(MessageUtils.formatTimeStampString(context, date, true));

        // Error code: ***
        int errorCode = cursor.getInt(MessageListAdapter.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }

        return details.toString();
    }

    static private String getPriorityDescription(Context context, int PriorityValue) {
        Resources res = context.getResources();
        switch(PriorityValue) {
            case PduHeaders.PRIORITY_HIGH:
                return res.getString(R.string.priority_high);
            case PduHeaders.PRIORITY_LOW:
                return res.getString(R.string.priority_low);
            case PduHeaders.PRIORITY_NORMAL:
            default:
                return res.getString(R.string.priority_normal);
        }
    }

    public static int getAttachmentType(SlideshowModel model) {
        if (model == null) {
            return WorkingMessage.TEXT;
        }

        int numberOfSlides = model.size();

        if (numberOfSlides > 2) {
            return WorkingMessage.SLIDESHOW;
        } else if (numberOfSlides > 1) {
            SlideModel slide = model.get(1);
            if (slide.isEmpty()) {
                model.remove(1);
            } else {
                return WorkingMessage.SLIDESHOW;
            }
        }
        if (numberOfSlides == 1 || numberOfSlides == 2) {
            // Only one slide in the slide-show.
            SlideModel slide = model.get(0);
            if (slide.hasVideo()) {
                return WorkingMessage.VIDEO;
            }

            if (slide.hasAudio() && slide.hasImage()) {
                return WorkingMessage.SLIDESHOW;
            }

            if (slide.hasAudio()) {
                return WorkingMessage.AUDIO;
            }

            if (slide.hasImage()) {
                return WorkingMessage.IMAGE;
            }

            if (slide.hasText()) {
                return WorkingMessage.TEXT;
            }
        }

        return WorkingMessage.TEXT;
    }

    public static String formatTimeStampString(Context context, long when) {
        return formatTimeStampString(context, when, false);
    }

    public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                           DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        } else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        // If the caller has asked for full details, make sure to show the date
        // and time no matter what we've determined above (but still make showing
        // the year only happen if it is a different year from today).
        if (fullFormat) {
            format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }

        return DateUtils.formatDateTime(context, when, format_flags);
    }

    /**
     * @parameter recipientIds space-separated list of ids
     */
    public static String getRecipientsByIds(Context context, String recipientIds,
                                            boolean allowQuery) {
        String value = sRecipientAddress.get(recipientIds);
        if (value != null) {
            return value;
        }
        if (!TextUtils.isEmpty(recipientIds)) {
            StringBuilder addressBuf = extractIdsToAddresses(
                    context, recipientIds, allowQuery);
            if (addressBuf == null) {
                // temporary error?  Don't memoize.
                return "";
            }
            value = addressBuf.toString();
        } else {
            value = "";
        }
        sRecipientAddress.put(recipientIds, value);
        return value;
    }

    private static StringBuilder extractIdsToAddresses(Context context, String recipients,
                                                       boolean allowQuery) {
        StringBuilder addressBuf = new StringBuilder();
        String[] recipientIds = recipients.split(" ");
        boolean firstItem = true;
        for (String recipientId : recipientIds) {
            String value = sRecipientAddress.get(recipientId);

            if (value == null) {
                if (!allowQuery) {
                    // when allowQuery is false, if any value from sRecipientAddress.get() is null,
                    // return null for the whole thing. We don't want to stick partial result
                    // into sRecipientAddress for multiple recipient ids.
                    return null;
                }

                Uri uri = Uri.parse("content://mms-sms/canonical-address/" + recipientId);
                Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                                               uri, null, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            value = c.getString(0);
                            sRecipientAddress.put(recipientId, value);
                        }
                    } finally {
                        c.close();
                    }
                }
            }
            if (value == null) {
                continue;
            }
            if (firstItem) {
                firstItem = false;
            } else {
                addressBuf.append(";");
            }
            addressBuf.append(value);
        }

        return (addressBuf.length() == 0) ? null : addressBuf;
    }

    public static void selectAudio(Context context, int requestCode) {
        if (context instanceof Activity) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE_MUSIC);// HX ADD
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                    context.getString(R.string.select_audio));
            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

    public static void recordSound(Context context, int requestCode) {
        if (context instanceof Activity) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(ContentType.AUDIO_AMR);
            intent.setClassName("com.android.soundrecorder",
                    "com.android.soundrecorder.SoundRecorder");

            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

    public static void selectVideo(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.VIDEO_UNSPECIFIED);
    }

    public static void selectImage(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.IMAGE_UNSPECIFIED);
    }

    private static void selectMediaByType(
            Context context, int requestCode, String contentType) {
         if (context instanceof Activity) {

            Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);

            innerIntent.setType(contentType);

            Intent wrapperIntent = Intent.createChooser(innerIntent, null);

            ((Activity) context).startActivityForResult(wrapperIntent, requestCode);
        }
    }

    public static void viewSimpleSlideshow(Context context, SlideshowModel slideshow) {
        if (!slideshow.isSimple()) {
            throw new IllegalArgumentException(
                    "viewSimpleSlideshow() called on a non-simple slideshow");
        }
        SlideModel slide = slideshow.get(0);
        MediaModel mm = null;
        if (slide.hasImage()) {
            mm = slide.getImage();
        } else if (slide.hasVideo()) {
            mm = slide.getVideo();
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        String contentType;
        if (mm.isDrmProtected()) {
            contentType = mm.getDrmObject().getContentType();
        } else {
            contentType = mm.getContentType();
        }

        // Workaround to Fix CR 294724. some gif file's contentType may be set to
        // application/oct-stream during mms transfer. So we should set it back to
        // image/gif, otherwise the system cannot find any activity to handle this
        // intent and throws exception.
        try {
            if (contentType.equalsIgnoreCase("application/oct-stream")) {
                Log.d(TAG, "attachment file name: " + mm.getSrc());
                if (mm.getSrc().toLowerCase().endsWith(".gif")) {
                    contentType = ContentType.IMAGE_GIF;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, e.fillInStackTrace());
        }

        intent.setDataAndType(mm.getUri(), contentType);
        context.startActivity(intent);
    }

    public static void showErrorDialog(Context context,
            String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setIcon(R.drawable.ic_sms_mms_not_delivered);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    /**
     * The quality parameter which is used to compress JPEG images.
     */
    public static final int IMAGE_COMPRESSION_QUALITY = 80;
    /**
     * The minimum quality parameter which is used to compress JPEG images.
     */
    public static final int MINIMUM_IMAGE_COMPRESSION_QUALITY = 50;

    public static Uri saveBitmapAsPart(Context context, Uri messageUri, Bitmap bitmap)
            throws MmsException {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, IMAGE_COMPRESSION_QUALITY, os);

        PduPart part = new PduPart();

        part.setContentType("image/jpeg".getBytes());
        String contentId = "Image" + System.currentTimeMillis();
        part.setContentLocation((contentId + ".jpg").getBytes());
        part.setContentId(contentId.getBytes());
        part.setData(os.toByteArray());

        Uri retVal = PduPersister.getPduPersister(context).persistPart(part,
                        ContentUris.parseId(messageUri));

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("saveBitmapAsPart: persisted part with uri=" + retVal);
        }

        return retVal;
    }

    /**
     * Message overhead that reduces the maximum image byte size.
     * 5000 is a realistic overhead number that allows for user to also include
     * a small MIDI file or a couple pages of text along with the picture.
     */
    public static final int MESSAGE_OVERHEAD = 5000;

    public static void resizeImageAsync(final Context context,
            final Uri imageUri, final Handler handler,
            final ResizeImageResultCallback cb,
            final boolean append) {

        // Show a progress toast if the resize hasn't finished
        // within one second.
        // Stash the runnable for showing it away so we can cancel
        // it later if the resize completes ahead of the deadline.
        final Runnable showProgress = new Runnable() {
            public void run() {
                Toast.makeText(context, R.string.compressing, Toast.LENGTH_SHORT).show();
            }
        };
        // Schedule it for one second from now.
        handler.postDelayed(showProgress, 1000);

        new Thread(new Runnable() {
            public void run() {
                final PduPart part;
                try {
                    UriImage image = new UriImage(context, imageUri);
                    part = image.getResizedImageAsPart(
                        MmsConfig.getMaxImageWidth(),
                        MmsConfig.getMaxImageHeight(),
                        MmsConfig.getMaxMessageSize() - MESSAGE_OVERHEAD);
                } finally {
                    // Cancel pending show of the progress toast if necessary.
                    handler.removeCallbacks(showProgress);
                }

                handler.post(new Runnable() {
                    public void run() {
                        cb.onResizeResult(part, append);
                    }
                });
            }
        }).start();
    }

    public static void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener, int validNum) {
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.discard_message)
                .setMessage(
                        validNum > ALL_RECIPIENTS_VALID ? R.string.discard_message_reason_some_invalid
                                : R.string.discard_message_reason_all_invalid)
                .setPositiveButton(R.string.yes, listener)
                .setNegativeButton(R.string.no, null)
                .show();
    }

    public static String getLocalNumber() {
        if (null == sLocalNumber) {
            sLocalNumber = MmsApp.getApplication().getTelephonyManager().getLine1Number();
        }
        return sLocalNumber;
    }

    public static boolean isLocalNumber(String number) {
        if (number == null) {
            return false;
        }

        // we don't use Mms.isEmailAddress() because it is too strict for comparing addresses like
        // "foo+caf_=6505551212=tmomail.net@gmail.com", which is the 'from' address from a forwarded email
        // message from Gmail. We don't want to treat "foo+caf_=6505551212=tmomail.net@gmail.com" and
        // "6505551212" to be the same.
        if (number.indexOf('@') >= 0) {
            return false;
        }

        return PhoneNumberUtils.compare(number, getLocalNumber());
    }

    public static void handleReadReport(final Context context,
            final long threadId,
            final int status,
            final Runnable callback) {
        String selection = Mms.MESSAGE_TYPE + " = " + PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
            + " AND " + Mms.READ + " = 0"
            + " AND " + Mms.READ_REPORT + " = " + PduHeaders.VALUE_YES;

        if (threadId != -1) {
            selection = selection + " AND " + Mms.THREAD_ID + " = " + threadId;
        }

        final Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                        Mms.Inbox.CONTENT_URI, new String[] {Mms._ID, Mms.MESSAGE_ID},
                        selection, null, null);

        if (c == null) {
            return;
        }

        final Map<String, String> map = new HashMap<String, String>();
        try {
            if (c.getCount() == 0) {
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            while (c.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, c.getLong(0));
                map.put(c.getString(1), AddressUtils.getFrom(context, uri));
            }
        } finally {
            c.close();
        }

        OnClickListener positiveListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                for (final Map.Entry<String, String> entry : map.entrySet()) {
                    MmsMessageSender.sendReadRec(context, entry.getValue(),
                                                 entry.getKey(), status);
                }

                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnClickListener negativeListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        confirmReadReportDialog(context, positiveListener,
                                         negativeListener,
                                         cancelListener);
    }

    private static void confirmReadReportDialog(Context context,
            OnClickListener positiveListener, OnClickListener negativeListener,
            OnCancelListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.confirm);
        builder.setMessage(R.string.message_send_read_report);
        builder.setPositiveButton(R.string.yes, positiveListener);
        builder.setNegativeButton(R.string.no, negativeListener);
        builder.setOnCancelListener(cancelListener);
        builder.show();
    }

    public static String extractEncStrFromCursor(Cursor cursor,
            int columnRawBytes, int columnCharset) {
        String rawBytes = cursor.getString(columnRawBytes);
        int charset = cursor.getInt(columnCharset);

        if (TextUtils.isEmpty(rawBytes)) {
            return "";
        } else if (charset == CharacterSets.ANY_CHARSET) {
            return rawBytes;
        } else {
            return new EncodedStringValue(charset, PduPersister.getBytes(rawBytes)).getString();
        }
    }

    private static String extractEncStr(Context context, EncodedStringValue value) {
        if (value != null) {
            return value.getString();
        } else {
            return "";
        }
    }

    public static ArrayList<String> extractUris(URLSpan[] spans) {
        int size = spans.length;
        ArrayList<String> accumulator = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            accumulator.add(spans[i].getURL());
        }
        return accumulator;
    }

    /**
     * Play/view the message attachments.
     * TOOD: We need to save the draft before launching another activity to view the attachments.
     *       This is hacky though since we will do saveDraft twice and slow down the UI.
     *       We should pass the slideshow in intent extra to the view activity instead of
     *       asking it to read attachments from database.
     * @param context
     * @param msgUri the MMS message URI in database
     * @param slideshow the slideshow to save
     * @param persister the PDU persister for updating the database
     * @param sendReq the SendReq for updating the database
     */
    public static void viewMmsMessageAttachment(Context context, Uri msgUri,
            SlideshowModel slideshow) {
        boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();
        if (isSimple) {
            // In attachment-editor mode, we only ever have one slide.
            MessageUtils.viewSimpleSlideshow(context, slideshow);
        } else {
            // If a slideshow was provided, save it to disk first.
            if (slideshow != null) {
                PduPersister persister = PduPersister.getPduPersister(context);
                try {
                    PduBody pb = slideshow.toPduBody();
                    persister.updateParts(msgUri, pb);
                    slideshow.sync(pb);
                } catch (MmsException e) {
                    Log.e(TAG, "Unable to save message for preview");
                    return;
                }
            }
            // Launch the slideshow activity to play/view.
            Intent intent = new Intent(context, SlideshowActivity.class);
            intent.setData(msgUri);
            context.startActivity(intent);
        }
    }

    public static void viewMmsMessageAttachment(final ComposeMessageActivity activity, final WorkingMessage msg) {
        final SlideshowModel slideshow = msg.getSlideshow();
        if (slideshow == null) {
            throw new IllegalStateException("msg.getSlideshow() == null");
        }
        if (slideshow.isSimple()) {
            MessageUtils.viewSimpleSlideshow(activity, slideshow);
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Uri uri = msg.saveAsMms(false);
                    if (!activity.isFinishing()) {
                        viewMmsMessageAttachment(activity, uri, slideshow);
                    }
                }
            }).start();
        }
    }

    /**
     * Debugging
     */
    public static void writeHprofDataToFile(){
        String filename = Environment.getExternalStorageDirectory() + "/mms_oom_hprof_data";
        try {
            android.os.Debug.dumpHprofData(filename);
            Log.i(TAG, "##### written hprof data to " + filename);
        } catch (IOException ex) {
            Log.e(TAG, "writeHprofDataToFile: caught " + ex);
        }
    }

    public static boolean isAlias(String string) {
        if (!MmsConfig.isAliasEnabled()) {
            return false;
        }

        if (TextUtils.isEmpty(string)) {
            return false;
        }

        // TODO: not sure if this is the right thing to use. Mms.isPhoneNumber() is
        // intended for searching for things that look like they might be phone numbers
        // in arbitrary text, not for validating whether something is in fact a phone number.
        // It will miss many things that are legitimate phone numbers.
        if (Mms.isPhoneNumber(string)) {
            return false;
        }

        if (!isAlphaNumeric(string)) {
            return false;
        }

        int len = string.length();

        if (len < MmsConfig.getAliasMinChars() || len > MmsConfig.getAliasMaxChars()) {
            return false;
        }

        return true;
    }

    public static boolean isAlphaNumeric(String s) {
        char[] chars = s.toCharArray();
        for (int x = 0; x < chars.length; x++) {
            char c = chars[x];

            if ((c >= 'a') && (c <= 'z')) {
                continue;
            }
            if ((c >= 'A') && (c <= 'Z')) {
                continue;
            }
            if ((c >= '0') && (c <= '9')) {
                continue;
            }

            return false;
        }
        return true;
    }




    /**
     * Given a phone number, return the string without syntactic sugar, meaning parens,
     * spaces, slashes, dots, dashes, etc. If the input string contains non-numeric
     * non-punctuation characters, return null.
     */
    private static String parsePhoneNumberForMms(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);

            // accept the first '+' in the address
            if (c == '+' && builder.length() == 0) {
                builder.append(c);
                continue;
            }

            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }

            if (numericSugarMap.get(c) == null) {
                return null;
            }
        }
        return builder.toString();
    }

    /**
     * Returns true if the address passed in is a valid MMS address.
     */
    public static boolean isValidMmsAddress(String address) {
        String retVal = parseMmsAddress(address);
        return (retVal != null);
    }

    /**
     * parse the input address to be a valid MMS address.
     * - if the address is an email address, leave it as is.
     * - if the address can be parsed into a valid MMS phone number, return the parsed number.
     * - if the address is a compliant alias address, leave it as is.
     */
    public static String parseMmsAddress(String address) {
        // if it's a valid Email address, use that.
        if (Mms.isEmailAddress(address)) {
            return address;
        }

        // if we are able to parse the address to a MMS compliant phone number, take that.
        String retVal = parsePhoneNumberForMms(address);
        if (retVal != null) {
            return retVal;
        }

        // if it's an alias compliant address, use that.
        if (isAlias(address)) {
            return address;
        }

        // it's not a valid MMS address, return null
        return null;
    }

    private static void log(String msg) {
        Log.d(TAG, "[MsgUtils] " + msg);
    }

    public static boolean hasThreadFroNumber(Context context, String address){
        boolean isEmail = Mms.isEmailAddress(address);
        boolean isPhoneNumber = Mms.isPhoneNumber(address);

        String refinedAddress = isEmail ? address.toLowerCase() : address;

        ContentResolver contentResolver = context.getContentResolver();
        Uri canonicalUri = Uri.parse("content://mms-sms/canonical-addresses");
        Uri threadsUri = Uri.parse("content://mms-sms/threadID");
        Uri smsCanonicalUri = Uri.parse("content://sms/threadID");

        String selection = "address=?";
        String[] selectionArgs;
        String threadSelection = "recipient_ids=?";

        long retVal = -1L;
        if (!isPhoneNumber) {
            selectionArgs = new String[] { refinedAddress };
        } else {
            selection += " OR " + String.format(Locale.ENGLISH, "PHONE_NUMBERS_EQUAL(address, ?, %d)",
                        (context.getResources().getBoolean(
                                com.android.internal.R.bool.config_use_strict_phone_number_comparation) ? 1 : 0));
            selectionArgs = new String[] { refinedAddress, refinedAddress };
        }

        Cursor cursor = null;

        try {
            cursor = contentResolver.query(canonicalUri, ID_PROJECTION,
                    selection, selectionArgs, null);

            if (cursor.getCount() == 0) {
                ContentValues contentValues = new ContentValues(1);
                contentValues.put(CanonicalAddressesColumns.ADDRESS, refinedAddress);
                contentResolver.insert(smsCanonicalUri, contentValues);
                cursor = contentResolver.query(canonicalUri, ID_PROJECTION,
                        selection, selectionArgs, null);
            }

            if (cursor.moveToFirst()) {
                retVal = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
            }

            cursor = contentResolver.query(
                    threadsUri, ID_PROJECTION,
                    threadSelection, new String[]{String.valueOf(retVal)}, null);
            if (cursor!=null && cursor.moveToFirst()) {
                return (cursor.getCount()>0);
            } else {
                return false;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

    }

    private static boolean isCDMAActive() {
        boolean isCDMA = false;
        int activePhone = TelephonyManager.getDefault().getPhoneType();
        if (TelephonyManager.PHONE_TYPE_CDMA == activePhone) {
            isCDMA = true;
        }

        return isCDMA;
    }

    /*--------------Methods for encoding sms delivery pdu on sim card-------------*/
    //The following methods may be integrated into framework in the future.

    /**
     * Generate a Delivery PDU byte array. see getSubmitPdu for reference.
     */
    public static byte[] getDeliveryPdu(String scAddress, String destinationAddress,
            String message, long date) {
        if (isCDMAActive()) {
            return getCDMADeliveryPdu(scAddress, destinationAddress, message, date);
        } else {
            return getDeliveryPdu(scAddress, destinationAddress, message, date, null,
                    ENCODING_UNKNOWN);
        }
    }


    public static byte[] getCDMADeliveryPdu(String scAddress, String destinationAddress, String message,long date) {
        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            Log.d(TAG,"getCDMADeliveryPdu,message =null");
            return null;
        }

        //according to submit pdu encoding  as written in privateGetSubmitPdu

        // MTI = SMS-DELIVERY, UDHI = header != null
        byte[] header = null;
        byte mtiByte = (byte)(0x00 | (header != null ? 0x40 : 0x00));
        ByteArrayOutputStream headerStream = getDeliveryPduHeader(destinationAddress, mtiByte);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(
                MAX_USER_DATA_BYTES + 40);

        DataOutputStream dos = new DataOutputStream(byteStream);
        //int status,Status of message.  See TS 27.005 3.1, "<stat>"

                  /*      0 = "REC UNREAD"    */
                  /*      1 = "REC READ"      */
                  /*      2 = "STO UNSENT"    */
                  /*      3 = "STO SENT"      */

        try{

            //int uTeleserviceID;
            int uTeleserviceID = SmsEnvelope.TELESERVICE_CT_WAP;//int
            dos.writeInt(uTeleserviceID);

            //unsigned char bIsServicePresent
            byte bIsServicePresent = 0;//byte
            dos.writeInt(bIsServicePresent);

            //uServicecategory
            int uServicecategory = 0;//int
            dos.writeInt(uServicecategory);

            //RIL_CDMA_SMS_Address
            //digit_mode
            //number_mode
            //number_type
            //number_plan
            //number_of_digits
            //digits[]
            CdmaSmsAddress destAddr = CdmaSmsAddress.parse(
                    PhoneNumberUtils.cdmaCheckAndProcessPlusCode(destinationAddress));
            if (destAddr == null) return null;
            dos.writeByte(destAddr.digitMode);//int
            dos.writeByte(destAddr.numberMode);//int
            dos.writeByte(destAddr.ton);//int
            Log.d(TAG, "message type=" + destAddr.ton +"destination add=" + destinationAddress + "message=" + message);
            dos.writeByte(destAddr.numberPlan);//int
            dos.writeByte(destAddr.numberOfDigits);//byte
            dos.write(destAddr.origBytes, 0, destAddr.origBytes.length); // digits

            //RIL_CDMA_SMS_Subaddress
         // Subaddress is not supported.
            dos.writeByte(0); //subaddressType int
            dos.writeByte(0); //subaddr_odd byte
            dos.writeByte(0); //subaddr_nbr_of_digits byte

            SmsHeader smsHeader = new SmsHeader().fromByteArray(headerStream.toByteArray());
            UserData uData = new UserData();
            uData.payloadStr = message;
          //  uData.userDataHeader = smsHeader;
            uData.msgEncodingSet = true;
            uData.msgEncoding = UserData.ENCODING_UNICODE_16;

            BearerData bearerData = new BearerData();
            bearerData.messageType = BearerData.MESSAGE_TYPE_DELIVER;

            bearerData.deliveryAckReq = false;
            bearerData.userAckReq = false;
            bearerData.readAckReq = false;
            bearerData.reportReq = false;

            bearerData.userData = uData;

            byte[] encodedBearerData = BearerData.encode(bearerData);
            if (null != encodedBearerData) {
                // bearer data len
                dos.writeByte(encodedBearerData.length);//int
                Log.d(TAG, "encodedBearerData length=" + encodedBearerData.length);

                // aBearerData
                dos.write(encodedBearerData, 0, encodedBearerData.length);
            }else{
                dos.writeByte(0);
            }


        }catch (IOException e) {
            Log.e(TAG, "Error writing dos", e);
        }finally{
            try{
                if(null != byteStream){
                    byteStream.close();
                }

                if(null != dos){
                    dos.close();
                }

                if(null != headerStream){
                    headerStream.close();
                }
            }catch(IOException e){
                Log.e(TAG, "Error close dos", e);
            }

        }



        return byteStream.toByteArray();

    }

    /**
     * Generate a Delivery PDU byte array. see getSubmitPdu for reference.
     */
    public static byte[] getDeliveryPdu(String scAddress, String destinationAddress, String message,
            long date, byte[] header, int encoding) {
        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            return null;
        }

        // MTI = SMS-DELIVERY, UDHI = header != null
        byte mtiByte = (byte)(0x00 | (header != null ? 0x40 : 0x00));
        ByteArrayOutputStream bo = getDeliveryPduHeader(destinationAddress, mtiByte);
        // User Data (and length)
        byte[] userData;
        if (encoding == ENCODING_UNKNOWN) {
            // First, try encoding it with the GSM alphabet
            encoding = ENCODING_7BIT;
        }
        try {
            if (encoding == ENCODING_7BIT) {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
            } else { //assume UCS-2
                try {
                    userData = encodeUCS2(message, header);
                } catch (UnsupportedEncodingException uex) {
                    Log.e("GSM", "Implausible UnsupportedEncodingException ",
                            uex);
                    return null;
                }
            }
        } catch (EncodeException ex) {
            // Encoding to the 7-bit alphabet failed. Let's see if we can
            // encode it as a UCS-2 encoded message
            try {
                userData = encodeUCS2(message, header);
                encoding = ENCODING_16BIT;
            } catch (UnsupportedEncodingException uex) {
                Log.e("GSM", "Implausible UnsupportedEncodingException ",
                            uex);
                return null;
            }
        }

        if (encoding == ENCODING_7BIT) {
            if ((0xff & userData[0]) > MAX_USER_DATA_SEPTETS) {
                // Message too long
                return null;
            }
            bo.write(0x00);
        } else { //assume UCS-2
            if ((0xff & userData[0]) > MAX_USER_DATA_BYTES) {
                // Message too long
                return null;
            }
            // TP-Data-Coding-Scheme
            // Class 3, UCS-2 encoding, uncompressed
            bo.write(0x0b);
        }
        byte[] timestamp = getTimestamp(date);
        bo.write(timestamp, 0, timestamp.length);

        bo.write(userData, 0, userData.length);
        return bo.toByteArray();
    }

    private static ByteArrayOutputStream getDeliveryPduHeader(
            String destinationAddress, byte mtiByte) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(
                MAX_USER_DATA_BYTES + 40);
        bo.write(mtiByte);

        byte[] daBytes;
        daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);

        // destination address length in BCD digits, ignoring TON byte and pad
        // TODO Should be better.
        bo.write((daBytes.length - 1) * 2
                - ((daBytes[daBytes.length - 1] & 0xf0) == 0xf0 ? 1 : 0));

        // destination address
        bo.write(daBytes, 0, daBytes.length);

        // TP-Protocol-Identifier
        bo.write(0);
        return bo;
    }

    private static byte[] encodeUCS2(String message, byte[] header)
        throws UnsupportedEncodingException {
        byte[] userData, textPart;
        textPart = message.getBytes("utf-16be");

        if (header != null) {
            // Need 1 byte for UDHL
            userData = new byte[header.length + textPart.length + 1];

            userData[0] = (byte)header.length;
            System.arraycopy(header, 0, userData, 1, header.length);
            System.arraycopy(textPart, 0, userData, header.length + 1, textPart.length);
        }
        else {
            userData = textPart;
        }
        byte[] ret = new byte[userData.length+1];
        ret[0] = (byte) (userData.length & 0xff );
        System.arraycopy(userData, 0, ret, 1, userData.length);
        return ret;
    }

    private static byte[] getTimestamp(long time) {
        // See TS 23.040 9.2.3.11
        byte[] timestamp = new byte[TIMESTAMP_LENGTH];
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddkkmmss:Z", Locale.US);
        String[] date = sdf.format(time).split(":");
        // generate timezone value
        String timezone = date[date.length - 1];
        String signMark = timezone.substring(0, 1);
        int hour = Integer.parseInt(timezone.substring(1, 3));
        int min = Integer.parseInt(timezone.substring(3));
        int timezoneValue = hour * 4 + min / 15;
        // append timezone value to date[0] (time string)
        String timestampStr = date[0] + timezoneValue;

        int digitCount = 0;
        for (int i = 0; i < timestampStr.length(); i++) {
            char c = timestampStr.charAt(i);
            int shift = ((digitCount & 0x01) == 1) ? 4 : 0;
            timestamp[(digitCount >> 1)] |= (byte)((charToBCD(c) & 0x0F) << shift);
            digitCount++;
        }

        if (signMark.equals("-")) {
            timestamp[timestamp.length - 1] = (byte) (timestamp[timestamp.length - 1] | 0x08);
        }

        return timestamp;
    }

    private static int charToBCD(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else {
            throw new RuntimeException ("invalid char for BCD " + c);
        }
    }

    // TODO: should be better according to TS 24.008 10.5.4.7
    public static boolean isValidSimAddress(String address) {
        return PhoneNumberUtils.networkPortionToCalledPartyBCD(address) != null;
    }
}
