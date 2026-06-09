package com.mercantil.swaggergenerator.component;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

@Component
public class EndpointPathResolver {

    // =========================================================
    // ✅ RESOLVE PATH
    // =========================================================
    public String resolvePath(
            String controllerBasePath,
            MethodDeclaration method) {

        String methodPath =
                extractMethodPath(method);

        return normalizePath(
                controllerBasePath,
                methodPath);
    }

    // =========================================================
    // ✅ EXTRACT METHOD PATH
    // =========================================================
    private String extractMethodPath(
            MethodDeclaration method) {

        for (AnnotationExpr annotation
                : method.getAnnotations()) {

            String name =
                    annotation.getNameAsString();

            // =============================================
            // ✅ MAPPINGS
            // =============================================
            if ("PostMapping".equals(name)
                    || "GetMapping".equals(name)
                    || "PutMapping".equals(name)
                    || "DeleteMapping".equals(name)
                    || "PatchMapping".equals(name)
                    || "RequestMapping".equals(name)) {

                String value =
                        extractAnnotationValue(annotation);

                if (value != null
                        && !value.isBlank()) {

                    return value;
                }
            }
        }

        return "";
    }

    // =========================================================
    // ✅ EXTRACT VALUE
    // =========================================================
    private String extractAnnotationValue(
            AnnotationExpr annotation) {

        // =============================================
        // ✅ @PostMapping("/x")
        // =============================================
        if (annotation instanceof SingleMemberAnnotationExpr) {

            SingleMemberAnnotationExpr single =
                    (SingleMemberAnnotationExpr) annotation;

            return clean(
                    single.getMemberValue()
                            .toString());
        }

        // =============================================
        // ✅ @RequestMapping(value="/x")
        // =============================================
        if (annotation instanceof NormalAnnotationExpr) {

            NormalAnnotationExpr normal =
                    (NormalAnnotationExpr) annotation;

            Optional<MemberValuePair> pairOpt =
                    normal.getPairs()
                            .stream()

                            .filter(pair ->

                                    "value".equals(
                                            pair.getNameAsString())

                                            ||

                                            "path".equals(
                                                    pair.getNameAsString()))

                            .findFirst();

            if (pairOpt.isPresent()) {

                return clean(
                        pairOpt.get()
                                .getValue()
                                .toString());
            }
        }

        return null;
    }

    // =========================================================
    // ✅ NORMALIZE
    // =========================================================
    private String normalizePath(
            String base,
            String method) {

        String left =
                base == null
                        ? ""
                        : base.trim();

        String right =
                method == null
                        ? ""
                        : method.trim();

        if (!left.startsWith("/")) {

            left = "/" + left;
        }

        if (!right.startsWith("/")) {

            right = "/" + right;
        }

        String path =
                (left + right)
                        .replaceAll("/+", "/");

        if (path.length() > 1
                && path.endsWith("/")) {

            path =
                    path.substring(
                            0,
                            path.length() - 1);
        }

        return path;
    }

    // =========================================================
    // ✅ CLEAN
    // =========================================================
    private String clean(
            String value) {

        if (value == null) {

            return null;
        }

        return value
                .replace("\"", "")
                .trim();
    }
}