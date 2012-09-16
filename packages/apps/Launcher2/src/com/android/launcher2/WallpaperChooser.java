/*
 * Copyright (C) 2011, Code Aurora Forum. All rights reserved.
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

import java.io.IOException;
import java.util.ArrayList;
import java.lang.Thread;
import java.lang.Runnable;

import com.android.launcher.R;

public class WallpaperChooser extends Activity implements AdapterView.OnItemSelectedListener,
        OnClickListener {
    private static final String TAG = "Launcher.WallpaperChooser";

    private Gallery mGallery;
    private ImageView mImageView;
    private boolean mIsWallpaperSet;

    private Bitmap mBitmap;

    private ArrayList<Integer> mThumbs;
    private ArrayList<Integer> mImages;
    private WallpaperLoader mLoader;

    private Context resPackageCtx = null;

    private static final String RES_PACKAGENAME = "com.android.launcher.res";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        try {
            resPackageCtx = this.createPackageContext(RES_PACKAGENAME,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            Log.e("Res_Update", "Create Res Apk Failed");
        }

        findWallpapers();

        setContentView(R.layout.wallpaper_chooser);

        mGallery = (Gallery) findViewById(R.id.gallery);
        mGallery.setAdapter(new ImageAdapter(this));
        mGallery.setOnItemSelectedListener(this);
        mGallery.setCallbackDuringFling(false);

        findViewById(R.id.set).setOnClickListener(this);

        mImageView = (ImageView) findViewById(R.id.wallpaper);
    }

    private void findWallpapers() {
        mThumbs = new ArrayList<Integer>(24);
        mImages = new ArrayList<Integer>(24);

        Resources resources = null;
        int wallpaperId = 0;
        int extra_wallpaperId = 0;

        if (null == resPackageCtx) {
            Log.i(TAG,"resPackageCtx is null");
            resources = getResources();
            wallpaperId = R.array.wallpapers;
            extra_wallpaperId = R.array.extra_wallpapers;
        } else {
            Log.i(TAG,"resPackageCtx = "+resPackageCtx);
            resources = resPackageCtx.getResources();
            wallpaperId = resources.getIdentifier("wallpapers", "array", RES_PACKAGENAME);
            extra_wallpaperId = resources.getIdentifier("extra_wallpapers", "array", RES_PACKAGENAME);
        }

        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher2; Resources needs the real package name,
        // com.android.launcher. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = resources.getResourcePackageName(wallpaperId);
        Log.i(TAG,"packageName = "+packageName);

        addWallpapers(resources, packageName, wallpaperId);
        addWallpapers(resources, packageName, extra_wallpaperId);
    }

    private void addWallpapers(Resources resources, String packageName, int list) {
        final String[] extras = resources.getStringArray(list);
        for (String extra : extras) {
            int res = resources.getIdentifier(extra, "drawable", packageName);
            if (res != 0) {
                final int thumbRes = resources.getIdentifier(extra + "_small",
                        "drawable", packageName);

                if (thumbRes != 0) {
                    mThumbs.add(thumbRes);
                    mImages.add(res);
                    // Log.d(TAG, "addWallpapers: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsWallpaperSet = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel(true);
            mLoader = null;
        }
    }

    public void onItemSelected(AdapterView parent, View v, int position, long id) {
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel();
        }
        mLoader = (WallpaperLoader) new WallpaperLoader().execute(position);
    }

    /*
     * When using touch if you tap an image it triggers both the onItemClick and
     * the onTouchEvent causing the wallpaper to be set twice. Ensure we only
     * set the wallpaper once.
     */
    private void selectWallpaper(int position) {
        if (mIsWallpaperSet) {
            return;
        }

        mIsWallpaperSet = true;

        Context resPackageCtx = null;
        try {
            resPackageCtx = this.createPackageContext(RES_PACKAGENAME,
                     Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
           Log.e("Res_Update", "Create Res Apk Failed");
        }

        try {
            final WallpaperManager wpm = (WallpaperManager)getSystemService(WALLPAPER_SERVICE);
            if (null == resPackageCtx) {
                wpm.setResource(mImages.get(position));
                setResult(RESULT_OK);
                finish();
            } else {
                new ChangeWallpaperTask(WallpaperChooser.this).execute(mBitmap);
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to set wallpaper: " + e);
        }
    }

    public void onNothingSelected(AdapterView parent) {
    }

    private class ImageAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        ImageAdapter(WallpaperChooser context) {
            mLayoutInflater = context.getLayoutInflater();
        }

        public int getCount() {
            return mThumbs.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView image;

            if (convertView == null) {
                image = (ImageView) mLayoutInflater.inflate(R.layout.wallpaper_item, parent, false);
            } else {
                image = (ImageView) convertView;
            }
            
            int thumbRes = mThumbs.get(position);
            Drawable thumbDrawable = null;

            if (null == resPackageCtx) {
                image.setImageResource(thumbRes);
                thumbDrawable = image.getDrawable();
            } else {
                thumbDrawable = resPackageCtx.getResources().getDrawable(thumbRes);
                image.setImageDrawable(thumbDrawable);
            }

            if (thumbDrawable != null) {
                thumbDrawable.setDither(true);
            } else {
                Log.e(TAG, "Error decoding thumbnail resId=" + thumbRes + " for wallpaper #"
                        + position);
            }
            return image;
        }
    }

    public void onClick(View v) {
        selectWallpaper(mGallery.getSelectedItemPosition());
    }

    class WallpaperLoader extends AsyncTask<Integer, Void, Bitmap> {
        BitmapFactory.Options mOptions;

        WallpaperLoader() {
            mOptions = new BitmapFactory.Options();
            mOptions.inDither = false;
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;            
        }
        
        protected Bitmap doInBackground(Integer... params) {
            if (isCancelled()) return null;
            try {
                if (null == resPackageCtx) {
                    return BitmapFactory.decodeResource(getResources(),
                            mImages.get(params[0]), mOptions);
                } else {
                    Drawable drawable = resPackageCtx.getResources().getDrawable(mImages.get(params[0]));
                    return getBitmapFromDrawable(drawable);
                }
            } catch (OutOfMemoryError e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap b) {
            if (b == null) return;

            if (!isCancelled() && !mOptions.mCancel) {
                // Help the GC
                if (mBitmap != null) {
                    mBitmap.recycle();
                }
    
                final ImageView view = mImageView;
                view.setImageBitmap(b);
    
                mBitmap = b;
    
                final Drawable drawable = view.getDrawable();
                drawable.setFilterBitmap(true);
                drawable.setDither(true);

                view.postInvalidate();

                mLoader = null;
            } else {
               b.recycle(); 
            }
        }

        void cancel() {
            mOptions.requestCancelDecode();
            super.cancel(true);
        }
    }

    private Bitmap getBitmapFromDrawable(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    class ChangeWallpaperTask extends AsyncTask<Bitmap, Void, Void> {

        private Context context;
        private ProgressDialog mpDialog = null;

        ChangeWallpaperTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            mpDialog = new ProgressDialog(context);
            mpDialog.setTitle(R.string.progress_title_wallpaper);
            mpDialog.setMessage(getResources().getString(R.string.progress_msg_wallpaper));
            mpDialog.show();
        }

        @Override
        protected Void doInBackground(Bitmap... params) {

            WallpaperManager wpm = (WallpaperManager)getSystemService(WALLPAPER_SERVICE);
            try {
                wpm.setBitmap(params[0]);
            } catch (IOException e) {
                Log.e(TAG, "Failed to set wallpaper: " + e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void res) {
            if (mpDialog != null && mpDialog.isShowing()) {
                mpDialog.dismiss();
            }

            setResult(RESULT_OK);
            finish();
        }
    }
}
