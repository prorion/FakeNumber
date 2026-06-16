# FakeNumber

앱이 코드로 조회하는 **"내 전화번호"를 단말 내부에서 가짜 번호로 인식**시키는 LSPosed/Xposed 모듈입니다.

표준 텔레포니 API의 반환값을 가로채 위조하므로, 실제 SIM·통화·SMS에는 영향을 주지 않고 **앱이 읽는 값만** 바뀝니다. 위조할 번호는 모듈 앱의 UI에서 재빌드 없이 바로 변경할 수 있습니다.

> ⚠️ 본 모듈은 프라이버시 보호·앱 동작 테스트 등 **정당하고 인가된 용도**를 위한 도구입니다. 타인 사칭, 인증 우회, 사기 등 불법적·기만적 목적의 사용을 금합니다. 사용에 따른 모든 책임은 사용자에게 있습니다.

---

## 동작 원리

후킹 코드는 **대상 앱 프로세스 안**에서 실행되며, 아래 텔레포니 메서드의 반환값을 `afterHookedMethod`에서 덮어씁니다. 원본 반환값의 표기 형식(E.164 / 대시 / RAW)을 감지해 **같은 형식으로** 위조하므로 앱의 포맷 검증을 깨지 않습니다.

후킹 대상 메서드:

| 메서드 | 비고 |
| --- | --- |
| `SubscriptionManager.getPhoneNumber(int)` | API 33+ 표준 경로 |
| `SubscriptionManager.getPhoneNumber(int, int)` | 2-arg 직접 호출 대비 |
| `TelephonyManager.getLine1Number()` | deprecated이나 잔존 |
| `TelephonyManager.getLine1Number(int)` | hidden 오버로드 |
| `TelephonyManager.getMsisdn()` | hidden |
| `SubscriptionInfo.getNumber()` | deprecated이나 잔존 |

설정 UI와 후킹 코드는 서로 다른 프로세스에서 동작하므로, 번호 공유에는 LSPosed의 `XSharedPreferences`(`xposedsharedprefs` 메타데이터)를 사용합니다.

## 요구 사항

- 루팅된 Android 기기 (Magisk + Zygisk)
- **LSPosed** — 원조 LSPosed는 Android 14까지만 지원하므로, Android 15/16에서는 **[JingMatrix 포크](https://github.com/JingMatrix/LSPosed)(현 Vector)** 사용
- minSdk 28 (Android 9) / targetSdk 35

## 빌드

```bash
./gradlew assembleDebug
```

산출물: `app/build/outputs/apk/debug/app-debug.apk`
(LSPosed는 서명 종류와 무관하므로 디버그 APK로 충분합니다. AAB 아님.)

## 설치 및 사용

1. APK 설치
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. **LSPosed Manager → 모듈 → FakeNumber 토글 ON** (번호 저장보다 먼저)
3. 모듈 **scope**에서 번호를 위조할 **대상 앱만** 선택 (시스템 전역은 부트루프 위험, 비권장)
4. 런처에서 **FakeNumber** 실행 → 위조할 번호 입력 → 저장
   - `01012341234`, `010-1234-1234`, `+821012341234` 등 **아무 포맷으로 입력해도** 자동 정규화됩니다.
5. **대상 앱을 완전 강제중지**(설정 → 앱 → 강제중지) 후 재실행 → 위조값 반영

> 모듈이 OFF인 상태에서 번호를 저장하면 권한 폴백으로 후킹쪽이 값을 못 읽습니다. 반드시 **모듈 ON → 저장** 순서를 지키세요. 번호만 바꿀 때는 강제중지로 즉시 반영됩니다.

## 한계

표준 텔레포니 API 경로만 위조합니다. 앱이 다음 경로로 번호를 얻으면 잡히지 않습니다.

- 사용자가 직접 입력한 값 / 서버에 저장된 계정 번호 (메신저 다수)
- SMS 인증 기반 번호 확인
- Google Play Services 번호 힌트 API, SIM 주소록(ADN), IMS/캐리어 서비스 등

또한 다이얼러/메시지 등 통신 컴포넌트에 전역 적용하면 오작동·부트루프 위험이 있으므로 **대상 앱 단위 scope**를 권장합니다.

## 기술 스택

Kotlin · Gradle (Kotlin DSL) · Android Gradle Plugin · Xposed API 82 (`compileOnly`)

## 라이선스

별도 명시가 없는 한 개인 학습·연구용으로 제공됩니다.
