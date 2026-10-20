plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Mock PG (payment gateway)"

// 가짜 외부 시스템: DB 없이 상태를 메모리에 보관
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // 비동기 웹훅 도착 대기, 버전은 Spring Boot BOM 관리
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
