package com.inavisys.samsunglifelocationdemo.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inavisys.samsunglifelocationdemo.R
import com.inavisys.location.core.common.formatDuration
import com.inavisys.location.core.tracking.UIData
import com.inavisys.location.core.tracking.model.*
import com.inavisys.location.samsunglife.CurrentPoint
import com.inavisys.location.samsunglife.SamsungLifeService
import kotlinx.coroutines.launch


enum class Mode(val label: String) {
    Image("이미지"),
    Data("데이터"),
    Section("구간"),
    Lap("랩"),
}

@Composable
fun SessionScreen() {
    val scope = rememberCoroutineScope()

    var currentSessionId  by remember { mutableStateOf<Long?>(null) }
    var selectedSessionId by remember { mutableStateOf<Long?>(null) }

    var sessionList by remember { mutableStateOf<List<String>>(emptyList()) }

    var uiData by remember { mutableStateOf(UIData()) }

    var currentPoint by remember { mutableStateOf<CurrentPoint?>(null) }

    var mode by remember { mutableStateOf(Mode.Image) }

    var routeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var trackingResult by remember { mutableStateOf<TrackingResult?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.all { it.value }) {
            scope.launch {
                val sessionId = SamsungLifeService.startTracking()
                if (sessionId != null) {
                    SamsungLifeService.startForegroundService(ForegroundServiceConfig(R.mipmap.ic_launcher, "포그라운드 서비스", "트래킹을 위한 포그라운드 서비스 실행중"))
                    uiData = UIData()
                    currentSessionId = sessionId
                    selectedSessionId = sessionId
                    sessionList = SamsungLifeService.getSessionIds()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        sessionList = SamsungLifeService.getSessionIds()
        SamsungLifeService.setCurrentPointListener { currentPoint = it }
        SamsungLifeService.setUiDataListener { uiData = it }
    }

    LaunchedEffect(selectedSessionId, currentSessionId, mode) {
        val id = selectedSessionId ?: run {
            routeBitmap = null
            trackingResult = null
            return@LaunchedEffect
        }

        when (mode) {
            Mode.Image -> {
                trackingResult = null
                routeBitmap = SamsungLifeService.getSessionRouteImage(id, RouteImageConfig())
            }
            else -> {
                routeBitmap = null
                trackingResult = if (id != currentSessionId) SamsungLifeService.getTrackingResult(id) else null }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    permissionLauncher.launch(buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
                    }.toTypedArray())
                },
                enabled = currentSessionId == null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }
            Button(
                onClick = {
                    scope.launch {
                        SamsungLifeService.stopTracking()
                        SamsungLifeService.stopForegroundService()
                        currentSessionId = null
                        sessionList = SamsungLifeService.getSessionIds()
                    }
                },
                enabled = currentSessionId != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }

        SectionHeader("세션 목록", "총 ${sessionList.size}개")

        if (sessionList.isEmpty()) {
            Text("(저장된 세션 없음)", fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(sessionList, key = { it }) { id ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSessionId = id.toLongOrNull() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .background(if (id.toLongOrNull() == selectedSessionId) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("#$id", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    HorizontalDivider()
                }
            }
        }

        SectionHeader("세션 상세", selectedSessionId?.let { "#$it" } ?: "선택 없음")
        DetailSessionTabBar(mode) { mode = it }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f)
                .padding(top = 4.dp)
        ) {
            val isCurrentSession = selectedSessionId != null && selectedSessionId == currentSessionId

            when (mode) {
                Mode.Image   -> ImageTab(routeBitmap)
                Mode.Data    -> DataTab(if (isCurrentSession) uiData else null, trackingResult)
                Mode.Section -> SectionTab(if (isCurrentSession) uiData else null, trackingResult)
                Mode.Lap     -> LapTab(trackingResult)
            }
        }

        SectionHeader("현재 포인트", if (currentPoint != null) "실시간" else "대기")

        Box(Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp)) {

            val p = currentPoint
            if (p == null) {
                Text("(측위 중이 아님)", fontSize = 11.sp)
            } else {
                Text(buildString {
                    append("lat=%.6f  lon=%.6f\n".format(p.latitude, p.longitude))
                    append("speed=%.1f m/s".format(p.speed ?: 0f))
                }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ImageTab(bitmap: Bitmap?) {
    if (bitmap == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "(이미지 없음 — 포인트가 2개 이상 필요)",
                fontSize = 12.sp
            )
        }
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun DataTab(uiData: UIData?, result: TrackingResult?) {
    if (uiData == null && result == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("(세션 선택 없음)", fontSize = 12.sp)
        }
        return
    }

    val distance   = uiData?.distance ?: (result?.totalDistanceKm?.times(1000.0) ?: 0.0)
    val avgPace    = uiData?.averagePace ?: result?.averagePaceSecPerKm ?: 0.0
    val movingPace = uiData?.averagePaceMove ?: result?.averageMovingPaceSecPerKm ?: 0.0
    val elevGain   = uiData?.elevationGainMeters ?: result?.elevationGainMeters ?: 0.0
    val elevLoss   = uiData?.elevationLossMeters ?: result?.elevationLossMeters ?: 0.0
    val kcal       = uiData?.kcal ?: result?.caloriesKcal ?: 0.0
    val stepCount  = uiData?.totalSteps ?: result?.totalSteps ?: 0L
    val cadenceMin = uiData?.cadence?.minSpm ?: result?.minCadenceSpm ?: 0
    val cadenceMax = uiData?.cadence?.maxSpm ?: result?.maxCadenceSpm ?: 0
    val cadenceAvg = uiData?.cadence?.averageSpm ?: result?.averageCadenceSpm ?: 0

    val movement   = uiData?.movement ?: MovementStats(
        runningDistanceMeters  = (result?.runningDistanceKm ?: 0.0) * 1000.0,
        walkingDistanceMeters  = (result?.walkingDistanceKm ?: 0.0) * 1000.0,
        runningDurationMillis  = result?.runningTimeMillis ?: 0L,
        walkingDurationMillis  = result?.walkingTimeMillis ?: 0L,
        idleDurationMillis     = result?.idleTimeMillis ?: 0L,
        excludedDurationMillis = result?.excludedTimeMillis ?: 0L,
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item { DataRow("거리",     "%.2fm".format(distance)) }
        item { DataRow("페이스",   "${fmtPace(avgPace)}/km") }
        item { DataRow("이동페이스", "${fmtPace(movingPace)}/km") }
        item { DataRow("칼로리",   "%.1f kcal".format(kcal)) }
        item { DataRow("걸음수",   "%,d 걸음".format(stepCount)) }
        item { DataRow("케이던스", "최소:$cadenceMin 최대:$cadenceMax 평균:$cadenceAvg spm") }
        item { DataRow("상승고도", "%.1fm".format(elevGain)) }
        item { DataRow("하강고도", "%.1fm".format(elevLoss)) }
        item { DataRow("러닝시간", "%s  (%.1fm)".format(fmtMs(movement.runningDurationMillis), movement.runningDistanceMeters)) }
        item { DataRow("걷기시간", "%s  (%.1fm)".format(fmtMs(movement.walkingDurationMillis), movement.walkingDistanceMeters)) }
        item { DataRow("대기시간", fmtMs(movement.idleDurationMillis)) }
    }
}

@Composable
private fun SectionTab(uiData: UIData?, result: TrackingResult?) {
    if (uiData == null && result == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("(세션 선택 없음)", fontSize = 12.sp)
        }
        return
    }

    val avgPace = uiData?.averagePace ?: result?.averagePaceSecPerKm ?: 0.0
    val bestPace= uiData?.bestPace ?: result?.bestPaceSecPerKm ?: 0.0

    val sectionElevation = uiData?.sectionElevations ?: result?.sectionElevations ?: emptyList()
    val sectionPaces = uiData?.sectionPaces ?: result?.sectionPaces ?: emptyList()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text = "평균: ${fmtPace(avgPace)}/km  최고: ${fmtPace(bestPace)}/km",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (sectionElevation.isEmpty()) {
            item { Text("(구간 데이터 없음)", fontSize = 11.sp) }
        } else {
            items(sectionElevation, key = { it.sectionIndex }) { sec ->
                val pace = sectionPaces.getOrNull(sec.sectionIndex)

                Column(Modifier.padding(vertical = 2.dp)) {
                    Text("구간 ${sec.sectionIndex + 1}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("  상승=%.1fm  하강=%.1fm  순변화=%.1fm".format(sec.elevationGainMeters, sec.elevationLossMeters, sec.netElevationChangeMeters), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    if (pace != null) {
                        val sign = if (pace.diffFromFirstSecPerKm >= 0) "+" else "-"
                        Text("  페이스=${fmtPace(pace.paceSecPerKm)}/km  첫구간대비=$sign${fmtPace(kotlin.math.abs(pace.diffFromFirstSecPerKm))}/km", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LapTab(result: TrackingResult?) {
    if (result == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "(종료된 세션만 조회 가능)",
                fontSize = 12.sp
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text = "랩 거리: %.2fkm  총 %d랩".format(result.lapDistanceKm, result.lapCount),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (result.laps.isEmpty()) {
            item {
                Text("(랩 데이터 없음)", fontSize = 11.sp)
            }
        } else {
            items(result.laps, key = { it.lapNumber }) { lap ->
                val partial = lap.distanceKm < result.lapDistanceKm - 0.001
                Text(
                    "${lap.lapNumber}랩${if (partial) " (부분)" else ""}  거리=%.2fkm  시간=%s  페이스=${fmtPace(lap.paceSecPerKm)}/km".format(lap.distanceKm, lap.timeMillis.formatDuration()),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = if (partial) Color.Gray else Color.Unspecified,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically)
    {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(trailing, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSessionTabBar(current: Mode, onChange: (Mode) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Mode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = mode == current,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size)
            ) {
                Text(mode.label)
            }
        }
    }
}

private fun fmtPace(sec: Double): String {
    if (sec <= 0.0)
        return "—"

    val s = sec.toInt()
    return "%d'%02d\"".format(s / 60, s % 60)
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}