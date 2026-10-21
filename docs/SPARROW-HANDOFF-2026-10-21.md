# Sparrow 보안 수정 작업 인수인계 — 2026-10-21

## 현재 상태와 작업 원칙

- 기준 커밋: `f0f483d` (main). 이 문서와 함께 올라가는 변경은 **중간 작업이며 검증 완료본이 아니다**.
- 사용자의 중단 요청으로 보안 수정 작업을 멈추고 인수인계한다.
- **기존 기능을 유지해야 한다. 기능을 없애야 해결되는 항목은 수정하지 말고 사용자에게 별도 보고한다.**
- 빌드, 단위 테스트, 기기 테스트, Sparrow 재분석은 아직 실행하지 않았다. 아래 추가 테스트도 실행 전이다.
- 앱 설치·삭제, 데이터 초기화, 패키지 변경은 하지 않았다. 별도 테스트 앱을 설치하지 않는다.
- 기존 사용자 파일 `app.zip`은 변경하거나 커밋하지 않는다. 수정 전 소스일 수 있으므로 재분석에 그대로 쓰지 않는다.

## 확인한 분석

- 프로젝트: daycare / [Sparrow 이슈 목록](https://app.sparrowcloud.ai/projects/6105?assetId=21470042&tab=issue&assetFullName=app&mode=parent)
- app 하위 총 30건: null 역참조 4건, 일반적인 예외 처리 7건, DNS 보안 결정 17건, salt 없는 해시 1건, exported 옵션 1건.
- 사이트의 해결·제외 상태를 변경하지 않았고 AI 가이드도 사용하지 않았다. 위험도 감소나 경고 해소는 아직 확인하지 못했다.

## 이번에 실제 변경한 내용

| 파일 (app/src 기준) | 변경 내용 | 관련 이슈 |
| --- | --- | --- |
| main/java/com/example/pathreport/MainActivity.kt | Compose의 drafts를 지역 변수 rows로 한 번 읽고 null 검사하여 `drafts!!` 제거 | 9721867 |
| main/java/com/example/pathreport/StopDetector.kt | 장소 좌표가 null 또는 유효 범위 밖이면 해당 후보를 지우고 건너뜀. 정상 대상은 계속 감지 | 9721875 |
| main/java/com/example/pathreport/SecureCareStore.kt | 원자적 저장 실패 복구를 catch(Exception)에서 committed 플래그와 finally로 변경 | 9721869 |
| main/java/com/example/pathreport/TripTracking.kt | 서비스 시작 실패를 SecurityException, IllegalStateException, IllegalArgumentException으로 구분하고 안내 문구 분리 | 9721878 |
| main/java/com/example/pathreport/TripLocationService.kt | startForeground 실패도 위 세 예외로 구분하고 중지·안내 유지 | 9721877 |
| main/java/com/example/pathreport/LocationEntry.kt | 구버전 Geocoder 작업의 IOException과 RuntimeException을 구분. 역주소 조회의 RuntimeException은 빈 결과로 숨기지 않고 호출자에게 전달 | 9721864, 9721865 |
| test/java/com/example/pathreport/CoordinateOnlyStorageTest.kt | 좌표 `!!`를 명시적인 AssertionError로 변경 | 9721885 |
| test/java/com/example/pathreport/TripExcelTest.kt | 날짜 파싱의 `!!` 제거, 잘못된 날짜를 엄격히 검사하고 실패를 명시 | 9721886 |
| test/java/com/example/pathreport/StopDetectorTest.kt | null/NaN/범위 밖 좌표가 도착으로 기록되지 않고 정상 대상의 기록은 유지되는 회귀 테스트 추가 | 위 StopDetector 변경 검증용 |

GPS 반경 50m, 정확도 기준 30m, 정차 20초, 암호화 형식·키 별칭·데이터 스키마는 변경하지 않았다. CRUD, 지도·검색, 운행 감지, 엑셀 내보내기를 제거하지 않았다. 실제 동작 보장은 후속 검증이 필요하다.

## 후속 작업 — 반드시 확인할 사항

1. 먼저 이 커밋의 diff를 검토하고 빌드·단위 테스트를 실행한다. 수정된 서비스 예외 분기가 각 Android 버전의 실제 실패를 충분히 처리하는지 확인한다. RuntimeException 처리가 남은 곳을 경고 회피 목적으로 무작정 좁히지 않는다.
2. SecureCareStore의 finally 복구를 확인한다. 쓰기 실패 시 기존 암호화 파일 보존, 예외 전달, 성공 후 재읽기 검증이 유지되어야 한다. 복구 자체의 예외가 원래 예외를 가릴 가능성도 검토한다. 실제 사용자 데이터로 고장 주입 실험을 하지 않는다.
3. MainActivity의 일반 예외 경고 **9721866은 아직 수정 전**. Sparrow 상세 화면에서 정확한 지적 위치가 기존 87행 exportTrips의 `catch (_: Exception)`임을 확인했다. 파일 선택기를 못 여는 상황에서 exportSession.finish와 exporting=false, 사용자 안내를 반드시 유지하며 예외를 구분한다. 다른 저장·로드 경계의 catch는 데이터 보존 목적이 있으므로 일괄 제거하지 않는다.
4. TripExportDocument의 **9721876은 아직 수정 전**. 엑셀 출력 실패 시 스트림 닫기, 새로 만든 불완전 문서 정리, TripExportFailure의 partialFileRemoved 전달을 유지한다. 기존 TripExportFlowTest에 정상 출력·쓰기 실패·닫기 실패·삭제 실패·null 출력 검증이 있다. 취소 예외 전달도 검토한다.
5. DNS 경고는 오탐 가능성이 높다. **9721863 상세 추적은 CareCipher.kt의 Cipher.getInstance → init → `check(cipher.iv.size == 12)`**를 DNS 보안 결정으로 지적했다. 이 코드는 DNS 조회나 인증용 호스트 검사 없이 AES-GCM IV 길이를 검증한다. IV 검사나 암호화를 삭제하지 않는다. 나머지 동일 분류의 추적도 개별 확인한 뒤 근거를 남긴다. 현재 모든 17건을 상세 검증한 것은 아니다.
6. **9721872**의 SHA-256은 SecureCareStore의 legacyDigest로, 구버전 자료 이관 중 중복 처리를 막는 표식이다. 비밀번호 저장용 해시가 아니다. 암호화 저장 영역에 들어가며, 무작정 salt를 추가하면 기존 digest 비교와 이관 재시도 호환성이 깨질 수 있다. 기존 자료 호환성과 실제 노출 경로를 검토하고 처리 여부를 결정한다. 이번에는 변경하지 않았다.
7. **9721857**의 MainActivity exported=true는 MAIN/LAUNCHER 진입점이다. false로 바꾸면 정상 실행이 막힐 수 있어 변경하지 않았다. 외부 Intent로 인증·데이터 처리 우회가 없는지 확인한다. TripLocationService는 exported=false다.
8. 수정 후 Sparrow에 최신 소스로 재분석하여 실제 남은 항목을 확인한다. 소스 수정만으로 기존 분석 결과가 갱신되지는 않는다. 근거 없이 해결/제외로 표시하지 않는다.

### DNS 경고 ID 목록

- CoordinateOnlyStorageTest: 9721884, 9721883, 9721882, 9721881
- CareCipherTest: 9721880, 9721879
- SecureCareStore: 9721874, 9721873, 9721871, 9721870, 9721868
- SecureCareStoreTest(androidTest): 9721863, 9721862, 9721861, 9721860, 9721859, 9721858

## 후속 검증 방법

프로젝트 루트에서 Android Studio에 설정된 JDK로 `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest`를 실행한다. 환경 의존 문제와 소스 컴파일 실패를 구분하여 기록한다. 이전에 사용한 JDK는 `C:\Program Files\Android\Android Studio\jbr`, SDK는 `C:\Users\wlals\AppData\Local\Android\Sdk`다.

기기 검증은 등록 자료 보존, 장소 검색·핀 선택, GPS 도착 감지와 중단·재개, 엑셀 저장·취소·실패 복구 순으로 진행한다. 앱 삭제나 데이터 초기화 없이 검증하고, 설치가 필요하면 사용자와 범위를 맞춘다.

원격 최신 버전에서 이동 경로 기록 기능은 이미 제거된 상태였다. 이번 작업에서 제거한 것이 아니며 보안 수정과 함께 임의로 복원하지 않는다. 기존 `docs/SECURITY-TEST-2026-10-21.md`는 이전 MobSF 검증 기록이며 이번 수정의 통과 증거가 아니다.
