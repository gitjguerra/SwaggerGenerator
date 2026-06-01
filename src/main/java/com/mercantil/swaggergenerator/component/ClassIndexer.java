package com.mercantil.swaggergenerator.component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

@Component
public class ClassIndexer {

    private final Map<String, ClassOrInterfaceDeclaration> classIndex = new HashMap<>();
    private final Map<String, String> classFileMap = new HashMap<>();

    /**
     * Escanea todo el proyecto desde el root
     */
    public void scanProject(String rootPath) {

        try {
            List<Path> javaFiles = Files.walk(Paths.get(rootPath))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            JavaParser parser = new JavaParser();

            for (Path path : javaFiles) {
                parseFile(parser, path);
            }

            System.out.println("✅ Classes indexed: " + classIndex.size());

        } catch (IOException e) {
            throw new RuntimeException("Error scanning project", e);
        }
    }

    /**
     * Parsea cada archivo .java
     */
    private void parseFile(JavaParser parser, Path path) {

        try {
            ParseResult<CompilationUnit> result = parser.parse(path);

            if (result.getResult().isEmpty()) return;

            CompilationUnit cu = result.getResult().get();

            cu.findAll(ClassOrInterfaceDeclaration.class)
                    .forEach(clazz -> {

                        String simpleName = clazz.getNameAsString();
                        String fullName = resolveFullName(cu, simpleName);

                        // ✅ índice por nombre simple
                        classIndex.put(simpleName, clazz);

                        // ✅ índice por nombre completo
                        classIndex.put(fullName, clazz);

                        // opcional (debug)
                        classFileMap.put(simpleName, path.toString());

                        System.out.println("Indexed: " + fullName);
                    });

        } catch (Exception e) {
            System.out.println("⚠️ Error parsing: " + path);
        }
    }

    /**
     * Construye nombre completo con el package
     */
    private String resolveFullName(CompilationUnit cu, String className) {

        return cu.getPackageDeclaration()
                .map(pkg -> pkg.getNameAsString() + "." + className)
                .orElse(className);
    }

    /**
     * 🔍 BUSCAR CLASE (USAR ESTE EN TU SCHEMA BUILDER)
     */
    public Optional<ClassOrInterfaceDeclaration> findClass(String type) {

        if (type == null) return Optional.empty();

        // 1️⃣ búsqueda directa
        if (classIndex.containsKey(type)) {
            return Optional.of(classIndex.get(type));
        }

        // 2️⃣ fallback por nombre simple
        for (String key : classIndex.keySet()) {
            if (key.endsWith("." + type)) {
                return Optional.of(classIndex.get(key));
            }
        }

        return Optional.empty();
    }

    /**
     * Debug: ver dónde está una clase
     */
    public String findClassPath(String type) {
        return classFileMap.get(type);
    }
}