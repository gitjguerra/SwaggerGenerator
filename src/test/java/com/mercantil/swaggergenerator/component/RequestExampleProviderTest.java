package com.mercantil.swaggergenerator.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mercantil.swaggergenerator.util.ParserUtil;

class RequestExampleProviderTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void clearSystemProperty() {
		System.clearProperty("pathRules");
	}

	private RuleEngine loadRuleEngineFromXml(String xml) throws IOException {

		Path rulesFile = tempDir.resolve("rules.xml");
		Files.writeString(rulesFile, xml);

		System.setProperty("pathRules", rulesFile.toAbsolutePath().toString());

		RuleEngine ruleEngine = new RuleEngine();
		ruleEngine.loadRules();

		return ruleEngine;
	}

	@Test
	@SuppressWarnings("unchecked")
	void buildsOneArrayElementPerIndexedRule() throws IOException {

		RuleEngine ruleEngine = loadRuleEngineFromXml("<rules>" + "<api path=\"/test/endpoint\">" + "<request>"
				+ "<field name=\"detalle[0].monto\" value=\"100\" />"
				+ "<field name=\"detalle[1].monto\" value=\"200\" />" + "</request>" + "</api>" + "</rules>");

		RequestExampleProvider provider = new RequestExampleProvider(ruleEngine, new ClassIndexer(),
				new ExamplePathResolver(), new ParserUtil());

		Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

		schemaMap.put("BodyEntradaTest", Map.of("type", "object", "properties",
				Map.of("detalle", Map.of("type", "array", "items", Map.of("$ref", "#/components/schemas/Detalle")))));

		schemaMap.put("Detalle",
				Map.of("type", "object", "properties", Map.of("monto", Map.of("type", "integer"))));

		Object example = provider.build("/test/endpoint", "BodyEntradaTest", schemaMap, new LinkedHashMap<>());

		Map<String, Object> exampleMap = (Map<String, Object>) example;

		List<Object> detalle = (List<Object>) exampleMap.get("detalle");

		// ✅ antes solo se generaba un único elemento (índice [0]) sin importar
		// cuántos índices tuviera definidos rules.xml
		assertThat(detalle).hasSize(2);

		assertThat(((Map<String, Object>) detalle.get(0)).get("monto")).isEqualTo(100);
		assertThat(((Map<String, Object>) detalle.get(1)).get("monto")).isEqualTo(200);
	}

	@Test
	@SuppressWarnings("unchecked")
	void fallsBackToASingleElementWhenNoIndexedRulesExist() throws IOException {

		RuleEngine ruleEngine = loadRuleEngineFromXml("<rules></rules>");

		RequestExampleProvider provider = new RequestExampleProvider(ruleEngine, new ClassIndexer(),
				new ExamplePathResolver(), new ParserUtil());

		Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

		schemaMap.put("BodyEntradaTest", Map.of("type", "object", "properties",
				Map.of("detalle", Map.of("type", "array", "items", Map.of("$ref", "#/components/schemas/Detalle")))));

		schemaMap.put("Detalle",
				Map.of("type", "object", "properties", Map.of("monto", Map.of("type", "integer"))));

		Object example = provider.build("/test/endpoint", "BodyEntradaTest", schemaMap, new LinkedHashMap<>());

		Map<String, Object> exampleMap = (Map<String, Object>) example;

		List<Object> detalle = (List<Object>) exampleMap.get("detalle");

		assertThat(detalle).hasSize(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	void appliesRuleDefinedPathsThatDoNotExistInTheDtoSchema() throws IOException {

		// ✅ simula lo que hoy solo lograban los SpecialRequestHandler: una regla
		// en rules.xml que impone una estructura anidada/renombrada que no
		// existe literalmente en el schema del DTO (aquí, el schema no tiene
		// NINGÚN campo declarado).
		RuleEngine ruleEngine = loadRuleEngineFromXml("<rules>" + "<api path=\"/test/endpoint\">" + "<request>"
				+ "<field name=\"wrapperNoDeclaradoEnElDto.subCampo\" value=\"42\" />" + "</request>" + "</api>"
				+ "</rules>");

		RequestExampleProvider provider = new RequestExampleProvider(ruleEngine, new ClassIndexer(),
				new ExamplePathResolver(), new ParserUtil());

		Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

		schemaMap.put("BodyEntradaTest", Map.of("type", "object", "properties", Map.of()));

		Object example = provider.build("/test/endpoint", "BodyEntradaTest", schemaMap, new LinkedHashMap<>());

		Map<String, Object> exampleMap = (Map<String, Object>) example;

		Map<String, Object> wrapper = (Map<String, Object>) exampleMap.get("wrapperNoDeclaradoEnElDto");

		assertThat(wrapper).isNotNull();
		assertThat(wrapper.get("subCampo")).isEqualTo(42);
	}
}
