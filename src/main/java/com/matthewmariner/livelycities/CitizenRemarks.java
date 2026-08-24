package com.matthewmariner.livelycities;

import java.util.Random;
import javax.annotation.Nullable;

/**
 * One citizen's authored one-liners, and whichever of them it is saying now.
 *
 * <p>Built and owned by {@link LivelyEntity}, exactly like {@link CitizenWalk}:
 * one per entity that has anything to say, {@code null} for everything else.
 * {@link CitizenChatter} drives it once per game tick and
 * {@code ChatterOverlay} reads it once per frame.
 *
 * <p><b>Why the text lives here and not in the overlay.</b> The predecessor's
 * loudest text bugs were orphaned bubbles and text drifting off its NPC, and both
 * came from the overlay keeping its own list of what to draw and where. This
 * splits the two halves that were conflated:
 * <ul>
 *   <li><b>What is being said</b> has a lifetime in game ticks — a remark stays up
 *       for {@code remarkDwellTicks} — so it is state, and it belongs on the
 *       entity that is saying it. It is cleared by {@link LivelyEntity#despawn()},
 *       which is what makes an orphaned bubble impossible rather than
 *       unlikely: there is no list for a despawned entity to stay in.</li>
 *   <li><b>Where it is drawn</b> is not state at all. The overlay recomputes it
 *       from the object's live position every frame and caches nothing, so text
 *       cannot drift away from the citizen it belongs to — there is nothing for it
 *       to drift from.</li>
 * </ul>
 *
 * <p><b>The randomness is seeded, and not from the clock.</b> Same reasoning as
 * {@link CitizenWalk}: a citizen's remark order is then the same every session, so
 * a street reads as a place rather than as a random generator, and a test can
 * assert something about a thousand ticks. The seed is
 * {@link EntityDefinition#stableHash()} XOR'd with a salt, because the walk
 * already uses the bare hash — two {@link Random}s from one seed would make a
 * citizen's remark rolls line up exactly with its destination rolls forever, which
 * is a correlation nobody asked for and nobody could debug.
 *
 * <p><b>Client-thread-free.</b> Nothing here touches the client.
 */
final class CitizenRemarks
{
	/**
	 * Mixed into the seed so the remark {@link Random} is not the walk
	 * {@link Random} — see the class javadoc. An arbitrary constant; its only
	 * requirement is being non-zero and fixed forever, because changing it changes
	 * every citizen's remark order.
	 */
	private static final long SEED_SALT = 0x5AF31C0FFEEL;

	private final String[] remarks;
	private final Random random;

	/**
	 * The stagger offset, so a crowd does not all roll on the same tick — see
	 * {@link #dueAt(int, int)}. Derived from the identity hash rather than drawn
	 * from {@link #random}, so it does not depend on how many times the citizen has
	 * spoken.
	 */
	private final long spread;

	@Nullable
	private String current;

	/** Only meaningful while {@link #current} is non-null. */
	private int expiresAtTick;

	private CitizenRemarks(EntityDefinition definition, String[] remarks)
	{
		this.remarks = remarks;
		this.random = new Random(definition.stableHash() ^ SEED_SALT);
		this.spread = definition.stableHash();
	}

	/**
	 * @return a remark holder, or {@code null} if this entity has nothing to say —
	 * which is most of the dataset. Of the 135 shipped citizens 39 carry remarks,
	 * 54 carry an empty array and 42 carry no {@code remarks} field at all; scenery
	 * never speaks. {@link EntityDefinition} has already flattened all four cases
	 * into "the array is empty".
	 */
	@Nullable
	static CitizenRemarks forDefinition(EntityDefinition definition)
	{
		String[] remarks = definition.getRemarks();
		return remarks.length == 0 ? null : new CitizenRemarks(definition, remarks);
	}

	/** @return true while a remark is on screen */
	boolean isTalking()
	{
		return current != null;
	}

	/** @return the remark being said, or {@code null} */
	@Nullable
	String text()
	{
		return current;
	}

	/**
	 * Stops talking now. Called by {@link LivelyEntity#despawn()} and by the hard
	 * off switch, and safe to call when there is nothing to stop.
	 */
	void clear()
	{
		current = null;
	}

	/**
	 * Ends a remark that has been up for its dwell.
	 *
	 * @return true if this call ended one
	 */
	boolean expire(int tick)
	{
		if (current != null && tick >= expiresAtTick)
		{
			current = null;
			return true;
		}
		return false;
	}

	/**
	 * Whether this citizen's roll falls on this tick.
	 *
	 * <p>Staggered per citizen rather than synchronised, so a street does not
	 * produce a chorus every {@code interval} ticks and then silence. With the
	 * shipped defaults — a 100-tick (one minute) interval and an 8-tick (4.8 second)
	 * dwell — a synchronised roll would mean every talker in view starting and
	 * stopping in lockstep once a minute, which reads as a script firing rather than
	 * as people talking.
	 *
	 * @param interval the roll interval in game ticks, already through
	 *                 {@link CitizenChatter#effectiveIntervalTicks(int)}, so at least
	 *                 {@link CitizenChatter#MIN_ROLL_INTERVAL_TICKS}
	 */
	boolean dueAt(int tick, int interval)
	{
		return Math.floorMod(tick - spread, interval) == 0;
	}

	/**
	 * Rolls this citizen's own die.
	 *
	 * <p><b>Called for every eligible citizen on its due tick, before the
	 * concurrency cap is applied</b>, so that each citizen consumes exactly one
	 * draw per due tick whatever the crowd around it is doing. Rolling only until
	 * the cap filled would make one citizen's sequence depend on how many other
	 * citizens happened to be nearby, and then no test could pin either.
	 *
	 * @param chancePercent 0..100
	 * @return true if it wants to say something
	 */
	boolean rolls(int chancePercent)
	{
		return random.nextInt(100) < chancePercent;
	}

	/**
	 * Picks a remark and starts its dwell.
	 *
	 * @param tick       the current game tick
	 * @param dwellTicks how long it stays up
	 */
	void say(int tick, int dwellTicks)
	{
		current = remarks[random.nextInt(remarks.length)];
		expiresAtTick = tick + dwellTicks;
	}

	/** @return how many different things this citizen can say */
	int size()
	{
		return remarks.length;
	}
}
