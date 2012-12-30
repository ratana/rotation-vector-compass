package com.adamratana.rotationvectorcompass.camera;

import java.io.IOException;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered
 * preview of the Camera to the surface. We need to center the SurfaceView
 * because not all devices have cameras that support preview sizes at the same
 * aspect ratio as the device's display.
 */
@SuppressLint({ "NewApi", "ViewConstructor" })
public class CameraPreviewLayer extends ViewGroup implements SurfaceHolder.Callback {
	private static final String TAG = "CameraPreviewLayer";
	private static final boolean FLAG_DEBUG = true;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private Size mPreviewSize;
	private List<Size> mSupportedPreviewSizes;
	private Camera mCamera;
	private int mCurrentSurfaceWidth = 0;
	private int mCurrentSurfaceHeight = 0;
	private boolean mForceLayout = false;
	private boolean mCanAutoFocus = false;
	private boolean mHasSurface = false;
	private static final int FOCUS_NOT_STARTED = 0;
	private static final int FOCUSING = 1;
	private FOVUpdateDelegate mFOVDelegate;
	private int mFocusState = FOCUS_NOT_STARTED;

	public void setCanAutoFocus(boolean b) {
		mCanAutoFocus = b;
	}

	private final Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			// done
			mFocusState = FOCUS_NOT_STARTED;
		}
	};

	public CameraPreviewLayer(Context context, FOVUpdateDelegate fovDelegate) {
		super(context);
		mFOVDelegate = fovDelegate;
		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	/**
	 * Updates the reported field of view based on the aspect ratio of the
	 * surface, calls back to the FOVDelegate
	 * 
	 * @param width
	 * @param height
	 */
	private void updateFOV(int width, int height) {
		if (mCamera != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			Camera.Parameters p = mCamera.getParameters();

			int zoom = 100;

			if (p.isZoomSupported()) {
				if (p.getZoomRatios() != null) {
					if (p.getZoomRatios().get(p.getZoom()) != null) {
						zoom = p.getZoomRatios().get(p.getZoom()).intValue();
						log("zoom: " + zoom);
					}
				}
			}
			if (zoom <= 0) {
				zoom = 100;
			}

			double aspect = (double) width / (double) height;
			if (width > height) {
				aspect = (double) height / (double) width;
			}

			float origHorizontalViewAngle = 0;
			float origVerticalViewAngle = 0;
			boolean correctlyReportedViewAngles = true;

			try {
				origHorizontalViewAngle = p.getHorizontalViewAngle();
			} catch (Exception e) {
				correctlyReportedViewAngles = false;
				log("error getting horizontal view angle: " + e);
			}
			try {
				origVerticalViewAngle = p.getVerticalViewAngle();
			} catch (Exception e) {
				correctlyReportedViewAngles = false;
				log("error getting vertical view angle: " + e);
			}

			log("camera reported view angle - h: " + origHorizontalViewAngle + " v: " + origVerticalViewAngle + " zoom: " + zoom + " correctly reported? " + correctlyReportedViewAngles);

			// fix for xperia reporting vfov as 0
			if (origVerticalViewAngle > 70 || origVerticalViewAngle < 10) {
				origHorizontalViewAngle = 51.2f;
				origVerticalViewAngle = 39.4f;
				correctlyReportedViewAngles = false;
			}

			log("final view angles - h: " + origHorizontalViewAngle + " v: " + origVerticalViewAngle + " zoom: " + zoom + " correctly reported? " + correctlyReportedViewAngles);

			double thetaV = Math.toRadians(origVerticalViewAngle);
			double thetaH = 2d * Math.atan(aspect * Math.tan(thetaV / 2));
			thetaV = 2d * Math.atan(100d * Math.tan(thetaV / 2d) / zoom);
			thetaH = 2d * Math.atan(100d * Math.tan(thetaH / 2d) / zoom);
			log("scaledWidth: " + width + " scaledHeight: " + height);
			log("adjusted FOV V: " + Math.toDegrees(thetaV));
			log("adjusted FOV H: " + Math.toDegrees(thetaH));

			mFOVDelegate.onFOVUpdate(width, height, origHorizontalViewAngle, origVerticalViewAngle, (float) Math.toDegrees(thetaH), (float) Math.toDegrees(thetaV));
		}
	}

	/**
	 * Handle screen touch events, in this case, focus when we touch, stop when
	 * we let go
	 * 
	 * @param ev
	 * @return
	 */
	public boolean handleTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			if (mHasSurface && mCamera != null && mCanAutoFocus && mFocusState != FOCUSING) {
				mFocusState = FOCUSING;
				// Log.e("SunSurveyor", "autofocusing!");
				try {
					mCamera.autoFocus(mAutoFocusCallback);
				} catch (RuntimeException re) {
					try {
						mFocusState = FOCUS_NOT_STARTED;
						mCamera.cancelAutoFocus();
					} catch (RuntimeException re2) {

					}
				}
			}
		} else if (ev.getAction() == MotionEvent.ACTION_UP) {
			if (mHasSurface && mCamera != null && mCanAutoFocus && mFocusState == FOCUSING) {
				mCamera.cancelAutoFocus();
				mFocusState = FOCUS_NOT_STARTED;
			}
		}
		return super.onTouchEvent(ev);
	}

	public void stop() {
		mHolder.removeCallback(this);
	}

	public void setCamera(Camera camera) {
		mCamera = camera;
		if (mCamera != null) {
			mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
			requestLayout();
		}
	}

	/**
	 * Given a camera, setup a surface to display the preview optimally, layout
	 * accordingly
	 * 
	 * @param camera
	 */
	public void setCameraHolderAndSurface(Camera camera) {
		mCamera = camera;
		mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
		mPreviewSize = null;
		mForceLayout = true;
		setHolder();
		setupSurface();
		requestLayout();
	}

	private void setHolder() {
		try {
			mCamera.setPreviewDisplay(mHolder);
		} catch (IOException ioe) {
			log("setHolder(): exception, can't set holder...");
		}
	}

	private void setupSurface() {
		if (mCamera == null || mPreviewSize == null) {
			mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, mCurrentSurfaceWidth, mCurrentSurfaceHeight);
		}

		requestLayout();

		// Now that the size is known, set up the camera parameters and begin
		// the preview.
		Camera.Parameters parameters = mCamera.getParameters();
		if (mPreviewSize != null) {
			log("setupSurface(): using preview size: " + mPreviewSize.width + " / " + mPreviewSize.height);
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
		}
		mCamera.setParameters(parameters);
		mCamera.startPreview();
	}

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.1;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	/**
	 * logging if debug enabled
	 * 
	 * @param msg
	 */
	private static void log(String msg) {
		if (FLAG_DEBUG) {
			Log.e(TAG, msg);
		}
	}

	// ViewGroup methods
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// We purposely disregard child measurements because act as a
		// wrapper to a SurfaceView that centers the camera preview instead
		// of stretching it.
		final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);

		if (mSupportedPreviewSizes != null) {
			if (width > height) {
				mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
			} else {
				mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, height, width);
			}
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		// When layout happens, find the right size for the camera preview,
		// based on the current width and height of the display (which can be
		// either portrait or landscape)
		log("onLayout()");
		if ((mForceLayout || changed) && getChildCount() > 0) {
			mForceLayout = false;
			final View child = getChildAt(0);

			final int h = b - t;
			final int w = r - l;

			if (w == 0 || h == 0) {
				log("onLayout: width or height is 0, exiting");
				return;
			}

			int width = w;
			int height = h;

			int previewWidth = width;
			int previewHeight = height;

			// accounting for rotated camera preview - the preview itself is
			// always w > h due to physical sensor
			if (mPreviewSize != null) {
				if (w < h) {
					previewWidth = mPreviewSize.height;
					previewHeight = mPreviewSize.width;
				} else {
					previewWidth = mPreviewSize.width;
					previewHeight = mPreviewSize.height;
				}
			}
			log("width: " + width + " height: " + height + " previewWidth: " + previewWidth + " previewHeight: " + previewHeight);
			int scaledWidth = 0;
			int scaledHeight = 0;

			// Center the child SurfaceView within the parent.
			if (width > height && width * previewHeight > height * previewWidth) {
				final int scaledChildWidth = previewWidth * height / previewHeight;
				int left = (width - scaledChildWidth) / 2;
				int right = (width + scaledChildWidth) / 2;
				int bot = 0;
				int top = height;
				child.layout(left, bot, right, top);
				scaledWidth = width;
				scaledHeight = height;

			} else {
				int scaledChildHeight = previewHeight * width / previewWidth;

				if (width < height) {
					scaledChildHeight = previewHeight * width / previewWidth;
				}

				int left = 0;
				int right = width;
				int bot = (height - scaledChildHeight) / 2;
				int top = (height + scaledChildHeight) / 2;

				if (scaledChildHeight > height) {
					if (width > height) {
						float ratio = (float) height / (float) scaledChildHeight;
						bot = 0;
						top = height;
						left = (int) ((width - (ratio * width)) / 2);
						right = width - left;
					}
				}

				child.layout(left, bot, right, top);
				scaledWidth = right - left;
				scaledHeight = top - bot;
			}
			updateFOV(scaledWidth, scaledHeight);
		}
	}

	// SurfaceHolder.Callback methods
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try {
			if (mCamera != null) {
				mCamera.setPreviewDisplay(holder);
			}
		} catch (IOException exception) {
		}

		// important for autofocus, we can't allow autofocus until the camera is
		// attached to the surface.
		mHasSurface = true;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		mCurrentSurfaceWidth = w;
		mCurrentSurfaceHeight = h;
		if (mCamera == null) {
			log("surfaceChanged(): camera is null, returning...");
			return;
		}
		setupSurface();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mHasSurface = false;
		// Surface will be destroyed when we return, so stop the preview.
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
		}
	}

	/**
	 * Delegate for receiving updates to the camera field of view Updates are
	 * necessary when the screen rotates, the vertical field of view which
	 * projections are based on will become the horizontal field of view of the
	 * camera sensor, for instance
	 * 
	 * @author Adam
	 * 
	 */
	public interface FOVUpdateDelegate {
		/**
		 * called when field of view changes
		 * 
		 * @param width
		 *            - width of the display surface
		 * @param height
		 *            - height of the display surface
		 * @param fovH
		 *            - original camera reported horizontal field of view
		 * @param fovV
		 *            - original camera reported vertical field of view
		 * @param adjustedFOVH
		 *            - adjusted horizontal field of view
		 * @param adjustedFOVV
		 *            - adjusted vertical field of view
		 */
		public void onFOVUpdate(int width, int height, float fovH, float fovV, float adjustedFOVH, float adjustedFOVV);
	}
}