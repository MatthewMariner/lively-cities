package com.matthewmariner.livelycities;

/**
 * How much of the roster to show.
 *
 * <p><b>There is no density field in the dataset</b>, and inventing one would
 * mean editing 45 vendored files. So this is proportional thinning instead: each
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
 */
public enum CrowdDensity
{
	/** No thinning at all: the dial's "off" position. */
	FULL("Full", 100),

	/** Roughly two thirds of the roster. */
	NORMAL("Normal", 66),

	/** Roughly a third of the roster. */
	SPARSE("Sparse", 33);

	/** Buckets the hash is spread over; the percentage is a count of buckets. */
	private static final int BUCKETS = 100;

	private final String label;
	private final int keepPercent;

	CrowdDensity(String label, int keepPercent)
	{
		this.label = label;
		this.keepPercent = keepPercent;
	}

	public int getKeepPercent()
	{
		return keepPercent;
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
