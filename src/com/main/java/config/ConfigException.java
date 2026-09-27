package com.main.java.config;

/**
 * Missing or malformed configuration. Messages name the setting, never its value.
 *
 * @author jgohil
 */
public class ConfigException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ConfigException(String message) {
		super(message);
	}
}
