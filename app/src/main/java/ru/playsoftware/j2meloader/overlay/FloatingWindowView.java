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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;

public class FloatingWindowView extends FrameLayout {
	private WindowManager windowManager;
	private WindowManager.LayoutParams params;
	private MicroActivity activity;
	private OnCloseListener onCloseListener;
	private OnMinimizeListener onMinimizeListener;
	private OnMaximizeListener onMaximizeListener;
	private View headerView;
	private FrameLayout contentContainer;
	private int initialX, initialY;
	private float initialTouchX, initialTouchY;

	public interface OnCloseListener {
		void onClose();
	}

	public interface OnMinimizeListener {
		void onMinimize();
	}
	
	public interface OnMaximizeListener {
		void onMaximize();
	}

	public FloatingWindowView(Context context, MicroActivity activity) {
		super(context);
		this.activity = activity;
		this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		init();
	}

	public FloatingWindowView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		setBackgroundColor(0xFF000000);
		
		// Inflate layout với header và content container
		LayoutInflater.from(getContext()).inflate(R.layout.floating_window_content, this, true);
		
		// Get header view from included layout
		headerView = findViewById(R.id.header_view);
		contentContainer = findViewById(R.id.content_container);
		
		// Find buttons in header - headerView is the included LinearLayout
		ImageView closeBtn = null;
		ImageView minimizeBtn = null;
		ImageView maximizeBtn = null;
		if (headerView != null) {
			closeBtn = headerView.findViewById(R.id.btn_close);
			minimizeBtn = headerView.findViewById(R.id.btn_minimize);
			maximizeBtn = headerView.findViewById(R.id.btn_maximize);
		}

		if (closeBtn != null) {
			closeBtn.setOnClickListener(v -> {
				if (onCloseListener != null) {
					onCloseListener.onClose();
				}
			});
		}

		if (minimizeBtn != null) {
			minimizeBtn.setOnClickListener(v -> {
				if (onMinimizeListener != null) {
					onMinimizeListener.onMinimize();
				}
			});
		}
		
		if (maximizeBtn != null) {
			maximizeBtn.setOnClickListener(v -> {
				if (onMaximizeListener != null) {
					onMaximizeListener.onMaximize();
				}
			});
		}

		// Content sẽ được thêm từ service

		// Drag to move - set touch listener on header
		if (headerView != null) {
			headerView.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (params == null) {
						return false;
					}
					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							initialX = params.x;
							initialY = params.y;
							initialTouchX = event.getRawX();
							initialTouchY = event.getRawY();
							return true;
						case MotionEvent.ACTION_MOVE:
							params.x = initialX + (int) (event.getRawX() - initialTouchX);
							params.y = initialY + (int) (event.getRawY() - initialTouchY);
							windowManager.updateViewLayout(FloatingWindowView.this, params);
							return true;
					}
					return false;
				}
			});
		}
	}

	public void setParams(WindowManager.LayoutParams params) {
		this.params = params;
	}

	public void setOnCloseListener(OnCloseListener listener) {
		this.onCloseListener = listener;
	}

	public void setOnMinimizeListener(OnMinimizeListener listener) {
		this.onMinimizeListener = listener;
	}
	
	public void setOnMaximizeListener(OnMaximizeListener listener) {
		this.onMaximizeListener = listener;
	}
}

