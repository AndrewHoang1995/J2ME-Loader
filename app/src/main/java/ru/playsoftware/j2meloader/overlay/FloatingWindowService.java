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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.AppUtils;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.woesss.j2me.jar.Descriptor;

public class FloatingWindowService extends Service {
	private static final String TAG = "FloatingWindowService";
	private static final String CHANNEL_ID = "FloatingWindowChannel";
	private static final int NOTIFICATION_ID = 1001;

	private static final Map<String, MicroActivity> activityReferences = new HashMap<>();

	private WindowManager windowManager;
	private final Map<String, FloatingWindow> floatingWindows = new HashMap<>();

	public static void setActivityReference(String windowId, MicroActivity activity) {
		activityReferences.put(windowId, activity);
	}
	
	public static void removeActivityReference(String windowId) {
		activityReferences.remove(windowId);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		createNotificationChannel();
		startForeground(NOTIFICATION_ID, createNotification());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			String windowId = intent.getStringExtra("windowId");
			
			if ("START_FLOATING".equals(action)) {
				String appName = intent.getStringExtra("appName");
				showFloatingWindow(windowId, appName);
			} else if ("MINIMIZE".equals(action)) {
				minimizeToBubble(windowId);
			} else if ("MAXIMIZE".equals(action)) {
				maximizeFromBubble(windowId);
			} else if ("MAXIMIZE_WINDOW".equals(action)) {
				maximizeWindow(windowId);
			} else if ("STOP".equals(action)) {
				if (windowId != null) {
					stopFloatingWindow(windowId);
				} else {
					// Stop all windows
					stopAllFloatingWindows();
				}
				if (floatingWindows.isEmpty()) {
					stopForeground(true);
					stopSelf();
				} else {
					updateNotification();
				}
			}
		}
		return START_STICKY;
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
					CHANNEL_ID,
					"Floating Window Service",
					NotificationManager.IMPORTANCE_LOW
			);
			channel.setDescription("Keeps game running in background");
			NotificationManager manager = getSystemService(NotificationManager.class);
			if (manager != null) {
				manager.createNotificationChannel(channel);
			}
		}
	}

	private Notification createNotification() {
		int windowCount = floatingWindows.size();
		String title = windowCount > 1 
				? windowCount + " games running" 
				: (windowCount == 1 ? floatingWindows.values().iterator().next().getAppName() : "J2ME Games");
		
		Intent notificationIntent = new Intent(this, MicroActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				this, 0, notificationIntent,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
		);

		Intent stopAllIntent = new Intent(this, FloatingWindowService.class);
		stopAllIntent.setAction("STOP");
		PendingIntent stopAllPending = PendingIntent.getService(
				this, 0, stopAllIntent,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
		);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(title)
				.setContentText(windowCount + " game(s) running in background")
				.setSmallIcon(R.mipmap.ic_launcher)
				.setContentIntent(pendingIntent)
				.setOngoing(true);
		
		if (windowCount > 0) {
			builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop All", stopAllPending);
		}
		
		return builder.build();
	}
	
	private void updateNotification() {
		NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.notify(NOTIFICATION_ID, createNotification());
		}
	}

	private void showFloatingWindow(String windowId, String appName) {
		if (windowId == null) {
			Log.e(TAG, "WindowId is null");
			return;
		}
		
		// Check if window already exists
		if (floatingWindows.containsKey(windowId)) {
			FloatingWindow window = floatingWindows.get(windowId);
			window.showFloatingView();
			return;
		}

		// Try to get activity from reference
		MicroActivity activity = activityReferences.get(windowId);
		if (activity == null || activity.binding == null) {
			Log.e(TAG, "Activity reference is null for windowId: " + windowId);
			// Wait a bit and try again
			new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
				MicroActivity retryActivity = activityReferences.get(windowId);
				if (retryActivity != null && retryActivity.binding != null) {
					showFloatingWindowInternal(windowId, appName, retryActivity);
				} else {
					// Try ContextHolder as fallback
					try {
						MicroActivity fallbackActivity = ContextHolder.getActivity();
						if (fallbackActivity != null && fallbackActivity.binding != null) {
							showFloatingWindowInternal(windowId, appName, fallbackActivity);
						} else {
							Log.e(TAG, "Activity still null after retry for windowId: " + windowId);
						}
					} catch (NullPointerException e) {
						Log.e(TAG, "ContextHolder.getActivity() failed: " + e.getMessage());
					}
				}
			}, 500);
			return;
		}
		
		showFloatingWindowInternal(windowId, appName, activity);
	}

	private void showFloatingWindowInternal(String windowId, String appName, MicroActivity activity) {
		// Get appPath from activity
		String appPath = getAppPathFromActivity(activity);
		FloatingWindow window = new FloatingWindow(windowId, appName, appPath, activity, windowManager);
		window.createFloatingView();
		
		FloatingWindowView floatingView = window.getFloatingView();
		floatingView.setOnCloseListener(() -> {
			Intent intent = new Intent(this, FloatingWindowService.class);
			intent.setAction("STOP");
			intent.putExtra("windowId", windowId);
			startService(intent);
		});
		floatingView.setOnMinimizeListener(() -> {
			Intent intent = new Intent(this, FloatingWindowService.class);
			intent.setAction("MINIMIZE");
			intent.putExtra("windowId", windowId);
			startService(intent);
		});
		floatingView.setOnMaximizeListener(() -> {
			Intent intent = new Intent(this, FloatingWindowService.class);
			intent.setAction("MAXIMIZE_WINDOW");
			intent.putExtra("windowId", windowId);
			startService(intent);
		});

		// Tạo một FrameLayout wrapper để chứa activity content
		FrameLayout wrapper = new FrameLayout(this);
		View activityRoot = activity.binding.getRoot();
		
		// Lưu parent và layout params để restore sau
		android.view.ViewGroup originalParent = (android.view.ViewGroup) activityRoot.getParent();
		android.view.ViewGroup.LayoutParams originalParams = activityRoot.getLayoutParams();
		
		// Lưu vào window để restore sau
		window.setOriginalParent(originalParent, originalParams);
		
		// Remove từ parent cũ và thêm vào wrapper
		if (originalParent != null) {
			originalParent.removeView(activityRoot);
		}
		
		wrapper.addView(activityRoot, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
		));
		
		// Thêm wrapper vào floating view
		FrameLayout contentContainer = floatingView.findViewById(R.id.content_container);
		if (contentContainer != null) {
			contentContainer.addView(wrapper);
		}

		try {
			window.showFloatingView();
			floatingWindows.put(windowId, window);
			updateNotification();
			// Move activity to background nhưng vẫn giữ nó chạy
			activity.moveTaskToBack(true);
		} catch (Exception e) {
			Log.e(TAG, "Error showing floating window for " + windowId, e);
			// Restore view nếu có lỗi
			if (originalParent != null && activityRoot.getParent() == wrapper) {
				wrapper.removeView(activityRoot);
				originalParent.addView(activityRoot, originalParams);
			}
		}
	}

	private void minimizeToBubble(String windowId) {
		if (windowId == null) {
			return;
		}
		
		FloatingWindow window = floatingWindows.get(windowId);
		if (window == null || window.isMinimized()) {
			return;
		}

		window.hideFloatingView();
		window.createBubbleView();
		
		BubbleView bubbleView = window.getBubbleView();
		
		// Đếm số bubble đang minimized để đánh số
		int bubbleCount = 0;
		for (FloatingWindow w : floatingWindows.values()) {
			if (w.isMinimized() && w.getBubbleView() != null) {
				bubbleCount++;
			}
		}
		bubbleView.setBubbleNumber(bubbleCount + 1);
		
		// Load icon từ appPath
		try {
			String appPath = window.getAppPath();
			if (appPath != null) {
				android.graphics.Bitmap iconBitmap = loadAppIcon(appPath);
				if (iconBitmap != null) {
					bubbleView.setIcon(iconBitmap);
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "Failed to load icon for bubble: " + windowId, e);
		}
		
		// Set dismiss listener
		bubbleView.setOnDismissListener(() -> {
			Intent intent = new Intent(this, FloatingWindowService.class);
			intent.setAction("STOP");
			intent.putExtra("windowId", windowId);
			startService(intent);
		});
		
		// Click để maximize
		bubbleView.setOnClickListener(v -> {
			Intent intent = new Intent(this, FloatingWindowService.class);
			intent.setAction("MAXIMIZE");
			intent.putExtra("windowId", windowId);
			startService(intent);
		});

		WindowManager.LayoutParams bubbleParams = window.getBubbleParams();
		int screenHeight = getResources().getDisplayMetrics().heightPixels;
		int dismissThreshold = (int) (screenHeight * 0.7f); // 70% từ trên xuống để dismiss
		
		bubbleView.setOnTouchListener(new BubbleTouchListener(bubbleView, bubbleParams, windowManager, screenHeight, dismissThreshold));

		try {
			window.showBubbleView();
			window.setMinimized(true);
		} catch (Exception e) {
			Log.e(TAG, "Error showing bubble for " + windowId, e);
		}
	}

	private void maximizeFromBubble(String windowId) {
		if (windowId == null) {
			return;
		}
		
		FloatingWindow window = floatingWindows.get(windowId);
		if (window == null) {
			return;
		}

		if (!window.isMinimized()) {
			// If not minimized, just show floating window
			window.showFloatingView();
			return;
		}

		// Remove bubble
		window.hideBubbleView();

		// Show floating window
		window.showFloatingView();
		window.setMinimized(false);
	}
	
	private void maximizeWindow(String windowId) {
		if (windowId == null) {
			return;
		}
		
		FloatingWindow window = floatingWindows.get(windowId);
		if (window == null) {
			return;
		}
		
		// Nếu đang minimized, hide bubble và show floating window
		if (window.isMinimized()) {
			window.hideBubbleView();
			window.showFloatingView();
			window.setMinimized(false);
		}
		
		// Mở to floating window để đè lên MainActivity
		FloatingWindowView floatingView = window.getFloatingView();
		if (floatingView != null && window.getFloatingParams() != null) {
			try {
				WindowManager.LayoutParams params = window.getFloatingParams();
				
				// Resize để to hơn - gần fullscreen
				int screenWidth = getResources().getDisplayMetrics().widthPixels;
				int screenHeight = getResources().getDisplayMetrics().heightPixels;
				
				// Set size gần fullscreen (90% màn hình)
				params.width = (int) (screenWidth * 0.95f);
				params.height = (int) (screenHeight * 0.9f);
				
				// Center window
				params.x = (screenWidth - params.width) / 2;
				params.y = (screenHeight - params.height) / 2;
				
				// Update layout
				windowManager.updateViewLayout(floatingView, params);
			} catch (Exception e) {
				Log.e(TAG, "Error maximizing window for " + windowId, e);
			}
		}
	}

	private void stopFloatingWindow(String windowId) {
		if (windowId == null) {
			return;
		}
		
		FloatingWindow window = floatingWindows.get(windowId);
		if (window == null) {
			return;
		}

		FloatingWindowView floatingView = window.getFloatingView();
		if (floatingView != null) {
			try {
				// Restore view về activity trước khi remove
				FrameLayout contentContainer = floatingView.findViewById(R.id.content_container);
				if (contentContainer != null && contentContainer.getChildCount() > 0) {
					FrameLayout wrapper = (FrameLayout) contentContainer.getChildAt(0);
					if (wrapper != null && wrapper.getChildCount() > 0) {
						View activityRoot = wrapper.getChildAt(0);
						MicroActivity activity = window.getActivity();
						if (activity != null && activity.binding != null) {
							wrapper.removeView(activityRoot);
							activity.setContentView(activityRoot);
						}
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Error restoring view for " + windowId, e);
			}
		}

		window.destroy();
		floatingWindows.remove(windowId);
		removeActivityReference(windowId);
	}

	private void stopAllFloatingWindows() {
		Iterator<Map.Entry<String, FloatingWindow>> iterator = floatingWindows.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, FloatingWindow> entry = iterator.next();
			String windowId = entry.getKey();
			stopFloatingWindow(windowId);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopAllFloatingWindows();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private String getAppPathFromActivity(MicroActivity activity) {
		// Try to get appPath from activity using reflection or stored value
		// For now, we'll try to extract from the activity's appPath field
		try {
			java.lang.reflect.Field field = activity.getClass().getDeclaredField("appPath");
			field.setAccessible(true);
			return (String) field.get(activity);
		} catch (Exception e) {
			// Fallback: try to get from intent
			android.content.Intent intent = activity.getIntent();
			if (intent != null && intent.getData() != null) {
				return intent.getData().toString();
			}
		}
		return null;
	}
	
	private android.graphics.Bitmap loadAppIcon(String appPath) {
		try {
			File appDir;
			if (appPath.startsWith("file://")) {
				appDir = new File(java.net.URI.create(appPath));
			} else {
				appDir = new File(appPath);
			}
			
			// Try to load icon from standard locations
			File iconFile = new File(appDir, Config.MIDLET_ICON_FILE);
			if (!iconFile.exists()) {
				// Try to get from manifest
				File manifestFile = new File(appDir, Config.MIDLET_MANIFEST_FILE);
				if (manifestFile.exists()) {
					Descriptor params = new Descriptor(manifestFile, false);
					String iconPath = Config.MIDLET_RES_DIR + '/' + params.getIcon();
					iconFile = new File(appDir, iconPath);
				}
			}
			
			if (iconFile.exists()) {
				return android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
			}
		} catch (Exception e) {
			Log.w(TAG, "Failed to load app icon from: " + appPath, e);
		}
		return null;
	}
	
	// Helper class để xử lý smooth drag như Messenger
	private class BubbleTouchListener implements View.OnTouchListener {
		private final BubbleView bubbleView;
		private final WindowManager.LayoutParams params;
		private final WindowManager windowManager;
		private final int screenHeight;
		private final int dismissThreshold;
		private final int screenWidth;
		private final int bubbleSize = 180;
		
		private int initialX, initialY;
		private float initialTouchX, initialTouchY;
		private float touchOffsetX = 0, touchOffsetY = 0; // Offset giữa touch point và bubble center
		private long lastMoveTime = 0;
		private float velocityX = 0, velocityY = 0;
		private float lastX = 0, lastY = 0;
		private boolean isDragging = false;
		private ValueAnimator snapAnimator;
		
		public BubbleTouchListener(BubbleView bubbleView, WindowManager.LayoutParams params,
		                           WindowManager windowManager, int screenHeight, int dismissThreshold) {
			this.bubbleView = bubbleView;
			this.params = params;
			this.windowManager = windowManager;
			this.screenHeight = screenHeight;
			this.dismissThreshold = dismissThreshold;
			this.screenWidth = getResources().getDisplayMetrics().widthPixels;
		}
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (params == null) {
				return false;
			}
			
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					// Reset alpha về 1.0 khi touch
					bubbleView.onTouchDown();
					
					// Cancel any ongoing animation
					if (snapAnimator != null && snapAnimator.isRunning()) {
						snapAnimator.cancel();
					}
					
					// Lưu vị trí touch ngay lập tức
					initialTouchX = event.getRawX();
					initialTouchY = event.getRawY();
					
					// Reset offset - sẽ được tính lại khi bắt đầu drag
					touchOffsetX = 0;
					touchOffsetY = 0;
					
					lastX = 0;
					lastY = 0;
					lastMoveTime = System.currentTimeMillis();
					velocityX = 0;
					velocityY = 0;
					isDragging = false;
					
					return false; // Allow click event
					
				case MotionEvent.ACTION_MOVE:
					float currentX = event.getRawX();
					float currentY = event.getRawY();
					float deltaX = currentX - initialTouchX;
					float deltaY = currentY - initialTouchY;
					
					// Calculate velocity for smooth movement
					long currentTime = System.currentTimeMillis();
					long deltaTime = currentTime - lastMoveTime;
					if (deltaTime > 0) {
						float dx = currentX - lastX;
						float dy = currentY - lastY;
						velocityX = dx / deltaTime * 1000; // pixels per second
						velocityY = dy / deltaTime * 1000;
					}
					lastX = currentX;
					lastY = currentY;
					lastMoveTime = currentTime;
					
					// Nếu kéo đủ xa thì bắt đầu drag
					if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10 || isDragging) {
						if (!isDragging) {
							// Scale up khi bắt đầu drag
							bubbleView.setScale(1.15f);
						}
						
						isDragging = true;
						bubbleView.setClickable(false);
						
						// Tính vị trí bubble center dựa trên touch point và offset đã lưu
						// Giữ nguyên offset để bubble không nhảy, chỉ di chuyển mượt mà
						float bubbleCenterX = currentX - touchOffsetX;
						float bubbleCenterY = currentY - touchOffsetY;
						
						// Tính vị trí bubble (top-left corner)
						int bubbleX = (int) (bubbleCenterX - bubbleSize / 2f);
						int bubbleY = (int) (bubbleCenterY - bubbleSize / 2f);
						
						// Giới hạn trong màn hình nhưng cho phép một phần ra ngoài
						bubbleX = Math.max(-bubbleSize / 2, Math.min(screenWidth - bubbleSize / 2, bubbleX));
						bubbleY = Math.max(0, Math.min(screenHeight - bubbleSize, bubbleY));
						params.x = bubbleX;
						params.y = bubbleY;
						
						try {
							windowManager.updateViewLayout(bubbleView, params);
						} catch (Exception e) {
							// Ignore
						}
						
						// Scale animation khi drag - nhỏ hơn một chút khi kéo xa
						float dragDistance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
						float scale = Math.max(0.98f, 1.15f - dragDistance / 600f);
						bubbleView.setScale(scale);
						
						// Check dismiss state
						if (currentY > dismissThreshold) {
							float dismissProgress = Math.min(1.0f, (currentY - dismissThreshold) / (screenHeight - dismissThreshold));
							bubbleView.setDismissing(true, 1.0f - dismissProgress * 0.5f);
						} else {
							bubbleView.setDismissing(false, 1.0f);
						}
						return true;
					}
					return false;
					
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					bubbleView.setClickable(true);
					
					// Bắt đầu đếm thời gian idle sau khi ACTION_UP
					bubbleView.onTouchUp();
					
					if (isDragging) {
						// Check dismiss
						float finalY = event.getRawY();
						if (finalY > dismissThreshold) {
							bubbleView.triggerDismiss();
							return true;
						}
						
						// Snap to nearest edge với animation mượt
						isDragging = false;
						return true;
					} else {
						// Không drag, chỉ click - không cần reset scale vì chưa scale
						// bubbleView.setScale(1.0f);
					}
					return false;
			}
			return false;
		}


	}
}
