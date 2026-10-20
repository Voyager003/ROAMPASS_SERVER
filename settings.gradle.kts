rootProject.name = "ROAMPASS_SERVER"

// 서로 독립된 Spring Boot 앱 3개. 서로의 코드에 의존하지 않고 HTTP로만 통신
include("roampass-api", "mock-pg", "mock-supplier")
