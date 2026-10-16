#!/bin/sh
# PackHub 的 Gradle 启动脚本（精简版，功能与官方 gradlew 一致）
# 需要 JAVA_HOME 指向 JDK 25（Minecraft 26.3 使用 Java 25）

APP_HOME=$(cd "${0%/*}" > /dev/null && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
    if ! command -v java > /dev/null 2>&1; then
        echo "ERROR: 未找到 java，请设置 JAVA_HOME 指向 JDK 25" >&2
        exit 1
    fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
