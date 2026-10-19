package ge.n24.operator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.Notification;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.text.Editable;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** N24 native chat operator – no WebView, no site admin UI. */
public final class MainActivity extends Activity {
  private static final String API="https://n24.ge/destek/native-api.php?action=";
  private static final int NAVY=Color.rgb(15,59,80), TEAL=Color.rgb(3,135,119), INK=Color.rgb(23,51,68), GREY=Color.rgb(111,132,143), BG=Color.rgb(243,248,251);
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private final Handler handler=new Handler(Looper.getMainLooper());
  private LinearLayout root,content,messageContainer;private ScrollView messageScroll;
  private String token="",userName="",userRole="",tab="open",page="login",lastInbox="",lastMessages="",fcmToken="";
  private int chatId=0;private String chatName="",chatStatus="open";private boolean archived=false,pending=false,foreground=false;
  private EditText loginEmail,loginPassword,replyEdit;private Spinner identitySpinner;
  private JSONArray currentChats=new JSONArray();
  private TextView unreadBadge;
  private boolean alertPolling=false, pushRegistered=false;
  private long lastNotifiedId=0,lastTypingSent=0;
  private int newMessageCount=0;
  private static final String CHANNEL="n24_native_chat_v26";
  private static final String[][] ROLE_OPTIONS={{"Operator","operator"},{"Baş admin","chief"},{"Moderator","moderator"},{"Redaktor","editor"}};
  interface Answer {void done(JSONObject response,String error);}
  @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(NAVY);
    createChannel();requestPushPermission();token=loadSecret();
    if(token.isEmpty())showLogin();else checkSession();
    lastNotifiedId=prefs().getLong("last_guest_alert_id",-1);
    handler.postDelayed(ticker,5500);
    handler.postDelayed(alertTicker,9000);
  }
  @Override protected void onResume(){super.onResume();foreground=true;}
  @Override protected void onPause(){foreground=false;super.onPause();}
  @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
  private final Runnable ticker=new Runnable(){@Override public void run(){if(foreground&&!pending&&!token.isEmpty()){
     if(page.equals("inbox"))loadChats(false);else if(page.equals("chat"))loadMessages(false);
   }handler.postDelayed(this,6500);}};
  private final Runnable alertTicker=new Runnable(){@Override public void run(){
    if(foreground&&!token.isEmpty())pollNewMessages();
    handler.postDelayed(this,9000);
  }};
  private void pollNewMessages(){
    if(alertPolling)return;alertPolling=true;
    // Avoid duplicate foreground alerts after Firebase service has already posted this ID.
    long fromPush=prefs().getLong("last_guest_alert_id",-1);
    if(fromPush>lastNotifiedId)lastNotifiedId=fromPush;
    long since=Math.max(0,lastNotifiedId);
    request("alerts&since_id="+since,"GET",null,(r,e)->{
      alertPolling=false;
      if(e!=null||r==null)return;
      long last=r.optLong("last_id",0);
      if(lastNotifiedId<0){
        lastNotifiedId=last;
        prefs().edit().putLong("last_guest_alert_id",last).apply();
        return;
      }
      if(last>lastNotifiedId){
        newMessageCount=Math.max(1,r.optInt("new_count",1));
        lastNotifiedId=last;
        prefs().edit().putLong("last_guest_alert_id",last).apply();
        updateUnreadBadge();
        showForegroundNotice(newMessageCount);
      }
    });
  }
  private void showForegroundNotice(int count){
    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      snackbar("Yeni dəstək mesajı · Android bildiriş icazəsini aktiv edin");return;
    }
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent click=PendingIntent.getActivity(this,25,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
    b.setSmallIcon(R.drawable.ic_notification).setContentTitle("N24 · Yeni dəstək mesajı")
     .setContentText(count+" yeni mesaj var").setContentIntent(click).setAutoCancel(true);
    if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_ALL);
    nm.notify(2025,b.build());
  }
  private void updateUnreadBadge(){
    if(unreadBadge==null||page.equals("login"))return;
    unreadBadge.setText(newMessageCount>0?"🔔  "+newMessageCount+" yeni dəstək mesajı":"Bildirişlər · " +(pushRegistered?"Cihaz qeydiyyatı var":"Cihaz push qeydiyyatını yoxlayın"));
  }
  private void requestPushPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);}
  private void createChannel(){if(Build.VERSION.SDK_INT>=26){
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    NotificationChannel ch=new NotificationChannel(CHANNEL,"N24 dəstək · səsli bildiriş",NotificationManager.IMPORTANCE_HIGH);
    ch.enableVibration(true);
    Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    ch.setSound(sound,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
    nm.createNotificationChannel(ch);
  }}
  private JSONObject json(Object... fields){JSONObject j=new JSONObject();try{for(int i=0;i+1<fields.length;i+=2)j.put(String.valueOf(fields[i]),fields[i+1]);}catch(Exception ignored){}return j;}
  private void request(String path,String method,JSONObject body,Answer listener){
    String credential=token;
    io.execute(()->{JSONObject parsed=null;String error=null;HttpURLConnection conn=null;
      try{
        conn=(HttpURLConnection)new URL(API+path).openConnection();conn.setConnectTimeout(9000);conn.setReadTimeout(12000);conn.setRequestMethod(method);conn.setUseCaches(false);conn.setRequestProperty("Accept","application/json");
        if(!credential.isEmpty()){conn.setRequestProperty("Authorization","Bearer "+credential);conn.setRequestProperty("X-N24-Operator-Token",credential);}
        if(body!=null){conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
        int code=conn.getResponseCode();InputStream stream=code<400?conn.getInputStream():conn.getErrorStream();StringBuilder raw=new StringBuilder();
        if(stream!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){raw.append(line);if(raw.length()>200000)break;}}
        parsed=new JSONObject(raw.toString());if(code>=400||!parsed.optBoolean("ok",false))error=parsed.optString("error","Server xətası ("+code+")");
      }catch(Exception e){error="Bağlantı xətası. İnterneti və V24 sayt yeniləməsini yoxlayın.";}
      finally{if(conn!=null)conn.disconnect();}
      final JSONObject result=parsed;final String err=error;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())listener.done(result,err);});
    });
  }
  private void checkSession(){request("me","GET",null,(r,e)->{if(e!=null){clearSecret();showLogin();}else{userName=r.optString("name","Admin");userRole=r.optString("role","");activatePush();showInbox();pollNewMessages();}});}
  private void activatePush(){
    try{
      if(FirebaseApp.initializeApp(this)==null){pushRegistered=false;updateUnreadBadge();return;}
      FirebaseMessaging.getInstance().getToken()
        .addOnSuccessListener(t->{fcmToken=t;request("push_register","POST",json("token",t),(a,b)->{
          pushRegistered=(b==null&&a!=null&&a.optBoolean("ok"));updateUnreadBadge();
        });})
        .addOnFailureListener(e->{pushRegistered=false;updateUnreadBadge();});
    }catch(Exception e){pushRegistered=false;updateUnreadBadge();}
  }
  private void showPushStatus(){
    request("push_status","GET",null,(r,e)->{
      if(e!=null){new AlertDialog.Builder(this).setTitle("Bildiriş yoxlaması").setMessage(e).setPositiveButton("Bağla",null).show();return;}
      boolean server=r.optBoolean("server_ready"),registered=r.optBoolean("device_registered");
      boolean permission=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
      NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      boolean channel=Build.VERSION.SDK_INT<26||(nm.getNotificationChannel(CHANNEL)!=null&&nm.getNotificationChannel(CHANNEL).getImportance()>NotificationManager.IMPORTANCE_NONE);
      String text="Android bildiriş icazəsi: "+(permission?"VAR":"BAĞLIDIR")
        +"\nSəsli bildiriş kanalı: "+(channel?"AKTİV":"BAĞLIDIR")
        +"\nFirebase tətbiq tokeni: "+(fcmToken.isEmpty()?"YOXDUR":"VAR")
        +"\nCihaz serverə qeydiyyat: "+(registered?"VAR":"YOXDUR")
        +"\nServer Firebase açarı: "+(server?"VAR":"YOXDUR")
        +"\n\nBağlı APK-də bildiriş üçün bütün sətirlər aktiv olmalıdır.";
      AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("N24 · Bildiriş yoxlaması").setMessage(text).setNegativeButton("Bağla",null);
      if(server&&registered&&userRole.equals("super_admin")&&!fcmToken.isEmpty()){
        b.setPositiveButton("Test bildirişi göndər",(d,w)->request("push_test","POST",json("token",fcmToken),(rr,err)->snackbar(err==null?"Test bildirişi göndərildi":"Test: "+err)));
      }
      b.show();
    });
  }
  private void buildPage(String type){page=type;unreadBadge=null;root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setFitsSystemWindows(true);setContentView(root);}
  private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
  private GradientDrawable rounded(int color,int rad){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(rad));return g;}
  private GradientDrawable outlined(int color,int stroke,int rad){GradientDrawable g=rounded(color,rad);g.setStroke(dp(1),stroke);return g;}
  private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
  private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
  private void margins(View v,int left,int top,int right,int bottom){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.setMargins(dp(left),dp(top),dp(right),dp(bottom));v.setLayoutParams(p);}
  private Button button(String label,int bg,Runnable onClick){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setPadding(dp(10),0,dp(10),0);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackground(rounded(bg,13));b.setOnClickListener(v->onClick.run());return b;}
  private void header(String heading,String subtitle,String right,Runnable action){LinearLayout bar=vertical();bar.setPadding(dp(20),dp(22),dp(20),dp(18));bar.setBackgroundColor(NAVY);LinearLayout row=horizontal();TextView title=text(heading,22,Color.WHITE,true);row.addView(title,new LinearLayout.LayoutParams(0,dp(36),1));if(right!=null){Button b=button(right,Color.rgb(40,100,115),action);row.addView(b,lp(-2,36));}bar.addView(row);TextView sub=text(subtitle,13,Color.rgb(189,223,220),false);bar.addView(sub,lp(-1,25));root.addView(bar);
    if(!page.equals("login")){
      LinearLayout stripe=horizontal();stripe.setPadding(dp(17),dp(5),dp(17),dp(5));stripe.setBackgroundColor(Color.rgb(232,246,245));
      unreadBadge=text("Bildirişlər",13,TEAL,true);
      stripe.addView(unreadBadge,new LinearLayout.LayoutParams(0,dp(36),1));
      Button test=button("🔔 Yoxla",TEAL,()->showPushStatus());
      stripe.addView(test,lp(-2,34));root.addView(stripe);updateUnreadBadge();
    }
  }
  private EditText edit(String hint,boolean password){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setPadding(dp(16),0,dp(16),0);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(GREY);e.setBackground(outlined(Color.WHITE,Color.rgb(217,231,235),15));if(password)e.setInputType(129);return e;}
  private void snackbar(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
  private void showLogin(){buildPage("login");header("N24 · Canlı dəstək","Yalnız operatorlar üçün native tətbiq",null,null);ScrollView scroll=new ScrollView(this);root.addView(scroll,lp(-1,-1));LinearLayout wrap=vertical();wrap.setPadding(dp(22),dp(34),dp(22),dp(16));scroll.addView(wrap);TextView icon=text("◉",55,TEAL,true);icon.setGravity(Gravity.CENTER);wrap.addView(icon,lp(-1,72));TextView heading=text("Operator girişi",26,INK,true);heading.setGravity(Gravity.CENTER);wrap.addView(heading,lp(-1,50));TextView sub=text("Saytın admin emaili və parolu ilə daxil olun.",14,GREY,false);sub.setGravity(Gravity.CENTER);wrap.addView(sub,lp(-1,52));loginEmail=edit("Admin email",false);loginEmail.setInputType(33);wrap.addView(loginEmail,lp(-1,54));margins(loginEmail,0,16,0,12);loginPassword=edit("Parol",true);wrap.addView(loginPassword,lp(-1,54));Button enter=button("Daxil ol  →",TEAL,()->{
     if(pending)return;String email=loginEmail.getText().toString().trim(),password=loginPassword.getText().toString();if(email.isEmpty()||password.isEmpty()){snackbar("Email və parolu yazın");return;}
     pending=true;request("login","POST",json("email",email,"password",password),(r,e)->{pending=false;if(e!=null){snackbar(e);return;}token=r.optString("token","");saveSecret(token);userName=r.optString("name","");userRole=r.optString("role","");loginPassword.setText("");activatePush();showInbox();pollNewMessages();});
   });wrap.addView(enter,lp(-1,54));margins(enter,0,18,0,0);TextView note=text("Şifrə telefonda saxlanılmır. Tətbiq yalnız təhlükəsiz N24 çat API-sinə qoşulur.",12,GREY,false);note.setGravity(Gravity.CENTER);wrap.addView(note,lp(-1,84));}
  private void showInbox(){lastInbox="";buildPage("inbox");header("N24 · Söhbətlər",userName+"  ·  "+formatRole(userRole),"Çıxış",()->new AlertDialog.Builder(this).setMessage("Hesabdan çıxılsın?").setPositiveButton("Çıxış",(d,w)->logout()).setNegativeButton("İmtina",null).show());
    LinearLayout tabs=horizontal();tabs.setPadding(dp(14),dp(16),dp(14),dp(12));for(String item:new String[]{"open","closed","archive"}){String label=item.equals("open")?"Açıq":item.equals("closed")?"Bağlı":"Arxiv";Button b=button(label,item.equals(tab)?TEAL:Color.rgb(126,153,167),()->{tab=item;showInbox();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(4),0,dp(4),0);tabs.addView(b,p);}root.addView(tabs);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=vertical();content.setPadding(dp(14),dp(8),dp(14),dp(20));scroll.addView(content);TextView wait=text("Söhbətlər yüklənir...",16,GREY,false);content.addView(wait,lp(-1,70));
    Button refresh=button("↻  Yenilə",Color.rgb(210,234,234),()->loadChats(true));refresh.setTextColor(TEAL);root.addView(refresh,lp(-1,52));margins(refresh,18,8,18,15);loadChats(true);
  }
  private void loadChats(boolean force){if(!page.equals("inbox"))return;request("list&tab="+tab,"GET",null,(r,e)->{if(!page.equals("inbox"))return;if(e!=null){snackbar(e);return;}JSONArray chats=r.optJSONArray("chats");if(chats==null)chats=new JSONArray();String signature=chats.toString();if(!force&&signature.equals(lastInbox))return;lastInbox=signature;content.removeAllViews();if(chats.length()==0){TextView empty=text("Bu bölmədə söhbət yoxdur",16,GREY,false);empty.setGravity(Gravity.CENTER);content.addView(empty,lp(-1,140));return;}
    for(int i=0;i<chats.length();i++){JSONObject c=chats.optJSONObject(i);if(c==null)continue;int id=c.optInt("id");String name=c.optString("display_name","Qonaq");String date=c.optString("last_message_at",""),stat=c.optString("status","open");
      LinearLayout card=horizontal();card.setPadding(dp(16),dp(13),dp(12),dp(13));card.setBackground(outlined(Color.WHITE,Color.rgb(223,235,239),20));TextView avatar=text(name.isEmpty()?"?":name.substring(0,1).toUpperCase(),22,TEAL,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(rounded(Color.rgb(226,247,243),100));card.addView(avatar,lp(52,52));LinearLayout info=vertical();info.setPadding(dp(12),0,0,0);info.addView(text(name,17,INK,true),lp(-1,28));info.addView(text(date.isEmpty()?"Yeni söhbət":date,12,GREY,false),lp(-1,25));card.addView(info,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("›",30,TEAL,true);card.addView(arrow,lp(23,54));card.setOnClickListener(v->{chatId=id;chatName=name;chatStatus=stat;archived=tab.equals("archive");showChat();});content.addView(card,lp(-1,-2));margins(card,0,0,0,10);
    }
  });}
  private String relativeTime(long ts,String fallback){
    if(ts<=0)return fallback;long elapsed=Math.max(0,(System.currentTimeMillis()/1000L-ts)/60L);
    if(elapsed<1)return "İndicə";
    if(elapsed<60)return elapsed+" dəqiqə əvvəl";
    if(elapsed<1440)return (elapsed/60)+" saat əvvəl";
    if(elapsed<2880)return "Dünən";
    return new java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault()).format(new java.util.Date(ts*1000L));
  }
  private void sendTyping(boolean isTyping){
    if(!page.equals("chat")||chatId<1||!chatStatus.equals("open")||archived||token.isEmpty())return;
    if(identitySpinner==null)return;
    int idx=identitySpinner.getSelectedItemPosition();List<String[]> options=allowedIdentities();
    if(idx<0||idx>=options.size())idx=0;
    request("typing","POST",json("chat_id",chatId,"typing",isTyping,"identity",options.get(idx)[1]),(r,e)->{});
  }
  private String formatRole(String role){switch(role){case "super_admin":return "Baş admin";case "chief_editor":return "Baş redaktor";case "moderator":return "Moderator";case "editor":return "Redaktor";default:return "Operator";}}
  private List<String[]> allowedIdentities(){List<String[]> list=new ArrayList<>();for(String[] p:ROLE_OPTIONS){if(p[1].equals("operator")||(userRole.equals("super_admin"))||(userRole.equals("moderator")&&p[1].equals("moderator"))||((userRole.equals("editor")||userRole.equals("chief_editor"))&&p[1].equals("editor")))list.add(p);}return list;}
  private void showChat(){newMessageCount=0;lastMessages="";buildPage("chat");header("‹  "+chatName,"Söhbət #"+chatId+"  ·  N24 canlı dəstək","Geri",()->showInbox());
    LinearLayout actions=horizontal();actions.setPadding(dp(14),dp(10),dp(14),dp(10));Button status=button(chatStatus.equals("open")?"Bağla":"Yenidən aç",TEAL,()->{
       request("status","POST",json("chat_id",chatId,"status",chatStatus.equals("open")?"closed":"open"),(r,e)->{if(e!=null)snackbar(e);else{chatStatus=chatStatus.equals("open")?"closed":"open";showChat();}});
    });if(!archived){actions.addView(status,new LinearLayout.LayoutParams(0,dp(40),1));}
    Button more=button(archived?"Arxivdən çıxar":"Arxivlə",Color.rgb(65,109,134),()->{String action=archived?"restore":"archive";if(!archived&&!chatStatus.equals("closed")){snackbar("Əvvəlcə söhbəti bağlayın");return;}
     new AlertDialog.Builder(this).setMessage(archived?"Söhbət arxivdən çıxarılsın?":"Söhbət arxivləşdirilsin?").setPositiveButton("Bəli",(d,w)->request(action,"POST",json("chat_id",chatId),(r,e)->{if(e!=null)snackbar(e);else showInbox();})).setNegativeButton("İmtina",null).show();
    });actions.addView(more,new LinearLayout.LayoutParams(0,dp(40),1));root.addView(actions);
    messageScroll=new ScrollView(this);messageScroll.setFillViewport(true);root.addView(messageScroll,new LinearLayout.LayoutParams(-1,0,1));messageContainer=vertical();messageContainer.setPadding(dp(16),dp(14),dp(16),dp(14));messageScroll.addView(messageContainer);
    if(chatStatus.equals("open")&&!archived){LinearLayout compose=vertical();compose.setPadding(dp(14),dp(10),dp(14),dp(16));compose.setBackground(outlined(Color.WHITE,Color.rgb(223,234,239),22));
      TextView label=text("Hansı adla cavab verirsiniz?",12,GREY,true);compose.addView(label,lp(-1,25));List<String[]> roles=allowedIdentities();List<String> captions=new ArrayList<>();for(String[] p:roles)captions.add(p[0]);identitySpinner=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,captions);identitySpinner.setAdapter(a);compose.addView(identitySpinner,lp(-1,44));
      LinearLayout row=horizontal();replyEdit=edit("Mesajınızı yazın...",false);replyEdit.setSingleLine(false);replyEdit.setMinLines(2);replyEdit.setMaxLines(4);replyEdit.addTextChangedListener(new TextWatcher(){
        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        @Override public void onTextChanged(CharSequence s,int start,int before,int count){
          if(s.length()==0){sendTyping(false);return;}
          long now=SystemClock.elapsedRealtime();if(now-lastTypingSent>=2800){lastTypingSent=now;sendTyping(true);}
        }
        @Override public void afterTextChanged(Editable e){}
      });
      row.addView(replyEdit,new LinearLayout.LayoutParams(0,dp(76),1));Button send=button("➤",TEAL,()->sendReply(roles));row.addView(send,lp(65,62));margins(send,8,0,0,0);compose.addView(row);root.addView(compose);
    }else{TextView note=text(archived?"Arxivdədir · əvvəl bərpa edin":"Bağlı söhbət · cavab üçün yenidən açın",14,GREY,false);note.setGravity(Gravity.CENTER);root.addView(note,lp(-1,60));}
    loadMessages(true);
  }
  private void loadMessages(boolean force){if(!page.equals("chat")||chatId<1)return;final int requestedChat=chatId;request("messages&chat_id="+chatId,"GET",null,(r,e)->{if(!page.equals("chat")||requestedChat!=chatId)return;if(e!=null){snackbar(e);return;}JSONObject chat=r.optJSONObject("chat");if(chat!=null){chatStatus=chat.optString("status","open");archived=chat.optInt("archived",0)==1;}
    JSONArray msgs=r.optJSONArray("messages");if(msgs==null)msgs=new JSONArray();String signature=msgs.toString();if(!force&&signature.equals(lastMessages))return;lastMessages=signature;messageContainer.removeAllViews();
    if(msgs.length()==0){TextView empty=text("Hələ mesaj yoxdur",16,GREY,false);empty.setGravity(Gravity.CENTER);messageContainer.addView(empty,lp(-1,110));}
    for(int i=0;i<msgs.length();i++){JSONObject m=msgs.optJSONObject(i);if(m==null)continue;boolean operator=m.optString("sender").equals("operator");String body=m.optString("body",""),role=m.optString("operator_label","operator"),time=m.optString("created_at","");
      LinearLayout line=horizontal();line.setGravity(operator?Gravity.RIGHT:Gravity.LEFT);LinearLayout card=vertical();card.setPadding(dp(13),dp(10),dp(13),dp(10));card.setBackground(rounded(operator?Color.rgb(219,247,235):Color.WHITE,17));
      TextView sender=text(operator?formatIdentity(role):chatName,12,TEAL,true);card.addView(sender,lp(-1,22));TextView message=text(body,16,INK,false);message.setMaxWidth(dp(280));card.addView(message,lp(-2,-2));TextView date=text(relativeTime(m.optLong("created_ts",0),time),11,GREY,false);card.addView(date,lp(-2,20));line.addView(card,lp(-2,-2));messageContainer.addView(line,lp(-1,-2));margins(line,0,0,0,11);
    }
    messageScroll.post(()->messageScroll.fullScroll(View.FOCUS_DOWN));
  });}
  private String formatIdentity(String key){for(String[] o:ROLE_OPTIONS)if(o[1].equals(key))return o[0];return "Operator";}
  private void sendReply(List<String[]> options){if(pending||replyEdit==null)return;String body=replyEdit.getText().toString().trim();if(body.isEmpty())return;int pos=identitySpinner.getSelectedItemPosition();if(pos<0||pos>=options.size())pos=0;String identity=options.get(pos)[1];pending=true;
    request("reply","POST",json("chat_id",chatId,"body",body,"identity",identity),(r,e)->{pending=false;if(e!=null){snackbar(e);return;}replyEdit.setText("");InputMethodManager manager=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);manager.hideSoftInputFromWindow(replyEdit.getWindowToken(),0);loadMessages(true);});
  }
  private void logout(){JSONObject body=json("fcm_token",fcmToken);request("logout","POST",body,(r,e)->{clearSecret();token="";userName="";showLogin();});}
  @Override public void onBackPressed(){if(page.equals("chat"))showInbox();else if(page.equals("inbox"))new AlertDialog.Builder(this).setMessage("Tətbiqdən çıxılsın?").setPositiveButton("Çıx",(a,b)->finish()).setNegativeButton("İmtina",null).show();else super.onBackPressed();}
  private SharedPreferences prefs(){return getSharedPreferences("n24_native",Context.MODE_PRIVATE);}
  private SecretKey key() throws Exception {KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("n24_native_session")){KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder("n24_native_session",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());kg.generateKey();}return (SecretKey)ks.getKey("n24_native_session",null);}
  private void saveSecret(String value){try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());String packed=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);prefs().edit().putString("encrypted_session",packed).apply();}catch(Exception e){snackbar("Sessiyanı saxlamaq mümkün olmadı");}}
  private String loadSecret(){try{String s=prefs().getString("encrypted_session","");if(!s.contains(":"))return "";String[] split=s.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(split[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(split[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
  private void clearSecret(){prefs().edit().remove("encrypted_session").apply();}
}
