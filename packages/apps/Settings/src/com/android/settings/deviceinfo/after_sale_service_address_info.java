/*
 * Copyright (C) 2008 The Android Open Source Project
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
 
//WATERWORLD_12_MODIFY
package com.android.settings;

import android.net.sip.SipManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import android.telephony.TelephonyManager;
import com.android.settings.R;
import android.util.Log;
import android.content.res.Resources;
import android.content.Intent;

import android.view.KeyEvent;


public class after_sale_service_address_info extends PreferenceActivity {

	private static final String TAG = "AfterSaleServiceAddressInfo";
    private PreferenceScreen adapter;
	private static final int[]  cityHNJ = {R.string.CITY_HLJ_HEB,  R.string.CITY_HLJ_HEB, R.string.CITY_HLJ_MDJ, R.string.CITY_HLJ_QQHE, 
											  R.string.CITY_HLJ_QTH,  R.string.CITY_HLJ_JX};
										      
	
	private static final int[]  cityJL = {R.string.CITY_JL_CC,   R.string.CITY_JL_CC, R.string.CITY_JL_CC, R.string.CITY_JL_BC,   R.string.CITY_JL_LY,  
										   	 R.string.CITY_JL_BS,   R.string.CITY_JL_MHK,  R.string.CITY_JL_SP, R.string.CITY_JL_SY,
										     R.string.CITY_JL_TH,   R.string.CITY_JL_YBZ};	

	private static final int[]  cityLL = {R.string.CITY_LL_SY,   R.string.CITY_LL_SY,  R.string.CITY_LL_DL};		

	private static final int[]  ADDRESS_INFO_HLJ = {R.string.ADDRESS_INFO_HLJ_HEB1,   R.string.ADDRESS_INFO_HLJ_HEB2,  R.string.ADDRESS_INFO_HLJ_MDJ,
													   R.string.ADDRESS_INFO_HLJ_QQHE,   R.string.ADDRESS_INFO_HLJ_QTH,   R.string.ADDRESS_INFO_HLJ_JX};	

	private static final int[]  ADDRESS_INFO_JL = {R.string.ADDRESS_INFO_JL_CC1,   R.string.ADDRESS_INFO_JL_CC2,  R.string.ADDRESS_INFO_JL_CC3,
													   R.string.ADDRESS_INFO_JL_BC,    R.string.ADDRESS_INFO_JL_LY,   R.string.ADDRESS_INFO_JL_BS,
													   R.string.ADDRESS_INFO_JL_MHK,  R.string.ADDRESS_INFO_JL_SP,  R.string.ADDRESS_INFO_JL_SY,
													   R.string.ADDRESS_INFO_JL_TH, R.string.ADDRESS_INFO_JL_YBZ};

	private static final int[]  ADDRESS_INFO_LL = {R.string.ADDRESS_INFO_LL_SY1, R.string.ADDRESS_INFO_LL_SY2, R.string.ADDRESS_INFO_LL_DL};


	private static final int[]  cityBJ = {R.string.CITY_BJ_BJ,   R.string.CITY_BJ_BJ,  R.string.CITY_BJ_BJ,   R.string.CITY_BJ_BJ};	
	private static final int[]  ADDRESS_INFO_BJ = {R.string.ADDRESS_INFO_BJ_BJ1, R.string.ADDRESS_INFO_BJ_BJ2, R.string.ADDRESS_INFO_BJ_BJ3, R.string.ADDRESS_INFO_BJ_BJ4};

	private static final int[]  cityTJ = {R.string.CITY_TJ_TJ,   R.string.CITY_TJ_TJ};	
	private static final int[]  ADDRESS_INFO_TJ = {R.string.ADDRESS_INFO_TJ_TJ1, R.string.ADDRESS_INFO_TJ_TJ2};

	private static final int[]  cityHB = {R.string.CITY_HB_SJZ,   R.string.CITY_HB_HD,   R.string.CITY_HB_TS,   R.string.CITY_HB_BD};	
	private static final int[]  ADDRESS_INFO_HB = {R.string.ADDRESS_INFO_HB_SJZ, R.string.ADDRESS_INFO_HB_HD, R.string.ADDRESS_INFO_HB_TS, R.string.ADDRESS_INFO_HB_BD};

	private static final int[]  citySX = {R.string.CITY_SX_TY,   R.string.CITY_SX_NL,   R.string.CITY_SX_CZ,   R.string.CITY_SX_YQ};	
	private static final int[]  ADDRESS_INFO_SX = {R.string.ADDRESS_INFO_SX_TY, R.string.ADDRESS_INFO_SX_NL, R.string.ADDRESS_INFO_SX_CZ, R.string.ADDRESS_INFO_SX_YQ};


	private static final int[]  cityNM = {R.string.CITY_NM_HHHT,   R.string.CITY_NM_BT,   R.string.CITY_NM_HLE};	
	private static final int[]  ADDRESS_INFO_NM = {R.string.ADDRESS_INFO_NM_HHHT, R.string.ADDRESS_INFO_NM_BT, R.string.ADDRESS_INFO_NM_HLE};

	private static final int[]  cityJS = {R.string.CITY_JS_NJ,  R.string.CITY_JS_NJ,  R.string.CITY_JS_SZ,  R.string.CITY_JS_SZ,
										  R.string.CITY_JS_KS,  R.string.CITY_JS_CZ,  R.string.CITY_JS_HA,  R.string.CITY_JS_HA,
										  R.string.CITY_JS_LYG,  R.string.CITY_JS_NT,  R.string.CITY_JS_SQ,  R.string.CITY_JS_TZ,
										  R.string.CITY_JS_WX,  R.string.CITY_JS_XZ,  R.string.CITY_JS_YC,  R.string.CITY_JS_YC,
										  R.string.CITY_JS_YZ,  R.string.CITY_JS_YZ,  R.string.CITY_JS_ZJ};	
	private static final int[]  ADDRESS_INFO_JS = {R.string.ADDRESS_INFO_JS_NJ1, R.string.ADDRESS_INFO_JS_NJ2, R.string.ADDRESS_INFO_JS_SZ1, R.string.ADDRESS_INFO_JS_SZ2
												   , R.string.ADDRESS_INFO_JS_KS, R.string.ADDRESS_INFO_JS_CZ, R.string.ADDRESS_INFO_S_HA1, R.string.ADDRESS_INFO_S_HA2
												   , R.string.ADDRESS_INFO_JS_LYG, R.string.ADDRESS_INFO_JS_NT, R.string.ADDRESS_INFO_JS_SQ, R.string.ADDRESS_INFO_JS_TZ
												   , R.string.ADDRESS_INFO_JS_WX, R.string.ADDRESS_INFO_JS_XZ, R.string.ADDRESS_INFO_JS_YC1, R.string.ADDRESS_INFO_JS_YC2
												   , R.string.ADDRESS_INFO_JS_YZ1, R.string.ADDRESS_INFO_JS_YZ2, R.string.ADDRESS_INFO_JS_ZJ};


	private static final int[]  cityZJ = {R.string.CITY_ZJ_HZ,   R.string.CITY_ZJ_FY,   R.string.CITY_ZJ_HN,   R.string.CITY_ZJ_JX,
										  R.string.CITY_ZJ_JH,   R.string.CITY_ZJ_LS,   R.string.CITY_ZJ_NB,   R.string.CITY_ZJ_NH,
										  R.string.CITY_ZJ_CZ,   R.string.CITY_ZJ_SX,   R.string.CITY_ZJ_TZ,   R.string.CITY_ZJ_WZ,
										  R.string.CITY_ZJ_ZS};	
	private static final int[]  ADDRESS_INFO_ZJ = {R.string.ADDRESS_INFO_ZJ_HZ, R.string.ADDRESS_INFO_ZJ_FY, R.string.ADDRESS_INFO_ZJ_HN, R.string.ADDRESS_INFO_ZJ_JX,
												   R.string.ADDRESS_INFO_ZJ_JH, R.string.ADDRESS_INFO_ZJ_LS, R.string.ADDRESS_INFO_ZJ_NB, R.string.ADDRESS_INFO_ZJ_NH, 
												   R.string.ADDRESS_INFO_ZJ_CZ, R.string.ADDRESS_INFO_ZJ_SX, R.string.ADDRESS_INFO_ZJ_TZ, R.string.ADDRESS_INFO_ZJ_WZ, 
												   R.string.ADDRESS_INFO_ZJ_ZS };

	private static final int[]  citySH = {R.string.CITY_SH_SH,   R.string.CITY_SH_SH,   R.string.CITY_SH_SH,   R.string.CITY_SH_SH,
										  R.string.CITY_SH_SH,   R.string.CITY_SH_SH,   R.string.CITY_SH_SH,   R.string.CITY_SH_SH};	
	private static final int[]  ADDRESS_INFO_SH = {R.string.ADDRESS_INFO_SH_SH1, R.string.ADDRESS_INFO_SH_SH2, R.string.ADDRESS_INFO_SH_SH3, R.string.ADDRESS_INFO_SH_SH4,
												   R.string.ADDRESS_INFO_SH_SH5, R.string.ADDRESS_INFO_SH_SH6, R.string.ADDRESS_INFO_SH_SH7, R.string.ADDRESS_INFO_SH_SH8 };	

	private static final int[]  citySD = {R.string.CITY_SD_JN,   R.string.CITY_SD_JN,   R.string.CITY_SD_BZ,   R.string.CITY_SD_DZ,
										  R.string.CITY_SD_DY,   R.string.CITY_SD_HZ,   R.string.CITY_SD_JinNing,   R.string.CITY_SD_LW,
										  R.string.CITY_SD_LC,   R.string.CITY_SD_LY,   R.string.CITY_SD_QD,   R.string.CITY_SD_QD,
										  R.string.CITY_SD_RZ,   R.string.CITY_SD_TA,   R.string.CITY_SD_WH,   R.string.CITY_SD_WF,
										  R.string.CITY_SD_YT,   R.string.CITY_SD_ZZ,   R.string.CITY_SD_ZB};	
	private static final int[]  ADDRESS_INFO_SD = {R.string.ADDRESS_INFO_SD_JN1, R.string.ADDRESS_INFO_SD_JN2, R.string.ADDRESS_INFO_SD_BZ, R.string.ADDRESS_INFO_SD_DZ,
												   R.string.ADDRESS_INFO_SD_DY, R.string.ADDRESS_INFO_SD_HZ, R.string.ADDRESS_INFO_SD_JinNing, R.string.ADDRESS_INFO_SD_LW,
												   R.string.ADDRESS_INFO_SD_LC, R.string.ADDRESS_INFO_SD_LY, R.string.ADDRESS_INFO_SD_QD1, R.string.ADDRESS_INFO_SD_QD2,
												   R.string.ADDRESS_INFO_SD_RZ, R.string.ADDRESS_INFO_SD_TA, R.string.ADDRESS_INFO_SD_WH, R.string.ADDRESS_INFO_SD_WF,
												   R.string.ADDRESS_INFO_SD_YT, R.string.ADDRESS_INFO_SD_ZZ, R.string.ADDRESS_INFO_SD_ZB };	

	private static final int[]  cityAH = {R.string.CITY_AH_HF,   R.string.CITY_AH_HF,   R.string.CITY_AH_HF,   R.string.CITY_AH_AQ,   R.string.CITY_AH_BB,
										  R.string.CITY_AH_HZ,   R.string.CITY_AH_CH,   R.string.CITY_AH_ChiZhou,   R.string.CITY_AH_CZ,
										  R.string.CITY_AH_FY,   R.string.CITY_AH_HB,   R.string.CITY_AH_HN,   R.string.CITY_AH_HS,
										  R.string.CITY_AH_LA,   R.string.CITY_AH_MA,   R.string.CITY_AH_SZ,   R.string.CITY_AH_TL,
										  R.string.CITY_AH_WH,   R.string.CITY_AH_XC};	
	
	private static final int[]  ADDRESS_INFO_AH = {R.string.ADDRESS_INFO_AH_HF1, R.string.ADDRESS_INFO_AH_HF2, R.string.ADDRESS_INFO_AH_HF3, R.string.ADDRESS_INFO_AH_AQ, R.string.ADDRESS_INFO_AH_BB,
												   R.string.ADDRESS_INFO_AH_HZ, R.string.ADDRESS_INFO_AH_CH, R.string.ADDRESS_INFO_AH_ChiZhou, R.string.ADDRESS_INFO_AH_CZ,
												   R.string.ADDRESS_INFO_AH_FY, R.string.ADDRESS_INFO_AH_HB, R.string.ADDRESS_INFO_AH_HN, R.string.ADDRESS_INFO_AH_HS,
												   R.string.ADDRESS_INFO_AH_LA, R.string.ADDRESS_INFO_AH_MA, R.string.ADDRESS_INFO_AH_SZ, R.string.ADDRESS_INFO_AH_TL,
												   R.string.ADDRESS_INFO_AH_WH, R.string.ADDRESS_INFO_AH_XC};	


	private static final int[]  cityFJ = {R.string.CITY_FJ_FZ,   R.string.CITY_FJ_FZ,   R.string.CITY_FJ_QZ,   R.string.CITY_FJ_XM,
										  R.string.CITY_FJ_XM};	
	private static final int[]  ADDRESS_INFO_FJ = {R.string.ADDRESS_INFO_FJ_FZ1, R.string.ADDRESS_INFO_FJ_FZ2, R.string.ADDRESS_INFO_FJ_QZ, R.string.ADDRESS_INFO_FJ_XM1,
												   R.string.ADDRESS_INFO_FJ_XM2};	

	private static final int[]  cityJX = {R.string.CITY_JX_NC,   R.string.CITY_JX_FZ,   R.string.CITY_JX_GZ,   R.string.CITY_JX_JA,
										  R.string.CITY_JX_JDZ,   R.string.CITY_JX_JJ,   R.string.CITY_JX_PX,   R.string.CITY_JX_SR,
										  R.string.CITY_JX_XY,   R.string.CITY_JX_YC,   R.string.CITY_JX_YT};	
	private static final int[]  ADDRESS_INFO_JX = {R.string.ADDRESS_INFO_JX_NC, R.string.ADDRESS_INFO_JX_FZ, R.string.ADDRESS_INFO_JX_GZ, R.string.ADDRESS_INFO_JX_JA,
												   R.string.ADDRESS_INFO_JX_JDZ, R.string.ADDRESS_INFO_JX_JJ, R.string.ADDRESS_INFO_JX_PX, R.string.ADDRESS_INFO_JX_SR,
												   R.string.ADDRESS_INFO_JX_XY, R.string.ADDRESS_INFO_JX_YC, R.string.ADDRESS_INFO_JX_YT};	

	private static final int[]  cityHN = {R.string.CITY_HN_ZZ,   R.string.CITY_HN_ZZ,   R.string.CITY_HN_SQ};	
	private static final int[]  ADDRESS_INFO_HN = {R.string.ADDRESS_INFO_HN_ZZ1, R.string.ADDRESS_INFO_HN_ZZ2, R.string.ADDRESS_INFO_HN_SQ};


	private static final int[]  cityHuiBei = {R.string.CITY_HuiBei_WH,   R.string.CITY_HuiBei_WH,   R.string.CITY_HuiBei_WH,   R.string.CITY_HuiBei_DY,
										  R.string.CITY_HuiBei_EZ,   R.string.CITY_HuiBei_HH,   R.string.CITY_HuiBei_HS,   R.string.CITY_HuiBei_JM,
										  R.string.CITY_HuiBei_JZ,   R.string.CITY_HuiBei_QJ,   R.string.CITY_HuiBei_SZ,   R.string.CITY_HuiBei_XT,   R.string.CITY_HuiBei_XN,
										  R.string.CITY_HuiBei_XF,   R.string.CITY_HuiBei_XF,   R.string.CITY_HuiBei_XF,   R.string.CITY_HuiBei_XG,
										  R.string.CITY_HuiBei_YC,   R.string.CITY_HuiBei_ZX};	
	private static final int[]  ADDRESS_INFO_HuiBei = {R.string.ADDRESS_INFO_HuiBei_WH1, R.string.ADDRESS_INFO_HuiBei_WH2, R.string.ADDRESS_INFO_HuiBei_WH3, R.string.ADDRESS_INFO_HuiBei_DY,
												   R.string.ADDRESS_INFO_HuiBei_EZ, R.string.ADDRESS_INFO_HuiBei_HH, R.string.ADDRESS_INFO_HuiBei_HS, R.string.ADDRESS_INFO_HuiBei_JM,
												   R.string.ADDRESS_INFO_HuiBei_JZ, R.string.ADDRESS_INFO_HuiBei_QJ, R.string.ADDRESS_INFO_HuiBei_SZ, R.string.ADDRESS_INFO_HuiBei_XT, R.string.ADDRESS_INFO_HuiBei_XN,
												   R.string.ADDRESS_INFO_HuiBei_XF1, R.string.ADDRESS_INFO_HuiBei_XF2, R.string.ADDRESS_INFO_HuiBei_XF3, R.string.ADDRESS_INFO_HuiBei_XG,
												   R.string.ADDRESS_INFO_HuiBei_YC, R.string.ADDRESS_INFO_HuiBei_ZX};	

	private static final int[]  cityHuiNan = {R.string.CITY_HuiNan_CS,   R.string.CITY_HuiNan_CS,   R.string.CITY_HuiNan_CD,   R.string.CITY_HuiNan_CZ,
										  R.string.CITY_HuiNan_HH,   R.string.CITY_HuiNan_JS,   R.string.CITY_HuiNan_LD,   R.string.CITY_HuiNan_SY,
										  R.string.CITY_HuiNan_YY,   R.string.CITY_HuiNan_YueYang};	
	private static final int[]  ADDRESS_INFO_HuiNan = {R.string.ADDRESS_INFO_HuiNan_CS1, R.string.ADDRESS_INFO_HuiNan_CS2, R.string.ADDRESS_INFO_HuiNan_CD, R.string.ADDRESS_INFO_HuiNan_CZ,
												   R.string.ADDRESS_INFO_HuiNan_HH, R.string.ADDRESS_INFO_HuiNan_JS, R.string.ADDRESS_INFO_HuiNan_LD, R.string.ADDRESS_INFO_HuiNan_SY,
												   R.string.ADDRESS_INFO_HuiNan_YY, R.string.ADDRESS_INFO_HuiNan_YueYang};	

	private static final int[]  cityGD = {R.string.CITY_GD_GZ,   R.string.CITY_GD_SZ,   R.string.CITY_GD_SZ,   R.string.CITY_GD_CZ,
										  R.string.CITY_GD_DG,   R.string.CITY_GD_FS,   R.string.CITY_GD_HZ,   R.string.CITY_GD_JY,
										  R.string.CITY_GD_MZ,   R.string.CITY_GD_QY,   R.string.CITY_GD_ST,   R.string.CITY_GD_SG,
										  R.string.CITY_GD_YJ,   R.string.CITY_GD_ZJ};	
	private static final int[]  ADDRESS_INFO_GD = {R.string.ADDRESS_INFO_GD_GZ, R.string.ADDRESS_INFO_GD_SZ1, R.string.ADDRESS_INFO_GD_SZ2, R.string.ADDRESS_INFO_GD_CZ,
												   R.string.ADDRESS_INFO_GD_DG, R.string.ADDRESS_INFO_GD_FS, R.string.ADDRESS_INFO_GD_HZ, R.string.ADDRESS_INFO_GD_JY,
												   R.string.ADDRESS_INFO_GD_MZ, R.string.ADDRESS_INFO_GD_QY, R.string.ADDRESS_INFO_GD_ST, R.string.ADDRESS_INFO_GD_SG,
												   R.string.ADDRESS_INFO_GD_YJ, R.string.ADDRESS_INFO_GD_ZJ};	


	private static final int[]  cityGX = {R.string.CITY_GX_NN,   R.string.CITY_GX_NN,   R.string.CITY_GX_NN,   R.string.CITY_GX_NN,
										  R.string.CITY_GX_GL,   R.string.CITY_GX_GL,   R.string.CITY_GX_FCG,   R.string.CITY_GX_FCG,
										  R.string.CITY_GX_BH,   R.string.CITY_GX_CZ,   R.string.CITY_GX_HC,   R.string.CITY_GX_LB,
										  R.string.CITY_GX_LZ,   R.string.CITY_GX_QZ,   R.string.CITY_GX_QZ,   R.string.CITY_GX_YL,
										  R.string.CITY_GX_YL,   R.string.CITY_GX_YL,   R.string.CITY_GX_YL};	
	private static final int[]  ADDRESS_INFO_GX = {R.string.ADDRESS_INFO_GX_NN1, R.string.ADDRESS_INFO_GX_NN2, R.string.ADDRESS_INFO_GX_NN3, R.string.ADDRESS_INFO_GX_NN4,
												   R.string.ADDRESS_INFO_GX_GL1, R.string.ADDRESS_INFO_GX_GL2, R.string.ADDRESS_INFO_GX_FCG1, R.string.ADDRESS_INFO_GX_FCG2,
												   R.string.ADDRESS_INFO_GX_BH, R.string.ADDRESS_INFO_GX_CZ, R.string.ADDRESS_INFO_GX_HC, R.string.ADDRESS_INFO_GX_LB,
												   R.string.ADDRESS_INFO_GX_LZ, R.string.ADDRESS_INFO_GX_QZ1, R.string.ADDRESS_INFO_GX_QZ2, R.string.ADDRESS_INFO_GX_YL1,
												   R.string.ADDRESS_INFO_GX_YL2, R.string.ADDRESS_INFO_GX_YL3, R.string.ADDRESS_INFO_GX_YL4};	

	private static final int[]  cityHaiNan = {R.string.CITY_HaiNan_HK};	
	private static final int[]  ADDRESS_INFO_HaiNan = {R.string.ADDRESS_INFO_HaiNan_HK};	

	private static final int[]  cityYN = {R.string.CITY_YN_KM,   R.string.CITY_YN_KM,   R.string.CITY_YN_KM,   R.string.CITY_YN_QJ,
										  R.string.CITY_YN_DL};	
	private static final int[]  ADDRESS_INFO_YN = {R.string.ADDRESS_INFO_YN_KM1, R.string.ADDRESS_INFO_YN_KM2, R.string.ADDRESS_INFO_YN_KM3, R.string.ADDRESS_INFO_YN_QJ,
												   R.string.ADDRESS_INFO_YN_DL};	

	private static final int[]  cityGZ = {R.string.CITY_GZ_GY,   R.string.CITY_GZ_GY,   R.string.CITY_GZ_LPS};	
	private static final int[]  ADDRESS_INFO_GZ = {R.string.ADDRESS_INFO_GZ_GY1, R.string.ADDRESS_INFO_GZ_GY2, R.string.ADDRESS_INFO_GZ_LPS};

	private static final int[]  citySC = {R.string.CITY_SC_CD,   R.string.CITY_SC_CD,   R.string.CITY_SC_LS,   R.string.CITY_SC_SN,
										  R.string.CITY_SC_MY,   R.string.CITY_SC_GY,   R.string.CITY_SC_DZ,   R.string.CITY_SC_GA,
										  R.string.CITY_SC_LZ,   R.string.CITY_SC_NC,   R.string.CITY_SC_NC};	
	private static final int[]  ADDRESS_INFO_SC = {R.string.ADDRESS_INFO_SC_CD1, R.string.ADDRESS_INFO_SC_CD2, R.string.ADDRESS_INFO_SC_LS, R.string.ADDRESS_INFO_SC_SN,
												   R.string.ADDRESS_INFO_SC_MY, R.string.ADDRESS_INFO_SC_GY, R.string.ADDRESS_INFO_SC_DZ, R.string.ADDRESS_INFO_SC_GA,
												   R.string.ADDRESS_INFO_SC_LZ, R.string.ADDRESS_INFO_SC_NC1, R.string.ADDRESS_INFO_SC_NC2};	

	private static final int[]  cityCQ = {R.string.CITY_CQ_CQ,   R.string.CITY_CQ_CQ,   R.string.CITY_CQ_CQ,   R.string.CITY_CQ_CQ,
										  R.string.CITY_CQ_CQ,   R.string.CITY_CQ_CQ,   R.string.CITY_CQ_CQ,   R.string.CITY_CQ_CQ,
										  R.string.CITY_CQ_CQ};	
	private static final int[]  ADDRESS_INFO_CQ = {R.string.ADDRESS_INFO_CQ_CQ1, R.string.ADDRESS_INFO_CQ_CQ2, R.string.ADDRESS_INFO_CQ_CQ3, R.string.ADDRESS_INFO_CQ_CQ4,
												   R.string.ADDRESS_INFO_CQ_CQ5, R.string.ADDRESS_INFO_CQ_CQ6, R.string.ADDRESS_INFO_CQ_CQ7, R.string.ADDRESS_INFO_CQ_CQ8,
												   R.string.ADDRESS_INFO_CQ_CQ9};


	private static final int[]  cityShanXi = {R.string.CITY_ShanXi_XA,   R.string.CITY_ShanXi_XA,   R.string.CITY_ShanXi_BJ,   R.string.CITY_ShanXi_AK,
										  R.string.CITY_ShanXi_WN,   R.string.CITY_ShanXi_YA,   R.string.CITY_ShanXi_YL,   R.string.CITY_ShanXi_HZ};	
	private static final int[]  ADDRESS_INFO_ShanXi = {R.string.ADDRESS_INFO_ShanXi_XA1, R.string.ADDRESS_INFO_ShanXi_XA2, R.string.ADDRESS_INFO_ShanXi_BJ, R.string.ADDRESS_INFO_ShanXi_AK,
												   R.string.ADDRESS_INFO_ShanXi_WN, R.string.ADDRESS_INFO_ShanXi_YA, R.string.ADDRESS_INFO_ShanXi_YL, R.string.ADDRESS_INFO_ShanXi_HZ};


	private static final int[]  cityGS = {R.string.CITY_GS_LZ,   R.string.CITY_GS_ZY};	
	private static final int[]  ADDRESS_INFO_GS = {R.string.ADDRESS_INFO_GS_LZ, R.string.ADDRESS_INFO_GS_ZY};

	private static final int[]  cityNX = {R.string.CITY_NX_YC,   R.string.CITY_NX_SZS};	
	private static final int[]  ADDRESS_INFO_NX = {R.string.ADDRESS_INFO_NX_YC, R.string.ADDRESS_INFO_NX_SZS};	

	private static final int[]  cityQH = {R.string.CITY_QH_XN};	
	private static final int[]  ADDRESS_INFO_QH = {R.string.ADDRESS_INFO_QH_XN};		

	private static final int[]  cityXJ = {R.string.CITY_XJ_WLMQ};	
	private static final int[]  ADDRESS_INFO_XJ = {R.string.ADDRESS_INFO_XJ_WLMQ};

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
		//id = getIntent().getExtras().getInt("id");
        Log.v(TAG, "AfterSaleServiceAddressInfo City= "+getIntent().getStringExtra("City"));
		Intent in = getIntent();
		String City = in.getStringExtra("City");
		addPreferencesFromResource(R.xml.after_sale_service_address_info);
		adapter = getPreferenceScreen();
		if(adapter == null)
		{
			Log.v(TAG, "AfterSaleServiceAddressInfo adapter == null");
		}

		if(City.equals("Province_HLJ"))
		{
			for(int i=0; i < 6; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHNJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_HLJ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_JL"))
		{
			for(int i=0; i < 11; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityJL[i]));
				prf.setSummary(getString(ADDRESS_INFO_JL[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_LN"))
		{
			for(int i=0; i < 3; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityLL[i]));
				prf.setSummary(getString(ADDRESS_INFO_LL[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_BJ"))
		{
			for(int i=0; i < 4; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityBJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_BJ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_TJ"))
		{
			for(int i=0; i < 2; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityTJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_TJ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_HB"))
		{
			for(int i=0; i < 4; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHB[i]));
				prf.setSummary(getString(ADDRESS_INFO_HB[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_SX"))
		{
			for(int i=0; i < 4; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(citySX[i]));
				prf.setSummary(getString(ADDRESS_INFO_SX[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_NM"))
		{
			for(int i=0; i < 3; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityNM[i]));
				prf.setSummary(getString(ADDRESS_INFO_NM[i]));
				adapter.addPreference(prf);
			}
		}		
		else if(City.equals("Province_JS"))
		{
			for(int i=0; i < 19; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityJS[i]));
				prf.setSummary(getString(ADDRESS_INFO_JS[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_ZJ"))
		{
			for(int i=0; i < 13; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityZJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_ZJ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_SH"))
		{
			for(int i=0; i < 8; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(citySH[i]));
				prf.setSummary(getString(ADDRESS_INFO_SH[i]));
				adapter.addPreference(prf);
			}
		}		
		else if(City.equals("Province_SD"))
		{
			for(int i=0; i < 19; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(citySD[i]));
				prf.setSummary(getString(ADDRESS_INFO_SD[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_AH"))
		{
			for(int i=0; i < 19; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityAH[i]));
				prf.setSummary(getString(ADDRESS_INFO_AH[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_FJ"))
		{
			for(int i=0; i < 5; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityFJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_FJ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_JX"))
		{
			for(int i=0; i < 11; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityJX[i]));
				prf.setSummary(getString(ADDRESS_INFO_JX[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_HN"))
		{
			for(int i=0; i < 3; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHN[i]));
				prf.setSummary(getString(ADDRESS_INFO_HN[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_HuBei"))
		{
			for(int i=0; i < 19; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHuiBei[i]));
				prf.setSummary(getString(ADDRESS_INFO_HuiBei[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_HuNan"))
		{
			for(int i=0; i < 10; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHuiNan[i]));
				prf.setSummary(getString(ADDRESS_INFO_HuiNan[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_GD"))
		{
			for(int i=0; i < 14; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityGD[i]));
				prf.setSummary(getString(ADDRESS_INFO_GD[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_GX"))
		{
			for(int i=0; i < 19; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityGX[i]));
				prf.setSummary(getString(ADDRESS_INFO_GX[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_HaiNan"))
		{
			for(int i=0; i < 1; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHaiNan[i]));
				prf.setSummary(getString(ADDRESS_INFO_HaiNan[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_YN"))
		{
			for(int i=0; i < 5; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityYN[i]));
				prf.setSummary(getString(ADDRESS_INFO_YN[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_GZ"))
		{
			for(int i=0; i < 3; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityGZ[i]));
				prf.setSummary(getString(ADDRESS_INFO_GZ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_SC"))
		{
			for(int i=0; i < 11; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(citySC[i]));
				prf.setSummary(getString(ADDRESS_INFO_SC[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_CQ"))
		{
			for(int i=0; i < 9; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityCQ[i]));
				prf.setSummary(getString(ADDRESS_INFO_CQ[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_ShanXi"))
		{
			for(int i=0; i < 8; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityShanXi[i]));
				prf.setSummary(getString(ADDRESS_INFO_ShanXi[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_GS"))
		{
			for(int i=0; i < 2; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityGS[i]));
				prf.setSummary(getString(ADDRESS_INFO_GS[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_NX"))
		{
			for(int i=0; i < 2; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityNX[i]));
				prf.setSummary(getString(ADDRESS_INFO_NX[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_QH"))
		{
			for(int i=0; i < 1; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityQH[i]));
				prf.setSummary(getString(ADDRESS_INFO_QH[i]));
				adapter.addPreference(prf);
			}
		}
		else if(City.equals("Province_XJ"))
		{
			for(int i=0; i < 1; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityXJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_XJ[i]));
				adapter.addPreference(prf);
			}
		}
		else
		{
			for(int i=0; i < 6; ++i)
			{
				Preference prf = new Preference(this);
				prf.setTitle(getString(cityHNJ[i]));
				prf.setSummary(getString(ADDRESS_INFO_HLJ[i]));
				adapter.addPreference(prf);
			}		
		}
    }
}
