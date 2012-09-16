
package android.widget;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Canvas;

import android.util.AttributeSet;
import android.util.Log;

import android.view.View;
import android.content.Context;
import com.android.launcher.R;


public class IndicatorPanel extends View
{
	private Bitmap mBackGround;
	private Bitmap mIndiActive,mIndiInActive;
	private int mMaxCount;
	private int mPos = 0;
	private Paint mPaint = new Paint();
	private final String TAG = "IndicatorPanel";

	//final Rect rc;
	int mGap;
	int mLeft;

	public IndicatorPanel(Context context)
	{		
		super(context);
		initView();
	}
	public IndicatorPanel(Context context, AttributeSet attrs)
	{
		super(context,attrs);
		initView();
	}

	public IndicatorPanel(Context context, AttributeSet attrs, int defStyle)
	{
		super(context,attrs,defStyle);
		initView();		
	}
	
	private void initView()
	{
		
	}

	protected void onFinishInflate() {
		mBackGround = makeBitmap(90,90,(byte)0xFF,(byte)0x0,(byte)0x0,(byte)0x0);
		mIndiActive = Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.slider_active));
		mIndiInActive = Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.slider_inactive));
		mGap = mIndiActive.getWidth();
		
    }
	public void setProperty(int pos)
	{
		mPos = pos;
	}

	public void setProperty(int pos,int max)
	{
		mPos = pos;
		mMaxCount = max;
		Log.i(TAG,"setProperty p:" + pos +",m:" + mMaxCount);
	}

	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		canvas.drawBitmap(mBackGround,0,0,mPaint);
		int iconW = mIndiActive.getWidth();
		int iconH =  mIndiActive.getHeight();
		int posX = ((getWidth()  + mGap)>> 1)  -  mMaxCount * mGap;
		int poxY = (getHeight() - iconH) >> 1;
		Log.i(TAG,"onDraw x:" + posX +",y:" + poxY + ",iconW" + iconW);
		for(int i = 0;i < mMaxCount;i++)
		{
			if(mPos == i)
			{
				canvas.drawBitmap(mIndiActive,posX,poxY,mPaint);
			}
			else
			{
				canvas.drawBitmap(mIndiInActive,posX,poxY,mPaint);
			}
			posX += (iconW << 1);
		}
	}
	
	private Bitmap makeBitmap(int screenW,int screenH,byte alpha,byte red,
		byte green,byte blue)
	{
		int color = (alpha << 24) + (red << 16) + (green << 8) + blue;
		int scrW = screenW;
		int scrH = screenH;

		Log.i(TAG,"makeBitmap w:" + scrW +",h:" + screenH);
		
		int pixCount = scrW * scrH;
		int pix[] = new int[pixCount];
		
		Bitmap bitmap = Bitmap.createBitmap(scrW,scrH,Bitmap.Config.ARGB_8888);
		for(int i = 0;i < pixCount; i++)
		{
			pix[i] = color;
		}
		bitmap.setPixels(pix,0,scrW,0,0,scrW,scrH);
		
		return bitmap;
	}
	
}

