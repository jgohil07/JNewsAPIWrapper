package com.main.java.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The one JSON mapper for machine output: snake_case names, ISO-8601 UTC timestamps, nulls written explicitly so the
 * schema is stable.
 *
 * @author jgohil
 */
public final class Json {

	public static final ObjectMapper OUTPUT = JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.build();

	private Json() {}
}
