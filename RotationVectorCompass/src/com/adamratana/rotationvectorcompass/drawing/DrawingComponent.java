package com.adamratana.rotationvectorcompass.drawing;

import java.util.Collection;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.adamratana.rotationvectorcompass.math.Vector3;

public interface DrawingComponent {
	/**
	 * Draws the component using the supplied canvas, radius, rotation and
	 * paints.
	 * 
	 * @param canvas
	 * @param drawRadius
	 * @param textRotation
	 *            - in degrees
	 * @param xMax
	 *            - max X value considered valid
	 * @param yMax
	 *            - max Y value considered valid
	 * @param paint
	 * @param textPaint
	 *            - to be used for any text output
	 */
	public void draw(Canvas canvas, float drawRadius, float textRotation, int xMax, int yMax, Paint paint, Paint textPaint);

	/**
	 * Called before transforming vertices, add any vertices to be transformed
	 * to the vertex batch
	 * 
	 * @param vertexBatch
	 */
	public void addTo(Collection<Vector3> vertexBatch);

	/**
	 * Called immediately before draw
	 */
	public void prepareDraw();

}
