@echo off

echo Iniciando swagger-monitor...

start "swagger-monitor" /MIN cmd /c ^
java -DBM_HOME=C:\BM_HOME -jar swagger-monitor-0.0.1-SNAPSHOT.jar --server.port=8081 ^> swagger-monitor.log 2^>^&1

echo Iniciando swaggergenerator...

start "swaggergenerator" /MIN cmd /c ^
java -jar swaggergenerator-0.0.1-SNAPSHOT.jar ^> swaggergenerator.log 2^>^&1

echo ----------------------------------------
echo Aplicaciones iniciadas correctamente.
echo ----------------------------------------

pause