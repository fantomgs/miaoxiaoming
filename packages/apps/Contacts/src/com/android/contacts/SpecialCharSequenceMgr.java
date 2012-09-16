/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2006, 2011 The Android Open Source Project
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
package com.android.contacts;

import com.android.internal.telephony.ITelephony;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony.Intents;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import android.os.SystemProperties;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import com.android.contacts.DialtactsActivity;
import android.app.Activity;		//Add By zzg 2012_03_29
import com.android.contacts.DialtactsActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";
    private static final String MMI_IMEI_DISPLAY = "*#06#";
	private static final String MMI_FACTORY_KIT_ENTER = "*#37*#";	//Add By zzg 2012_03_27

	//Add By zzg 2012_04_21
	private static final String MMI_QRD_MENU					= "*#38*#";	
	private static final String MMI_QRD_TEST_DEV_TOOLS 			= "*#381*#";	
	private static final String MMI_QRD_TEST_MULTIPLE_PDP_TEST 	= "*#382*#";	
	private static final String MMI_QRD_TEST_QRD_SAVELOG		= "*#383*#";		
	private static final String MMI_QRD_TEST_QRD_TOOLS 			= "*#384*#";	
	private static final String MMI_QRD_TEST_QVTESTER_04		= "*#385*#";	
	private static final String MMI_QRD_TEST_SPARE_PARTS		= "*#386*#";		
	//Add End

	private static final String MM_TP_UTILITY				    = "*#39*#";		//Add by zzg 2012_05_04
	private static final String MM_MSG_2133UPDATE				= "*#40*#";		//Add by zzg 2012_05_05
	private static final String MM_OEMINFO				        = "*#41*#";		//Add by zzg 2012_07_27
	private static final String MM_ININ_CYTTSP                  = "*#10000*#"; 	//Add by ydc 2012_07_11
	private static final String MMI_UNLOCK_NETWORK				= "*#9950#0#";
    private static final int SUB1 = 0;
    private static final int SUB2 = 1;
	

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    static boolean handleChars(Context context, String input, EditText textField) {
        return handleChars(context, input, false, textField);
    }

    static boolean handleChars(Context context, String input) {
        return handleChars(context, input, false, null);
    }

    static boolean handleChars(Context context, String input, boolean useSystemWindow,
            EditText textField) {

        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);
		
        if (handleIMEIDisplay(context, dialString, useSystemWindow)		
				|| handleFactoryKitEnter(context, dialString)           //Add By zzg 2012_03_27
				|| handleQrdMenuEnter(context, dialString)           //Add By zzg 2012_03_27
                || handlePinEntry(context, dialString)
                || handleAdnEntry(context, dialString, textField)
                || handleSecretCode(context, dialString)
                || handleUnlockNetWorkEnter(context,dialString)) {
            return true;
        }

        return false;
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            Intent intent = new Intent(Intents.SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        int subscription = 0;
        Uri uri = null;
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                int index = Integer.parseInt(input.substring(0, len-1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                QueryHandler handler = new QueryHandler (context.getContentResolver());

                // create the cookie object
                SimContactQueryCookie sc = new SimContactQueryCookie(index - 1, handler,
                        ADN_QUERY_TOKEN);

                // setup the cookie fields
                sc.contactNum = index - 1;
                sc.setTextField(textField);

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
				if(SystemProperties.get("ro.product.customer_id").equals("JC_A107")||SystemProperties.get("ro.product.customer_id").equals("W706"))
				{
					sc.progressDialog.setTitle(R.string.uimContacts_title);
                	sc.progressDialog.setMessage(context.getText(R.string.uimContacts_emptyLoading));
				}
				else
				{
                	sc.progressDialog.setTitle(R.string.simContacts_title);
                	sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
				}
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                // display the progress dialog
                sc.progressDialog.show();
                subscription = TelephonyManager.getPreferredVoiceSubscription();

                if(subscription == SUB1) {
                    uri = Uri.parse("content://icc/adn_sub1");
                } else if (subscription == SUB2) {
                    uri = Uri.parse("content://icc/adn_sub2");
                } else {
                    Log.d(TAG, "handleAdnEntry:Invalid Subscription");
                }
                // run the query.
                handler.startQuery(ADN_QUERY_TOKEN, sc, uri,
                        new String[]{ADN_PHONE_NUMBER_COLUMN_NAME}, null, null, null);
                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    static boolean handlePinEntry(Context context, String input) {
        int subscription = 0;
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            try {
                // Use Voice Subscription for both change PIN & unblock PIN using PUK.
                subscription = TelephonyManager.getPreferredVoiceSubscription();
                Log.d(TAG, "Sending MMI on subscription :" + subscription);
                return ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                        .handlePinMmiOnSubscription(input, subscription);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                return false;
            }
        }
        return false;
    }

	static boolean handleUnlockNetWorkEnter(Context context,String input)
	{
		if(input.equals(MMI_UNLOCK_NETWORK))
		{
			Intent intent = new Intent(DialtactsActivity.sCleanNetworkLock);
	        context.sendBroadcast(intent);
			AlertDialog dlg = new AlertDialog.Builder(context)
				.setMessage(R.string.networklockErased)
				.setNeutralButton(android.R.string.ok,null)
				.show();
			
			return true;
		}
		return false;
	}
	//Add By zzg 2012_03_27	
	static boolean handleFactoryKitEnter(Context context, String input) 
	{
		if (input.equals(MMI_FACTORY_KIT_ENTER)) 
		{	
			Intent intent = new Intent("com.factory_test");	
			
	        context.startActivity(intent);		
			
			return true;			
		}

		//String temp="*#12577#";
        if (input.equals("*#12577#")) 
		{	
			Intent intent = new Intent("com.factory_test");	
			
	        context.startActivity(intent);		
			
			return true;			
		}

		return false;
	}  

		//Add By zzg 2012_03_27	
	static boolean handleNetworkLockEnter(Context context, String input) 
	{
		//Log.d(TAG, "===zzg 111 handleNetworkLockEnter==00000000"+input+"===handleNetworkLockEnter=="+MMI_FACTORY_KIT_ENTER);
		//NetWorkLock m_NetworkLock=new ;

		//String temp="*#12577#";
        if (input.equals("*#3412#")) 
		{	
			//Intent intent = new Intent("com.factory_test");	
			
	        //context.startActivity(intent);		
	        SystemProperties.set("persist.networklock","y");
			//NetWorkLock.writeValue("networklock","y");
			return true;			
		}

		return false;
	} 
		
	//Add End

	//Add By zzg 2012_03_27	
	static boolean handleQrdMenuEnter(Context context, String input) 
	{	
		if (input.equals(MMI_QRD_MENU)) 	//Qrd Menu
		{		 
			Intent intent = new Intent("com.qrdmenu");
			
	        context.startActivity(intent);		
			
			return true;			
		}
		else if (input.equals(MMI_QRD_TEST_DEV_TOOLS)) 	//Dev Tools
		{		        
			Intent intent = new Intent("com.dev_tools");	
			
	        context.startActivity(intent);		
			
			return true;			
		}
		
		else if (input.equals(MMI_QRD_TEST_MULTIPLE_PDP_TEST)) //Multiple PDP Test
		{	
			Intent intent = new Intent("com.multiple_pdptest");	
			
	        context.startActivity(intent);		
			
			return true;			
		}
		else if (input.equals(MMI_QRD_TEST_QRD_SAVELOG)) 	//QRDSaveLog
		{	
			Intent intent = new Intent("com.qrd_savelog");	
			
	        context.startActivity(intent);		
			
			return true;			
		}
		else if (input.equals(MMI_QRD_TEST_QRD_TOOLS)) 	//QRDTools
		{	
			Intent intent = new Intent("com.qrd_tools");	
			
	        context.startActivity(intent);		
			
			return true;			
		}
		else if (input.equals(MMI_QRD_TEST_QVTESTER_04)) //QVTester-04
		{	
			Intent intent = new Intent("com.qvtester_04");	
			
	        context.startActivity(intent);		
			
			return true;			
		}
		else if (input.equals(MMI_QRD_TEST_SPARE_PARTS)) //Spare parts
		{	
			Intent intent = new Intent("com.spare_parts");	
			
	        context.startActivity(intent);		
			
			return true;			
		}		
		else if (input.equals(MM_TP_UTILITY)) //TPUtility
		{	
			Intent intent = new Intent("com.tputility");	
			
	        context.startActivity(intent);		
			
			return true;			
		}	
		else if (input.equals(MM_MSG_2133UPDATE)) //Msg2133Update
		{	
			Intent intent = new Intent("com.msg2133update");	
			
	        context.startActivity(intent);		
			
			return true;			
		}
		else if (input.equals(MM_OEMINFO))	//OEMINFO
		{
			String oeminfo = SystemProperties.get("ro.build.oeminfo");		
			
			if (oeminfo != null)
			{
				Log.w(TAG, "***zzg oeminfo="+oeminfo+"***");
				Toast.makeText(context, oeminfo, Toast.LENGTH_LONG).show();
			}
		}
		else if(input.equals(MM_ININ_CYTTSP))
		{
			try 
			{
				String SYSFS_PALM_UPDATE = "/sys/class/ms-touchscreen-cyttsp/device/enable_init_sens";
				FileOutputStream out3 = new FileOutputStream(SYSFS_PALM_UPDATE);
				byte[] inputBuf3 = {1};
				out3.write(inputBuf3); 
				out3.close();
				 AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle("TP init")
                .setMessage("Success!!!!")
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
       			 alert.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
		 	}
		 	catch(Exception e)    
		 	{
            	;
         	}
			
			return true;
		}

		return false;
	}    
	//Add End

    static boolean handleIMEIDisplay(Context context, String input, boolean useSystemWindow) {
        if (input.equals(MMI_IMEI_DISPLAY)) {
            int subscription = TelephonyManager.getPreferredVoiceSubscription();
            int phoneType = ((TelephonyManager)context.getSystemService(
                    Context.TELEPHONY_SERVICE)).getPhoneType(subscription);

            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                showIMEIPanel(context, useSystemWindow);
                return true;
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                showMEIDPanel(context, useSystemWindow);
                return true;
            }
        }

        return false;
    }

    static String validateIMEI(String imei) {
        return imei == null ? "" : imei;
    }

    static void showIMEIPanel(Context context, boolean useSystemWindow) {
        String imeiStr;
        TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        if ( !TelephonyManager.isMultiSimEnabled() )
            imeiStr = validateIMEI(tm.getDeviceId());
        else
            imeiStr = validateIMEI(tm.getDeviceId(0)) + "\n" + validateIMEI(tm.getDeviceId(1));

        Log.d(TAG, "show dual IMEI dialog");

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.imei)
                .setMessage(imeiStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
    }

    static void showMEIDPanel(Context context, boolean useSystemWindow) {
        int subscription = TelephonyManager.getPreferredVoiceSubscription();
        String meidStr = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE))
                .getDeviceId(subscription);

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.meid)
                .setMessage(meidStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
    }
