/*
 * Copyright (C) 2008 ZXing authors
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

package com.google.zxing.demo.view;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.core.ResultPoint;
import com.google.zxing.demo.camera.CameraManager;
import com.micen.focusqrcode.R;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View
{

	private static final int[] SCANNER_ALPHA =
	{ 0, 64, 128, 192, 255, 192, 128, 64 };
	private static final long ANIMATION_DELAY = 80L;
	private static final int CURRENT_POINT_OPACITY = 0xA0;
	private static final int MAX_RESULT_POINTS = 20;
	private static final int POINT_SIZE = 6;
	private static final int TEXT_SIZE = 15;
	private static final int TEXT_PADDING_TOP = 30;
	private static final int LINE_WIDTH = 2;

	private CameraManager cameraManager;
	private final Paint paint;
	private Bitmap resultBitmap;
	private final int maskColor;
	private final int resultColor;
	private final int laserColor;
	private final int resultPointColor;
	private int scannerAlpha;
	private List<ResultPoint> possibleResultPoints;
	private List<ResultPoint> lastPossibleResultPoints;
	/** 
	 * 手机的屏幕密度 
	 */
	private static float density;

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		// Initialize these once for performance rather than calling them every time in onDraw().
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();
		maskColor = resources.getColor(R.color.viewfinder_mask);
		resultColor = resources.getColor(R.color.result_view);
		laserColor = resources.getColor(R.color.color_ffffff);
		resultPointColor = resources.getColor(R.color.possible_result_points);
		scannerAlpha = 0;
		possibleResultPoints = new ArrayList<>(5);
		lastPossibleResultPoints = null;
		density = context.getResources().getDisplayMetrics().density;
	}

	public void setCameraManager(CameraManager cameraManager)
	{
		this.cameraManager = cameraManager;
	}

	@SuppressLint("DrawAllocation")
	@Override
	public void onDraw(Canvas canvas)
	{
		if (cameraManager == null)
		{
			return; // not ready yet, early draw before done configuring
		}
		Rect frame = cameraManager.getFramingRect();
		Rect previewFrame = cameraManager.getFramingRectInPreview();
		if (frame == null || previewFrame == null)
		{
			return;
		}
		int width = canvas.getWidth();
		int height = canvas.getHeight();
		int top = frame.top;
		int bottom = frame.bottom;
		// Draw the exterior (i.e. outside the framing rect) darkened
		// TODO 绘制扫描框外层背景
		paint.setColor(resultBitmap != null ? resultColor : maskColor);
		canvas.drawRect(0, 0, width, top, paint);
		canvas.drawRect(0, top, frame.left, bottom + 1, paint);
		canvas.drawRect(frame.right + 1, top, width, bottom + 1, paint);
		canvas.drawRect(0, bottom + 1, width, height, paint);

		if (resultBitmap != null)
		{
			// Draw the opaque result bitmap over the scanning rectangle
			paint.setAlpha(CURRENT_POINT_OPACITY);
			canvas.drawBitmap(resultBitmap, null, frame, paint);
		}
		else
		{

			// 绘制四角包边
			paint.setColor(laserColor);
			canvas.drawRect(-10 + frame.left, -10 + top, -10 + (LINE_WIDTH + frame.left), -10 + (50 + top), paint);
			canvas.drawRect(-10 + frame.left, -10 + top, -10 + (50 + frame.left), -10 + (LINE_WIDTH + top), paint);
			canvas.drawRect(10 + ((0 - LINE_WIDTH) + frame.right), -10 + top, 10 + (1 + frame.right), -10 + (50 + top),
					paint);
			canvas.drawRect(10 + (-50 + frame.right), -10 + top, 10 + frame.right, -10 + (LINE_WIDTH + top), paint);
			canvas.drawRect(-10 + frame.left, 10 + (-49 + bottom), -10 + (LINE_WIDTH + frame.left), 10 + (1 + bottom),
					paint);
			canvas.drawRect(-10 + frame.left, 10 + ((0 - LINE_WIDTH) + bottom), -10 + (50 + frame.left),
					10 + (1 + bottom), paint);
			canvas.drawRect(10 + ((0 - LINE_WIDTH) + frame.right), 10 + (-49 + bottom), 10 + (1 + frame.right),
					10 + (1 + bottom), paint);
			canvas.drawRect(10 + (-50 + frame.right), 10 + ((0 - LINE_WIDTH) + bottom), 10 + frame.right,
					10 + (LINE_WIDTH - (LINE_WIDTH - 1) + bottom), paint);
			// paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
			paint.setColor(getResources().getColor(R.color.color_ffffff));
			// scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
			int vmiddle = frame.height() / 2 + frame.top;
			int hmiddle = frame.width() / 2 + frame.left;
			// canvas.drawRect(frame.left + 2, vmiddle - 1, frame.right - 1, vmiddle + 2, paint);
			canvas.drawRect(hmiddle - 20, vmiddle - 1, hmiddle + 20, vmiddle + 2, paint);
			canvas.drawRect(hmiddle - 1, vmiddle - 20, hmiddle + 2, vmiddle + 20, paint);

			float scaleX = frame.width() / (float) previewFrame.width();
			float scaleY = frame.height() / (float) previewFrame.height();

			TextPaint textPaint = new TextPaint();
			textPaint.setARGB(0xFF, 0xFF, 0xFF, 0xFF); // 字体颜色
			textPaint.setTextSize(TEXT_SIZE * density);
			textPaint.setAntiAlias(true); // 设置抗锯齿，否则字迹会很模糊
			StaticLayout layout = new StaticLayout(getResources().getString(R.string.scan_text), textPaint, frame.right
					- frame.left, Alignment.ALIGN_CENTER, 1.0F, 0.0F, true);
			canvas.translate(frame.left, (float) (frame.top - (float) TEXT_PADDING_TOP * density)); // 绘制起始位置
			layout.draw(canvas);

			// List<ResultPoint> currentPossible = possibleResultPoints;
			// List<ResultPoint> currentLast = lastPossibleResultPoints;
			// int frameLeft = frame.left;
			// int frameTop = frame.top;
			// if (currentPossible.isEmpty())
			// {
			// lastPossibleResultPoints = null;
			// }
			// else
			// {
			// possibleResultPoints = new ArrayList<>(5);
			// lastPossibleResultPoints = currentPossible;
			// paint.setAlpha(CURRENT_POINT_OPACITY);
			// paint.setColor(resultPointColor);
			// synchronized (currentPossible)
			// {
			// for (ResultPoint point : currentPossible)
			// {
			// canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX), frameTop
			// + (int) (point.getY() * scaleY), POINT_SIZE, paint);
			// }
			// }
			// }
			// if (currentLast != null)
			// {
			// paint.setAlpha(CURRENT_POINT_OPACITY / 2);
			// paint.setColor(resultPointColor);
			// synchronized (currentLast)
			// {
			// float radius = POINT_SIZE / 2.0f;
			// for (ResultPoint point : currentLast)
			// {
			// canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX), frameTop
			// + (int) (point.getY() * scaleY), radius, paint);
			// }
			// }
			// }

			// Request another update at the animation interval, but only repaint the laser line,
			// not the entire viewfinder mask.
			// 只刷新扫描框的内容，其他地方不刷新
			postInvalidateDelayed(ANIMATION_DELAY, frame.left - POINT_SIZE, frame.top - POINT_SIZE, frame.right
					+ POINT_SIZE, frame.bottom + POINT_SIZE);
		}
	}

	public void drawViewfinder()
	{
		Bitmap resultBitmap = this.resultBitmap;
		this.resultBitmap = null;
		if (resultBitmap != null)
		{
			resultBitmap.recycle();
		}
		invalidate();
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live scanning display.
	 *
	 * @param barcode An image of the decoded barcode.
	 */
	public void drawResultBitmap(Bitmap barcode)
	{
		resultBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point)
	{
		List<ResultPoint> points = possibleResultPoints;
		synchronized (points)
		{
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS)
			{
				// trim it
				points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}

}
