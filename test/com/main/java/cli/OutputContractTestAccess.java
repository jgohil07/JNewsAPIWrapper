package com.main.java.cli;

import com.main.java.pipeline.Aggregator;

/** Shares the contract test's sample result with tests in other packages. */
public final class OutputContractTestAccess {
	private OutputContractTestAccess() {}

	public static Aggregator.Result result(Aggregator.Status status) {
		return OutputContractTest.result(status);
	}
}
