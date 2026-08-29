package com.matthewmariner.livelycities;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * How long this plugin's own work actually takes, measured rather than promised.
 *
 * <p><b>Why this exists.</b> The predecessor plugin's README claimed "no lag or
 * resource issues" and never measured anything. Two people asked about FPS on
 * Reddit and got no answer, and that silence is part of why the plugin was
 * dismissed as slapped together. A number nobody can produce is the same as no
 * number, so this is instrumentation rather than a benchmark: a real figure falls
 * out of ordinary play, on a cadence, into the log and into a file.
 *
 * <p><b>This class measures; it does not write.</b> Everything here is counters and
 * text — {@link #summaryLine()} and {@link #toReportText()} hand out finished
 * {@code String}s and nothing else. The cadence is read here and acted on by
 * {@code LivelyCitiesDevReportsPlugin}, which lives in the test source set and never
 * ships, because filesystem I/O in the shipped jar costs the Plugin Hub submission its
 * automated review (riktenx, plugin-hub#12366 and #13208). So in a hub user's client
 * nothing calls {@link #onGameTick()} at all: the meters exist, the gate below keeps
 * them empty, and there is no reporting branch anywhere in {@code src/main} to look at.
 *
 * <p><b>Six meters, and the split is the whole point</b>, because they run on
 * different clocks and only one of them competes with the frame rate. Three of them
 * are the plugin's per-tick work broken apart, one is their sum, and one is per
 * frame:
 * <ul>
 *   <li><b>{@code game tick}</b> — the sum: every microsecond this plugin spends on
 *       one game tick, region loading included. The honest headline, and the reason
 *       the split below cannot hide anything.</li>
 *   <li><b>{@code region load}</b> — once per region <i>file</i>, when a scene load
 *       first brings it into scope: parse the JSON, validate every record, derive the
 *       echoes, build the wrappers. It happens in {@code syncRegions}, which
 *       {@code LivelyCitiesPlugin.tick()} calls immediately before the visibility
 *       pass, so it lands on the same tick and on no other.</li>
 *   <li><b>{@code visibility pass}</b> — once per game tick (600ms) and once per
 *       settings change: who is on screen, who is not, and who walks. <b>Exclusive of
 *       model building</b> — see below.</li>
 *   <li><b>{@code model build}</b> — once per entity, the first time it spawns.
 *       Bursty by nature: walking into Varrock wants dozens of models inside one
 *       visibility pass.</li>
 *   <li><b>{@code model build burst}</b> — what one pass spent building, summed.
 *       This is precisely the quantity {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS}
 *       exists to bound, so it is the line a re-measurement checks the cap against.</li>
 *   <li><b>{@code interpolation}</b> — once per rendered frame, and the only
 *       per-frame work this plugin does. It walks the active wandering citizens and
 *       nothing else; the right-click clickbox — the cost centre the plan named — is
 *       computed in {@code MenuOpened} and never per frame, so this number is
 *       expected to be near zero and the point of measuring it is to be able to say
 *       so with a figure instead of an argument.</li>
 * </ul>
 *
 * <h2>Why the visibility figure is exclusive of building, as of 2026-08-29</h2>
 *
 * <p>It used to be inclusive, and that made its percentiles describe two different
 * events at once. A steady-state pass and the pass that walks into a new city are not
 * the same product: one is a rhythmic cost paid every 600ms forever, the other is a
 * one-off at a region border. Averaging them into one p99 makes both figures less
 * meaningful, and it made the 2026-08-29 report unreadable in exactly that way — a
 * visibility p99 of 5.50ms and a worst pass of 18.31ms, both of which turn out to be
 * model building and neither of which says anything about deciding who is visible.
 *
 * <p>So the visibility meter now records the pass <i>minus</i> whatever it spent
 * building, the builds are reported as their own burst, and the two are added back up
 * in {@code game tick}. Nothing is discarded and nothing is hidden; three numbers with
 * three thresholds replace one number judged against a threshold written for a
 * different thing.
 *
 * <p><b>A distribution, not a mean.</b> A mean hides exactly the thing that matters:
 * one 40ms model-building tick inside three minutes of 30µs ticks averages to
 * nothing and is still a visible hitch. Samples go into a fixed histogram — 1µs
 * buckets to 1ms, then 100µs buckets to 11ms, then one overflow bucket — so median
 * and high percentiles are computed over the <i>whole session</i> in constant memory
 * and with no allocation on the hot path. The maximum is tracked exactly and
 * separately, so the overflow bucket can never hide the worst case.
 *
 * <p><b>The count is reported beside the time</b>, because "1ms at 8 objects" and
 * "1ms at 76" are different claims. Each meter carries a companion counter — active
 * objects for the two tick-clocked meters, interpolated walkers for the frame one —
 * and reports the value recorded at its own worst sample, which is the one a reader
 * actually wants.
 *
 * <h2>What the numbers should say</h2>
 *
 * <p>Written down in advance, so the measurement cannot be graded on a curve after
 * the fact. A frame at 60fps is 16.7ms; a game tick is 600ms but the work still
 * happens inside one frame, so the tick-clocked meters are judged against the frame
 * budget too, just less often.
 *
 * <ul>
 *   <li><b>Interpolation, p99 ≤ 0.5ms</b> — 3% of a frame — is <b>acceptable</b>, and
 *       given it is a loop over at most a few dozen wanderers doing one
 *       {@code setLocation} each, anything at all is a surprise. <b>p99 &gt; 2ms</b>
 *       (12% of a frame, every frame) is <b>a problem</b> and means the per-frame pass
 *       has acquired work that belongs on the tick. <i>(Registered 2026-08-24,
 *       unchanged.)</i></li>
 *   <li><b>Visibility pass, p99 ≤ 2ms</b> is <b>acceptable</b>: it lands in one frame
 *       out of every thirty-six, and an eighth of a frame once every 600ms is not
 *       something a player can see. <b>p99 &gt; 8ms</b> is <b>a problem</b> — half a
 *       frame, on a schedule, is a rhythmic stutter. <i>(Registered 2026-08-24. The
 *       numbers are unchanged; what changed on 2026-08-29 is that the figure they
 *       judge no longer contains model building, which is not a rhythmic cost and was
 *       never what this sentence was about.)</i></li>
 *   <li><b>Model building, max ≤ 20ms for one model</b> is <b>acceptable</b> — a
 *       thirtieth of a game tick, once, at a region border where the client is already
 *       busy. <b>Any single build over 50ms</b> is <b>a problem</b>. <i>(Registered
 *       2026-08-24, unchanged.)</i></li>
 *   <li><b>A model build burst, max ≤ one frame (16.7ms) for what a single pass
 *       spends building</b> is <b>acceptable</b>; <b>over 50ms</b> is <b>a
 *       problem</b>. <i>(Registered 2026-08-24 as "≤ 100ms for the burst that lands on
 *       entering a dense region", tightened 2026-08-29 — see
 *       {@link RenderPolicy#CROSSING_TICK_BUDGET_MICROS}. 100ms is six frames, and it
 *       was written when nothing capped the burst at all. Now that
 *       {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} bounds it, the acceptable figure
 *       is the one the cap is derived to hold. A threshold that moves has to move in
 *       public; this one moved down.)</i></li>
 *   <li><b>Region load, max ≤ 5ms for one region file</b> is <b>acceptable</b>;
 *       <b>over 20ms</b> is <b>a problem</b>. <i>(Registered 2026-08-29, newly
 *       measured and newly metered.)</i> It is paid once per file per scene load, on a
 *       tick where the client is already rebuilding a 104x104 scene: 5ms is under a
 *       third of a frame, and 20ms is more than a whole one at every border crossing,
 *       which is a hitch a player would learn to expect. The densest shipped file,
 *       region 12853, times at 1.9ms offline.</li>
 *   <li><b>A game tick, p99 ≤ 2ms and max ≤ one frame (16.7ms)</b> is
 *       <b>acceptable</b>; <b>p99 &gt; 8ms or any tick over 50ms</b> is <b>a
 *       problem</b>. <i>(Registered 2026-08-29, newly metered.)</i> The p99 is the
 *       visibility pass's own pair of lines, because the overwhelming majority of ticks
 *       are exactly a visibility pass and nothing else; the maximum is the crossing
 *       tick, which is allowed one dropped frame and no more.</li>
 * </ul>
 *
 * <h2>What the numbers actually said, and what changed because of it</h2>
 *
 * <p><b>2026-08-24, 300 game ticks in Varrock, a human playing, client 1.12.36.</b>
 * Interpolation passed by seventy times (p99 7µs against a 0.5ms bar) and no single
 * model build came near the 20ms bar (max 15.45ms, p99 1.50ms). <b>The visibility pass
 * failed</b>: p99 in the overflow bucket at ≥11ms and a worst pass of 53.73ms, against a
 * bar of 8ms. 371 builds landed across 331 passes, so the spike was never one slow
 * model — it was <i>forty</i> ordinary ones inside one game tick, which is what walking
 * into Varrock square looked like from here. The answer was
 * {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS}: cap a pass's builds, and let the rest
 * wait a tick, nearest first.
 *
 * <p><b>2026-08-29, the same walk, 300 game ticks, 184 entities.</b> The cap worked —
 * max 18.31ms against 53.73ms, p99 5.50ms against ≥11ms — and the measurement then
 * falsified the theory it was built on. The worst pass had <b>three objects active</b>,
 * i.e. it was the pass that could build almost nothing, and the worst model build in
 * the session was 8.61ms, so most of an 18ms pass was not model building at all.
 *
 * <p>The client log identifies that pass exactly, and it is the answer:
 * {@code visibility pass: +3 -0 (failed 0, 72 build(s) held over), 3 active of 164 in
 * scope} — the <b>first</b> pass of the session, on the scene load that brought Varrock
 * in. Twenty-three later passes have that same shape, 164 in scope and three built, and
 * every one of them is under 5.50ms because the p99 says only three samples are not. So
 * the extra fifteen milliseconds are not the shape of the pass. They are the cost of
 * running it for the first time: class loading, lambda linkage and interpretation for
 * the whole spawn path, on top of the session's first model build, which cost 8.61ms
 * for the same reason. A benchmark of the same scene against the real shipped data
 * makes that arithmetic rather than an argument — 32ms for the first
 * {@code updateVisibility} in a fresh JVM with a fake client that builds nothing, 921µs
 * for the identical pass on a second scene in the same JVM, and 25µs steady state.
 *
 * <p>Two things changed because of it, and neither is a widened threshold. The
 * visibility figure became <b>exclusive</b> of model building, so its pre-registered
 * 2ms/8ms lines judge the decision work they were written for; and
 * {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} was re-derived against a crossing-tick
 * budget instead of against the visibility pass's line, which raised it from three to
 * nine — because at three the measurement shows Varrock's crowd taking fourteen seconds
 * to arrive, and it was paying that for a spike that was never model building.
 *
 * <p><b>The thing the old thresholds did not reconcile is now reconciled.</b> They
 * called a single 20ms model build acceptable and a visibility p99 over 8ms a problem,
 * while every build happened <i>inside</i> a visibility pass — so a lone acceptable
 * build sat inside a problem pass and no cap could fix it. The two figures no longer
 * contain each other: a build is judged as a build, a burst of them as a burst, the
 * decision work on its own, and {@code game tick} adds all of it back up so the split
 * cannot be used to make a slow tick look fast.
 *
 * <p>Once measured, the figures belong in the README's Known limitations section and
 * in the performance sentence of {@code docs/SUBMISSION.md}'s PR body.
 *
 * <h2>Cost when nobody is looking</h2>
 *
 * <p>{@link #enabled} is a {@code final} field set once at construction. Disabled,
 * {@link #start()} returns {@code 0} without calling {@link System#nanoTime()} and
 * every {@code record} method returns on the same field read: no allocation, no clock
 * read, no formatting, and a branch on a final field that predicts perfectly.
 *
 * <p><b>What that does not say</b>, since an earlier revision of this paragraph claimed
 * "one branch per pass, not per entity" and that was not true. Wiring the model-build
 * meter cost {@code EntityScene} two {@code getRenderedModel()} calls per wanted
 * not-yet-active entity, on every pass, meter or no meter. Both are plain field reads
 * ({@code LivelyEntity.getRenderedModel()}), so the cost really is nil — but "nil"
 * and "not per entity" are different statements and only one of them is accurate. The
 * distinction is kept here because a performance claim this class cannot support is
 * the specific thing it was built to stop making.
 *
 * <p>The guard is deliberately <i>not</i> a log level: this repo has
 * already been bitten by {@code log.debug} arguments being evaluated eagerly (see
 * {@code EntityScene.updateVisibility}, where {@code countActive()} had to be pulled
 * out from inside a {@code log.debug} call), and a timer whose guard costs a
 * {@code nanoTime} would be the same mistake with a stopwatch attached.
 *
 * <p>Not a config item, for the same reason {@code CacheIdAudit} is not: a dev-only
 * capability compiled into the shipped jar is fine, a dev-only capability an ordinary
 * user can switch on is not. Both the {@code --developer-mode} launcher flag and
 * {@link #SYSTEM_PROPERTY} have to be set — {@code ./gradlew runWithTimings} sets
 * both, {@code ./gradlew run} and {@code ./gradlew test} set neither.
 *
 * <p><b>Client thread only.</b> Every caller is already on it, and the counters are
 * plain fields with no synchronisation precisely because there is only ever one
 * writer.
 */
@Singleton
final class FrameTimings
{
	/**
	 * Set by {@code ./gradlew runWithTimings} and by nothing else. Read with
	 * {@link Boolean#getBoolean}, a plain JVM system property — the same mechanism,
	 * for the same reasons, as
	 * {@code LivelyCitiesDevReportsPlugin.CACHE_AUDIT_SYSTEM_PROPERTY}.
	 */
	static final String SYSTEM_PROPERTY = "livelycities.frameTimings";

	/**
	 * Game ticks between reports: 300, i.e. three minutes.
	 *
	 * <p>Short enough that a quick look around a city produces a report, long enough
	 * that the log line is not something a developer starts scrolling past. The report
	 * is cumulative and each write overwrites the last, so it is always the whole
	 * session so far rather than a window — which means a client that is killed rather
	 * than closed still leaves a report behind, at most three minutes stale.
	 */
	static final int REPORT_INTERVAL_TICKS = 300;

	private final boolean enabled;

	private final Meter gameTick = new Meter(
		"game tick (region load + visibility pass, all of it)", "objects active");
	private final Meter visibility = new Meter(
		"visibility pass (per game tick, excluding model builds)", "objects active");
	private final Meter modelBuild = new Meter("model build (per first spawn)", "objects active");
	private final Meter buildBurst = new Meter(
		"model build burst (per visibility pass)", "models built");
	private final Meter regionLoad = new Meter("region load (per region file)", "entities");
	private final Meter interpolation = new Meter("interpolation (per frame)", "walkers");

	/**
	 * What the visibility pass currently in flight has spent building, and how many
	 * builds that was.
	 *
	 * <p><b>Accumulated here rather than in {@link EntityScene}</b>, and that is what
	 * keeps "off costs one field read" true. The scene would have to subtract
	 * {@code System.nanoTime()} twice per build to hand over a figure, which is a clock
	 * read per build in a client that is not measuring anything;
	 * {@link #recordModelBuild} already has the elapsed time in a local, inside the
	 * {@link #enabled} guard, so adding it up costs a disabled client nothing at all.
	 *
	 * <p>Reset by {@link #recordVisibility}, which is the end of the pass these belong
	 * to. A build outside a pass is impossible — {@code EntityScene} only ever builds
	 * inside {@code runVisibilityPass} — so there is no path that leaks one burst into
	 * the next.
	 */
	private long passBuildNanos;
	private int passBuilds;

	/**
	 * What {@code syncRegions} spent loading region files since the last visibility
	 * pass, for the {@code game tick} total.
	 *
	 * <p>Sound because of the order {@code LivelyCitiesPlugin.tick()} runs things in:
	 * {@code syncRegions} first, then the pass. So everything accumulated here belongs
	 * to the tick the next visibility sample closes, and there is no tick boundary for
	 * this class to be told about.
	 */
	private long tickRegionLoadNanos;

	private long ticks;
	private int ticksSinceReport;

	/**
	 * How many model builds {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} has held over
	 * to a later pass, and over how many passes.
	 *
	 * <p>Counters rather than a fourth {@link Meter}: a deferral has no duration. It is
	 * the absence of work, and the whole point of recording it is that the model-build
	 * meter's sample count no longer accounts for every entity that wanted a model — so
	 * without this the report would show a suspiciously quiet burst and no reason for it.
	 *
	 * <p><b>Counts budget deferrals and nothing else.</b> A spawn the client could not
	 * satisfy — a cold model cache — is also a build that did not happen, and it is
	 * deliberately invisible here as it always was: it records no sample and no deferral,
	 * because it neither timed anything nor was held back by anything. The two are
	 * different events and a reader has to be able to tell "we chose not to build this
	 * yet" from "we asked and the cache said no". {@code FrameTimingsTest} pins both
	 * directions.
	 */
	private long buildsDeferred;
	private long passesWithDeferredBuilds;

	/**
	 * The shipping gate: both halves, or off.
	 *
	 * @param developerMode the {@code --developer-mode} launcher flag, bound by
	 *                      {@code RuneLiteModule} for every launch
	 */
	@Inject
	FrameTimings(@Named("developerMode") boolean developerMode)
	{
		this(developerMode, Boolean.getBoolean(SYSTEM_PROPERTY));
	}

	/**
	 * The gate with both of its inputs supplied.
	 *
	 * <p>Package-private so the tests can drive all four combinations without setting
	 * a system property, and so that "both are needed" is a claim a test can break
	 * rather than a sentence in a comment.
	 */
	FrameTimings(boolean developerMode, boolean systemPropertySet)
	{
		this.enabled = developerMode && systemPropertySet;
	}

	/** An instance that measures nothing — what every test that is not about timing gets. */
	static FrameTimings off()
	{
		return new FrameTimings(false, false);
	}

	/**
	 * @return true if anything at all is being measured. Read by callers that would
	 * otherwise have to do work purely to feed a meter; the {@code record} methods
	 * check it themselves, so an ordinary caller does not need this.
	 */
	boolean isEnabled()
	{
		return enabled;
	}

	/**
	 * @return a start timestamp to hand back to one of the {@code record} methods, or
	 * {@code 0} when disabled — in which case {@link System#nanoTime()} is never
	 * called
	 */
	long start()
	{
		return enabled ? System.nanoTime() : 0L;
	}

	/**
	 * Closes one visibility pass, and with it one game tick.
	 *
	 * <p>Three samples come out of this call, and the arithmetic is the whole point of
	 * the 2026-08-29 split:
	 * <ul>
	 *   <li>{@code visibility} gets the elapsed time <b>minus</b> what the pass spent
	 *       building, which is the cost of deciding who is on screen;</li>
	 *   <li>{@code model build burst} gets what it spent building, if anything;</li>
	 *   <li>{@code game tick} gets the elapsed time <b>plus</b> the region loads that
	 *       ran in {@code syncRegions} before this pass — the whole of what this plugin
	 *       cost the tick.</li>
	 * </ul>
	 *
	 * <p>The subtraction is clamped at zero. It cannot go negative — the builds happened
	 * inside the interval being measured — but a meter that could index a histogram with
	 * a negative number inside an event handler is a bad way to find out otherwise.
	 *
	 * @param startedAt     the value {@link #start()} returned
	 * @param activeObjects how many {@code RuneLiteObject}s this pass left the client
	 *                      holding
	 */
	void recordVisibility(long startedAt, int activeObjects)
	{
		if (!enabled)
		{
			return;
		}

		final long elapsed = System.nanoTime() - startedAt;

		visibility.record(Math.max(0L, elapsed - passBuildNanos), activeObjects);
		if (passBuilds > 0)
		{
			buildBurst.record(passBuildNanos, passBuilds);
		}
		gameTick.record(elapsed + tickRegionLoadNanos, activeObjects);

		passBuildNanos = 0L;
		passBuilds = 0;
		tickRegionLoadNanos = 0L;
	}

	/**
	 * @param startedAt     the value {@link #start()} returned
	 * @param activeObjects how many objects this pass is putting on screen — a build at
	 *                      76 active is a different claim from one at 8
	 */
	void recordModelBuild(long startedAt, int activeObjects)
	{
		if (!enabled)
		{
			return;
		}

		final long elapsed = System.nanoTime() - startedAt;
		modelBuild.record(elapsed, activeObjects);

		// The burst this build belongs to, and the subtraction the pass is about to do.
		passBuildNanos += elapsed;
		passBuilds++;
	}

	/**
	 * One region file brought into scope: parsed, validated, echoed and wrapped.
	 *
	 * @param startedAt the value {@link #start()} returned
	 * @param entities  how many wrappers came out of it, authored and derived together —
	 *                  "2.4ms for four entities" and "2.4ms for a hundred and ten" are
	 *                  different claims about the same milliseconds
	 */
	void recordRegionLoad(long startedAt, int entities)
	{
		if (!enabled)
		{
			return;
		}

		final long elapsed = System.nanoTime() - startedAt;
		regionLoad.record(elapsed, entities);

		// Charged to the tick the next visibility pass closes — see tickRegionLoadNanos.
		tickRegionLoadNanos += elapsed;
	}

	/**
	 * @param startedAt the value {@link #start()} returned
	 * @param walkers   how many wandering citizens this frame interpolated
	 */
	void recordFrame(long startedAt, int walkers)
	{
		record(Pass.INTERPOLATION, startedAt, walkers);
	}

	/**
	 * @param deferred how many model builds this visibility pass held over to a later
	 *                 one. Zero on almost every pass and ignored, so "passes with
	 *                 deferrals" counts the bursts rather than the ticks.
	 */
	void recordBuildsDeferred(int deferred)
	{
		if (enabled && deferred > 0)
		{
			buildsDeferred += deferred;
			passesWithDeferredBuilds++;
		}
	}

	/** @return how many model builds the per-pass budget has held over this session */
	long getBuildsDeferred()
	{
		return buildsDeferred;
	}

	/** @return how many visibility passes held at least one build over */
	long getPassesWithDeferredBuilds()
	{
		return passesWithDeferredBuilds;
	}

	private void record(Pass pass, long startedAt, int companionCount)
	{
		if (enabled)
		{
			meterFor(pass).record(System.nanoTime() - startedAt, companionCount);
		}
	}

	/**
	 * One sample with its duration <b>stated</b> rather than clocked.
	 *
	 * <p>The seam the distribution tests drive, and it has to exist: a test that
	 * produced a real 300µs pass by sleeping or spinning would be asserting about the
	 * machine it ran on, and at 1µs bucket resolution a scheduler hiccup would move the
	 * answer. Stating the duration makes "the median of these nine samples is that one"
	 * an arithmetic claim.
	 *
	 * <p>Package-private, unused by the plugin, and it goes through exactly the same
	 * {@link Meter#record} the clocked path does — so what the tests exercise is the
	 * histogram rather than a second implementation of it.
	 */
	void recordElapsed(Pass pass, long elapsedNanos, int companionCount)
	{
		if (enabled)
		{
			meterFor(pass).record(elapsedNanos, companionCount);
		}
	}

	private Meter meterFor(Pass pass)
	{
		switch (pass)
		{
			case GAME_TICK:
				return gameTick;
			case VISIBILITY:
				return visibility;
			case MODEL_BUILD:
				return modelBuild;
			case BUILD_BURST:
				return buildBurst;
			case REGION_LOAD:
				return regionLoad;
			default:
				return interpolation;
		}
	}

	/** @return how many samples this meter holds */
	long sampleCount(Pass pass)
	{
		return meterFor(pass).count;
	}

	/** @return the slowest sample, in microseconds, or {@code -1} if there are none */
	long maxMicros(Pass pass)
	{
		return meterFor(pass).maxMicros;
	}

	/** @return the companion count recorded alongside the slowest sample */
	int countAtMax(Pass pass)
	{
		return meterFor(pass).countAtMax;
	}

	/**
	 * @return the lower edge, in microseconds, of the histogram bucket the given
	 * percentile falls in, or {@code -1} if there are no samples
	 */
	long percentileMicros(Pass pass, double fraction)
	{
		return meterFor(pass).percentileMicros(fraction);
	}

	/**
	 * The six things measured, and the clocks they run on. See the class javadoc.
	 *
	 * <p>{@link #GAME_TICK} is the sum of {@link #VISIBILITY}, {@link #BUILD_BURST} and
	 * whatever {@link #REGION_LOAD} ran on the same tick, so it is derived rather than
	 * separately clocked — but it is a distribution in its own right, and the maximum of
	 * a sum is not the sum of the maxima.
	 */
	enum Pass
	{
		GAME_TICK,
		VISIBILITY,
		MODEL_BUILD,
		BUILD_BURST,
		REGION_LOAD,
		INTERPOLATION
	}

	/**
	 * Advances the report clock by one game tick.
	 *
	 * <p>The only caller is {@code LivelyCitiesDevReportsPlugin} in the test source set,
	 * so in a shipped client this is never called and {@link #getTicks()} stays at zero.
	 *
	 * @return true if a report is due now. Always false when disabled, so even that
	 * caller's reporting path is closed unless both halves of the gate are set.
	 */
	boolean onGameTick()
	{
		if (!enabled)
		{
			return false;
		}

		ticks++;
		if (++ticksSinceReport < REPORT_INTERVAL_TICKS)
		{
			return false;
		}

		ticksSinceReport = 0;
		return true;
	}

	/** @return how many game ticks have been counted. For the tests and the report. */
	long getTicks()
	{
		return ticks;
	}

	/**
	 * @return true if anything has been measured yet. A report over zero samples says
	 * nothing and should not be written.
	 */
	boolean hasSamples()
	{
		return gameTick.count > 0
			|| visibility.count > 0
			|| modelBuild.count > 0
			|| buildBurst.count > 0
			|| regionLoad.count > 0
			|| interpolation.count > 0;
	}

	/**
	 * One line for the client log, at info, on the {@link #REPORT_INTERVAL_TICKS}
	 * cadence and never otherwise.
	 */
	String summaryLine()
	{
		return "Lively Cities frame timings after " + ticks + " game tick(s) — "
			+ gameTick.summary() + "; " + visibility.summary() + "; " + modelBuild.summary()
			+ "; " + buildBurst.summary() + "; " + deferralSummary()
			+ "; " + regionLoad.summary() + "; " + interpolation.summary();
	}

	/**
	 * The build budget's own line, in the same shape as a meter's.
	 *
	 * <p>Printed even when it is zero, which is the useful case: "the budget held
	 * nothing back" and "nobody wired the budget up" are different answers, and a line
	 * that only appears when it fires cannot tell them apart.
	 */
	private String deferralSummary()
	{
		return "model builds held over by the " + RenderPolicy.MAX_MODEL_BUILDS_PER_PASS
			+ "-per-pass budget: " + buildsDeferred
			+ " (over " + passesWithDeferredBuilds + " pass(es))";
	}

	/**
	 * The whole session as stable, diffable plain text.
	 *
	 * <p>Built on the client thread and handed to a background thread to write — by
	 * {@code LivelyCitiesDevReportsPlugin}, in the test source set — so the text is a
	 * snapshot rather than a view of counters that are still moving. The same split
	 * {@code CacheIdAudit} uses, and the seam that lets all the file handling live
	 * outside the shipped jar.
	 */
	String toReportText()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Lively Cities frame timings\n");
		sb.append("# game ticks measured: ").append(ticks).append('\n');
		sb.append("#\n");
		sb.append("# The visibility figure is EXCLUSIVE of model building, as of 2026-08-29. A\n");
		sb.append("# steady-state pass and the pass that walks into a new city are different\n");
		sb.append("# events with different acceptable costs, and one p99 over both describes\n");
		sb.append("# neither. So: 'visibility pass' is the cost of deciding who is on screen,\n");
		sb.append("# 'model build burst' is what that pass spent building, 'region load' is the\n");
		sb.append("# scene load that happened just before it, and 'game tick' is all of it added\n");
		sb.append("# back up. Nothing is dropped; the total is the first line.\n");
		sb.append("#\n");
		sb.append("# Percentiles are over the whole session, from a 1us-resolution histogram;\n");
		sb.append("# the maximum is exact. A percentile printed with a leading '>=' fell in the\n");
		sb.append("# overflow bucket.\n");
		sb.append("#\n");
		sb.append("# Acceptable: interpolation p99 <= 0.5ms, visibility p99 <= 2ms, game tick\n");
		sb.append("# p99 <= 2ms and max <= 16.7ms, no single model build over 20ms, no build\n");
		sb.append("# burst over 16.7ms, no region load over 5ms. A problem: interpolation\n");
		sb.append("# p99 > 2ms, visibility p99 > 8ms, game tick p99 > 8ms or any tick over 50ms,\n");
		sb.append("# any single model build over 50ms, any burst over 50ms, any region load\n");
		sb.append("# over 20ms. See FrameTimings' javadoc for where each of those comes from and\n");
		sb.append("# when it was registered.\n");
		sb.append("#\n");
		sb.append("# A pass builds at most ").append(RenderPolicy.MAX_MODEL_BUILDS_PER_PASS)
			.append(" model(s); the rest wait for the next one, nearest\n");
		sb.append("# first. 'held over' below counts those. It does NOT count a build the client\n");
		sb.append("# could not satisfy — a cold model cache asks, gets nothing, and is retried;\n");
		sb.append("# that has never appeared in this report and still does not.\n");
		sb.append('\n');

		gameTick.appendTo(sb);
		visibility.appendTo(sb);
		modelBuild.appendTo(sb);
		buildBurst.appendTo(sb);

		sb.append("# model builds held over by the per-pass build budget\n");
		sb.append("budget:    ").append(RenderPolicy.MAX_MODEL_BUILDS_PER_PASS)
			.append(" build(s) per visibility pass\n");
		sb.append("held over: ").append(buildsDeferred)
			.append("  (over ").append(passesWithDeferredBuilds).append(" pass(es))\n");
		sb.append('\n');

		regionLoad.appendTo(sb);
		interpolation.appendTo(sb);

		return sb.toString();
	}

	/**
	 * One measured thing: a histogram of durations, plus the companion count recorded
	 * alongside each sample.
	 *
	 * <p>Nothing here allocates. {@link #record} is a bounds check, an array increment
	 * and four comparisons, which is what lets the call sites sit inside the per-frame
	 * loop without an argument about whether measuring changed the measurement.
	 */
	private static final class Meter
	{
		/** 0..999us at 1us resolution — where every healthy sample is expected to land. */
		private static final int FINE_BUCKETS = 1000;

		/** 1ms..11ms at 100us resolution, for the model-building spikes. */
		private static final int COARSE_BUCKETS = 100;
		private static final int COARSE_WIDTH_MICROS = 100;

		/** One more for "11ms or worse", which the exact maximum then describes properly. */
		private static final int OVERFLOW_BUCKET = FINE_BUCKETS + COARSE_BUCKETS;
		private static final int BUCKETS = OVERFLOW_BUCKET + 1;

		private final String name;
		private final String countLabel;
		private final long[] buckets = new long[BUCKETS];

		private long count;
		private long totalMicros;

		/**
		 * The slowest sample seen, in microseconds — <b>{@code -1} until there is one.</b>
		 *
		 * <p>The sentinel is load-bearing rather than stylistic. Initialised to 0, the
		 * {@code micros > maxMicros} test in {@link #record} is false for <i>every</i> 0µs
		 * sample, so {@link #countAtMax} is never assigned and the report says "at 0
		 * walkers" no matter how many there were. That failure is invisible in the case
		 * anyone would look for it and only bites in the healthy one: the interpolation
		 * meter is <i>expected</i> to read 0µs — that is the claim this class exists to
		 * support — so the number would have been wrong precisely when the answer was
		 * good. Found by review, after a test asserting {@code countAtMax == 0} passed
		 * against both the working implementation and the broken one.
		 *
		 * <p>{@code -1} never reaches {@link #format}: both {@link #summary} and
		 * {@link #appendTo} return early on {@code count == 0}, and any sample at all
		 * replaces it.
		 */
		private long maxMicros = -1L;

		/** The companion count at the slowest sample — the "at how many objects?" figure. */
		private int countAtMax;

		private int minCount = Integer.MAX_VALUE;
		private int maxCount;
		private long totalCount;

		Meter(String name, String countLabel)
		{
			this.name = name;
			this.countLabel = countLabel;
		}

		void record(long elapsedNanos, int companionCount)
		{
			// Truncating rather than rounding, so a sub-microsecond pass reads as 0us
			// rather than as 1us. "Too fast to measure" is the honest answer and it is
			// the one the interpolation pass is expected to give.
			long micros = elapsedNanos / 1000L;
			if (micros < 0)
			{
				// nanoTime is monotonic, so this cannot happen — but a negative index
				// would be an ArrayIndexOutOfBounds inside a frame handler, and that is
				// a bad way to find out.
				micros = 0;
			}

			buckets[bucketOf(micros)]++;
			count++;
			totalMicros += micros;

			if (micros > maxMicros)
			{
				maxMicros = micros;
				countAtMax = companionCount;
			}

			totalCount += companionCount;
			minCount = Math.min(minCount, companionCount);
			maxCount = Math.max(maxCount, companionCount);
		}

		private static int bucketOf(long micros)
		{
			if (micros < FINE_BUCKETS)
			{
				return (int) micros;
			}

			long coarse = (micros - FINE_BUCKETS) / COARSE_WIDTH_MICROS;
			if (coarse < COARSE_BUCKETS)
			{
				return FINE_BUCKETS + (int) coarse;
			}

			return OVERFLOW_BUCKET;
		}

		/**
		 * @return the lower edge of the bucket the given percentile falls in, in
		 * microseconds, or {@code -1} if there are no samples
		 */
		long percentileMicros(double fraction)
		{
			if (count == 0)
			{
				return -1;
			}

			// Nearest-rank: the smallest value at or below which at least `fraction` of
			// the samples lie. Ceil rather than round, so p50 of a single sample is that
			// sample rather than nothing.
			long rank = (long) Math.ceil(fraction * count);
			if (rank < 1)
			{
				rank = 1;
			}

			long seen = 0;
			for (int i = 0; i < BUCKETS; i++)
			{
				seen += buckets[i];
				if (seen >= rank)
				{
					return lowerEdgeMicros(i);
				}
			}

			return maxMicros;
		}

		private static long lowerEdgeMicros(int bucket)
		{
			if (bucket < FINE_BUCKETS)
			{
				return bucket;
			}
			return FINE_BUCKETS + (long) (bucket - FINE_BUCKETS) * COARSE_WIDTH_MICROS;
		}

		private boolean isOverflow(long micros)
		{
			return micros >= lowerEdgeMicros(OVERFLOW_BUCKET);
		}

		String summary()
		{
			if (count == 0)
			{
				return name + ": no samples";
			}

			return name + ": median " + formatPercentile(0.5)
				+ ", p95 " + formatPercentile(0.95)
				+ ", p99 " + formatPercentile(0.99)
				+ ", max " + format(maxMicros) + " at " + countAtMax + " " + countLabel
				+ " (" + count + " sample(s))";
		}

		void appendTo(StringBuilder sb)
		{
			sb.append("# ").append(name).append('\n');
			if (count == 0)
			{
				sb.append("samples: 0\n\n");
				return;
			}

			sb.append("samples: ").append(count).append('\n');
			sb.append("median:  ").append(formatPercentile(0.5)).append('\n');
			sb.append("p95:     ").append(formatPercentile(0.95)).append('\n');
			sb.append("p99:     ").append(formatPercentile(0.99)).append('\n');
			sb.append("max:     ").append(format(maxMicros))
				.append("  (at ").append(countAtMax).append(' ').append(countLabel).append(")\n");
			sb.append("mean:    ").append(format(totalMicros / count)).append('\n');
			sb.append(countLabel).append(": min ").append(minCount)
				.append(", mean ").append(totalCount / count)
				.append(", max ").append(maxCount).append('\n');
			sb.append('\n');
		}

		private String formatPercentile(double fraction)
		{
			long micros = percentileMicros(fraction);
			return (isOverflow(micros) ? ">=" : "") + format(micros);
		}

		/**
		 * <p>{@link Locale#ROOT} is not decoration. {@code String.format} without one
		 * consults the default locale, so {@code %.2f} renders 11.0 as {@code 11,00} on
		 * a comma-decimal JVM — the report stops being the "stable, diffable plain text"
		 * this class promises, two contributors' reports no longer diff, and
		 * {@code ./gradlew build} goes red for anyone whose machine is set to a German
		 * locale while staying green on every machine here. Found by review running the
		 * suite under {@code -Duser.language=de}.
		 */
		private static String format(long micros)
		{
			if (micros < 1000)
			{
				return micros + "us";
			}
			return String.format(Locale.ROOT, "%.2fms", micros / 1000.0);
		}
	}
}
