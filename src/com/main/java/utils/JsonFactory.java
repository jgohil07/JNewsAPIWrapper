package com.main.java.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 
 * @author jgohil
 *
 */
public class JsonFactory {

	/**
	 * HTTP POST request with URL, Headers and JSON value and any parameters
	 * 
	 * @param url
	 * @param json
	 * @param header
	 * @param parameters
	 * @return
	 */
	public JSONObject postRequestJSON(String url, String json, String[] headerNames, String[] headerValues, String parameters){

		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpPost request = new HttpPost(url);

			if((headerNames.length == headerValues.length) && headerNames.length > 0) {
				for(int i = 0 ; i < headerNames.length ; i++) {
					request.setHeader(headerNames[i], headerValues[i]);
				}
			}

			StringEntity input = new StringEntity(json);
			input.setContentType("application/json;charset=UTF-8");
			request.setEntity(input);
			HttpResponse response = client.execute(request);
			int resp_status = response.getStatusLine().getStatusCode();
			//System.out.println("HTTP Response Code: " + resp_status);
			//System.out.println(JsonFactory.class + "\tURL: " + url + " => "+ "Response Code: " + resp_status);
			if (resp_status==200 || resp_status==403) {
				BufferedReader br = new BufferedReader(
						new InputStreamReader((response.getEntity().getContent())));

				StringBuilder content = new StringBuilder();
				String line;
				while (null != (line = br.readLine())) {
					content.append(line);
				}
				JSONObject myPlainObj = null;
				if(content.toString().isEmpty()) 
					return null;
				myPlainObj = new JSONObject(content.toString());
				//System.out.println(content.toString());

				if(myPlainObj != null) {
					return myPlainObj;
				}
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();

		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * HTTP GET Request with URL, header and parameters.
	 * 
	 * @param url
	 * @param header
	 * @param parameters
	 * @return
	 */
	public List<JSONObject> getRequest(String url, String[] headerNames, String[] headerValues, String parameters) {

		List<JSONObject> jsonObjList = new ArrayList<JSONObject>();
		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = null;

		if(parameters!=null) {
			request = new HttpGet(url+"?"+parameters);
		} else {
			request = new HttpGet(url);
		}

		if((headerNames.length == headerValues.length) && headerNames.length > 0) {
			for(int i = 0 ; i < headerNames.length ; i++) {
				request.setHeader(headerNames[i], headerValues[i]);
			}
		}

		HttpResponse response = null;
		try {
			response = client.execute(request);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println(JsonFactory.class + "\tURL: " + url + " => "+ "Response Code: " + response.getStatusLine().getStatusCode());
		BufferedReader br = null;
		try {
			br = new BufferedReader (new InputStreamReader(response.getEntity().getContent()));
		} catch (IllegalStateException | IOException e) {
			e.printStackTrace();
		}
		StringBuilder content = new StringBuilder();
		String line;
		try {
			while (null != (line = br.readLine())) {
				content.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println(content.toString());
		if(content.toString().startsWith("{")){
			//System.out.println("Content is JSON Object");
			JSONObject myPlainObj = null;
			try {
				myPlainObj = new JSONObject(content.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
			jsonObjList.add(myPlainObj);
			return jsonObjList;
		} else if(content.toString().startsWith("[")){
			JSONArray myArr;
			JSONObject myObj = null;
			try {
				myArr = new JSONArray(content.toString());
				//System.out.println(content.toString());
				for(int i=0;i<myArr.length();i++){
					//System.out.println("Size of Array: " + myArr.length());
					myObj = new JSONObject();
					myObj = myArr.getJSONObject(i);
					jsonObjList.add(myObj);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return jsonObjList;
		} else {
			//System.out.println("Response is null.");
			return null;
		}
	}

	/**
	 * 
	 * @param url
	 * @param header
	 * @return
	 */
	public List<JSONObject> getRequestForRest(String url, String[] headerNames, String[] headerValues, String parameters) {

		List<JSONObject> jsonObjList = new ArrayList<JSONObject>();
		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = null;

		if(parameters!=null) {
			request = new HttpGet(url+"?"+parameters);
		}

		if((headerNames.length == headerValues.length) && headerNames.length > 0) {
			for(int i = 0 ; i < headerNames.length ; i++) {
				request.setHeader(headerNames[i], headerValues[i]);
			}
		}

		HttpResponse response = null;
		try {
			response = client.execute(request);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println("URL: " + url + " => "+ "Response Code: " + response.getStatusLine().getStatusCode());
		BufferedReader br = null;
		try {
			br = new BufferedReader (new InputStreamReader(response.getEntity().getContent()));
		} catch (IllegalStateException | IOException e) {
			e.printStackTrace();
		}
		StringBuilder content = new StringBuilder();
		String line;
		try {
			while (null != (line = br.readLine())) {
				content.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println(content.toString());
		if(content.toString().startsWith("{")){
			//System.out.println("Content is JSON Object");
			JSONObject myPlainObj = null;
			try {
				myPlainObj = new JSONObject(content.toString());
				//System.out.println(content.toString());
				System.out.println(myPlainObj.get("description").toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
			jsonObjList.add(myPlainObj);
			return jsonObjList;
		} else if(content.toString().startsWith("[")){
			JSONArray myArr;
			JSONObject myObj = null;
			try {
				myArr = new JSONArray(content.toString());
				System.out.println(content.toString());
				for(int i=0;i<myArr.length();i++){
					//System.out.println("Size of Array: " + myArr.length());
					myObj = new JSONObject();
					myObj = myArr.getJSONObject(i);
					jsonObjList.add(myObj);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return jsonObjList;
		} else {
			//System.out.println("Response is null.");
			return null;
		}
	}

	/**
	 * 
	 * HTTP POST requests with only URL and header are handled here - Ex Logout API
	 * @param url
	 * @param header
	 * @return
	 */
	public JSONObject postRequestHeader(String url, String[] headerNames, String[] headerValues) {

		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpPost request = new HttpPost(url);

			if((headerNames.length == headerValues.length) && headerNames.length > 0) {
				for(int i = 0 ; i < headerNames.length ; i++) {
					request.setHeader(headerNames[i], headerValues[i]);
				}
			}

			HttpResponse response = client.execute(request);
			BufferedReader br = new BufferedReader(
					new InputStreamReader((response.getEntity().getContent())));

			StringBuilder content = new StringBuilder();
			String line;
			while (null != (line = br.readLine())) {
				content.append(line);
			}
			JSONObject myPlainObj = null;
			if(content.toString().isEmpty()) 
				return null;
			myPlainObj = new JSONObject(content.toString());
			//System.out.println(content.toString());

			if(myPlainObj != null) {
				return myPlainObj;
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();

		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * RESTful Get Method 
	 * 
	 * @param url
	 * @param header
	 * @return
	 */
	public String httpGetRequestJson(String url, String[] headerNames, String[] headerValues, String parameters) {

		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		StringBuilder content = new StringBuilder();

		if(parameters!=null) {
			request = new HttpGet(url + "?" + parameters);
		}
		//System.out.println("URL: " + request.toString());

		if(headerNames!=null && headerNames!=null) {
			if((headerNames.length == headerValues.length) && headerNames.length > 0) {
				for(int i = 0 ; i < headerNames.length ; i++) {
					request.setHeader(headerNames[i], headerValues[i]);
				}
			}
		}

		try {
			response = client.execute(request);
			int resp_status = response.getStatusLine().getStatusCode();
			//System.out.println(JsonFactory.class + "\tURL: " + url + "  ==> "	+ "GET Method Response Code: " + resp_status);

			if (resp_status == 200 || resp_status == 403) {
				BufferedReader br = null;
				br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

				String line;
				while (null != (line = br.readLine())) {
					content.append(line);
				}
				return content.toString();
			} else {
				return null;
			}
		} catch (IOException e) {
			System.out.println(JsonFactory.class + e.getMessage());
			return null;
		}
	}

	/**
	 * HTTP GET method with default headers only
	 * 
	 * @param url
	 * @param parameters
	 * @return
	 */
	public String httpGetRequestJson(String url, String parameters) {

		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		StringBuilder content = new StringBuilder();

		if(parameters!=null) {
			request = new HttpGet(url + "?" + parameters);
		}
		//System.out.println("URL: " + request.toString());

		try {
			response = client.execute(request);
			int resp_status = response.getStatusLine().getStatusCode();
			//System.out.println(JsonFactory.class + "\tURL: " + url + "  ==> "	+ "GET Method Response Code: " + resp_status);

			if (resp_status == 200 || resp_status == 403) {
				BufferedReader br = null;
				br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

				String line;
				while (null != (line = br.readLine())) {
					content.append(line);
				}
				return content.toString();
			} else {
				return null;
			}
		} catch (IOException e) {
			System.out.println(JsonFactory.class + e.getMessage());
			return null;
		}
	}

	/**
	 * RESTFul POST Method
	 * 
	 * @param url
	 * @param query
	 * @param header
	 * @return
	 */
	public String httpPostRequestJson(String url, String query, String[] headerNames, String[] headerValues) {

		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpPost request = new HttpPost(url);

			if((headerNames.length == headerValues.length) && headerNames.length > 0) {
				for(int i = 0 ; i < headerNames.length ; i++) {
					request.setHeader(headerNames[i], headerValues[i]);
				}
			}

			StringEntity input = new StringEntity(query);
			input.setContentType("application/json;charset=UTF-8");
			request.setEntity(input);
			HttpResponse response = client.execute(request);
			int resp_status = response.getStatusLine().getStatusCode();
			//System.out.println(JsonFactory.class + "\tURL: " + url + " => "	+ "POST Method Response Code: " + resp_status);
			if (resp_status == 200 || resp_status == 403) {
				BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

				StringBuilder content = new StringBuilder();
				String line;
				while (null != (line = br.readLine())) {
					content.append(line);
				}
				return content.toString();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * RESTFul DELETE Method
	 * 
	 * @param url
	 * @param header
	 * @return
	 */
	public String httpDeleteRequestJson(String url, String[] headerNames, String[] headerValues) {
		// System.out.println("HTTP Post Request URL: " + url );
		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpDelete request = new HttpDelete(url);

			if((headerNames.length == headerValues.length) && headerNames.length > 0) {
				for(int i = 0 ; i < headerNames.length ; i++) {
					request.setHeader(headerNames[i], headerValues[i]);
				}
			}

			HttpResponse response = client.execute(request);
			int resp_status = response.getStatusLine().getStatusCode();
			//System.out.println(JsonFactory.class + "\tURL: " + url + " => "	+ "DELETE Method Response Code: " + resp_status);
			if (resp_status == 200 || resp_status == 403) {
				BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

				StringBuilder content = new StringBuilder();
				String line;
				while (null != (line = br.readLine())) {
					content.append(line);
				}
				return content.toString();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * RESTFul PUT Method
	 * 
	 * @param url
	 * @param query
	 * @param header
	 * @return
	 */
	public String httpPutRequestJson(String url, String query, String[] headerNames, String[] headerValues, String parameters) {
		// System.out.println("HTTP Post Request URL: " + url );
		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpPut request = new HttpPut(url);

			if((headerNames.length == headerValues.length) && headerNames.length > 0) {
				for(int i = 0 ; i < headerNames.length ; i++) {
					request.setHeader(headerNames[i], headerValues[i]);
				}
			}

			StringEntity input = new StringEntity(query);
			input.setContentType("application/json;charset=UTF-8");
			request.setEntity(input);
			HttpResponse response = client.execute(request);
			int resp_status = response.getStatusLine().getStatusCode();
			//System.out.println(JsonFactory.class + "\tURL: " + url + " => "	+ "PUT Method Response Code: " + resp_status);
			if (resp_status == 200 || resp_status == 403) {
				BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

				StringBuilder content = new StringBuilder();
				String line;
				while (null != (line = br.readLine())) {
					content.append(line);
				}
				return content.toString();
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
}
