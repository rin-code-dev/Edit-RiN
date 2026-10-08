package com.hikariatelier.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Choreographer

/**
 * Feeds device orientation and motion data to the preview WebView.
 * Only registers sensor listeners while the preview is active and sensors are enabled.
 */
internal class PreviewSensors(
    context: Context,
    private val onUpdate: (alpha: Float, beta: Float, gamma: Float, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) -> Unit
) : SensorEventListener, Choreographer.FrameCallback {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelerationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    @Volatile private var latestAlpha = 0f
    @Volatile private var latestBeta = 0f
    @Volatile private var latestGamma = 0f

    @Volatile private var latestAx = 0f
    @Volatile private var latestAy = 0f
    @Volatile private var latestAz = 0f

    @Volatile private var latestGx = 0f
    @Volatile private var latestGy = 0f
    @Volatile private var latestGz = 0f

    @Volatile private var hasNewData = false
    private var isStarted = false
    private var isEnabled = false
    private var choreographerScheduled = false
    private var registered = false

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        updateRegistration()
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        updateRegistration()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        updateRegistration()
    }

    fun destroy() {
        isStarted = false
        isEnabled = false
        updateRegistration()
    }

    private fun updateRegistration() {
        val shouldListen = isStarted && isEnabled && sensorManager != null
        if (shouldListen) {
            if (registered) return
            val rotationRegistered = rotationVectorSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            } == true
            val gravityRegistered = accelerometerSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            } == true
            val accelerationRegistered = linearAccelerationSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            } == true
            registered = rotationRegistered || gravityRegistered || accelerationRegistered
            if (!registered) return
            if (!choreographerScheduled) {
                choreographerScheduled = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        } else {
            if (registered) sensorManager?.unregisterListener(this)
            registered = false
            hasNewData = false
            if (choreographerScheduled) {
                choreographerScheduled = false
                Choreographer.getInstance().removeFrameCallback(this)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                // orientationAngles[0]: azimuth in radians (-PI to PI)
                // orientationAngles[1]: pitch in radians (-PI to PI)
                // orientationAngles[2]: roll in radians (-PI/2 to PI/2)
                var alpha = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (alpha < 0f) alpha += 360f
                // Android pitch is negative when top is tilted up towards user; W3C beta is positive.
                val beta = -Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val gamma = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                latestAlpha = alpha
                latestBeta = beta
                latestGamma = gamma
                hasNewData = true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                latestGx = event.values[0]
                latestGy = event.values[1]
                latestGz = event.values[2]
                if (linearAccelerationSensor == null) {
                    // Fallback when linear acceleration sensor is not available
                    latestAx = event.values[0]
                    latestAy = event.values[1]
                    latestAz = event.values[2]
                }
                hasNewData = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                latestAx = event.values[0]
                latestAy = event.values[1]
                latestAz = event.values[2]
                hasNewData = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun doFrame(frameTimeNanos: Long) {
        if (!choreographerScheduled) return
        if (hasNewData) {
            hasNewData = false
            onUpdate(
                latestAlpha, latestBeta, latestGamma,
                latestAx, latestAy, latestAz,
                latestGx, latestGy, latestGz
            )
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}
