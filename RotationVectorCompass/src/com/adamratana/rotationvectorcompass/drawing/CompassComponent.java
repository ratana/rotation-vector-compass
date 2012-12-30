package com.adamratana.rotationvectorcompass.drawing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.util.FloatMath;

import com.adamratana.rotationvectorcompass.math.Vector3;

/**
 * A Spherical compass representation, with the camera viewpoint inside the
 * sphere
 * 
 * @author Adam
 * 
 */
public class CompassComponent implements DrawingComponent {
	private static final int POINTS_PER_SEGMENT = 72;
	private static final int NUM_SEGMENTS = 11;
	private static final int NUM_POINTS = POINTS_PER_SEGMENT * NUM_SEGMENTS;
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0f);
	private static boolean SHOW_TEXT = true;
	private static final String COMPASS_N = "N", COMPASS_S = "S", COMPASS_E = "E", COMPASS_W = "W";

	private List<Vector3> mPublishedPoints = new ArrayList<Vector3>(NUM_POINTS + 1);
	private List<Vector3> mVertices = new ArrayList<Vector3>(NUM_POINTS + 1);

	private LineBatcher mLineBatcher = new LineBatcher(NUM_POINTS);

	private int mTextColor = Color.WHITE;

	// the compass degree text markers
	private static final String[] COMPASS_TEXT = new String[25];

	static {
		for (int i = 0; i < 25; i++) {
			COMPASS_TEXT[i] = " " + ((i * 15) + "\u00B0");
		}
	}

	public CompassComponent() {
		for (int i = 0; i < NUM_POINTS; i++) {
			mVertices.add(new Vector3());
			mPublishedPoints.add(new Vector3());
		}
	}

	@Override
	public void draw(Canvas canvas, float drawRadius, float textRotation, int xRange, int yRange, Paint paint, Paint textPaint) {
		paint.setStrokeWidth(2);

		Vector3 fp = null, pp = null;
		int pointsSeen = 0;
		boolean fpSeen = false, anyDrawn = false, horizonDrawn = false;

		// Horizontal Lines
		for (int i = 0; i < NUM_SEGMENTS; i++) {
			fpSeen = false;
			fp = null;
			pointsSeen = 0;
			for (int j = 0; j < POINTS_PER_SEGMENT; j++) {
				Vector3 v = mPublishedPoints.get(i * POINTS_PER_SEGMENT + j);
				if (v.z > 0) {
					if (j == 0) {
						fp = v;
						fpSeen = true;
					}
					if (pointsSeen > 0) {
						mLineBatcher.addLine(v.x, v.y, pp.x, pp.y);
						anyDrawn = true;
						if (j == POINTS_PER_SEGMENT - 1 && fpSeen) {
							mLineBatcher.addLine(fp.x, fp.y, v.x, v.y);
						}
					}
					pp = v;
					++pointsSeen;
				} else {
					pointsSeen = 0;
				}
			}
		}

		// Vertical Lines
		for (int i = 0; i < (POINTS_PER_SEGMENT / 3); i++) {
			pointsSeen = 0;
			fp = null;
			fpSeen = false;
			for (int j = 0; j < NUM_SEGMENTS; j++) {
				Vector3 v = mPublishedPoints.get(j * POINTS_PER_SEGMENT + i * 3);
				if (v.z > 0) {
					if (j == 0) {
						fp = v;
						fpSeen = true;
					}
					if (pointsSeen > 0) {
						mLineBatcher.addLine(v.x, v.y, pp.x, pp.y);
						anyDrawn = true;
						if (j == POINTS_PER_SEGMENT - 1 && fpSeen) {
							mLineBatcher.addLine(fp.x, fp.y, v.x, v.y);
						}
					}
					pp = v;
					++pointsSeen;
				} else {
					pointsSeen = 0;
				}
			}
		}

		// Render lines if any visible
		if (anyDrawn) {
			paint.setColor(Color.rgb(55, 181, 229));
			paint.setStyle(Style.STROKE);
			mLineBatcher.drawLines(canvas, paint);
		}

		fp = null;
		fpSeen = false;
		pointsSeen = 0;
		// Vertical Line representing North
		for (int j = 0; j < NUM_SEGMENTS; j++) {
			Vector3 v = mPublishedPoints.get(j * POINTS_PER_SEGMENT);
			if (v.z > 0) {
				if (j == 0) {
					fp = v;
					fpSeen = true;
				}
				if (pointsSeen > 0) {
					mLineBatcher.addLine(v.x, v.y, pp.x, pp.y);
					anyDrawn = true;
					if (j == POINTS_PER_SEGMENT - 1 && fpSeen) {
						mLineBatcher.addLine(fp.x, fp.y, v.x, v.y);
					}
				}
				pp = v;
				++pointsSeen;
			} else {
				pointsSeen = 0;
			}
		}

		if (anyDrawn) {
			paint.setColor(Color.RED);
			paint.setStyle(Style.STROKE);
			mLineBatcher.drawLines(canvas, paint);
		}

		// horizon line
		fp = null;
		fpSeen = false;
		pointsSeen = 0;
		for (int j = 0; j < POINTS_PER_SEGMENT; j++) {
			Vector3 v = mPublishedPoints.get(5 * POINTS_PER_SEGMENT + j);
			if (v.z > 0) {
				if (j == 0) {
					fp = v;
					fpSeen = true;
				}
				if (pointsSeen > 0) {
					mLineBatcher.addLine(v.x, v.y, pp.x, pp.y);
					horizonDrawn = true;
					if (j == POINTS_PER_SEGMENT - 1 && fpSeen) {
						mLineBatcher.addLine(fp.x, fp.y, v.x, v.y);
					}
				}
				pp = v;
				++pointsSeen;
			} else {
				pointsSeen = 0;
			}
		}

		if (horizonDrawn) {
			paint.setColor(Color.RED);
			paint.setStyle(Style.STROKE);
			mLineBatcher.drawLines(canvas, paint);
		}

		// the compass text - degree markers, NSWE
		if (SHOW_TEXT) {
			textPaint.setTextSize(20);
			textPaint.setColor(mTextColor);
			textPaint.setTextAlign(Align.CENTER);

			for (int j = 0; j < POINTS_PER_SEGMENT; j++) {
				int angle = (int) (360.0 / POINTS_PER_SEGMENT * j);

				for (int k = 0; k < 11; k += 5) {
					int idx = k * POINTS_PER_SEGMENT + j;
					int angleIncrement = (k == 5 ? 15 : 30);
					Vector3 v = mPublishedPoints.get(idx);
					if (angle % angleIncrement == 0 && v.z > 0) {
						if (textRotation != 0) {
							canvas.save();
							canvas.rotate(textRotation, v.x, v.y);
						}

						// draw circle and angle designation
						switch (angle) {
						case 0:
							textPaint.setTextSize(textPaint.getTextSize() * 3);
							int oldTextColor = textPaint.getColor();
							textPaint.setColor(Color.RED);
							drawTextStrokeFill(canvas, COMPASS_N, v.x, v.y, textPaint);
							textPaint.setColor(oldTextColor);

							textPaint.setTextSize(textPaint.getTextSize() / 3);
							break;
						case 90:
							textPaint.setTextSize(textPaint.getTextSize() * 3);
							drawTextStrokeFill(canvas, COMPASS_E, v.x, v.y, textPaint);
							textPaint.setTextSize(textPaint.getTextSize() / 3);
							break;
						case 180:
							textPaint.setTextSize(textPaint.getTextSize() * 3);
							drawTextStrokeFill(canvas, COMPASS_S, v.x, v.y, textPaint);
							textPaint.setTextSize(textPaint.getTextSize() / 3);
							break;
						case 270:
							textPaint.setTextSize(textPaint.getTextSize() * 3);
							drawTextStrokeFill(canvas, COMPASS_W, v.x, v.y, textPaint);
							textPaint.setTextSize(textPaint.getTextSize() / 3);
							break;
						case 360:
							break;
						default:
							textPaint.setTextSize(textPaint.getTextSize() * 1.3f);
							drawTextStrokeFill(canvas, COMPASS_TEXT[angle / 15], v.x, v.y, textPaint);
							textPaint.setTextSize(textPaint.getTextSize() / 1.3f);
							break;
						}

						if (textRotation != 0) {
							canvas.restore();
						}
					}
				}
			}
		}
	}

	/**
	 * reset all points to their original positions on the sphere
	 */
	public void resetPoints() {
		for (int j = 0; j < NUM_SEGMENTS; j++) {
			int idx = j - 5;
			float jCosVal = FloatMath.cos(DEGREES_TO_RADIANS * (float) (idx * 15));
			float jCosValInv = FloatMath.cos(DEGREES_TO_RADIANS * (float) (90 - idx * 15));
			for (int i = 0; i < POINTS_PER_SEGMENT; i++) {
				float sinVal = FloatMath.sin(DEGREES_TO_RADIANS * (float) (i * (360 / POINTS_PER_SEGMENT)));
				float cosVal = FloatMath.cos(DEGREES_TO_RADIANS * (float) (i * (360 / POINTS_PER_SEGMENT)));
				mVertices.get(i + (POINTS_PER_SEGMENT * j)).set(sinVal * jCosVal * 1, -cosVal * jCosVal * 1, jCosValInv * 1);
			}
		}
	}

	/**
	 * draws text surrounded by a black stroke
	 * 
	 * @param canvas
	 * @param text
	 * @param x
	 * @param y
	 * @param p
	 *            - paint for drawing the text
	 */
	public static void drawTextStrokeFill(Canvas canvas, String text, float x, float y, Paint p) {
		int tempColor = p.getColor();
		p.setColor(Color.BLACK);
		p.setStyle(Style.FILL_AND_STROKE);
		p.setStrokeWidth(p.getStrokeWidth() + 3);
		canvas.drawText(text, x, y, p);
		p.setStrokeWidth(p.getStrokeWidth() - 3);
		p.setColor(tempColor);
		p.setStyle(Style.FILL);
		canvas.drawText(text, x, y, p);
	}

	@Override
	public void prepareDraw() {
		// publish the points to be drawn, since any rotation/modification can
		// happen on a separate thread.
		final int size = mVertices.size();
		for (int i = 0; i < size; i++) {
			mPublishedPoints.get(i).set(mVertices.get(i));
		}
	}

	@Override
	public void addTo(Collection<Vector3> transformationCollection) {
		transformationCollection.addAll(mVertices);
	}
}