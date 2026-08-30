# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY xm-base/pom.xml xm-base/
COPY ofs-domain/pom.xml ofs-domain/
COPY ofs-app/pom.xml ofs-app/

# Download dependencies
RUN mvn dependency:go-offline -B

COPY . .
RUN mvn package -pl ofs-app -am -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app

COPY --from=builder /app/ofs-app/target/*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
