package com.main.java.core;

/**
 * An HTTP request failed: transport error, timeout, non-2xx status or an oversized body. The message carries the URL
 * without its query string, so credentials passed as parameters never leak into logs.
 *
 * @author jgohil
 */
public class FetchException extends NewsException {

	private static final long serialVersionUID = 1L;

	/** HTTP status, or -1 when no response was received. */
	private final int status;

	public FetchException(String message, int status) {
		super(message);
		this.status = status;
	}

	public FetchException(String message, Throwable cause) {
		super(message, cause);
		this.status = -1;
	}

	public int getStatus() {
		return status;
	}
}
