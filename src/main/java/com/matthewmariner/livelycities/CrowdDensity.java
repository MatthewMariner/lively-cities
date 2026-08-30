package com.matthewmariner.livelycities;

/**
 * How much of the roster to show.
 *
 * <p><b>There is no density field in the dataset</b>, and inventing one would
 * mean editing 27 vendored files. So this is proportional thinning instead: each
 * level keeps a percentage of the entities, and every entity's own identity
 * decides whether it is one of them.
 *
 * <p><b>Why the decision is a hash and not a random number.</b> The choice has to
 * come out the same every time it is asked, for three separate reasons:
 * <ul>
 *   <li>The visibility pass runs every game tick. A coin flip per pass would
 *       make the whole crowd strobe at 1.6 Hz.</li>
 *   <li>Even a per-session draw is wrong: the same street would be populated by
 *       different people after every login, and after every hop and every
 *       region-cache eviction, because the wrappers are rebuilt. The point of
 *       thinning a city is a quieter city, not a different city each time you
 *       walk down the same road.</li>
 *   <li>A hash of a stable id is testable. {@code CrowdDensityTest} can assert
 *       both the proportion and the exact verdict for a known uuid, which a
 *       {@code Random} — even a seeded one — cannot offer without pinning an
 *       implementation detail of the JDK's generator.</li>
 * </ul>
 *
 * <p>The hash is {@link EntityDefinition#stableHash()}, derived from the record's
 * uuid, so it survives restarts, evictions and reorderings of the region files.
 *
 * <p>The levels nest: everything {@link #SPARSE} keeps, {@link #NORMAL} keeps
 * too. That falls out of comparing one bucket against a rising threshold rather
 * than hashing per level, and it means turning the dial up only ever adds people
 * — it never swaps one crowd for another.
 *
 * <p><b>{@link #CROWDED} is the one level that adds rather than subtracts</b>, and
 * it is deliberately not a fourth percentage. Thinning can only ever remove
 * authored citizens, so there is no percentage above 100 to ask for; going the
 * other way needs citizens that are not in the dataset, which is
 * {@link CitizenEcho}'s job. So {@link #getKeepPercent()} is 100 for both
 * {@link #FULL} and {@link #CROWDED} — they keep the same authored roster, which is
 * all of it — and the difference between them is the separate, boolean
 * {@link #includesEchoes()}. Two questions, two accessors: "how much of the roster"
 * and "and the derived ones as well?".
 */
public enum CrowdDensity
{
	/**
	 * Everything {@link #FULL} shows, plus the procedurally-derived echoes. Opt-in, and
	 * the only level that shows an entity the dataset does not contain.
	 *
	 * <p><b>How many more is not a fixed proportion and this javadoc used to say it
	 * was.</b> It read "roughly twice as many citizens", which was the original request
	 * and stopped being true when {@link CitizenEcho}'s flesh and body rules landed; the
	 * user-facing description on {@code LivelyCitiesConfig.crowdDensity} was corrected
	 * and this was not. Only a citizen whose own colours can be re-dealt honestly seeds
	 * anything, so the figure is a property of the dataset rather than of this enum —
	 * {@link CitizenEcho}'s javadoc carries it, and {@code CitizenEchoTest} recomputes
	 * it from the shipped files.
	 *
	 * <p>Listed first so the dropdown reads densest-to-sparsest. It is a strict
	 * superset of {@link #FULL} for the same reason every other level nests: the
	 * authored roster is untouched and the echoes are added on top, so turning the
	 * dial up here cannot make an authored citizen disappear.
	 */
	CROWDED("Crowded", 100, true),

	/** No thinning at all, and no additions: the dial's "off" position. */
	FULL("Full", 100, false),

	/** Roughly two thirds of the roster. */
	NORMAL("Normal", 66, false),

	/** Roughly a third of the roster. */
	SPARSE("Sparse", 33, false);

	/** Buckets the hash is spread over; the percentage is a count of buckets. */
	private static final int BUCKETS = 100;

	private final String label;
	private final int keepPercent;
	private final boolean echoes;

	CrowdDensity(String label, int keepPercent, boolean echoes)
	{
		this.label = label;
		this.keepPercent = keepPercent;
		this.echoes = echoes;
	}

	/**
	 * @return what share of the <b>authored</b> roster this level keeps. Never above
	 * 100 — see the class javadoc for why the extra citizens are not expressed here.
	 */
	public int getKeepPercent()
	{
		return keepPercent;
	}

	/**
	 * @return whether this level also shows {@link CitizenEcho}'s derived citizens.
	 * True for {@link #CROWDED} and nothing else, which is what makes the feature
	 * purely additive and purely opt-in: {@link #FULL} still yields exactly the
	 * authored set and nothing more.
	 */
	public boolean includesEchoes()
	{
		return echoes;
	}

	/**
	 * @param stableHash the entity's {@link EntityDefinition#stableHash()}
	 * @return whether this level keeps that entity
	 */
	public boolean keeps(long stableHash)
	{
		if (keepPercent >= BUCKETS)
		{
			// Not just an optimisation: it guarantees FULL keeps everything
			// regardless of how the buckets are spread.
			return true;
		}

		return Math.floorMod(stableHash, (long) BUCKETS) < keepPercent;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
