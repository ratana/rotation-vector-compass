package com.adamratana.rotationvectorcompass.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Camera;
import android.view.Surface;

public class CameraUtil {

	/**
	 * set the camera display orientation based on the activity's rotation
	 * 
	 * @param activity
	 * @param cameraId
	 * @param camera
	 */
	@SuppressLint("NewApi")
	public static void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera) {
		final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		final int rotDeg = getCameraRotationForSurfaceRotation(rotation, camera, cameraId);
		if (rotDeg != 0) {
			camera.setDisplayOrientation(rotDeg);
		}
	}

	/**
	 * gets the correct rotation of the camera for the surface rotation
	 * requested, adjust for front/back camera
	 * 
	 * @param surfaceRotation
	 * @param camera
	 * @param cameraID
	 * @return
	 */
	@SuppressLint("NewApi")
	public static int getCameraRotationForSurfaceRotation(int surfaceRotation, Camera camera, int cameraID) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraID, info);

		int degrees = 0;

		switch (surfaceRotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		return result;
	}
}
