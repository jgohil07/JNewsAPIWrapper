package com.main.java.cli;

/**
 * Process exit codes (part of the CLI contract, docs/cli-contract.md).
 *
 * @author jgohil
 */
public final class ExitCodes {
	/** Every requested source healthy; data on stdout. */
	public static final int OK = 0;
	/** Nothing trustworthy to output (a required category below its minimum, or a fatal error); stdout empty. */
	public static final int FAILED = 1;
	/** Usage or configuration error; stdout empty. */
	public static final int USAGE = 2;
	/** Data on stdout, but some sources failed or are stale (see "sources" / stderr). */
	public static final int PARTIAL = 3;

	private ExitCodes() {}
}
