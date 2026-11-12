package dev.gurk.opencarta.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ui.components.OpenCartaBrandLogo
import dev.gurk.opencarta.ui.theme.CartaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class CryptoWallet(
    val symbol: String,
    val name: String,
    val address: String
)

private val APP_WALLETS = listOf(
    CryptoWallet(
        symbol = "BTC",
        name = "Bitcoin",
        address = "bc1qytee2a0z2tg4k4zdtqj08zpellkrecrgdgg36z"
    ),
    CryptoWallet(
        symbol = "ETH",
        name = "Ethereum",
        address = "0x49372575383aEA56b78524C4FD0873DE1175e9be"
    ),
    CryptoWallet(
        symbol = "LTC",
        name = "Litecoin",
        address = "ltc1qk2pe4srtcjt2cnnp5fcfvmknwckjus9ge8v5p9"
    ),
    CryptoWallet(
        symbol = "XMR",
        name = "Monero",
        address = "43HUf74vj1XTo9utkkLoniUSqKfXzM2ST1msHfc22CrjRg1RyBspUoxdLa2rergVgMSXoep2wYrwsiR4F1jRGcok7jgGvUz"
    )
)

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/gurkenwerfer"
private const val GITHUB_URL = "https://github.com/stefexec"

@Composable
fun AboutScreen(
    onOpenPacketLog: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }
    val scope = rememberCoroutineScope()
    var copiedSymbol by remember { mutableStateOf<String?>(null) }

    fun copyAddress(wallet: CryptoWallet) {
        clipboardManager.setText(AnnotatedString(wallet.address))
        copiedSymbol = wallet.symbol
        Toast.makeText(context, "${wallet.symbol} address copied to clipboard!", Toast.LENGTH_SHORT).show()
        scope.launch {
            delay(2000)
            if (copiedSymbol == wallet.symbol) {
                copiedSymbol = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OpenCartaBrandLogo()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { uriHandler.openUri(GITHUB_URL) }
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = "© 2026 OpenCarta v$versionName • by ",
                color = CartaColors.TextSecondary,
                fontSize = 13.sp
            )
            Text(
                text = "Gurkenwerfer",
                color = CartaColors.Mint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        InfoCard(
            icon = Icons.Default.Bluetooth,
            title = "Reverse-Engineered BLE Protocol",
            body = "Direct GATT control over service 1011123e-8535-b5a0-7140-a304d2495cb7. Supports 0x99 live telemetry, 0xcc session & temp curves, 0x11 device settings, 0xe0–0xe4 LED presets, 0xd1 timeouts, and 0xac/0xaf renaming — with zero cloud account required."
        )

        // Support the Developer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SUPPORT THE DEVELOPER",
                color = CartaColors.TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            APP_WALLETS.forEach { wallet ->
                val isCopied = copiedSymbol == wallet.symbol
                val truncated = if (wallet.address.length > 20) {
                    "${wallet.address.take(18)}..."
                } else {
                    wallet.address
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CartaColors.SurfaceElevated)
                        .border(
                            1.dp,
                            if (isCopied) CartaColors.Mint else CartaColors.Border,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { copyAddress(wallet) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wallet.symbol,
                        color = CartaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp)
                    )
                    Text(
                        text = truncated,
                        color = if (isCopied) CartaColors.Mint else CartaColors.TextSecondary,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isCopied) CartaColors.Mint.copy(alpha = 0.2f) else Color(0xFF252A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = "Copy address",
                            tint = if (isCopied) CartaColors.Mint else CartaColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // Buy Me A Coffee Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFDD00))
                    .clickable { uriHandler.openUri(BUY_ME_A_COFFEE_URL) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x22000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalCafe,
                        contentDescription = "Coffee",
                        tint = Color.Black,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Alternatively, you can Buy\nMe a Coffee. Thank you! 💖",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenPacketLog)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CartaColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CartaColors.Mint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("BLE Packet Log & Raw Hex", color = CartaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Open live GATT debugger", color = CartaColors.TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CartaColors.Surface)
            .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = CartaColors.Mint, modifier = Modifier.size(20.dp))
            Text(title, color = CartaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(body, color = CartaColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
