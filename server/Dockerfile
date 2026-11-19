# O jar é gerado fora do Docker (precisa do Android SDK por causa do módulo :contract):
#   ./gradlew :server:buildFatJar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S cvforge && adduser -S cvforge -G cvforge
USER cvforge
COPY --chown=cvforge:cvforge build/libs/*-all.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
