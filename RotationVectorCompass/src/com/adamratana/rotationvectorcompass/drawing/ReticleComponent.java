package com.adamratana.rotationvectorcompass.drawing;

import java.util.Collection;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;

import com.adamratana.rotationvectorcompass.math.Vector3;

/**
 * A target reticle, static, in the middle of the display
 * 
 * @author Adam
 * 
 */
public class ReticleComponent implements DrawingComponent {
	@Override
	public void draw(Canvas canvas, float drawRadius, float textRotation, int xRange, int yRange, Paint paint, Paint textPaint) {
		paint.setStrokeWidth(2);
		paint.setStyle(Style.STROKE);
		paint.setColor(Color.RED);
		canvas.drawCircle(0.5f * xRange, 0.5f * yRange, 0.1f * drawRadius, paint);
		canvas.drawLine(0.5f * xRange, 0.5f * yRange - 0.15f * drawRadius, 0.5f * xRange, 0.5f * yRange + 0.15f * drawRadius, paint);
		canvas.drawLine(0.5f * xRange - 0.15f * drawRadius, 0.5f * yRange, 0.5f * xRange + 0.15f * drawRadius, 0.5f * yRange, paint);
	}

	@Override
	public void addTo(Collection<Vector3> transformationCollection) {
	}

	@Override
	public void prepareDraw() {
	}
}