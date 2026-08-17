package com.mercantil.swaggergenerator.component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

@Component
public class ClassIndexer {

	private static final Logger log = LogManager.getLogger(ClassIndexer.class);

    // ✅ mapa de clases por nombre simple (ambiguo si hay colisión entre paquetes)
    private final Map<String, ClassOrInterfaceDeclaration> classMap = new HashMap<>();

    // ✅ mapa por nombre totalmente calificado (package.SimpleName), permite
    // desambiguar una colisión de nombre simple cuando se conoce el contexto
    // (imports/paquete) de quien referencia el tipo
    private final Map<String, ClassOrInterfaceDeclaration> classMapByFqn = new HashMap<>();

    // =========================================================
    // ✅ REGISTRAR CLASE
    // =========================================================
    public void register(ClassOrInterfaceDeclaration clazz) {

        String name = clazz.getNameAsString();

        // ✅ dos clases con el mismo nombre simple en paquetes distintos
        // colisionan silenciosamente en classMap; se advierte para que sea
        // visible en logs. classMapByFqn (ver findClass(name, context)) permite
        // resolver correctamente cuando se conoce quién referencia el tipo.
        ClassOrInterfaceDeclaration existing = classMap.get(name);

        if (existing != null && existing != clazz) {
            log.warn("Colision en ClassIndexer: '{}' ya estaba registrada, se sobreescribe", name);
        }

        classMap.put(name, clazz);

        packageOf(clazz).ifPresent(pkg -> classMapByFqn.put(pkg + "." + name, clazz));
    }

    // =========================================================
    // ✅ BUSCAR CLASE (por nombre simple, puede ser ambiguo)
    // =========================================================
    public Optional<ClassOrInterfaceDeclaration> findClass(String name) {

        ClassOrInterfaceDeclaration clazz = classMap.get(name);

        return Optional.ofNullable(clazz);
    }

    // =========================================================
    // ✅ BUSCAR CLASE CON CONTEXTO
    // ✅ desambigua colisiones de nombre simple usando, en orden: los imports
    // explícitos de quien referencia el tipo, y luego si hay una clase con ese
    // nombre en el MISMO paquete (igual que la resolución de tipos de Java).
    // Si ninguna estrategia aplica, cae al lookup ambiguo por nombre simple.
    // =========================================================
    public Optional<ClassOrInterfaceDeclaration> findClass(String name, ClassOrInterfaceDeclaration context) {

        if (context == null) {
            return findClass(name);
        }

        Optional<CompilationUnit> cuOpt = context.findCompilationUnit();

        if (cuOpt.isPresent()) {

            CompilationUnit cu = cuOpt.get();

            for (ImportDeclaration imp : cu.getImports()) {

                if (imp.isAsterisk() || imp.isStatic()) {
                    continue;
                }

                if (!imp.getName().getIdentifier().equals(name)) {
                    continue;
                }

                ClassOrInterfaceDeclaration byFqn = classMapByFqn.get(imp.getNameAsString());

                if (byFqn != null) {
                    return Optional.of(byFqn);
                }
            }

            Optional<String> pkg = packageOf(context);

            if (pkg.isPresent()) {

                ClassOrInterfaceDeclaration samePackage = classMapByFqn.get(pkg.get() + "." + name);

                if (samePackage != null) {
                    return Optional.of(samePackage);
                }
            }
        }

        return findClass(name);
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
        classMapByFqn.clear();
    }

    private Optional<String> packageOf(ClassOrInterfaceDeclaration clazz) {

        return clazz.findCompilationUnit().flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString());
    }
}
