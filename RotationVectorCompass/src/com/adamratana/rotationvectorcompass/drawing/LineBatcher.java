package com.adamratana.rotationvectorcompass.drawing;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Accumulates lines to draw in a batch for efficiency with hw-accelerated
 * canvas implementations
 * 
 * @author Adam
 * 
 */
public class LineBatcher {
	private final float[] mLineBuffer;
	private final int mMaxLines;
	private int mLineCount = 0;

	/**
	 * Intializes the buffer to hold maxLines
	 * 
	 * @param maxLines
	 */
	public LineBatcher(int maxLines) {
		mLineBuffer = new float[4 * maxLines];
		mMaxLines = maxLines;
	}

	/**
	 * Add a line to the current batch; ignores if maxLines already allocated
	 * 
	 * @param x1
	 *            - from X
	 * @param y1
	 *            - from Y
	 * @param x2
	 *            - to X
	 * @param y2
	 *            - to Y
	 */
	public void addLine(float x1, float y1, float x2, float y2) {
		if (mLineCount < mMaxLines) {
			mLineBuffer[mLineCount * 4] = x1;
			mLineBuffer[mLineCount * 4 + 1] = y1;
			mLineBuffer[mLineCount * 4 + 2] = x2;
			mLineBuffer[mLineCount * 4 + 3] = y2;
			++mLineCount;
		}
	}

	/**
	 * Draw the current batch of lines; clears batch when done
	 * 
	 * @param canvas
	 * @param paint
	 */
	public void drawLines(Canvas canvas, Paint paint) {
		if (mLineCount > 0) {
			canvas.drawLines(mLineBuffer, 0, mLineCount * 4, paint);
			mLineCount = 0;
		}
	}
}
