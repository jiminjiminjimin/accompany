# 안심 동행

노인 주간보호센터의 장소·어르신 관리 Android 앱입니다. 현재 단계는 한국어 관리 화면과 안전한 기기 내부 저장까지 구현했습니다.

- 장소/어르신 등록·수정·삭제, 별도 ID 및 여러 명 연결
- 여러 장소 행 추가, 행별 오류 및 저장 전 확인
- 주소 검색 결과 선택, 현재 GPS 위치 등록, 좌표 직접 입력
- Android Keystore + AES-256-GCM 파일 저장, 기존 평문 자료의 검증 후 이전
- 기기 인증 앱 잠금, 백업/기기 이전 제외, 화면 캡처 차단

GPS 자동 운행 기록과 엑셀 내보내기는 다음 단계입니다. GPS 기록은 실제 승하차 확인이 아닌 위치 기반 추정입니다.

## 빌드

기존 JDK·Gradle 환경을 유지합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-debug.ps1 -Offline -NoDaemon -NoConfigurationCache
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

## 문서

- [협업 인수인계: 진행 상황·구조·다음 작업](docs/협업-인수인계.md)
- [협업자에게 바로 전달할 메시지](docs/협업자에게-전달할-메시지.md)
- [전체 요구사항·진행 상태·개인정보 설계](docs/개발계획.md)
- [검증 결과와 환경 제약](docs/검증결과.md)
- [Android Studio에서 확인할 순서](docs/Android-Studio-확인순서.md)

기기 PIN·패턴·비밀번호 설정 후 앱을 열어 주세요. 앱 잠금/종료/화면 회전 시 미저장 입력은 복원되지 않습니다. 키와 자료는 백업되지 않으며, 앱 제거·데이터 삭제 후에는 복구할 수 없습니다. 테스트에는 가상 데이터만 사용합니다.
