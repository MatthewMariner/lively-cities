package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Decides who is saying something, and for how long.
 *
 * <p><b>The off switches were built before the feature, on purpose.</b> Overhead
 * text was the predecessor's single loudest complaint — 144 upvotes on "please add
 * an option to shut them up", a mute toggle its author promised and never shipped,
 * and upstream issue #35 still open on release day plus a year. So the dials are
 * not a follow-up to this class; they are its parameters, and every one of them is
 * consulted on every pass:
 *
 * <ul>
 *   <li>{@link LivelyCitiesConfig#overheadText()} — the hard off switch. False
 *       means no rolls at all <i>and</i> every remark already up is cleared, so
 *       turning it off is silence now rather than silence in two minutes when the
 *       last dwell expires.</li>
 *   <li>{@link CitizenOverrides} — the per-citizen mute, at the granularity people
 *       actually complained at.</li>
 *   <li>{@link LivelyCitiesConfig#remarkIntervalTicks()} and
 *       {@link LivelyCitiesConfig#remarkDwellTicks()} — the cadence.</li>
 *   <li>{@link LivelyCitiesConfig#chatterRadius()} — the distance limit.</li>
 *   <li>{@link LivelyCitiesConfig#maxConcurrentRemarks()} — the cap that stops a
 *       crowd becoming a wall of text.</li>
 * </ul>
 *
 * <p><b>The defaults are the predecessor's timings</b>, which are the one thing
 * about its chatter that was reported as feeling right: roll every 60 ticks, only
 * within 15 tiles, dwell 120 ticks. What is new is that all four are dials, that
 * there is a cap at all, and that the roll is a per-citizen chance rather than a
 * certainty — with a 60-tick roll and a 120-tick dwell, a citizen that spoke on
 * every roll would be talking permanently.
 *
 * <p><b>The pass order is deliberate.</b> Expire, then count, then roll, then
 * admit nearest-first up to the cap. Expiring first means a citizen whose remark
 * ends on the same tick another wants to start does not block it; admitting
 * nearest-first means the cap sheds the far edge of a crowd rather than whichever
 * citizen the region file happened to list last — the same rule
 * {@link EntityScene#updateVisibility} uses for the object cap, for the same
 * reason.
 *
 * <p><b>The clock is the game tick and nothing else.</b> {@link #onGameTick} is
 * called from {@link EntityScene#onGameTick} and from nowhere else;
 * {@link #onSettingsChanged} exists so a settings change can take effect at once
 * <i>without</i> advancing it. RuneLite posts one {@code ConfigChanged} per key, so
 * switching profiles posts one per item this plugin owns — and a chatter clock that
 * advanced on those would run cadence at about thirty times speed for a moment,
 * which is the same bug the walkers already had and which is why
 * {@code onSettingsChanged} exists on the scene at all.
 *
 * <p><b>Client-thread-free.</b> Nothing here touches the client; it reads
 * {@link LivelyEntity#isActive()}, which does, so callers are on the client thread
 * anyway.
 */
@Slf4j
final class CitizenChatter
{
	/**
	 * Game ticks between a citizen's chances to say something: 60, i.e. 36
	 * seconds. The predecessor's cadence.
	 */
	static final int DEFAULT_ROLL_INTERVAL_TICKS = 60;

	/** The tightest cadence worth offering: once every 10 ticks, i.e. 6 seconds. */
	static final int MIN_ROLL_INTERVAL_TICKS = 10;

	/**
	 * The loosest: once every 600 ticks, i.e. six minutes. Past this the feature is
	 * indistinguishable from the hard off switch, which already exists — a dial
	 * whose far end duplicates another control is a dial that misleads.
	 */
	static final int MAX_ROLL_INTERVAL_TICKS = 600;

	/**
	 * How long a remark stays on screen: 120 ticks, 72 seconds. Also the
	 * predecessor's figure.
	 */
	static final int DEFAULT_DWELL_TICKS = 120;

	static final int MIN_DWELL_TICKS = 5;
	static final int MAX_DWELL_TICKS = 600;

	/**
	 * Tiles from the player inside which a citizen may start talking: 15, the
	 * predecessor's figure and comfortably inside the default 25-tile cull radius,
	 * so the citizens that talk are the ones a player is actually standing among.
	 */
	static final int DEFAULT_RADIUS_TILES = 15;

	static final int MIN_RADIUS_TILES = 1;

	/**
	 * Remarks on screen at once: 3.
	 *
	 * <p>This is the number that stops "ambience" being "a wall of text". Varrock
	 * square holds 40 citizens inside a 25-tile square; without a cap, a 60-tick
	 * roll at {@link #REMARK_CHANCE_PERCENT} over a 120-tick dwell puts roughly
	 * twenty bubbles on screen at once, which is the complaint restated rather than
	 * answered.
	 */
	static final int DEFAULT_MAX_CONCURRENT = 3;

	static final int MIN_MAX_CONCURRENT = 1;

	/** The most bubbles the dial will allow, for anyone who wants a market square. */
	static final int MAX_MAX_CONCURRENT = 12;

	/**
	 * The chance, in percent, that an eligible citizen says something on its due
	 * tick.
	 *
	 * <p>Not a dial: the two things a user actually wants to control are how often
	 * they get a chance ({@code remarkIntervalTicks}) and how many can be on screen
	 * ({@code maxConcurrentRemarks}), and a third multiplier interacting with both
	 * would make neither predictable. A quarter, so a citizen with something to say
	 * speaks roughly once every four intervals — about once every two and a half
	 * minutes at the default cadence.
	 */
	static final int REMARK_CHANCE_PERCENT = 25;

	private final LivelyCitiesConfig config;
	private final CitizenOverrides overrides;

	/**
	 * Game ticks since this chatter started. Its own counter rather than
	 * {@code client.getTickCount()} so the class stays client-free and so the
	 * cadence restarts cleanly on a scene invalidation instead of inheriting a
	 * phase from before the player logged in.
	 */
	private int tick;

	/**
	 * Reused between passes so the per-tick allocation is one growable list rather
	 * than one per pass. The pass runs every game tick over every entity in scope.
	 */
	private final List<LivelyEntity> candidates = new ArrayList<>();

	CitizenChatter(LivelyCitiesConfig config, CitizenOverrides overrides)
	{
		this.config = config;
		this.overrides = overrides;
	}

	/**
	 * One game tick of chatter.
	 *
	 * @param entities       the entities in scope — the live list, filtered here on
	 *                       {@link LivelyEntity#isActive()} so a citizen that is not
	 *                       on screen cannot be talking
	 * @param playerLocation the local player's tile, never null
	 * @return how many remarks are on screen when this returns
	 */
	int onGameTick(List<LivelyEntity> entities, WorldPoint playerLocation)
	{
		tick++;

		if (!config.overheadText())
		{
			// The hard off switch. Clearing rather than merely not rolling is the
			// difference between "off" and "off in two minutes".
			silence(entities);
			return 0;
		}

		int dwell = clamp(config.remarkDwellTicks(), MIN_DWELL_TICKS, MAX_DWELL_TICKS);
		int interval = clamp(config.remarkIntervalTicks(), MIN_ROLL_INTERVAL_TICKS, MAX_ROLL_INTERVAL_TICKS);
		int radius = clamp(config.chatterRadius(), MIN_RADIUS_TILES, RenderPolicy.MAX_CULL_RADIUS);
		int cap = clamp(config.maxConcurrentRemarks(), MIN_MAX_CONCURRENT, MAX_MAX_CONCURRENT);

		Set<UUID> muted = overrides.mutedUuids();

		int talking = 0;
		candidates.clear();

		for (int i = 0; i < entities.size(); i++)
		{
			LivelyEntity entity = entities.get(i);
			CitizenRemarks remarks = entity.getRemarks();
			if (remarks == null)
			{
				continue;
			}

			if (!entity.isActive())
			{
				// Not on screen. despawn() already cleared any remark; this is the
				// belt to that braces, and it costs a field read.
				remarks.clear();
				continue;
			}

			// Expire before counting: a remark ending this tick must not hold a slot
			// against a remark starting this tick.
			remarks.expire(tick);

			if (remarks.isTalking())
			{
				talking++;
				continue;
			}

			if (muted.contains(entity.getDefinition().getUuid()))
			{
				continue;
			}

			if (!remarks.dueAt(tick, interval))
			{
				continue;
			}

			// Measured from the authored tile, the same anchor the cull check uses,
			// so "near enough to talk" and "near enough to render" are the same kind
			// of question. A wandering citizen can be up to
			// RenderPolicy.DATASET_OVERHANG_ALLOWANCE tiles from it; deriving this
			// from the live position instead would make a citizen start and stop
			// being eligible mid-stride.
			if (RenderPolicy.tileDistance(playerLocation, entity.getDefinition().getWorldLocation()) > radius)
			{
				continue;
			}

			// Rolled for every eligible citizen, before the cap — see
			// CitizenRemarks.rolls.
			if (remarks.rolls(REMARK_CHANCE_PERCENT))
			{
				candidates.add(entity);
			}
		}

		if (candidates.isEmpty() || talking >= cap)
		{
			return talking;
		}

		// Nearest first, so the cap sheds the far edge of the crowd.
		candidates.sort(Comparator.comparingInt(
			e -> RenderPolicy.tileDistance(playerLocation, e.getDefinition().getWorldLocation())));

		int started = 0;
		for (int i = 0; i < candidates.size() && talking < cap; i++)
		{
			CitizenRemarks remarks = candidates.get(i).getRemarks();
			if (remarks == null)
			{
				continue;
			}
			remarks.say(tick, dwell);
			talking++;
			started++;
		}

		if (started > 0 && log.isDebugEnabled())
		{
			log.debug("chatter: {} remark(s) started, {} wanted to, {} on screen of at most {}",
				started, candidates.size(), talking, cap);
		}

		return talking;
	}

	/**
	 * Re-applies the hard off switch without advancing the clock.
	 *
	 * <p>Called from {@link EntityScene#onSettingsChanged} so that unticking
	 * "Overhead chatter" clears the bubbles on the click rather than on the next
	 * game tick. It deliberately does nothing else: a settings change is not a
	 * tick, and starting a remark here would let a user produce chatter by dragging
	 * a slider.
	 */
	void onSettingsChanged(List<LivelyEntity> entities)
	{
		if (!config.overheadText())
		{
			silence(entities);
		}
	}

	/**
	 * Forgets the cadence. Called when the scene is invalidated or torn down, so a
	 * fresh login starts a fresh phase rather than inheriting one from the last
	 * world.
	 */
	void reset()
	{
		tick = 0;
		candidates.clear();
	}

	/** @return the chatter clock, for tests and log lines */
	int getTick()
	{
		return tick;
	}

	private static void silence(List<LivelyEntity> entities)
	{
		for (int i = 0; i < entities.size(); i++)
		{
			CitizenRemarks remarks = entities.get(i).getRemarks();
			if (remarks != null)
			{
				remarks.clear();
			}
		}
	}

	/**
	 * {@code @Range} only constrains the slider. The value also arrives from a
	 * hand-edited {@code settings.properties} and from a profile synced off another
	 * install, so the clamp lives here where every read goes through it — the same
	 * reasoning as {@link RenderPolicy#clampCullRadius(int)}.
	 */
	private static int clamp(int requested, int min, int max)
	{
		return Math.max(min, Math.min(max, requested));
	}
}
