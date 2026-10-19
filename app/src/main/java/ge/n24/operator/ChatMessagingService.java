package ge.n24.operator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class ChatMessagingService extends FirebaseMessagingService {
  @Override public void onMessageReceived(RemoteMessage message){
    if(!"new_chat_message".equals(message.getData().get("type")))return;
    NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    String channel="n24_native_chat_v26";
    if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(channel,"N24 yeni çat mesajları",NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(true);
      Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
      ch.setSound(sound,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
      manager.createNotificationChannel(ch);}
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    String messageId=message.getData().get("message_id");
    try{long id=Long.parseLong(messageId==null?"0":messageId);
      if(id>0){long last=getSharedPreferences("n24_native",MODE_PRIVATE).getLong("last_guest_alert_id",-1);
        if(id<=last)return;
        getSharedPreferences("n24_native",MODE_PRIVATE).edit().putLong("last_guest_alert_id",id).apply();
      }
    }catch(NumberFormatException ignored){}
    Intent open=new Intent(this,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent click=PendingIntent.getActivity(this,22,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
    builder.setSmallIcon(R.drawable.ic_notification).setContentTitle("N24 · Canlı dəstək").setContentText("1".equals(message.getData().get("is_test"))?"Test bildirişi gəldi":"Yeni dəstək mesajı var").setAutoCancel(true).setContentIntent(click);
    if(Build.VERSION.SDK_INT<26)builder.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE);
    manager.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),builder.build());
  }
}
