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

            val forwardEnabled = prefs.forwardWhatsappToTelegram.get()
            val chatId = prefs.telegramChatId.get()

            Timber.d("WhatsApp notification posted from $pkg. forwardEnabled=$forwardEnabled, chatId='$chatId'")

            if (!forwardEnabled) {
                Timber.d("WhatsApp forwarding is disabled in settings")
                return
            }

            if (chatId.isEmpty()) {
                Timber.w("Telegram Chat ID is empty in settings! Please set Telegram Chat ID in settings.")
                return
            }

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

            val normalizedText = text.lowercase().replace("’", "'")
            val senderLower = sender.lowercase()

            // 1. WhatsApp system notifications have no contact sender, or sender is "WhatsApp" / "WhatsApp Business" itself
            val isWhatsAppSystemSender = sender.isBlank() ||
                senderLower == "whatsapp" ||
                senderLower == "whatsapp business"

            // 2. Specific automated backup or status notification text phrases
            val isAutomatedBackupOrStatusText = normalizedText.contains("couldn't complete backup") ||
                normalizedText.contains("tap for more info") ||
                normalizedText.contains("checking for new messages") ||
                normalizedText.contains("whatsapp web is currently active") ||
                normalizedText.contains("whatsapp web is active")

            // Drop if it's from WhatsApp system itself, or an automated status text
            if (isWhatsAppSystemSender || isAutomatedBackupOrStatusText) {
                Timber.d("Ignoring WhatsApp system notification: sender='$sender', text='$text'")
                return
            }

            // Deduplicate rapid duplicate notifications posted within a 5-second window
            val currentKey = "$sender:$text"
            val currentTime = System.currentTimeMillis()
            if (currentKey == lastMessageKey && currentTime - lastMessageTime < 5000L) {
                Timber.d("Skipping duplicate notification within 5s window: '$currentKey'")
                return
            }
            lastMessageKey = currentKey
            lastMessageTime = currentTime

            Timber.d("Forwarding WhatsApp message from '$sender' to Telegram")
            TelegramForwarder.forwardMessage("WhatsApp: $sender", text, chatId)
        } catch (t: Throwable) {
            Timber.e(t, "Error processing WhatsApp notification")
        }
    }

    companion object {
        private var lastMessageKey: String = ""
        private var lastMessageTime: Long = 0L
    }
}
