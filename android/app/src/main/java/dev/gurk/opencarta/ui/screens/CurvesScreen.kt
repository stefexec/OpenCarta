package dev.gurk.opencarta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ble.CartaState
import dev.gurk.opencarta.data.*
import dev.gurk.opencarta.ui.components.CurveStudioCard
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun CurvesScreen(
    state: CartaState,
    allCurves: List<TempCurve>,
    activeCurve: TempCurve,
    runnerState: CurveRunnerState,
    onSelectCurve: (String) -> Unit,
    onSetInterpMode: (CurveInterpMode) -> Unit,
    onUpdateCurvePoint: (Int, Int?, Int?) -> Unit,
    onDragCurvePoint: (Int, Int?, Int) -> Unit,
    onAddCurvePoint: () -> Unit,
    onRemoveCurvePoint: (Int) -> Unit,
    onGenerateCurve: (Int, Int, Int, CurveGenShape) -> Unit,
    onSaveCustomCurve: (String) -> Unit,
    onRenameCustomCurve: (String) -> Unit,
    onDeleteCustomCurve: () -> Unit,
    onStartCurve: () -> Unit,
    onStopCurve: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Curves",
                color = CartaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                state.isSessionActive -> CartaColors.Danger
                                state.isConnected -> CartaColors.Mint
                                else -> CartaColors.TextMuted
                            }
                        )
                )
                Text(
                    text = if (state.isConnected) "${state.currentTemp}${state.unitSymbol}" else "Offline",
                    color = CartaColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CurveStudioCard(
            allCurves = allCurves,
            activeCurve = activeCurve,
            runnerState = runnerState,
            isFahrenheit = state.isFahrenheit,
            isConnected = state.isConnected,
            hasAtomizer = state.hasAtomizer,
            onSelectCurve = onSelectCurve,
            onSetInterpMode = onSetInterpMode,
            onUpdatePoint = onUpdateCurvePoint,
            onDragPoint = onDragCurvePoint,
            onAddPoint = onAddCurvePoint,
            onRemovePoint = onRemoveCurvePoint,
            onGenerateCurve = onGenerateCurve,
            onSaveCustom = onSaveCustomCurve,
            onRenameCustom = onRenameCustomCurve,
            onDeleteCustom = onDeleteCustomCurve,
            onStartCurve = onStartCurve,
            onStopCurve = onStopCurve
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

