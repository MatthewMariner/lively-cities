package com.matthewmariner.livelycities;

import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The developer-mode gate on {@link LivelyCitiesPlugin#runDeveloperCacheAudit()}
 * — <b>not</b> the audit's own logic, which {@code CacheIdAuditTest} covers
 * directly against {@link FakeClient} without needing a plugin at all.
 *
 * <p>{@code ./gradlew test} must never run a real cache walk, so both halves of
 * the gate — {@code developerMode} and
 * {@link LivelyCitiesPlugin#CACHE_AUDIT_SYSTEM_PROPERTY} — are proven
 * independently: either one missing has to mean nothing runs.
 *
 * <p>The game state is pinned away from {@code LOGGED_IN} throughout, so
 * {@code startUp()}'s other branch ({@code tick()}, which needs a real scene)
 * never fires — this file is only about the audit branch.
 */
public class LivelyCitiesPluginCacheAuditTest
{
	private FakeClient client;
	private InlineClientThread clientThread;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.setGameState(GameState.LOGIN_SCREEN);
		clientThread = new InlineClientThread();

		// Belt and braces: a leftover from a crashed prior run must not leak
		// into a test that asserts the gate is closed.
		System.clearProperty(LivelyCitiesPlugin.CACHE_AUDIT_SYSTEM_PROPERTY);
	}

	@After
	public void tearDown()
	{
		System.clearProperty(LivelyCitiesPlugin.CACHE_AUDIT_SYSTEM_PROPERTY);
	}

	@Test
	public void theAuditDoesNotRunWhenDeveloperModeIsOff()
	{
		System.setProperty(LivelyCitiesPlugin.CACHE_AUDIT_SYSTEM_PROPERTY, "true");
		CountingPlugin plugin = plugin();
		plugin.developerMode = false;

		plugin.startUp();

		assertEquals("the system property alone must not be enough — "
				+ "this is what stops a real user's client from ever running it",
			0, plugin.auditRuns);
	}

	@Test
	public void theAuditDoesNotRunWhenTheSystemPropertyIsUnset()
	{
		CountingPlugin plugin = plugin();
		plugin.developerMode = true;

		plugin.startUp();

		assertEquals("developerMode alone is true on every `./gradlew run` — "
				+ "this is what stops an ordinary dev session from paying for a full cache walk",
			0, plugin.auditRuns);
	}

	@Test
	public void theAuditRunsExactlyOnceWhenBothFlagsAreSet()
	{
		System.setProperty(LivelyCitiesPlugin.CACHE_AUDIT_SYSTEM_PROPERTY, "true");
		CountingPlugin plugin = plugin();
		plugin.developerMode = true;

		plugin.startUp();

		assertEquals(1, plugin.auditRuns);
	}

	/**
	 * A default-constructed plugin — nobody has set {@code developerMode} — must
	 * behave exactly like the "off" case above. This is the scenario every other
	 * test in this project's suite is already in, since none of them wire the
	 * field: if this ever regressed to defaulting {@code true}, every other test
	 * would still pass and only a real client run would ever notice.
	 */
	@Test
	public void aPluginNobodyConfiguredNeverRunsTheAudit()
	{
		System.setProperty(LivelyCitiesPlugin.CACHE_AUDIT_SYSTEM_PROPERTY, "true");
		CountingPlugin plugin = plugin();

		plugin.startUp();

		assertEquals(0, plugin.auditRuns);
	}

	private CountingPlugin plugin()
	{
		CountingPlugin plugin = new CountingPlugin();
		plugin.client = client;
		plugin.clientThread = clientThread;
		return plugin;
	}

	private static final class CountingPlugin extends LivelyCitiesPlugin
	{
		int auditRuns;

		@Override
		void runDeveloperCacheAudit()
		{
			auditRuns++;
		}
	}

	/**
	 * The real {@link ClientThread}, minus the thread — see the identical class
	 * in {@code LivelyCitiesPluginLifecycleTest} for why {@code invoke} running
	 * inline is faithful to every path the plugin uses it for.
	 */
	private static final class InlineClientThread extends ClientThread
	{
		@Override
		public void invoke(Runnable runnable)
		{
			runnable.run();
		}
	}
}
