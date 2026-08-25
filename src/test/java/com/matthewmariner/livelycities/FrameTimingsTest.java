package com.matthewmariner.livelycities;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The frame-time instrumentation: the gate that keeps it off, the distribution it
 * reports, and the three places the scene feeds it.
 *
 * <p><b>What this file cannot do</b> is produce the number. That needs a human
 * playing — a real client, a real cache, a real Varrock — and this is the machinery
 * that makes the number fall out of ordinary play rather than out of a benchmark
 * nobody will run. What it can do is prove the machinery is honest: that it is off
 * unless a developer asked, that a mean is not being passed off as a distribution,
 * and that the three meters are wired to the three things they claim to measure.
 *
 * <p>Durations are <b>stated</b> rather than clocked, through
 * {@link FrameTimings#recordElapsed}. A test that produced a real 300µs pass would be
 * asserting about the machine it ran on: at 1µs bucket resolution a scheduler hiccup
 * moves the answer, and a flaky performance test is worse than none.
 */
public class FrameTimingsTest
{
	private static final int VARROCK_NORTH = 12853;
	private static final WorldPoint PLAYER = new WorldPoint(3220, 3420, 0);

	private static final long MICRO = 1_000L;
	private static final long MILLI = 1_000_000L;

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		regions = new FakeRegions();
		config = new FakeConfig();
		view = FakeWorldView.around(PLAYER, VARROCK_NORTH);
		client.setLocalPlayer(new FakePlayer(PLAYER));
		client.setTopLevelWorldView(view);

		System.clearProperty(FrameTimings.SYSTEM_PROPERTY);
	}

	@After
	public void tearDown()
	{
		System.clearProperty(FrameTimings.SYSTEM_PROPERTY);
	}

	// --- the gate --------------------------------------------------------------

	/**
	 * Both halves, or nothing.
	 *
	 * <p>{@code developerMode} alone is true for every {@code ./gradlew run}, and the
	 * system property alone is something a determined user could pass to a launcher. A
	 * dev-only capability compiled into the shipped jar is fine; one an ordinary user
	 * can switch on is not — the same rule, and the same shape, as the cache id audit.
	 */
	@Test
	public void nothingIsMeasuredUnlessBothHalvesOfTheGateAreSet()
	{
		assertFalse("neither", new FrameTimings(false, false).isEnabled());
		assertFalse("developer mode alone — every ordinary ./gradlew run",
			new FrameTimings(true, false).isEnabled());
		assertFalse("the system property alone — a user with a launcher argument",
			new FrameTimings(false, true).isEnabled());
		assertTrue("both", new FrameTimings(true, true).isEnabled());
	}

	/**
	 * The injected constructor reads the real system property, so the gate the plugin
	 * ships with is the gate under test above rather than a parallel one.
	 */
	@Test
	public void theInjectedConstructorReadsTheSystemProperty()
	{
		assertFalse("unset", new FrameTimings(true).isEnabled());

		System.setProperty(FrameTimings.SYSTEM_PROPERTY, "true");
		assertTrue("set, with developer mode", new FrameTimings(true).isEnabled());
		assertFalse("set, without it", new FrameTimings(false).isEnabled());
	}

	/**
	 * Off, the meter never reads a clock and never keeps anything.
	 *
	 * <p>The assertion on {@link FrameTimings#start()} is the load-bearing one and it
	 * is not decoration: {@code start()} is called on every frame and every game tick
	 * whether the meter is on or not, so if it read {@code System.nanoTime()}
	 * regardless, an ordinary user would pay two clock reads per frame forever to feed
	 * counters nobody is going to look at. Zero is what says it did not.
	 */
	@Test
	public void aDisabledMeterReadsNoClockAndKeepsNothing()
	{
		FrameTimings off = FrameTimings.off();

		assertEquals("no nanoTime call when nobody is looking", 0L, off.start());

		off.recordVisibility(off.start(), 40);
		off.recordModelBuild(off.start(), 40);
		off.recordFrame(off.start(), 3);
		off.recordElapsed(FrameTimings.Pass.VISIBILITY, 5 * MILLI, 40);

		assertFalse("nothing recorded", off.hasSamples());
		assertEquals(0L, off.sampleCount(FrameTimings.Pass.VISIBILITY));
		assertEquals(0L, off.sampleCount(FrameTimings.Pass.MODEL_BUILD));
		assertEquals(0L, off.sampleCount(FrameTimings.Pass.INTERPOLATION));
	}

	/** And an enabled one does read the clock, or the assertion above proves nothing. */
	@Test
	public void anEnabledMeterDoesReadTheClock()
	{
		assertNotEquals("a real nanoTime, so 'zero when off' is a branch and not a constant",
			0L, new FrameTimings(true, true).start());
	}

	@Test
	public void aDisabledMeterNeverAsksForAReport()
	{
		FrameTimings off = FrameTimings.off();

		for (int i = 0; i < FrameTimings.REPORT_INTERVAL_TICKS * 3; i++)
		{
			assertFalse("tick " + i, off.onGameTick());
		}

		assertEquals("and does not even count the ticks", 0L, off.getTicks());
	}

	/**
	 * The Gradle task and the constant agree.
	 *
	 * <p>A system property name is a string written in two files that no compiler
	 * relates to each other, and getting it wrong is silent in the worst way: the task
	 * launches, the client runs, the plugin measures nothing, and the report file never
	 * appears. Both dev-gated properties are checked, because the cache audit has
	 * exactly the same seam and has never had this.
	 */
	@Test
	public void theGradleTasksSetTheSystemPropertiesThePluginReads() throws IOException
	{
		// Gradle runs tests with the project directory as the working directory.
		File buildFile = new File("build.gradle");
		assertTrue("expected to find " + buildFile.getAbsolutePath()
				+ " — if the test working directory moved, this test needs to move with it",
			buildFile.isFile());

		String buildScript = new String(
			Files.readAllBytes(buildFile.toPath()), StandardCharsets.UTF_8);

		// Both halves of both gates, and per task rather than per file. The gate is an
		// AND — the system property plus --developer-mode — so a whole-file grep for
		// "--developer-mode" would be satisfied by the *other* task still having it,
		// and deleting it here would leave runWithTimings launching a client, measuring
		// nothing, writing no file, and this test green. Which is the exact silent
		// failure it exists to prevent.
		String withTimings = taskBlock(buildScript, "runWithTimings");
		assertTrue("./gradlew runWithTimings has to set " + FrameTimings.SYSTEM_PROPERTY,
			withTimings.contains("systemProperty '" + FrameTimings.SYSTEM_PROPERTY + "'"));
		assertTrue("./gradlew runWithTimings has to pass --developer-mode too, or the"
				+ " system property is set for a client that ignores it",
			withTimings.contains("--developer-mode"));
		assertTrue("./gradlew runWithTimings has to run on the TEST runtime classpath, or"
				+ " LivelyCitiesDevReportsPlugin — the half that writes the file — is not on"
				+ " the classpath at all and the client measures into nothing",
			withTimings.contains("sourceSets.test.runtimeClasspath"));

		String audit = taskBlock(buildScript, "auditCacheIds");
		assertTrue("./gradlew auditCacheIds has to set "
				+ LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY,
			audit.contains("systemProperty '"
				+ LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY + "'"));
		assertTrue("./gradlew auditCacheIds has to pass --developer-mode too",
			audit.contains("--developer-mode"));
		assertTrue("./gradlew auditCacheIds has to run on the TEST runtime classpath, or the"
				+ " plugin that triggers the walk and writes the report is not loaded",
			audit.contains("sourceSets.test.runtimeClasspath"));

		// And the Windows launcher, which does not go through Gradle at all and so
		// duplicates both property names. It is the launcher to use on this machine —
		// the Gradle tasks run a Linux-side client that reads a different
		// credentials.properties than the Jagex Launcher writes, and log in as the
		// wrong character — so a typo here is not a typo in a convenience script.
		File launcher = new File("run-windows.sh");
		assertTrue("expected to find " + launcher.getAbsolutePath(), launcher.isFile());

		String script = new String(
			Files.readAllBytes(launcher.toPath()), StandardCharsets.UTF_8);

		assertTrue("run-windows.sh --timings has to set "
				+ FrameTimings.SYSTEM_PROPERTY,
			script.contains("-D" + FrameTimings.SYSTEM_PROPERTY + "=true"));
		assertTrue("run-windows.sh --audit has to set "
				+ LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY,
			script.contains(
				"-D" + LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY + "=true"));
		assertTrue("run-windows.sh has to pass --developer-mode, or both properties are"
				+ " set for a client that ignores them",
			script.contains("--developer-mode"));
	}

	/**
	 * The body of one {@code tasks.register('name', ...)} block.
	 *
	 * <p>Crude on purpose — a build script is not worth a parser here. It reads to the
	 * first line that closes the block at column zero, which is how every task in this
	 * build script is written, and fails loudly rather than returning the whole file if
	 * the shape ever changes.
	 */
	private static String taskBlock(String buildScript, String taskName)
	{
		int start = buildScript.indexOf("tasks.register('" + taskName + "'");
		assertNotEquals("no tasks.register('" + taskName + "' in build.gradle", -1, start);

		int end = buildScript.indexOf("\n}", start);
		assertNotEquals("tasks.register('" + taskName + "' is never closed at column zero"
			+ " — this helper's assumption about the build script's shape has broken", -1, end);

		return buildScript.substring(start, end);
	}

	// --- the distribution ------------------------------------------------------

	/**
	 * A median and a high percentile, not a mean — and the fixture is built so the
	 * three answers are different numbers.
	 *
	 * <p>Ninety-eight samples of 10µs and two of 40ms. The mean is 810µs, which
	 * describes nothing that happened: no pass took 810µs, ninety-eight of them were
	 * eighty times faster and two were fifty times slower. Those two spikes are exactly
	 * what a model-building burst looks like, and a mean would bury them — which is the
	 * whole reason this reports percentiles.
	 */
	@Test
	public void theReportedFigureIsADistributionAndNotAMean()
	{
		FrameTimings timings = new FrameTimings(true, true);

		for (int i = 0; i < 98; i++)
		{
			timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 10 * MICRO, 40);
		}
		timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 40 * MILLI, 76);
		timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 40 * MILLI, 76);

		assertEquals("the median is the ordinary pass",
			10, timings.percentileMicros(FrameTimings.Pass.VISIBILITY, 0.5));
		assertEquals("and so is the 95th — two samples in a hundred cannot move it",
			10, timings.percentileMicros(FrameTimings.Pass.VISIBILITY, 0.95));
		assertTrue("but the 99th has to find the spikes, or the high percentile is decoration",
			timings.percentileMicros(FrameTimings.Pass.VISIBILITY, 0.99) >= 11_000);
		assertEquals("and the maximum is exact rather than a bucket edge",
			40_000, timings.maxMicros(FrameTimings.Pass.VISIBILITY));
	}

	/**
	 * The object count travels with the timing, and it is the count at the <i>worst</i>
	 * sample rather than the last one.
	 *
	 * <p>"1ms at 8 objects" and "1ms at 76" are different claims about the same
	 * millisecond, and the second one is the one worth reporting.
	 */
	@Test
	public void theSlowestSampleCarriesTheObjectCountItHappenedAt()
	{
		FrameTimings timings = new FrameTimings(true, true);

		timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 100 * MICRO, 8);
		timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 9 * MILLI, 76);
		timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 120 * MICRO, 12);

		assertEquals(9000, timings.maxMicros(FrameTimings.Pass.VISIBILITY));
		assertEquals("the count at the peak, not at the last sample",
			76, timings.countAtMax(FrameTimings.Pass.VISIBILITY));
		assertTrue("and it has to reach a reader",
			timings.summaryLine().contains("76 objects active"));
	}

	/**
	 * A sample past the top of the histogram is reported as "at least the last bucket"
	 * rather than as that bucket, and the exact maximum is printed beside it.
	 *
	 * <p>Without the marker a 900ms pass and an 11ms one would print the same p99, and
	 * the difference between those two is the difference between "fine" and "the plugin
	 * froze the client for a second".
	 */
	@Test
	public void aSampleOffTheTopOfTheHistogramSaysSoRatherThanRoundingDown()
	{
		FrameTimings timings = new FrameTimings(true, true);
		timings.recordElapsed(FrameTimings.Pass.MODEL_BUILD, 900 * MILLI, 76);

		String report = timings.toReportText();

		// The specific figure, not merely ">=" somewhere in the file: the header
		// explains the marker, so asserting on the marker alone passes whether or not
		// any percentile ever carries it.
		assertTrue("the p99 has to be marked as a floor, not printed as if it were exact: "
				+ report,
			report.contains(">=11.00ms"));
		assertTrue("and the exact maximum has to be in there beside it: " + report,
			report.contains("900.00ms"));
	}

	/**
	 * Sub-microsecond work reads as {@code 0us}, not as {@code 1us}.
	 *
	 * <p>"Too fast to measure" is the honest answer and it is the one the per-frame
	 * pass is expected to give when nobody is wandering. Rounding up would turn a
	 * truthful zero into a small lie that accumulates over a percentile.
	 */
	@Test
	public void workTooFastToMeasureIsReportedAsZeroRatherThanRoundedUp()
	{
		FrameTimings timings = new FrameTimings(true, true);
		timings.recordElapsed(FrameTimings.Pass.INTERPOLATION, 400, 0);

		assertEquals(0, timings.percentileMicros(FrameTimings.Pass.INTERPOLATION, 0.5));
		assertTrue(timings.summaryLine(), timings.summaryLine().contains("median 0us"));
	}

	/**
	 * The report names the acceptance thresholds it should be read against.
	 *
	 * <p>A number with no threshold beside it is a number a reader has to take a view
	 * on, and the point of this whole exercise is that the predecessor's "no lag or
	 * resource issues" was exactly that. The thresholds are written down in
	 * {@code FrameTimings}' javadoc and repeated in the file, so the file is legible on
	 * its own — and this is what stops the header being trimmed to a table.
	 */
	@Test
	public void theReportSaysWhatWouldCountAsAcceptableAndWhatWouldNot()
	{
		FrameTimings timings = new FrameTimings(true, true);
		timings.recordElapsed(FrameTimings.Pass.INTERPOLATION, 30 * MICRO, 3);

		String report = timings.toReportText();
		assertTrue(report, report.contains("Acceptable:"));
		assertTrue(report, report.contains("A problem:"));
		assertTrue("the per-frame budget", report.contains("interpolation p99 <= 0.5ms"));
		assertTrue("the per-tick budget", report.contains("visibility p99 <= 2ms"));
		assertTrue("and the fact that one figure contains the other",
			report.contains("INCLUSIVE of model building"));
	}

	// --- the cadence -----------------------------------------------------------

	@Test
	public void aReportIsDueEveryReportIntervalTicksAndNotInBetween()
	{
		FrameTimings timings = new FrameTimings(true, true);

		for (int tick = 1; tick <= FrameTimings.REPORT_INTERVAL_TICKS * 2; tick++)
		{
			boolean due = timings.onGameTick();
			assertEquals("tick " + tick, tick % FrameTimings.REPORT_INTERVAL_TICKS == 0, due);
		}

		assertEquals(FrameTimings.REPORT_INTERVAL_TICKS * 2L, timings.getTicks());
	}

	/**
	 * The reporter reports on that cadence and never otherwise — and never at all when
	 * the stopwatch is off.
	 *
	 * <p>The subject is {@link LivelyCitiesDevReportsPlugin} rather than
	 * {@link LivelyCitiesPlugin} since the reporting moved to the test source set: a
	 * shipped client does not merely fail to reach this path, it does not contain it.
	 * What is still worth pinning is the cadence itself, and the "off" case is still
	 * worth pinning with it — {@code ./gradlew run} loads this reporter too, and it must
	 * write nothing there.
	 */
	@Test
	public void thePluginReportsOnTheCadenceAndNeverWhenTheStopwatchIsOff()
	{
		CountingReporter quiet = reporter(FrameTimings.off());
		for (int i = 0; i < FrameTimings.REPORT_INTERVAL_TICKS + 5; i++)
		{
			quiet.onGameTick(new GameTick());
		}
		assertEquals("an ordinary developer client must never reach the reporting path",
			0, quiet.reports);

		CountingReporter measuring = reporter(new FrameTimings(true, true));
		for (int i = 0; i < FrameTimings.REPORT_INTERVAL_TICKS - 1; i++)
		{
			measuring.onGameTick(new GameTick());
		}
		assertEquals("not one tick early", 0, measuring.reports);

		measuring.onGameTick(new GameTick());
		assertEquals(1, measuring.reports);
	}

	/**
	 * The reporter's tick runs <b>after</b> the plugin's, on a real {@link EventBus}.
	 *
	 * <p><b>Why this is a behavioural test and not a one-line annotation read.</b> The
	 * only thing keeping "the tick that triggers a report is a tick the report already
	 * includes" true is {@code @Subscribe(priority = -1f)} on
	 * {@link LivelyCitiesDevReportsPlugin#onGameTick} — a floating-point annotation
	 * argument whose sign is the entire contract. Review found that <i>deleting</i> it
	 * left the suite green and, worse, that <i>flipping it to {@code +1f}</i> — which
	 * inverts the property its six-line javadoc asserts — also left the suite green.
	 * Reading the annotation back would need reflection, which this project bans in
	 * tests; so instead both plugins go on a real bus and the ordering is observed.
	 *
	 * <p>{@code EventBus} sorts subscribers by
	 * {@code Comparator.comparingDouble(Subscriber::getPriority).reversed()} — highest
	 * first — so a negative priority sorts last. The plugin's handler records a
	 * visibility sample; the reporter's handler captures the sample count at the moment
	 * it runs. After means one, before means zero.
	 */
	@Test
	public void theReporterSeesTheTickTheStopwatchJustMeasured()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, 5);

		final long[] seenByReporter = {-1L};

		// The capture hangs off the first thing the reporter's own handler does, so
		// what is being timed is the real @Subscribe on the real class — not a
		// priority written into this fixture, which would only be testing EventBus.
		LivelyCitiesPlugin owner = new LivelyCitiesPlugin()
		{
			@Override
			boolean processedGameTick()
			{
				seenByReporter[0] = timings.sampleCount(FrameTimings.Pass.VISIBILITY);
				return true;
			}
		};
		owner.frameTimings = timings;

		LivelyCitiesDevReportsPlugin reporter = new LivelyCitiesDevReportsPlugin();
		reporter.livelyCities = owner;

		EventBus bus = new EventBus();
		bus.register(reporter);
		// See TickPassRunner: its name, not its registration order, is what makes this
		// test faithful. EventBus breaks priority ties on the subscriber's class name,
		// so an anonymous subscriber declared here would sort ahead of the reporter for
		// free and this would pass with no priority at all.
		bus.register(new TickPassRunner(scene, PLAYER, view));

		bus.post(new GameTick());

		assertEquals("the reporter's handler has to run after the pass it measures —"
				+ " a positive priority would make this 0",
			1L, seenByReporter[0]);
	}

	/**
	 * A tick the plugin did not process does not advance the cadence.
	 *
	 * <p><b>This is the population the report describes</b>, and keeping it right is the
	 * whole reason {@link LivelyCitiesPlugin#processedGameTick()} exists. Before the
	 * reporting moved to the test source set, {@code frameTimings.onGameTick()} was the
	 * last statement of {@code LivelyCitiesPlugin.tick()} — <i>after</i> its "no player
	 * or no world view yet" early return — so a tick that did no work never counted.
	 * Splitting the two classes made it far too easy to count every tick instead, and
	 * the difference is not academic: with Lively Cities toggled off and this reporter
	 * left running, a counting-every-tick cadence would go on rewriting
	 * {@code frame-timings.txt} out of samples nobody is producing any more.
	 *
	 * <p>The stopwatch is on and the tick count is well past the interval, so the only
	 * thing standing between this and a report is the gate.
	 */
	@Test
	public void aTickThePluginDidNotProcessDoesNotAdvanceTheCadence()
	{
		CountingReporter idle = wireReporter(
			new CountingReporter(), new FrameTimings(true, true), false);

		for (int i = 0; i < FrameTimings.REPORT_INTERVAL_TICKS * 2; i++)
		{
			idle.onGameTick(new GameTick());
		}

		assertEquals("no report, because the plugin processed none of those ticks",
			0, idle.reports);
		assertEquals("and the stopwatch must not have counted them either",
			0L, idle.stopwatch().getTicks());
	}

	/**
	 * And once more on the way out.
	 *
	 * <p>The periodic report is the one to rely on if the client is killed, but a client
	 * that is <i>closed</i> lands here — and everything since the last 300-tick boundary
	 * exists only in the meters, so a shutdown that did not report would throw away up
	 * to five minutes of the session, including all of a session shorter than that.
	 *
	 * <p>Deleting the call from {@code shutDown()} left the whole suite green before
	 * this test existed: {@code LivelyCitiesPluginLifecycleTest} does exercise
	 * {@code shutDown()}, but every plugin it builds carries {@code FrameTimings.off()},
	 * so the report was a no-op there whether it was called or not. Since the reporting
	 * moved to the test source set the {@code shutDown()} in question is
	 * {@link LivelyCitiesDevReportsPlugin}'s, which is the whole of that plugin's
	 * teardown — so deleting it now leaves a plugin whose shutdown does nothing at all,
	 * and this is still the only thing that would notice.
	 */
	@Test
	public void theTailOfTheSessionIsReportedOnTheWayOut()
	{
		CountingReporter plugin = reporter(new FrameTimings(true, true));

		plugin.shutDown();

		assertEquals("shutDown has to report the tail of the session", 1, plugin.reports);
	}

	// --- what it decides to write -----------------------------------------------

	/**
	 * A stopwatch that never took a sample writes nothing.
	 *
	 * <p>{@code shutDown()} reports unconditionally, so without the
	 * {@code hasSamples()} guard every client that started and closed without reaching
	 * the cadence would write a report saying "no samples" three times — over the top
	 * of the report from the session that did have something in it. The file is only
	 * ever read after the fact, so a developer would find the empty one and conclude
	 * the instrument was broken.
	 *
	 * <p>This goes one level below {@link CountingReporter}: the question is not whether
	 * {@code reportFrameTimings()} ran — it did — but whether it decided to write.
	 */
	@Test
	public void aStopwatchWithNothingToSayWritesNoReportAtAll()
	{
		RecordingReporter plugin = recordingReporter(new FrameTimings(true, true));
		assertFalse("the fixture is pointless unless the meters really are empty",
			plugin.stopwatch().hasSamples());

		plugin.reportFrameTimings();

		assertEquals("an empty report is worse than no report: it overwrites a real one",
			java.util.Collections.emptyList(), plugin.fileNames);
	}

	/**
	 * The frame report goes in {@code frame-timings.txt} — and emphatically not in the
	 * model id audit's file.
	 *
	 * <p><b>This is the assertion that did not exist.</b> {@code ReportWriter} took a
	 * file name as an argument the moment it grew a second caller, which left each
	 * caller naming its own constant and nothing checking which one. Swapping
	 * {@link LivelyCitiesDevReportsPlugin#FRAME_REPORT_FILE_NAME} for
	 * {@link LivelyCitiesDevReportsPlugin#CACHE_AUDIT_REPORT_FILE_NAME}
	 * on that line compiles, type-checks, and leaves the whole suite green while the
	 * frame report quietly destroys {@code model-id-audit.txt} every 300 ticks —
	 * destroying, in particular, the output of the one piece of tooling that has to run
	 * against a live cache and therefore cannot be re-derived from a test run. Neither
	 * constant appeared in an assertion anywhere before this, and the move put them in
	 * the same class, four lines apart, which does not make the mistake harder.
	 */
	@Test
	public void theFrameReportIsWrittenUnderItsOwnNameAndNeverTheAudits()
	{
		FrameTimings timings = new FrameTimings(true, true);
		RecordingReporter plugin = recordingReporter(timings);
		timings.recordElapsed(FrameTimings.Pass.VISIBILITY, 900L, 3);

		plugin.reportFrameTimings();

		assertEquals("one report, written once",
			java.util.Collections.singletonList(LivelyCitiesDevReportsPlugin.FRAME_REPORT_FILE_NAME),
			plugin.fileNames);
		assertNotEquals("the frame report must never be written over the cache id audit's",
			LivelyCitiesDevReportsPlugin.CACHE_AUDIT_REPORT_FILE_NAME, plugin.fileNames.get(0));
		assertEquals("and what it writes has to be the frame report's own text",
			timings.toReportText(), plugin.texts.get(0));
	}

	// --- what the scene feeds it ------------------------------------------------

	/**
	 * One visibility sample per pass, carrying the number of objects the pass left on
	 * screen.
	 */
	@Test
	public void everyVisibilityPassIsOneSampleCarryingTheActiveObjectCount()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, 5);

		scene.updateVisibility(PLAYER, view);

		assertEquals(1L, timings.sampleCount(FrameTimings.Pass.VISIBILITY));
		assertEquals("the first pass builds its budget and no more, so five citizens are "
				+ "not five on screen any more",
			RenderPolicy.MAX_MODEL_BUILDS_PER_PASS, scene.countActive());

		// The load-bearing one, and it is the guard on `planned - failed - buildsDeferred`
		// rather than a restatement of the line above: `planned` is 5 here and `failed` is
		// 0, so a version that forgot to subtract the held-over builds would report five
		// objects active for a pass that put three on screen — overstating the load every
		// timing in the report was measured under, which is the one thing this meter is
		// not allowed to get wrong.
		assertEquals("the meter reports what the client is actually holding",
			scene.countActive(), timings.countAtMax(FrameTimings.Pass.VISIBILITY));

		scene.updateVisibility(PLAYER, view);
		assertEquals("a second pass is a second sample",
			2L, timings.sampleCount(FrameTimings.Pass.VISIBILITY));
		assertEquals("and the other two arrive on it", 5, scene.countActive());
	}

	/**
	 * Every frame is a sample, including the ones with nothing to interpolate.
	 *
	 * <p>That is the point of the meter rather than an edge case: "the per-frame pass
	 * does nothing" is the claim, and a meter that only sampled the busy frames could
	 * not make it. The fixture is five {@code StationaryCitizen}s, so the walker list
	 * is empty and the loop body never runs.
	 */
	@Test
	public void everyFrameIsASampleEvenWithNobodyWalking()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, 5);
		scene.updateVisibility(PLAYER, view);
		assertEquals("the fixture has to have no walkers, or this measures the wrong thing",
			0, scene.getWalkerCount());

		for (int i = 0; i < 10; i++)
		{
			scene.onFrame(view, i / 10f);
		}

		assertEquals(10L, timings.sampleCount(FrameTimings.Pass.INTERPOLATION));
		assertEquals(0, timings.countAtMax(FrameTimings.Pass.INTERPOLATION));
	}

	/**
	 * A model is timed the once, when it is built, and never again.
	 *
	 * <p>The distinction the meter has to draw is between a first spawn — which merges,
	 * recolours, transforms and lights a model — and a reactivation, which hands the
	 * client a model it already has. Timing both would report the expensive thing's
	 * name over the cheap thing's numbers and drag every percentile to nothing.
	 */
	@Test
	public void aModelIsTimedOnceWhenItIsBuiltAndNotOnEveryRespawn()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, 3);

		scene.updateVisibility(PLAYER, view);
		assertEquals("three models built", 3L, timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));

		// Out of range and back again: three despawns and three respawns, and not one
		// model rebuilt.
		WorldPoint faraway = new WorldPoint(PLAYER.getX(), PLAYER.getY() + 200, 0);
		scene.updateVisibility(faraway, view);
		assertEquals(0, scene.countActive());
		scene.updateVisibility(PLAYER, view);
		assertEquals(3, scene.countActive());

		assertEquals("a respawn reuses the cached model, so it is not a build",
			3L, timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));
	}

	/**
	 * A spawn deferred by a cold model cache times nothing.
	 *
	 * <p>It did not build a model — it asked the client, got nothing, and will try
	 * again — so counting it would put a pile of near-zero samples into the meter whose
	 * job is to describe how long building a model takes, and pull the median to the
	 * floor.
	 */
	@Test
	public void aSpawnDeferredByAColdCacheIsNotAModelBuild()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, 3);
		client.setCacheCold(true);

		scene.updateVisibility(PLAYER, view);

		assertEquals("nothing spawned, so nothing was built", 0, scene.countActive());
		assertEquals(0L, timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));
		assertEquals("the pass itself still happened and is still timed",
			1L, timings.sampleCount(FrameTimings.Pass.VISIBILITY));
		assertEquals("and it has to report what the client is actually holding — three "
				+ "entities the pass planned for, three that could not be built, none active",
			0, timings.countAtMax(FrameTimings.Pass.VISIBILITY));
	}

	/**
	 * A build the budget held over is counted as held over — and is not a model build,
	 * not a failure, and not a cold-cache miss.
	 *
	 * <p>The meter has four outcomes to keep apart for a wanted entity that is not yet
	 * on screen, and three of them look identical from outside: it built a model (a
	 * MODEL_BUILD sample), the client would not give it one (nothing recorded, ever),
	 * the budget said not this pass (a deferral), or it failed structurally (nothing
	 * recorded, and the entity is latched out). Fold any two together and the report
	 * starts describing a burst it did not measure.
	 */
	@Test
	public void aBuildTheBudgetHeldOverIsCountedAndIsNotAModelBuild()
	{
		FrameTimings timings = new FrameTimings(true, true);
		int crowd = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS * 3;
		EntityScene scene = sceneWith(timings, crowd);

		scene.updateVisibility(PLAYER, view);

		assertEquals("the pass builds its budget and no more",
			(long) RenderPolicy.MAX_MODEL_BUILDS_PER_PASS,
			timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));
		assertEquals("and the other six are held over, not lost and not failed",
			crowd - RenderPolicy.MAX_MODEL_BUILDS_PER_PASS, timings.getBuildsDeferred());
		assertEquals("over the one pass that held them", 1L, timings.getPassesWithDeferredBuilds());

		// The second pass builds three more and holds three over; the third builds the
		// last three and holds none, so the pass counter has to stop at two.
		scene.updateVisibility(PLAYER, view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("every citizen is built exactly once", (long) crowd,
			timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));
		assertEquals(crowd, scene.countActive());
		assertEquals("six held over on the first pass and three on the second",
			(crowd - RenderPolicy.MAX_MODEL_BUILDS_PER_PASS)
				+ (crowd - 2 * RenderPolicy.MAX_MODEL_BUILDS_PER_PASS),
			timings.getBuildsDeferred());
		assertEquals("a pass that held nothing over must not be counted as one that did",
			2L, timings.getPassesWithDeferredBuilds());
	}

	/**
	 * A cold-cache deferral is not a budget deferral, and the meter has to say so.
	 *
	 * <p>The sibling of {@link #aSpawnDeferredByAColdCacheIsNotAModelBuild}, and the
	 * assertion that stops the new counter from quietly absorbing the old case. Both are
	 * "a wanted citizen that did not get a model this pass"; only one of them is a
	 * decision this plugin made. Counting a cold cache as a budget deferral would make
	 * the report claim the fix was working on a login where it never even engaged.
	 */
	@Test
	public void aColdCacheDeferralIsNotABudgetDeferral()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, RenderPolicy.MAX_MODEL_BUILDS_PER_PASS * 3);
		client.setCacheCold(true);

		scene.updateVisibility(PLAYER, view);

		assertEquals("nothing spawned, so nothing was built", 0, scene.countActive());
		assertEquals(0L, timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));
		assertEquals("and nothing was held over either — the budget was never spent, so it "
				+ "never held anything back",
			0L, timings.getBuildsDeferred());
		assertEquals(0L, timings.getPassesWithDeferredBuilds());
	}

	/**
	 * The report says what the budget held over, and says it even when the answer is
	 * nothing.
	 *
	 * <p>A fix whose effect cannot be read off the next report is not finished: the
	 * model-build meter's sample count no longer accounts for every citizen that wanted
	 * a model, so without this line a reader would see a quiet burst and no reason for
	 * it. Printed at zero as well, because "the budget held nothing back" and "nobody
	 * wired the budget up" are different answers.
	 */
	@Test
	public void theReportSaysHowManyBuildsTheBudgetHeldOver()
	{
		FrameTimings quiet = new FrameTimings(true, true);
		quiet.recordElapsed(FrameTimings.Pass.VISIBILITY, 900L, 3);
		String quietReport = quiet.toReportText();
		assertTrue("the line has to be there at zero: " + quietReport,
			quietReport.contains("held over: 0"));
		assertTrue("with the budget it was measured against: " + quietReport,
			quietReport.contains("budget:    " + RenderPolicy.MAX_MODEL_BUILDS_PER_PASS
				+ " build(s) per visibility pass"));

		FrameTimings busy = new FrameTimings(true, true);
		EntityScene scene = sceneWith(busy, RenderPolicy.MAX_MODEL_BUILDS_PER_PASS * 3);
		scene.updateVisibility(PLAYER, view);

		String report = busy.toReportText();
		assertTrue("and the real figure once it fires: " + report,
			report.contains("held over: " + busy.getBuildsDeferred() + "  (over 1 pass(es))"));
		assertTrue("the summary line carries it too: " + busy.summaryLine(),
			busy.summaryLine().contains("model builds held over by the "
				+ RenderPolicy.MAX_MODEL_BUILDS_PER_PASS + "-per-pass budget: "
				+ busy.getBuildsDeferred()));
	}

	/** A meter that is off counts no deferrals either. */
	@Test
	public void aDisabledMeterCountsNoDeferrals()
	{
		FrameTimings off = FrameTimings.off();
		off.recordBuildsDeferred(7);

		assertEquals(0L, off.getBuildsDeferred());
		assertEquals(0L, off.getPassesWithDeferredBuilds());
	}

	/** With the stopwatch off, the same passes record nothing at all. */
	@Test
	public void aSceneWithNoStopwatchRecordsNothing()
	{
		FrameTimings off = FrameTimings.off();
		EntityScene scene = sceneWith(off, 3);

		scene.updateVisibility(PLAYER, view);
		scene.onFrame(view, 0.5f);

		assertEquals("three citizens have to be on screen, or nothing was exercised",
			3, scene.countActive());
		assertFalse(off.hasSamples());
	}

	// --- helpers ----------------------------------------------------------------

	/**
	 * The frame meter is handed the number of walkers it actually interpolated.
	 *
	 * <p><b>Why this exists as a separate test</b>, when
	 * {@link #everyFrameIsASampleEvenWithNobodyWalking} already reads
	 * {@code countAtMax(INTERPOLATION)}: that one asserts the count is <b>0</b>, with a
	 * fixture of five stationary citizens — and 0 is also what a completely
	 * disconnected meter returns. Review proved it: replacing
	 * {@code recordFrame(startedAt, walkers.size())} with {@code recordFrame(startedAt,
	 * 99)} left the entire suite green. An expected value that coincides with the
	 * broken value is not a test, and this file had two of them.
	 *
	 * <p>So the fixture here has a walker count that is neither 0 nor any number a
	 * plausible mutation would pick, and the assertion is that <i>that</i> number comes
	 * back out.
	 */
	@Test
	public void theFrameMeterIsToldHowManyWalkersItActuallyInterpolated()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWithWanderers(timings, 3);

		scene.updateVisibility(PLAYER, view);
		assertEquals("the fixture has to actually walk, or this measures nothing",
			3, scene.getWalkerCount());

		scene.onFrame(view, 0.5f);

		assertEquals("the frame meter reports the walker count it was given",
			3, timings.countAtMax(FrameTimings.Pass.INTERPOLATION));
	}

	/**
	 * The model-build meter is handed the count the pass <b>planned</b>, which is not
	 * the same number as the count it considered.
	 *
	 * <p>The second mutation that survived the whole suite:
	 * {@code recordModelBuild(buildStartedAt, planned)} → {@code recordModelBuild(
	 * buildStartedAt, 0)}, green everywhere. {@code planned} is what the pass is about
	 * to have on screen — the same value for every build inside one pass.
	 *
	 * <p><b>Why the fixture is 90 citizens and not 5.</b> With five, {@code planned},
	 * {@code candidates.size()} and the fixture size are all 5, so the test passed
	 * against {@code recordModelBuild(buildStartedAt, candidates.size())} too — an
	 * expected value that coincides with a broken one, which is exactly the failure this
	 * file has already been caught by twice. The two are genuinely different in
	 * production: {@code planned} stops at {@link RenderPolicy#MAX_ACTIVE_OBJECTS} and
	 * {@code candidates} does not, so in the densest shipped neighbourhood at
	 * {@code CROWDED} the report would have claimed "at 120 objects active" for a pass
	 * that put 80 on screen — overstating the load the timing was measured under, in
	 * the one place the measurement matters. 90 candidates against a cap of 80 makes
	 * that gap real in the fixture.
	 *
	 * <p>Note this is only observable at all because {@code maxMicros} now starts at
	 * {@code -1}: every sample here truncates to 0µs, and under the old {@code
	 * micros > maxMicros} test against an initial 0, no sample ever won and the answer
	 * was 0 regardless.
	 */
	@Test
	public void theModelBuildMeterIsToldWhatThePassPlannedAndNotWhatItConsidered()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = packedSceneWith(timings, 90);

		// Eighty models no longer fit in one pass — that is what
		// RenderPolicy.MAX_MODEL_BUILDS_PER_PASS is for — so this is the same fixture
		// arriving over the twenty-seven passes it now takes. `planned` is unaffected by
		// the build budget and is 80 on every one of them, which is the point.
		VisibilityPasses.settle(scene, PLAYER, view);

		assertEquals("all ninety have to be candidates, or the cap is not what bound",
			90, scene.inScopeEntities().size());
		assertEquals("and the cap has to bind, or planned and candidates.size() are the "
				+ "same number again",
			RenderPolicy.MAX_ACTIVE_OBJECTS, scene.countActive());

		assertEquals("one build per object the pass planned for", (long) RenderPolicy.MAX_ACTIVE_OBJECTS,
			timings.sampleCount(FrameTimings.Pass.MODEL_BUILD));
		assertEquals("the model-build meter reports what the pass planned — 80 — and not the "
				+ "90 candidates it sorted through to get there, and not the three it was "
				+ "allowed to build on the pass the sample came from",
			RenderPolicy.MAX_ACTIVE_OBJECTS, timings.countAtMax(FrameTimings.Pass.MODEL_BUILD));
	}

	/**
	 * The visibility meter is handed everything the pass left the client holding, not
	 * just the objects it spawned this time round.
	 *
	 * <p>{@code runVisibilityPass} returns {@code planned - failed}, and the third
	 * mutation that survived the whole suite was {@code return spawned}. The two
	 * coincide on every first pass — an entity that was not already active either spawns
	 * or fails — so every fixture in this file agreed with the broken version. They come
	 * apart on the <b>second</b> pass over the same crowd, which is what a real client
	 * does every game tick: nothing new spawns, {@code spawned} is 0, and the client is
	 * still holding all five. A meter fed {@code spawned} would report a steady state as
	 * "0 objects active" and make the p99 of a busy scene look like the p99 of an empty
	 * one.
	 *
	 * <p>Read off the report's own min/mean/max line rather than {@code countAtMax},
	 * because {@code countAtMax} keeps the count from the <i>slowest</i> sample and both
	 * passes here truncate to 0µs, so the first one wins the tie and the second pass —
	 * the only one that can tell the two versions apart — never shows up in it.
	 *
	 * <p>The fixture is three rather than the five it used to be, because three is what
	 * {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} lets one pass build: with five, the
	 * first pass legitimately leaves three on screen and the min/mean/max line stops
	 * being one number. What this test needs is two passes that agree, and the
	 * held-over-builds arithmetic is guarded next door in
	 * {@link #everyVisibilityPassIsOneSampleCarryingTheActiveObjectCount}.
	 */
	@Test
	public void theVisibilityMeterCountsEverythingLeftActiveAndNotJustTheNewSpawns()
	{
		FrameTimings timings = new FrameTimings(true, true);
		EntityScene scene = sceneWith(timings, 3);

		scene.updateVisibility(PLAYER, view);
		assertEquals("the first pass has to actually spawn them", 3, scene.countActive());

		// The second pass: everybody is already on screen and stays there.
		scene.updateVisibility(PLAYER, view);
		assertEquals("and the second pass has to leave them there", 3, scene.countActive());

		assertEquals("two passes, two samples", 2L,
			timings.sampleCount(FrameTimings.Pass.VISIBILITY));

		String section = meterSection(timings.toReportText(), "visibility pass (per game tick)");
		assertTrue("both passes left three objects with the client, so neither sample may "
				+ "read 0:\n" + section,
			section.contains("objects active: min 3, mean 3, max 3"));
	}

	/**
	 * Guice builds the scene through the constructor that takes the stopwatch.
	 *
	 * <p><b>The one thing in this file no hand-built fixture can see.</b> Every test
	 * here constructs {@link EntityScene} itself and passes a {@link FrameTimings} in,
	 * so moving {@code @Inject} onto the four-argument convenience constructor — the one
	 * that supplies {@link FrameTimings#off()} — leaves all of them green while the
	 * instrument is dead in every real client: the developer sets the system property,
	 * sees the "frame timings are on" line at startup, and gets a report of three empty
	 * meters.
	 *
	 * <p>So this asks the real injector, rather than reading the annotation. Guice is
	 * how RuneLite builds this plugin, the bindings below are the ones the client
	 * supplies, and the assertion is on the consequence — a meter this test owns either
	 * received a sample or did not. Not reflection and not a mocking framework: the
	 * production container, building the production object graph, out of the same
	 * hand-rolled fakes every other test here uses.
	 */
	@Test
	public void theSceneGuiceBuildsIsTheOneWiredToTheStopwatch()
	{
		FrameTimings timings = new FrameTimings(true, true);
		regions.file(VARROCK_NORTH, regions.citizen(VARROCK_NORTH, 3221, 3420, 0));

		Injector injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(RegionDataLoader.class).toInstance(regions);
				bind(LivelyCitiesConfig.class).toInstance(config);
				bind(CitizenOverrides.class).toInstance(config.overrides());
				bind(FrameTimings.class).toInstance(timings);
			}
		});

		EntityScene scene = injector.getInstance(EntityScene.class);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("the scene has to have actually done a pass", 1, scene.countActive());
		assertEquals("the injected scene has to be holding the injected stopwatch — on the "
				+ "four-argument constructor it would build its own FrameTimings.off() and "
				+ "this meter would never hear from it again",
			1L, timings.sampleCount(FrameTimings.Pass.VISIBILITY));
	}

	/**
	 * A 0µs maximum still carries its companion count.
	 *
	 * <p>The regression test for the bug the two above were blind to, stated in the
	 * meter's own terms rather than through the scene: {@code maxMicros} initialised to
	 * 0 makes {@code micros > maxMicros} false for every 0µs sample, so {@code
	 * countAtMax} is never assigned. It matters because "too fast to measure" is the
	 * answer the interpolation meter is <i>expected</i> to give — the failure was
	 * invisible in the case anyone would look for and only bit in the healthy one.
	 */
	@Test
	public void aMaximumOfZeroStillReportsTheCountItHappenedAt()
	{
		FrameTimings timings = new FrameTimings(true, true);

		timings.recordElapsed(FrameTimings.Pass.INTERPOLATION, 900L, 42);
		timings.recordElapsed(FrameTimings.Pass.INTERPOLATION, 400L, 37);

		assertEquals("both samples truncate to 0us, so the first one is the max",
			0L, timings.maxMicros(FrameTimings.Pass.INTERPOLATION));
		assertEquals("and it has to remember there were 42 walkers, not 0",
			42, timings.countAtMax(FrameTimings.Pass.INTERPOLATION));
		assertTrue("the report must not claim 0 walkers when there were 42",
			timings.summaryLine().contains("at 42 walkers"));
	}

	private EntityScene sceneWithWanderers(FrameTimings timings, int citizens)
	{
		EntityDefinition[] roster = new EntityDefinition[citizens];
		for (int i = 0; i < citizens; i++)
		{
			roster[i] = regions.wanderer(
				VARROCK_NORTH,
				new WorldPoint(3221 + i, 3420, 0),
				new WorldPoint(3221 + i, 3418, 0),
				new WorldPoint(3223 + i, 3422, 0),
				512);
		}
		regions.file(VARROCK_NORTH, roster);

		EntityScene scene = new EntityScene(client, regions, config, config.overrides(), timings);
		scene.syncRegions(view);
		return scene;
	}

	/**
	 * A block of citizens packed tightly enough round the player that every one of them
	 * clears the cull radius — so the {@link RenderPolicy#MAX_ACTIVE_OBJECTS} cap is
	 * what decides how many are planned, and {@code planned} and {@code candidates}
	 * stop being the same number.
	 *
	 * <p>Ten to a row from (3212, 3412), which is a 10x9 block for 90 citizens: every
	 * tile is inside region {@code VARROCK_NORTH} and within eight tiles of the player,
	 * so nothing is culled and nothing is off-region.
	 */
	private EntityScene packedSceneWith(FrameTimings timings, int citizens)
	{
		EntityDefinition[] roster = new EntityDefinition[citizens];
		for (int i = 0; i < citizens; i++)
		{
			roster[i] = regions.citizen(VARROCK_NORTH, 3212 + (i % 10), 3412 + (i / 10), 0);
		}
		regions.file(VARROCK_NORTH, roster);

		EntityScene scene = new EntityScene(client, regions, config, config.overrides(), timings);
		scene.syncRegions(view);
		return scene;
	}

	/**
	 * One meter's block out of {@link FrameTimings#toReportText()}.
	 *
	 * <p>Needed rather than convenient: the visibility meter and the model-build meter
	 * both label their companion count "objects active", so a bare
	 * {@code report.contains(..)} on that line can be satisfied by the wrong meter — and
	 * in the two-pass fixture it is, which would make the assertion green against the
	 * exact mutation it exists to catch.
	 */
	private static String meterSection(String report, String heading)
	{
		int start = report.indexOf("# " + heading);
		assertTrue("the report has no '" + heading + "' section:\n" + report, start >= 0);

		int end = report.indexOf("\n\n", start);
		return end < 0 ? report.substring(start) : report.substring(start, end);
	}

	private EntityScene sceneWith(FrameTimings timings, int citizens)
	{
		EntityDefinition[] roster = new EntityDefinition[citizens];
		for (int i = 0; i < citizens; i++)
		{
			roster[i] = regions.citizen(VARROCK_NORTH, 3221 + i, 3420, 0);
		}
		regions.file(VARROCK_NORTH, roster);

		EntityScene scene = new EntityScene(client, regions, config, config.overrides(), timings);
		scene.syncRegions(view);
		return scene;
	}

	private static CountingReporter reporter(FrameTimings timings)
	{
		return wireReporter(new CountingReporter(), timings);
	}

	private static RecordingReporter recordingReporter(FrameTimings timings)
	{
		return wireReporter(new RecordingReporter(), timings);
	}

	/**
	 * Everything {@link LivelyCitiesDevReportsPlugin} needs before its cadence and its
	 * shutdown can be called, which is one thing: the stopwatch.
	 *
	 * <p>It is handed over through a real {@link LivelyCitiesPlugin} rather than set on
	 * the reporter directly, because that is exactly how the reporter reaches it in a
	 * client — {@code @PluginDependency} binds the running plugin into the reporter's
	 * injector, and {@code stopwatch()} reads its field. Wiring it any other way here
	 * would leave that hop untested.
	 */
	private static <T extends LivelyCitiesDevReportsPlugin> T wireReporter(T plugin, FrameTimings timings)
	{
		return wireReporter(plugin, timings, true);
	}

	/**
	 * @param processingTicks what the owning plugin says it did with the tick. The
	 * reporter's cadence only counts ticks the plugin actually processed — see
	 * {@link LivelyCitiesDevReportsPlugin#onGameTick} — so a fixture that left this
	 * false would report nothing and every cadence assertion below would pass for the
	 * wrong reason.
	 */
	private static <T extends LivelyCitiesDevReportsPlugin> T wireReporter(
		T plugin, FrameTimings timings, boolean processingTicks)
	{
		LivelyCitiesPlugin owner = new LivelyCitiesPlugin()
		{
			@Override
			boolean processedGameTick()
			{
				return processingTicks;
			}
		};
		owner.frameTimings = timings;
		plugin.livelyCities = owner;
		return plugin;
	}

	/**
	 * The reporter with the report itself taken out — the same seam
	 * {@code DevReportsCacheAuditTest} uses for the cache audit, and for the same
	 * reason: what is under test is when the report runs, not that a file lands on a
	 * real user's disk.
	 */
	private static final class CountingReporter extends LivelyCitiesDevReportsPlugin
	{
		private int reports;

		@Override
		void reportFrameTimings()
		{
			reports++;
		}
	}

	/**
	 * The reporter with the <i>disk</i> taken out but the report left in — one level
	 * below {@link CountingReporter}, which replaces the whole method.
	 *
	 * <p>What that buys is the two things the counting seam is blind to: whether
	 * {@code reportFrameTimings()} decided to write anything at all, and what file name
	 * it chose. Both are decisions the method makes and neither leaves any other trace.
	 */
	private static final class RecordingReporter extends LivelyCitiesDevReportsPlugin
	{
		private final java.util.List<String> fileNames = new java.util.ArrayList<>();
		private final java.util.List<String> texts = new java.util.ArrayList<>();

		@Override
		java.util.concurrent.CompletableFuture<Void> writeReportAsync(
			File outputDir, String fileName, String text, String what)
		{
			fileNames.add(fileName);
			texts.add(text);
			return java.util.concurrent.CompletableFuture.completedFuture(null);
		}
	}
}
