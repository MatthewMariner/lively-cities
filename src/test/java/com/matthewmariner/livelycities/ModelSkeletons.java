package com.matthewmariner.livelycities;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which framemap the <i>body</i> of a shipped record is rigged to — the other half of
 * the question {@link AnimationSkeletons} answers about animations.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link AnimationSkeletonTest}'s first rule compares a citizen's two animations
 * against each other, which is a question the dataset can ask about itself. It has a
 * blind spot exactly one record wide: a figure with <i>one</i> animation has nothing to
 * disagree with, and every cameo is such a figure. Three records walked through that
 * gap — "Sludgellama" posed on the Braindeath Island rig while wearing a Rogue's body,
 * and "Child"/"Liam" playing human emotes on gnome-rigged child models. Comparing the
 * animation's framemap against the rig the record's <i>own models</i> imply is the check
 * that catches all three, and it needs this table.
 *
 * <h2>What a row actually claims, and how it was measured</h2>
 *
 * <p>A model id carries no rig of its own that can be read out of the cache in
 * isolation — rigging lives in the NPC definition that composes the model, not in the
 * model file. So the claim a row makes is the inference the evidence supports:
 *
 * <blockquote>every NPC definition in the 1.12.36 cache whose {@code models} list
 * contains this id has its own {@code stand} and {@code walk} on this framemap.</blockquote>
 *
 * <p>That inference is strong because the game itself almost never breaks it: of the
 * 13,533 NPC definitions whose {@code stand} and {@code walk} both resolve to a
 * framemap, 13,529 use exactly one — the four that do not are two fishing spots and the
 * two Nightmare bosses. Three shipped model ids are the exception on this side and are
 * recorded as such below rather than rounded off.
 *
 * <p>The numbers were produced by an independent read-only decoder run against the
 * 1.12.36 cache, walking {@code idx2/archive 9} (NPC definitions), {@code idx2/archive
 * 12} (sequences) and {@code idx0} (frames, whose first two bytes name the framemap).
 * They are transcribed here rather than read at test time for the same reason
 * {@link AnimationSkeletons}' numbers are: a test that reads the player's own game cache
 * passes or fails depending on whose machine it runs on.
 *
 * <h2>Coverage, and what it deliberately does not cover</h2>
 *
 * <p>This table holds 376 model ids: 340 that appear on at least one NPC, listed
 * below with the framemap that implies, and 36 that appear on no NPC at all — object
 * geometry (braziers, stalls, planters), which no amount of decoding turns into a
 * rig. Those are named in {@link #NO_NPC_EVIDENCE} so that "this record was skipped"
 * stays a counted, reviewable outcome rather than a silent one.
 *
 * <h2>Why 376 rows for a 324-id dataset</h2>
 *
 * <p>376 was the dataset's distinct-model-id count when these numbers were decoded.
 * The nine-city cut on 2026-08-24 took it to 324, leaving 52 rows here describing
 * model ids nothing ships any more. <b>They were deliberately kept.</b>
 *
 * <p>The count on each framemap comment below is therefore a count of <i>that row</i>,
 * not of what ships: framemap 0 lists 255 model ids and 218 of them are in the
 * dataset today, and eight of the smaller rows now describe nothing shipped at all.
 * They used to say "shipped model ids" and were left saying it through the cut, which
 * is the reason they say "model ids" now — a row is a reading of the cache, and the
 * dataset does not get a vote on whether it is true.
 *
 * <p>The reasoning is the difference between this table and something like
 * {@code EntityTheme}. A theme row is a <i>judgement about a citizen</i> — if the
 * citizen is gone the row asserts nothing and should go with it. A row here is a
 * <i>measurement of the game cache</i>: "every NPC built out of model 6640 stands and
 * walks on framemap 0" is true or false about the 1.12.36 cache and has nothing to do
 * with which regions this plugin happens to ship. Deleting those rows would discard
 * correct, expensively-obtained readings — the decoder run is not reproducible from
 * inside this repository — and the top-up pass that brings the thin cities back up
 * will want them.
 *
 * <p>What that decision costs is the property the old count quietly carried: that the
 * table and the dataset were the same set, so a shipped id with no row would have
 * shown up as a size mismatch. That is now stated as its own assertion instead —
 * {@code AnimationSkeletonTest.everyShippedModelIdIsEitherRiggedOrExplicitlyUnriggable}
 * — which is a stronger check than the size pin ever was, because it names the
 * uncovered id rather than reporting that two numbers differ.
 */
final class ModelSkeletons
{
	private static final Map<Integer, Set<Integer>> BY_MODEL = new TreeMap<>();

	private static final Map<Integer, Integer> BY_BODY = new TreeMap<>();

	/**
	 * Shipped model ids that appear in no NPC composition in the cache, so nothing can
	 * be inferred about their rig.
	 *
	 * <p>Pinned as a set rather than a count: a model id moving off this list because
	 * somebody looked it up properly is a real improvement, and a model id moving
	 * <i>onto</i> it is a transcription that got dropped.
	 */
	static final Set<Integer> NO_NPC_EVIDENCE = Collections.unmodifiableSet(
		new TreeSet<>(java.util.Arrays.asList(
			431, 1079, 1239, 1569, 1680, 2138, 2260, 2361, 2384, 2408, 2468, 2491, 2578, 2676, 2815,
			2830, 3818, 5217, 12147, 13446, 14186, 14651, 14652, 15049, 16519, 19268, 19891, 19995,
			20040, 24840, 24884, 25033, 25858, 32751, 36449, 36939)));

	static
	{
		// framemap 0 (human) — 255 model ids
		rig(0,
			150, 159, 164, 169, 176, 177, 179, 180, 182, 183, 184, 185, 194, 196, 201, 202, 206, 207,
			208, 211, 214, 217, 219, 220, 223, 228, 229, 230, 231, 235, 236, 238, 241, 246, 247, 248,
			249, 251, 252, 253, 256, 259, 260, 262, 263, 265, 268, 279, 281, 283, 295, 296, 299, 302,
			305, 306, 308, 311, 315, 317, 323, 324, 337, 356, 364, 376, 377, 378, 390, 391, 393, 398,
			405, 417, 418, 432, 433, 444, 448, 468, 479, 483, 491, 509, 510, 534, 539, 540, 546, 562,
			563, 3194, 3476, 3660, 3707, 3711, 3779, 3848, 4045, 4123, 4218, 4226, 4390, 4391, 4392,
			4591, 4843, 4844, 4924, 5215, 5218, 5221, 5233, 5430, 5433, 5434, 6085, 6086, 6090, 6364,
			6640, 6642, 6645, 6652, 6654, 6657, 6661, 6663, 6666, 6668, 6671, 6673, 6675, 6677, 6678,
			6679, 6703, 6705, 6708, 6710, 6755, 6848, 7121, 7366, 7368, 7611, 7723, 8798, 8915, 8925,
			8934, 9452, 9619, 9620, 9622, 9761, 9762, 9763, 10301, 10304, 10692, 10705, 10723, 10741,
			10742, 10743, 10980, 11115, 11352, 11354, 11357, 11359, 11732, 11766, 11809, 11811, 11812,
			12137, 12138, 12144, 12752, 13925, 14373, 14380, 14395, 14397, 14421, 14423, 15007, 15103,
			15106, 15939, 16456, 18086, 18128, 18129, 18131, 18541, 18546, 18554, 18958, 19945, 19947,
			19950, 21812, 21886, 23178, 23179, 23957, 25643, 25650, 25651, 25652, 25668, 25669, 25671,
			25675, 25676, 25684, 25685, 26114, 26119, 26120, 26125, 26130, 26619, 26630, 26632, 26634,
			27139, 27154, 27619, 27628, 27632, 27639, 27650, 28285, 28346, 28512, 28515, 31783, 31794,
			31797, 31805, 31889, 31911, 34283, 35119, 37996, 41801, 41802, 46747, 47706, 54165, 54275);

		// framemap 121 — 1 model id
		rig(121,
			42012);

		// framemap 280 (cat) — 6 model ids
		rig(280,
			3006, 3010, 9386, 9388, 13405, 13409);

		// framemap 297 (dwarf) — 23 model ids
		rig(297,
			2970, 2972, 2973, 2974, 2977, 2978, 2979, 2980, 2981, 2983, 2984, 2985, 2986, 2990, 2992,
			2993, 7045, 7050, 7059, 7061, 7063, 7072, 10326);

		// framemap 320 — 1 model id
		rig(320,
			7744);

		// framemap 325 — 1 model id
		rig(325,
			11723);

		// framemap 326 — 1 model id
		rig(326,
			9610);

		// framemap 330 — 1 model id
		rig(330,
			11724);

		// framemap 343 — 1 model id
		rig(343,
			11725);

		// framemap 344 (swarm) — 1 model id
		rig(344,
			2950);

		// framemap 461 — 3 model ids
		rig(461,
			3756, 4942, 6239);

		// framemap 790 — 1 model id
		rig(790,
			7363);

		// framemap 1105 (dog) — 4 model ids
		rig(1105,
			18167, 18168, 18169, 18170);

		// framemap 1247 — 3 model ids
		rig(1247,
			20273, 20276, 20281);

		// framemap 1255 — 1 model id
		rig(1255,
			23905);

		// framemap 1290 — 1 model id
		rig(1290,
			21154);

		// framemap 1304 — 1 model id
		rig(1304,
			32942);

		// framemap 1310 (penguin) — 1 model id
		rig(1310,
			21552);

		// framemap 1359 — 1 model id
		rig(1359,
			22792);

		// framemap 1385 — 2 model ids
		rig(1385,
			23713, 23714);

		// framemap 1415 (goblin) — 12 model ids
		rig(1415,
			24431, 24433, 24441, 24443, 24448, 24450, 24456, 24458, 24472, 24476, 24482, 24484);

		// framemap 1490 — 3 model ids
		rig(1490,
			26177, 26181, 26188);

		// framemap 1653 — 1 model id
		rig(1653,
			39571);

		// framemap 1655 — 1 model id
		rig(1655,
			32204);

		// framemap 1944 — 1 model id
		rig(1944,
			41886);

		// framemap 2402 (gnome/child) — 10 model ids
		rig(2402,
			12735, 56095, 56138, 56139, 56164, 56179, 56184, 56187, 56217, 56218);

		// --- Shipped model ids that are not unanimous ------------------------
		//
		// Three of them, and all three are dominated so heavily by one rig that the
		// minority is plainly a shared generic part (a hand, a hair) rather than a
		// second rigging of the same body. They are recorded with both framemaps
		// anyway: the guard's job is to catch a record on the wrong skeleton, not to
		// adjudicate which of two skeletons a shared eyelash belongs to.
		// model 244: 93 on framemap 0, 1 on framemap 551
		sharedRig(244, 0, 551);
		// model 353: 597 on framemap 0, 1 on framemap 1477
		sharedRig(353, 0, 1477);
		// model 18914: 48 on framemap 0, 1 on framemap 1477
		sharedRig(18914, 0, 1477);

		// --- npcAppearanceId bodies ------------------------------------------
		//
		// A record carrying npcAppearanceId wears that NPC's whole composition, so its
		// rig is simply that NPC's own. All 7 in the dataset are framemap 0.
		// NpcID 512 — "Dark wizard", stand 808 / walk 819
		body(512, 0);
		// NpcID 526 — "Rogue", stand 808 / walk 819
		body(526, 0);
		// NpcID 1798 — "White Knight", stand 2561 / walk 2562
		body(1798, 0);
		// NpcID 3114 — "Farmer", stand 808 / walk 819
		body(3114, 0);
		// NpcID 3680 — "Sailor", stand 808 / walk 819
		body(3680, 0);
		// NpcID 4214 — "Hobbes", stand 808 / walk 819
		body(4214, 0);
		// NpcID 7987 — "Lord Marshal Brogan", stand 2256 / walk 819
		body(7987, 0);
	}

	private ModelSkeletons()
	{
	}

	private static void rig(int framemap, int... modelIds)
	{
		for (int modelId : modelIds)
		{
			put(modelId, framemap);
		}
	}

	private static void sharedRig(int modelId, int... framemaps)
	{
		for (int framemap : framemaps)
		{
			put(modelId, framemap);
		}
	}

	private static void put(int modelId, int framemap)
	{
		Set<Integer> framemaps = BY_MODEL.get(modelId);
		if (framemaps == null)
		{
			framemaps = new TreeSet<>();
			BY_MODEL.put(modelId, framemaps);
		}
		if (!framemaps.add(framemap))
		{
			throw new IllegalStateException("duplicate framemap entry for model " + modelId);
		}
	}

	private static void body(int npcId, int framemap)
	{
		if (BY_BODY.put(npcId, framemap) != null)
		{
			throw new IllegalStateException("duplicate framemap entry for NPC body " + npcId);
		}
	}

	/**
	 * @return every framemap this model id was seen rigged to, or an empty set when no
	 * NPC in the cache uses it and nothing can be inferred
	 */
	static Set<Integer> framemapsOf(int modelId)
	{
		Set<Integer> framemaps = BY_MODEL.get(modelId);
		return framemaps == null ? Collections.<Integer>emptySet()
			: Collections.unmodifiableSet(framemaps);
	}

	/**
	 * @return the framemap the NPC whose whole appearance a record clones is rigged to,
	 * or {@code null} if this table has no row for it — which
	 * {@link AnimationSkeletonTest} treats as a failure rather than as a pass
	 */
	static Integer framemapOfBody(int npcAppearanceId)
	{
		return BY_BODY.get(npcAppearanceId);
	}

	/**
	 * The rig a record's own appearance implies: the NPC body it wears if it has one,
	 * otherwise every framemap its raw model ids were seen on.
	 *
	 * <p>The precedence is not a convenience — it mirrors the runtime.
	 * {@code LivelyEntity.loadParts()} sources the parts and the recolours from the
	 * {@code npcAppearanceId} <i>instead of</i> from the record when it is set, so an
	 * NPC-dressed figure's rig is that NPC's whether or not the record also carries
	 * model ids.
	 *
	 * @return the framemaps, or an empty set when the record's appearance carries no
	 * evidence either way
	 */
	static Set<Integer> impliedRig(EntityDefinition entity)
	{
		if (entity.getNpcAppearanceId() > 0)
		{
			Integer framemap = framemapOfBody(entity.getNpcAppearanceId());
			return framemap == null ? Collections.<Integer>emptySet()
				: Collections.singleton(framemap);
		}

		Set<Integer> framemaps = new TreeSet<>();
		for (int modelId : entity.getModelIds())
		{
			framemaps.addAll(framemapsOf(modelId));
		}
		return framemaps;
	}

	static Map<Integer, Set<Integer>> all()
	{
		return Collections.unmodifiableMap(BY_MODEL);
	}

	static Map<Integer, Integer> bodies()
	{
		return Collections.unmodifiableMap(BY_BODY);
	}
}
