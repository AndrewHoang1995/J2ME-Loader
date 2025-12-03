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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import ru.playsoftware.j2meloader.R;

public class BubbleView extends View {
	private Paint paint;
	private Paint textPaint;
	private Path bubblePath;
	private OnDismissListener onDismissListener;
	private float dismissThreshold = 0.3f; // 30% từ cạnh dưới để dismiss
	private boolean isDismissing = false;
	private float currentAlpha = 1.0f;
	private Bitmap iconBitmap;
	private int bubbleNumber = 0; // Số thứ tự của bubble
	private float scale = 1.0f; // Scale khi drag
	private float idleAlpha = 1.0f; // Alpha khi idle (0.7 sau 1.5s không touch)
	private Handler handler;
	private Runnable idleAlphaRunnable;

	public interface OnDismissListener {
		void onDismiss();
	}

	public BubbleView(Context context) {
		super(context);
		init();
	}

	public BubbleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(0xFF6200EE);
		paint.setStyle(Paint.Style.FILL);
		
		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		textPaint.setColor(0xFFFFFFFF);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setStyle(Paint.Style.FILL);
		
		bubblePath = new Path();
		handler = new Handler(Looper.getMainLooper());
		idleAlphaRunnable = () -> {
			if (idleAlpha != 0.7f) {
				idleAlpha = 0.7f;
				invalidate();
			}
		};
	}
	
	public void setOnDismissListener(OnDismissListener listener) {
		this.onDismissListener = listener;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		// Apply scale transformation
		canvas.save();
		int width = getWidth();
		int height = getHeight();
		canvas.scale(scale, scale, width / 2f, height / 2f);
		
		int radius = Math.min(width, height) / 2 - 10;
		int centerX = width / 2;
		int centerY = height / 2;

		// Apply alpha khi đang dismiss và idle alpha
		float finalAlpha = currentAlpha * idleAlpha;
		int alpha = (int) (255 * finalAlpha);
		paint.setAlpha(alpha);
		textPaint.setAlpha(alpha);

		// Vẽ bubble hình tròn
		bubblePath.reset();
		bubblePath.addCircle(centerX, centerY, radius, Path.Direction.CW);
		
		// Đổi màu khi đang dismiss
		if (isDismissing) {
			paint.setColor(0xFFFF0000); // Màu đỏ khi đang dismiss
		} else {
			paint.setColor(0xFF6200EE);
		}
		
		canvas.drawPath(bubblePath, paint);
		
		// Vẽ icon game hoặc dismiss icon
		if (isDismissing) {
			// Vẽ icon X khi đang dismiss
			textPaint.setTextSize(radius * 0.6f);
			canvas.drawText("✕", centerX, centerY + radius / 3, textPaint);
		} else if (iconBitmap != null) {
			// Vẽ icon game
			int iconSize = (int) (radius * 1.4f);
			int iconLeft = centerX - iconSize / 2;
			int iconTop = centerY - iconSize / 2;
			Rect src = new Rect(0, 0, iconBitmap.getWidth(), iconBitmap.getHeight());
			RectF dst = new RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
			canvas.drawBitmap(iconBitmap, src, dst, paint);
		} else {
			// Fallback: vẽ play icon nếu không có icon game
			textPaint.setTextSize(radius * 0.6f);
			canvas.drawText("▶", centerX, centerY + radius / 3, textPaint);
		}
		
		// Vẽ số thứ tự ở góc trên bên phải
		if (bubbleNumber > 0) {
			Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			numberPaint.setColor(0xFFFFFFFF);
			numberPaint.setStyle(Paint.Style.FILL);
			numberPaint.setTextSize(radius * 0.4f);
			numberPaint.setFakeBoldText(true);
			
			// Vẽ background tròn cho số
			float numberX = width - radius * 0.7f;
			float numberY = radius * 0.7f;
			float numberRadius = radius * 0.3f;
			Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			bgPaint.setColor(0xFF000000);
			bgPaint.setStyle(Paint.Style.FILL);
			bgPaint.setAlpha((int) (255 * finalAlpha));
			canvas.drawCircle(numberX, numberY, numberRadius, bgPaint);
			
			// Vẽ số
			String numberText = String.valueOf(bubbleNumber);
			numberPaint.setAlpha((int) (255 * finalAlpha));
			canvas.drawText(numberText, numberX, numberY + numberRadius * 0.4f, numberPaint);
		}
		
		// Reset alpha
		paint.setAlpha(255);
		textPaint.setAlpha(255);
		
		canvas.restore();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int size = Math.min(
				MeasureSpec.getSize(widthMeasureSpec),
				MeasureSpec.getSize(heightMeasureSpec)
		);
		setMeasuredDimension(size, size);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Touch handling sẽ được xử lý bởi OnTouchListener trong FloatingWindowService
		return super.onTouchEvent(event);
	}
	
	public void setDismissing(boolean dismissing, float alpha) {
		if (isDismissing != dismissing || currentAlpha != alpha) {
			isDismissing = dismissing;
			currentAlpha = alpha;
			invalidate();
		}
	}
	
	public void triggerDismiss() {
		if (onDismissListener != null) {
			onDismissListener.onDismiss();
		}
	}
	
	public void setIcon(Bitmap bitmap) {
		this.iconBitmap = bitmap;
		invalidate();
	}
	
	public void setBubbleNumber(int number) {
		this.bubbleNumber = number;
		invalidate();
	}
	
	public void setScale(float scale) {
		if (this.scale != scale) {
			this.scale = scale;
			invalidate();
		}
	}
	
	/**
	 * Gọi khi ACTION_UP để bắt đầu đếm thời gian idle
	 */
	public void onTouchUp() {
		// Hủy runnable cũ nếu có
		handler.removeCallbacks(idleAlphaRunnable);
		// Đặt lại alpha về 1.0 khi touch
		if (idleAlpha != 1.0f) {
			idleAlpha = 1.0f;
			invalidate();
		}
		// Sau 1.5 giây không touch thì set alpha xuống 0.7
		handler.postDelayed(idleAlphaRunnable, 1500);
	}
	
	/**
	 * Gọi khi ACTION_DOWN để reset alpha về 1.0
	 */
	public void onTouchDown() {
		// Hủy runnable nếu đang chờ
		handler.removeCallbacks(idleAlphaRunnable);
		// Reset alpha về 1.0 khi touch lại
		if (idleAlpha != 1.0f) {
			idleAlpha = 1.0f;
			invalidate();
		}
	}
}

