# 깜빡이 영단어 (Kkamppagi English) - Android 앱 프로젝트

## 1. 안드로이드 OS 호환성 (2020년 이후 기기 완벽 지원)
- **minSdk = 30 (Android 11, 2020년 9월 출시)**: 2020년 이후 출시된 모든 갤럭시·픽셀 등 안드로이드 스마트폰/태블릿에서 작동합니다.
- **targetSdk = 35 (Android 15)**: 최신 보안 및 TTS(음성 합성) 패키지 가시성(`<queries>`) 규정을 준수합니다.

## 2. APK 파일로 빌드하는 2가지 방법

### 방법 A: Android Studio에서 1분 만에 APK 추출 (PC 설치 시)
1. 본 압축 파일을 해제합니다.
2. **Android Studio** 실행 후 `Open`을 눌러 압축 해제한 `KkamppagiEnglish` 폴더를 엽니다.
3. 상단 Gradle 동기화(Sync)가 완료되면 상단 메뉴에서 **[Build] > [Build Bundle(s) / APK(s)] > [Build APK(s)]**를 클릭합니다.
4. 우측 하단 알림의 **`locate`**를 클릭하면 `app/build/outputs/apk/debug/app-debug.apk` 파일을 바로 얻을 수 있습니다.
5. 해당 `.apk` 파일을 스마트폰으로 옮겨 실행하면 즉시 설치됩니다.

### 방법 B: GitHub Actions 클라우드 자동 빌드 (PC에 Android Studio 미설치 시)
1. GitHub에 새 저장소(Repository)를 만들고 이 프로젝트의 파일들을 업로드합니다.
2. 포함된 `.github/workflows/build-apk.yml` 워크플로우가 자동으로 실행됩니다.
3. 저장소의 **[Actions]** 탭 > 최신 실행 항목 하단의 **Artifacts (`KkamppagiEnglish-debug-apk`)**에서 컴파일된 `.apk` 파일을 바로 다운로드할 수 있습니다.
