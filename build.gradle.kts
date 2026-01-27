import com.google.protobuf.gradle.id

plugins {
	kotlin("jvm") version "2.3.0"
	kotlin("plugin.spring") version "2.3.0"
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.protobuf") version "0.9.5"
	kotlin("plugin.jpa") version "2.3.0"
}

group = "com.capstone"
version = "0.0.1-SNAPSHOT"
description = "Capstone project focus"

extra["springGrpcVersion"] = "1.0.1"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.grpc:spring-grpc-server-web-spring-boot-starter")
	implementation("io.grpc:grpc-services")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.grpc:spring-grpc-dependencies:${property("springGrpcVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc"
	}
	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java"
		}
	}
	generateProtoTasks {
		all().forEach {
			it.plugins {
				id("grpc") {
					option("@generated=omit")
				}
			}
		}
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
