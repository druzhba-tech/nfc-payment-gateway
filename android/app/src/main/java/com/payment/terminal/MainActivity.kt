package com.payment.terminal

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payment.terminal.service.NfcPaymentHceService
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ScreenState {
    INPUT_AMOUNT,
    WAITING_NFC,
    SUCCESS
}

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var nfcAdapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    private var hceComponentName: ComponentName? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация NFC HCE для устранения конфликтов
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            cardEmulation = CardEmulation.getInstance(nfcAdapter)
            hceComponentName = ComponentName(this, NfcPaymentHceService::class.java)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B0F19),
                    surface = Color(0xFF161F30),
                    primary = Color(0xFF00D2FF),
                    onPrimary = Color(0xFF001F29),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                TerminalApp(client)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Устанавливаем приоритет нашего HCE сервиса перед системными тегами
        try {
            val adapter = nfcAdapter
            val comp = hceComponentName
            if (adapter != null && adapter.isEnabled && comp != null) {
                cardEmulation?.setPreferredService(this, comp)
                Log.d("MainActivity", "NFC preferred service successfully activated")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Could not set preferred service", e)
        }
    }

    override fun onPause() {
        super.onPause()
        // Освобождаем приоритет при сворачивании приложения
        try {
            val adapter = nfcAdapter
            if (adapter != null && adapter.isEnabled) {
                cardEmulation?.unsetPreferredService(this)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Could not unset preferred service", e)
        }
    }
}

@Composable
fun TerminalApp(client: OkHttpClient) {
    var screenState by remember { mutableStateOf(ScreenState.INPUT_AMOUNT) }
    var amountInput by remember { mutableStateOf("50") }
    var orderId by remember { mutableStateOf("ORD-1001") }
    var serverHost by remember { mutableStateOf("druzhba-tech.github.io/nfc-payment-gateway") }
    var paidBank by remember { mutableStateOf("Alif Mobi") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (screenState) {
            ScreenState.INPUT_AMOUNT -> {
                InputAmountScreen(
                    amount = amountInput,
                    serverHost = serverHost,
                    onServerHostChange = { serverHost = it },
                    onDigitClick = { digit ->
                        if (amountInput == "0") amountInput = digit
                        else if (amountInput.length < 7) amountInput += digit
                    },
                    onClearClick = { amountInput = "0" },
                    onBackspaceClick = {
                        if (amountInput.length > 1) amountInput = amountInput.dropLast(1)
                        else amountInput = "0"
                    },
                    onConfirm = {
                        val num = amountInput.toDoubleOrNull() ?: 10.0
                        orderId = "ORD-" + (100000..999999).random()
                        
                        // Ссылка на рабочий GitHub Pages шлюз с параметрами
                        val url = if (serverHost.startsWith("http")) {
                            "$serverHost/?order=$orderId&amount=$num"
                        } else {
                            "https://$serverHost/?order=$orderId&amount=$num"
                        }
                        
                        // Активируем платежную ссылку в NFC HCE сервисе
                        NfcPaymentHceService.activePaymentUrl = url
                        screenState = ScreenState.WAITING_NFC
                    }
                )
            }

            ScreenState.WAITING_NFC -> {
                WaitingNfcScreen(
                    amount = amountInput,
                    orderId = orderId,
                    paymentUrl = NfcPaymentHceService.activePaymentUrl,
                    onSimulateSuccess = {
                        paidBank = "Alif Mobi"
                        screenState = ScreenState.SUCCESS
                    },
                    onCancel = {
                        screenState = ScreenState.INPUT_AMOUNT
                    }
                )
            }

            ScreenState.SUCCESS -> {
                SuccessScreen(
                    amount = amountInput,
                    orderId = orderId,
                    bank = paidBank,
                    onNewOrder = {
                        amountInput = "0"
                        screenState = ScreenState.INPUT_AMOUNT
                    }
                )
            }
        }
    }
}

@Composable
fun InputAmountScreen(
    amount: String,
    serverHost: String,
    onServerHostChange: (String) -> Unit,
    onDigitClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirm: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NFC ТЕРМИНАЛ РТ (+992 92 882 6696)",
                color = Color(0xFF00D2FF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF94A3B8))
            }
        }

        if (showSettings) {
            OutlinedTextField(
                value = serverHost,
                onValueChange = onServerHostChange,
                label = { Text("Адрес шлюза оплаты", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Дисплей суммы
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Сумма к получению", color = Color(0xFF94A3B8), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = amount,
                        color = Color(0xFF00D2FF),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TJS",
                        color = Color(0xFF94A3B8),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Цифровая клавиатура
        val keypad = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        for (row in keypad) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (key in row) {
                    Button(
                        onClick = {
                            when (key) {
                                "C" -> onClearClick()
                                "⌫" -> onBackspaceClick()
                                else -> onDigitClick(key)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (key == "C" || key == "⌫") Color(0xFF26334D) else Color(0xFF1A2235)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        Text(
                            text = key,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (key == "C") Color(0xFFEF4444) else Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка подтверждения
        Button(
            onClick = onConfirm,
            enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00D2FF),
                contentColor = Color(0xFF001F29)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Nfc, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "ПРИНЯТЬ ОПЛАТУ (NFC)", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun WaitingNfcScreen(
    amount: String,
    orderId: String,
    paymentUrl: String,
    onSimulateSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Анимированный NFC круг
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00D2FF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Nfc,
                    contentDescription = "NFC",
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(text = "Приложите телефон", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = "Поднесите смартфон покупателя к задней крышке терминала",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$amount TJS",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00D2FF)
            )
            Text(text = "Номер заказа: $orderId", fontSize = 12.sp, color = Color(0xFF64748B))

            Spacer(modifier = Modifier.height(20.dp))

            // Кнопка быстрой проверки (тест)
            Button(
                onClick = onSimulateSuccess,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚡ Симулировать оплату (Тест)", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отменить")
            }
        }
    }
}

@Composable
fun SuccessScreen(
    amount: String,
    orderId: String,
    bank: String,
    onNewOrder: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(text = "ОПЛАЧЕНО!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF22C55E))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "$amount TJS", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(text = "Банк: $bank", fontSize = 14.sp, color = Color(0xFF94A3B8))
            Text(text = "Заказ: $orderId", fontSize = 12.sp, color = Color(0xFF64748B))

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onNewOrder,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF), contentColor = Color(0xFF001F29)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("НОВЫЙ ПЛАТЕЖ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
