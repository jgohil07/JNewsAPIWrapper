package com.main.java.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.main.java.core.FetchException;
import com.main.java.core.NewsException;
import com.main.java.testutil.FixtureServer;
import com.main.java.testutil.FixtureServer.Reply;
import com.main.java.utils.JsonFactory;

@SuppressWarnings("deprecation")
class JsonFactoryTest {

	private FixtureServer server;
	private final JsonFactory jf = new JsonFactory();

	@BeforeEach
	void setUp() throws IOException {
		server = new FixtureServer();
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	@Test
	void getParsesArrayAndSendsHeaders() {
		server.on("/x", Reply.json("[{\"a\":1},{\"a\":2}]"));
		List<JSONObject> list = jf.getRequest(server.url("/x"), new String[] { "X-T" }, new String[] { "v" }, "q=1");
		assertEquals(2, list.size());
		assertEquals("v", server.lastRequest().headers().getFirst("X-T"));
		assertEquals("q=1", server.lastRequest().query());
	}

	@Test
	void forbiddenThrowsInsteadOfReturningBody() {
		server.on("/x", Reply.of(403, "application/json", "{\"error\":\"nope\"}"));
		assertThrows(FetchException.class, () -> jf.httpGetRequestJson(server.url("/x"), null));
	}

	@Test
	void nullHeaderValuesAreAnErrorNotANullPointer() {
		assertThrows(IllegalArgumentException.class,
				() -> jf.httpGetRequestJson(server.url("/x"), new String[] { "A" }, null, null));
	}

	@Test
	void getRequestForRestWorksWithoutParameters() {
		server.on("/x", Reply.json("{\"description\":\"ok\"}"));
		assertEquals(1, jf.getRequestForRest(server.url("/x"), null, null, null).size(), "bug 8: request was null");
	}

	@Test
	void postSendsJsonAndReturnsObject() {
		server.on("/p", Reply.json("{\"id\":7}"));
		JSONObject o = jf.postRequestJSON(server.url("/p"), "{\"k\":1}", new String[0], new String[0], null);
		assertEquals(7, o.getInt("id"));
		assertEquals("{\"k\":1}", server.lastRequest().body());
		assertEquals("application/json;charset=UTF-8", server.lastRequest().headers().getFirst("Content-Type"));
	}

	@Test
	void emptyBodyGivesNullButHtmlFails() {
		server.on("/e", Reply.of(204, null, ""));
		assertNull(jf.postRequestHeader(server.url("/e"), null, null));
		server.on("/h", Reply.of(200, "text/html", "<html/>"));
		assertThrows(NewsException.class, () -> jf.getRequest(server.url("/h"), null, null, null));
	}
}
