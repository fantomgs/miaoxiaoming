/*
 * HorizontalListView.java v1.5
 *
 * 
 * The MIT License
 * Copyright (c) 2011 Paul Soucy (paul@dev-smart.com)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package android.widget;

import java.util.LinkedList;
import java.util.Queue;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;

import android.widget.AbsListView;
import android.graphics.Canvas;

import android.widget.ListAdapter;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Scroller;
import com.android.launcher.R;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.widget.IndicatorPanel;

public class HorizontialListView extends AdapterView<ListAdapter>{//AdapterView<ArrayAdapter> {

	private final String TAG = "HorizontialListView";
	public boolean mAlwaysOverrideTouch = true;
	protected ListAdapter mAdapter;
	private int mLeftViewIndex = -1;
	private int mRightViewIndex = 0;
	protected int mCurrentX;
	protected int mNextX;
	private int mMaxX = Integer.MAX_VALUE;
	private final int mRows = 4;
	private final int mColumns = 4;	
	private int mDisplayOffset = 0;
	protected Scroller mScroller;
	private GestureDetector mGesture;
	private Queue<View> mRemovedViewQueue = new LinkedList<View>();
	private OnItemSelectedListener mOnItemSelected;
	private OnItemClickListener mOnItemClicked;
	//private OnItemLongClickListener mOnItemLongClicked;
	private boolean mDataChanged = false;
	private boolean mLayoutChanged = true;
	private int mScreenCur = 0;
	private final int mVelocityMin = 100;
	private int mPageCount = 1;
	private IndicatorPanel mIndicator;
	private int mTouchX = -1,mTouchY = -1,mOldTouchX = -1,mOldTouchY = -1;
	private int mDownX,mDownY;
	private final int mIconPad = 2;
	private View mViewSelected = null,mViewOldSelected;
	private final int mMoveMin = 5;
	private Drawable mSelector,mSelectorSaved;
	private boolean isShowIcon = false;
	
	enum TouchDirection
	{
		DIRECTION_LEFT,
		DIRECTION_RIGHT
	};
	private TouchDirection mTouchDirection = TouchDirection.DIRECTION_LEFT;
	
	

	public HorizontialListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}
	
	private synchronized void initView() {
		mLeftViewIndex = -1;
		mRightViewIndex = 0;
		mDisplayOffset = 0;
		mCurrentX = 0;
		mNextX = 0;
		mMaxX = Integer.MAX_VALUE;
		mLayoutChanged = true;
		mScroller = new Scroller(getContext());
		mGesture = new GestureDetector(getContext(), mOnGesture);
		setHorizontalScrollBarEnabled(false);
		setHorizontalScrollBarEnabled(true);
		setOverScrollMode(View.OVER_SCROLL_NEVER);
		setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);	
		mSelector = getResources().getDrawable(R.drawable.grid_selector);
		//setBackgroundDrawable(mSelector);
	}

	public void setIndicator(IndicatorPanel indicator)
	{
		mIndicator = indicator;
	}
	
	@Override
	public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
		mOnItemSelected = listener;
	}
	
	@Override
	public void setOnItemClickListener(AdapterView.OnItemClickListener listener){
		mOnItemClicked = listener;
	}
/*
	@Override
	public void setOnItemLongClickListener(AdapterView.OnItemLongClickListener listener){
//		super.setOnItemLongClickListener(listener);
		mOnItemLongClicked = listener;
	}
*/	
		
	private DataSetObserver mDataObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			synchronized(HorizontialListView.this){
				mDataChanged = true;
			}
			invalidate();
			requestLayout();
		}

		@Override
		public void onInvalidated() {
			reset();
			invalidate();
			requestLayout();
		}
		
	};

	@Override
	public ListAdapter getAdapter() {
		return mAdapter;
	}

	@Override
	public View getSelectedView() {
		//TODO: implement
		//return null;	
///*		
		
		if (getChildCount() > 0) {
			View v = getPressedView();
			//v.setBackgroundDrawable(getResources().getDrawable(R.drawable.grid_selector));
            return v;
        } else {
            return null;
        }
//*/        
	}

	@Override
	public void setAdapter(ListAdapter adapter) {
		
		if(mAdapter != null) {
			mAdapter.unregisterDataSetObserver(mDataObserver);			
		}
		
		mAdapter = adapter;
		if(mAdapter != null)
			mAdapter.registerDataSetObserver(mDataObserver);
		reset();
	}
	
	private synchronized void reset(){
		initView();
		removeAllViewsInLayout();
        requestLayout();
	}

	public void onLayoutChanged(boolean changed)
	{
		mLayoutChanged = changed;
	}
	
	@Override
	public void setSelection(int position) {
		//TODO: implement
	}
	
	private void addAndMeasureChild(final View child, int viewPos) {
		LayoutParams params = (LayoutParams)child.getLayoutParams();
		if(params == null) {
			params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		}

		addViewInLayout(child, viewPos, params, false);
/*		
		child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
				MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
				*/
	}

	@Override
	protected synchronized void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if(mAdapter == null){
			return;
		}
		
		if(mDataChanged){
			int oldCurrentX = mCurrentX;
			initView();
			removeAllViewsInLayout();
			mNextX = oldCurrentX;
			
		}

		if(mScroller.computeScrollOffset()){
			int scrollx = mScroller.getCurrX();
			mNextX = scrollx;			
		}		
		
		if(mNextX < 0){
			mNextX = 0;
			mScroller.forceFinished(true);
		}
		if(mNextX > mMaxX) {
			mNextX = mMaxX;
			mScroller.forceFinished(true);
		}
		
		int dx = mCurrentX - mNextX;
		
		//if(mDataChanged || mLayoutChanged)
		{
			//removeNonVisibleItems(dx);
			fillList(dx);
			mLayoutChanged = false;
			mDataChanged = false;
			
		}	
		positionItems(dx);
		

		Log.i(TAG,"mDisplayOffset"+ mDisplayOffset);
		mScreenCur = -(mDisplayOffset - (getWidth() >> 1))/getWidth();
		mIndicator.setProperty(mScreenCur,mPageCount);
		mIndicator.invalidate();				
		
		mCurrentX = mNextX;

		
		if(!mScroller.isFinished()){
			post(new Runnable(){
				@Override
				public void run() {
					requestLayout();
				}
			});
			
		}else
		{
			if(mDownX == -1 || mDownY == -1)
			{
				isShowIcon = false;
				post(new Runnable(){
					public void run(){
						releaseAllBackground();
						}
					});
			}
		}
	}
	
				
	private void fillList(final int dx) {
		int edge = 0;
		View child = getChildAt(getChildCount()-1);
		if(child != null) {
			edge = child.getRight();
		}
		fillListRight(edge, dx);
		
		edge = 0;
		child = getChildAt(0);
		if(child != null) {
			edge = child.getLeft();
		}
		fillListLeft(edge, dx);
		
		
	}
	
	private void fillListRight(int rightEdge, final int dx) {
		while(/*rightEdge + dx < getWidth() && */mRightViewIndex < mAdapter.getCount()) {
			
			View child = mAdapter.getView(mRightViewIndex, mRemovedViewQueue.poll(), this);
			addAndMeasureChild(child, -1);
			int nb = mAdapter.getCount();
			
			rightEdge  = ((nb / (mRows  * mColumns)) + (((nb % (mColumns * mRows))  > 0)? 1 :0)) * getWidth();//       > mColumns *  ? / mColumns * getWidth();//+= child.getMeasuredWidth();			
			Log.i("fillListRight","rightEdge"+ rightEdge+"getWidth:" + getWidth());
			if(mRightViewIndex == mAdapter.getCount()-1){
				mMaxX = mCurrentX + rightEdge - getWidth();
			}
			mRightViewIndex++;			
		}

		
	}
	private synchronized void detectPressedView()
	{
		View v = getChildAt(0);
		if(v.getWidth() <= 0 || v.getHeight() <= 0)
			return ;
		
		int count = getChildCount();
		
		int i = mTouchY / v.getHeight() * mColumns + mTouchX / v.getWidth();
		Rect rc = new Rect();		

		while(i >= 0 && i < count)
		{
			v = getChildAt(i);
			rc.set(v.getLeft(),v.getTop(),v.getRight(),v.getBottom());
			if(rc.contains(mTouchX,mTouchY))
			{
				mViewSelected = v;
				return;
			}
			i++;
		}
		mViewSelected = null;
	}
	
	private final synchronized View getPressedView()
	{		
		return mViewSelected;		
	}
	
	private final void releaseAllBackground()
	{
		for(int i = 0;i < getChildCount();getChildAt(i).setBackgroundColor(0x00FFFFFF),i++);
	}
	
	@Override
	protected synchronized boolean drawChild(Canvas canvas, View child, long drawingTime) {
		//boolean isSuccess = super.drawChild(canvas,child,drawingTime);
	
		View v = getPressedView();
		if(mViewOldSelected != v)
		{
			Log.i(TAG,"x1:"+ mOldTouchX + ",x2:" + mTouchX + ",y1:" + mOldTouchY + ",y2" + mTouchY);
			if(v != null 
				//&& ((Math.abs(Math.abs(mOldTouchX) - Math.abs(mTouchX)) + Math.abs(Math.abs(mOldTouchY) - Math.abs(mTouchY))) < mMoveMin) 
				&& isShowIcon)
			{					
				Log.i(TAG,"drawChild: getChild");
				//mSelector.setBounds(v.getLeft(),v.getTop(),v.getRight(),v.getBottom());
				//mSelector.draw(canvas);
				if(mViewOldSelected != null)
					mViewOldSelected.setBackgroundColor(0x00FFFFFF);
				
				mViewOldSelected = v;
				mSelectorSaved = v.getBackground();
				v.setBackgroundDrawable(mSelector);
				
			}
			else
			{
				Log.i(TAG,"drawChild: recover mViewOldSelected" + mViewOldSelected + ",mSelectorSaved:" + mSelectorSaved + "v:" + v);
				if(mViewOldSelected != null)
				{
					//mViewOldSelected.setBackgroundDrawable(mSelectorSaved);
					mViewOldSelected.setBackgroundColor(0x00FFFFFF);
				}
				mViewOldSelected = null;
				mSelectorSaved = null;
			}
			postInvalidate();
		}
		
		
		return super.drawChild(canvas,child,drawingTime);
	}
	private void fillListLeft(int leftEdge, final int dx) {
		while(leftEdge + dx > 0 && mLeftViewIndex >= 0) {
			View child = mAdapter.getView(mLeftViewIndex, mRemovedViewQueue.poll(), this);
			addAndMeasureChild(child, 0);
			leftEdge -= child.getMeasuredWidth();
			mLeftViewIndex--;
			mDisplayOffset -= child.getMeasuredWidth();
		}
	}
	
	private void removeNonVisibleItems(final int dx) {
		View child = getChildAt(0);
		while(child != null && child.getRight() + dx <= 0) {
			mDisplayOffset += child.getMeasuredWidth();
			mRemovedViewQueue.offer(child);
			removeViewInLayout(child);
			mLeftViewIndex++;
			child = getChildAt(0);
			
		}
		
		child = getChildAt(getChildCount()-1);
		while(child != null && child.getLeft() + dx >= getWidth()) {
			mRemovedViewQueue.offer(child);
			removeViewInLayout(child);
			mRightViewIndex--;
			child = getChildAt(getChildCount()-1);
		}
	}
	
	private void positionItems(final int dx) {
		
		if(getChildCount() > 0){
			mDisplayOffset += dx;
			int left = mDisplayOffset;
			int top = 0;

			int rows = mRows;//getHeight() / getChildAt(0).getMeasuredHeight();
			int columns = mColumns;//getWidth() / getChildAt(0).getMeasuredWidth();
			int itw = getWidth() / columns;
			int ith = getHeight() / rows;
			int posX = 0;
			int posY = 0;
			int childWidth = 0;
			int childHeight = 0;

			Log.i("positionItems",rows + "," + columns);
			int countInPage = rows * columns;
			int pos = mDisplayOffset;
			mPageCount = (getChildCount() / countInPage) + 1;
			for(int i=0,j = 0;i<getChildCount();i++){
				View child = getChildAt(i);
				
				j = i % countInPage;
/*
				child.measure(MeasureSpec.makeMeasureSpec(childWidth,MeasureSpec.AT_MOST),
					MeasureSpec.makeMeasureSpec(childHeight,MeasureSpec.AT_MOST));
*/					
				
				childWidth = getMeasuredWidth() < (itw - (mIconPad << 1)) ? getMeasuredWidth() : (itw - (mIconPad << 1));
				childHeight = getMeasuredHeight() < ith ? getMeasuredHeight() : ith;
				posX = left + ((itw - childWidth) >> 1);

				
					
				//posY = top + ()
				child.layout(posX , top, posX + childWidth, top + childHeight);
				
				

				//if(getMeasuredWidth() < (itw - (mIconPad << 1)))
				//{
				//	child.setGravity(Gravity.RIGHT | Gravity.TOP);
				//}
				if(((i + 1) % countInPage) != 0)
				{
					if(((j + 1) % columns) != 0)
					{
						left += itw;
					}
					else
					{
						top += ith;
						left = pos;
					}
				}
				else
				{
					top = 0;
					left = pos += getWidth();
				}
			}
		}
	}
	
	public synchronized void scrollTo(int x) {
		mScroller.startScroll(mNextX, 0, x - mNextX, 0);
		requestLayout();
	}
	public synchronized void scrollBy(int x) {
		//super.scrollBy(x,0);
		mScroller.startScroll(mNextX, 0, x, 0);
		requestLayout();
	}
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		boolean handled = mGesture.onTouchEvent(ev);
		Log.i("dispatchTouchEvent","ev.getAction()" + ev.getAction() + ",offset" + mScroller.computeScrollOffset() + "mNextX:" + mNextX);

		mOldTouchX = mTouchX;
		mOldTouchY = mTouchY;

		switch(ev.getAction())
		{
			case MotionEvent.ACTION_UP:
			{
				mDownX = mTouchX = -1;
				mDownY = mTouchY = -1;
						
			if( !mScroller.computeScrollOffset()
				&& (mNextX % getWidth() != 0))
			{
				Log.i("dispatchTouchEvent","In");
				awakenScrollBars();
				flingToScreen((mNextX + (getWidth() >> 1)) / getWidth());
			}
			//releaseAllBackground();
			}
			break;
			case MotionEvent.ACTION_DOWN:
			{
				mDownX = (int)ev.getX();
				mDownY = (int)ev.getY();
			}			
			case MotionEvent.ACTION_MOVE:
			{
				mTouchX = (int)ev.getX();
				mTouchY = (int)ev.getY();
/*
				post(new Runnable(){
					public void run(){
						detectPressedView();
						}
					});
				//postInvalidate();
*/				
			}
			break;
			default:
			{
				mTouchX = -1;
				mTouchY = -1;
				post(new Runnable(){
					public void run(){
						detectPressedView();
						postInvalidate();
						}
					});
			}
		}
		
		return handled;
	}
	
	protected boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {		
		synchronized(HorizontialListView.this){
			mScroller.fling(mNextX, 0, (int)-velocityX, 0, 0, mMaxX, 0, 0);
			int width = getWidth();
			int finalX = mScroller.getFinalX();
			int nPage = mDownX / width;//mScreenCur;//finalX / width;	
			int nPageCur = nPage;
			Log.i(TAG,"velocityX:" + velocityX + ",finalX:" + finalX + ",mNextX:" + mNextX);
			awakenScrollBars();
/*
//Way 1:
			if(Math.abs(finalX + mDisplayOffset) > (width >> 1))
			{
				if(mNextX % getWidth() != 0)
				{
					flingToScreen((mNextX + (getWidth() >> 1)) / getWidth());
				}
				else
				{
					if(velocityX < 0)
					{
						flingToRight();
					}
					else
					{
						flingToLeft();
					}
				}
			}
			else
			{
				mScroller.extendDuration(1000);
				mScroller.setFinalX(mScreenCur* width);
			}
*/
	
// Way 2:
			if(velocityX < 0)
			{
				//if((finalX % width) > (width >> 1))
				if(Math.abs(Math.abs(finalX) -  nPageCur * width) >  (width >> 1))
				{
					nPage = (((nPage + 1) > (mPageCount - 1))? nPage :(nPage + 1));					
					mScroller.fling(mNextX, 0, -(((int)velocityX) << 2), 0, nPageCur * width, nPage * width, 0, 0);
					mScroller.extendDuration(1000);
					mScroller.setFinalX(nPage * width);
				}
				else
				{
					mScroller.fling(mNextX, 0, (int)-velocityX, 0, nPage * width, mMaxX, 0, 0);
					mScroller.extendDuration(1000);
					mScroller.setFinalX(nPage * width);
				}
			}
			else
			{
				//if((finalX % width) > (width >> 1))
				//if(Math.abs(Math.abs(finalX) -  mNextX) >  (width >> 1))
				if(Math.abs(Math.abs(finalX) -  nPageCur * width) >  (width >> 1))
				{
					nPage = ((nPage - 1) < 0 ? nPage :(nPage - 1));
					mScroller.fling(mNextX, 0, -(((int)velocityX) << 2), 0, nPage * width, nPageCur * width, 0, 0);
					mScroller.extendDuration(1000);
					mScroller.setFinalX(nPage* width);
				}
				else
				{		
					//nPage = (nPage < 1 ? nPage : nPage -1);
					mScroller.fling(mNextX, 0, -(((int)velocityX)), 0, (nPage < 1 ? nPage : nPage -1) * width, nPageCur * width, 0, 0);
					mScroller.extendDuration(1000);
					mScroller.setFinalX(nPage * width);
				}
			}
			
		}
		
/*		
		Log.i("onFling","velocityX" + velocityX);
		int width = getWidth();
		int nPage = mScroller.getFinalX() / width;				
		Log.i("computeScroll","getCurrVelocity" + mScroller.getCurrVelocity());
		if(velocityX < 0)
		{
			Log.i("computeScroll","1:" + mScroller.getFinalX() + "width" + width + "nPage" + nPage + "mPageCount" + mPageCount);
			if((mScroller.getFinalX() % width) > (width >> 1))
			{
				Log.i("computeScroll","2:" + mScroller.getFinalX());
				nPage = (((nPage + 1) > (mPageCount - 1))? nPage :(nPage + 1));
				
			}
		}
		else
		{
			Log.i("computeScroll","4");
			if((mScroller.getFinalX() % width) > (width >> 1))
			{
				Log.i("computeScroll","5");
				nPage = (((nPage + 1) > (mPageCount - 1))? nPage :(nPage + 1));
			}
		}
		mScroller.setFinalX(nPage * width);
*/		
		
		requestLayout();
		
		return true;
	}
	
	protected boolean onDown(MotionEvent e) {
		post(new Runnable(){
					public void run(){
						detectPressedView();
						}
					});
		mScroller.forceFinished(true);		
		return true;
	}

	public void scrollToScreen(int screen)
	{
		final int newX = screen * getWidth(); 
		final int deltaX = newX - getScrollX(); 

		Log.i("scrollToScreen","deltaX:"+ deltaX + "mNextX:" + mNextX + "getScrollX():"+ getScrollX());
		mScroller.startScroll(mNextX,0,deltaX,0,(Math.abs(deltaX) << 1));
		requestLayout();
	}

	public void flingToLeft()
	{
		flingToScreen(mScreenCur > 0 ? (mScreenCur - 1):mScreenCur);
	}
	
	public void flingToRight()
	{
		flingToScreen(mScreenCur < (mPageCount - 1) ? (mScreenCur + 1):mScreenCur);
	}
	
	public void flingToScreen(int screen)
	{
		final int newX = screen * getWidth(); 
		final int deltaX = newX - mNextX; 
		Log.i("flingToScreen","deltaX:"+ deltaX + "mNextX:" + mNextX + "getScrollX():"+ getScrollX());
		if(mNextX > newX)
		{
			if(deltaX < 0)
			{
				mScroller.fling(mNextX, 0, (int)deltaX  << 1, 0, 0, mMaxX, 0, 0);
			}
			else
			{
				mScroller.fling(mNextX, 0, -(int)deltaX  << 1, 0, 0, newX, 0, 0);
			}
		}
		else
		{
			if(deltaX < 0)
			{
				mScroller.fling(mNextX, 0, (int)deltaX  << 1, 0, 0, mMaxX, 0, 0);
			}
			else
			{
				mScroller.fling(mNextX, 0, (int)deltaX  << 1, 0, 0, newX, 0, 0);
			}
		}
		mScroller.setFinalX(newX);
		requestLayout();
	}

	protected boolean onSingleTapUp(MotionEvent e) {			
		//scrollToScreen()
		Log.i("flingToScreen","mScroller.computeScrollOffset:" + mScroller.computeScrollOffset());
		//flingToScreen((mNextX + (getWidth() >> 1)) / getWidth());
		return true;
	}
	
	public void computeScroll() { 
/*		
		int width = getWidth();
		int nPage = mScroller.getFinalX() / width;
		if (mScroller.computeScrollOffset()) { 
			//if(Math.abs(mScroller.getCurrVelocity()) < mVelocityMin)
			{

			}
			//scrollTo(mScroller.getCurrX(), mScroller.getCurrY()); 
/*		 	
		 	mTouchDirection
		 	if()
		 	{
			 scrollTo(mScroller.getCurrX(), mScroller.getCurrY()); 
		 	}
			else
			{
			}
			 //postInvalidate(); 
*/	
/*
		 }  
		else
		{
			Log.i("computeScroll","else nPage" + nPage);
			if((mScroller.getCurrX() % width) != 0)
			{
				if((mScroller.getFinalX() % width) > (width >> 1))
				{
					nPage = (((nPage + 1) > (mPageCount - 1))? nPage :(nPage + 1));
				}
				scrollToScreen(nPage * width);
			}
		}
*/		
	 } 
		
	private OnGestureListener mOnGesture = new GestureDetector.SimpleOnGestureListener() {

		@Override
		public boolean onDown(MotionEvent e) {
			return HorizontialListView.this.onDown(e);
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			return HorizontialListView.this.onFling(e1, e2, velocityX, velocityY);
		}
		@Override
		public boolean onSingleTapUp(MotionEvent e) 
		{
			return HorizontialListView.this.onSingleTapUp(e);
		}
		@Override
		public void onShowPress(MotionEvent e) {
			isShowIcon = true;
			post(new Runnable(){
					public void run(){
						detectPressedView();
						postInvalidate();
						}
					});
		}
		
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			
			synchronized(HorizontialListView.this){
				mNextX += (int)distanceX;
				isShowIcon = false;
				post(new Runnable(){
					public void run(){
						detectPressedView();
						postInvalidate();
						}
					});
			}
			requestLayout();
			
			return true;
		}
		
		@Override
		public void onLongPress(MotionEvent e)
		{
			isShowIcon = false;

			post(new Runnable(){
					public void run(){
						detectPressedView();
						postInvalidate();
						}
					});
			
			Rect viewRect = new Rect();
			for(int i = 0;i < getChildCount();i++)
			{
				View child = getChildAt(i);
				viewRect.set(child.getLeft(),
						child.getTop(),
						child.getRight(),
						child.getBottom());
				if(viewRect.contains((int)e.getX(),(int)e.getY()) 
					&& getOnItemLongClickListener() != null)
					getOnItemLongClickListener().onItemLongClick(HorizontialListView.this,child,i,child.getId());
			}

		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			Rect viewRect = new Rect();
			post(new Runnable(){
					public void run(){						
						postInvalidate();
						}
					});
			for(int i=0;i<getChildCount();i++){
				View child = getChildAt(i);
				int left = child.getLeft();
				int right = child.getRight();
				int top = child.getTop();
				int bottom = child.getBottom();
				viewRect.set(left, top, right, bottom);
				if(viewRect.contains((int)e.getX(), (int)e.getY())){
					child.setBackgroundDrawable(mSelector);
					if(mOnItemClicked != null){
						mOnItemClicked.onItemClick(HorizontialListView.this, child, mLeftViewIndex + 1 + i, mAdapter.getItemId( mLeftViewIndex + 1 + i ));
					}
					if(mOnItemSelected != null){
						mOnItemSelected.onItemSelected(HorizontialListView.this, child, mLeftViewIndex + 1 + i, mAdapter.getItemId( mLeftViewIndex + 1 + i ));
					}
					break;
				}
				
			}
			return true;
		}
		
		
		
	};

	

}
