package com.android.launcher2;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.view.View;
import android.view.HapticFeedbackConstants;

public class Panel extends FrameLayout {

    private Launcher mLauncher;
    private Workspace mWorkspace;
    private RelativeLayout mAllAppsButtonCluster;

    public Panel(Context context) {
        this(context, null);
        // TODO Auto-generated constructor stub
    }

    public Panel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        // TODO Auto-generated constructor stub
    }

    public Panel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public void setWorkspace(Workspace workspace) {
        mWorkspace = workspace;
    }

    public View getAllAppsButtonCluster() {
        return mAllAppsButtonCluster;
    }

    public void setScreenIndicator() {
        ImageView indicator = null;
        FrameLayout.LayoutParams flp = null;
        final Config.ScreenIndicator[] si = Config.getInstance(getContext()).getIndicators();
        Drawable[] draws = new Drawable[si.length];
        for (int i = 0; i < si.length; i++) {
            indicator = new ImageView(getContext());
            indicator.setImageResource(si[i].getDrawable());
            indicator.setScaleType(ImageView.ScaleType.CENTER);
            indicator.setFocusable(true);
            indicator.setClickable(true);
            draws[i] = indicator.getDrawable();
            addView(indicator);

            flp = new FrameLayout.LayoutParams(
                    si[i].getWidth(), si[i].getHeight(), si[i].getGravity());
            flp.setMargins(si[i].getLeftMargin(), 0, si[i].getRightMargin(), 0);
            indicator.setLayoutParams(flp);

            indicator.setHapticFeedbackEnabled(false);
            indicator.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!mLauncher.isAllAppsVisible()) {
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    }
                    mLauncher.showPreviews(v);
                    return true;

                }
            });

            final int direction = si[i].getDirection();
            indicator.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mWorkspace.scroll(direction);
                }
            });
        }

        mWorkspace.setIndicators(draws);

    }

    public void setPreferenceShortcut() {
        RelativeLayout rl = new RelativeLayout(getContext());
        rl.setPadding(0, Config.convertDipToPx(2), 0, 0);
        addView(rl);
        mAllAppsButtonCluster = rl;
        Config.HotseatPanel hsp = Config.getInstance(getContext()).getHotseatPanel();
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                hsp.getWidth() == 0 ? FrameLayout.LayoutParams.FILL_PARENT : hsp.getWidth(),
                hsp.getHeight() == 0 ? FrameLayout.LayoutParams.FILL_PARENT : hsp.getHeight(),
                hsp.getGravity());
        rl.setLayoutParams(flp);
        rl.setBackgroundResource(hsp.getBackground());

        ImageView hotseat = null;
        RelativeLayout.LayoutParams rlp = null;
        Config.Hotseat[] hs = Config.getInstance(getContext()).getHotseats();
        for (int i = 0; i < hs.length; i++) {
            if (hs[i].isAllAppView()) {
                HandleView mHandleView = new HandleView(getContext());
                mHandleView.setId(hs[i].getId());
                mHandleView.setImageResource(hs[i].getDrawable());
                mHandleView.setBackgroundResource(hs[i].getBackground());
                mHandleView.setPadding(hs[i].getLeftPadding(), 0, hs[i].getRightPadding(), 0);
                mHandleView.setScaleType(ImageView.ScaleType.CENTER);
                mHandleView.setFocusable(true);
                mHandleView.setClickable(true);
                rl.addView(mHandleView);

                rlp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.FILL_PARENT);
                rlp.setMargins(hs[i].getLeftMargin(), 0, hs[i].getRightMargin(), 0);
                for(int j = 0; j < hs[i].getLayoutsSize(); j++) {
                    rlp.addRule(hs[i].getLayoutRule(j), hs[i].getLayoutValue(j));
                }
                mHandleView.setLayoutParams(rlp);

                mHandleView.setLauncher(mLauncher);
                mHandleView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mLauncher.isAllAppsVisible()) {
                            mLauncher.closeAllApps(true);
                        }
                        else {
                            mLauncher.showAllApps(true);
                        }
                    }
                });

                mHandleView.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (!mLauncher.isAllAppsVisible()) {
                            mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        }
                        mLauncher.showPreviews(v);
                        return true;

                    }
                });
            }else {
                hotseat = new ImageView(getContext());
                hotseat.setId(hs[i].getId());
                hotseat.setImageResource(hs[i].getDrawable());
                hotseat.setBackgroundResource(hs[i].getBackground());
                hotseat.setPadding(hs[i].getLeftPadding(), 0, hs[i].getRightPadding(), 0);
                hotseat.setScaleType(ImageView.ScaleType.CENTER);
                hotseat.setFocusable(true);
                hotseat.setClickable(true);
                hotseat.setContentDescription(hs[i].getLabel());
                rl.addView(hotseat);

                rlp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.FILL_PARENT);
                rlp.setMargins(hs[i].getLeftMargin(), 0, hs[i].getRightMargin(), 0);
                for(int j = 0; j < hs[i].getLayoutsSize(); j++) {
                    rlp.addRule(hs[i].getLayoutRule(j), hs[i].getLayoutValue(j));
                }

                hotseat.setLayoutParams(rlp);

                final Intent intent = hs[i].getIntent();
                hotseat.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mLauncher.isAllAppsVisible()) {
                            return;
                        }
                        mLauncher.startActivitySafely(intent, "hotseat");
                    }
                });
            }


        }
    }
}
