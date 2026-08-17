package com.mercantil.swaggergenerator.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleEngineTest {

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
	void suffixMatchDoesNotCrossUnrelatedFieldsEndingWithTheSameRawCharacters() throws IOException {

		// ✅ la regla "id" solo debe aplicar a rutas cuyo ÚLTIMO SEGMENTO sea
		// exactamente "id" (p.ej. "cliente.id"), no a cualquier campo que
		// termine con esos caracteres en crudo como "productoId".
		RuleEngine ruleEngine = loadRuleEngineFromXml(

				"<rules>" + "<api path=\"/test/endpoint\">" + "<request>"
						+ "<field name=\"id\" value=\"VALOR-CLIENTE-ID\" />" + "</request>" + "</api>" + "</rules>");

		assertThat(ruleEngine.getRequestValue("/test/endpoint", "cliente.id")).isEqualTo("VALOR-CLIENTE-ID");

		assertThat(ruleEngine.getRequestValue("/test/endpoint", "productoId")).isNull();
	}

	@Test
	void suffixMatchPrefersTheMoreSpecificRule() throws IOException {

		RuleEngine ruleEngine = loadRuleEngineFromXml(

				"<rules>" + "<api path=\"/test/endpoint\">" + "<request>"
						+ "<field name=\"id\" value=\"VALOR-GENERICO\" />"
						+ "<field name=\"producto.id\" value=\"VALOR-PRODUCTO\" />" + "</request>" + "</api>"
						+ "</rules>");

		assertThat(ruleEngine.getRequestValue("/test/endpoint", "detalle.producto.id")).isEqualTo("VALOR-PRODUCTO");
	}
}
