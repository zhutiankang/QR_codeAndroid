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

package com.google.zxing.demo;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.core.BarcodeFormat;
import com.google.zxing.core.DecodeHintType;
import com.google.zxing.core.Result;
import com.google.zxing.core.ResultMetadataType;
import com.google.zxing.core.client.result.ParsedResultType;
import com.google.zxing.demo.camera.CameraManager;
import com.google.zxing.demo.endecode.FinishListener;
import com.google.zxing.demo.module.QRCodeTypeDefine;
import com.google.zxing.demo.view.ViewfinderView;
import com.google.zxing.demo.widge.SystemBarTintManager;
import com.micen.focusqrcode.R;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback, OnClickListener
{

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
	private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

	private static final String[] ZXING_URLS =
	{ "http://zxing.appspot.com/scan", "zxing://scan/" };

	public static final int HISTORY_REQUEST_CODE = 0x0000bacc;

	private static final Collection<ResultMetadataType> DISPLAYABLE_METADATA_TYPES = EnumSet.of(
			ResultMetadataType.ISSUE_NUMBER, ResultMetadataType.SUGGESTED_PRICE,
			ResultMetadataType.ERROR_CORRECTION_LEVEL, ResultMetadataType.POSSIBLE_COUNTRY);

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private Result savedResultToShow;
	private ViewfinderView viewfinderView;
	private Result lastResult;
	private boolean hasSurface;
	private String sourceUrl;
	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	private RelativeLayout zxingCaptureRlBack;

	public ViewfinderView getViewfinderView()
	{
		return viewfinderView;
	}

	public Handler getHandler()
	{
		return handler;
	}

	public CameraManager getCameraManager()
	{
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_zxing_capture);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
		{
			SystemBarTintManager tintManager = new SystemBarTintManager(this);
			tintManager.setStatusBarTintEnabled(true);
			tintManager.setStatusBarTintColor(getResources().getColor(R.color.color_333333));
		}
		zxingCaptureRlBack = (RelativeLayout) findViewById(R.id.zxing_capture_rl_back);
		zxingCaptureRlBack.setOnClickListener(this);
		zxingCaptureRlBack.setBackgroundResource(R.drawable.bg_common_btn);

		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);

	}

	@Override
	protected void onResume()
	{
		super.onResume();
		// CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
		// want to open the camera driver and measure the screen size if we're going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);
		handler = null;
		lastResult = null;
		resetStatusView();

		beepManager.updatePrefs();
		// ambientLightManager.start(cameraManager);

		inactivityTimer.onResume();

		sourceUrl = null;
		// scanFromWebPageManager = null;
		decodeFormats = null;
		characterSet = null;

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface)
		{
			// The activity was paused but not stopped, so the surface still exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		}
		else
		{
			// Install the callback and wait for surfaceCreated() to init the camera.
			surfaceHolder.addCallback(this);
		}
	}

	private static boolean isZXingURL(String dataString)
	{
		if (dataString == null)
		{
			return false;
		}
		for (String url : ZXING_URLS)
		{
			if (dataString.startsWith(url))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onPause()
	{
		if (handler != null)
		{
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		// ambientLightManager.stop();
		beepManager.close();
		cameraManager.closeDriver();
		// historyManager = null; // Keep for onActivityResult
		if (!hasSurface)
		{
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	// TODO
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode)
		{
		case KeyEvent.KEYCODE_BACK:
			finish();
			return true;
		case KeyEvent.KEYCODE_FOCUS:
		case KeyEvent.KEYCODE_CAMERA:
			// Handle these events so they don't launch the Camera app
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		if (holder == null)
		{
			Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface)
		{
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show the results.
	 *
	 * @param rawResult The contents of the barcode.
	 * @param scaleFactor amount by which thumbnail was scaled
	 * @param barcode   A greyscale bitmap of the camera data which was decoded.
	 */
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor)
	{
		inactivityTimer.onActivity();
		lastResult = rawResult;
		if (TextUtils.isEmpty(lastResult.getText()))
		{
			Toast.makeText(CaptureActivity.this, R.string.scan_failed, Toast.LENGTH_SHORT).show();
			restartPreviewAfterDelay(3000);
		}
		else
		{
			try
			{
				JSONObject object = new JSONObject(rawResult.getText());
				if (object.has("isMIC") && object.has("action") && object.has("param"))
				{
					if (QRCodeTypeDefine.getValue(QRCodeTypeDefine.isMIC).equals(object.getString("isMIC")))
					{
						JSONObject jsonObject = object.getJSONObject("param");
						String action = object.getString("action");
						// ParsedResultType.MIC
						setResult(RESULT_OK, new Intent().putExtra("resultType", ParsedResultType.MIC.toString())
								.putExtra("action", action).putExtra("resultContent", jsonObject.toString()));
						finish();
						return;
					}
				}
				else
				{
					setResultForText(rawResult);
				}

			}
			catch (JSONException e)
			{
				e.printStackTrace();
				setResultForText(rawResult);

			}
		}
	}

	private void setResultForText(Result rawResult)
	{
		setResult(
				RESULT_OK,
				new Intent().putExtra("resultType", ParsedResultType.TEXT.toString()).putExtra("resultContent",
						rawResult.getText()));
		finish();
	}

	private void initCamera(SurfaceHolder surfaceHolder)
	{
		if (surfaceHolder == null)
		{
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen())
		{
			Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try
		{
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a RuntimeException.
			if (handler == null)
			{
				handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
			}
			// decodeOrStoreSavedBitmap(null, null);
		}
		catch (IOException ioe)
		{
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		}
		catch (RuntimeException e)
		{
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS)
	{
		if (handler != null)
		{
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
		resetStatusView();
	}

	private void resetStatusView()
	{
		// resultView.setVisibility(View.GONE);
		// statusView.setText(R.string.msg_default_status);
		// statusView.setVisibility(View.VISIBLE);
		viewfinderView.setVisibility(View.VISIBLE);
		lastResult = null;
	}

	public void drawViewfinder()
	{
		viewfinderView.drawViewfinder();
	}

	@Override
	public void onClick(View v)
	{
		if (R.id.zxing_capture_rl_back == v.getId())
		{
			setResult(RESULT_CANCELED);
			finish();
		}
	}

}
