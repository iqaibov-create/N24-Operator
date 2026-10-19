# N24 Operator V27 — Native Android

Bu paket **WebView olmayan Android tətbiqinin mənbə kodudur**, hazır APK deyil.
Əvvəl sayt tərəfdə N24 V27 yeniləməsini və /v27-install.php quraşdırıcısını işlədin.

## V27 yenilikləri

- Yeni N24 Operator üçün özəl ikon: 5 Android launcher ölçüsü, adi və dairəvi ikon.
- Native çatda mesaj vaxtları: "İndicə", "5 dəqiqə əvvəl", "Dünən" və s.
- Operator seçdiyi adla mesaj yazarkən saytdakı oxucuya animasiyalı "yazır..." görünür.
- Əvvəlki V26 native giriş, söhbət siyahısı, səsli bildiriş kodu və rol seçimləri saxlanılıb.

## Termux-dan GitHub-a göndər

1. ZIP-i Download qovluğuna yüklə, sonra:

```bash
cd ~/N24-Operator
unzip -o /sdcard/Download/N24-Operator-V27-NATIVE-ANDROID-SOURCE.zip -d .
rm -f app/src/main/java/ge/n24/operator/MainActivity.kt app/src/main/java/ge/n24/operator/ChatMessagingService.kt
git add -A
git commit -m "N24 Operator Native V27"
git push origin main
```

2. GitHub → https://github.com/iqaibov-create/N24-Operator/actions → yeni build → Artifacts → `N24-Native-Operator-V27-APK` → `app-debug.apk`.
3. Build qırmızı olsa **Build APK** addımındakı səhvi göndər.

## Firebase (bağlı tətbiqdə səsli push üçün MÜTLƏQ)

Android Firebase qeydiyyatında package ID **ge.n24.operator** olmalıdır.
`google-services.json` Firebase Console-dan götürülür, eyni fayl public repoya push olunmur.
Termux-da:

```bash
base64 /sdcard/Download/google-services.json | tr -d '\n'
```

Alınan kodu GitHub repository → Settings → Secrets and variables → Actions → New repository secret
`N24_FIREBASE_JSON_BASE64` olaraq əlavə et, sonra GitHub Actions-da yeni APK build et.

**Server üçün ayrıca FƏRQLİ JSON**: Firebase Console → Project Settings → Service accounts → Generate new private key.
Bu fayl `firebase-service-account.json` adı ilə **public_html-dən KƏNARDA**
`n24_private_firebase/firebase-service-account.json` yolunda olmalıdır.
Google Cloud-da Firebase Cloud Messaging API HTTP v1 aktiv olmalıdır.

Android-də bildiriş icazəsi və səsli bildiriş kanalı aktiv olmalıdır; Samsung-da batareya məhdudiyyətini yoxla.

**Diqqət:** Service account JSON-u, şəxsi key-i, GitHub tokenini və admin parolunu buraya və public GitHub-a yükləmə.

## Məhdudiyyət

APK bu mühitdə Android SDK ilə yığılmayıb. GitHub build nəticəsi və real Firebase push ayrıca yoxlanmalıdır.
