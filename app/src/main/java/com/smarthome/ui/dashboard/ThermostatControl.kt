package com.smarthome.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ThermostatControl(
    currentTemp: Float,
    targetTemp: Float,
    onTargetTempChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var tempValue by remember { mutableStateOf(targetTemp) }
    val animatedTemp by animateFloatAsState(targetValue = tempValue)

    Box(
        modifier = modifier.size(250.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val touchX = change.position.x - size.width / 2
                        val touchY = change.position.y - size.height / 2
                        val angle = atan2(touchY, touchX)
                        // Convert angle to temp range (e.g., 15 - 30 degrees)
                        var normalizedAngle = (angle * 180 / PI).toFloat() + 90f
                        if (normalizedAngle < 0) normalizedAngle += 360f
                        
                        // Map 0-360 to 15-30
                        val newTemp = 15f + (normalizedAngle / 360f) * 15f
                        tempValue = newTemp.coerceIn(15f, 30f)
                        onTargetTempChanged(tempValue)
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 20.dp.toPx()

            // Draw Background Track
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 15.dp.toPx())
            )

            // Draw Progress Arc
            val sweepAngle = ((tempValue - 15f) / 15f) * 360f
            drawArc(
                color = Color(0xFFEF5350), // Warm Red
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Knob
            val knobAngle = sweepAngle - 90f
            val knobRadius = radius
            val knobX = center.x + knobRadius * cos(knobAngle * PI / 180).toFloat()
            val knobY = center.y + knobRadius * sin(knobAngle * PI / 180).toFloat()

            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(knobX, knobY)
            )
            drawCircle(
                color = Color(0xFFEF5350),
                radius = 8.dp.toPx(),
                center = Offset(knobX, knobY)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animatedTemp.toInt()}°C",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFEF5350)
            )
            Text(
                text = "Target",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Current: ${currentTemp}°C",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    }
}
