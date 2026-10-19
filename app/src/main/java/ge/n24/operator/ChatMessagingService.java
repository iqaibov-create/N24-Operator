package ge.n24.operator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class ChatMessagingService extends FirebaseMessagingService {
  @Override public void onMessageReceived(RemoteMessage message){
    if(!"new_chat_message".equals(message.getData().get("type")))return;
    NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    String channel="n24_native_chat_alerts";
    if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(channel,"N24 yeni çat mesajları",NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(true);manager.createNotificationChannel(ch);}
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    Intent open=new Intent(this,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent click=PendingIntent.getActivity(this,22,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
    builder.setSmallIcon(R.drawable.ic_notification).setContentTitle("N24 · Canlı dəstək").setContentText("Yeni dəstək mesajı var").setAutoCancel(true).setContentIntent(click);
    if(Build.VERSION.SDK_INT<26)builder.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE);
    manager.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),builder.build());
  }
}
