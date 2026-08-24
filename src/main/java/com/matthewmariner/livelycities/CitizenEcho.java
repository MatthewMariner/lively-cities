package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Extra citizens, derived from the authored ones — the whole of
 * {@link CrowdDensity#CROWDED}.
 *
 * <p>The dataset holds 129 hand-placed citizens and the user asked for twice as
 * many. There is no second dataset, so the second half has to come from the first:
 * an <b>echo</b> is a citizen built entirely out of one authored citizen's own
 * record, standing on separately-validated ground a few tiles away, wearing that
 * citizen's own colours re-dealt, and carrying none of its identity.
 *
 * <p><b>Nothing here is invented and nothing here is random.</b> Every decision —
 * which citizen seeds an echo, how many, where it stands, which way it faces, what
 * colour it is, and what its uuid is — is a pure function of the source's
 * {@link EntityDefinition#stableHash()} and the source's own fields. That is the
 * same promise {@link CrowdDensity} already makes and for the same reason: a crowd
 * whose membership changes between logins reads as broken, and a placement that
 * changes between logins cannot be validated once and trusted.
 *
 * <h2>Which citizens seed echoes, and why not all of them</h2>
 *
 * <p>An echo must not look like its source standing next to itself, and the only
 * appearance machinery in the dataset is the parallel
 * {@code modelRecolorFind}/{@code modelRecolorReplace} arrays. So an echo's
 * different look is the source's <b>own</b> {@code replace} palette re-dealt
 * against the <b>same</b> {@code find} slots — the tunic colour goes where the
 * hair colour was. That is a genuinely different citizen wearing nothing the
 * authoring tool did not already choose for that model.
 *
 * <p>The price is that a citizen whose record carries fewer than two recolour
 * pairs has no palette to re-deal, and inventing a colour for it would be exactly
 * the hand-authored content this feature is not allowed to add. Those citizens
 * therefore seed no echoes. In the shipped data:
 * <ul>
 *   <li>45 of the 129 carry no recolour at all and 4 carry a single pair — no
 *       second slot to deal into, so 49 seed nothing;</li>
 *   <li>4 more carry two or more pairs whose {@code replace} values are all
 *       identical ("Brother Keptic", "Dark wizard", "Ambatu", "Sister Palus"), so
 *       every re-deal is the deal it started with — they seed nothing either;</li>
 *   <li>the remaining <b>76</b> seed {@link #MAX_ECHOES_PER_CITIZEN} echoes each
 *       where their palette supports two <i>distinct</i> re-deals, and one where it
 *       supports only one.</li>
 * </ul>
 * That comes to <b>144 echoes against 129 authored citizens — 273 in total,
 * 2.12×</b>, which is the "roughly twice as many" the request asked for.
 * {@code CitizenEchoTest} recomputes all of those numbers from the shipped files
 * rather than trusting this paragraph.
 *
 * <p>The cap of two per citizen is a judgement and is written down as one: a
 * single richly-recoloured citizen with ten viable re-deals would otherwise become
 * a crowd of eleven identical bodies in one doorway, which is not what doubling a
 * city means.
 *
 * <h2>Where an echo stands</h2>
 *
 * <p><b>Placement is the hard part, and it is not a heuristic.</b> Most tiles near
 * a citizen are wall, counter, water or scenery. Two sources of known-good ground
 * are used, in this order:
 *
 * <ol>
 *   <li><b>The source's authored wander box.</b> The 63 {@code WanderingCitizen}s
 *       carry a box a human drew, so every tile in it is ground that person already
 *       decided a citizen could walk on. Box tiles are tried first, in a
 *       hash-rotated pass over the box, and an echo placed on one is marked
 *       {@link EntityDefinition#isEchoOnAuthoredGround()}.</li>
 *   <li><b>A ring of candidate offsets</b> at Chebyshev distance
 *       {@link #MIN_SEPARATION_TILES} from the source, for the citizens with no
 *       box. These are <i>candidates</i>, not conclusions: nothing about the offset
 *       claims the tile is walkable.</li>
 * </ol>
 *
 * <p>Either way the tile is then checked against the client's live collision map
 * before the echo is ever spawned — see {@link #isPlaceable} and
 * {@link StandableGround}. An echo whose tile the collision map rejects is
 * <b>skipped, not moved</b>: it simply never appears, on that plane, in that city,
 * forever. A "try the next tile along" fallback would make the placement depend on
 * when the scene happened to be loaded, which is the determinism this class exists
 * to keep.
 *
 * <p>When the collision map has no answer at all — the four-slot array is
 * allocated before the scene is built — the wander box is the fallback signal: an
 * echo standing on authored ground is allowed through, and one standing on a
 * derived offset is not. That is the honest asymmetry; a human vouched for one and
 * nobody has vouched for the other.
 *
 * <h2>What an echo is not</h2>
 *
 * <ul>
 *   <li><b>It does not wander.</b> Its ground is proved standable at exactly one
 *       tile, and {@link CitizenWalk} validates nothing as it goes — so a walking
 *       echo would spend most of its life on tiles no collision check has ever
 *       seen. It also keeps the per-frame pass exactly as cheap as it is today,
 *       which matters when the crowd has just doubled; and two citizens pacing one
 *       authored box would read as a duplicate rather than as a crowd.</li>
 *   <li><b>It never speaks.</b> The only honest source of a remark is the dataset,
 *       and an echo has no authored line — putting the source's sentence in a
 *       second mouth would turn an authored voice into ambient noise. Overhead-text
 *       spam was the predecessor plugin's loudest complaint, and doubling the crowd
 *       doubles the potential noise, so the quiet half of the crowd is the half
 *       that is not authored. It costs no code: {@link EntityDefinition} gives an
 *       echo an empty remarks array and {@link CitizenRemarks#forDefinition}
 *       returns {@code null} for that, so an echo has no remark object at all —
 *       and {@link CitizenMenu} therefore offers it no "Mute" entry, because there
 *       is nothing to mute.</li>
 *   <li><b>It does not carry its source's identity.</b> Its name is
 *       {@link #ECHO_NAME}, its examine line is {@link #ECHO_EXAMINE_TEXT}, and it
 *       has its own uuid — so hiding "Ali the wanderer" hides Ali and not the
 *       stranger standing near him, and both survive a restart because both uuids
 *       are derived rather than generated.</li>
 * </ul>
 *
 * <p><b>Client-thread-free apart from {@link #isPlaceable}</b>, which reads the
 * live collision map. {@link #echoesOf} touches nothing but its argument, which is
 * what lets the tests derive the whole shipped roster's echoes without a game
 * running.
 */
final class CitizenEcho
{
	/**
	 * The closest an echo may stand to its source, or to another echo of the same
	 * source: two tiles, Chebyshev.
	 *
	 * <p>Two rather than one because one is not a gap. Citizen models are roughly a
	 * tile wide, so two of them on adjacent tiles interpenetrate and read as one
	 * clipped body rather than as two people — which is the "twins" failure in its
	 * most literal form. At two there is a whole empty tile between them and they
	 * read as two people standing near each other.
	 */
	static final int MIN_SEPARATION_TILES = 2;

	/**
	 * The most echoes one authored citizen may seed: two.
	 *
	 * <p>A judgement, not arithmetic. The palette of the richest shipped citizen
	 * supports ten distinct re-deals; letting it spend all ten would put eleven
	 * copies of one body in one doorway. Two is what turns 129 authored citizens
	 * into 273 — the "twice as many" that was asked for — and it is the number the
	 * count in this class's javadoc is computed from.
	 */
	static final int MAX_ECHOES_PER_CITIZEN = 2;

	/**
	 * The name every echo carries.
	 *
	 * <p>Honestly generic, and deliberately not the source's: an echo of "Ali the
	 * wanderer" is not Ali, and a second Ali standing six tiles from the first is
	 * the impersonation problem that got the predecessor plugin disabled. It is also
	 * not blank — {@link CitizenLabel} would fall back to the entity type, and a
	 * menu row reading {@code Examine StationaryCitizen} is worse than one reading
	 * {@code Examine Passer-by}.
	 */
	static final String ECHO_NAME = "Passer-by";

	/**
	 * What Examine says about an echo.
	 *
	 * <p>The one string in this feature that is written rather than derived, and it
	 * is written because the alternative is worse: the source's own examine text
	 * would claim the echo is a character it is not, and no examine text at all
	 * would leave a player who clicked with nothing but the plugin's name.
	 * {@link CitizenLabel#examineMessage} already appends "a cosmetic citizen added
	 * by the plugin, not a real NPC"; this sentence adds the part only this feature
	 * knows, which is <i>why</i> there is an extra person standing there and which
	 * setting to turn off to stop it.
	 */
	static final String ECHO_EXAMINE_TEXT =
		"One of the extra townsfolk the \"Crowded\" crowd density setting adds. "
			+ "Nobody in particular.";

	/**
	 * Mixed into an echo's uuid derivation. Two arbitrary odd 64-bit constants,
	 * fixed forever: changing either changes every echo's uuid, which would silently
	 * discard every "Hide" a user had set on an echo.
	 */
	private static final long ECHO_UUID_SALT_HIGH = 0x9E3779B97F4A7C15L;
	private static final long ECHO_UUID_SALT_LOW = 0xBF58476D1CE4E5B9L;

	/** Shared, so the 49 citizens that seed nothing do not each allocate a list. */
	private static final List<EntityDefinition> NONE = Collections.emptyList();

	private CitizenEcho()
	{
	}

	/**
	 * Every echo one authored citizen seeds.
	 *
	 * <p>Pure: the same definition in gives the same echoes out, field for field,
	 * in the same order, in every session. Cheap enough to call from
	 * {@code EntityScene}'s region build rather than caching, and called from
	 * nowhere else.
	 *
	 * @param source an authored entity, or an echo (which seeds nothing)
	 * @return the echoes, oldest-index first; empty for anything that cannot seed
	 * one. Never null.
	 */
	static List<EntityDefinition> echoesOf(EntityDefinition source)
	{
		if (source.isEcho())
		{
			// An echo of an echo would compound a derivation on a derivation, and the
			// second generation's colours would be a re-deal of a re-deal — further
			// from the authored palette with every step. One generation only.
			return NONE;
		}

		if (!source.getType().isCitizen())
		{
			// Scenery. A second market stall two tiles from the first is not a
			// livelier city, and scenery carries no name or examine text to replace.
			return NONE;
		}

		short[] find = source.getRecolorFind();
		short[] replace = source.getRecolorReplace();
		if (find.length < 2 || replace.length < 2)
		{
			// Nothing to re-deal — see the class javadoc. 49 of the 129 shipped
			// citizens land here.
			return NONE;
		}

		int[] deals = distinctDeals(replace);
		if (deals.length == 0)
		{
			// Two or more pairs, all replacing with the same colour: every re-deal is
			// the deal it started with. Four shipped citizens land here.
			return NONE;
		}

		long sourceHash = source.stableHash();
		int wanted = Math.min(deals.length, MAX_ECHOES_PER_CITIZEN);
		List<Spot> spots = spotsFor(source, sourceHash, wanted);
		if (spots.isEmpty())
		{
			return NONE;
		}

		// The deal each echo gets is rotated by the source's own hash, so two
		// citizens with the same palette do not both hand their first echo the same
		// re-deal. Consecutive indices modulo a deduplicated list, so sibling echoes
		// differ from each other as well as from their source.
		int firstDeal = (int) Math.floorMod(sourceHash >>> 42, deals.length);

		List<EntityDefinition> out = new ArrayList<>(spots.size());
		for (int index = 0; index < spots.size(); index++)
		{
			Spot spot = spots.get(index);
			UUID uuid = echoUuid(source.getUuid(), index);
			int deal = deals[(firstDeal + index) % deals.length];

			out.add(EntityDefinition.echoOf(
				source,
				uuid,
				spot.tile,
				facing(uuid),
				find.clone(),
				redeal(replace, deal),
				ECHO_NAME,
				ECHO_EXAMINE_TEXT,
				spot.onAuthoredGround));
		}

		return out;
	}

	/**
	 * Whether this echo's tile is ground a citizen could actually be standing on.
	 *
	 * <p>Called once per echo per visibility pass, only for echoes already inside
	 * the cull radius, and it is the gate that makes the whole feature safe: an echo
	 * that fails is not a candidate, so it is despawned by the same rule that
	 * despawns a citizen in an unticked city. Nothing nudges it anywhere.
	 *
	 * <p>The {@link StandableGround.Verdict#UNKNOWN} branch is the wander box acting
	 * as the fallback signal — see the class javadoc — and it is the only place an
	 * echo is admitted without the collision map having said yes.
	 *
	 * @param definition an echo definition; an authored entity is always placeable,
	 *                   because a human put it there
	 */
	static boolean isPlaceable(@Nullable WorldView worldView, EntityDefinition definition)
	{
		if (!definition.isEcho())
		{
			return true;
		}

		StandableGround.Verdict verdict = StandableGround.verdict(worldView, definition.getWorldLocation());
		if (verdict == StandableGround.Verdict.UNKNOWN)
		{
			return definition.isEchoOnAuthoredGround();
		}

		return verdict == StandableGround.Verdict.STANDABLE;
	}

	/**
	 * An echo's uuid, derived from its source's and its own index.
	 *
	 * <p><b>Derived rather than generated, and that is a requirement rather than a
	 * convenience.</b> {@link CitizenOverrides} hides and mutes by uuid, and it
	 * persists those uuids into the user's RuneLite profile. A {@code randomUUID}
	 * here would mean "Hide" on an echo lasted until the next login and then hid a
	 * stranger instead.
	 *
	 * <p>Both halves go through {@link EntityDefinition#mix}, the same mixer
	 * {@link EntityDefinition#stableHash()} uses, so a one-bit difference in the
	 * source uuid or in the index spreads across the whole word — two echoes of the
	 * same citizen are nowhere near each other in uuid space, and neither is near
	 * their source.
	 */
	static UUID echoUuid(UUID sourceUuid, int index)
	{
		long nudge = index + 1L;
		return new UUID(
			EntityDefinition.mix(sourceUuid.getMostSignificantBits() ^ (ECHO_UUID_SALT_HIGH * nudge)),
			EntityDefinition.mix(sourceUuid.getLeastSignificantBits() ^ (ECHO_UUID_SALT_LOW * nudge)));
	}

	/**
	 * The rotations of {@code replace} that produce a mapping the source does not
	 * already have, one per <i>distinct</i> result.
	 *
	 * <p>Rotation by {@code k} sends slot {@code i}'s colour to slot
	 * {@code (i + k) mod n}, so the echo wears the source's own colours in a
	 * different order. Two things are excluded:
	 * <ul>
	 *   <li>{@code k = 0}, which is the source.</li>
	 *   <li>Any {@code k} whose result was already produced by a smaller one. A
	 *       palette like {@code [red, blue, red, blue]} has only one distinct
	 *       re-deal however many rotations it admits, and without this two sibling
	 *       echoes would be issued "different" deals that come out identical — the
	 *       twins problem moved one step sideways.</li>
	 * </ul>
	 *
	 * @return the usable rotations, ascending; empty if the palette has none
	 */
	static int[] distinctDeals(short[] replace)
	{
		int n = replace.length;
		Set<String> seen = new LinkedHashSet<>();
		seen.add(signature(replace));

		int[] deals = new int[n - 1];
		int found = 0;
		for (int k = 1; k < n; k++)
		{
			if (seen.add(signature(redeal(replace, k))))
			{
				deals[found++] = k;
			}
		}

		if (found == deals.length)
		{
			return deals;
		}

		int[] trimmed = new int[found];
		System.arraycopy(deals, 0, trimmed, 0, found);
		return trimmed;
	}

	/**
	 * @return {@code replace} rotated by {@code deal} — a fresh array, so the
	 * source's own palette is never touched
	 */
	static short[] redeal(short[] replace, int deal)
	{
		int n = replace.length;
		short[] out = new short[n];
		for (int i = 0; i < n; i++)
		{
			out[i] = replace[(i + deal) % n];
		}
		return out;
	}

	/**
	 * The tiles this source's echoes stand on, in echo order.
	 *
	 * <p>Candidates are offered box-first and then ring, and accepted greedily
	 * subject to {@link #MIN_SEPARATION_TILES} from the source and from every echo
	 * already placed. Nothing here consults the collision map — that happens at
	 * spawn time, per session, in {@link #isPlaceable} — so this stays a pure
	 * function of the source and can be asserted about offline.
	 *
	 * @return up to {@code wanted} spots; possibly fewer, and possibly empty
	 */
	private static List<Spot> spotsFor(EntityDefinition source, long sourceHash, int wanted)
	{
		WorldPoint anchor = source.getWorldLocation();
		List<Spot> candidates = new ArrayList<>();
		appendBoxCandidates(source, sourceHash, anchor, candidates);
		appendRingCandidates(sourceHash, anchor, candidates);

		List<Spot> picked = new ArrayList<>(wanted);
		for (int i = 0; i < candidates.size() && picked.size() < wanted; i++)
		{
			Spot candidate = candidates.get(i);
			if (isFarEnough(candidate.tile, anchor, picked))
			{
				picked.add(candidate);
			}
		}

		return picked;
	}

	/**
	 * Every tile of the source's authored wander box that is far enough from the
	 * source, in a hash-rotated pass over the box.
	 *
	 * <p>Rotated rather than scanned from a corner: every box would otherwise seed
	 * its echoes in its own south-west corner, and a city's echoes would all cluster
	 * on one side of the people they came from.
	 *
	 * <p>The box is the already-validated, already-clamped one from
	 * {@link EntityDefinition#getWanderBox()}, so it is on the source's plane, it
	 * contains the source's tile, and it reaches no further than
	 * {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE}. Nothing here re-checks any of
	 * that.
	 */
	private static void appendBoxCandidates(
		EntityDefinition source, long sourceHash, WorldPoint anchor, List<Spot> into)
	{
		EntityDefinition.WanderBox box = source.getWanderBox();
		if (box == null)
		{
			return;
		}

		List<WorldPoint> inBox = new ArrayList<>();
		for (int x = box.getMinX(); x <= box.getMaxX(); x++)
		{
			for (int y = box.getMinY(); y <= box.getMaxY(); y++)
			{
				if (RenderPolicy.tileDistance(anchor, new WorldPoint(x, y, box.getPlane()))
					>= MIN_SEPARATION_TILES)
				{
					inBox.add(new WorldPoint(x, y, box.getPlane()));
				}
			}
		}

		if (inBox.isEmpty())
		{
			return;
		}

		int start = (int) Math.floorMod(sourceHash, inBox.size());
		for (int i = 0; i < inBox.size(); i++)
		{
			into.add(new Spot(inBox.get((start + i) % inBox.size()), true));
		}
	}

	/**
	 * The ring of tiles at exactly {@link #MIN_SEPARATION_TILES} from the source, in
	 * a hash-rotated pass.
	 *
	 * <p>The fallback for the 66 citizens with no box, and the top-up for the two
	 * shipped wanderers whose box is too small to hold two well-separated echoes.
	 * These are candidates and nothing more: the ring says "this tile is the right
	 * distance away", and {@link StandableGround} is what says whether anybody could
	 * stand on it.
	 */
	private static void appendRingCandidates(long sourceHash, WorldPoint anchor, List<Spot> into)
	{
		List<WorldPoint> ring = new ArrayList<>();
		int r = MIN_SEPARATION_TILES;
		for (int dx = -r; dx <= r; dx++)
		{
			for (int dy = -r; dy <= r; dy++)
			{
				if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
				{
					ring.add(new WorldPoint(anchor.getX() + dx, anchor.getY() + dy, anchor.getPlane()));
				}
			}
		}

		int start = (int) Math.floorMod(sourceHash >>> 21, ring.size());
		for (int i = 0; i < ring.size(); i++)
		{
			into.add(new Spot(ring.get((start + i) % ring.size()), false));
		}
	}

	private static boolean isFarEnough(WorldPoint tile, WorldPoint anchor, List<Spot> placed)
	{
		if (RenderPolicy.tileDistance(anchor, tile) < MIN_SEPARATION_TILES)
		{
			return false;
		}

		for (int i = 0; i < placed.size(); i++)
		{
			if (RenderPolicy.tileDistance(placed.get(i).tile, tile) < MIN_SEPARATION_TILES)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * One of the eight cardinal facings, from the echo's own uuid.
	 *
	 * <p>From the echo's uuid rather than the source's, so two echoes of one citizen
	 * do not stand shoulder to shoulder facing the same way — which is the
	 * "twins" tell that survives a recolour. Eight rather than 2048 because a
	 * citizen model facing 13/2048 of a turn off north looks like a rendering fault
	 * rather than a person; the game's own NPCs use the eight.
	 */
	private static int facing(UUID echoUuid)
	{
		return (int) Math.floorMod(EntityDefinition.stableHashOf(echoUuid), 8L) * 256;
	}

	private static String signature(short[] palette)
	{
		StringBuilder out = new StringBuilder(palette.length * 7);
		for (short colour : palette)
		{
			out.append(colour).append(',');
		}
		return out.toString();
	}

	/** One candidate tile and where the claim that it is walkable comes from. */
	private static final class Spot
	{
		private final WorldPoint tile;
		private final boolean onAuthoredGround;

		Spot(WorldPoint tile, boolean onAuthoredGround)
		{
			this.tile = tile;
			this.onAuthoredGround = onAuthoredGround;
		}
	}
}
