package com.matthewmariner.livelycities;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The developer-mode gate on {@link LivelyCitiesDevReportsPlugin#runCacheAudit()}
 * — <b>not</b> the audit's own logic, which {@code CacheIdAuditTest} covers
 * directly against {@link FakeClient} without needing a plugin at all.
 *
 * <p>Was {@code LivelyCitiesPluginCacheAuditTest}. The gate did not change; the class it
 * guards did. The trigger and the file both moved out of {@code src/main} and into the
 * test source set, so the shipped jar has no filesystem I/O in it — see
 * {@link LivelyCitiesDevReportsPlugin}'s javadoc for why. The gate is still checked, and
 * still checked as an AND, even though nothing a hub user runs can load this class:
 * {@code ./gradlew run} loads it too, and an ordinary dev session must not pay for a
 * full cache walk.
 *
 * <p>{@code ./gradlew test} must never run a real cache walk, so both halves —
 * {@code developerMode} and
 * {@link LivelyCitiesDevReportsPlugin#CACHE_AUDIT_SYSTEM_PROPERTY} — are proven
 * independently: either one missing has to mean nothing runs.
 *
 * <p>The game state is pinned away from {@code LOGGED_IN} throughout, matching the
 * client this plugin starts in; nothing in {@code startUp()} depends on it any more,
 * but a fixture that quietly stopped resembling the real thing is not worth the two
 * lines saved.
 */
public class DevReportsCacheAuditTest
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
		System.clearProperty(LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY);
	}

	@After
	public void tearDown()
	{
		System.clearProperty(LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY);
	}

	@Test
	public void theAuditDoesNotRunWhenDeveloperModeIsOff()
	{
		System.setProperty(LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY, "true");
		CountingPlugin plugin = plugin();
		plugin.developerMode = false;

		plugin.startUp();

		assertEquals("the system property alone must not be enough", 0, plugin.auditRuns);
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
		System.setProperty(LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY, "true");
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
		System.setProperty(LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY, "true");
		CountingPlugin plugin = plugin();

		plugin.startUp();

		assertEquals(0, plugin.auditRuns);
	}

	/**
	 * The audit's report goes in {@code model-id-audit.txt}, and never in the frame
	 * report's file.
	 *
	 * <p>The mirror of {@code FrameTimingsTest}'s
	 * {@code theFrameReportIsWrittenUnderItsOwnNameAndNeverTheAudits}, and it exists for
	 * the same reason: {@link ReportWriter} takes the file name as an argument, so each
	 * of its two callers names its own constant and nothing else checks which one. This
	 * is the cheaper direction of the two failures — a frame report is one dev session
	 * away from being regenerated, while the audit needs a live cache — but an unasserted
	 * constant is an unasserted constant, and the two call sites are in one class now.
	 *
	 * <p>The audit itself runs for real here, against an empty {@link FakeRegions} and
	 * {@link FakeClient}: no cache is touched, the report comes out empty, and what is
	 * under test is the envelope rather than the contents.
	 */
	@Test
	public void theCacheAuditIsWrittenUnderItsOwnNameAndNeverTheFrameReports()
	{
		RecordingPlugin plugin = new RecordingPlugin();
		plugin.client = client;
		plugin.clientThread = clientThread;
		plugin.regionDataLoader = new FakeRegions();

		plugin.runCacheAudit();

		assertEquals("one report, written once",
			Collections.singletonList(LivelyCitiesDevReportsPlugin.CACHE_AUDIT_REPORT_FILE_NAME),
			plugin.fileNames);
		assertNotEquals("the cache id audit must never be written over the frame report's file",
			LivelyCitiesDevReportsPlugin.FRAME_REPORT_FILE_NAME, plugin.fileNames.get(0));
	}

	/**
	 * The plugin with the disk taken out but the audit left in — see the identically
	 * shaped {@code RecordingReporter} in {@code FrameTimingsTest}.
	 */
	private static final class RecordingPlugin extends LivelyCitiesDevReportsPlugin
	{
		private final List<String> fileNames = new ArrayList<>();

		@Override
		CompletableFuture<Void> writeReportAsync(
			File outputDir, String fileName, String text, String what)
		{
			fileNames.add(fileName);
			return CompletableFuture.completedFuture(null);
		}
	}

	private CountingPlugin plugin()
	{
		CountingPlugin plugin = new CountingPlugin();
		plugin.client = client;
		plugin.clientThread = clientThread;

		// startUp() also asks the stopwatch whether it is on, so that it can say so in
		// the log. A reporter with no plugin behind it would NPE there and fail every
		// test in this file for a reason that is not the gate under test.
		LivelyCitiesPlugin owner = new LivelyCitiesPlugin();
		owner.frameTimings = FrameTimings.off();
		plugin.livelyCities = owner;
		return plugin;
	}

	private static final class CountingPlugin extends LivelyCitiesDevReportsPlugin
	{
		int auditRuns;

		@Override
		void runCacheAudit()
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
