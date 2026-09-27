package com.main.java.pipeline;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of one source in one run. Items of a source are used only when its status is {@code ok}.
 *
 * @param status     {@code ok}, {@code stale} (newest item older than the limit), {@code failed} or {@code skipped}
 *                   (not configured, e.g. no API key)
 * @param items      valid items read
 * @param rejected   items dropped by validation
 * @param newestAt   newest valid item, or null
 * @param oldestAt   oldest valid item, or null: the source only covers the span oldestAt..newestAt, so a longer
 *                   {@code --since} window is not fully covered by it
 * @param error      why the source is not ok, or null
 * @param note       non-fatal remark (e.g. symbol list unavailable), or null
 * @author jgohil
 */
public record SourceHealth(String id, String name, String homepage, List<String> categories, boolean fallback,
		String status, int items, int rejected, Instant newestAt, Instant oldestAt, int maxAgeHours, long durationMs, String error,
		String note) {

	public static final String OK = "ok";
	public static final String STALE = "stale";
	public static final String FAILED = "failed";
	public static final String SKIPPED = "skipped";

	public SourceHealth {
		categories = List.copyOf(categories);
	}

	@com.fasterxml.jackson.annotation.JsonIgnore
	public boolean isOk() {
		return OK.equals(status);
	}

	public SourceHealth withDuration(long ms) {
		return new SourceHealth(id, name, homepage, categories, fallback, status, items, rejected, newestAt, oldestAt, maxAgeHours,
				ms, error, note);
	}

	public SourceHealth withNote(String n) {
		return new SourceHealth(id, name, homepage, categories, fallback, status, items, rejected, newestAt, oldestAt, maxAgeHours,
				durationMs, error, n);
	}
}
