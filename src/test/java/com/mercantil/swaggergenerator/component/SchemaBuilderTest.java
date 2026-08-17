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

	@Test
	@SuppressWarnings("unchecked")
	void mapOfListPreservesBothTheObjectAndArrayShape() {

		CompilationUnit cu = StaticJavaParser.parse("import java.util.List;\nimport java.util.Map;\n"
				+ "class Pedido { private Map<String, List<Cuenta>> cuentasPorTipo; }\n"
				+ "class Cuenta { private String numero; }");

		List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

		ClassOrInterfaceDeclaration pedido = classByName(classes, "Pedido");
		ClassOrInterfaceDeclaration cuenta = classByName(classes, "Cuenta");

		classIndexer.register(pedido);
		classIndexer.register(cuenta);

		schemaBuilder.setSchemaMap(new LinkedHashMap<>());

		Map<String, Object> pedidoSchema = schemaBuilder.build(pedido);

		Map<String, Object> props = (Map<String, Object>) pedidoSchema.get("properties");

		assertThat(props).hasSize(1);

		Map<String, Object> fieldSchema = (Map<String, Object>) props.values().iterator().next();

		// ✅ el Map exterior no debe perderse (antes se clasificaba como "array" a secas)
		assertThat(fieldSchema.get("type")).isEqualTo("object");

		Map<String, Object> additionalProperties = (Map<String, Object>) fieldSchema.get("additionalProperties");

		assertThat(additionalProperties).isNotNull();
		assertThat(additionalProperties.get("type")).isEqualTo("array");

		Map<String, Object> items = (Map<String, Object>) additionalProperties.get("items");

		assertThat(items.get("$ref")).isEqualTo("#/components/schemas/Cuenta");
	}

	@Test
	@SuppressWarnings("unchecked")
	void listOfMapIsNotSilentlyDropped() {

		CompilationUnit cu = StaticJavaParser.parse("import java.util.List;\nimport java.util.Map;\n"
				+ "class Pedido { private List<Map<String, Cuenta>> registros; }\n"
				+ "class Cuenta { private String numero; }");

		List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

		ClassOrInterfaceDeclaration pedido = classByName(classes, "Pedido");
		ClassOrInterfaceDeclaration cuenta = classByName(classes, "Cuenta");

		classIndexer.register(pedido);
		classIndexer.register(cuenta);

		schemaBuilder.setSchemaMap(new LinkedHashMap<>());

		Map<String, Object> pedidoSchema = schemaBuilder.build(pedido);

		Map<String, Object> props = (Map<String, Object>) pedidoSchema.get("properties");

		// ✅ antes el campo desaparecía por completo de "properties"
		assertThat(props).hasSize(1);

		Map<String, Object> fieldSchema = (Map<String, Object>) props.values().iterator().next();

		assertThat(fieldSchema.get("type")).isEqualTo("array");

		Map<String, Object> items = (Map<String, Object>) fieldSchema.get("items");

		assertThat(items.get("type")).isEqualTo("object");

		Map<String, Object> itemsAdditionalProperties = (Map<String, Object>) items.get("additionalProperties");

		assertThat(itemsAdditionalProperties.get("$ref")).isEqualTo("#/components/schemas/Cuenta");
	}

	@Test
	@SuppressWarnings("unchecked")
	void jsonCreatorMapParameterKeepsObjectWrapperShape() {

		CompilationUnit cu = StaticJavaParser.parse("import java.util.Map;\n" + "class BodyEntradaConsulta {\n"
				+ "  private final Map<String, Cuenta> datos;\n" + "  @JsonCreator\n"
				+ "  public BodyEntradaConsulta(@JsonProperty(\"datos\") Map<String, Cuenta> datos) { this.datos = datos; }\n"
				+ "}\n" + "class Cuenta { private String numero; }");

		List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

		ClassOrInterfaceDeclaration bodyEntrada = classByName(classes, "BodyEntradaConsulta");
		ClassOrInterfaceDeclaration cuenta = classByName(classes, "Cuenta");

		classIndexer.register(bodyEntrada);
		classIndexer.register(cuenta);

		schemaBuilder.setSchemaMap(new LinkedHashMap<>());

		Map<String, Object> schema = schemaBuilder.build(bodyEntrada);

		Map<String, Object> props = (Map<String, Object>) schema.get("properties");

		Map<String, Object> datosSchema = (Map<String, Object>) props.get("datos");

		// ✅ antes se perdía el wrapper "object" y quedaba solo el $ref a Cuenta
		assertThat(datosSchema.get("type")).isEqualTo("object");

		Map<String, Object> additionalProperties = (Map<String, Object>) datosSchema.get("additionalProperties");

		assertThat(additionalProperties.get("$ref")).isEqualTo("#/components/schemas/Cuenta");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resolvesTheCorrectClassWhenSimpleNameCollidesAcrossPackages() {

		// ✅ dos clases "Address" reales en paquetes distintos, con campos
		// distintos -- antes de la resolución con contexto, cualquier referencia
		// a "Address" habría resuelto a la última registrada, sin importar
		// cuál import tenía realmente cada archivo.
		ClassOrInterfaceDeclaration customerAddress = classByName(StaticJavaParser
				.parse("package com.foo.customer;\n" + "class Address { private String zonaunica; }\n")
				.findAll(ClassOrInterfaceDeclaration.class), "Address");

		ClassOrInterfaceDeclaration branchAddress = classByName(StaticJavaParser
				.parse("package com.foo.branch;\n" + "class Address { private String marcadorx; }\n")
				.findAll(ClassOrInterfaceDeclaration.class), "Address");

		ClassOrInterfaceDeclaration pedido = classByName(StaticJavaParser
				.parse("package com.foo.pedidos;\n" + "import com.foo.customer.Address;\n"
						+ "class Pedido { private Address direccion; }\n")
				.findAll(ClassOrInterfaceDeclaration.class), "Pedido");

		classIndexer.register(customerAddress);
		classIndexer.register(branchAddress);
		classIndexer.register(pedido);

		Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();
		schemaBuilder.setSchemaMap(schemaMap);

		Map<String, Object> pedidoSchema = schemaBuilder.build(pedido);

		Map<String, Object> props = (Map<String, Object>) pedidoSchema.get("properties");

		assertThat(props).hasSize(1);

		Map<String, Object> direccionSchema = (Map<String, Object>) props.values().iterator().next();

		assertThat(direccionSchema.get("$ref")).isEqualTo("#/components/schemas/Address");

		// ✅ la clave correcta: el schema generado bajo "Address" debe tener el
		// campo de com.foo.customer.Address (zonaunica), NO el de com.foo.branch
		// (marcadorx), porque Pedido importa explícitamente com.foo.customer.Address.
		Map<String, Object> addressProperties = (Map<String, Object>) schemaMap.get("Address").get("properties");

		assertThat(addressProperties).containsKey("zonaunica");
		assertThat(addressProperties).doesNotContainKey("marcadorx");
	}

	private ClassOrInterfaceDeclaration classByName(List<ClassOrInterfaceDeclaration> classes, String name) {

		return classes.stream().filter(c -> c.getNameAsString().equals(name)).findFirst().orElseThrow();
	}
}
