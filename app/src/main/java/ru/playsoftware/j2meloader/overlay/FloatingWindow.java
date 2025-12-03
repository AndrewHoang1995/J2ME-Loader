/*
 * Copyright 2024 J2ME-Loader
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.overlay;

import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import javax.microedition.shell.MicroActivity;

public class FloatingWindow {
	private static final String TAG = "FloatingWindow";
	
	private final String windowId;
	private final String appName;
	private final String appPath;
	private final MicroActivity activity;
	private FloatingWindowView floatingView;
	private BubbleView bubbleView;
	private WindowManager.LayoutParams floatingParams;
	private WindowManager.LayoutParams bubbleParams;
	private boolean isMinimized = false;
	private WindowManager windowManager;
	
	// Lưu thông tin để restore view
	private android.view.ViewGroup originalParent;
	private android.view.ViewGroup.LayoutParams originalParams;
	
	public FloatingWindow(String windowId, String appName, String appPath, MicroActivity activity, WindowManager windowManager) {
		this.windowId = windowId;
		this.appName = appName;
		this.appPath = appPath;
		this.activity = activity;
		this.windowManager = windowManager;
	}
	
	public String getAppPath() {
		return appPath;
	}
	
	public String getWindowId() {
		return windowId;
	}
	
	public String getAppName() {
		return appName;
	}
	
	public MicroActivity getActivity() {
		return activity;
	}
	
	public FloatingWindowView getFloatingView() {
		return floatingView;
	}
	
	public BubbleView getBubbleView() {
		return bubbleView;
	}
	
	public WindowManager.LayoutParams getBubbleParams() {
		return bubbleParams;
	}
	
	public WindowManager.LayoutParams getFloatingParams() {
		return floatingParams;
	}
	
	public boolean isMinimized() {
		return isMinimized;
	}
	
	public void setMinimized(boolean minimized) {
		isMinimized = minimized;
	}
	
	public void createFloatingView() {
		if (floatingView != null) {
			return;
		}
		
		floatingView = new FloatingWindowView(activity, activity);
		floatingView.setTag(windowId);
		
		int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
				? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
				: WindowManager.LayoutParams.TYPE_PHONE;
		
		floatingParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				type,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
						| WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
				PixelFormat.TRANSLUCENT
		);
		
		floatingParams.gravity = Gravity.TOP | Gravity.START;
		floatingParams.x = 100 + (windowId.hashCode() % 200);
		floatingParams.y = 100 + (windowId.hashCode() % 200);
		floatingParams.width = (int) (windowManager.getDefaultDisplay().getWidth() * 0.8f);
		floatingParams.height = (int) (windowManager.getDefaultDisplay().getHeight() * 0.7f);
		floatingView.setParams(floatingParams);
	}
	
	public void showFloatingView() {
		if (floatingView == null) {
			createFloatingView();
		}
		
		try {
			if (floatingView.getParent() == null) {
				windowManager.addView(floatingView, floatingParams);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error showing floating view for " + windowId, e);
		}
	}
	
	public void hideFloatingView() {
		if (floatingView != null) {
			try {
				if (floatingView.getParent() != null) {
					windowManager.removeView(floatingView);
				}
			} catch (IllegalArgumentException e) {
				Log.w(TAG, "Floating view already removed for " + windowId);
			} catch (Exception e) {
				Log.e(TAG, "Error hiding floating view for " + windowId, e);
			}
		}
	}
	
	public void createBubbleView() {
		if (bubbleView != null) {
			return;
		}
		
		bubbleView = new BubbleView(activity);
		bubbleView.setTag(windowId);
		
		int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
				? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
				: WindowManager.LayoutParams.TYPE_PHONE;
		
		bubbleParams = new WindowManager.LayoutParams(
				180, 180, // Tăng kích thước từ 120 lên 180
				type,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
				PixelFormat.TRANSLUCENT
		);
		
		// Sử dụng Gravity.TOP | Gravity.START để có thể kéo sang trái
		// Khi drag sẽ thay đổi gravity thành NO_GRAVITY để tự do di chuyển
		bubbleParams.gravity = Gravity.TOP | Gravity.START;
		bubbleParams.x = 0;
		bubbleParams.y = 200 + (windowId.hashCode() % 300);
	}
	
	public void showBubbleView() {
		if (bubbleView == null) {
			createBubbleView();
		}
		
		try {
			if (bubbleView.getParent() == null) {
				windowManager.addView(bubbleView, bubbleParams);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error showing bubble for " + windowId, e);
		}
	}
	
	public void hideBubbleView() {
		if (bubbleView != null) {
			try {
				if (bubbleView.getParent() != null) {
					windowManager.removeView(bubbleView);
				}
			} catch (IllegalArgumentException e) {
				Log.w(TAG, "Bubble already removed for " + windowId);
			} catch (Exception e) {
				Log.e(TAG, "Error hiding bubble for " + windowId, e);
			}
		}
	}
	
	public void setOriginalParent(android.view.ViewGroup parent, android.view.ViewGroup.LayoutParams params) {
		this.originalParent = parent;
		this.originalParams = params;
	}
	
	public android.view.ViewGroup getOriginalParent() {
		return originalParent;
	}
	
	public android.view.ViewGroup.LayoutParams getOriginalParams() {
		return originalParams;
	}
	
	public void destroy() {
		hideFloatingView();
		hideBubbleView();
		floatingView = null;
		bubbleView = null;
		floatingParams = null;
		bubbleParams = null;
		originalParent = null;
		originalParams = null;
	}
}

