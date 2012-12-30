package com.adamratana.rotationvectorcompass;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.adamratana.rotationvectorcompass.drawing.CompassComponent;
import com.adamratana.rotationvectorcompass.drawing.DrawingComponent;
import com.adamratana.rotationvectorcompass.drawing.ReticleComponent;
import com.adamratana.rotationvectorcompass.math.Matrix4;
import com.adamratana.rotationvectorcompass.math.Vector3;
import com.adamratana.rotationvectorcompass.math.Vector4;

public class OverlayView extends View {
	/**
	 * for formatting orientation values, fov
	 */
	private static final DecimalFormat degreeFormat = new DecimalFormat("##0.0\u00B0");

	/**
	 * for formatting orthographic scale
	 */
	private static final DecimalFormat scaleFormat = new DecimalFormat("##0.0");

	/**
	 * for debugging, set to true;
	 */
	private static final boolean FLAG_DEBUG = true;

	/**
	 * for logging, fps counter
	 */
	private static final String TAG = "OverlayView";

	/**
	 * Lock used to mitigate between objects being rotated on a secondary thread
	 * (methods called by motion event listeners), and objects being displayed
	 * on the screen. we draw based on published values, and we lock while
	 * publishing those values, or drawing those published values, so values are
	 * not updated in mid-draw.
	 * 
	 */
	private Object mPublishingLock = new Object();

	/**
	 * a matrix to use for creating a perspective projection transform
	 */
	private Matrix4 mPerspectiveProjectionMatrix = new Matrix4();
	/**
	 * Matrix to use for creating an orthographic projection transform
	 */
	private Matrix4 mOrthographicProjectionMatrix = new Matrix4();
	/**
	 * Matrix used to transform components, using perspective, rotation, etc.
	 */
	private Matrix4 mModelViewMatrix = new Matrix4();

	/**
	 * Used to derive the orientation of the device
	 */
	private OrientationCalculator mOrientationCalculator = new OrientationCalculatorImpl();

	/**
	 * device orientation - bearing, pitch, roll
	 */
	float[] mDerivedDeviceOrientation = { 0, 0, 0 };

	/**
	 * the radius is based on the lessor of view width and height, divided by 2
	 */
	private float mDrawRadius = 0;

	/**
	 * the perspective projection's field of view
	 */
	private float mPerspectiveFOV = 60.0f;

	/**
	 * orthorgraphic zoom scale
	 */
	private float mOrthographicScale = 1.0f;

	/**
	 * aspect ratio of the view
	 */
	private float mViewAspectRatio;

	/**
	 * batch of vertices to rotate/translate/scale for all components
	 */
	private ArrayList<Vector3> mPerspectiveVertexBatch = new ArrayList<Vector3>();

	private ArrayList<Vector3> mOrthographicVertexBatch = new ArrayList<Vector3>();

	/**
	 * the 3d compass
	 */
	private CompassComponent mCompassComponent = new CompassComponent();

	/**
	 * the reticle
	 */
	private ReticleComponent mReticleComponent = new ReticleComponent();

	/**
	 * list of augmented reality items to display
	 */
	private List<DrawingComponent> mDisplayList = new ArrayList<DrawingComponent>();

	/**
	 * handle on the text view in which to output orientation information and
	 * field of view information
	 */
	private TextView mFOVView;

	/**
	 * how the display is rotated. will be Surface.ROTATION_0 to
	 * Surface.ROTATION_270
	 */
	private int mDisplayRotation = 0;

	private Vector4 vTemp = new Vector4();

	/**
	 * used for drawing our items with the canvas API
	 */
	private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

	/**
	 * sets the view in which to display the field of view and orientation
	 * 
	 * @param fovView
	 */
	public void setFOVView(TextView fovView) {
		mFOVView = fovView;
	}

	/**
	 * determines whether we are using a perspective projection or orthographic
	 */
	private boolean mPerspectiveProjection = true;

	/**
	 * when true, uses perspective, when false, is orthographic
	 * 
	 * @param usePerspectiveProjection
	 */
	public void setPerspectiveProjection(boolean usePerspectiveProjection) {
		mPerspectiveProjection = usePerspectiveProjection;
	}

	/**
	 * set the scale for the orthographic projection
	 * 
	 * @param degrees
	 */
	public void setOrthograhicScale(float scale) {
		mOrthographicScale = scale;
		updateOrientationDisplay();
	}

