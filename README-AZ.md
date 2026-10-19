# N24 Operator V22 — Termux + GitHub ilə APK

Bu ZIP **Android Kotlin mənbə kodudur, hazır APK deyil**. GitHub Actions onu debug APK kimi yığır.

## 1. GitHub-da ayrıca repo yarat

GitHub-da `iqaibov-create` hesabında **N24-Operator** adlı yeni, boş *Public* və ya *Private* repo yarat. `Ali-eba-Android` reposuna bu faylları yerləşdirmə.

## 2. Termux əmrləri (telefondan)

ZIP-i telefonun **Download** qovluğuna endir və Termux-da:

```sh
termux-setup-storage
pkg update -y
pkg install git unzip -y
mkdir -p "$HOME/N24-Operator"
cd "$HOME/N24-Operator"
unzip -o /sdcard/Download/N24-Operator-V22-ANDROID-GITHUB-SOURCE.zip -d .
ls -la
```

`app` və `.github` qovluqları kökdə olmalıdır. Sonra:

```sh
cd "$HOME/N24-Operator"
git init
git branch -M main
git add .
git commit -m "N24 Operator V22 initial build"
git remote add origin https://github.com/iqaibov-create/N24-Operator.git
git push -u origin main
```

GitHub istifadəçi adı/parol istəsə, adi parol yox, **Personal Access Token** və ya `gh auth login` istifadə et. `git commit` zamanı ad/e-poçt istəsə, şəxsi Git konfiqurasiyanı daxil et.

## 3. APK yüklə

GitHub → `N24-Operator` → **Actions** → **N24 Operator APK** → son uğurlu run → **Artifacts** → `N24-Operator-V22-debug-APK`.

Artifact ZIP-in içində `app-debug.apk` olacaq. Bu debug APK-dir; Play Store üçün release deyil.

## 4. Firebase push (sonrakı addım)

Firebase olmadan da **operator yazışma paneli işləyir**, amma tətbiq bağlıykən push bildirişi gəlmir. Bildiriş üçün:

1. Firebase-də Android app yaradıb **package** `ge.n24.operator` seç.
2. `google-services.json` endir; Termux-da `base64 -w0 /sdcard/Download/google-services.json` ilə kodlaşdır.
3. GitHub repoda **Settings → Secrets and variables → Actions** bölməsində `N24_FIREBASE_JSON_BASE64` yarat və həmin base64 məzmunu daxil et.
4. `Actions → N24 Operator APK → Run workflow` ilə yenidən yığ.
5. Serverdə FCM üçün ayrıca **service-account** məxfi açarı `public_html` xaricində qurulmalıdır. Onu GitHub-a, APK-yə, çatda və ya ictimai qovluğa qoyma.
6. Saytdakı V20 `n20_operator_push_tokens` cədvəli və FCM göndərmə PHP kodu da düzgün quraşdırılmalıdır.

## 5. Sayt hissəsi

Əvvəlki V19–V21 sayt modulları quraşdırılmış olmalıdır. V22 üçün yalnız `XeberPortal-V22-OPERATOR-MOBIL-PANEL-FIX.zip` sayt yeniləməsini `public_html` daxilinə açmaq kifayətdir; ayrıca SQL yoxdur.

APK-də **Admin** düyməsi giriş üçün, **Söhbətlər** isə operator panelini açmaq üçündür. Login məlumatlarını ZIP-ə daxil etmə.
