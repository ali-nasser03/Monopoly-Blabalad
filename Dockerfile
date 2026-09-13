# ---------- مرحلة البناء ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# ننسخ pom.xml لحاله أول حتى Docker يعمل cache للـ dependencies
# ومايعيد تحميلها كل مرة نعدل فيها كود (بس pom.xml).
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- مرحلة التشغيل ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/monopoly-balbalad.jar app.jar

# منصات زي Railway/Render بتحدد PORT تلقائيًا وقت التشغيل
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
