package com.mercantil.swaggergenerator.util;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class HttpMethodUtil {

    public Map<String, String> detect(MethodDeclaration method) {

        // ✅ POST
        if (method.getAnnotationByName("PostMapping").isPresent()) {
            return Map.of(
                "method", "post",
                "path", extract(method, "PostMapping")
            );
        }

        // ✅ GET
        if (method.getAnnotationByName("GetMapping").isPresent()) {
            return Map.of(
                "method", "get",
                "path", extract(method, "GetMapping")
            );
        }

        // ✅ PUT
        if (method.getAnnotationByName("PutMapping").isPresent()) {
            return Map.of(
                "method", "put",
                "path", extract(method, "PutMapping")
            );
        }

        // ✅ DELETE
        if (method.getAnnotationByName("DeleteMapping").isPresent()) {
            return Map.of(
                "method", "delete",
                "path", extract(method, "DeleteMapping")
            );
        }

        // ✅ PATCH
        if (method.getAnnotationByName("PatchMapping").isPresent()) {
            return Map.of(
                "method", "patch",
                "path", extract(method, "PatchMapping")
            );
        }

        // ✅ REQUEST MAPPING
        if (method.getAnnotationByName("RequestMapping").isPresent()) {

            String annotation = method.getAnnotationByName("RequestMapping").get().toString();

            String httpMethod = null;

            if (annotation.contains("RequestMethod.POST")) httpMethod = "post";
            else if (annotation.contains("RequestMethod.GET")) httpMethod = "get";
            else if (annotation.contains("RequestMethod.PUT")) httpMethod = "put";
            else if (annotation.contains("RequestMethod.DELETE")) httpMethod = "delete";

            String path = extract(method, "RequestMapping");

            if (httpMethod != null) {
                return Map.of("method", httpMethod, "path", path);
            }
        }

        return null;
    }

    private String extract(MethodDeclaration method, String name) {
        return method.getAnnotationByName(name)
                .flatMap(a -> a.toString().contains("\"")
                        ? java.util.Optional.of(a.toString().split("\"")[1])
                        : java.util.Optional.empty())
                .orElse("");
    }
}