	/**
	 * set the FOV for the perspective projection
	 * 
	 * @param degrees
	 */
	public void setFOV(double degrees) {
		mPerspectiveFOV = (float) degrees;
		updatePerspectiveProjectionMatrix();
		updateOrientationDisplay();
	}

	public OverlayView(Context context) {
		super(context);
		init();
	}

	public OverlayView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public OverlayView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	/**
	 * update the display text for fov and device orientation
	 */
	private void updateOrientationDisplay() {
		if (mPerspectiveProjection) {
			mFOVView.setText(degreeFormat.format(mDerivedDeviceOrientation[0]) + "\n" + degreeFormat.format(mDerivedDeviceOrientation[1]) + "\n" + degreeFormat.format(mDerivedDeviceOrientation[2]) + "\n\nv fov\n" + degreeFormat.format(mPerspectiveFOV));
		} else {
			mFOVView.setText(degreeFormat.format(mDerivedDeviceOrientation[0]) + "\n" + degreeFormat.format(mDerivedDeviceOrientation[1]) + "\n" + degreeFormat.format(mDerivedDeviceOrientation[2]) + "\n\nscale\n" + scaleFormat.format(mOrthographicScale));
		}
	}

	/**
	 * determine the display rotation, initialize our display list components
	 */
	@SuppressLint("NewApi")
	private void init() {
		// determine rotation of the display, which determines how we rotate our
		// components.
		Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		mDisplayRotation = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) ? display.getRotation() : display.getOrientation();

		// establish our display list of components to draw
		mDisplayList.add(mCompassComponent);
		mDisplayList.add(mReticleComponent);

