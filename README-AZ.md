# N24 Native Operator V24 (WebView YOXDUR)

Bu Android tətbiqi yalnız canlı dəstək üçün nəzərdə tutulub: admin email/parolu ilə giriş, açıq/bağlı/arxiv söhbətlər, native mesaj ekranı, cavab verərkən **Operator / Baş admin / Moderator / Redaktor** etiketi seçimi. HTML/WebView yoxdur.

## Quraşdırma ardıcıllığı
1. ƏVVƏL `XeberPortal-V24-NATIVE-CHAT-SERVER-UPDATE.zip`-i `public_html`-ə açıb `https://n24.ge/v24-install.php` quraşdırıcısını super admin kimi işə salın.
2. GitHub reposu əvvəlki `iqaibov-create/N24-Operator` olaraq qalır. Termux ilə bu layihə fayllarını həmin repoya göndərin.
3. Android app-da admin email və parolunu yazın.

### Termux əmrləri
```bash
cd ~/N24-Operator
unzip -o /sdcard/Download/N24-Operator-V24-NATIVE-ANDROID-SOURCE.zip -d .
# V22 WebView Kotlin mənbələrini layihədən çıxarırıq:
rm -f app/src/main/java/ge/n24/operator/MainActivity.kt app/src/main/java/ge/n24/operator/ChatMessagingService.kt
git add -A
git commit -m "N24 native operator V24"
git push origin main
```

Sonra GitHub → N24-Operator → Actions → `N24 Native Operator APK` → Artifacts → `N24-Native-Operator-V24-APK` yüklənir; ZIP içində `app-debug.apk` var.

**Qeyd:** Köhnə debug APK ilə imza fərqlidirsə Android yenisini quraşdırmağa icazə verməyəcək. O halda köhnə `N24 Operator` tətbiqini silib, yeni APK-ni quraşdırın. Saytın mesajları serverdə saxlandığı üçün silinmir.

## Real bildiriş və səs üçün Firebase
1. Firebase Console-da Android tətbiqi əlavə edin: `ge.n24.operator`.
2. `google-services.json`-ı alın, faylı base64 koduna çevirin (`base64 -w 0 google-services.json` uyğun olmayanda `base64 google-services.json | tr -d '\n'`).
3. GitHub repo → Settings → Secrets and variables → Actions → New repository secret:
   - Name: `N24_FIREBASE_JSON_BASE64`
   - Value: base64 məzmunu.
4. Saytın serverində Firebase service account JSON-u `public_html`-dən **kənarda** `n24_private_firebase/firebase-service-account.json` kimi yerləşdirin (V20 README-dəki kimi). **Bu şəxsi açarı GitHub-a yükləməyin və heç kimə göndərməyin.**
5. GitHub Actions-da build yenidən başladın, tətbiqi quraşdırın, Android bildiriş icazəsi verin.

Başqa telefondan `n24.ge` saytının chat widgetinə mesaj yazın. Operator APK bildirişi səsli göstərməlidir. Server Firebase tənzimlənməyibsə, mesajlar yenə native tətbiqdə görünür, amma push gəlmir.

## Qeydlər
- Native ekranlar Java/Android UI komponentləri ilə çəkilir, WebView yoxdur.
- Çat siyahısı tətbiq açıq olanda ~6.5 saniyədən bir yenilənir. Tətbiq bağlı olarkən dəyişiklik üçün FCM lazımdır.
- Operator adı yalnız vizual etiketdir; real admin hüququnu dəyişmir.
- GitHub Actions build burada icra edilməyib. Build nəticəsini Actions-dan yoxlayın.
