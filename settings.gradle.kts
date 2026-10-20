rootProject.name = "ROAMPASS_SERVER"

// Three independent Spring Boot apps. They never depend on each other's code — only HTTP.
include("roampass-api", "mock-pg", "mock-supplier")
