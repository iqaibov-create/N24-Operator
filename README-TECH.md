Native Android implementation details:
- HTTPS native-api.php with 14-day bearer access tokens
- Hashed tokens in server DB; AES-256/GCM encryption via Android Keystore on client
- Role authorization in every server request; choice of reply display label gated by role
- FirebaseMessagingService data message type 'new_chat_message'
- Android default high-importance notification channel id n24_native_chat_alerts
- Without optional Firebase config, native chat UI works after server V24 migration, but no background push
- No WebView, browser login or site admin UI
