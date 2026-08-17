package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ExamplePathResolver {

	// =========================================================
	// ✅ SET NESTED VALUE
	// =========================================================
	@SuppressWarnings("unchecked")
	public void setNestedValue(Map<String, Object> root, String path, Object value) {

		if (root == null || path == null || path.isBlank()) {

			return;
		}

		String[] parts = path.split("\\.");

		Object current = root;

		for (int i = 0; i < parts.length; i++) {

			String part = parts[i];

			boolean last = i == parts.length - 1;

			// =================================================
			// ✅ ARRAY
			// =================================================
			if (part.contains("[") && part.contains("]")) {

				String field = part.substring(0, part.indexOf("["));

				int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

				Map<String, Object> currentMap = (Map<String, Object>) current;

				Object listObj = currentMap.get(field);

				// ✅ crear lista si no existe
				if (!(listObj instanceof List)) {

					listObj = new ArrayList<>();

					currentMap.put(field, listObj);
				}

				List<Object> list = (List<Object>) listObj;

				if (!(list instanceof ArrayList)) {

				    list = new ArrayList<>(list);

				    currentMap.put(field, list);
				}

				while (list.size() <= index) {

				    list.add(new LinkedHashMap<String, Object>());
				}

				// ✅ último nodo
				if (last) {

					list.set(index, value);
					return;
				}

				Object next = list.get(index);

				// ✅ asegurar map interno
				if (!(next instanceof Map)) {

					next = new LinkedHashMap<String, Object>();

					list.set(index, next);
				}

				current = next;

				continue;
			}

			// =================================================
			// ✅ NORMAL OBJECT
			// =================================================
			Map<String, Object> currentMap = (Map<String, Object>) current;

			// ✅ último nodo
			if (last) {

				currentMap.put(part, value);

				return;
			}

			Object next = currentMap.get(part);

			// ✅ asegurar map interno
			if (!(next instanceof Map)) {

				next = new LinkedHashMap<String, Object>();

				currentMap.put(part, next);
			}

			current = next;
		}
	}

	// =========================================================
	// ✅ PARSE VALUE
	// =========================================================
	public Object parseValue(String key, String value) {

		// =====================================================
		// ✅ NULL/VACÍO
		// =====================================================
		if (value == null || value.isEmpty()) {

			return "";
		}

		String lower = key == null ? "" : key.toLowerCase();

		// =====================================================
		// ✅ STRINGS CRÍTICOS
		// ✅ preservar ceros a la izquierda
		// =====================================================
		if (lower.contains("cuenta") || lower.contains("cta") || lower.contains("tarj") || lower.contains("telefono")
				|| lower.contains("telf") || lower.contains("cel") || lower.contains("identificador")
				|| lower.contains("rif") || lower.contains("codpais") || lower.contains("subcanal")
				|| lower.contains("codterm") || lower.contains("codtrans") || lower.contains("serial")
				|| lower.contains("terminal") || lower.contains("referencia") || lower.contains("nro")
				|| value.startsWith("0")) {

			return value;
		}

		// =====================================================
		// ✅ INTEGER / LONG
		// =====================================================
		if (value.matches("-?\\d+")) {

			// ✅ preservar números largos exactamente como vienen en rules.xml
			if (value.matches("-?\\d+") && value.length() > 15) {
			    return value;
			}

			try {

				return Integer.parseInt(value);

			} catch (Exception e) {

				try {

					return Long.parseLong(value);

				} catch (Exception ex) {

					return value;
				}
			}
		}

		// =====================================================
		// ✅ DECIMAL
		// =====================================================
		if (value.matches("-?\\d+\\.\\d+")) {

			try {

				return Double.parseDouble(value);

			} catch (Exception e) {

				return value;
			}
		}

		// =====================================================
		// ✅ BOOLEAN
		// =====================================================
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {

			return Boolean.parseBoolean(value);
		}

		// =====================================================
		// ✅ DEFAULT STRING
		// =====================================================
		return value;
	}
}