package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Named;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The developer-only half of this plugin's diagnostics: the two reports, and the disk
 * they are written to.
 *
 * <h2>Why it is a second plugin, in the test source set</h2>
 *
 * <p>Filesystem I/O in {@code src/main} costs a hub submission its automated review.
 * riktenx, on <a href="https://github.com/runelite/plugin-hub/pull/12366">plugin-hub#12366</a>:
 * "file i/o will make your plugin require manually review. if you can not use it your
 * plugin can be automatically reviewed." Neither of the two things that wrote a file
 * here is reachable by anybody who installs this plugin — {@code CacheIdAudit} and
 * {@link FrameTimings} are each gated behind {@code --developer-mode} <i>and</i> a JVM
 * system property — so the shipped jar was paying manual-review latency for capabilities
 * its users cannot invoke.
 *
 * <p>Deleting them was one option. The other, and the one riktenx names on
 * <a href="https://github.com/runelite/plugin-hub/pull/13208">plugin-hub#13208</a>, is
 * this: "you can either add a separate debug plugin in the test source set (which won't
 * ship with your plugin and won't get looked at but you can use it during development)
 * or just remove it". {@code src/test/java} is not packaged into the hub's jar, and both
 * {@code ./gradlew runWithTimings} and {@code ./gradlew auditCacheIds} already run on
 * {@code sourceSets.test.runtimeClasspath} — so this class is on the classpath of
 * exactly the two launches that want it and of nothing else. The dev capability is
 * unchanged; it simply stopped shipping.
 *
 * <h2>What stayed behind</h2>
 *
 * <p>The <i>measuring</i> and the <i>auditing</i>, because neither is file I/O.
 * {@link FrameTimings} still owns the histograms, the cadence and
 * {@link FrameTimings#toReportText()}; {@code CacheIdAudit} still owns the cache walk
 * and {@code CacheIdAudit.Report.toReportText()}. Both hand out a finished
 * {@code String}. Turning a {@code String} into a file is this class's job and
 * {@link ReportWriter}'s, and neither exists in the shipped jar.
 *
 * <p>That split is also why {@code src/main} has no {@code if} left over pretending a
 * file might be written. There is no sink to install, no nullable writer, no
 * "reporting enabled" branch: the plugin that reports is simply not loaded unless
 * somebody launched a developer client from this repo's test classpath.
 *
 * <h2>How it reaches the stopwatch</h2>
 *
 * <p>{@link PluginDependency} makes RuneLite build this plugin's injector as a
 * <i>child</i> of {@link LivelyCitiesPlugin}'s (see {@code PluginManager.instantiate}:
 * with one dependency the parent injector is {@code deps.get(0).getInjector()}), and
 * bind that plugin instance into it. So {@link #livelyCities} is the running plugin,
 * and {@link #stopwatch()} reads the very {@link FrameTimings} singleton
 * {@code EntityScene} is measuring into.
 *
 * <p><b>Deliberately not {@code @Inject FrameTimings}.</b> That would rely on Guice
 * hoisting the just-in-time binding into the root injector so both plugins share one
 * instance — which it does do, but a mistake there produces a report full of zeros
 * rather than an error, and a silently-wrong instrument is the one failure this whole
 * class of tooling exists to prevent. Going through the plugin instance cannot be
 * wrong: if the dependency were unmet, RuneLite throws
 * {@code PluginInstantiationException} at load and nothing starts.
 *
 * <p>{@link #regionDataLoader} <i>is</i> injected directly, and the asymmetry is the
 * explanation: it is a stateless reader of classpath resources, so a second instance
 * would give identical answers. The stopwatch is state, and there must be exactly one.
 */
@PluginDependency(LivelyCitiesPlugin.class)
@PluginDescriptor(
	name = "Lively Cities dev reports",
	description = "Developer-only: writes the Lively Cities frame-timing and cache-id reports",
	tags = {"developer", "lively", "cities"},
	developerPlugin = true,
	loadInSafeMode = false
)
public class LivelyCitiesDevReportsPlugin extends Plugin
{
	/**
	 * Set on {@code ./gradlew auditCacheIds} (never on {@code ./gradlew run} or
	 * {@code ./gradlew test}) to ask {@link #runCacheAudit()} to run once at startup.
	 * Read with {@link Boolean#getBoolean}, a plain JVM system property — no new
	 * argument parsing, no reflection.
	 *
	 * <p>Checked in addition to {@link #developerMode}, not instead of it, and both are
	 * still checked even though this class no longer ships: the audit is a real
	 * client-cache walk over every id the dataset references, and an ordinary
	 * {@code ./gradlew run} session must not pay for it.
	 */
	static final String CACHE_AUDIT_SYSTEM_PROPERTY = "livelycities.validateCacheIds";

	/** Under {@code ~/.runelite/lively-cities/} — see {@link ReportWriter}. */
	static final String CACHE_AUDIT_REPORT_FILE_NAME = "model-id-audit.txt";

	/** Under {@code ~/.runelite/lively-cities/} — see {@link ReportWriter}. */
	static final String FRAME_REPORT_FILE_NAME = "frame-timings.txt";

	/**
	 * Plain slf4j rather than Lombok's {@code @Slf4j}: Lombok is wired into this build
	 * for the main source set only ({@code compileOnly} / {@code annotationProcessor}),
	 * and adding {@code testAnnotationProcessor} purely for one logger would change the
	 * dependency block a hub reviewer reads for a class that never reaches them.
	 */
	private static final Logger log = LoggerFactory.getLogger(LivelyCitiesDevReportsPlugin.class);

	/**
	 * The running {@link LivelyCitiesPlugin}, bound into this plugin's parent injector
	 * by {@link PluginDependency}. Read only for {@link #stopwatch()}.
	 */
	@Inject
	LivelyCitiesPlugin livelyCities;

	@Inject
	Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	RegionDataLoader regionDataLoader;

	/**
	 * True when the client was launched with {@code --developer-mode}. Bound by
	 * {@code RuneLiteModule} for every launch, so this is always resolvable and defaults
	 * to {@code false} when a test constructs this plugin directly instead of through
	 * Guice.
	 */
	@Inject
	@Named("developerMode")
	boolean developerMode;

	/**
	 * The one {@link FrameTimings} in the client — the instance {@code EntityScene}
	 * records into. See the class javadoc for why it is fetched this way.
	 *
	 * <p>Package-private and non-final so a test can hand over its own meter without
	 * standing up a {@link LivelyCitiesPlugin}.
	 */
	FrameTimings stopwatch()
	{
		return livelyCities.frameTimings;
	}

	@Override
	protected void startUp()
	{
		// Both flags, deliberately: developerMode alone is true for every ordinary
		// `./gradlew run`, and the cache id audit is a deliberate, on-demand action.
		if (developerMode && Boolean.getBoolean(CACHE_AUDIT_SYSTEM_PROPERTY))
		{
			clientThread.invoke(this::runCacheAudit);
		}

		if (stopwatch().isEnabled())
		{
			// Said out loud, because a measurement running silently is a measurement
			// somebody forgets is running — and because "is it actually on?" is the
			// first question when the report file turns out to be empty.
			log.info("Lively Cities: frame timings are on ({} is set) — a summary every {} game "
					+ "tick(s), and a report in ~/.runelite/lively-cities/{}",
				FrameTimings.SYSTEM_PROPERTY, FrameTimings.REPORT_INTERVAL_TICKS,
				FRAME_REPORT_FILE_NAME);
		}
	}

	/**
	 * The cache-backed half of the durability tooling: walks every distinct model id,
	 * merged-object id and animation id {@code RegionData/*.json} references and asks
	 * the live client whether each one still resolves — see {@code CacheIdAudit}'s
	 * javadoc for why this cannot run in the normal test suite.
	 *
	 * <p>Runs once, from the client thread (required — every model/animation load in
	 * this plugin goes through the client), and only ever on demand: see
	 * {@link #CACHE_AUDIT_SYSTEM_PROPERTY}.
	 *
	 * <p>Package-private and non-final so a test can override it and count the calls —
	 * the developer-mode gate above is what is under test there, not the audit's own
	 * logic, which {@code CacheIdAuditTest} covers directly against {@code FakeClient}.
	 */
	void runCacheAudit()
	{
		log.info("Lively Cities: {} is set, running the cache id audit", CACHE_AUDIT_SYSTEM_PROPERTY);

		CacheIdAudit.DatasetIds dataset = CacheIdAudit.collect(regionDataLoader);
		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		log.info("Lively Cities cache id audit: {} model id(s) checked ({} failing), "
				+ "{} merged-object id(s) checked ({} failing), {} animation id(s) checked "
				+ "({} failing, {} known permanently null)",
			dataset.modelIds.size(), report.failingModelIds.size(),
			dataset.mergedObjectIds.size(), report.failingMergedObjectIds.size(),
			dataset.animationIdsByName.size(), report.failingAnimations.size(),
			report.knownPermanentNullAnimations.size());

		// Disk I/O never happens on the client thread. The text is built here, where
		// the report was, so what crosses the thread boundary is a finished snapshot
		// rather than an object still holding collections.
		writeReportAsync(reportDir(), CACHE_AUDIT_REPORT_FILE_NAME, report.toReportText(),
			"cache id audit");
	}

	/**
	 * The frame-timing cadence: one info line and one file, every
	 * {@link FrameTimings#REPORT_INTERVAL_TICKS} game ticks.
	 *
	 * <p><b>Priority, not registration order.</b> RuneLite's {@code EventBus} sorts
	 * subscribers by {@code Subscribe.priority()}, highest first, so a negative priority
	 * puts this after {@link LivelyCitiesPlugin#onGameTick} — which is where the
	 * visibility pass this is measuring actually happens. The tick that triggers a
	 * report is therefore a tick the report already includes, exactly as it was when
	 * both halves lived in one class.
	 *
	 * <p><b>And only ticks the plugin actually processed count.</b> Before the split
	 * this call was the last statement of {@code LivelyCitiesPlugin.tick()}, <i>after</i>
	 * its "no player or no world view yet" early return, so a tick that did no work
	 * never advanced the cadence. Calling it unconditionally here would quietly change
	 * the population the report describes from "ticks the plugin processed" to "ticks
	 * that happened" — and with Lively Cities toggled off and this reporter left on, the
	 * 300-tick cadence would keep firing and rewriting the file out of stale samples.
	 * {@link LivelyCitiesPlugin#processedGameTick()} is the seam that keeps the old
	 * meaning, and the {@code -1f} priority above is what makes it readable: the plugin
	 * has already run and recorded its answer by the time this asks.
	 */
	@Subscribe(priority = -1f)
	public void onGameTick(GameTick event)
	{
		if (!livelyCities.processedGameTick())
		{
			return;
		}

		if (stopwatch().onGameTick())
		{
			reportFrameTimings();
		}
	}

	/**
	 * The tail of the session, on the way out.
	 *
	 * <p>Non-blocking, per {@code AGENTS.md} — the periodic report is the one to rely on
	 * if the client is killed rather than closed. Nothing here touches
	 * {@link LivelyCitiesPlugin}'s scene, so it does not matter which of the two plugins
	 * RuneLite shuts down first.
	 */
	@Override
	protected void shutDown()
	{
		reportFrameTimings();
	}

	/**
	 * Builds the frame report and hands it to a background thread.
	 *
	 * <p>The text is built here, on the client thread, and written on a background one:
	 * disk I/O never happens on the client thread, and a report handed over as finished
	 * text is a snapshot rather than a view of counters that are still moving.
	 *
	 * <p>Unreachable for an ordinary user twice over: this class is not in the shipped
	 * jar at all, and {@link FrameTimings#onGameTick()} returns false forever unless both
	 * halves of its gate are set.
	 *
	 * <p><b>Nothing is written when there is nothing to say.</b> {@link #shutDown()}
	 * calls this unconditionally, so without the {@code hasSamples()} guard a client
	 * that was started and closed without the stopwatch ever taking a sample would
	 * replace a real report with a file that says "no samples" three times over.
	 *
	 * <p>Package-private and non-final so a test can override it and count the calls.
	 * What needs asserting is <i>when</i> this runs, and under which file name
	 * ({@link #writeReportAsync}); that it can write a file is {@code ReportWriterTest}'s,
	 * against a temp directory rather than the real {@code ~/.runelite}.
	 */
	void reportFrameTimings()
	{
		FrameTimings frameTimings = stopwatch();
		if (!frameTimings.hasSamples())
		{
			return;
		}

		log.info(frameTimings.summaryLine());

		writeReportAsync(reportDir(), FRAME_REPORT_FILE_NAME, frameTimings.toReportText(),
			"frame timings");
	}

	/**
	 * Hands one finished report to a background thread.
	 *
	 * <p>Package-private and non-final so a test can run it inline and see what it was
	 * handed — the same seam, for the same reason, as {@link #reportFrameTimings()} and
	 * {@link #runCacheAudit()}, one level further down. It exists because the
	 * <b>file name</b> is the one argument at these call sites that nothing else could
	 * check. {@link ReportWriter} has been parameterised by file name since it grew a
	 * second caller, so each caller names its own constant — and passing
	 * {@link #CACHE_AUDIT_REPORT_FILE_NAME} from the frame-timing path compiles,
	 * type-checks, and silently makes the frame report overwrite the model id audit.
	 *
	 * @param what what to call it in the log line, so a failure names the report that
	 *             failed rather than "a report"
	 */
	CompletableFuture<Void> writeReportAsync(File outputDir, String fileName, String text, String what)
	{
		return CompletableFuture.runAsync(() -> writeReport(outputDir, fileName, text, what));
	}

	/**
	 * Writes one finished report. Background thread only — see {@link ReportWriter}.
	 */
	private static void writeReport(File outputDir, String fileName, String text, String what)
	{
		try
		{
			File file = ReportWriter.write(outputDir, fileName, text);
			log.info("Lively Cities {} report written to {}", what, file);
		}
		catch (IOException e)
		{
			log.warn("Lively Cities {}: could not write the report under {}", what, outputDir, e);
		}
	}

	/**
	 * The plugin's own subdirectory of {@code ~/.runelite}.
	 *
	 * <p>Resolved per call rather than held in a static, because
	 * {@code RuneLite.RUNELITE_DIR} is a static that a test never wants written to and
	 * that neither report is on a hot path for.
	 */
	private static File reportDir()
	{
		return new File(RuneLite.RUNELITE_DIR, "lively-cities");
	}
}
