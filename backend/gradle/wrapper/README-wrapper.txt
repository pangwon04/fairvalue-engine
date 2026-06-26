gradle-wrapper.jar 는 이 저장소에 포함되지 않았습니다(생성 환경의 네트워크 제약).
아래 중 하나로 jar 를 생성하세요:
  1) gradle 이 로컬에 있으면:  cd backend && gradle wrapper --gradle-version 8.7
  2) CI 에서는 contracts-ci.yml 이 jar 부재 시 자동으로 `gradle wrapper` 를 실행합니다.
생성 후 gradle/wrapper/gradle-wrapper.jar 를 커밋하면 `./gradlew test` 가 동작합니다.
