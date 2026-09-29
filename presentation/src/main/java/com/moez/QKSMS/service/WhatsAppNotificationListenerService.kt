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
import androidx.core.app.NotificationCompat
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.worker.TelegramForwarder
import timber.log.Timber
import javax.inject.Inject

class WhatsAppNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var prefs: Preferences

    override fun onCreate() {
        super.onCreate()
        injectDependencies()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        injectDependencies()
        Timber.d("WhatsApp notification listener connected")
    }

    private fun injectDependencies() {
        if (!::prefs.isInitialized) {
            try {
                AndroidInjection.inject(this)
            } catch (e: Exception) {
                Timber.e(e, "Error injecting WhatsAppNotificationListenerService")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        try {
            injectDependencies()
            if (!::prefs.isInitialized) {
                Timber.w("WhatsApp notification listener: prefs not initialized")
                return
            }

            if (!prefs.forwardWhatsappToTelegram.get()) return

            val chatId = prefs.telegramChatId.get()
            if (chatId.isEmpty()) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            var sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            var text = ""

            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                val lastMessage = messagingStyle.messages.last()
                val msgText = lastMessage.text?.toString() ?: ""
                val personName = lastMessage.person?.name?.toString()

                if (!personName.isNullOrBlank()) {
                    sender = if (messagingStyle.isGroupConversation && !messagingStyle.conversationTitle.isNullOrBlank()) {
                        "${messagingStyle.conversationTitle} ($personName)"
                    } else {
                        personName
                    }
                } else if (messagingStyle.isGroupConversation && !messagingStyle.conversationTitle.isNullOrBlank()) {
                    sender = messagingStyle.conversationTitle.toString()
                }
                text = msgText
            }

            if (text.isBlank()) {
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence("android.text")?.toString()
                    ?: ""
            }

            if (sender.isBlank() && text.isBlank()) return

            if (text.contains("Checking for new messages", ignoreCase = true) ||
                text.contains("WhatsApp Web is currently active", ignoreCase = true) ||
                text.contains("WhatsApp Web is active", ignoreCase = true) ||
                sender.contains("WhatsApp Web", ignoreCase = true)
            ) {
                return
            }

            Timber.d("Forwarding WhatsApp message from '$sender' to Telegram")
            TelegramForwarder.forwardMessage("WhatsApp: $sender", text, chatId)
        } catch (t: Throwable) {
            Timber.e(t, "Error processing WhatsApp notification")
        }
    }
}
