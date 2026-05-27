package com.mercantil.swaggergenerator.util;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;

@Component
public class ParserUtil {

    public String resolveJsonName(FieldDeclaration field, String defaultName) {

        Optional<AnnotationExpr> opt = field.getAnnotationByName("JsonProperty");

        if (opt.isPresent()) {

            AnnotationExpr ann = opt.get();

            try {

                // ✅ @JsonProperty("name")
                if (ann.isSingleMemberAnnotationExpr()) {
                    Expression value =
                        ann.asSingleMemberAnnotationExpr().getMemberValue();

                    return value.asStringLiteralExpr().asString();
                }

                // ✅ @JsonProperty(value = "name")
                if (ann.isNormalAnnotationExpr()) {

                    for (MemberValuePair pair :
                            ann.asNormalAnnotationExpr().getPairs()) {

                        if ("value".equals(pair.getNameAsString())) {
                            return pair.getValue()
                                       .asStringLiteralExpr()
                                       .asString();
                        }
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return defaultName;
    }

    public String extractGeneric(String input) {

        if (input == null) return null;

        int start = input.indexOf("<");
        int end = input.lastIndexOf(">");

        if (start == -1 || end == -1 || end <= start) {
            return input;
        }

        String inner = input.substring(start + 1, end);

        if (inner.contains("<")) {
            return extractGeneric(inner);
        }

        return inner.trim();
    }

    public String resolveFinalType(String type) {

        if (type == null) return null;

        // ✅ quita paquete
        if (type.contains(".")) {
            type = type.substring(type.lastIndexOf(".") + 1);
        }

        // ✅ maneja generics aún presentes
        if (type.contains("<")) {
            return extractGeneric(type);
        }

        return type;
    }

    // 🔥 EXTRA PRO (muy útil para otros builders)
    public boolean isList(String type) {
        return type != null && type.contains("List<");
    }

    public String unwrapOptional(String type) {
        if (type != null && type.startsWith("Optional<")) {
            return extractGeneric(type);
        }
        return type;
    }
}