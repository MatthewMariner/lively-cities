package com.matthewmariner.livelycities;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which part of a body a shipped model id actually <i>is</i> — the table
 * {@link BodySlotLintTest} needs in order to say "this citizen has no legs".
 *
 * <h2>Why this exists</h2>
 *
 * <p>A human played the shipped plugin and photographed townsfolk with bare legs.
 * Nothing in the plugin was broken: the records render exactly as authored, and what
 * they authorise is a figure with no trousers in it. 47 of the 98 kit-built human
 * citizens were missing a body slot — legs, hands or footwear — and that renders for
 * <b>every</b> user at <b>every</b> density, unlike the palette fault
 * ({@link CitizenEcho#isFlesh}) which only reaches somebody who moved the crowd dial
 * to {@code Crowded}. It is also, word for word, the complaint that got the
 * predecessor plugin pulled: <i>"Who is this man? Why does he not have any legs?"</i>
 *
 * <p>The dataset cannot answer "does this record have legs" by itself. A record is a
 * bare list of model ids, and whether one of them is a trouser or a hat is a fact
 * about the game cache. So the fact is measured once and written down here, exactly
 * like {@link ModelSkeletons} writes down which framemap a model is rigged to, and
 * for the same reason: a test that read the player's own cache would pass or fail
 * depending on whose machine it ran on.
 *
 * <h2>How each row was measured</h2>
 *
 * <p>By a read-only decode of {@code idx7} in the 1.12.36 cache — the model files
 * themselves — taking each model's vertex bounding box. The game's model space puts
 * {@code y = 0} at the tile plane and counts <b>negative upwards</b>, so a standing
 * human runs from about {@code y = -205} at the crown to {@code y = 0} at the floor,
 * and {@code x} is the left-right axis with the spine at {@code x = 0}.
 *
 * <p>Three reference heights decide the three slots. Each is chosen so that only one
 * kind of geometry can possibly be there — they are not thresholds on how wrong
 * something looks, they are places on the body:
 *
 * <p><b>Every one of the three is a bounding-box test</b> — the model's overall vertex
 * extents, not a question about a particular part of the mesh. That is stated up front
 * because it is the one thing about this table a reader could otherwise get wrong, and
 * the {@link #HANDS} bullet below used to.
 *
 * <ul>
 *   <li><b>{@link #FEET}: the model reaches {@code y >= 0}</b>, i.e. it touches or
 *       crosses the tile plane. Nothing but footwear does. Every foot model in the
 *       game is modelled slightly <i>below</i> the floor — the shipped boots
 *       {@code 183}, {@code 185} and {@code 4218} all bottom out at {@code +6}, and so
 *       do the boots the donor NPC kits carry ({@code 181}, {@code 358} and
 *       {@code 361}, all of them {@code -20..+6} give or take a unit) — because a
 *       sole sits on the ground rather than hovering over it.
 *
 *       <p><b>The margin here is one unit, not six.</b> The lowest-reaching body model
 *       that is not footwear stops at {@code -1}, and two of them do: {@code 16519}
 *       ({@code -10..-1}) and {@code 1239} ({@code -23..-1}), both of them hems. So the
 *       test separates the two kinds by a single unit of model space. It still
 *       separates them — a hem that stops at {@code -1} is a hem that is off the floor,
 *       and every sole crosses it — but a reader deciding how much slack this rule has
 *       should know the answer is "none to speak of" rather than the {@code -6} this
 *       javadoc claimed until 2026-08-30.</li>
 *   <li><b>{@link #LEGS}: the model covers {@code y = -55}</b>, mid-shin, so the only
 *       thing that can be there is a leg, a trouser, a skirt or a long robe. The kit
 *       legs model {@code 28285} spans {@code -114..-18} and passes straight through
 *       it.
 *
 *       <p><b>Both margins here are two units.</b> Above it, the lowest-hanging model
 *       that fills {@link #HANDS} and neither of the other two slots is {@code 9386},
 *       which stops at {@code -57}; the next pair, {@code 56139} and {@code 56164},
 *       stop at {@code -61}. Below it, the tallest model that fills {@link #FEET} and
 *       neither of the other two is {@code 11723} and {@code 25858}, both of which
 *       reach {@code -53}. The javadoc used to name {@code -64} above and {@code 4218}
 *       at {@code -38} below, which would have been margins of nine and seventeen;
 *       {@code 4218} is simply not the tallest footwear in the set. Two units either
 *       side is still a gap that no measured model crosses, and mid-shin is still the
 *       one height on a human where only one kind of geometry can be — but it is a
 *       narrow gap and it is now written down as one.</li>
 *   <li><b>{@link #HANDS}: the model covers {@code y = -95} and its bounding box
 *       reaches {@code |x| >= 25}</b> — hand height, and wider than a hip. The lateral
 *       half of that test is what separates a hand from a trouser: the kit hands
 *       {@code 176}, {@code 179} and the game's own {@code 10218} all span
 *       {@code -114..-91} at {@code x = ±35}, two blobs with a gap in the middle,
 *       while {@code 28285} reaches the same height but only to {@code x = ±17}.
 *
 *       <p><b>The two halves are measured independently, and that is the rule rather
 *       than a shortcut.</b> This javadoc used to argue the lateral half <i>at hand
 *       height</i> — "{@code ±35} at {@code y = -95} against {@code ±17}" — which is a
 *       different and stricter test, and the table below is not it: <b>16 of the 58
 *       {@link #HANDS} ids have no face spanning {@code y = -95} whose vertices reach
 *       {@code |x| >= 25}</b>, among them the kit sleeve {@code 256} and the dwarf
 *       sleeves {@code 2977}–{@code 2979}. They are wide at the shoulder and narrow at
 *       the wrist, which is what a sleeve is.
 *
 *       <p>The looser reading is the one kept, because the stricter one would fail
 *       {@link BodySlotLintTest}: several shipped citizens have no hand model that
 *       survives it, and reclassifying a sleeve as "not hands" would report them as
 *       shipping without hands when they are wearing sleeves. Widening a rule to keep
 *       a lint green is exactly the move this file's siblings refuse, so the honest
 *       version is that the rule was always the loose one and only the argument for it
 *       was strict. What the loose rule actually claims is weaker and still true: a
 *       model that reaches hand height <i>and</i> is wider than a hip somewhere is
 *       carrying an arm or a sleeve, and a trouser is neither.</li>
 * </ul>
 *
 * <h2>The two things that are not body geometry, and the trap in the second</h2>
 *
 * <ul>
 *   <li><b>{@link #HELD_PROP}</b> — a model whose x-extent does not straddle the
 *       centre line, i.e. {@code min_x > 0 || max_x < 0}. A held item hangs off one
 *       hand; a body part is built about the spine.
 *
 *       <p><b>This is the measurement trap, and the first attempt at this repair fell
 *       into it.</b> "Mike" the Lumbridge gardener carries a watering can ({@code 7368},
 *       {@code x -49..-13}) which sits squarely in the leg band at {@code y -97..-45}.
 *       Counted as geometry it makes his missing legs look like a tidy 26-unit gap
 *       between a can and a boot, which invites a taxonomy of gap sizes and a
 *       threshold for how big a gap is too big. He does not have a 26-unit gap. He has
 *       no legs, partly hidden behind a watering can. Excluding props first is what
 *       turns that back into the categorical fact it is.</li>
 *   <li><b>{@link #WHOLE_FIGURE}</b> — one mesh from crown to floor
 *       ({@code min_y <= -160} and {@code max_y >= -10}). These are entire NPC bodies
 *       rather than kit parts, and a record built from one has no slots to be missing.
 *       "Stranger" in Varrock is model {@code 35119} and nothing else, which is exactly
 *       what the game's own "Mysterious Stranger" is — seven NPC definitions, 8208
 *       among them, each of them that one model, {@code -205..-1}, hem on the floor. Asking which of its models is the
 *       boot is a category error, and bolting a boot onto it would push a shoe through
 *       the bottom of a robe.</li>
 * </ul>
 *
 * <p><b>{@link #NO_SLOT}</b> is everything else — heads, jaws, hair, hats, torsos,
 * arms, capes. It is listed rather than left implicit so that the four sets partition
 * the dataset and a model id with no row fails loudly instead of silently counting as
 * "not legs". {@code 54275} is in it, and that is the whole of the biggest single bug
 * this table found: see {@link BodySlotLintTest}.
 *
 * <p>The three slot sets deliberately <b>overlap</b> — 143 model ids fill at least one
 * slot and several fill two or three. A long robe like {@code 12144} covers the shin
 * and reaches the floor and has sleeves at hand height; a full-length garment is
 * legitimately all three. The lint asks "is any model in this record's kit in the
 * {@code LEGS} set", never "which model is the legs".
 */
final class BodySlots
{
	/** Fills the shin at {@code y = -55}: a leg, a trouser, a skirt, a long robe. */
	static final Set<Integer> LEGS = set(
		256, 259, 260, 262, 265, 268, 279, 317, 323, 432, 433, 479, 1079, 1680,
		2138, 2361, 2830, 2950, 2980, 2981, 3010, 4226, 4390, 5217, 5218, 5221, 5233, 6090,
		7045, 7363, 8925, 9388, 9622, 9761, 10742, 11357, 12138, 12144, 12147, 12752, 13405, 13409,
		13446, 13925, 14380, 14395, 14397, 14421, 18131, 18168, 18169, 18170, 18958, 19995, 20040, 20273,
		21812, 23905, 24472, 24476, 24840, 25668, 25669, 26125, 27628, 27639, 28285, 28346, 28512, 31783,
		32204, 36939, 54165, 56179, 56184, 56218);

	/** Reaches the tile plane: a boot, a shoe, a sandal, a hem that is on the floor. */
	static final Set<Integer> FEET = set(
		182, 183, 184, 185, 256, 259, 1079, 1569, 1680, 2138, 2361, 2384, 2408, 2468,
		2491, 2676, 2815, 2830, 2985, 3010, 4218, 5218, 7121, 7363, 7366, 9610, 10326, 11723,
		11724, 12144, 12147, 13409, 13446, 14186, 18128, 18129, 18169, 18170, 19268, 19891, 19945, 19947,
		19950, 19995, 20040, 20273, 23905, 24441, 24443, 24840, 25650, 25651, 25858, 26119, 27628, 31783,
		32204, 32751, 36939, 41886, 56179, 56184);

	/** Fills {@code y = -95} out at {@code |x| >= 25}: a hand, a gauntlet, a long sleeve. */
	static final Set<Integer> HANDS = set(
		150, 176, 177, 179, 180, 256, 260, 265, 302, 305, 317, 323, 353, 356,
		479, 1079, 1680, 2138, 2950, 2977, 2978, 2979, 3010, 3660, 3779, 4924, 5217, 5221,
		5233, 9386, 9388, 10301, 10743, 11732, 11812, 12144, 12147, 13409, 13446, 14652, 15106, 18958,
		21812, 23905, 24431, 24433, 24448, 24450, 24840, 25652, 26114, 26125, 27632, 31783, 31797, 32204,
		56139, 56164);

	/**
	 * One mesh, crown to floor — an entire NPC rather than a kit part. A record built
	 * from one of these is exempt from the slot rules; it has no slots.
	 */
	static final Set<Integer> WHOLE_FIGURE = set(
		2260, 3818, 14651, 21154, 21552, 25671, 32942, 35119, 36449, 37996, 47706);

	/**
	 * Hangs off one hand and never crosses the spine. Not body geometry, and counting
	 * it as such is what made a legless gardener look like a small gap — see the class
	 * javadoc.
	 */
	static final Set<Integer> HELD_PROP = set(
		238, 491, 509, 510, 539, 540, 546, 562, 563, 2990, 2992, 2993, 4045, 4591,
		7368, 7723, 18541, 23178, 23179, 24884, 25684, 25685, 27650, 31889, 31911, 46747, 56217);

	/** Body geometry that fills none of the three slots: heads, hair, hats, torsos, arms, capes. */
	static final Set<Integer> NO_SLOT = set(
		159, 164, 194, 196, 201, 206, 207, 208, 211, 214, 217, 219, 220, 223,
		228, 229, 230, 231, 235, 236, 241, 244, 246, 247, 248, 249, 251, 252,
		253, 281, 283, 295, 296, 299, 306, 308, 315, 324, 337, 364, 376, 377,
		378, 390, 391, 393, 398, 405, 417, 418, 431, 468, 483, 1239, 2578, 2970,
		2972, 2973, 2974, 2983, 2984, 2986, 3006, 3476, 3848, 4123, 4391, 4392, 4843, 5215,
		5430, 5433, 5434, 6085, 6086, 6364, 6755, 6848, 7050, 7059, 7061, 7063, 7072, 7611,
		8798, 8915, 9452, 9619, 9620, 9762, 9763, 10304, 10692, 10723, 10741, 10980, 11115, 11352,
		11766, 11809, 11811, 12137, 12735, 14373, 15007, 15103, 15939, 16519, 18086, 18167, 18546, 18554,
		18914, 20276, 20281, 21886, 23957, 24456, 24458, 24482, 24484, 25643, 25675, 25676, 26120, 26130,
		26619, 26630, 26632, 26634, 27139, 27154, 27619, 28515, 31794, 31805, 34283, 41801, 41802, 54275,
		56095, 56138, 56187);

	/**
	 * The four repair parts, and the only model ids the 2026-08-29 body-slot repair
	 * added to any record.
	 *
	 * <p>All four were already among the shipped model ids before the repair, which is
	 * the constraint that kept the distinct-model-id figure at 324 — see
	 * {@code ModelIdAuditTest.theDistinctModelIdFigureIsPinned}. A repair that reached
	 * for a part the dataset did not already carry would have widened the surface a
	 * cache renumbering can break, for no gain: the game has more than one trouser.
	 */
	static final int KIT_LEGS = 28285;
	static final int KIT_HANDS = 176;
	static final int KIT_BOOT = 185;
	static final int KIT_TALL_BOOT = 4218;

	/**
	 * The five colours every humanoid kit model is authored in, which the client
	 * substitutes per player: skin, hair, torso, legs, boots.
	 *
	 * <p>These are the game's own names for the five parts, not this project's — the
	 * measurement is in {@link CitizenEcho#isFlesh}'s javadoc, and {@link #SKIN_BASE} is
	 * {@link CitizenEcho#PLAYER_SKIN_BASE}, asserted equal in
	 * {@link BodySlotLintTest#theKitBaseColoursAreTheOnesThePluginItselfUses}. They are
	 * here because a record's {@code modelRecolorFind} slot is the <b>author's statement
	 * of which part they were aiming at</b>: a slot with {@code find = 25238} repaints
	 * the trousers and nothing else, whatever the kit is. That makes "what colour are
	 * this citizen's trousers" answerable from the record alone, which is what
	 * {@link BodySlotLintTest} needs and what the geometry table above cannot give.
	 */
	static final int SKIN_BASE = 4550;

	/** @see #SKIN_BASE */
	static final int HAIR_BASE = 6798;

	/** @see #SKIN_BASE */
	static final int TORSO_BASE = 8741;

	/** @see #SKIN_BASE */
	static final int LEGS_BASE = 25238;

	/** @see #SKIN_BASE */
	static final int BOOT_BASE = 4626;

	/**
	 * The four kit bases that are not skin — the slots a citizen's clothes live on.
	 *
	 * <p>Hair is in here with the garments. It is not cloth, but it is not skin either,
	 * and a head painted the face colour where the hair goes is the same category of
	 * mistake as a leg painted it: the author aimed the slot at something that is not a
	 * complexion.
	 */
	static final Set<Integer> NON_SKIN_KIT_BASES = set(HAIR_BASE, TORSO_BASE, LEGS_BASE, BOOT_BASE);

	/**
	 * The Elder Chaos druid hood.
	 *
	 * <p>{@code -198..-156} — head height, overlapping whatever head the record already
	 * carries. Two NPCs in the entire game wear it: 6607 "Elder Chaos druid" and 7560
	 * "Deathly mage". It is in {@link #NO_SLOT} because a hood is not a body part, and
	 * it is named here because 26 of the 142 shipped citizens were carrying it, 20 of
	 * them in the array position where their donor kit carries a trouser or a boot.
	 */
	static final int ELDER_CHAOS_HOOD = 54275;

	private BodySlots()
	{
	}

	private static Set<Integer> set(int... ids)
	{
		TreeSet<Integer> out = new TreeSet<>();
		for (int id : ids)
		{
			if (!out.add(id))
			{
				throw new IllegalStateException("duplicate model id " + id);
			}
		}
		return Collections.unmodifiableSet(out);
	}

	/** @return every model id this table has a row for, in ascending order */
	static Set<Integer> classified()
	{
		TreeSet<Integer> out = new TreeSet<>();
		for (Set<Integer> slot : Arrays.asList(LEGS, FEET, HANDS, WHOLE_FIGURE, HELD_PROP, NO_SLOT))
		{
			out.addAll(slot);
		}
		return Collections.unmodifiableSet(out);
	}
}
