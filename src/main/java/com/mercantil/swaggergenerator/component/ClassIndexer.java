package com.mercantil.swaggergenerator.component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

@Component
public class ClassIndexer {

	private static final Logger log = LogManager.getLogger(ClassIndexer.class);

    // ✅ mapa de clases por nombre simple
    private final Map<String, ClassOrInterfaceDeclaration> classMap = new HashMap<>();

    // =========================================================
    // ✅ REGISTRAR CLASE
    // =========================================================
    public void register(ClassOrInterfaceDeclaration clazz) {

        String name = clazz.getNameAsString();

        // 🔥 LOG
        //log.info("Registrando clase: {}", name);

        classMap.put(name, clazz);
    }

    // =========================================================
    // ✅ BUSCAR CLASE
    // =========================================================
    public Optional<ClassOrInterfaceDeclaration> findClass(String name) {

        ClassOrInterfaceDeclaration clazz = classMap.get(name);

        /*
        if (clazz == null) {
            log.info("ClassIndexer no encontró: {}", name);
        } else {
        	log.info("ClassIndexer encontró: {}", name);
        }
        */

        return Optional.ofNullable(clazz);
    }

    // =========================================================
    // ✅ NUEVO: OBTENER TODAS LAS CLASES 🔥
    // =========================================================
    public Map<String, ClassOrInterfaceDeclaration> getAllClasses() {
        return classMap;
    }

    // =========================================================
    // ✅ LIMPIAR (útil entre servicios)
    // =========================================================
    public void clear() {
        classMap.clear();
    }
}