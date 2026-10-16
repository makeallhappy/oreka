#!/bin/bash
# orkweb-spring 开发环境管理脚本
# 用 systemd --user 托管,避免终端 session 结束时进程被回收

UNIT=orkweb-spring-dev
JAR=/home/nathan/oreka/orkweb-spring/target/orkweb-spring-0.0.2-SNAPSHOT.jar
JAVA=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
# 音频输出路径可用 OREKA_AUDIO_PATH 环境变量覆盖(默认 application.yml 里的 /var/log/orkaudio/audio)
AUDIO_PATH=${OREKA_AUDIO_PATH:-/tmp/orkaudio-audio}

case "$1" in
  start)
    systemctl --user start "$UNIT" 2>/dev/null || \
      systemd-run --user --unit="$UNIT" --no-block --setenv=OREKA_AUDIO_PATH="$AUDIO_PATH" "$JAVA" -jar "$JAR"
    sleep 8
    systemctl --user status "$UNIT" --no-pager | head -5
    ;;
  stop)
    systemctl --user stop "$UNIT"
    echo "stopped"
    ;;
  restart)
    systemctl --user stop "$UNIT" 2>/dev/null
    sleep 2
    systemd-run --user --unit="$UNIT" --no-block --setenv=OREKA_AUDIO_PATH="$AUDIO_PATH" "$JAVA" -jar "$JAR"
    sleep 8
    systemctl --user status "$UNIT" --no-pager | head -5
    ;;
  status)
    systemctl --user status "$UNIT" --no-pager
    ;;
  logs)
    journalctl --user -u "$UNIT" -f
    ;;
  *)
    echo "用法: $0 {start|stop|restart|status|logs}"
    exit 1
    ;;
esac
