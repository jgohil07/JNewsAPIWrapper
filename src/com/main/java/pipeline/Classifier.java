package com.main.java.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.core.NewsException;
import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.Topic;

/**
 * Adds topics from keyword rules ({@code resources/taxonomy.json}) to items in the categories a rule applies to. A
 * topic the source's own section already gave is kept as {@code by: feed}; rule topics are marked {@code by: rule} so
 * consumers can weigh them.
 *
 * @author jgohil
 */
public final class Classifier {

	/** One rule: any pattern matching the title or summary adds the topic. */
	public record Rule(String topic, String label, List<String> appliesTo, List<String> patterns) {}

	@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "_comment" })
	record Document(List<Rule> rules) {}

	private record Compiled(String topic, Set<String> appliesTo, Pattern pattern) {}

	private final List<Compiled> rules;
	private final Map<String, String> labels;

	private Classifier(List<Rule> rules) {
		List<Compiled> compiled = new ArrayList<>();
		Map<String, String> l = new LinkedHashMap<>();
		for (Rule r : rules) {
			if (r.topic() == null || !r.topic().matches("[a-z0-9-]+") || r.patterns() == null || r.patterns().isEmpty()) {
				throw new NewsException("taxonomy.json: invalid rule " + r.topic());
			}
			String joined = String.join("|", r.patterns().stream().map(p -> "(?:" + p + ")").toList());
			compiled.add(new Compiled(r.topic(), Set.copyOf(r.appliesTo()), Pattern.compile(joined,
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
			l.put(r.topic(), r.label() == null ? r.topic() : r.label());
		}
		this.rules = List.copyOf(compiled);
		this.labels = Map.copyOf(l);
	}

	public static Classifier load() {
		try (InputStream in = Classifier.class.getResourceAsStream("/taxonomy.json")) {
			Objects.requireNonNull(in, "taxonomy.json missing from the classpath");
			ObjectMapper m = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			return new Classifier(m.readValue(in, Document.class).rules());
		} catch (IOException e) {
			throw new NewsException("taxonomy.json is invalid: " + e.getMessage(), e);
		}
	}

	public static Classifier of(List<Rule> rules) {
		return new Classifier(rules);
	}

	/** Topic id to display label. */
	public Map<String, String> labels() {
		return labels;
	}

	public NewsItem classify(NewsItem item) {
		Map<String, Topic> topics = new LinkedHashMap<>();
		item.topics().forEach(t -> topics.put(t.id(), t));
		String text = item.title() + "\n" + (item.summary() == null ? "" : item.summary());
		for (Compiled r : rules) {
			if (topics.containsKey(r.topic())) {
				continue;
			}
			if (item.categories().stream().noneMatch(r.appliesTo()::contains)) {
				continue;
			}
			if (r.pattern().matcher(text).find()) {
				topics.put(r.topic(), new Topic(r.topic(), "rule"));
			}
		}
		return topics.size() == item.topics().size() ? item : item.withTopics(List.copyOf(topics.values()));
	}
}
