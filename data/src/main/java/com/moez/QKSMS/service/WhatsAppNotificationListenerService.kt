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
package dev.octoshrimpy.quik.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.worker.TelegramForwarder
import timber.log.Timber
import javax.inject.Inject

class WhatsAppNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var prefs: Preferences

    override fun onCreate() {
        super.onCreate()
        try {
            AndroidInjection.inject(this)
        } catch (e: Exception) {
            Timber.e(e, "Error injecting WhatsAppNotificationListenerService")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") return

        if (!prefs.forwardWhatsappToTelegram.get()) return

        val chatId = prefs.telegramChatId.get()
        if (chatId.isEmpty()) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        // Ignore generic system / background status notifications from WhatsApp
        if (text.contains("Checking for new messages", ignoreCase = true) ||
            text.contains("WhatsApp Web is currently active", ignoreCase = true) ||
            text.contains("WhatsApp Web is active", ignoreCase = true) ||
            title.contains("WhatsApp Web", ignoreCase = true)
        ) {
            return
        }

        Timber.d("Intercepted WhatsApp message from '$title': $text")
        TelegramForwarder.forwardMessage("WhatsApp: $title", text, chatId)
    }
}
