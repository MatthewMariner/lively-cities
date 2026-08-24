package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
 * <p>The dataset holds 135 hand-placed citizens and the user asked for twice as
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
 *   <li>the <b>6 cameos</b> are refused outright, before their palette is even
 *       looked at. They are opt-in content behind their own checkbox and an echo is
 *       not, so an echo of one would be a body the {@code cameos} setting does not
 *       govern — see {@link #echoesOfSource};</li>
 *   <li>a source <b>dressed from an {@code npcAppearanceId}</b> is refused too, and
 *       for a sharper reason: its colours come from the composition rather than from
 *       its record, so re-dealing the record's palette would change nothing and the
 *       echo would be a pixel-for-pixel twin. One shipped citizen is in this bucket
 *       and not the one above — "Rufus" in Varrock square, who wears
 *       {@code NpcID.FARMER1} because his authored {@code modelIds} had no footwear
 *       in them (GitHub issue #1). He used to seed two echoes and now seeds none;</li>
 *   <li>of the 128 that remain, 45 carry no recolour at all and 4 carry a single
 *       pair — no second slot to deal into, so 49 seed nothing;</li>
 *   <li>4 more carry two or more pairs whose {@code replace} values are all
 *       identical ("Brother Keptic", "Dark wizard", "Ambatu", "Sister Palus"), so
 *       every re-deal is the deal it started with — they seed nothing either;</li>
 *   <li>the remaining <b>75</b> seed {@link #MAX_ECHOES_PER_CITIZEN} echoes each
 *       where their palette supports two <i>distinct</i> re-deals, and one where it
 *       supports only one — 142 echoes asked for, of which 141 find somewhere legal
 *       to stand (see below).</li>
 * </ul>
 * That comes to <b>141 echoes against 135 authored citizens — 276 in total,
 * 2.04×</b>, which is the "roughly twice as many" the request asked for.
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
 * <p><b>Placement is decided for a whole region file at a time, not one citizen at
 * a time.</b> {@link #echoesOfRegion} is the only entry point, and it is a roster in
 * and a roster out for exactly one reason: separation is a claim about a tile and
 * everything else standing near it, so a derivation that could only see one
 * citizen's own lineage could only ever enforce it against that lineage. It could
 * not, and did not: measured over the shipped files, per-citizen placement left 41
 * pairs of rendered entities closer than {@link #MIN_SEPARATION_TILES} — 57 counted
 * from each echo's own side of the pair — including three exact same-tile
 * collisions, two of them echo on echo. That is the "twins standing in each other"
 * failure in its most literal form, produced by the rule that exists to prevent it.
 *
 * <p>So this class keeps one set of claimed tiles per region: every authored
 * entity's tile, seeded before any echo is placed, plus every echo tile as it is
 * placed. A candidate tile is refused unless it is at least
 * {@link #MIN_SEPARATION_TILES} from all of them, whoever they belong to.
 *
 * <p><b>What that costs.</b> An echo with nowhere legal left to stand is not derived
 * at all: across the shipped files that is exactly one of the 142 asked for — the
 * "Mysterious Old Man" in Varrock gets one echo instead of two — and it moves four
 * others off a wander-box tile onto a ring offset the collision map then has to
 * vouch for. Skipping is the same answer this class already gives an echo whose tile
 * the collision map refuses, and for the same reason: the alternative is moving it
 * somewhere nobody has vouched for.
 *
 * <p><b>Determinism survives it</b>, which it would not if the answer depended on
 * the order the region file happens to list its citizens in. Sources are walked in
 * ascending uuid order, so the roster is a set as far as the derivation is
 * concerned: reordering the JSON, or a loader that read the two rosters the other
 * way round, cannot move anybody. Region file scope is the right unit because it is
 * the unit the scene builds in — {@link EntityScene#ensureBuilt} always has the
 * whole file and never half of it — so the same crowd appears every session
 * whatever order the player walks the regions in.
 *
 * <p>Within that, most tiles near a citizen are wall, counter, water or scenery, so
 * two sources of known-good ground are used, in this order:
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
 * live collision map. {@link #echoesOfRegion} touches nothing but its argument,
 * which is what lets the tests derive the whole shipped roster's echoes without a
 * game running.
 */
final class CitizenEcho
{
	/**
	 * The closest an echo may stand to <b>anything else the plugin renders in its
	 * region</b> — its source, another citizen, a market stall, or any other echo,
	 * whoever seeded it: two tiles, Chebyshev.
	 *
	 * <p>Two rather than one because one is not a gap. Citizen models are roughly a
	 * tile wide, so two of them on adjacent tiles interpenetrate and read as one
	 * clipped body rather than as two people — which is the "twins" failure in its
	 * most literal form. At two there is a whole empty tile between them and they
	 * read as two people standing near each other.
	 *
	 * <p>It says nothing about two <i>authored</i> entities, and cannot: 44 pairs of
	 * hand-placed entities in the shipped files are closer than this to each other,
	 * including eight that share a tile exactly. A human put those there on purpose
	 * (a stall and its owner, a pair of guards) and this feature does not get a vote
	 * on authored content — it only has to avoid adding to it.
	 */
	static final int MIN_SEPARATION_TILES = 2;

	/**
	 * The most echoes one authored citizen may seed: two.
	 *
	 * <p>A judgement, not arithmetic. The palette of the richest shipped citizen
	 * supports ten distinct re-deals; letting it spend all ten would put eleven
	 * copies of one body in one doorway. Two is what turns 135 authored citizens
	 * into 276 — the "twice as many" that was asked for — and it is the number the
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

	/**
	 * The order sources are considered in: ascending uuid.
	 *
	 * <p>Not the region file's own order, deliberately. Two citizens can want the
	 * same tile and only the first one asked may have it, so whatever decides who is
	 * asked first decides where somebody stands — and "whichever roster the loader
	 * read first, in whatever order the JSON happened to list them" is not a
	 * derivation, it is a coincidence that a re-export of the dataset would change.
	 * The uuid is the one thing about a citizen that is fixed forever (see
	 * {@link EntityDefinition#stableHash()}), so it is what orders them.
	 */
	private static final Comparator<EntityDefinition> BY_UUID =
		Comparator.comparing(EntityDefinition::getUuid);

	private CitizenEcho()
	{
	}

	/**
	 * Every echo one region file's roster seeds, placed so that no two of them — and
	 * no one of them and any authored entity — stand closer than
	 * {@link #MIN_SEPARATION_TILES}.
	 *
	 * <p>Pure: the same roster in gives the same echoes out, field for field, in the
	 * same order, in every session, whatever order the roster is listed in. Cheap
	 * enough to call from {@link EntityScene#ensureBuilt}'s region build rather than
	 * caching, and called from nowhere else.
	 *
	 * <p><b>The whole file, or it is not this method's answer.</b> Handing it a
	 * subset produces echoes that are correctly separated from that subset and
	 * possibly standing inside whatever was left out, which is the bug this signature
	 * exists to make hard to write. {@code EntityScene} holds a region's roster as
	 * one list and passes that list.
	 *
	 * @param roster every authored entity in one region file, in any order
	 * @return the echoes, grouped by source in ascending source-uuid order and
	 * oldest-index first within a source; empty if nothing in the roster can seed
	 * one. Never null.
	 */
	static List<EntityDefinition> echoesOfRegion(List<EntityDefinition> roster)
	{
		// Seeded with every authored tile before a single echo is placed, so the
		// citizens a human positioned are the fixed points and the derived ones move
		// around them rather than the other way round.
		Occupancy occupied = new Occupancy();
		for (int i = 0; i < roster.size(); i++)
		{
			occupied.claim(roster.get(i).getWorldLocation());
		}

		List<EntityDefinition> sources = new ArrayList<>(roster);
		sources.sort(BY_UUID);

		List<EntityDefinition> out = new ArrayList<>();
		for (int i = 0; i < sources.size(); i++)
		{
			out.addAll(echoesOfSource(sources.get(i), occupied));
		}

		return out;
	}

	/**
	 * Every echo one authored citizen seeds, given what is already standing in its
	 * region.
	 *
	 * @param occupied the region's claimed tiles. Read for every candidate and
	 *                 <b>added to</b> for every spot taken, which is what makes one
	 *                 citizen's echoes visible to the next citizen's placement.
	 */
	private static List<EntityDefinition> echoesOfSource(EntityDefinition source, Occupancy occupied)
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

		if (source.isCameo())
		{
			// A cameo is opt-in content behind its own checkbox, and an echo is not:
			// it is gated on the crowd density and on its source's city, and nothing
			// else. Deriving one from a cameo would put an extra human body in the
			// Grand Exchange for any user who set the density to Crowded, whatever
			// the cameos checkbox said — which is the exact leak that checkbox
			// exists to close. It would also be an extra person in a posed group
			// photo.
			//
			// Structural rather than incidental. The six shipped cameos carry no
			// authored recolour (their palette comes from an NPC composition at
			// render time), so the palette check below would refuse them anyway
			// today — and that is a coincidence about this dataset, not a rule.
			return NONE;
		}

		if (source.getNpcAppearanceId() != 0)
		{
			// Dressed from an NPC composition, so its colours come from the client and
			// not from its record — and an echo inherits the same npcAppearanceId,
			// because that is where its body comes from. Re-dealing the record's
			// palette would therefore change nothing at all: LivelyEntity.assemble
			// applies the composition's find/replace pair for an NPC-dressed entity,
			// not the record's. The echo would be a pixel-for-pixel copy of its source
			// standing two tiles away — the "twins" failure this whole class is built
			// to avoid, arrived at through the one door that does not go past the
			// palette check below.
			//
			// Refused rather than fixed by re-dealing the *composition's* palette:
			// that is the NPC's own colour scheme rather than anything a human chose
			// for this citizen, so dealing it around would be inventing an appearance,
			// which is exactly what this feature is not allowed to do.
			return NONE;
		}

		short[] find = source.getRecolorFind();
		short[] replace = source.getRecolorReplace();
		if (find.length < 2 || replace.length < 2)
		{
			// Nothing to re-deal — see the class javadoc. 49 of the 128 shipped citizens
			// that reach this line land here.
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
		List<Spot> spots = spotsFor(source, sourceHash, wanted, occupied);
		if (spots.isEmpty())
		{
			// Nowhere legal to stand: every box tile and every ring tile is within
			// MIN_SEPARATION_TILES of somebody already standing there. Skipped, not
			// moved — the same answer isPlaceable gives a refused tile.
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
	 * subject to {@link #MIN_SEPARATION_TILES} from every tile the region has already
	 * claimed — which is every authored entity in the file plus every echo placed so
	 * far, this source's own siblings included. Nothing here consults the collision
	 * map — that happens at spawn time, per session, in {@link #isPlaceable} — so
	 * this stays a pure function of the roster and can be asserted about offline.
	 *
	 * @param occupied claimed as each spot is taken, so the next candidate and the
	 *                 next citizen both see it
	 * @return up to {@code wanted} spots; possibly fewer, and possibly empty
	 */
	private static List<Spot> spotsFor(
		EntityDefinition source, long sourceHash, int wanted, Occupancy occupied)
	{
		WorldPoint anchor = source.getWorldLocation();
		List<Spot> candidates = new ArrayList<>();
		appendBoxCandidates(source, sourceHash, anchor, candidates);
		appendRingCandidates(sourceHash, anchor, candidates);

		List<Spot> picked = new ArrayList<>(wanted);
		for (int i = 0; i < candidates.size() && picked.size() < wanted; i++)
		{
			Spot candidate = candidates.get(i);
			if (occupied.isClear(candidate.tile))
			{
				occupied.claim(candidate.tile);
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
	 *
	 * <p>The distance test below is a pre-filter and not the rule: it drops the tiles
	 * nobody could use before they are rotated into a candidate order, so that the
	 * rotation is over usable tiles. {@link Occupancy} is what actually decides, and
	 * it decides against the whole region rather than against one tile.
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
	 * <p>The fallback for the 66 citizens with no box, and the top-up for the six
	 * shipped wanderers whose box cannot hold two well-separated echoes — either
	 * because it is too small, or because somebody else is already standing in the
	 * part of it that would do. These are candidates and nothing more: the ring says
	 * "this tile is the right distance away", and {@link StandableGround} is what says
	 * whether anybody could stand on it.
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

	/**
	 * The tiles one region has already given away, and the separation test against
	 * them.
	 *
	 * <p><b>A neighbourhood lookup rather than a distance loop</b>, because this is
	 * asked once per candidate tile per citizen and a region's roster can be ninety
	 * entities: "no claimed tile within {@link #MIN_SEPARATION_TILES}" is the same
	 * question as "none of the {@code (2n-1)²} tiles around this one is claimed", and
	 * the second is a handful of hash lookups instead of a walk of everything placed
	 * so far. At the shipped separation of two that is nine lookups.
	 *
	 * <p>Planes are part of the key. Two citizens on the same {@code x,y} one storey
	 * apart are not standing in each other, and the dataset really does stack them —
	 * {@link RenderPolicy#tileDistance} ignores the plane, so it could not be used
	 * for this on its own.
	 */
	private static final class Occupancy
	{
		private final Set<Long> claimed = new HashSet<>();

		/** @return true if nothing is standing within {@link #MIN_SEPARATION_TILES} */
		boolean isClear(WorldPoint tile)
		{
			int reach = MIN_SEPARATION_TILES - 1;
			for (int dx = -reach; dx <= reach; dx++)
			{
				for (int dy = -reach; dy <= reach; dy++)
				{
					if (claimed.contains(key(tile.getX() + dx, tile.getY() + dy, tile.getPlane())))
					{
						return false;
					}
				}
			}
			return true;
		}

		void claim(WorldPoint tile)
		{
			claimed.add(key(tile.getX(), tile.getY(), tile.getPlane()));
		}

		/**
		 * One tile as one long: twenty bits per axis, which is four times the width
		 * of the world map, and the plane above them.
		 *
		 * <p>Sound because every coordinate that reaches here is positive:
		 * {@link EntityDefinition} refuses a record whose {@code x} or {@code y} is
		 * zero or less, and the only arithmetic done to one afterwards is the
		 * {@code ±(MIN_SEPARATION_TILES - 1)} neighbourhood walk above. A negative
		 * coordinate would spill into the next field's bits, which is why that
		 * validation is the precondition rather than a formality.
		 */
		private static long key(int x, int y, int plane)
		{
			return ((long) plane << 40) | ((long) x << 20) | y;
		}
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
