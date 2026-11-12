package dev.gurk.opencarta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.data.*
import dev.gurk.opencarta.ui.theme.CartaColors
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun CurveStudioCard(
    allCurves: List<TempCurve>,
    activeCurve: TempCurve,
    runnerState: CurveRunnerState,
    isFahrenheit: Boolean,
    isConnected: Boolean,
    onSelectCurve: (String) -> Unit,
    onSetInterpMode: (CurveInterpMode) -> Unit,
    onUpdatePoint: (Int, Int?, Int?) -> Unit,
    onDragPoint: (Int, Int?, Int) -> Unit,
    onAddPoint: () -> Unit,
    onRemovePoint: (Int) -> Unit,
    onGenerateCurve: (Int, Int, Int, CurveGenShape) -> Unit,
    onSaveCustom: (String) -> Unit,
    onRenameCustom: (String) -> Unit,
    onDeleteCustom: () -> Unit,
    onStartCurve: () -> Unit,
    onStopCurve: () -> Unit
) {
    val unitStr = if (isFahrenheit) "°F" else "°C"
    fun toUnit(c: Int) = if (isFahrenheit) CurveRepository.cToF(c) else c
    fun toC(u: Int) = if (isFahrenheit) CurveRepository.fToC(u) else u

    var showPresetMenu by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPointsEditor by remember { mutableStateOf(false) }

    var genStartText by remember(activeCurve.id, isFahrenheit) {
        mutableStateOf(toUnit(activeCurve.pointsC.firstOrNull()?.tempC ?: 215).toString())
    }
    var genEndText by remember(activeCurve.id, isFahrenheit) {
        mutableStateOf(toUnit(activeCurve.pointsC.lastOrNull()?.tempC ?: 235).toString())
    }
    var genDurText by remember(activeCurve.id) {
        mutableStateOf(activeCurve.durationSec.toString())
    }
    var selectedShape by remember { mutableStateOf(CurveGenShape.EXP) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CartaColors.Surface)
            .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Header + Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TEMP CURVE STUDIO",
                color = CartaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            val badgeText = when {
                !runnerState.running -> "IDLE"
                runnerState.phase == CurvePhase.PREHEAT -> "PRE-HEATING"
                else -> "RUNNING ${runnerState.elapsedSec}s/${runnerState.durationSec}s"
            }
            val badgeColor = when {
                !runnerState.running -> CartaColors.TextMuted
                runnerState.phase == CurvePhase.PREHEAT -> CartaColors.Danger
                else -> CartaColors.Mint
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CartaColors.SurfaceElevated)
                        .border(1.dp, CartaColors.Border, RoundedCornerShape(10.dp))
                        .clickable { showPresetMenu = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeCurve.name,
                        color = CartaColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CartaColors.TextSecondary)
                }
                DropdownMenu(
                    expanded = showPresetMenu,
                    onDismissRequest = { showPresetMenu = false },
                    containerColor = CartaColors.SurfaceElevated
                ) {
                    allCurves.forEach { curve ->
                        DropdownMenuItem(
                            text = { Text(curve.name, color = CartaColors.TextPrimary, fontSize = 13.sp) },
                            onClick = {
                                onSelectCurve(curve.id)
                                showPresetMenu = false
                            }
                        )
                    }
                }
            }

            if (activeCurve.isCustom) {
                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = CartaColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDeleteCustom, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CartaColors.Danger, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Touch-Draggable Curve Canvas
        CurveInteractiveCanvas(
            activeCurve = activeCurve,
            runnerState = runnerState,
            isFahrenheit = isFahrenheit,
            onDragPoint = onDragPoint
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = activeCurve.desc.ifEmpty { "Drag dots directly on the graph to shape your temperature curve." },
            color = CartaColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Interpolation mode + Edit Points toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurveInterpMode.entries.forEach { mode ->
                    val selected = activeCurve.mode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) CartaColors.Mint.copy(alpha = 0.2f) else CartaColors.SurfaceElevated)
                            .border(
                                1.dp,
                                if (selected) CartaColors.Mint else CartaColors.Border,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSetInterpMode(mode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) CartaColors.Mint else CartaColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            TextButton(
                onClick = { showPointsEditor = !showPointsEditor },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (showPointsEditor) "Hide Editor" else "Edit / Auto-Gen",
                    color = CartaColors.Mint,
                    fontSize = 12.sp
                )
            }
        }

        if (showPointsEditor) {
            Spacer(modifier = Modifier.height(8.dp))
            // Auto-Gen Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CartaColors.Background)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Auto-Generate Curve", color = CartaColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactNumField(genStartText, { genStartText = it }, "Start $unitStr", Modifier.weight(1f))
                    Text("→", color = CartaColors.TextSecondary)
                    CompactNumField(genEndText, { genEndText = it }, "End $unitStr", Modifier.weight(1f))
                    CompactNumField(genDurText, { genDurText = it }, "Sec", Modifier.weight(0.8f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CartaColors.SurfaceElevated)
                                .clickable { showShapeMenu = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedShape.label, color = CartaColors.TextPrimary, fontSize = 11.sp, maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, null, tint = CartaColors.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showShapeMenu,
                            onDismissRequest = { showShapeMenu = false },
                            containerColor = CartaColors.SurfaceElevated
                        ) {
                            CurveGenShape.entries.forEach { shape ->
                                DropdownMenuItem(
                                    text = { Text(shape.label, color = CartaColors.TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        selectedShape = shape
                                        showShapeMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val sU = genStartText.toIntOrNull() ?: toUnit(215)
                            val eU = genEndText.toIntOrNull() ?: toUnit(235)
                            val d = genDurText.toIntOrNull() ?: 55
                            onGenerateCurve(toC(sU), toC(eU), d, selectedShape)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Mint, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Generate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Points List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeCurve.pointsC.forEachIndexed { idx, pt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CartaColors.Background)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("#${idx + 1}", color = CartaColors.TextMuted, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallStepperButton("-") { onUpdatePoint(idx, pt.t - 2, null) }
                            Text("${pt.t}s", color = CartaColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                            SmallStepperButton("+") { onUpdatePoint(idx, pt.t + 2, null) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallStepperButton("-") { onUpdatePoint(idx, null, pt.tempC - 2) }
                            Text("${toUnit(pt.tempC)}$unitStr", color = CartaColors.Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(46.dp))
                            SmallStepperButton("+") { onUpdatePoint(idx, null, pt.tempC + 2) }
                        }
                        if (activeCurve.pointsC.size > 2) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = CartaColors.TextMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemovePoint(idx) }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAddPoint,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CartaColors.Border),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Add Point", color = CartaColors.TextPrimary, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, CartaColors.Border),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Custom", color = CartaColors.Mint, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Run / Stop Curve Session CTA
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStartCurve,
                enabled = isConnected && !runnerState.running,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CartaColors.Mint,
                    contentColor = Color.Black,
                    disabledContainerColor = CartaColors.SurfaceElevated,
                    disabledContentColor = CartaColors.TextMuted
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("RUN CURVE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (runnerState.running) {
                Button(
                    onClick = onStopCurve,
                    colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Danger, contentColor = Color.White),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(0.7f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showSaveDialog) {
        TextPromptDialog(
            title = "Save Custom Curve",
            initial = "${activeCurve.name} (Custom)",
            onConfirm = {
                onSaveCustom(it)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showRenameDialog) {
        TextPromptDialog(
            title = "Rename Custom Curve",
            initial = activeCurve.name,
            onConfirm = {
                onRenameCustom(it)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun CurveInteractiveCanvas(
    activeCurve: TempCurve,
    runnerState: CurveRunnerState,
    isFahrenheit: Boolean,
    onDragPoint: (Int, Int?, Int) -> Unit
) {
    val dur = activeCurve.durationSec.toFloat().coerceAtLeast(10f)
    val minT = if (isFahrenheit) 300f else 150f
    val maxT = if (isFahrenheit) 600f else 320f
    fun toUnit(c: Int) = if (isFahrenheit) CurveRepository.cToF(c).toFloat() else c.toFloat()
    fun toC(u: Float) = if (isFahrenheit) CurveRepository.fToC(u.roundToInt()) else u.roundToInt()

    var dragIdx by remember { mutableIntStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CartaColors.Background)
            .border(1.dp, CartaColors.Border, RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeCurve.id, activeCurve.pointsC.size, isFahrenheit) {
                    val padL = 38.dp.toPx()
                    val padR = 14.dp.toPx()
                    val padT = 14.dp.toPx()
                    val padB = 22.dp.toPx()
                    val pw = size.width - padL - padR
                    val ph = size.height - padT - padB

                    detectDragGestures(
                        onDragStart = { offset ->
                            var best = -1
                            var bestDist = 28.dp.toPx()
                            activeCurve.pointsC.forEachIndexed { idx, pt ->
                                val px = padL + (pt.t / dur) * pw
                                val py = padT + ph - ((toUnit(pt.tempC) - minT) / (maxT - minT)) * ph
                                val d = hypot(offset.x - px, offset.y - py)
                                if (d < bestDist) {
                                    bestDist = d
                                    best = idx
                                }
                            }
                            dragIdx = best
                        },
                        onDrag = { change, _ ->
                            if (dragIdx != -1) {
                                change.consume()
                                val pos = change.position
                                val fracY = (1f - (pos.y - padT) / ph).coerceIn(0f, 1f)
                                val tempU = minT + fracY * (maxT - minT)
                                val fracX = ((pos.x - padL) / pw).coerceIn(0f, 1f)
                                val newT = (fracX * dur).roundToInt()
                                onDragPoint(dragIdx, newT, toC(tempU))
                            }
                        },
                        onDragEnd = { dragIdx = -1 },
                        onDragCancel = { dragIdx = -1 }
                    )
                }
        ) {
            val padL = 38.dp.toPx()
            val padR = 14.dp.toPx()
            val padT = 14.dp.toPx()
            val padB = 22.dp.toPx()
            val pw = size.width - padL - padR
            val ph = size.height - padT - padB

            fun xOf(t: Float) = padL + (t.coerceIn(0f, dur) / dur) * pw
            fun yOf(temp: Float) = padT + ph - ((temp.coerceIn(minT, maxT) - minT) / (maxT - minT)) * ph

            // Grid + Y/X Axis labels
            val axisPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#6E767C")
                textSize = 9.5.sp.toPx()
                isAntiAlias = true
            }
            for (i in 0..4) {
                val temp = minT + (i / 4f) * (maxT - minT)
                val y = yOf(temp)
                drawLine(CartaColors.Border, Offset(padL, y), Offset(size.width - padR, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(
                    "${temp.roundToInt()}°",
                    4.dp.toPx(),
                    y + 3.dp.toPx(),
                    axisPaint
                )
            }
            val xStep = if (dur <= 60f) 15 else 30
            var tTick = 0
            while (tTick <= dur.toInt()) {
                val tx = xOf(tTick.toFloat())
                drawLine(CartaColors.Border.copy(alpha = 0.5f), Offset(tx, padT), Offset(tx, size.height - padB), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(
                    "${tTick}s",
                    (tx - 6.dp.toPx()).coerceAtLeast(padL),
                    size.height - 6.dp.toPx(),
                    axisPaint
                )
                tTick += xStep
            }

            // Target curve path (dashed blue/mint)
            val pts = activeCurve.pointsC.sortedBy { it.t }
            if (pts.size >= 2) {
                val targetPath = Path()
                for (t in 0..dur.toInt()) {
                    val tempC = evalLocal(pts, activeCurve.mode, t)
                    val x = xOf(t.toFloat())
                    val y = yOf(toUnit(tempC))
                    if (t == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                }
                drawPath(
                    path = targetPath,
                    color = CartaColors.Blue,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                    )
                )
            }

            // Live atomizer trace (solid red)
            if (runnerState.liveTrace.size > 1) {
                val tracePath = Path()
                runnerState.liveTrace.forEachIndexed { idx, tp ->
                    val x = xOf(tp.t.toFloat())
                    val y = yOf(tp.temp.toFloat())
                    if (idx == 0) tracePath.moveTo(x, y) else tracePath.lineTo(x, y)
                }
                drawPath(
                    path = tracePath,
                    color = CartaColors.Danger,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Playhead
            if (runnerState.running) {
                val px = xOf(runnerState.elapsedSec.toFloat())
                drawLine(CartaColors.Mint, Offset(px, padT), Offset(px, size.height - padB), strokeWidth = 2.dp.toPx())
            }

            // Breakpoint dots + per-point temperature labels & Y-axis ticks
            val ptLabelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#DCE3E8")
                textSize = 9.5.sp.toPx()
                isFakeBoldText = true
                isAntiAlias = true
            }
            val ptBgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#CC0D1114")
                style = android.graphics.Paint.Style.FILL
            }
            val unitChar = if (isFahrenheit) "°F" else "°C"

            activeCurve.pointsC.forEachIndexed { idx, pt ->
                val tempVal = toUnit(pt.tempC).roundToInt()
                val cx = xOf(pt.t.toFloat())
                val cy = yOf(tempVal.toFloat())
                val isActive = idx == dragIdx

                // Subtle tick mark on Y-axis at this point's exact temperature
                drawLine(
                    color = if (isActive) CartaColors.Mint else CartaColors.Blue.copy(alpha = 0.55f),
                    start = Offset(padL - 4.dp.toPx(), cy),
                    end = Offset(padL + 2.dp.toPx(), cy),
                    strokeWidth = if (isActive) 2.5.dp.toPx() else 1.5.dp.toPx()
                )

                drawCircle(
                    color = if (isActive) Color.White else CartaColors.Blue,
                    radius = if (isActive) 8.dp.toPx() else 5.5.dp.toPx(),
                    center = Offset(cx, cy)
                )
                if (isActive) {
                    drawCircle(
                        color = CartaColors.Mint,
                        radius = 10.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Point value pill label (shows temp, plus time when dragging)
                val label = if (isActive) "${tempVal}$unitChar • ${pt.t}s" else "${tempVal}°"
                val textW = ptLabelPaint.measureText(label)
                val padX = 4.dp.toPx()
                val boxH = 14.dp.toPx()
                val placeBelow = cy < padT + 22.dp.toPx()
                val labelY = if (placeBelow) cy + 20.dp.toPx() else cy - 10.dp.toPx()
                val labelX = (cx - textW / 2f).coerceIn(padL + 2.dp.toPx(), size.width - padR - textW - 2.dp.toPx())

                drawContext.canvas.nativeCanvas.drawRoundRect(
                    labelX - padX,
                    labelY - 10.dp.toPx(),
                    labelX + textW + padX,
                    labelY - 10.dp.toPx() + boxH,
                    4.dp.toPx(),
                    4.dp.toPx(),
                    ptBgPaint
                )
                drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, ptLabelPaint)
            }
        }
    }
}

private fun evalLocal(pts: List<CurvePoint>, mode: CurveInterpMode, elapsedSec: Int): Int {
    if (elapsedSec <= pts.first().t) return pts.first().tempC
    if (elapsedSec >= pts.last().t) return pts.last().tempC
    for (i in 0 until pts.lastIndex) {
        val a = pts[i]
        val b = pts[i + 1]
        if (elapsedSec in a.t..b.t) {
            if (mode == CurveInterpMode.STEP || b.t == a.t) return a.tempC
            val frac = (elapsedSec - a.t).toDouble() / (b.t - a.t).toDouble()
            return (a.tempC + frac * (b.tempC - a.tempC)).roundToInt()
        }
    }
    return pts.last().tempC
}

@Composable
private fun CompactNumField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) onValueChange(it) },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CartaColors.Mint,
            unfocusedBorderColor = CartaColors.Border,
            focusedTextColor = CartaColors.TextPrimary,
            unfocusedTextColor = CartaColors.TextPrimary
        )
    )
}

@Composable
private fun SmallStepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CartaColors.SurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = CartaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CartaColors.SurfaceElevated,
        title = { Text(title, color = CartaColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CartaColors.Mint,
                    unfocusedBorderColor = CartaColors.Border,
                    focusedTextColor = CartaColors.TextPrimary,
                    unfocusedTextColor = CartaColors.TextPrimary
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Mint, contentColor = Color.Black)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CartaColors.TextSecondary)
            }
        }
    )
}

