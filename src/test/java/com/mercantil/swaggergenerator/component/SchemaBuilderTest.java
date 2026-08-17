package com.mercantil.swaggergenerator.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

class SchemaBuilderTest {

	private final ClassIndexer classIndexer = new ClassIndexer();
	private final SchemaBuilder schemaBuilder = new SchemaBuilder(new TypeUtil(), new ParserUtil(), classIndexer);

	@Test
	void nestedClienteDtoSchemaIsFullyBuiltNotJustReferenced() {

		CompilationUnit cu = StaticJavaParser.parse(

				"class Pedido { private ClienteDTO cliente; }\n" + "class ClienteDTO { private String nombre; }");

		List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

		ClassOrInterfaceDeclaration pedido = classes.stream().filter(c -> c.getNameAsString().equals("Pedido"))
				.findFirst().orElseThrow();

		ClassOrInterfaceDeclaration clienteDto = classes.stream()
				.filter(c -> c.getNameAsString().equals("ClienteDTO")).findFirst().orElseThrow();

		classIndexer.register(pedido);
		classIndexer.register(clienteDto);

		Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();
		schemaBuilder.setSchemaMap(schemaMap);

		schemaBuilder.build(pedido);

		assertThat(schemaMap).containsKey("ClienteDTO");

		@SuppressWarnings("unchecked")
		Map<String, Object> clienteProperties = (Map<String, Object>) schemaMap.get("ClienteDTO").get("properties");

		assertThat(clienteProperties).isNotEmpty();
	}
}
