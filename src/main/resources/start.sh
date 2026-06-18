
#!/bin/bash

echo "Iniciando swagger-monitor..."

nohup java \
  -DBM_HOME=/opt/BM_HOME \
  -jar swagger-monitor-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  > swagger-monitor.log 2>&1 &

PID_MONITOR=$!

echo "Iniciando swaggergenerator..."

nohup java \
  -jar swaggergenerator-0.0.1-SNAPSHOT.jar \
  > swaggergenerator.log 2>&1 &

PID_GENERATOR=$!

echo "----------------------------------------"
echo "swagger-monitor PID: $PID_MONITOR"
echo "swaggergenerator PID: $PID_GENERATOR"
echo "Aplicaciones iniciadas correctamente."
echo "----------------------------------------"
