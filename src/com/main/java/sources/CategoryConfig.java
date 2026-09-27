package com.main.java.sources;

/**
 * A site section.
 *
 * @param minHealthy fewest healthy sources the section needs; below that, fallbacks are tried and the run fails
 * @param required   the site build fails when this section is below {@code minHealthy}
 * @param group      {@code india-finance}, {@code india} or {@code world}, for the CLI and the UI rail
 * @author jgohil
 */
public record CategoryConfig(String id, String label, String group, int minHealthy, boolean required) {}
