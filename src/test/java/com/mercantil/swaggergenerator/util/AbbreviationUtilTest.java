package com.mercantil.swaggergenerator.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbbreviationUtilTest {

	@Test
	void acronymRunIsTreatedAsOneTokenNotOneLetterPerToken() {

		// ✅ "NIT" no debe partirse en "n","i","t" individuales; "Cliente" se
		// abrevia a "clte" según abbreviations.txt (cliente=clte).
		assertThat(AbbreviationUtil.normalizeJsonKey("NITCliente")).isEqualTo("nitClte");
	}

	@Test
	void trailingAcronymIsKeptTogether() {

		assertThat(AbbreviationUtil.normalizeJsonKey("ClienteDTO")).isEqualTo("clteDto");
	}

	@Test
	void plainCamelCaseStillNormalizesPerWord() {

		assertThat(AbbreviationUtil.normalizeJsonKey("tipoIdClte")).isEqualTo("tipoIdClte");
	}
}
