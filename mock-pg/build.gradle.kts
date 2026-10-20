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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
