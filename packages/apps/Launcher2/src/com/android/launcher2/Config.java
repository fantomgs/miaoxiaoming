/*
 * Copyright (C) 2011, Code Aurora Forum. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of Code Aurora Forum, Inc. nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.
 
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

package com.android.launcher2;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.database.Cursor;

import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.content.res.XmlResourceParser;
import android.os.SystemProperties;

import android.view.Gravity;
import android.widget.RelativeLayout;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;

import android.net.Uri;
import android.util.Log;
import android.util.Xml;
import android.provider.Settings;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;


import com.android.launcher.R;

public class Config {
    private final static String TAG = "czhu";
    private final static boolean LOGD = true;

    public static final String PROPERTY_THEME_CONFIG = "persist.sys.theme_config";

    private static Context mContext;

    private static Config INSTANCE;

    /** Used when obtaining a reference to the singleton instance. */
    private static Object INSTANCE_LOCK = new Object();
    private boolean mInitialized;

    //all for panel attributes
    public final static String INDICATOR = "indicator";
    public final static String HOTSEAT = "hotseat";
    public final static String WIDTH = "width";
    public final static String HEIGHT = "height";
    public final static String LEFT_MARGIN = "margin-left";
    public final static String RIGHT_MARGIN = "margin-right";
    public final static String DRAWABLE = "drawable";
    public final static String GRAVITY = "gravity";
    public final static String PANEL = "panel";
    public final static String LEFT_PADDING = "padding-left";
    public final static String RIGHT_PADDING = "padding-right";
    public final static String BACKGROUND = "background";
    public final static String LAYOUT = "layout";
    public final static String LAYOUT_RULE = "rule";
    public final static String LAYOUT_VALUE = "value";
    public final static String HOTSEAT_ID = "id";
    public final static String DIRECTION = "direction";
    public final static String INTENT = "intent";
    public final static String ALL_APP = "all-app";
    public final static String HOTSEAT_PANEL = "hotseat-panel";

    //for wallpaper
    public final static String WALLPAPER = "wallpaper";

    //for favorites
    public final static String FAVORITES = "favorites";
    public final static String FAVORITE = "favorite";
    public final static String FAVORITE_TYPE = "type";
    public final static String CLOCK = "clock";
    public final static String SEARCH = "search";
    public final static String APPWIDGET = "appwidget";
    public final static String SHORTCUT = "shortcut";

    public final static String CLASS_NAME = "className";
    public final static String PACKAGE_NAME = "packageName";
    public final static String SCREEN = "screen";
    public final static String CELL_X = "cellX";
    public final static String CELL_Y = "cellY";
    public final static String SPAN_X = "spanX";
    public final static String SPAN_Y = "spanY";
    public final static String ICON = "icon";
    public final static String TITLE = "title";
    public final static String URI = "uri";
    ArrayList<ScreenIndicator> mIndicators;
    ArrayList<Hotseat> mHotseats;
    HotseatPanel mHotseatPanel;
    int mWallpaper;
    int mWallpaperType;
    String mWallpaperInfo;
    ArrayList<Favorite>mFavorites;

    public static Config getInstance(Context context) {
        synchronized (INSTANCE_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new Config();
            }

            if (!INSTANCE.init(context)) {
                return null;
            }

            return INSTANCE;
        }
    }

    private boolean init(Context context) {
        if (mInitialized) return true;
        mInitialized = true;

        // This will be around as long as this process is
        mContext = context;
        readXml();
        initWallpaperInfo();
        return true;
    }

    private int getAttributeIntValue(XmlPullParser parser, String namespace,
            String name, int defaultValue) {
        String value = parser.getAttributeValue(namespace, name);
        if (value != null) {
            return Integer.valueOf(value);
        } else {
            return defaultValue;
        }
    }

    private void initWallpaperInfo() {
        Log.d(TAG, "initWallpaperInfo");
        String theme = SystemProperties.get(Config.PROPERTY_THEME_CONFIG, "config");
        String selectWhere = "themeName like ?";
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(LauncherSettings.ThemeInfo.CONTENT_URI,
            null, selectWhere, new String[] {theme}, null);
            if (cursor != null && cursor.moveToNext()) {
                mWallpaperType = cursor.getInt(cursor.getColumnIndex(LauncherSettings.ThemeInfo.WALLPAPER_TYPE));
                mWallpaperInfo = cursor.getString(cursor.getColumnIndex(LauncherSettings.ThemeInfo.WALLPAPER_INFO));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d(TAG, "leave initWallpaperInfo");
    }

    public void readXml() {
        Log.d(TAG, "in read xml");
        String themeFile = SystemProperties.get(PROPERTY_THEME_CONFIG, "config");
        Log.d(TAG, "theme file is" + themeFile);
        XmlPullParser parser = null;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            parser = factory.newPullParser();

            parser.setInput(mContext.openFileInput(themeFile + ".xml"), null);
        } catch (FileNotFoundException e) {
            int configFile = mContext.getResources().getIdentifier(themeFile, "xml", mContext.getPackageName());
            parser = mContext.getResources().getXml(configFile);
        } catch (XmlPullParserException xppe) {
            int configFile = mContext.getResources().getIdentifier(themeFile, "xml", mContext.getPackageName());
            parser = mContext.getResources().getXml(configFile);
        }

        try {
            int eventType = parser.getEventType();
            ScreenIndicator sIndicator = null;
            Hotseat sHotseat = null;
            Favorite sFavorite = null;
            mFavorites = new ArrayList<Favorite>();
            while(eventType != XmlPullParser.END_DOCUMENT) {
                 String name = null;

                 switch (eventType){
                 case XmlPullParser.START_DOCUMENT:

                     break;
                 case XmlPullParser.START_TAG:
                     name = parser.getName();
                     Log.d(TAG, "name is " + name);
                     if (name.equalsIgnoreCase(PANEL)){
                         mIndicators = new ArrayList<ScreenIndicator>();
                         mHotseats = new ArrayList<Hotseat>();
                     } else if (name.equalsIgnoreCase(INDICATOR)){
                         sIndicator = new ScreenIndicator();
                         sIndicator.setWidth(getAttributeIntValue(parser, null, WIDTH, 0));
                         sIndicator.setHeight(getAttributeIntValue(parser, null, HEIGHT, 0));
                         sIndicator.setDrawable(mContext.getResources().getIdentifier(
                                 parser.getAttributeValue(null, DRAWABLE),
                                 "drawable", mContext.getPackageName()));
                         sIndicator.setLeftMargin(getAttributeIntValue(parser, null,LEFT_MARGIN, 0));
                         sIndicator.setRightMargin(getAttributeIntValue(parser, null, RIGHT_MARGIN, 0));
                         sIndicator.setGravity(getAttributeIntValue(parser, null, GRAVITY, Gravity.NO_GRAVITY));
                         sIndicator.setDirection(getAttributeIntValue(parser, null, DIRECTION, -1));
                     } else if (name.equalsIgnoreCase(HOTSEAT)) {
                         sHotseat = new Hotseat();
                         sHotseat.setDrawable(mContext.getResources().getIdentifier(
                                                     parser.getAttributeValue(null, DRAWABLE),
                                                     "drawable", mContext.getPackageName()));
                         if (parser.getAttributeValue(null, BACKGROUND) != null) {
                             sHotseat.setBackground(mContext.getResources().getIdentifier(
                                                     parser.getAttributeValue(null, BACKGROUND),
                                                     "drawable", mContext.getPackageName()));
                         }
                         sHotseat.setId(getAttributeIntValue(parser, null, HOTSEAT_ID, 0));
                         sHotseat.setLeftPadding(getAttributeIntValue(parser, null, LEFT_PADDING, 0));
                         sHotseat.setRightPadding(getAttributeIntValue(parser, null, RIGHT_PADDING, 0));
                         sHotseat.setLeftMargin(getAttributeIntValue(parser, null,LEFT_MARGIN, 0));
                         sHotseat.setRightMargin(getAttributeIntValue(parser, null, RIGHT_MARGIN, 0));
                         sHotseat.isAllApp = getAttributeIntValue(parser, null, ALL_APP, 0);
                     } else if (sHotseat != null) {
                         if (name.equalsIgnoreCase(LAYOUT)) {
                             sHotseat.setLayouts(new Layout(getAttributeIntValue(parser, null, LAYOUT_RULE, RelativeLayout.ALIGN_BASELINE),
                                         Integer.parseInt(parser.nextText())));
                         } else if (name.equalsIgnoreCase(INTENT)) {
                             loadHotseatIntent(sHotseat, parser.nextText());
                         }
                     } else if (name.equalsIgnoreCase(HOTSEAT_PANEL)) {
                         mHotseatPanel = new HotseatPanel();
                         mHotseatPanel.setWidth(getAttributeIntValue(parser, null, WIDTH, 0));
                         mHotseatPanel.setHeight(getAttributeIntValue(parser, null, HEIGHT, 0));
                         mHotseatPanel.setGravity(getAttributeIntValue(parser, null, GRAVITY, 0));
                         if (parser.getAttributeValue(null, BACKGROUND) != null) {
                             mHotseatPanel.setBackground(mContext.getResources().getIdentifier(
                                                           parser.getAttributeValue(null, BACKGROUND),
                                                           "drawable", mContext.getPackageName()));
                          }
                     } else if (name.equalsIgnoreCase(WALLPAPER)) {
                         mWallpaper = mContext.getResources().getIdentifier(parser.nextText(),
                             "drawable", mContext.getPackageName());
                     } else if (name.equalsIgnoreCase(FAVORITES)) {
                         //mFavorites = new ArrayList<Favorite>();
                     } else if (name.equalsIgnoreCase(FAVORITE)) {
                         sFavorite = new Favorite();
                         sFavorite.setType(parser.getAttributeValue(null, FAVORITE_TYPE));
                         sFavorite.setCellX(getAttributeIntValue(parser, null, CELL_X, 0));
                         sFavorite.setCellY(getAttributeIntValue(parser, null, CELL_Y, 0));
                         sFavorite.setClassName(parser.getAttributeValue(null, CLASS_NAME));
                         sFavorite.setPackageName(parser.getAttributeValue(null, PACKAGE_NAME));
                         sFavorite.setSpanX(getAttributeIntValue(parser, null, SPAN_X, 0));
                         sFavorite.setSpanY(getAttributeIntValue(parser, null, SPAN_Y, 0));
                         sFavorite.setScreen(getAttributeIntValue(parser, null, SCREEN, 0));
                         sFavorite.setIcon(getAttributeIntValue(parser, null, ICON, 0));
                         sFavorite.setTitle(getAttributeIntValue(parser, null, TITLE, 0));
                         sFavorite.setUri(parser.getAttributeValue(null, URI));
                     }
                     break;
                 case XmlPullParser.END_TAG:
                     name = parser.getName();
                     if (name.equalsIgnoreCase(PANEL)){

                     } else if (name.equalsIgnoreCase(INDICATOR)) {
                         mIndicators.add(sIndicator);
                         Log.d(TAG, sIndicator.toString());
                         sIndicator = null;
                     } else if (name.equalsIgnoreCase(HOTSEAT)) {
                         mHotseats.add(sHotseat);
                         Log.d(TAG, sHotseat.toString());
                         sHotseat = null;
                     } else if (name.equalsIgnoreCase(FAVORITE)) {
                         mFavorites.add(sFavorite);
                         Log.d(TAG, sFavorite.toString());
                         sFavorite = null;
                     }
                     break;
             }
             eventType = parser.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static String[] FAVORITE_PROJECTION = {
        "intent",
        "screen",
        "cellX",
        "cellY",
        "spanX",
        "spanY",
        "itemType",
        "appWidgetId"
    };

    private static int COLUMN_INTENT      = 0;
    private static int COLUMN_SCREEN      = 1;
    private static int COLUMN_CELLX       = 2;
    private static int COLUMN_CELLY       = 3;
    private static int COLUMN_SPANX       = 4;
    private static int COLUMN_SPANY       = 5;
    private static int COLUMN_ITEMTYPE    = 6;
    private static int COLUMN_APPWIDGETID = 7;

    public void saveTheme() {
        if (LOGD) Log.d(TAG, "saveTheme");
        ContentResolver resolver = mContext.getContentResolver();
        String theme = SystemProperties.get(Config.PROPERTY_THEME_CONFIG, "config");
        Cursor cursor = null;
        try {
            ContentValues themeValues = new ContentValues();
            themeValues.put(LauncherSettings.ThemeInfo.THEME_NAME, theme);
            themeValues.put(LauncherSettings.ThemeInfo.IS_SAVED, true);

            WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(mContext).getWallpaperInfo();
            if (wallpaperInfo != null) {
                themeValues.put(LauncherSettings.ThemeInfo.WALLPAPER_TYPE, LauncherSettings.ThemeInfo.TYPE_LIVE);
                themeValues.put(LauncherSettings.ThemeInfo.WALLPAPER_INFO, wallpaperInfo.getComponent().flattenToString());
            } else {
                saveWallpaper(theme);
                themeValues.put(LauncherSettings.ThemeInfo.WALLPAPER_TYPE, LauncherSettings.ThemeInfo.TYPE_FILE);
                themeValues.put(LauncherSettings.ThemeInfo.WALLPAPER_INFO, theme);
            }

            Log.d(TAG, "theme info saved");

            int themeId = insertOrUpdateThemeInfo(theme, themeValues);

            if (themeId == -1) {
                Log.e(TAG, "theme info insert or update error");
                return;
            }

            String selectWhere = LauncherSettings.ThemeFavorites.THEME_ID + " = ?";
            // delete old saved entry first
            resolver.delete(LauncherSettings.ThemeFavorites.CONTENT_URI, selectWhere,
                new String[] {String.valueOf(themeId)});

            // query current favorites and saved to theme_favorites.
            cursor = resolver.query(LauncherSettings.Favorites.CONTENT_URI,
                null, null, null, null);
            if (cursor != null) {
                ContentValues values = new ContentValues();
                while (cursor.moveToNext()) {
                    values.clear();
                    int columnIndex = 1;
                    values.put("title", cursor.getString(columnIndex++));
                    values.put("intent", cursor.getString(columnIndex++));
                    values.put("container", cursor.getInt(columnIndex++));
                    values.put("screen", cursor.getInt(columnIndex++));
                    values.put("cellX", cursor.getInt(columnIndex++));
                    values.put("cellY", cursor.getInt(columnIndex++));
                    values.put("spanX", cursor.getInt(columnIndex++));
                    values.put("spanY", cursor.getInt(columnIndex++));
                    values.put("itemType", cursor.getInt(columnIndex++));
                    values.put("appWidgetId", cursor.getInt(columnIndex++));
                    values.put("isShortcut", cursor.getInt(columnIndex++));
                    values.put("iconType", cursor.getInt(columnIndex++));
                    values.put("iconPackage", cursor.getString(columnIndex++));
                    values.put("iconResource", cursor.getString(columnIndex++));
                    values.put("icon", cursor.getBlob(columnIndex++));
                    values.put("uri", cursor.getString(columnIndex++));
                    values.put("displayMode", cursor.getInt(columnIndex++));
                    values.put("themeId", themeId);
                    resolver.insert(LauncherSettings.ThemeFavorites.CONTENT_URI, values);
                }

                if (LOGD) Log.d(TAG, "theme favorites saved");
            }
        }catch (Exception e) {
            Log.e(TAG, "saveTheme error:", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // return the _id of the inserted row or updated row.
    private int insertOrUpdateThemeInfo(String theme, ContentValues values) {
        ContentResolver resolver = mContext.getContentResolver();
        String selectWhere = LauncherSettings.ThemeInfo.THEME_NAME + " like ?";
        Cursor cursor = null;
        int ret = -1;
        try {
            cursor = resolver.query(LauncherSettings.ThemeInfo.CONTENT_URI, null, selectWhere, new String[] {theme}, null);
            if (cursor != null) {
                if (cursor.getCount() == 0) {
                    Uri uri = resolver.insert(LauncherSettings.ThemeInfo.CONTENT_URI, values);
                    ret = Integer.parseInt(uri.getLastPathSegment());
                } else {
                    if (cursor.moveToFirst()) {
                        ret = cursor.getInt(0);
                    }
                    resolver.update(LauncherSettings.ThemeInfo.CONTENT_URI,
                        values, selectWhere, new String[] {theme});
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return ret;
    }

    public void saveXml() {
        Log.d(TAG, "in saveXml");
        String themeFile = SystemProperties.get(PROPERTY_THEME_CONFIG, "config");
        Log.d(TAG, "theme file is" + themeFile);
        try {
            int configFile = mContext.getResources().getIdentifier(themeFile, "xml", mContext.getPackageName());
            XmlResourceParser parser = mContext.getResources().getXml(configFile);

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(mContext.openFileOutput(themeFile + ".xml", mContext.MODE_PRIVATE), null);

            serializer.startDocument("UTF-8",true);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                 if(eventType == XmlPullParser.START_TAG) {
                     logd("Start tag "+parser.getName());
                     String tagName = parser.getName();
                     if (FAVORITE.equalsIgnoreCase(tagName)) {
                        // ignore FAVORITE tag
                     } else if (FAVORITES.equalsIgnoreCase(tagName)) {
                        // save FAVORITES from current databases to xml file
                        serializer.startTag(null, tagName);
                        saveFavorites(serializer);
                     } else if (WALLPAPER.equalsIgnoreCase(tagName)) {
                        WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(mContext).getWallpaperInfo();
                        if (wallpaperInfo != null) {
                            serializer.startTag(null, tagName);
                            serializer.attribute(null, PACKAGE_NAME, wallpaperInfo.getPackageName());
                            serializer.attribute(null, CLASS_NAME, wallpaperInfo.getServiceName());
                        } else {
                            serializer.startTag(null, tagName);
                            saveWallpaper(themeFile);
                        }
                     } else {
                         serializer.startTag(null, tagName);
                         int count = parser.getAttributeCount();
                         for (int i = 0; i < count; i++) {
                             serializer.attribute(null, parser.getAttributeName(i), parser.getAttributeValue(i));
                         }
                     }
                 } else if(eventType == XmlPullParser.END_TAG) {
                     logd("End tag "+parser.getName());
                     String tagName = parser.getName();
                     if (FAVORITE.equalsIgnoreCase(tagName)) {
                        // ignore FAVORITE tag
                     } else {
                        serializer.endTag(null, parser.getName());
                     }
                 } else if(eventType == XmlPullParser.TEXT) {
                     logd("Text "+parser.getText());
                     serializer.text(parser.getText());
                 }
                 eventType = parser.next();
            }
            logd("End document");
            serializer.endDocument();
        } catch (Exception e) {
            Log.e(TAG, "saveXml error", e);
            mContext.deleteFile(themeFile + ".xml");
        }
    }

    private void writeBitmap(Bitmap bitmap, String fileName) throws FileNotFoundException {
        FileOutputStream out = mContext.openFileOutput(fileName, mContext.MODE_PRIVATE);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            Log.w(TAG, "Could not write wallpaper");
        }
    }

    private void saveWallpaper(String theme) {
        try {
            Drawable dr = WallpaperManager.getInstance(mContext).getDrawable();
            byte[] data = null;
            if (dr != null) {
                Bitmap bitmap = ((BitmapDrawable) dr).getBitmap();
                if (bitmap != null) {
                    writeBitmap(bitmap, theme + ".png");
                }
            }
        }catch (Exception e) {
            Log.e(TAG, "saveWallpaper error", e);
        }
    }

    public Bitmap getWallpaperBitmap() {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(mContext.openFileInput(mWallpaperInfo + ".png"));
        } catch (Exception e) {
            Log.e(TAG, "getWallpaper error", e);
        }

        return bitmap;
    }

    private void saveFavorites(XmlSerializer serializer) {
        try {
            Cursor cursor = mContext.getContentResolver()
                .query(LauncherSettings.Favorites.CONTENT_URI, FAVORITE_PROJECTION, null, null, null);
            if (cursor != null) {
                logd("cursor count: " + cursor.getCount());
            }
            while (cursor.moveToNext()) {
                /* 1. read favorite info from databases. */
                Favorite item = new Favorite();
                item.setScreen(cursor.getInt(COLUMN_SCREEN));
                item.setCellX(cursor.getInt(COLUMN_CELLX));
                item.setCellY(cursor.getInt(COLUMN_CELLY));
                item.setSpanX(cursor.getInt(COLUMN_SPANX));
                item.setSpanY(cursor.getInt(COLUMN_SPANY));
                int itemType = cursor.getInt(COLUMN_ITEMTYPE);
                switch (itemType) {
                    case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION: {
                        item.setType(FAVORITE);
                        String intentUri = cursor.getString(COLUMN_INTENT);
                        Intent intent = Intent.parseUri(intentUri, 0);
                        ComponentName cn = intent.getComponent();
                        logd("app shortcut comp: " + cn);
                        item.setPackageName(cn.getPackageName());
                        item.setClassName(cn.getClassName());
                        break;
                    }
                    case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET: {
                        item.setType(APPWIDGET);
                        int appWidgetId = cursor.getInt(COLUMN_APPWIDGETID);
                        logd("appwidget id: " + appWidgetId);
                        final AppWidgetProviderInfo appWidgetInfo = AppWidgetManager.getInstance(mContext).getAppWidgetInfo(appWidgetId);
                        if (appWidgetInfo != null) {
                            ComponentName cn = appWidgetInfo.provider;
                            logd("appwidget comp: " + cn);
                            item.setPackageName(cn.getPackageName());
                            item.setClassName(cn.getClassName());
                        } else {
                            logd("favorite without component info: " + item);
                            continue;
                        }
                        break;
                    }
                    default: {
                        // TODO:
                        // We ignore favorite of other type now. Should implement it later.
                        logd("favorite without component info: " + item);
                        continue;
                    }
                }

                /* 2. write favorite item into xml as tag favorite. */
                serializer.startTag(null, FAVORITE);
                serializer.attribute(null, SCREEN, String.valueOf(item.getScreen()));
                serializer.attribute(null, CELL_X, String.valueOf(item.getCellX()));
                serializer.attribute(null, CELL_Y, String.valueOf(item.getCellY()));
                serializer.attribute(null, SPAN_X, String.valueOf(item.getSpanX()));
                serializer.attribute(null, SPAN_Y, String.valueOf(item.getSpanY()));
                serializer.attribute(null, FAVORITE_TYPE, item.getType());
                serializer.attribute(null, PACKAGE_NAME, item.getPackageName());
                serializer.attribute(null, CLASS_NAME, item.getClassName());
                serializer.endTag(null, FAVORITE);
            }
        } catch (Exception e) {
            Log.e(TAG, "saveFavorites error", e);
        }
    }

    public class ScreenIndicator {
        private int width;
        private int height;
        private int drawable;
        private int leftMargin;
        private int rightMargin;
        private int gravity;
        private int direction;

        public ScreenIndicator() {
        }

        public void setWidth(int width) {
            this.width = convertDipToPx(width);
        }

        public int getWidth() {
            return this.width;
        }

        public void setHeight(int height) {
            this.height = convertDipToPx(height);
        }

        public int getHeight() {
            return this.height;
        }

        public void setDrawable(int drawable) {
            this.drawable = drawable;
        }

        public int getDrawable() {
            return this.drawable;
        }

        public void setLeftMargin(int leftMargin) {
            this.leftMargin = convertDipToPx(leftMargin);
        }

        public int getLeftMargin() {
            return this.leftMargin;
        }

        public void setRightMargin(int rightMargin) {
            this.rightMargin = convertDipToPx(rightMargin);
        }

        public int getRightMargin() {
            return this.rightMargin;
        }

        public void setGravity(int gravity) {
            this.gravity = gravity;
        }

        public int getGravity() {
            return this.gravity;
        }

        public void setDirection(int direction) {
            this.direction = direction;
        }

        public int getDirection() {
            return direction;
        }

        @Override
        public String toString() {
            return "Indicator Config{"
                + Integer.toHexString(System.identityHashCode(this))
                + "/w: " + width + "/h: " + height + "/d: " + drawable
                + "/lm: " + leftMargin + "/rm: " + rightMargin
                + "/gv: " + gravity + "}";
        }
    }

    public class Hotseat {
        private int id;
        private int drawable;
        private int background;
        private int leftPadding;
        private int rightPadding;
        private int leftMargin;
        private int rightMargin;
        private ArrayList<Layout> layouts;
        private Intent intent;
        private CharSequence label;

        public int isAllApp;

        public Hotseat() {
            layouts = new ArrayList<Layout>();
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setDrawable(int drawable) {
            this.drawable = drawable;
        }

        public int getDrawable() {
            return drawable;
        }

        public void setBackground(int background) {
            this.background = background;
        }

        public int getBackground() {
            return background;
        }

        public void setLeftPadding(int leftPadding) {
            this.leftPadding = convertDipToPx(leftPadding);
        }

        public int getLeftPadding() {
            return leftPadding;
        }

        public void setRightPadding(int rightPadding) {
            this.rightPadding = convertDipToPx(rightPadding);
        }

        public int getRightPadding() {
            return rightPadding;
        }

        public void setLeftMargin(int leftMargin) {
            this.leftMargin = convertDipToPx(leftMargin);
        }

        public int getLeftMargin() {
            return this.leftMargin;
        }

        public void setRightMargin(int rightMargin) {
            this.rightMargin = convertDipToPx(rightMargin);
        }

        public int getRightMargin() {
            return this.rightMargin;
        }

        public void setLayouts(Layout layout) {
            layouts.add(layout);
        }

        public int getLayoutsSize() {
            return layouts.size();
        }

        public int getLayoutRule(int index) {
            return layouts.get(index).getRule();
        }

        public int getLayoutValue(int index) {
            return layouts.get(index).getValue();
        }

        public void setIntent(Intent intent) {
            this.intent = intent;
        }

        public Intent getIntent() {
            return intent;
        }

        public void setLabel(CharSequence label) {
            this.label = label;
        }

        public CharSequence getLabel() {
            return label;
        }

        public boolean isAllAppView() {
            return (isAllApp == 1);
        }

        @Override
        public String toString() {
            return "Hotseat Config{"
                + Integer.toHexString(System.identityHashCode(this))
                + "/d: " + drawable + "/b: " + background
                + "/lm: " + leftMargin + "/rm: " + rightMargin
                + "/lp: " + leftPadding + "/rp: " +  rightPadding
                + "/layout: " + layouts + "}";
        }
    }

    public class Layout {
        private int rule;
        private int value;

        public Layout(int rule, int value) {
            this.rule = rule;
            this.value = value;
        }

        public int getRule() {
            return rule;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "Layout Config{"
                + Integer.toHexString(System.identityHashCode(this))
                + "/rule: " + rule + "/value: " + value  + "}";
        }
    }

    public class HotseatPanel {
        private int width;
        private int height;
        private int gravity;
        private int background;

        public void setWidth(int width) {
            this.width = convertDipToPx(width);
        }

        public int getWidth() {
            return width;
        }

        public void setHeight(int height) {
            this.height = convertDipToPx(height);
        }

        public int getHeight() {
            return height;
        }

        public void setGravity(int gravity) {
            this.gravity = gravity;
        }

        public int getGravity() {
            return gravity;
        }

        public void setBackground(int background) {
            this.background = background;
        }

        public int getBackground() {
            return background;
        }
    }


    public class Favorite {
        private String packageName;
        private String className;
        private int screen;
        private int cellX;
        private int cellY;
        private int spanX;
        private int spanY;
        private int icon;
        private int title;
        private String uri;

        private String type;


        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getClassName() {
            return className;
        }

        public void setScreen(int screen) {
            this.screen = screen;
        }

        public int getScreen() {
            return screen;
        }

        public void setCellX(int x) {
            this.cellX = x;
        }

        public int getCellX() {
            return cellX;
        }

        public void setCellY(int y) {
            this.cellY = y;
        }

        public int getCellY() {
            return cellY;
        }

        public void setSpanX(int x) {
            this.spanX = x;
        }

        public int getSpanX() {
            return spanX;
        }

        public void setSpanY(int y) {
            this.spanY = y;
        }

        public int getSpanY() {
            return spanY;
        }

        //resource id
        public void setIcon(int icon) {
            this.icon = icon;
        }

        public int getIcon() {
            return icon;
        }

        //resource id
        public void setTitle(int title) {
            this.title = title;
        }

        public int getTitle() {
            return title;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        @Override
        public String toString() {
            return "Favorite Config{"
                + Integer.toHexString(System.identityHashCode(this))
                + "/type: " + type + "/screen: " + screen
                + "/class: " + className
                + "/package: " + packageName + "/cellX: " + cellX
                + "/cellY: " + cellY + "/spanX: " + spanX
                + "/spanY: " + spanY + "/icon: " + icon
                + "/title: " + title + "/uri: " + uri + "}";
        }
    }
    public static int convertDipToPx(float dipValue){
        final float scale = mContext.getResources().getDisplayMetrics().density;
        return (int)(dipValue * scale + 0.5f);
    }

    public ScreenIndicator[] getIndicators() {
        return (ScreenIndicator[])mIndicators.toArray(new ScreenIndicator[mIndicators.size()]);
    }

    public Hotseat[] getHotseats() {
        return (Hotseat[])mHotseats.toArray(new Hotseat[mHotseats.size()]);
    }

    public HotseatPanel getHotseatPanel() {
        return mHotseatPanel;
    }

    public int getPanelHeight() {
        int siHeight = 0;
        for (ScreenIndicator si : mIndicators) {
            siHeight = (si.getHeight() > siHeight) ? si.getHeight() : siHeight;
        }
        return (siHeight + mHotseatPanel.getHeight());
    }

    public int getWallpaperType() {
        return mWallpaperType;
    }

    public int getWallpaper() {
        return mWallpaper;
    }

    public ComponentName getWallpaperComponent() {
        return ComponentName.unflattenFromString(mWallpaperInfo);
    }

    public Favorite[] getFavorites() {
        return (Favorite[])mFavorites.toArray(new Favorite[mFavorites.size()]);
    }

    // Load the Intent templates from arrays.xml to populate the hotseats. For
    // each Intent, if it resolves to a single app, use that as the launch
    // intent & use that app's label as the contentDescription. Otherwise,
    // retain the ResolveActivity so the user can pick an app.
    private void loadHotseatIntent(Hotseat hotseat, String hotseatConfig) {
        PackageManager pm = mContext.getPackageManager();
        Intent intent = null;
        if (hotseatConfig.equals("*BROWSER*")) {
            // magic value meaning "launch user's default web browser"
            // replace it with a generic web request so we can see if there is indeed a default
            String defaultUri = mContext.getString(R.string.default_browser_url);
            intent = new Intent(
                     Intent.ACTION_VIEW,
                        ((defaultUri != null)
                            ? Uri.parse(defaultUri)
                            : getDefaultBrowserUri())
                    ).addCategory(Intent.CATEGORY_BROWSABLE);
                // note: if the user launches this without a default set, she
                // will always be taken to the default URL above; this is
                // unavoidable as we must specify a valid URL in order for the
                // chooser to appear, and once the user selects something, that
                // URL is unavoidably sent to the chosen app.
         } else {
             try {
                intent = Intent.parseUri(hotseatConfig, 0);
            } catch (java.net.URISyntaxException ex) {
                Log.w(TAG, "Invalid hotseat intent: " + hotseatConfig);
                // bogus; leave intent=null
            }
         }

            if (intent == null) {
                hotseat.setIntent(null);
                hotseat.setLabel(mContext.getText(R.string.activity_not_found));
                return;
            }

            if (LOGD) {
                Log.d(TAG, "loadHotseats: hotseat "
                    + " initial intent=["
                    + intent.toUri(Intent.URI_INTENT_SCHEME)
                    + "]");
            }

            ResolveInfo bestMatch = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            List<ResolveInfo> allMatches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (LOGD) {
                Log.d(TAG, "Best match for intent: " + bestMatch);
                Log.d(TAG, "All matches: ");
                for (ResolveInfo ri : allMatches) {
                    Log.d(TAG, "  --> " + ri);
                }
            }
            // did this resolve to a single app, or the resolver?
            if (allMatches.size() == 0 || bestMatch == null) {
                // can't find any activity to handle this. let's leave the
                // intent as-is and let Launcher show a toast when it fails
                // to launch.
                hotseat.setIntent(intent);

                // set accessibility text to "Not installed"
                hotseat.setLabel(mContext.getText(R.string.activity_not_found));
            } else {
                boolean found = false;
                for (ResolveInfo ri : allMatches) {
                    if (bestMatch.activityInfo.name.equals(ri.activityInfo.name)
                        && bestMatch.activityInfo.applicationInfo.packageName
                            .equals(ri.activityInfo.applicationInfo.packageName)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    if (LOGD) Log.d(TAG, "Multiple options, no default yet");
                    // the bestMatch is probably the ResolveActivity, meaning the
                    // user has not yet selected a default
                    // so: we'll keep the original intent for now
                    hotseat.setIntent(intent);

                    // set the accessibility text to "Select shortcut"
                    hotseat.setLabel(mContext.getText(R.string.title_select_shortcut));
                } else {
                    // we have an app!
                    // now reconstruct the intent to launch it through the front
                    // door
                    ComponentName com = new ComponentName(
                        bestMatch.activityInfo.applicationInfo.packageName,
                        bestMatch.activityInfo.name);
                    hotseat.setIntent(new Intent(Intent.ACTION_MAIN).setComponent(com));

                    // load the app label for accessibility
                    hotseat.setLabel(bestMatch.activityInfo.loadLabel(pm));
                }
            }

            /*if (LOGD) {
                Log.d(TAG, "loadHotseats: hotseat " + i
                    + " final intent=["
                    + ((mHotseats[i] == null)
                        ? "null"
                        : mHotseats[i].toUri(Intent.URI_INTENT_SCHEME))
                    + "] label=[" + mHotseatLabels[i]
                    + "]"
                    );*/
            //}
    }

    private Uri getDefaultBrowserUri() {
        String url = mContext.getString(R.string.default_browser_url);
        if (url.indexOf("{CID}") != -1) {
            url = url.replace("{CID}", "android-google");
        }
        return Uri.parse(url);
    }

    private void logd(String msg) {
        Log.d(TAG, msg);
    }
}
