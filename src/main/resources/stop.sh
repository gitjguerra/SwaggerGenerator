#!/bin/bash

echo "Deteniendo swagger-monitor..."

pkill -f "swagger-monitor-0.0.1-SNAPSHOT.jar"

echo "Deteniendo swaggergenerator..."

pkill -f "swaggergenerator-0.0.1-SNAPSHOT.jar"

echo "Aplicaciones detenidas."