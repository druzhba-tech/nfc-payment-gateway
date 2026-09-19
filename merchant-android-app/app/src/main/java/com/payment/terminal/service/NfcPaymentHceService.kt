package com.payment.terminal.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets

class NfcPaymentHceService : HostApduService() {

    companion object {
        private const val TAG = "NfcPaymentHce"

        // Текущий платежный URL, который передается покупателю при касании
        @Volatile
        var activePaymentUrl: String = "http://192.168.1.100:3000/?order=ORD-001"

        // Capability Container (CC) файл для Type 4 Tag
        private val CAPABILITY_CONTAINER = byteArrayOf(
            0x00, 0x0F,       // Длина CC (15 байт)
            0x20,             // Версия маппинга 2.0
            0x00, 0x7F,       // MLe (макс. R-APDU)
            0x00, 0x7F,       // MLc (макс. C-APDU)
            0x04, 0x06,       // NDEF File Control TLV
            0xE1.toByte(), 0x04, // NDEF File ID (E104)
            0x00, 0xFF.toByte(), // Максимальный размер NDEF (255 байт)
            0x00,             // Чтение без ограничений
            0x00              // Запись без ограничений
        )

        // Статусные байты ответа
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }

    private var selectedFile: Int = 0 // 1 = CC, 2 = NDEF

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return STATUS_FAILED

        val hex = bytesToHex(commandApdu)
        Log.d(TAG, "APDU In: $hex")

        // 1. Выбор NDEF приложения (AID: D2760000850101)
        if (hex.startsWith("00A4040007D2760000850101")) {
            selectedFile = 0
            return STATUS_SUCCESS
        }

        // 2. Выбор CC файла (File ID: E103)
        if (hex.startsWith("00A4000C02E103")) {
            selectedFile = 1
            return STATUS_SUCCESS
        }

        // 3. Выбор NDEF файла (File ID: E104)
        if (hex.startsWith("00A4000C02E104")) {
            selectedFile = 2
            return STATUS_SUCCESS
        }

        // 4. Чтение данных (READ BINARY: 00 B0 ...)
        if (commandApdu.size >= 4 && commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xB0.toByte()) {
            val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)

            if (selectedFile == 1) {
                // Отдаем CC файл
                return sliceWithStatus(CAPABILITY_CONTAINER, offset)
            } else if (selectedFile == 2) {
                // Генерируем NDEF запись с текущим платежным URL
                val ndefFile = createNdefUriFile(activePaymentUrl)
                return sliceWithStatus(ndefFile, offset)
            }
        }

        return byteArrayOf(0x6A.toByte(), 0x82.toByte()) // File not found
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = 0
        Log.d(TAG, "NFC сессия деактивирована: reason=$reason")
    }

    // Создание NDEF URI сообщения в стандарте NFC Forum
    private fun createNdefUriFile(url: String): ByteArray {
        val uriBytes = url.toByteArray(StandardCharsets.UTF_8)
        val payloadLength = uriBytes.size + 1 // +1 байт для префикса протокола (0x00 = прямой URL)
        
        val record = ByteArray(payloadLength + 4)
        record[0] = 0xD1.toByte() // MB=1, ME=1, SR=1, TNF=0x01 (Well-Known)
        record[1] = 0x01.toByte() // Длина типа ('U' = 1 байт)
        record[2] = payloadLength.toByte() // Длина полезной нагрузки
        record[3] = 0x55.toByte() // Тип 'U' (URI)
        record[4] = 0x00.toByte() // Префикс 0x00 (нет сокращения, полный URL)
        System.arraycopy(uriBytes, 0, record, 5, uriBytes.size)

        // Добавляем 2 байта длины файла NDEF в начале
        val ndefFile = ByteArray(record.size + 2)
        ndefFile[0] = ((record.size shr 8) and 0xFF).toByte()
        ndefFile[1] = (record.size and 0xFF).toByte()
        System.arraycopy(record, 0, ndefFile, 2, record.size)

        return ndefFile
    }

    private fun sliceWithStatus(data: ByteArray, offset: Int): ByteArray {
        if (offset >= data.size) return STATUS_SUCCESS
        val length = data.size - offset
        val result = ByteArray(length + 2)
        System.arraycopy(data, offset, result, 0, length)
        result[length] = 0x90.toByte()
        result[length + 1] = 0x00.toByte()
        return result
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
