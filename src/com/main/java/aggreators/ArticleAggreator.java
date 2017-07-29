package com.main.java.aggreators;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.models.Articles;
import com.main.java.base.Constants;
import com.main.java.utils.JsonFactory;

/**
 * @author jgohil
 *
 */
public class ArticleAggreator extends Constants {
	
	/**
	 * 
	 * @param uriSource
	 * @param apiKey
	 * @param sortByDefault
	 * @param sourceDefault
	 * @return
	 */
	public Articles getArticlesForSource(String uriSource, String apiKey, String sortBy, String source) {
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(uriSource, "apiKey=" + apiKey + "&sortBy=" + sortBy + "&source=" + source);
		ObjectMapper mapper = new ObjectMapper();
		Articles ac = null;
		try {
			ac = mapper.readValue(response, Articles.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ac;
	}
	
	/**
	 * 
	 * @param sortByDefault
	 * @param sourceDefault
	 * @return
	 */
	public Articles getArticlesForSource(String sortBy, String source) {
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(baseURI + apiVersion + articlesURIPath, "apiKey=" + apiKey + "&sortBy=" + sortBy + "&source=" + source);
		ObjectMapper mapper = new ObjectMapper();
		Articles ac = null;
		try {
			ac = mapper.readValue(response, Articles.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ac;
	}
	
	/**
	 * 
	 * @param sourceDefault
	 * @return
	 */
	public Articles getArticlesForSource(String source) {
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(baseURI + apiVersion + articlesURIPath, "apiKey=" + apiKey + "&" + sortByDefault + "&source=" + source);
		ObjectMapper mapper = new ObjectMapper();
		Articles ac = null;
		try {
			ac = mapper.readValue(response, Articles.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ac;
	}
	
	/**
	 * Picks up required parameters directly from the constants file
	 * 
	 * @return
	 */
	public Articles getArticlesForSource() {
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(baseURI + apiVersion + articlesURIPath, "apiKey=" + apiKey + "&" + sortByDefault + "&" + sourceDefault);
		ObjectMapper mapper = new ObjectMapper();
		Articles ac = null;
		try {
			ac = mapper.readValue(response, Articles.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ac;
	}
}


