package com.example.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class GyroscopeSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _currentRotationDegrees = MutableStateFlow(0f)
    val currentRotationDegrees: StateFlow<Float> = _currentRotationDegrees.asStateFlow()

    private val _hasHardwareGyro = MutableStateFlow(gyroscopeSensor != null || rotationVectorSensor != null)
    val hasHardwareGyro: StateFlow<Boolean> = _hasHardwareGyro.asStateFlow()

    private var lastTimestamp: Long = 0
    private var accumulatedZAngle: Float = 0f
    private var isListening = false

    private var initialHeading: Float? = null

    fun startListening() {
        if (isListening) return
        isListening = true
        lastTimestamp = 0
        accumulatedZAngle = 0f
        initialHeading = null
        _currentRotationDegrees.value = 0f

        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        sensorManager.unregisterListener(this)
    }

    fun resetAngle() {
        accumulatedZAngle = 0f
        initialHeading = null
        _currentRotationDegrees.value = 0f
    }

    fun addManualDegrees(deltaDegrees: Float) {
        accumulatedZAngle += deltaDegrees
        if (accumulatedZAngle < 0) accumulatedZAngle += 360f
        _currentRotationDegrees.value = accumulatedZAngle
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isListening) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // orientation[0] is azimuth / rotation around Z axis in radians (-pi to +pi)
            val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            
            if (initialHeading == null) {
                initialHeading = azimuthDegrees
            }
            
            val initial = initialHeading ?: azimuthDegrees
            var deltaHeading = azimuthDegrees - initial
            if (deltaHeading < 0) deltaHeading += 360f
            
            // Prefer rotation vector if smooth, fallback to integrated gyro if needed
            if (gyroscopeSensor == null) {
                accumulatedZAngle = deltaHeading
                _currentRotationDegrees.value = accumulatedZAngle
            }
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (lastTimestamp != 0L) {
                val dt = (event.timestamp - lastTimestamp) * 1.0f / 1_000_000_000.0f // seconds
                // Z-axis angular velocity in rad/s
                val omegaZ = event.values[2]
                
                // Integrate radians -> degrees
                val deltaDeg = Math.toDegrees((omegaZ * dt).toDouble()).toFloat()
                
                // Only consider positive clockwise rotation or absolute accumulated delta
                if (abs(deltaDeg) > 0.05f) {
                    accumulatedZAngle += abs(deltaDeg)
                    _currentRotationDegrees.value = accumulatedZAngle
                }
            }
            lastTimestamp = event.timestamp
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
