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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

@Composable
fun ThermostatControl(
    currentTemp: Float,
    setTemp: Float,
    unit: TempUnit,
    onSetTempChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Round setTemp to nearest 0.5 to keep the dial stable
    val roundedSetTemp = remember(setTemp) { (round(setTemp * 2) / 2f) }
    var tempValue by remember(roundedSetTemp) { mutableStateOf(roundedSetTemp) }
    val animatedTemp by animateFloatAsState(targetValue = tempValue, label = "tempAnimation")

    val minTemp = 0f
    val maxTemp = 30f

    Box(
        modifier = modifier.size(250.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            val touchX = change.position.x - size.width / 2
                            val touchY = change.position.y - size.height / 2
                            val angle = atan2(touchY, touchX)
                            var normalizedAngle = (angle * 180 / PI).toFloat() + 90f
                            if (normalizedAngle < 0) normalizedAngle += 360f
                            
                            val rawTemp = minTemp + (normalizedAngle / 360f) * (maxTemp - minTemp)
                            // Round to nearest 0.5 step for smoother but controlled selection
                            val steppedTemp = (round(rawTemp * 2) / 2f).coerceIn(minTemp, maxTemp)
                            tempValue = steppedTemp
                        },
                        onDragEnd = {
                            onSetTempChanged(tempValue)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 20.dp.toPx()

            drawCircle(
                color = Color.LightGray.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 15.dp.toPx())
            )

            val sweepAngle = ((tempValue - minTemp) / (maxTemp - minTemp)) * 360f
            drawArc(
                color = Color(0xFFEF5350),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
            )

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
                text = "${"%.1f".format(animatedTemp.toUnit(unit))}${unit.symbol()}",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFEF5350)
            )
            Text(
                text = "Set",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Current: ${"%.1f".format(currentTemp.toUnit(unit))}${unit.symbol()}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    }
}
