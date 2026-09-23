/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.worker

import dev.octoshrimpy.quik.data.BuildConfig
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramForwarder {
    fun forwardMessage(address: String, body: String, chatId: String) {
        val botToken = BuildConfig.TELEGRAM_BOT_TOKEN
        if (botToken.isEmpty()) {
            Timber.w("Telegram forwarding skipped: TELEGRAM_BOT_TOKEN is not set at build time")
            return
        }
        if (chatId.isEmpty()) {
            Timber.w("Telegram forwarding skipped: Chat ID is not configured")
            return
        }

        Thread {
            try {
                val text = "From: $address\n$body"
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlString = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Timber.d("Successfully forwarded message to Telegram")
                } else {
                    Timber.e("Failed to forward message to Telegram, response code: $responseCode")
                }
                connection.inputStream.close()
            } catch (e: Exception) {
                Timber.e(e, "Error forwarding message to Telegram")
            }
        }.start()
    }
}
