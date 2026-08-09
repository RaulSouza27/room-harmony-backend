# Estágio de build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copia pom.xml e faz download das dependências (otimização de cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código fonte e compila a aplicação gerando o JAR
COPY src ./src
RUN mvn package -DskipTests

# Estágio de execução
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copia o JAR compilado do estágio de build
COPY --from=build /app/target/*.jar app.jar

# Define variáveis de ambiente padrão para o Spring Boot (podem ser sobrescritas no Dokploy)
ENV SERVER_PORT=3313
EXPOSE 3313

# Comando para iniciar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
