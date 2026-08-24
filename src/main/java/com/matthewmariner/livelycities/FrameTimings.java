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
 * <p><b>Three meters, and the split is the whole point</b>, because they run on
 * different clocks and only one of them competes with the frame rate:
 * <ul>
 *   <li><b>{@code visibility pass}</b> — once per game tick (600ms) and once per
 *       settings change. It decides who is on screen, and it is where model
 *       building happens, so this figure is <i>inclusive</i> of the next one.</li>
 *   <li><b>{@code model build}</b> — once per entity, the first time it spawns.
 *       Bursty by nature: walking into Varrock builds dozens of models inside one
 *       visibility pass. Broken out so a spike in the pass above can be attributed
 *       instead of guessed at.</li>
 *   <li><b>{@code interpolation}</b> — once per rendered frame, and the only
 *       per-frame work this plugin does. It walks the active wandering citizens and
 *       nothing else; the right-click clickbox — the cost centre the plan named — is
 *       computed in {@code MenuOpened} and never per frame, so this number is
 *       expected to be near zero and the point of measuring it is to be able to say
 *       so with a figure instead of an argument.</li>
 * </ul>
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
 *       has acquired work that belongs on the tick.</li>
 *   <li><b>Visibility pass, p99 ≤ 2ms</b> is <b>acceptable</b>: it lands in one frame
 *       out of every thirty-six, and an eighth of a frame once every 600ms is not
 *       something a player can see. <b>p99 &gt; 8ms</b> is <b>a problem</b> — half a
 *       frame, on a schedule, is a rhythmic stutter.</li>
 *   <li><b>Model building, max ≤ 20ms for one model and ≤ 100ms for the burst that
 *       lands on entering a dense region</b> is <b>acceptable</b> — a sixth of a game
 *       tick, once, at a region border where the client is already busy. <b>Any single
 *       build over 50ms</b> is <b>a problem</b>, and the fix is known rather than
 *       hypothetical: spread the burst across ticks instead of spending the whole
 *       80-object cap in one pass.</li>
 * </ul>
 *
 * <p>Once measured, the figure belongs in the README's Known limitations section and
 * in the performance sentence of {@code docs/SUBMISSION.md}'s PR body — both of which
 * currently say the measurement exists rather than what it said.
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

	private final Meter visibility = new Meter("visibility pass (per game tick)", "objects active");
	private final Meter modelBuild = new Meter("model build (per first spawn)", "objects active");
	private final Meter interpolation = new Meter("interpolation (per frame)", "walkers");

	private long ticks;
	private int ticksSinceReport;

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
	 * @param startedAt     the value {@link #start()} returned
	 * @param activeObjects how many {@code RuneLiteObject}s this pass left the client
	 *                      holding
	 */
	void recordVisibility(long startedAt, int activeObjects)
	{
		record(Pass.VISIBILITY, startedAt, activeObjects);
	}

	/**
	 * @param startedAt     the value {@link #start()} returned
	 * @param activeObjects how many objects this pass is putting on screen — a build at
	 *                      76 active is a different claim from one at 8
	 */
	void recordModelBuild(long startedAt, int activeObjects)
	{
		record(Pass.MODEL_BUILD, startedAt, activeObjects);
	}

	/**
	 * @param startedAt the value {@link #start()} returned
	 * @param walkers   how many wandering citizens this frame interpolated
	 */
	void recordFrame(long startedAt, int walkers)
	{
		record(Pass.INTERPOLATION, startedAt, walkers);
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
			case VISIBILITY:
				return visibility;
			case MODEL_BUILD:
				return modelBuild;
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
	 * The three things measured, and the three different clocks they run on. See the
	 * class javadoc.
	 */
	enum Pass
	{
		VISIBILITY,
		MODEL_BUILD,
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
		return visibility.count > 0 || modelBuild.count > 0 || interpolation.count > 0;
	}

	/**
	 * One line for the client log, at info, on the {@link #REPORT_INTERVAL_TICKS}
	 * cadence and never otherwise.
	 */
	String summaryLine()
	{
		return "Lively Cities frame timings after " + ticks + " game tick(s) — "
			+ visibility.summary() + "; " + modelBuild.summary() + "; " + interpolation.summary();
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
		sb.append("# The visibility figure is INCLUSIVE of model building: models are built\n");
		sb.append("# inside the visibility pass, so a spike in one explains a spike in the other.\n");
		sb.append("# Percentiles are over the whole session, from a 1us-resolution histogram;\n");
		sb.append("# the maximum is exact. A percentile printed with a leading '>=' fell in the\n");
		sb.append("# overflow bucket.\n");
		sb.append("#\n");
		sb.append("# Acceptable: interpolation p99 <= 0.5ms, visibility p99 <= 2ms, no single\n");
		sb.append("# model build over 20ms. A problem: interpolation p99 > 2ms, visibility\n");
		sb.append("# p99 > 8ms, or any single model build over 50ms. See FrameTimings' javadoc\n");
		sb.append("# for where those thresholds come from.\n");
		sb.append('\n');

		visibility.appendTo(sb);
		modelBuild.appendTo(sb);
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
