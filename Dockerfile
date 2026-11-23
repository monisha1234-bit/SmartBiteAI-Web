FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY SmartBiteWeb.java /app/SmartBiteWeb.java
COPY public /app/public
RUN javac SmartBiteWeb.java
ENV PORT=8080
EXPOSE 8080
CMD ["java", "SmartBiteWeb"]
