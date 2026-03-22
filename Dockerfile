# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY xm-base/pom.xml xm-base/
COPY xm-scenario/pom.xml xm-scenario/
COPY xm-spring/pom.xml xm-spring/

# Download dependencies
RUN mvn dependency:go-offline -B

COPY . .
RUN mvn package -pl xm-spring -am -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app

COPY --from=builder /app/xm-spring/target/*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
