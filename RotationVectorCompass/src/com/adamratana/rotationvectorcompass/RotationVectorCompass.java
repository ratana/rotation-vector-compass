package com.adamratana.rotationvectorcompass;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.adamratana.rotationvectorcompass.camera.CameraPreviewLayer;
import com.adamratana.rotationvectorcompass.camera.CameraUtil;
import com.adamratana.rotationvectorcompass.math.Matrix4;
import com.adamratana.rotationvectorcompass.rotation.MagAccelListener;
import com.adamratana.rotationvectorcompass.rotation.RotationUpdateDelegate;
import com.adamratana.rotationvectorcompass.rotation.RotationVectorListener;

/**
 * An Augmented Reality Compass using the Rotation Vector Sensor
 * 
 * This project is a proof of concept for various experiments involving the
 * Rotation Vector Sensor, Augmented Reality, overlaying these on top of a
 * Camera Preview, and using the Canvas API for drawing.
 * 
 * - Uses the Canvas API for drawing: Why the canvas API? Because the canvas is
 * easy to understand for most people with little to no graphics programming
 * experience and to those with no knowledge of OpenGL.
 * 
 * In addition we manually perform rotation and translation and vector
 * manipulation, which can serve as an introduction to these concepts.
 * 
 * - Uses Orthographic and Perspective Projections: As an academic display of
 * how both work, to compare and contrast, especially for those who many be
 * unfamiliar.
 * 
 * - open-source LibGDX for certain math classes Uses LibGDX for Matrix,
 * Quaternion, Vector3 classes, Vector4 derived from Vector3
 * 
 * - Uses the Rotation Vector virtual Sensor: This has been little-documented,
 * and the documentation might not make much sense to someone without 3d
 * graphics or OpenGL experience. We show it in use and in contrast to the
 * typical sensor fusion of Magnetometer / Accelerometer.
 * 
 * Some devices may not properly implement this, the galaxy tab 10.1 (version 1)
 * does not seem to have a consistent implementation.
 * 
 * - Has a basic, filtered Sensor Fusion implementation: Uses the Magnetometer /
 * Accelerometer to detect device rotation, with a simple filter to smooth the
 * values.
 * 
 * - Aims to provide an accurate representation of device orientation in 3d with
 * respect to North: A brute-force approach, but using typical API methods such
 * as SensorManager.getOrientation() do not provide acceptable values for all
 * positions
 * 
 * - Camera preview integration, with portrait mode support: Overlays the
 * compass on top of the camera preview Uses the camera reported field of view
 * information to properly apply perspective to the compass Supports the Camera
 * when the devices is held in Portrait mode, could find few working examples of
 * this Employing fixes for known device abberations in the wild. - some devices
 * report 0 for field of view - ZTE Blade crashes while calling
 * Camera.Parameters.getHorizontalViewAngle() - some devices when rotating the
 * camera display by 0 degrees do not behave correctly
 * 
 * - Supports Variable Device Orientation, Full Screen Allows the rotation of
 * the device, rather than locking to portrait or landscape Programatically
 * allows for locking of the current orientation Programatically allows for
 * toggling of full screen display
 * 
 * - Conditional support back to 2.1 Branching is applied for API calls which
 * are incompatible with 2.1, 2.2 - setting the camera display rotation is not
 * possible in 2.1 - getting the camera field of view is also not possible in
 * 2.1 - Rotation Vector not available prior to 2.3
 * 
 * @author Adam
 * 
 */
public class RotationVectorCompass extends Activity implements RotationUpdateDelegate, CameraPreviewLayer.FOVUpdateDelegate {
	private static final boolean FLAG_DEBUG = true;
	private static final String TAG = "RotationVectorCompass";

	private static final float MAX_FOV = 175f;
	private static final float MIN_FOV = 10f;
	private static final float DEFAULT_FOV = 40f;

	private static final float MAX_ORTHO_SCALE = 7.0f;
	private static final float DEFAULT_SCALE = 1.0f;
	private static final float MIN_ORTHO_SCALE = 0.25f;

	private OverlayView mOverlayView;
	private Matrix4 mRotationMatrix = new Matrix4();
	private SensorManager mSensorManager;

	private MagAccelListener mMagAccel;
	private RotationVectorListener mRotationVector;

