package com.main.java.aggreators;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.models.Sources;
import com.main.java.base.Constants;
import com.main.java.utils.JsonFactory;

/**
 * @author jgohil
 *
 */
public class SourcesAggreator extends Constants {
	
	public SourcesAggreator() {}

	/**
	 * 
	 * @param uriSource
	 * @param apiKey
	 * @param languageDefault
	 * @param countryDefault
	 * @return
	 */
	public Sources getAllSources(String uriSource, String apiKey, String language, String country) {
		String response = null;
		Sources sc = null;
		JsonFactory jf = new JsonFactory();
		response = jf.httpGetRequestJson(uriSource, "apiKey=" + apiKey + "&language=" + language + "&country=" + country);
		ObjectMapper mapper = new ObjectMapper();
		try {
			sc = mapper.readValue(response, Sources.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sc;
	}
	
	/**
	 * Picks up required parameters directly from the constants defined
	 * 
	 */
	public Sources getAllSources() {
		String response = null;
		Sources sc = null;
		JsonFactory jf = new JsonFactory();
		response = jf.httpGetRequestJson(baseURI + apiVersion + sourceURIPath, "apiKey=" + apiKey + "&" + languageDefault + "&" + countryDefault);
		ObjectMapper mapper = new ObjectMapper();
		try {
			sc = mapper.readValue(response, Sources.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sc;
	}
	
	/**
	 * 
	 * @param uriSource
	 * @param apiKey
	 * @param languageDefault
	 * @param countryDefault
	 * @param categoryDefault
	 * @return
	 */
	public Sources getSourcesForCategory(String uriSource, String apiKey, String language, String country, String category) {
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(uriSource, "apiKey=" + apiKey + "&language=" + language + "&country=" + country + "&category=" + category);
		ObjectMapper mapper = new ObjectMapper();
		Sources sc = null;
		try {
			sc = mapper.readValue(response, Sources.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sc;
	}
	
	/**
	 * 
	 * @param categoryDefault
	 * @return
	 */
	public Sources getSourcesForCategory(String category) {
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(baseURI + apiVersion + sourceURIPath, "apiKey=" + apiKey + "&category=" + category + "&" + languageDefault + "&" + countryDefault);
		ObjectMapper mapper = new ObjectMapper();
		Sources sc = null;
		try {
			sc = mapper.readValue(response, Sources.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sc;
	}
	
	/**
	 * Picks up required parameters directly from the constants file
	 * 
	 * @return
	 */
	public Sources getSourcesForCategory()
	{
		JsonFactory jf = new JsonFactory();
		String response = jf.httpGetRequestJson(baseURI + apiVersion + sourceURIPath, "apiKey=" + apiKey + "&" + categoryDefault + "&" + languageDefault + "&" + countryDefault);
		ObjectMapper mapper = new ObjectMapper();
		Sources sc = null;
		try {
			sc = mapper.readValue(response, Sources.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sc;
	}

}
