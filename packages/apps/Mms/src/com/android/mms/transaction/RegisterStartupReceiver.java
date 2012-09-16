/*
 * Copyright (C) 2009 The Android Open Source Project
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


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.os.SystemClock;
import android.text.TextUtils;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import android.telephony.SmsManager;
import android.os.SystemProperties;
import java.util.List;
import android.util.Log;

/*
 * Handles OTA Start procedure at phone power up. At phone power up, if phone is not OTA
 * provisioned (check MIN value of the Phone) and 'device_provisioned' is not set,
 * OTA Activation screen is shown that helps user activate the phone
 */
public class RegisterStartupReceiver extends BroadcastReceiver {
    private static final String TAG = "RegisterStartupReceiver";
    private static final boolean DBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private Context mContext;
	private TelephonyManager mTelephonyMgr;
	private static final String SERVER_NUMBER = "10659401";
	static final String s_RegisterFileName = "register_Start_up";
	
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        
        Thread t = new Thread(new WorkerThread());
        t.start();
        
        if (DBG) Log.d(TAG, "RegisterStartupReceiver finish!!!");
    }
    
    private int ISO_13818_CRC32(String s)
    {
	    	
		byte[] pdata = s.getBytes();
		int[] CRCTable = new int[256];
		int crc,i,j;
		int poly = 0xEDB88320;
		
		
		for(i=0; i<256; i++)
		{
			crc = i;
		
			for (j=0; j<8; j++)
			{
				if ( (crc & 1) == 1 )
				{
		      		crc = (poly ^ (crc >> 1));
		      	}
		      	else
		      	{
		      		crc = (crc >> 1);	
		      	}
		  	}
		
			CRCTable[i] = crc;	
		}
		
		crc = 0xFFFFFFFF;
		i = s.length();
		j=0;
		
		while(i > 0)
		{
		    crc = CRCTable[(crc ^ (pdata[j])) & 0xFF] ^ (crc >> 8);
		    j++;
		    i--;
		}
		
		return crc^0xFFFFFFFF;
	}

	private final void writeValue(String name,String value)
	{
		if (DBG) Log.d(TAG,"writeValue : " + name);
		if(mContext != null)
		{
			if (DBG) Log.d(TAG,"m_Context exist");
			SharedPreferences sp = mContext.getSharedPreferences(s_RegisterFileName,Context.MODE_WORLD_WRITEABLE);
			if (DBG) Log.d(TAG,"sp"+ sp);
			SharedPreferences.Editor edit = sp.edit();
			edit.putString(name,value);
			edit.commit();
			if (DBG) Log.d(TAG,"commit successed");
		}		
	}
	
	private final String readValue(String name)
	{
		if (DBG) Log.d(TAG,"readValue : " + name);
		if(mContext != null)
		{
			if (DBG) Log.d(TAG,"m_Context exist");
			SharedPreferences sp = mContext.getSharedPreferences(s_RegisterFileName,Context.MODE_WORLD_READABLE);
			if (DBG) Log.d(TAG,"m_Context exist value="+sp.getString(name,""));
			return sp.getString(name,"");
		}	
		return "";
	}
	
	private class WorkerThread implements Runnable {

	    // Pick off items on the queue, one by one, and compute their bitmap.
	    // Place the resulting bitmap in the cache, then call back by executing
	    // the given runnable so things can get updated appropriately.
	    public void run() {
			String imsi = null;									
			String deviceid = null;
			String operatorname = null;
			String operator = null;
			String versioncode = SystemProperties.get("ro.build.display.id");
			String devicemode = SystemProperties.get("ro.product.model");
          
      		if (DBG) Log.d(TAG, "!!!!!!!!WorkerThread Start!!!!");			
            
      		try {
				Thread.sleep(30*1000);
			} catch (InterruptedException ex) {
			}

			try {
				mTelephonyMgr = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
				imsi = mTelephonyMgr.getSubscriberId();								
				deviceid = mTelephonyMgr.getDeviceId();
				operatorname = mTelephonyMgr.getNetworkOperatorName();								
				operator = mTelephonyMgr.getNetworkOperator();
				
				if (DBG) Log.d(TAG, "!!!!!!!!imsi:" + imsi);
				if (DBG) Log.d(TAG, "!!!!!!!deviceid:" + deviceid);
				if (DBG) Log.d(TAG, "!!!!!!!!operatorname:" + operatorname);
				if (DBG) Log.d(TAG, "!!!!!!!operator:" + operator);
				
			} catch (Exception e) {
			  if (DBG) Log.d(TAG, "Error in TelephonyManager: " + e.getMessage());
			}
			
			if ( imsi == null || imsi == null )
			{
				return;	
			}

			if (DBG) Log.d(TAG, "********imsi:" + readValue("imsi"));
			if (DBG) Log.d(TAG, "********meid:" + readValue("meid"));
			if (DBG) Log.d(TAG, "********register:" + readValue("register"));
			
			if ( readValue("register").equals("yes"))	
            {
            	if ( readValue("imsi").equals(imsi)
            		&& readValue("meid").equals(deviceid))
				{
					return;
				}
				else
				{
					writeValue("meid",deviceid);
					writeValue("imsi",imsi);
					writeValue("register","no");
				}
            }
            else
            {
				writeValue("meid",deviceid);
				writeValue("imsi",imsi);
            }

			if (DBG) Log.d(TAG, "!!!!!!!!imsi:" + readValue("imsi"));
			if (DBG) Log.d(TAG, "!!!!!!!!meid:" + readValue("meid"));
			
            devicemode = devicemode.replace("-"," ");

            devicemode = "JCT-" + devicemode;
            
			String m_data = "<a1><b1>"+devicemode+"</b1>"+"<b2>"+deviceid+"</b2>"
							+"<b3>"+imsi+"</b3>"+"<b4>"+versioncode+"</b4></a1>";
										
			byte[] data_head;
			data_head = new byte[4];
			
			data_head[0] = 2;
			data_head[1] = 3;
			data_head[2] = (byte)m_data.length();
			data_head[3] = 0;
			
			String final_str = new String(data_head);										
			final_str = final_str + m_data;										
			final_str = final_str + String.format("%1$08x", ISO_13818_CRC32(final_str));
			
			if (DBG) Log.d(TAG, "!!!!!!!final_str length:" + final_str.length());
			if (DBG) Log.d(TAG, "!!!!!!!!final_str:" + final_str);
			
			//china Telecom
			if ( operator.equals("46003"))
			{				    
				try {					 
					if (DBG) Log.d(TAG, "!!!!!!!!!!!send message:"+final_str);
					List<String> texts = SmsManager.getDefault().divideMessage(final_str);
					for (String text : texts) {
				 		SmsManager.getDefault().sendTextMessage(SERVER_NUMBER, null, text, null, null);
				   	}
				  
				  	if (DBG) Log.d(TAG, "!!!!!!!!!!!send message sucess!");
				} catch (SecurityException e) {
				  // expected
				  	if (DBG) Log.d(TAG, "!!!!!!!!!!!send message failed!");
				}	
			}
	    }
	}
}
