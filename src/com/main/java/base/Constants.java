package com.main.java.base;

/**
 * @author jgohil
 *
 */
public class Constants {
	
	protected static String baseURI = "https://newsapi.org/";
	protected static String apiVersion = "v1";
	
	protected static String sourceURIPath = "/sources";
	protected static String articlesURIPath = "/articles";
	
	protected static String apiKey = "***REMOVED***";
	
	protected static String sourceDefault = "cnn";
	
	protected static String categoryDefault = "general";
	protected static String[] categorySupported = {"business","entertainment","gaming","general","music","politics","science-and-nature","sport","technology"};
	
	protected static String sortByDefault = "latest";
	protected static String[] sortbySupported = {"top","latest","popular"};
	
	protected static String languageDefault = "en";
	protected static String[] languageSupported = {"en","de","fr"};
	
	protected static String countryDefault = "us";
	protected static String[] countrySupported = {"au,de,gb,in,it,us","de","gb","in","it","us"};
}
