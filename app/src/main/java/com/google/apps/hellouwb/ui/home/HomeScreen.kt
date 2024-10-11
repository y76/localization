/*
 *
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.apps.hellouwb.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NearMeDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.uwb.RangingMeasurement
import androidx.core.uwb.RangingPosition
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import com.google.apps.uwbranging.UwbEndpoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ENDPOINT_COLORS =
  arrayListOf(
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Cyan,
    Color.Magenta,
    Color.DarkGray,
    Color.Yellow
  )





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(uiState: HomeUiState, modifier: Modifier = Modifier) {
  val topAppBarState = rememberTopAppBarState()

  Scaffold(
    topBar = { HomeTopAppBar(isRanging = uiState.isRanging, topAppBarState = topAppBarState) },
    modifier = modifier
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
      Row(modifier = Modifier.padding(innerPadding)) {
        ConnectStatusBar(
          uiState.connectedEndpoints,
          uiState.disconnectedEndpoints
        )
      }
      Row { RangingPlot(uiState.connectedEndpoints) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
  isRanging: Boolean,
  modifier: Modifier = Modifier,
  topAppBarState: TopAppBarState = rememberTopAppBarState(),
  scrollBehavior: TopAppBarScrollBehavior? =
    TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState),
) {
  CenterAlignedTopAppBar(
    title = { Text("UWB Ranging") },
    actions = {
      val icon = if (isRanging) Icons.Filled.NearMe else Icons.Filled.NearMeDisabled
      val iconColor = if (isRanging) Color.Green else Color.DarkGray
      Image(
        imageVector = icon,
        colorFilter = ColorFilter.tint(iconColor),
        contentDescription = null
      )
    },
    scrollBehavior = scrollBehavior,
    modifier = modifier
  )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun RangingPlot(connectedEndpoints: List<ConnectedEndpoint>) {
  val textMeasurer = rememberTextMeasurer()

  Canvas(modifier = Modifier.fillMaxSize().background(color = Color.White)) {
    // Assume the canvas is 20 meters wide.
    val center = Offset(size.width / 2.0f, size.height / 2.0f)
    val scale = drawPolar(center)
    connectedEndpoints.forEachIndexed { index, endpoint ->
      endpoint.position.distance?.let { distance ->
        endpoint.position.azimuth?.let { azimuth ->
          val color = ENDPOINT_COLORS[index % ENDPOINT_COLORS.size]
          drawPosition(
            distance.value,
            azimuth.value,
            scale = scale,
            centerOffset = center,
            color = color
          )

          // Draw distance and azimuth text near the top
          val textOffset = Offset(20f + (index * 150), 20f)
          drawText(
            textMeasurer = textMeasurer,
            text = "EP${index + 1}\nDist: ${String.format("%.2f", distance.value)}m\nAz: ${String.format("%.2f", azimuth.value)}°",
            topLeft = textOffset,
            style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 12.sp)
          )
        }
      }
    }
  }
}

private fun DrawScope.drawPolar(centerOffset: Offset): Float {
  val scale = size.minDimension / 20.0f
  (1..10).forEach {
    drawCircle(
      center = centerOffset,
      color = Color.DarkGray,
      radius = it * scale,
      style = Stroke(2f)
    )
  }

  val angles = floatArrayOf(0f, 30f, 60f, 90f, 120f, 150f)
  angles.forEach {
    val rad = it * PI / 180
    val start =
      centerOffset + Offset((scale * 10f * cos(rad)).toFloat(), (scale * 10f * sin(rad)).toFloat())
    val end =
      centerOffset - Offset((scale * 10f * cos(rad)).toFloat(), (scale * 10f * sin(rad)).toFloat())
    drawLine(
      color = Color.DarkGray,
      start = start,
      end = end,
      strokeWidth = 2f,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.0f, 5.0f), 10f)
    )
  }
  return scale
}

private fun DrawScope.drawPosition(
  distance: Float,
  azimuth: Float,
  scale: Float,
  centerOffset: Offset,
  color: Color,
) {
  val angle = azimuth * PI / 180
  val x = distance * sin(angle).toFloat()
  val y = distance * cos(angle).toFloat()
  drawCircle(
    center = centerOffset.plus(Offset(x * scale, -y * scale)),
    color = color,
    radius = 15.0f
  )
}

// Extend the existing HomeUiState interface
interface ExtendedHomeUiState : HomeUiState {
  val rangingInfoMap: Map<UwbEndpoint, RangingInfo>
}

@Composable
fun ConnectStatusBar(
  connectedEndpoints: List<ConnectedEndpoint>,
  disconnectedEndpoints: List<UwbEndpoint>,
  rangingInfoMap: Map<UwbEndpoint, RangingInfo> = emptyMap(),
  modifier: Modifier = Modifier
) {
  Column(modifier.fillMaxWidth().padding(8.dp)) {
    Text("Connected Endpoints:", style = MaterialTheme.typography.titleMedium)
    Text("Number of connected endpoints: ${connectedEndpoints.size}", color = Color.Gray)
    Text("Size of rangingInfoMap: ${rangingInfoMap.size}", color = Color.Gray)

    connectedEndpoints.forEachIndexed { index, connectedEndpoint ->
      Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ENDPOINT_COLORS[index % ENDPOINT_COLORS.size].copy(alpha = 0.1f))
      ) {
        Column(modifier = Modifier.padding(8.dp)) {
          Text(
            text = connectedEndpoint.endpoint.id.split("|")[0],
            color = ENDPOINT_COLORS[index % ENDPOINT_COLORS.size],
            style = MaterialTheme.typography.titleSmall
          )

          // Display information from ConnectedEndpoint
          Text("Distance: ${connectedEndpoint.position.distance?.value ?: "N/A"} m")
          Text("Azimuth: ${connectedEndpoint.position.azimuth?.value ?: "N/A"}°")
          Text("Elevation: ${connectedEndpoint.position.elevation?.value ?: "N/A"}°")
          Text("Timestamp: ${connectedEndpoint.position.elapsedRealtimeNanos} ns")

          if (rangingInfoMap.containsKey(connectedEndpoint.endpoint)) {
            val rangingInfo = rangingInfoMap[connectedEndpoint.endpoint]!!
            Text("Config ID: ${rangingInfo.configId}")
            Text("Address: ${rangingInfo.endpointAddress}")
            Text("Channel: ${rangingInfo.complexChannel.channel}")
            Text("Preamble Index: ${rangingInfo.complexChannel.preambleIndex}")
            Text("Session ID: ${rangingInfo.sessionId}")
            // Text("Session Scope: ${rangingInfo.sessionScope}")
          } else {
            Text("No additional ranging info available", color = Color.Red)
          }

          // Debug information
          Text("Endpoint ID: ${connectedEndpoint.endpoint.id}", color = Color.Gray, fontSize = 10.sp)
          Text("Endpoint hash: ${connectedEndpoint.endpoint.hashCode()}", color = Color.Gray, fontSize = 10.sp)
        }
      }
    }

    if (disconnectedEndpoints.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text("Disconnected Endpoints:", style = MaterialTheme.typography.titleMedium)
      disconnectedEndpoints.forEach { endpoint ->
        Text(text = endpoint.id, color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}
// Separate RangingInfo class
data class RangingInfo(
  val configId: Int,
  val endpointAddress: UwbAddress,
  val complexChannel: UwbComplexChannel,
  val sessionId: Int,
  //val sessionScope: UwbClientSessionScope
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(uiState: ExtendedHomeUiState, modifier: Modifier = Modifier) {
  val topAppBarState = rememberTopAppBarState()

  Scaffold(
    topBar = { HomeTopAppBar(isRanging = uiState.isRanging, topAppBarState = topAppBarState) },
    modifier = modifier
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
      Row(modifier = Modifier.padding(innerPadding)) {
        ConnectStatusBar(
          uiState.connectedEndpoints,
          uiState.disconnectedEndpoints,
          uiState.rangingInfoMap
        )
      }
      Row { RangingPlot(uiState.connectedEndpoints) }
    }
  }
}


@Preview
@Composable
fun PreviewHomeScreen(modifier: Modifier = Modifier) {
  val endpoint1 = UwbEndpoint("EP1", byteArrayOf())
  val endpoint2 = UwbEndpoint("EP2", byteArrayOf())

  val rangingInfo1 = RangingInfo(
    configId = 1,
    endpointAddress = UwbAddress(byteArrayOf(0x01, 0x02, 0x03, 0x04)),
    complexChannel = UwbComplexChannel(5, 9),
    sessionId = 1001
  )

  val rangingInfo2 = RangingInfo(
    configId = 2,
    endpointAddress = UwbAddress(byteArrayOf(0x05, 0x06, 0x07, 0x08)),
    complexChannel = UwbComplexChannel(9, 12),
    sessionId = 1002
  )

  HomeScreen(
    uiState = object : ExtendedHomeUiState {
      override val connectedEndpoints = listOf(
        ConnectedEndpoint(
          endpoint = endpoint1,
          position = RangingPosition(
            distance = RangingMeasurement(2.0f),
            azimuth = RangingMeasurement(10.0f),
            elevation = null,
            elapsedRealtimeNanos = 200L
          ),
          rangingInfo = rangingInfo1  // Add this line
        ),
        ConnectedEndpoint(
          endpoint = endpoint2,
          position = RangingPosition(
            distance = RangingMeasurement(10.0f),
            azimuth = RangingMeasurement(-10.0f),
            elevation = null,
            elapsedRealtimeNanos = 200L
          ),
          rangingInfo = rangingInfo2  // Add this line
        )
      )
      override val rangingInfoMap = mapOf(
        endpoint1 to rangingInfo1,
        endpoint2 to rangingInfo2
      )
      override val disconnectedEndpoints: List<UwbEndpoint> =
        listOf(UwbEndpoint("EP3", byteArrayOf()), UwbEndpoint("EP4", byteArrayOf()))
      override val isRanging = true
    },
    modifier = modifier
  )
}