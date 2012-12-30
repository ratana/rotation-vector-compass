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

	// /**
	// * decodes yuv into rgb
	// * @param rgb
	// * @param yuv420sp
	// * @param width
	// * @param height
	// */
	// public static void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width,
	// int height) {
	// final int frameSize = width * height;
	//
	// for (int j = 0, yp = 0; j < height; j++) {
	// int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
	// for (int i = 0; i < width; i++, yp++) {
	// int y = (0xff & ((int) yuv420sp[yp])) - 16;
	// if (y < 0)
	// y = 0;
	// if ((i & 1) == 0) {
	// v = (0xff & yuv420sp[uvp++]) - 128;
	// u = (0xff & yuv420sp[uvp++]) - 128;
	// }
	//
	// int y1192 = 1192 * y;
	// int r = (y1192 + 1634 * v);
	// int g = (y1192 - 833 * v - 400 * u);
	// int b = (y1192 + 2066 * u);
	//
	// if (r < 0)
	// r = 0;
	// else if (r > 262143)
	// r = 262143;
	// if (g < 0)
	// g = 0;
	// else if (g > 262143)
	// g = 262143;
	// if (b < 0)
	// b = 0;
	// else if (b > 262143)
	// b = 262143;
	//
	// rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
	// | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
	// }
	// }
	// }
	//
	// /**
	// * rotate an array from width/height to newWidth/newHeight - ie, rotate an
	// image by say 90 degrees
	// * @param inArray
	// * @param width
	// * @param height
	// * @param newWidth
	// * @param newHeight
	// * @param rotation
	// * @param outArray
	// */
	// public static void rotateArray(int[] inArray, int width, int height, int
	// newWidth, int newHeight, int rotation, int[] outArray) {
	// if (rotation == 0) {
	// return;
	// }
	// // width and height must either be the same, or swapped, but nothing
	// else.
	// if ((
	// (width == newWidth && height == newHeight)
	// ||
	// (width == newHeight && height == newWidth)
	// )) {
	// if (rotation == 90) {
	// int[] newPixels = new int[newWidth * newHeight];
	// for (int r = 0; r < height; r++) {
	// for (int c = 0; c < width; c++) {
	// int origIndex = r * width + c;
	// int newR = c;
	// int newC = height-r-1;
	// int newIndex = newR * newWidth + newC;
	// newPixels[newIndex] = inArray[origIndex];
	// }
	// }
	// // copy to out
	// System.arraycopy(newPixels, 0, outArray, 0, newPixels.length);
	// newPixels = null;
	// }
	// }
	// }
}
