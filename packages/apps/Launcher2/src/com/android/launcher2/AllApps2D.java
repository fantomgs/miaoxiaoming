/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.Collections;

import com.android.launcher.R;
import com.android.launcher2.LauncherSettings.Favorites;
import com.android.launcher2.LauncherSettings.LauncherInfo;

public class AllApps2D
        extends RelativeLayout
        implements AllAppsView,
                   AdapterView.OnItemClickListener,
                   AdapterView.OnItemLongClickListener,
                   View.OnKeyListener,
                   DragSource {

    private static final String TAG = "Launcher.AllApps2D";
    private static final boolean DEBUG = false;

    private Launcher mLauncher;
    private DragController mDragController;

    private GridView mGrid;

    private ArrayList<ApplicationInfo> mAllAppsList = new ArrayList<ApplicationInfo>();

    // preserve compatibility with 3D all apps:
    //    0.0 -> hidden
    //    1.0 -> shown and opaque
    //    intermediate values -> partially shown & partially opaque
    private float mZoom;

    private AppsAdapter mAppsAdapter;

    private static final int SORT_BY_ALPHABET = 0;
    private static final int SORT_BY_FAVORITE = 1;
    private int mMode = 0;
    private static final String[] SORT_PROJECTION = { LauncherInfo.CLASS_NAME, LauncherInfo.LAUNCH_COUNT };
    // ------------------------------------------------------------
    private static final String PRE_PACKAGE_NAME = "com.android.launcher.res";
    private boolean mPreInstallConfig = false;
    private ArrayList<String> mPreClassArray = new ArrayList<String> ();
    private ArrayList<ApplicationInfo> mPreAppList = new ArrayList<ApplicationInfo>();

    public static class HomeButton extends ImageButton {
        public HomeButton(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        @Override
        public View focusSearch(int direction) {
            if (direction == FOCUS_UP) return super.focusSearch(direction);
            if (direction == FOCUS_LEFT) return super.focusSearch(direction);
            return null;
        }
    }

    public class AppsAdapter extends ArrayAdapter<ApplicationInfo> {
        private final LayoutInflater mInflater;

        public AppsAdapter(Context context, ArrayList<ApplicationInfo> apps) {
            super(context, 0, apps);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ApplicationInfo info = getItem(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.application_boxed, parent, false);
            }

//            if (!info.filtered) {
//                info.icon = Utilities.createIconThumbnail(info.icon, getContext());
//                info.filtered = true;
//            }

            final TextView textView = (TextView) convertView;
            if (DEBUG) {
                Log.d(TAG, "icon bitmap = " + info.iconBitmap 
                    + " density = " + info.iconBitmap.getDensity());
            }
            info.iconBitmap.setDensity(Bitmap.DENSITY_NONE);
            textView.setCompoundDrawablesWithIntrinsicBounds(null, new BitmapDrawable(info.iconBitmap), null, null);
            textView.setText(info.title);

            return convertView;
        }
    }

    public AllApps2D(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(View.GONE);
        setSoundEffectsEnabled(false);

        mAppsAdapter = new AppsAdapter(getContext(), mAllAppsList);
        mAppsAdapter.setNotifyOnChange(false);

        Context prePackageContext = null;
        try {
            prePackageContext = context.createPackageContext(PRE_PACKAGE_NAME,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            Log.e("AllApps2D", "Create Res Apk Failed");
        }

        if (prePackageContext != null) {
            final int resId = prePackageContext.getResources().getIdentifier("pre_install_class", "array", PRE_PACKAGE_NAME);
            final String[] preApps = prePackageContext.getResources().getStringArray(resId);
            final int size = preApps.length;
            mPreInstallConfig = true;
            mPreClassArray.clear();
            for (int i=0;i<size;i++) {
                mPreClassArray.add(preApps[i]);
            }
            mPreAppList.clear();
        }
    }

    @Override
    protected void onFinishInflate() {
        setBackgroundColor(Color.BLACK);

        try {
            mGrid = (GridView)findViewWithTag("all_apps_2d_grid");
            if (mGrid == null) throw new Resources.NotFoundException();
            mGrid.setOnItemClickListener(this);
            mGrid.setOnItemLongClickListener(this);
            mGrid.setBackgroundColor(Color.BLACK);
            mGrid.setCacheColorHint(Color.BLACK);
            
            ImageButton homeButton = (ImageButton) findViewWithTag("all_apps_2d_home");
            if (homeButton == null) throw new Resources.NotFoundException();
            homeButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        mLauncher.closeAllApps(true);
                    }
                });
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllApps2D");
        }

        setOnKeyListener(this);
    }

    public AllApps2D(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!isVisible()) return false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                mLauncher.closeAllApps(true);
                break;
            default:
                return false;
        }

        return true;
    }

    public void onItemClick(AdapterView parent, View v, int position, long id) {
        final ApplicationInfo mApptoRun = (ApplicationInfo) parent.getItemAtPosition(position);
        final String packageName = mApptoRun.componentName.getPackageName();
        final String className = mApptoRun.componentName.getClassName();
        mLauncher.startActivitySafely(mApptoRun.intent, mApptoRun);
        //query which ap
        Thread updateLaunchInfoThread = new Thread(new Runnable() {
            public void run() {
                Log.i(TAG,"onItemClick(): packageName="+packageName+" & className="+className);
                ContentResolver contentResolver = mLauncher.getContentResolver();
                Utilities.updateLaunchInfo(contentResolver,packageName,className);
            }
        });
        updateLaunchInfoThread.start();
        if(mMode==SORT_BY_FAVORITE)
            updateOnClick(packageName,className);
    }


    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (!view.isInTouchMode()) {
            return false;
        }

        ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
        app = new ApplicationInfo(app);

        mDragController.startDrag(view, this, app, DragController.DRAG_ACTION_COPY);
        mLauncher.closeAllApps(true);

        return true;
    }

    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect prev) {
        if (gainFocus) {
            mGrid.requestFocus();
        }
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void onDropCompleted(View target, boolean success) {
    }

    /**
     * Zoom to the specifed level.
     *
     * @param zoom [0..1] 0 is hidden, 1 is open
     */
    public void zoom(float zoom, boolean animate) {
//        Log.d(TAG, "zooming " + ((zoom == 1.0) ? "open" : "closed"));
        cancelLongPress();

        mZoom = zoom;

        if (isVisible()) {
            getParent().bringChildToFront(this);
            setVisibility(View.VISIBLE);
            mGrid.setAdapter(mAppsAdapter);
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.all_apps_2d_fade_in));
            } else {
                onAnimationEnd();
            }
        } else {
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.all_apps_2d_fade_out));
            } else {
                onAnimationEnd();
            }
        }
    }

    protected void onAnimationEnd() {
        if (!isVisible()) {
            setVisibility(View.GONE);
            mGrid.setAdapter(null);
            mZoom = 0.0f;
        } else {
            mZoom = 1.0f;
        }

        mLauncher.zoomed(mZoom);
    }

    public boolean isVisible() {
        return mZoom > 0.001f;
    }

    @Override
    public boolean isOpaque() {
        return mZoom > 0.999f;
    }

    public void setApps(ArrayList<ApplicationInfo> list, int sortBy) {
        mMode = sortBy;
        mAppsAdapter.clear();
        addApps(list,sortBy,true);
    }

    public void addApps(ArrayList<ApplicationInfo> list, int sortBy, boolean isSetup) {

        ArrayList<ApplicationInfo> tempArray = new ArrayList<ApplicationInfo>();
        if (mPreInstallConfig) {
            mAllAppsList.removeAll(mPreAppList);
        }
        final int N = list.size();

        if(sortBy==SORT_BY_ALPHABET){
            for (int i=0; i<N; i++) {
                final ApplicationInfo item = list.get(i);
                int index = Collections.binarySearch(mAllAppsList, item,
                        LauncherModel.APP_NAME_COMPARATOR);
                if (index < 0) {
                    index = -(index+1);
                }
                if (mPreInstallConfig && isSetup && mPreClassArray.contains(item.componentName.getClassName())) {
                    tempArray.add(item);
                    continue;
                }
                mAllAppsList.add(index,item);
            }
        }else{
        if(isSetup){
            Cursor c = null;
            try {
                c = mLauncher.getContentResolver().query(LauncherInfo.CONTENT_URI,
                        SORT_PROJECTION, null, null,null);

                if (c != null && c.moveToFirst()) {
                    do{
                        for(int j=0;j<N;j++){
                            if(c.getString(0).equals(list.get(j).componentName.getClassName())){
                                list.get(j).count = c.getInt(1);
                                continue;
                            }
                        }
                    }while(c.moveToNext());

                    for(int k=0;k<N;k++){
                        final ApplicationInfo item = list.get(k);
                        int index = Collections.binarySearch(mAllAppsList, item,
                                LauncherModel.APP_COUNT_COMPARATOR);
                        if (index < 0) {
                            index = -(index+1);
                        }
                        if (mPreInstallConfig && mPreClassArray.contains(item.componentName.getClassName())) {
                            tempArray.add(item);
                            continue;
                        }
                        mAllAppsList.add(index,item);
                    }
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }else{
            for(int j=0;j<N;j++){
                        final ApplicationInfo item = list.get(j);
                        int index = Collections.binarySearch(mAllAppsList, item,
                                LauncherModel.APP_COUNT_COMPARATOR);
                        if (index < 0) {
                            index = -(index+1);
                        }
                        mAllAppsList.add(index,item);
                }
        }
        }
        Log.d(TAG, "addApps2: " + list.size() + " apps: " + list.toString());
        if (mPreInstallConfig) {
            if (isSetup) {
                int preAppSize =mPreClassArray.size();
                int tempSize = tempArray.size();
                mPreAppList.clear();
                for (int i=0;i<preAppSize;i++) {
                    for (int temp=0;temp<tempSize;temp++) {
                        if (mPreClassArray.get(i).equals(tempArray.get(temp).componentName.getClassName())) {
                            mPreAppList.add(tempArray.get(temp));
                            break;
                        }
                    }
                }
                Log.i(TAG,"add app unicom = "+mPreAppList);
            }
            mAllAppsList.addAll(0,mPreAppList);
        }
        mAppsAdapter.notifyDataSetChanged();
    }

    public void removeApps(ArrayList<ApplicationInfo> list) {
        final int N = list.size();
        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = findAppByComponent(mAllAppsList, item);
            if (index >= 0) {
                //mAppsAdapter.remove(item);
                mAllAppsList.remove(index);
            } else {
                Log.w(TAG, "couldn't find a match for item \"" + item + "\"");
                // Try to recover.  This should keep us from crashing for now.
            }
        }
        mAppsAdapter.notifyDataSetChanged();
    }

    public void updateApps(ArrayList<ApplicationInfo> list, int sortBy) {
        // Just remove and add, because they may need to be re-sorted.
        removeApps(list);
        mLauncher.removeAppInfo(list);
        addApps(list,sortBy,false);
        mLauncher.addAppInfo(list);
    }

    private static int findAppByComponent(ArrayList<ApplicationInfo> list, ApplicationInfo item) {
        ComponentName component = item.intent.getComponent();
        final int N = list.size();
        for (int i=0; i<N; i++) {
            ApplicationInfo x = list.get(i);
            if (x.intent.getComponent().equals(component)) {
                return i;
            }
        }
        return -1;
    }

    public void dumpState() {
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList", mAllAppsList);
    }
    
    public void surrender() {
    }

    private void updateAppLaunchCount(String componentName, String className) {
        ContentResolver cr = mLauncher.getContentResolver();
        Cursor c = null;
        int count = 0;
        String where = LauncherInfo.PACKAGE_NAME + "=? AND "
                + LauncherInfo.CLASS_NAME + "=?";
        String[] selectionArgs = { componentName, className };
        try {
            c = cr.query(LauncherInfo.CONTENT_URI,
                    new String[] { LauncherInfo.LAUNCH_COUNT }, where,
                    selectionArgs, null);
            if(c!=null && c.moveToFirst()){
                count = c.getInt(c.getColumnIndex(LauncherInfo.LAUNCH_COUNT));
            }
        } finally {
            if(c!=null){
                c.close();
            }
        }
        if(0==count){
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put(LauncherInfo.LAUNCH_COUNT, ++count);
        cr.update(LauncherInfo.CONTENT_URI, cv, where, selectionArgs);
    }

    public void sortApp(int sortBy){
        mMode = sortBy;

        switch(sortBy){
        case 0:
            sortByAlphabet();
            break;
        case 1:
            sortByFavorite();
            break;
        default:
            break;
        }
    }

    private void sortByAlphabet(){
        if (mPreInstallConfig) {
            mAllAppsList.removeAll(mPreAppList);
        }
        mAppsAdapter.sort(LauncherModel.APP_NAME_COMPARATOR);
        if (mPreInstallConfig) {
            mAllAppsList.addAll(0,mPreAppList);
        }
        mAppsAdapter.notifyDataSetChanged();
        mGrid.onLayoutChanged(true);
    }

    private void sortByFavorite() {
        Cursor c = null;
        try {
            c = mLauncher.getContentResolver().query(LauncherInfo.CONTENT_URI,
                    SORT_PROJECTION, null, null,null);

            if (c != null && c.moveToFirst()) {
                int count = mAllAppsList.size();

                do{
                    for(int j=0;j<count;j++){
                        if(c.getString(0).equals(mAllAppsList.get(j).componentName.getClassName())){
                            mAllAppsList.get(j).count = c.getInt(1);
                            continue;
                        }
                    }
                } while (c.moveToNext());
                if (mPreInstallConfig) {
                    mAllAppsList.removeAll(mPreAppList);
                }
                mAppsAdapter.sort(LauncherModel.APP_COUNT_COMPARATOR);
                if (mPreInstallConfig) {
                    mAllAppsList.addAll(0,mPreAppList);
                }
                mAppsAdapter.notifyDataSetChanged();
                mGrid.onLayoutChanged(true);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public void updateOnClick(String packageName, String className){
        if (mPreInstallConfig && mPreClassArray.contains(className)) {
            return;
        }
        final int count = mAllAppsList.size();
        for(int i=0;i<count;i++){
            if((packageName.equals(mAllAppsList.get(i).componentName.getPackageName()))
                    &&(className.equals(mAllAppsList.get(i).componentName.getClassName()))){
                mAllAppsList.get(i).count++;
                break;
            }
        }
        if (mPreInstallConfig) {
            mAllAppsList.removeAll(mPreAppList);
        }
        mAppsAdapter.sort(LauncherModel.APP_COUNT_COMPARATOR);
        if (mPreInstallConfig) {
            mAllAppsList.addAll(0,mPreAppList);
        }
        mAppsAdapter.notifyDataSetChanged();
        mGrid.onLayoutChanged(true);
    }
}