	private int mDisplayRotation;
	private boolean mUseRotationVector = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD);

	private float mFOV = DEFAULT_FOV;
	private float mOrthographicScale = DEFAULT_SCALE;
	private SeekBar mScaleAndFOVSlider = null;

	/** camera stuff **/
	private boolean mCameraOn = false;
	private LinearLayout mCameraViewHolder;
	private LayoutParams mCameraViewLayoutParams;
	private CameraPreviewLayer mPreview;
	private Camera mCamera;
	private int mNumCameras;
	private int mDefaultCameraID = -1; // The first rear facing camera
	private boolean mFullScreen = false;
	private boolean mPerspectiveProjection = true;
	private boolean mOrientationLocked = false;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setup window decorations
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		final Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		mDisplayRotation = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) ? display.getRotation() : display.getOrientation();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

			log("display: " + display.getWidth() + " x " + display.getHeight());
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			mFullScreen = true;
			mOrientationLocked = true;
		}

		if (mFullScreen) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		setContentView(R.layout.main);

		// sensor listeners
		mMagAccel = new MagAccelListener(this);
		mRotationVector = new RotationVectorListener(this);
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		// overlay view
		mOverlayView = (OverlayView) findViewById(R.id.rotateView2);
		mOverlayView.setFOVView((TextView) findViewById(R.id.fov_text));
		mOverlayView.setPerspectiveProjection(mPerspectiveProjection);
		mOverlayView.setFOV(DEFAULT_FOV);

		// seekbar which controls fov (perspective) and scale (orthographic)
		mScaleAndFOVSlider = (SeekBar) findViewById(R.id.fov_bar);
		mScaleAndFOVSlider.setProgress(mPerspectiveProjection ? (int) ((mFOV - MIN_FOV) / (MAX_FOV - MIN_FOV) * 1000f) : (int) ((mOrthographicScale - MIN_ORTHO_SCALE) / (MAX_ORTHO_SCALE - MIN_ORTHO_SCALE) * 1000f));

		// Orientation Lock Toggle
		final ToggleButton lockOrientationToggleButton = (ToggleButton) findViewById(R.id.lock_toggle);
		lockOrientationToggleButton.setChecked(mOrientationLocked);
		if (mOrientationLocked) {
			lockOrientationToggleButton.setVisibility(View.GONE);
		}
		lockOrientationToggleButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mOrientationLocked = isChecked;
				if (mOrientationLocked) {
					if (display.getWidth() > display.getHeight()) {
						setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					} else {
						setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					}
					Toast.makeText(getApplicationContext(), "Locking orientation, this can't be undone", Toast.LENGTH_SHORT).show();
					lockOrientationToggleButton.setVisibility(View.GONE);
				}
			}
		});

		// Full Screen Toggle
		final ToggleButton fullScreenToggleButton = (ToggleButton) findViewById(R.id.fs_toggle);
		if (mFullScreen) {
			fullScreenToggleButton.setVisibility(View.GONE);
		} else {
			fullScreenToggleButton.setChecked(mFullScreen);
			fullScreenToggleButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					mFullScreen = isChecked;
					Toast.makeText(getApplicationContext(), mFullScreen ? "Full Screen ON" : "Full Screen OFF", Toast.LENGTH_SHORT).show();

					if (mFullScreen) {
						// go full screen
						WindowManager.LayoutParams attrs = getWindow().getAttributes();
						attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
						getWindow().setAttributes(attrs);
					} else {
						// go non-full screen
						WindowManager.LayoutParams attrs = getWindow().getAttributes();
						attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
						getWindow().setAttributes(attrs);
					}
				}

			});
		}

		// Camera Preview Toggle
		final ToggleButton cameraToggleButton = (ToggleButton) findViewById(R.id.cam_toggle);
		cameraToggleButton.setChecked(mCameraOn);
		cameraToggleButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mCameraOn = isChecked;
				if (mCameraOn) {
					startCamera();
				} else {
					stopCamera();
				}
			}

		});

		// Rotation Vector / Mag Accel Toggle
		final ToggleButton rotationVectorToggleButton = (ToggleButton) findViewById(R.id.sensor_toggle);
		rotationVectorToggleButton.setChecked(mUseRotationVector);
		rotationVectorToggleButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					mUseRotationVector = isChecked;
					Toast.makeText(getApplicationContext(), mUseRotationVector ? "Rotation Vector Sensor" : "Magnetometer/Accelerometer Sensors", Toast.LENGTH_SHORT).show();
					applySensors(mUseRotationVector);
				}
			}

		});

		// Perspective / Orthographic Toggle
		final ToggleButton perspectiveProjectionToggleButton = (ToggleButton) findViewById(R.id.perspective_toggle);
		perspectiveProjectionToggleButton.setChecked(mPerspectiveProjection);
		perspectiveProjectionToggleButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mPerspectiveProjection = isChecked;
				mOverlayView.setPerspectiveProjection(mPerspectiveProjection);
				mScaleAndFOVSlider.setProgress(mPerspectiveProjection ? (int) ((mFOV - MIN_FOV) / (MAX_FOV - MIN_FOV) * 1000f) : (int) ((mOrthographicScale - MIN_ORTHO_SCALE) / (MAX_ORTHO_SCALE - MIN_ORTHO_SCALE) * 1000f));
				Toast.makeText(getApplicationContext(), mPerspectiveProjection ? "Perspective Projection" : "Orthographic Projection", Toast.LENGTH_SHORT).show();
			}
		});

		// Slider for adjusting Scale / FOV
		mScaleAndFOVSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (mPerspectiveProjection) {
					mFOV = MIN_FOV + progress / 1000.0f * (MAX_FOV - MIN_FOV);
					mOverlayView.setFOV(mFOV);
				} else {
					mOrthographicScale = MIN_ORTHO_SCALE + progress / 1000.0f * (MAX_ORTHO_SCALE - MIN_ORTHO_SCALE);
					mOverlayView.setOrthograhicScale(mOrthographicScale);
				}
			}
		});

		// Camera Setup
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			// Find the total number of cameras available
			mNumCameras = Camera.getNumberOfCameras();

			// Find the ID of the back-facing camera
			CameraInfo cameraInfo = new CameraInfo();
			for (int i = 0; i < mNumCameras; i++) {
				Camera.getCameraInfo(i, cameraInfo);
				if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
					mDefaultCameraID = i;
				}
			}
		} else {
			// to work on non-froyo
			mDefaultCameraID = 1;
		}
		mCameraViewHolder = (LinearLayout) findViewById(R.id.cameraViewHolder);
	}

	private void applySensors(boolean useRV) {
		mSensorManager.unregisterListener(mMagAccel);
		mSensorManager.unregisterListener(mRotationVector);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && useRV) {
			mSensorManager.registerListener(mRotationVector, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME);
		} else {
			mSensorManager.registerListener(mMagAccel, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
			mSensorManager.registerListener(mMagAccel, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME);
		}
	}

	private void updateViews() {
		mOverlayView.rotateView(mRotationMatrix);
	}

	private void stopCamera() {
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.release();
			mCamera = null;

			mCameraViewHolder.removeView(mPreview);
			mPreview = null;
		}
	}

	private void startCamera() {
		if (mCamera != null) {
			return;
		}
		/** camera stuff **/
		// Open the default i.e. the first rear facing camera.
		try {
			if (mDefaultCameraID != -1) {
				// new
				mCameraViewLayoutParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				mPreview = new CameraPreviewLayer(this, this);

				// create preview and attach to view -- will be removed when
				// pausing, will be recreated when resuming
				PackageManager pm = getPackageManager();
				if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
					mPreview.setCanAutoFocus(true);
				} else {
					mPreview.setCanAutoFocus(false);
				}

				mCameraViewHolder.addView(mPreview, mCameraViewLayoutParams);

				mCamera = Camera.open();

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
					CameraUtil.setCameraDisplayOrientation(this, mDefaultCameraID, mCamera);
				}
				mPreview.setCamera(mCamera);
			}
		} catch (Exception e) {
			log("startCamera(): exception caught: " + e);
		}
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

	// RotationUpdateDelegate methods
	@Override
	public void onRotationUpdate(float[] newMatrix) {
		// remap matrix values according to display rotation, as in
		// SensorManager documentation.
		switch (mDisplayRotation) {
		case Surface.ROTATION_0:
		case Surface.ROTATION_180:
			break;
		case Surface.ROTATION_90:
			SensorManager.remapCoordinateSystem(newMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, newMatrix);
			break;
		case Surface.ROTATION_270:
			SensorManager.remapCoordinateSystem(newMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, newMatrix);
			break;
		default:
			break;
		}
		mRotationMatrix.set(newMatrix);
		updateViews();
	}

	// CameraPreviewLayer.FOVDelegate methods
	@Override
	public void onFOVUpdate(int width, int height, float fovH, float fovV, float adjustedFOVH, float adjustedFOVV) {
		log("adjusted FOV for " + width + " x " + height + " h: " + fovH + " v: " + fovV + " adjH: " + adjustedFOVH + " adjV: " + adjustedFOVV);
		if (width > height) {
			mFOV = adjustedFOVH;
		} else {
			mFOV = adjustedFOVV;
		}
		mOverlayView.setFOV(mFOV);
		if (mPerspectiveProjection) {
			mScaleAndFOVSlider.setProgress((int) ((mFOV - MIN_FOV) / (MAX_FOV - MIN_FOV) * 1000f));
		}
	}

	// Other Activity life-cycle methods
	@Override
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(mMagAccel);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && mUseRotationVector) {
			mSensorManager.unregisterListener(mRotationVector);
		}
		if (mCameraOn) {
			stopCamera();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		applySensors(mUseRotationVector);
		if (mCameraOn) {
			startCamera();
		}
	}
}