static private void showDeviceIdPanel(Context context) {
        Log.d(TAG, "showDeviceIdPanel()...");

        int labelId;
        StringBuilder deviceId = null;
        TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

        if (tm.isMultiSimEnabled()) {
            labelId = R.string.imei;

            int phonecount = tm.getPhoneCount();
            int[] type = new int[phonecount];
            String[] ids = new String[phonecount];
            boolean multimode = false;
            for (int i=0; i<phonecount; i++) {
                type[i] = tm.getPhoneType(i);
                ids[i] = tm.getDeviceId(i);
                if (type[i] == TelephonyManager.PHONE_TYPE_CDMA) {
                    // C+G mode, use meid as its title?
                    multimode = true;
                    labelId = R.string.meid;
                }
            }

            // 16 IMEI characters, or 7 MEID characters, maybe plus subscription name
            deviceId = new StringBuilder(50);
            for (int i=0; i<phonecount; i++) {
                if (multimode) {
                    String prefix =
                        (type[i] == TelephonyManager.PHONE_TYPE_GSM)
                        ? "IMEI " : "MEID ";
                    deviceId.append(prefix);
                }
                deviceId.append(ids[i] == null ? "" : ids[i]);
                if (i != tm.getPhoneCount()-1) {
                    deviceId.append("\n");
                }
            }
        } else {
            int type = tm.getPhoneType();
            labelId = (type == TelephonyManager.PHONE_TYPE_GSM)
                ? R.string.imei : R.string.meid;
            deviceId = new StringBuilder();
            deviceId.append(tm.getDeviceId());
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(labelId)
            .setMessage(deviceId.toString())
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(true)
            .show();
       dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
    }

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactNum;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link handleAdnEntry}.
     */
    private static class QueryHandler extends AsyncQueryHandler {

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
            SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

            // close the progress dialog.
            sc.progressDialog.dismiss();

            // get the EditText to update or see if the request was cancelled.
            EditText text = sc.getTextField();

            // if the textview is valid, and the cursor is valid and postionable
            // on the Nth number, then we update the text field and display a
            // toast indicating the caller name.
            if ((c != null) && (text != null) && (c.moveToPosition(sc.contactNum))) {
                String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                String number = c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));

                // fill the text in.
                text.getText().replace(0, 0, number);

                // display the name as a toast
                Context context = sc.progressDialog.getContext();
                name = context.getString(R.string.menu_callNumber, name);
                Toast.makeText(context, name, Toast.LENGTH_SHORT)
                    .show();
            }
        }
    }
}
