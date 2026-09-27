package com.main.java.base;

import com.main.java.config.Env;

/**
 * @author jgohil
 *
 */
public class Constants {
	
	protected static String baseURI = "https://newsapi.org/";
	protected static String apiVersion = "v1";
	
	protected static String sourceURIPath = "/sources";
	protected static String articlesURIPath = "/articles";
	
	/** News API key, read from the NEWSAPI_KEY environment variable or .env (see .env.example); null when unset. */
	protected static String apiKey = Env.get("NEWSAPI_KEY").orElse(null);
	
	protected static String sourceDefault = "cnn";
	
	protected static String categoryDefault = "general";
	// Supported values below were verified against the live v1 API on 2026-09-27. Anything else is rejected, because
	// v1 answers unknown values with a silent empty list (categories) or different data (sortBy).
	protected static String[] categorySupported = {"business","entertainment","general","science","sports","technology"};
	
	protected static String sortByDefault = "top";
	protected static String[] sortbySupported = {"top"};
	
	protected static String languageDefault = "en";
	protected static String[] languageSupported = {"en","de"};
	
	protected static String countryDefault = "us";
	protected static String[] countrySupported = {"au","de","gb","in","it","us"};
}
