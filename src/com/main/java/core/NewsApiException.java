package com.main.java.core;

/**
 * News API answered, but with an error ({@code "status": "error"}) or with data that does not match the request.
 *
 * @author jgohil
 */
public class NewsApiException extends NewsException {

	private static final long serialVersionUID = 1L;

	/** News API error code such as {@code apiKeyInvalid}, or null when the check failed on our side. */
	private final String code;

	public NewsApiException(String code, String message) {
		super(code == null ? message : code + ": " + message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
