package com.main.java.core;

/**
 * Base class for every failure raised by JNewsAPIWrapper. Unchecked, so the original method signatures stay unchanged,
 * but always thrown instead of returning {@code null} or partial data.
 *
 * @author jgohil
 */
public class NewsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NewsException(String message) {
		super(message);
	}

	public NewsException(String message, Throwable cause) {
		super(message, cause);
	}
}
