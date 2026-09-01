package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
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
 * <p>The dataset held 142 hand-placed citizens when this class was written and the
 * user asked for twice as many. There was no second dataset, so the second half had
 * to come from the first:
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
 *   <li>the <b>127 liveried townsfolk</b> added on 2026-09-01 refuse it themselves,
 *       through {@code EntityRecord.noEcho} — the flag this javadoc used to say should
 *       be added "when there is a record that needs it, not before", and they are that
 *       record. Their colours sit on the slots they sit on in order to say which city
 *       the figure belongs to, and a re-deal's whole method is to put those colours on
 *       different slots;</li>
 *   <li>a source <b>dressed from an {@code npcAppearanceId}</b> is refused too, and
 *       for a sharper reason: its colours come from the composition rather than from
 *       its record, so re-dealing the record's palette would change nothing and the
 *       echo would be a pixel-for-pixel twin. One shipped citizen is in this bucket
 *       and not the one above — "Rufus" in Varrock square, who wears
 *       {@code NpcID.FARMER1} because his authored {@code modelIds} had no footwear
 *       in them (GitHub issue #1). He used to seed two echoes and now seeds none;</li>
 *   <li><b>48</b> more are refused for their <i>body</i>: they are sitting on
 *       something, miming a tool they were authored holding, welded to a bench by
 *       {@code mergedObjects}, or scaled and nudged to line up with one piece of
 *       scenery. An echo keeps all of that and answers to the name "Passer-by",
 *       which is the gardener-with-a-watering-can photograph. See
 *       {@link #isAnOrdinaryStandingBody};</li>
 *   <li>of the 87 that remain, 22 have fewer than two recolour pairs — no second
 *       slot to deal into, so they seed nothing;</li>
 *   <li>1 more carries two or more pairs whose {@code replace} values are all
 *       identical, so every re-deal is the deal it started with;</li>
 *   <li><b>36</b> more have a palette that can be re-dealt but not
 *       <i>honestly</i>: every rotation of it would move a skin colour onto a
 *       garment, a garment colour onto a face, or the game's own face colour off
 *       the slot the author put it on. See
 *       {@link #keepsEachColourOnItsOwnSideOfTheSkin};</li>
 *   <li>the remaining <b>28</b> seed {@link #MAX_ECHOES_PER_CITIZEN} echoes each
 *       where their palette supports two <i>distinct</i> re-deals, and one where it
 *       supports only one — 23 of them ask for two and 5 for one, so <b>51</b> echoes
 *       are asked for and all 51 find somewhere legal to stand (see below).</li>
 * </ul>
 * That comes to <b>51 echoes against 269 authored citizens — 320 in total,
 * 1.19×</b>. {@code CitizenEchoTest} recomputes all of those numbers from the
 * shipped files rather than trusting this paragraph.
 *
 * <p><b>The multiplier fell to 1.19× on 2026-09-01 without a single echo being
 * lost</b>, and the direction is worth reading correctly: 127 authored citizens
 * arrived, none of them seeds, so the derived half of the crowd is unchanged at 51
 * while the authored half nearly doubled. That is this feature doing a smaller share
 * of the work rather than doing it worse — the answer to "Lumbridge gets no echoes at
 * all" was always going to be authored content, and that is what arrived.
 *
 * <p><b>It used to be 96 seeds, 185 asked for, 184 placed and 2.30×</b>, and that
 * was the "roughly twice as many" the original request asked for. It was also
 * wrong, and visibly so: a human playing the shipped plugin photographed passers-by
 * with bare legs in Falador and Lumbridge, which is precisely what a re-deal does
 * when it rotates a trouser colour off the legs and a skin tone onto them, and a
 * gardener holding a watering can whose Examine read "Passer-by". The doubling was
 * being bought with figures that did not stand up, so the doubling is what gave
 * way — first to 1.51× when the flesh rule landed, then to 1.31× when the body rule
 * did, and 1.32× when the 2026-08-30 pass gave the game's own face colour its own
 * class.
 *
 * <p><b>It rose to 1.36× on 2026-08-31, and the rise was a data change rather than a
 * rule change.</b>
 * Nothing here was loosened: that pass repainted the seventeen authored
 * records that were painting a legs slot a colour out of {@link #isFlesh}'s gamut, on
 * the owner's report that figures still looked trouserless at {@link CrowdDensity#FULL}
 * — where no echo exists at all, so the fault was in the records and not in this class.
 * A wardrobe with one fewer flesh colour in it has more rotations that keep every
 * colour on its own side of the boundary, so four more citizens became seeds and five
 * more echoes appeared. The rule that admits them is the one that was already there.
 *
 * <p>Whether {@link CrowdDensity#CROWDED} still earns its place at 1.19× is a fair
 * question and a separate one. It is worth asking with the shape of the answer in
 * view rather than only the average: Varrock gets 21 echoes, Draynor 9 and Falador 8,
 * while <b>Lumbridge gets none at all</b>, so for a player standing there the setting
 * does nothing whatever. That was an argument about content — several cities were thin
 * and wanted authoring — and the 2026-09-01 pass is the content. The case for this
 * class is weaker than it was, and that is the right way for it to get weaker: a city
 * with 30 hand-placed citizens in it needs fewer strangers than one with 16.
 *
 * <p>(The 2.30× total read 324 between the top-up on 2026-08-29 and the review pass
 * that followed it, and a comment here observed that the dataset also held 324
 * distinct model ids. That was a coincidence, and neither figure means anything to
 * the other: the model-id count is pinned by
 * {@code ModelIdAuditTest.theDistinctModelIdFigureIsPinned} and no pass since has
 * moved it.)
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
 * at all. Across the shipped files that now costs nothing — all <b>51</b> asked for
 * find a tile — but it still moves <b>3</b> of them, belonging to 2 wanderers, off a
 * wander-box tile onto a ring offset the collision map then has to vouch for.
 * Skipping is the same answer this class already gives an echo whose tile the
 * collision map refuses, and for the same reason: the alternative is moving it
 * somewhere nobody has vouched for.
 *
 * <p>(All of these figures were wrong here until the 2026-08-29 review pass, and
 * wrong in the direction that flatters: this paragraph said "one of the 142" when
 * 142 is the citizen roster and not the number of echoes anybody asked for, and
 * "four others" when the real figure was in double digits. Nothing was checking
 * them. Everything is now — {@code CitizenEchoTest}'s
 * {@code theRingServesTheCitizensWithNoBoxAndTheWanderersWhoseBoxIsNotEnough}
 * recomputes the 3 and the 2, and {@code theShippedRosterIsHalfAgainAsBigUnderCrowded}
 * the 51. The one echo that used to have nowhere to stand — the "Mysterious Old
 * Man"'s second, in Varrock — is not blocked any more, because the flesh rule took
 * away the neighbours that were crowding it out. Nothing about the placement rule
 * changed; there is simply less to place.)
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
 *   <li><b>The source's authored wander box.</b> The 51 {@code WanderingCitizen}s
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
	 * <p>It says nothing about two <i>authored</i> entities, and cannot: 42 pairs of
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
	 * copies of one body in one doorway.
	 *
	 * <p>It is <b>not</b> the number that produces a doubling, and this javadoc said it
	 * was until the 2026-08-30 pass: two per citizen would turn 269 into 807 only if
	 * every citizen seeded two, and after the record, flesh and body rules 28 of them
	 * seed at all. The figure it actually produces is the one in this class's javadoc —
	 * 51 echoes, 320 in total, 1.19× — and this constant is only its ceiling. A cap and a
	 * total are different claims, and stating the cap as though it were the total is
	 * how "roughly twice as many" outlived the arithmetic that supported it.
	 */
	static final int MAX_ECHOES_PER_CITIZEN = 2;

	/**
	 * The flesh gamut, as a box in the game's own 6/3/7-bit HSL colour word. Read
	 * {@link #isFlesh} for where each edge comes from; they are here rather than
	 * inline so that the one place a number could be fiddled is the one place a
	 * reviewer has to look.
	 */
	private static final int FLESH_MIN_HUE = 2;
	private static final int FLESH_MAX_HUE = 7;
	private static final int FLESH_MIN_SATURATION = 1;
	private static final int FLESH_MAX_SATURATION = 6;

	/**
	 * The colour the game itself paints a face: the skin base every humanoid kit model
	 * is authored in and the client substitutes per player.
	 *
	 * <p>Not a bound and not a judgement — it is one of the five kit base colours the
	 * cache names, and it is here because {@link #keepsEachColourOnItsOwnSideOfTheSkin}
	 * treats it as its own class. A tan that is <i>like</i> skin may be dealt around a
	 * wardrobe; the colour that <i>is</i> skin stays where the author put it.
	 */
	static final int PLAYER_SKIN_BASE = 4550;

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

	/** Shared, so the 241 citizens that seed nothing do not each allocate a list. */
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

		if (source.isNoEcho())
		{
			// The record says so. This is the one gate here that is not derived from
			// anything, and it is the flag this class's javadoc said should be added
			// "when there is a record that needs it, not before" — see
			// EntityRecord.noEcho for the record that needed it and why no derivable
			// rule could have caught it.
			//
			// In short: an echo's difference from its source is the source's palette
			// re-dealt, and a liveried citizen's palette is the one thing about it
			// that must not move. Every other gate below asks what a figure is or what
			// it is doing; this one asks what its colours are for, which the numbers
			// cannot say.
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

		if (!isAnOrdinaryStandingBody(source))
		{
			// Its body is doing something an anonymous passer-by cannot be doing. See
			// isAnOrdinaryStandingBody: 48 shipped citizens land here — 44 for their
			// idle animation, 7 for a translate, 2 for a scale and 2 for a merged
			// object, several of them for more than one of those.
			return NONE;
		}

		short[] find = source.getRecolorFind();
		short[] replace = source.getRecolorReplace();
		if (find.length < 2 || replace.length < 2)
		{
			// Nothing to re-deal — see the class javadoc. 22 of the 87 shipped citizens
			// that reach this line land here.
			return NONE;
		}

		int[] deals = distinctDeals(replace);
		if (deals.length == 0)
		{
			// A palette with two or more pairs and no honest re-deal of it. Two
			// different ways of being in that position, and 37 shipped citizens that
			// reach this line are:
			//
			//  - 1 replaces every slot with the same colour, so every rotation is the
			//    deal it started with;
			//  - 36 have a mixed wardrobe whose every rotation would move a colour
			//    across the flesh boundary — a tunic colour onto the face, a skin tone
			//    onto the legs. See keepsEachColourOnItsOwnSideOfTheSkin.
			//
			// Counted apart rather than together in CitizenEchoTest, because they are
			// different facts about the dataset and only one of them is a judgement.
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
	 * Whether this source's <b>body</b> is something an anonymous stranger could
	 * plausibly have.
	 *
	 * <h2>The hole this closes</h2>
	 *
	 * <p>{@link EntityDefinition#echoOf} overwrites five things — name, examine text,
	 * remarks, orientation and palette — and <b>inherits</b> five others: the model ids,
	 * the merged objects, the scale, the translate and the idle animation. Until this
	 * gate only one of those inherited fields was checked at all, and it was the sixth
	 * one ({@code npcAppearanceId}). So everything the other five can express walked
	 * straight into a figure called {@link #ECHO_NAME}:
	 *
	 * <ul>
	 *   <li>a gardener holding a watering can, examined as "Passer-by" — which is a
	 *       photograph, not a hypothetical;</li>
	 *   <li>a butler sitting on a chair that is not there, two tiles from the chair he
	 *       was authored on;</li>
	 *   <li>a smith swinging a hammer at an anvil somebody else is standing at;</li>
	 *   <li>a citizen with a bench welded into his model by {@code mergedObjects},
	 *       carrying the bench with him;</li>
	 *   <li>a citizen shrunk to half size or nudged off his tile by {@code scale} and
	 *       {@code translate}, which were authored to line him up with a specific piece
	 *       of scenery and mean nothing anywhere else.</li>
	 * </ul>
	 *
	 * <p>Every one of those is the same fault: an echo's name says "nobody in
	 * particular" and its body says otherwise. The name cannot be fixed — an echo that
	 * kept its source's name would be the impersonation problem this whole class exists
	 * to avoid — so the body has to be refusable instead.
	 *
	 * <h2>The rule</h2>
	 *
	 * <p><b>The animation half is not a new judgement.</b> It is
	 * {@link LivelyAnimation#ArmsCrossedReady}'s rule, already written down there and
	 * already enforced on authored records: <i>a figure that only ever stands there gets
	 * a pose, never an action; an action animation assumes the item it was authored
	 * around, and a figure with no such model in its composition plays it as a mime.</i>
	 * An echo is by construction a figure that only ever stands there — it does not walk
	 * ({@link EntityDefinition#echoOf} forces {@link EntityType#StationaryCitizen}) and
	 * it does not speak. So an echo may only inherit a pose. The set below is that rule
	 * applied to {@link LivelyAnimation} as a whole rather than to the cases that
	 * happened to be photographed, and it has three groups, each with its own reason.
	 *
	 * <p><b>The prop half needs no list.</b> {@code mergedObjects} is scenery welded
	 * into a body, and a non-unit {@code scale} or {@code translate} is a figure
	 * positioned against a particular piece of the world. Both are refused outright.
	 * Note that {@code echoOf} clones {@code scale} and {@code translate} but
	 * <i>aliases</i> {@code modelIds} and {@code mergedObjects} — the clone is not a
	 * defence, it is just a copy of the same wrong numbers.
	 *
	 * <h2>What is deliberately not here</h2>
	 *
	 * <p><b>No per-record {@code noEcho} flag.</b> One was proposed, for uniforms and
	 * one-off costumes that no rule can derive. It is not added, on the evidence: the
	 * animation and prop rules refuse every case in the shipped dataset that needed
	 * refusing, and a schema field would be a rule enforced by whoever remembers to set
	 * it. The two documents that proposed it also disagreed about its default, which is
	 * the tell that nobody had decided what it meant. If a future record genuinely needs
	 * it, it should be added defaulting to {@code false}, matching
	 * {@link EntityRecord#cameo} — but it should be added when there is a record that
	 * needs it, not before.
	 *
	 * <p><b>No check on the models themselves.</b> That would need a vendored table of
	 * which model id is a prop, and the derivation is not allowed one — the same table
	 * exists on the test side ({@code BodySlots}) where it can be audited offline, and
	 * {@code BodySlotLintTest} is what keeps a citizen's body honest. This gate is about
	 * what the record <i>says</i> its body is doing.
	 */
	static boolean isAnOrdinaryStandingBody(EntityDefinition source)
	{
		if (!source.getMergedObjects().isEmpty())
		{
			// Scenery welded into the body. "Morten" and "Jofridr" each carry a bench,
			// and an echo of either would carry the bench two tiles away and set it down
			// in the middle of the road.
			return false;
		}

		if (source.getScale() != null || movesOffItsTile(source.getTranslate()))
		{
			// Both are only ever authored to line one figure up with one piece of the
			// world: "Nightfire" is scaled to half size and pushed a tile north to sit
			// where he sits, "Kaldrik" is nudged sideways to stand at a workbench. Away
			// from that scenery they are simply a citizen who is the wrong size or in
			// the wrong place.
			//
			// The two fields are tested differently on purpose, because their identities
			// are not the same. A translate of {0,0,0} moves nothing, so a record can
			// carry one harmlessly. A scale does not work that way: LivelyEntity negates
			// the stored value and multiplies by 128 before handing it to
			// ModelData.scale, so the no-op is {-1,-1,-1} and {0,0,0} would collapse the
			// figure to a point. Rather than guess at an identity no shipped record uses,
			// any authored scale at all is refused — a record that does not want to be
			// resized simply has no scale field, which is what 302 of the 311 shipped
			// records do — only 9 carry one, and 2 of those are citizens.
			return false;
		}

		LivelyAnimation idle = source.getIdleAnimation();
		return idle == null || !NOT_AN_ANONYMOUS_POSE.contains(idle);
	}

	/** @return true if this translate is present and actually moves the figure */
	private static boolean movesOffItsTile(@Nullable float[] vector)
	{
		if (vector == null)
		{
			return false;
		}

		for (float component : vector)
		{
			if (component != 0f)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Idle animations an echo may not inherit — see {@link #isAnOrdinaryStandingBody}.
	 *
	 * <p>Named individually rather than inferred, for the same reason
	 * {@code UprightPoseTest}'s list is: there is no property of an animation id that
	 * says "this one assumes a chair". It is a reading of what the animation depicts,
	 * and it belongs in a list a human can argue with.
	 */
	private static final Set<LivelyAnimation> NOT_AN_ANONYMOUS_POSE =
		Collections.unmodifiableSet(EnumSet.of(
			// --- Anchored to furniture ------------------------------------------
			// The figure is sitting on or leaning against something that is not part
			// of it. Move it two tiles and it sits on air.
			LivelyAnimation.Sitting,
			LivelyAnimation.ChurchSitting,
			LivelyAnimation.DwarfSit,
			LivelyAnimation.DwarfLean,
			LivelyAnimation.RiftGuardianSit,

			// --- Mimes a held tool ----------------------------------------------
			// Authored around an item. The source may well be carrying that item in
			// its modelIds; an echo standing somewhere else is a person bent over
			// nothing, working at nothing.
			LivelyAnimation.Woodcutting,
			LivelyAnimation.Fishing,
			LivelyAnimation.Mining,
			LivelyAnimation.DwarfMining,
			LivelyAnimation.DwarfMining2,
			LivelyAnimation.AnvilBang,
			LivelyAnimation.DwarfSmith,
			LivelyAnimation.Fletching,
			LivelyAnimation.FireCook,
			LivelyAnimation.RangeCook,
			LivelyAnimation.FurnaceSmelt,
			LivelyAnimation.HerbloreMix,
			LivelyAnimation.Alching,
			LivelyAnimation.WateringCanPour,
			LivelyAnimation.Grabbing,
			LivelyAnimation.FrontalGrab,
			LivelyAnimation.Eat,
			LivelyAnimation.BuryOrPickingUp,

			// --- A pose that is about a prop ------------------------------------
			// Standing still, but standing still holding something. The stick and the
			// book are in the animation whether or not they are in the models.
			LivelyAnimation.HumanWithStickIdle,
			LivelyAnimation.StandingWithBook));

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
	 * different order. Three things are excluded:
	 * <ul>
	 *   <li>{@code k = 0}, which is the source.</li>
	 *   <li>Any {@code k} whose result was already produced by a smaller one. A
	 *       palette like {@code [red, blue, red, blue]} has only one distinct
	 *       re-deal however many rotations it admits, and without this two sibling
	 *       echoes would be issued "different" deals that come out identical — the
	 *       twins problem moved one step sideways.</li>
	 *   <li>Any {@code k} that would move a colour across the flesh boundary — see
	 *       {@link #keepsEachColourOnItsOwnSideOfTheSkin}.</li>
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
			if (!keepsEachColourOnItsOwnSideOfTheSkin(replace, k))
			{
				continue;
			}

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
	 * Whether rotating by {@code deal} leaves every slot wearing a colour of the same
	 * <i>kind</i> as the one the author put there — the game's own face colour, some
	 * other flesh tone, or not flesh at all.
	 *
	 * <p><b>This is the whole of the "no trousers" fix, and it is a rule about the
	 * palette rather than a threshold on anything.</b> {@code find} is handed to the
	 * echo verbatim and only {@code replace} is rotated (see {@link #echoesOfSource}),
	 * so slot {@code i} still recolours whatever region of the body the author aimed
	 * it at — it just paints it with the colour authored for slot {@code i + deal}. A
	 * citizen whose palette says "skin here, tunic there" therefore has exactly one
	 * failure mode when it is re-dealt: the tunic colour lands on the skin slot and the
	 * skin colour lands on the tunic slot. That is a figure with a blue face and bare
	 * legs, which is the photograph this rule exists because of, and it is not a
	 * near-miss that a distance threshold could grade — it is a colour on the wrong
	 * side of a boundary.
	 *
	 * <p>So the boundary is what is enforced. A deal is admissible only if it is a
	 * permutation <i>within</i> the flesh colours and <i>within</i> the non-flesh
	 * colours; any deal that carries a colour across is refused outright, however
	 * close the two colours happen to be. The guarantee that buys is falsifiable
	 * without agreeing on a single number: <b>no surviving deal moves a colour across
	 * the flesh boundary</b>, which {@code CitizenEchoTest} asserts over every deal of
	 * every shipped palette rather than over a count of anything.
	 *
	 * <h2>Two classes were not enough, and the shipped data proved it</h2>
	 *
	 * <p>Until the 2026-08-30 pass this asked one question — flesh or not flesh — and
	 * <b>two of the 44 shipped echoes went out with the game's own face colour on a
	 * garment anyway</b>. The clearest is the echo of "Mary", whose record is in region
	 * 12852 and whose city is therefore Varrock — this javadoc said Draynor until
	 * 2026-08-31, which is where the region borders rather than where {@link City} files
	 * it. Her palette was {@code [322, 5532, 8099, 4550]} against the {@code find} slots
	 * {@code [8741, 25238, 6798, 43072]}, so {@value #PLAYER_SKIN_BASE} — the base
	 * colour the client substitutes for a player's face — sits on {@code 43072}, the
	 * base the kit's arm and hand models carry, which is a bare forearm and is right.
	 * Rotating by two lands it on {@code 25238}, the <b>legs</b> base. Her other flesh
	 * tone {@code 5532} is a dark brown trouser, and it is flesh-class too, so the
	 * two-class rule saw a flesh colour swapping places with a flesh colour and allowed
	 * it. The echo it produced is the photograph: a passer-by whose trousers are painted
	 * the exact colour the game paints faces.
	 *
	 * <p>So there are three classes rather than two, and the third is a single value:
	 * {@value #PLAYER_SKIN_BASE} is where the author put the face, and a re-deal may
	 * rearrange a wardrobe but <b>may not move the face</b>. It needs no new number,
	 * because the value is the game's own and is already named in {@link #isFlesh}'s
	 * javadoc as the kit base every human model is authored in.
	 *
	 * <p><b>The third class costs nothing on the shipped data any more, and saying so is
	 * the point.</b> Mary's {@code 5532} was repainted {@code 8472} on 2026-08-31, because
	 * a dark brown trouser inside the flesh gamut is still a trouser inside the flesh
	 * gamut — see {@code BodySlotLintTest.noLegsSlotIsPaintedAColourFromTheFleshGamut}.
	 * With it gone she has one flesh colour rather than two, so the two-class rule refuses
	 * the same rotation the three-class rule does, and she seeds nothing either way.
	 * Measured over every rotation of every shipped palette, <b>zero</b> are now allowed
	 * by two classes and refused by three; {@code CitizenEchoTest} asserts that count
	 * rather than letting this paragraph claim it.
	 *
	 * <p>The third class stays regardless. It is not held up by the dataset happening to
	 * need it — it is the statement that the one colour the client puts on a face may not
	 * be dealt onto a garment, and the next record somebody authors is exactly the case it
	 * exists for. Removing a rule because the data no longer exercises it is how the data
	 * gets to stop exercising it quietly.
	 *
	 * <p>What the rule as a whole costs is most of the crowd, and that is the honest
	 * price of the feature having been wrong rather than a regression in it: over the
	 * shipped files this rule alone takes the seeds from 97 to 44 and the echoes asked
	 * for from 186 to 81; the body rule in {@link #isAnOrdinaryStandingBody} then takes
	 * them to 28 and 51. A citizen whose wardrobe simply has no re-deal that keeps skin
	 * on skin now seeds nothing, which is the same answer this class already gives a
	 * citizen with one recolour pair.
	 *
	 * <p><b>Ordered before the distinctness check, and the ordering is a preference
	 * rather than a fix for a hazard that exists.</b> A refused deal is not entered into
	 * {@code seen}, which reads as protection against a refusal masking a later legal
	 * rotation that produces the same palette — but no such pair can occur. Two
	 * rotations that produce the same palette agree on the class of every slot, so this
	 * predicate returns the same answer for both: its truth set is the stabiliser of the
	 * class vector, a subgroup of the rotations, and a subgroup does not separate two
	 * elements that already act alike. The ordering stays because it is the one that
	 * needs no argument to read as correct, and because a future rule that is <i>not</i>
	 * invariant under the palette would need it. No test can distinguish the two orders;
	 * the claim that one is safer is the thing being corrected here.
	 */
	private static boolean keepsEachColourOnItsOwnSideOfTheSkin(short[] replace, int deal)
	{
		int n = replace.length;
		for (int i = 0; i < n; i++)
		{
			if (skinKind(replace[(i + deal) % n]) != skinKind(replace[i]))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Which side of the skin boundary a colour is on, at the resolution the re-deal
	 * rule needs: {@code 2} for the game's own face colour, {@code 1} for any other
	 * flesh tone, {@code 0} for cloth.
	 *
	 * <p>The middle class is a gamut and is argued for in {@link #isFlesh}. The top
	 * class is a single value and needs no argument: {@value #PLAYER_SKIN_BASE} is not
	 * <i>like</i> a face, it is the colour the client puts on one.
	 */
	private static int skinKind(short colour)
	{
		if ((colour & 0xFFFF) == PLAYER_SKIN_BASE)
		{
			return 2;
		}
		return isFlesh(colour) ? 1 : 0;
	}

	/**
	 * Whether a palette colour is skin.
	 *
	 * <h2>How the gamut was chosen</h2>
	 *
	 * <p>The game packs a model colour as HSL in sixteen bits: six bits of hue, three
	 * of saturation, seven of lightness. Human skin occupies a narrow wedge of that
	 * space, and the wedge is <b>measured off the game's own data rather than
	 * eyeballed</b>. Every humanoid kit model is authored in five base colours which
	 * the client substitutes per player, and the dataset's records are copies of NPC
	 * kits authored in exactly the same five: {@code 4550} is the skin base,
	 * {@code 6798} hair, {@code 8741} torso, {@code 25238} legs, {@code 4626} boots.
	 * So "what colour does the game paint a face" has a literal answer — every
	 * {@code find = 4550} recolour on every NPC definition in the cache — and a
	 * read-only decode of the 1.12.36 cache gives <b>3,320 such recolours across 320
	 * distinct target colours</b>. Their distribution is what the two bounds below are:
	 *
	 * <ul>
	 *   <li><b>Hue {@value #FLESH_MIN_HUE} to {@value #FLESH_MAX_HUE}</b> of 64, i.e.
	 *       {@code 11°} to {@code 39°} — the orange/tan sector between red and yellow.
	 *       Hue 4, where the skin base {@code 4550} itself sits, takes 1,850 of the
	 *       3,320 on its own; hues 2–7 together take 2,835, i.e. 85%. The upper edge is
	 *       also the game's own kit boundary: hue 8 is where the <i>torso</i> base
	 *       {@code 8741} lives, so hue 8 is mostly the browns and khakis a tunic is
	 *       dyed in. What is left outside on the other side is mostly hue 0 (311 uses)
	 *       — the greys, bone-whites and blacks of skeletons and ghosts, which are not
	 *       flesh in any sense this rule cares about.
	 *
	 *       <p><b>The upper edge is the one place this rule knowingly cuts skin</b>, and
	 *       saying otherwise would be the easiest sentence in this file to leave
	 *       standing. Hue 8 is the target of 30 of the 3,320, spread over seven
	 *       colours, and they are genuine complexions rather than cloth: {@code 8646}
	 *       is hue 8 at saturation 3 and lightness 70 — the skin base {@code 4550}'s
	 *       own saturation and lightness, one hue rung along — and the game paints 16
	 *       faces with it. Thirty out of 3,320 is where the boundary was put because a
	 *       boundary has to go somewhere the game's own kit puts one, not because
	 *       nothing skin-coloured lives above it. The cost is on the side this rule
	 *       chooses to err towards: a complexion called cloth is a deal
	 *       <i>permitted</i>, which is the wrong side — so the thirty are the residual
	 *       risk this gamut carries, and they are written down rather than
	 *       characterised away.</li>
	 *   <li><b>Saturation {@value #FLESH_MIN_SATURATION} to
	 *       {@value #FLESH_MAX_SATURATION}</b> of 7, i.e. everything except the two
	 *       rungs at the ends. Saturation 0 is the greyest rung the palette has — at
	 *       that setting the hue bits barely tint anything, so a "hue 4, saturation 0"
	 *       colour is not in the orange wedge at all, it is grey. Saturation 7 is
	 *       nearly fully saturated: a costume colour, not a face. The measured skin
	 *       recolours agree — saturation 3 (1,291) and 4 (835) dominate, and the two
	 *       excluded rungs are 222 and 81.</li>
	 * </ul>
	 *
	 * <p>Together the two bounds cover <b>83%</b> of the recolours the game itself
	 * applies to the skin base.
	 *
	 * <p><b>Lightness is deliberately not part of it.</b> Lightness is the axis skin
	 * genuinely varies along — the measured targets run the whole range from 0 to 125,
	 * and the shipped dataset paints its own skin slots from lightness 22 to the
	 * nineties — so any bound on it would be a threshold discriminating between
	 * complexions, which is both wrong and exactly the kind of tunable number this rule
	 * exists in order not to have.
	 *
	 * <p><b>Where the remaining error goes is not symmetric, and the bounds are set to
	 * put it on the harmless side.</b> A skin tone wrongly called cloth is a deal
	 * <i>permitted</i> that paints a leg with a complexion — the photograph. A cloth
	 * colour wrongly called skin is a deal <i>refused</i> — one echo fewer. So the
	 * generous edge is taken wherever the evidence is thin: saturation 1 is kept in
	 * even though it is nearly grey, because the game uses it for the skin base 361
	 * times, more often than saturation 2. And the boots base {@code 4626} is hue 4
	 * saturation 4 — inside the wedge, because a dark leather boot genuinely is the
	 * same hue as a dark complexion — so a record that dyes its boots a brown of that
	 * family has them held still. That is a deal refused too often, which is the side
	 * that cannot produce a photograph.
	 *
	 * <p><b>What it does not claim.</b> Not every figure in the dataset is human — a
	 * "Demon Butler" wears saturation-7 red where a face goes, and a shrouded figure
	 * wears white there. Those colours are correctly not flesh, and the rule simply has
	 * no opinion about them: it is a rule about where <i>skin-coloured</i> paint may
	 * go, and it is complete with respect to that.
	 */
	static boolean isFlesh(short colour)
	{
		int packed = colour & 0xFFFF;
		int hue = packed >>> 10;
		int saturation = (packed >>> 7) & 0x07;
		return hue >= FLESH_MIN_HUE && hue <= FLESH_MAX_HUE
			&& saturation >= FLESH_MIN_SATURATION && saturation <= FLESH_MAX_SATURATION;
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
	 * <p>The fallback for the 206 shipped citizens with no box, and the top-up for the
	 * three shipped wanderers whose box cannot hold two well-separated echoes — either
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
