# Rotation Vector Augmented Reality Compass

###An Augmented Reality Compass using the Rotation Vector Sensor:  
This project is a proof of concept for various experiments involving the Rotation Vector Sensor, Augmented Reality, overlaying these on top of a Camera Preview, and using the Canvas API for drawing.  

## Uses the Canvas API for drawing:

Why the canvas API?  Because the canvas is easy to understand for most people with little to no graphics programming experience and to those with no knowledge of OpenGL.

In addition we manually perform rotation and translation and vector manipulation, which can serve as an introduction to these concepts.

## Uses Orthographic and Perspective Projections:

As an academic display of how both work, to compare and contrast, especially for those who many be unfamiliar.

## Open-source [LibGDX](http://code.google.com/p/libgdx/) for certain math classes:
	
Uses [LibGDX](http://code.google.com/p/libgdx/) for Matrix, Quaternion, Vector3 classes, Vector4 derived from Vector3

## Uses the Rotation Vector virtual Sensor:
	
This has been little-documented, and the documentation might not make much sense to someone without 3d graphics or OpenGL experience.  We show it in use and in contrast to the typical sensor fusion of Magnetometer / Accelerometer.

Some devices may not properly implement this, the galaxy tab 10.1 (version 1) does not seem to have a consistent implementation.

## Has a basic, filtered Sensor Fusion implementation:

Uses the Magnetometer / Accelerometer to detect device rotation, with a simple filter to smooth the values.

## Aims to provide an accurate representation of device orientation in 3d with respect to North:

A brute-force approach, but using typical API methods such as SensorManager.getOrientation() do not provide acceptable values for all positions

## Camera preview integration, with portrait mode support:

Overlays the compass on top of the camera preview
Uses the camera reported field of view information to properly apply perspective to the compass
Supports the Camera when the devices is held in Portrait mode, could find few working examples of this
 	
## Employing fixes for known device abberations in the wild:

Some devices report 0 for field of view
ZTE Blade crashes while calling Camera.Parameters.getHorizontalViewAngle()
Some devices when rotating the camera display by 0 degrees do not behave correctly

## Supports Variable Device Orientation, Full Screen:

Allows the rotation of the device, rather than locking to portrait or landscape
Programatically allows for locking of the current orientation
Programatically allows for toggling of full screen display
 
## Conditional support back to 2.1:

Branching is applied for API calls which are incompatible with 2.1, 2.2:

- Setting the camera display rotation is not possible in 2.1
- getting the camera field of view is also not possible in 2.1
- Rotation Vector not available prior to 2.3
