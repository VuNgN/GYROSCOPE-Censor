package com.vungn.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Half.EPSILON
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vungn.sensor.ui.theme.SensorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var mSensor: Sensor? = null
    private val NS2S = 1.0f / 1000000000.0f
    private val deltaRotationVector = FloatArray(4) { 0f }
    private var timestamp: Float = 0f

    private val gravityX = MutableStateFlow(0f)
    private val gravityY = MutableStateFlow(0f)
    private val gravityZ = MutableStateFlow(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorSetup()
        lifecycleScope.launch {
            gravityX.collect {
                if (it > 50 || it < -50) gravityX.emit(0f)
            }
        }
        lifecycleScope.launch {
            gravityY.collect {
                if (it > 50 || it < -50) gravityY.emit(0f)
            }
        }
        setContent {
            val x by gravityX.collectAsState()
            val y by gravityY.collectAsState()
            val z by gravityZ.collectAsState()
            SensorTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "AT-LAB",
                                modifier = Modifier,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.logo_cse_thuyloi_400x400_fotor_bg_remover_2023090510938),
                            contentDescription = null,
                            modifier = Modifier
                                .graphicsLayer {
                                    this.rotationX = x
                                    this.rotationY = y
                                }
                                .shadow(elevation = 20.dp, clip = true, shape = CircleShape)
                        )
                    }
                }
            }
        }
    }

    private fun sensorSetup() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            val gravSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE)
            // Use the version 3 gravity sensor.
            mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.
        if (timestamp != 0f && event != null) {
            val dT = (event.timestamp - timestamp) * NS2S
            // Axis of the rotation sample, not normalized yet.
            var axisX: Float = event.values[0]
            var axisY: Float = event.values[1]
            var axisZ: Float = event.values[2]

            // Calculate the angular speed of the sample
            val omegaMagnitude: Float = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)

            // Normalize the rotation vector if it's big enough to get the axis
            // (that is, EPSILON should represent your maximum allowable margin of error)
            if (omegaMagnitude > EPSILON) {
                axisX /= omegaMagnitude
                axisY /= omegaMagnitude
                axisZ /= omegaMagnitude
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the timestep
            // We will convert this axis-angle representation of the delta rotation
            // into a quaternion before turning it into the rotation matrix.
            val thetaOverTwo: Float = omegaMagnitude * dT / 2.0f
            val sinThetaOverTwo: Float = sin(thetaOverTwo)
            val cosThetaOverTwo: Float = cos(thetaOverTwo)
            deltaRotationVector[0] = sinThetaOverTwo * axisX
            deltaRotationVector[1] = sinThetaOverTwo * axisY
            deltaRotationVector[2] = sinThetaOverTwo * axisZ
            deltaRotationVector[3] = cosThetaOverTwo
            lifecycleScope.launch {
                gravityX.emit(gravityX.value + (deltaRotationVector[0] * 180 / 3.14f))
                gravityY.emit(gravityY.value - (deltaRotationVector[1] * 180 / 3.14f))
                gravityZ.emit(gravityZ.value + (deltaRotationVector[2] * 180 / 3.14f))
            }
            Log.d(
                TAG,
                "X: ${deltaRotationVector[0]}\n Y: ${deltaRotationVector[1]}\n Z: ${deltaRotationVector[2]}\n W: ${deltaRotationVector[3]}"
            )
        }
        timestamp = event?.timestamp?.toFloat() ?: 0f
        val deltaRotationMatrix = FloatArray(9) { 0f }
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
        // User code should concatenate the delta rotation we computed with the current rotation
        // in order to get the updated rotation.
        // rotationCurrent = rotationCurrent * deltaRotationMatrix;
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged: ${sensor?.name}, $accuracy")
    }

    override fun onResume() {
        super.onResume()
        mSensor?.also { grav ->
            sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SensorTheme {
        Greeting("Android")
    }
}