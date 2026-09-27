package com.main.java.site;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import com.main.java.core.NewsException;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

/**
 * Local preview of a built site on the loopback interface only.
 *
 * @author jgohil
 */
public final class SiteServer {

	private SiteServer() {}

	/** Serves until the process is stopped. */
	public static void serve(Path dir, int port, PrintStream log) throws InterruptedException {
		Path root = dir.toAbsolutePath().normalize();
		if (!Files.isRegularFile(root.resolve(SiteBuilder.MARKER))) {
			throw new NewsException(root + " is not a site built by `jnews site build`");
		}
		HttpServer server = SimpleFileServer.createFileServer(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), port), root, SimpleFileServer.OutputLevel.INFO);
		server.start();
		log.println("jnews: serving " + root + " on http://127.0.0.1:" + port + "/ (Ctrl-C to stop)");
		Thread.currentThread().join();
	}
}