		// initialize our projection matricies
		mOrthographicProjectionMatrix.setToOrtho2D(0, 0, 1, -1);
		mPerspectiveProjectionMatrix.setToProjection(1 - mViewAspectRatio, 1000, mPerspectiveFOV, mViewAspectRatio);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		synchronized (mPublishingLock) {
			for (DrawingComponent drawingComponent : mDisplayList) {
				drawingComponent.draw(canvas, mDrawRadius, (int) -mDerivedDeviceOrientation[2], canvas.getWidth(), canvas.getHeight(), p, p);
			}
		}
	}

	/**
	 * With rotationMatrix, rotate the view components - we are assuming a fixed
	 * camera looking down the origin, and rotating the world around us.
	 * 
	 * @param rotationMatrix
	 */
	public void rotateView(Matrix4 rotationMatrix) {
		int width = getWidth();
		int height = getHeight();

		// Reset all components to their starting values, with each update we
		// calculate anew
		mPerspectiveVertexBatch.clear();
		mOrthographicVertexBatch.clear();
		mCompassComponent.resetPoints();

		for (DrawingComponent drawingComponent : mDisplayList) {
			drawingComponent.addTo(mPerspectiveProjection ? mPerspectiveVertexBatch : mOrthographicVertexBatch);
		}

		// convention, set our model view matrix to identity
		mModelViewMatrix.idt();

		// Translations for the perspective projection
		if (!mPerspectiveVertexBatch.isEmpty()) {
			mModelViewMatrix.mul(mPerspectiveProjectionMatrix).mul(rotationMatrix);
			// Projection matrix creates a perspective projection -
			// this maps a value into vector.w; x, y are divided by w to produce
			// perspective corrected x, y values.
			// the range of z is arbitrarily specified by us and depends on how
			// much resolution we want.
			//
			// We multiply by our rotation matrix. Matrix multiplication is done
			// in reverse order when represented this way. Perspective is
			// logically last.
			//
			// Then we scale and translate:
			// add 1, and mult by 0.5, width / height - this translates 0,0 to
			// 0.5, 0.5, moving the origin to the center of the view
			//
			// For 0, 90, 270, we make the assumption that our rotation matrix
			// is already taking this device position into account.
			// this is achieved by re-mapping the sensor coordinate systems in
			// these cases.
			switch (mDisplayRotation) {
			case Surface.ROTATION_0:
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				for (Vector3 v : mPerspectiveVertexBatch) {
					vTemp.set(v.x, -v.y, -v.z, 0);
					vTemp.mul(mModelViewMatrix);
					v.x = (vTemp.x / vTemp.w + 1.0f) * 0.5f * width;
					v.y = height - (vTemp.y / vTemp.w + 1.0f) * 0.5f * height;
					v.z = vTemp.z;
				}
				break;

			// For 180, we have to reflect x and y values
			// TODO: find a way to eliminate the need for this branch? I was not
			// able to successfully re-map the sensor coordinate systems to
			// accomplish this.
			case Surface.ROTATION_180:
				for (Vector3 v : mPerspectiveVertexBatch) {
					vTemp.set(v.x, -v.y, -v.z, 0);
					vTemp.mul(mPerspectiveProjectionMatrix);
					// reflect x and y axes...
					v.x = (-vTemp.x / vTemp.w + 1.0f) * 0.5f * width;
					v.y = height - (-vTemp.y / vTemp.w + 1.0f) * 0.5f * height;
					v.z = vTemp.z;
				}
				break;
			}
		}

		// Translations for the orthographic projection
		if (!mOrthographicVertexBatch.isEmpty()) {
			mModelViewMatrix.mul(mOrthographicProjectionMatrix).mul(rotationMatrix);
			// Then we scale and translate, with the origin at the center of the
			// device display
			// Here, orthographic scale is simply how "zoomed in" we are to the
			// orthographic view
			//
			// For 0, 90, 270, we make the assumption that our rotation matrix
			// is already taking this device position into account.
			// this is achieved by re-mapping the sensor coordinate systems in
			// these cases.
			switch (mDisplayRotation) {
			case Surface.ROTATION_0:
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				for (Vector3 v : mOrthographicVertexBatch) {
					vTemp.set(v.x, -v.y, -v.z, 0);
					vTemp.mul(mModelViewMatrix);
					v.x = (vTemp.x) * 0.5f * width * mOrthographicScale + width / 2;
					v.y = (vTemp.y) * 0.5f * width * mOrthographicScale + width / 2 + (height - width) / 2;
					v.z = vTemp.z * 0.5f * width * mOrthographicScale;
				}
				break;
			// For 180, we have to reflect x and y values
			// TODO: find a way to eliminate the need for this branch? I was not
			// able to successfully re-map the sensor coordinate systems to
			// accomplish this.
			case Surface.ROTATION_180:
				for (Vector3 v : mOrthographicVertexBatch) {
					vTemp.set(v.x, -v.y, -v.z, 0);
					vTemp.mul(mModelViewMatrix);
					// reflect x and y axes...
					v.x = (-vTemp.x) * 0.5f * width * mOrthographicScale + width / 2;
					v.y = (-vTemp.y) * 0.5f * width * mOrthographicScale + width / 2 + (height - width) / 2;
					v.z = vTemp.z * 0.5f * width * mOrthographicScale;
				}
				break;
			}
		}
		// prepare for drawing - ask components to prepare for drawing; publish
		// any modified values.
		synchronized (mPublishingLock) {
			for (DrawingComponent drawingComponent : mDisplayList) {
				drawingComponent.prepareDraw();
			}

			// derive the orientation of the device based on where the back of
			// the phone is pointing, from our rotated points
			// TODO: find some more efficient/correct implementation of doing
			// this.
			mOrientationCalculator.getOrientation(rotationMatrix, mDisplayRotation, mDerivedDeviceOrientation);
		}

		// update orientation display
		updateOrientationDisplay();

		// ask system for update of our view
		invalidate();
	}

	/**
	 * Calculate the smallest drawing radius and aspect ratio when this is
	 * called by the system during layout
	 */
	@Override
	public void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		mViewAspectRatio = (float) w / (float) h;
		mDrawRadius = Math.min(w, h) * 0.5f;
		updatePerspectiveProjectionMatrix();
		log("onSizeChanged(): " + w + " x " + h + " aspect: " + mViewAspectRatio);
	}

	/**
	 * Calculate the smallest drawing radius and aspect ratio when this is
	 * called by the system during layout
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
		mDrawRadius = Math.min(getMeasuredWidth(), getMeasuredHeight()) * 0.5f;
		mViewAspectRatio = (float) getMeasuredWidth() / (float) getMeasuredHeight();
		updatePerspectiveProjectionMatrix();
		log("onMeasure(): " + getMeasuredWidth() + " x " + getMeasuredHeight() + " aspect: " + mViewAspectRatio);
	}

	/**
	 * updates our perspective projection matrix with the current field of view
	 * and view aspect ratio
	 */
	private void updatePerspectiveProjectionMatrix() {
		mPerspectiveProjectionMatrix.setToProjection(1 - mViewAspectRatio, 1000, mPerspectiveFOV, mViewAspectRatio);
	}

	/**
	 * logging if debug enabled
	 * 
	 * @param msg
	 */
	private void log(String msg) {
		if (FLAG_DEBUG) {
			Log.e(TAG, msg);
		}
	}
}