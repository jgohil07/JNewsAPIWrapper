package com.main.java.sources;

import java.util.List;

import com.main.java.core.NewsException;

/**
 * A place news comes from. Implementations throw on any failure; they never return partial data silently.
 *
 * @author jgohil
 */
public interface NewsSource {

	SourceConfig config();

	/**
	 * @return every item the source currently lists, unvalidated
	 * @throws NewsException on transport, HTTP, format or API errors
	 */
	List<RawItem> fetch();

	/** @return why this source cannot run (e.g. a missing API key), or null when it can */
	default String unavailableReason() {
		return null;
	}

	/** @return true when an empty result is a valid answer (a search), not a broken feed */
	default boolean emptyIsHealthy() {
		return false;
	}

	/** @return a non-fatal remark about the last {@link #fetch()} (e.g. degraded enrichment), or null */
	default String note() {
		return null;
	}
}
