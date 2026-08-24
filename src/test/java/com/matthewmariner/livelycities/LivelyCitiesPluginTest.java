package com.matthewmariner.livelycities;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * The dev client's entry point — {@code ./gradlew run}, {@code runWithTimings} and
 * {@code auditCacheIds} all run this {@code main} on
 * {@code sourceSets.test.runtimeClasspath}.
 *
 * <p>{@link LivelyCitiesDevReportsPlugin} is loaded alongside the real plugin, and only
 * from here. It is the half of the diagnostics that writes files, kept out of
 * {@code src/main} so the shipped jar has no filesystem I/O in it at all — see its
 * javadoc for the maintainer guidance that shape comes from. It carries
 * {@code developerPlugin = true}, so {@code PluginManager.loadPlugins} skips it unless
 * the client was launched with {@code --developer-mode}, which all three tasks pass.
 */
public class LivelyCitiesPluginTest
{
	/**
	 * What the dev client loads, in one place so that {@code ShippedSourceTest} can
	 * assert on it.
	 *
	 * <p>Dropping the reporter from this list is silent in the worst way: the task
	 * launches, the client runs, the stopwatch measures, and the report file never
	 * appears — the same failure mode {@code FrameTimingsTest} guards the two Gradle
	 * tasks against, one step further along the same chain.
	 */
	static final List<Class<? extends Plugin>> BUILTIN_PLUGINS = Collections.unmodifiableList(
		Arrays.asList(LivelyCitiesPlugin.class, LivelyCitiesDevReportsPlugin.class));

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(builtinPlugins());
		RuneLite.main(args);
	}

	/**
	 * {@link #BUILTIN_PLUGINS} as the varargs array {@code loadBuiltin} wants.
	 *
	 * <p>Its own method so the one unavoidable unchecked cast is suppressed on one
	 * line rather than warned about on every build — {@code toArray} cannot produce a
	 * generic array, and {@code Class<? extends Plugin>[]} is exactly that. Kept as the
	 * single path from the list to the client so {@code ShippedSourceTest} is asserting
	 * about what actually gets loaded: {@code main} reads the list only through here.
	 */
	@SuppressWarnings("unchecked")
	static Class<? extends Plugin>[] builtinPlugins()
	{
		return BUILTIN_PLUGINS.toArray(new Class[0]);
	}
}
