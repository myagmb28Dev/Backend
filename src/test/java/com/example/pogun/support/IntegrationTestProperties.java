package com.example.pogun.support;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:pogun_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "app.storage.s3.bucket=test-bucket",
        "app.storage.s3.region=ap-northeast-2",
        "app.startup-warmup.enabled=false",
        "app.schema-sync.enabled=false",
        "app.presence.store=memory",
        "app.firebase.auth.mode=EMULATOR",
        "app.firebase.auth.allow-emulator=true",
        "app.firebase.auth.project-id=pogun-test",
        "app.firebase.auth.emulator-project-id=pogun-test",
        "app.firebase.auth.web-api-key=test-key",
        "FIREBASE_KEY_BASE64=",
        "FIREBASE_CONFIG_JSON="
})
public abstract class IntegrationTestProperties {
}
