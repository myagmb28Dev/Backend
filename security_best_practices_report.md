# 보안 점검 리포트

## 한눈에 보기

이번 점검에서는 Spring Boot + Firebase 기반 백엔드의 인증, 업로드, 로깅, 운영 기본 설정을 중심으로 보안을 확인했습니다.

현재 상태 요약:
- 치명적인 활성 취약점: 없음
- 이번에 바로 수정한 항목: 4건
- 아직 남아 있는 운영상 리스크: 2건

결론:
- 개발 및 테스트 환경 기준으로는 안전성이 눈에 띄게 좋아졌습니다.
- 다만 운영 배포 기준으로는 `ddl-auto: update` 제거와 이미지 디코드 검증 보강이 추가로 필요합니다.

---

## 이번에 수정한 항목

### 1. Auth Emulator 오동작 가능성 차단

영향:
- 잘못된 환경 변수 설정만으로 에뮬레이터 모드가 켜지면, 로컬 전용 인증 흐름이 의도치 않게 섞일 수 있습니다.

조치 내용:
- 이제 `FIREBASE_AUTH_EMULATOR_HOST`만으로는 에뮬레이터 모드가 켜지지 않습니다.
- `FIREBASE_ALLOW_AUTH_EMULATOR=true`를 함께 명시해야만 동작하도록 변경했습니다.
- 로컬 전용 스크립트에서만 이 값을 주도록 분리했습니다.

관련 파일:
- [FirebaseConfig.java](/D:/Codes/Pogun_Back/src/main/java/com/example/pogun/config/FirebaseConfig.java#L25)
- [start-local-auth-stack.ps1](/D:/Codes/Pogun_Back/scripts/start-local-auth-stack.ps1#L193)
- [start-real-firebase-backend.ps1](/D:/Codes/Pogun_Back/scripts/start-real-firebase-backend.ps1#L10)

적용 결과:
- 로컬 테스트 편의성은 유지하면서, 운영/실제 Firebase 모드와의 혼동 가능성을 줄였습니다.

---

### 2. SQL 및 데이터소스 로그 기본 노출 차단

영향:
- SQL 로그가 기본으로 켜져 있으면 쿼리 구조, 동작 패턴, 일부 민감한 값이 로그 수집 시스템에 남을 수 있습니다.

조치 내용:
- `spring.jpa.show-sql`을 기본 `false`로 변경했습니다.
- P6Spy 로그도 기본 비활성화되도록 바꿨습니다.
- 필요할 때만 환경 변수로 명시적으로 켜도록 정리했습니다.

관련 파일:
- [application.yaml](/D:/Codes/Pogun_Back/src/main/resources/application.yaml#L22)

적용 결과:
- 개발자가 의도적으로 켤 때만 SQL 로그가 나오고, 기본 운영 로그 노출 위험이 줄었습니다.

---

### 3. 파일 업로드 검증 강화

영향:
- 기존에는 확장자 위주 검증이라서, 이미지가 아닌 파일이 위장 업로드될 수 있었습니다.

조치 내용:
- 파일 비어 있음 검사
- 최대 크기 제한 검사
- 확장자 allowlist 검사
- Content-Type allowlist 검사
- PNG/JPEG 시그니처(매직 바이트) 검사 추가

관련 파일:
- [AiController.java](/D:/Codes/Pogun_Back/src/main/java/com/example/pogun/controller/AiController.java#L40)
- [AiControllerTest.java](/D:/Codes/Pogun_Back/src/test/java/com/example/pogun/controller/AiControllerTest.java#L48)

적용 결과:
- 단순 확장자 위장 파일은 이전보다 훨씬 쉽게 차단됩니다.

---

### 4. 민감 로그 최소화

영향:
- 이메일, Firebase UID, 채팅 원문 같은 데이터가 로그에 남으면 로그 유출 시 피해 범위가 커집니다.

조치 내용:
- 채팅 로그에서 `rawMessage`, `firebaseUid` 제거
- 메시지 길이 정도만 남기도록 조정
- 인증/탈퇴/로그아웃 로그도 이메일 대신 내부 식별자 중심으로 정리

관련 파일:
- [NoticeChatService.java](/D:/Codes/Pogun_Back/src/main/java/com/example/pogun/service/NoticeChatService.java#L78)
- [AuthService.java](/D:/Codes/Pogun_Back/src/main/java/com/example/pogun/service/AuthService.java#L62)

적용 결과:
- 운영 로그가 남더라도 직접적인 사용자 데이터 노출 가능성이 줄었습니다.

---

## 아직 남아 있는 항목

### 5. Hibernate `ddl-auto: update` 사용 중

심각도:
- 중간

설명:
- 현재는 앱 실행 시 엔티티 변경이 DB 스키마에 자동 반영될 수 있습니다.
- 개발 단계에서는 편하지만, 운영 환경에서는 승인되지 않은 스키마 변경이나 드리프트를 만들 수 있습니다.

관련 파일:
- [application.yaml](/D:/Codes/Pogun_Back/src/main/resources/application.yaml#L24)

권장 대응:
- 개발(local/dev)에서는 유지 가능
- 스테이징/운영에서는 `validate` 또는 `none`으로 분리
- 실제 배포는 Flyway 또는 Liquibase 같은 마이그레이션 기반으로 관리

---

### 6. 이미지 파일의 실제 디코드 검증은 아직 없음

심각도:
- 낮음

설명:
- 지금은 파일 시그니처까지 확인하므로 이전보다 훨씬 안전합니다.
- 다만 파일이 “정말 정상적으로 디코드 가능한 이미지인지”, “비정상 해상도나 손상 파일인지”까지는 아직 보지 않습니다.

관련 파일:
- [AiController.java](/D:/Codes/Pogun_Back/src/main/java/com/example/pogun/controller/AiController.java#L128)

권장 대응:
- `ImageIO.read(...)` 같은 실제 디코드 검증 추가
- 필요하면 최대 해상도 제한도 함께 적용

---

## 점검 결과 정리

현재 평가:
- 인증/권한 구조: 양호
- SQL Injection 관점: 양호
- 운영 기본 설정: 일부 보완 필요
- 파일 업로드 보안: 개선됨, 추가 강화 여지 있음
- 로그 민감정보 관리: 개선됨

우선순위 추천:
1. `ddl-auto`를 운영 환경에서 제거
2. 이미지 디코드 검증 추가
3. 이후 관리자/파일 업로드/웹소켓 경계에 대한 추가 보안 리뷰 진행

---

## 검증 결과

실행 확인:
- `./gradlew.bat compileJava` 성공
- `./gradlew.bat test --tests "com.example.pogun.controller.AiControllerTest"` 성공
- `./gradlew.bat test` 성공

점검 기준일:
- 2026-03-24