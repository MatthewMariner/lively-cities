/*
 * Copyright (c) 2026, Matthew Mariner
 * All rights reserved.
 *
 * The animation name -> id table below is derived from the "Citizens" RuneLite
 * plugin (BSD 2-Clause, see NOTICE), because the region dataset this plugin
 * loads stores animations by those exact names.
 *
 * Nine constants are not theirs: HumanLeanReady, ArmsCrossedReady, MageReady,
 * HumanSmugIdle, DrunkPlayerReady, VarrockTrampReady, NervousIdle,
 * GnomeChildReady and GnomeChildWalk, added here for records this project
 * authored, re-posed or moved onto the skeleton their models are rigged to. See
 * their javadoc.
 */
package com.matthewmariner.livelycities;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Symbolic animation names as they appear in {@code RegionData/*.json}.
 *
 * <p>The dataset stores {@code "idleAnimation": "Fletching"} — a name, not a
 * number — so the mapping has to live in code. {@link #fromName(String)} is
 * deliberately fail-soft: an unknown name yields {@code null} rather than an
 * exception, so a record with a typo still spawns, just without animation.
 */
public enum LivelyAnimation
{
	// Poses / emotes
	Flex(8917),
	Think(857),
	Yawn(2111),
	SlapHead(4275),
	HumanIdle(808),
	HumanWalk(819),
	Woodcutting(2117),
	HalfLayingDown(1147),
	Sitting(4114),
	ChurchSitting(3281),
	CurledUp(4712),

	HumanWithStickIdle(813),
	HumanWithStickWalk(1146),

	/**
	 * A human leaning, as a loopable ready pose.
	 *
	 * <p><b>Not from the predecessor plugin's table</b> — the only addition to it,
	 * and it is here because that table has no human lean and 1.12.36 does:
	 * {@code javap -p -constants net.runelite.api.gameval.AnimationID} declares
	 * {@code HUMAN_LEAN = 915} and {@code HUMAN_LEAN_READY = 916}. The {@code _READY}
	 * one is the pose rather than the transition into it, which is what makes it safe
	 * to loop — every other idle in this table that a human plays is a {@code _READY}
	 * too ({@code HumanIdle} is {@code HUMAN_READY = 808}). Used by the MrCream cameo,
	 * whose brief was "leaning casually".
	 */
	HumanLeanReady(916),

	// --- Poses added by this project ---------------------------------------
	//
	// None of the six below is from the predecessor plugin's table. Each is a
	// standing hold on the human framemap (0), and each is here because the
	// table had nothing that said what the record needed to say. The rule they
	// exist to satisfy is stated on ArmsCrossedReady.

	/**
	 * A human standing with arms folded.
	 *
	 * <p>{@code AnimationID.RD_KNIGHT_CROSSED_ARMS = 2256}, 12 frames, human framemap.
	 *
	 * <p><b>The rule this constant establishes.</b> A figure that only ever stands
	 * there gets a <i>pose</i>, never an <i>action</i>. An action animation assumes the
	 * item it was authored around — {@code Alching} assumes a staff, {@code Fishing}
	 * assumes a rod — and a figure with no such model in its composition plays it as a
	 * mime: bent at the waist, hands working at nothing. The test for "is this a pose"
	 * is one of two things, and both are checkable: the gameval name ends in
	 * {@code _READY}/{@code _IDLE}, or the game itself installs the id as some NPC's
	 * standing animation. This one passes the second test five times over — it is the
	 * {@code stand} of {@code NpcID.CORSCURS_LORD_MARSHAL} (7987, "Lord Marshal
	 * Brogan"), {@code Captain Kalt} (7377), {@code Lady Table} (4680), {@code Miss
	 * Cheevers} (4685) and {@code Savant} (4931). Used by the Peter cameo.
	 */
	ArmsCrossedReady(2256),

	/**
	 * A robed caster's standing ready pose.
	 *
	 * <p>{@code AnimationID.TOL_MAGE_READY01 = 5823}, 45 frames, human framemap, and the
	 * {@code stand} of {@code 'Transmute' The Alchemist} (3592) and {@code 'Currency'
	 * The Alchemist} (3594) — two robed human NPCs that hold no staff. Used by the Cazh
	 * cameo in place of {@link #Alching}, which is a cast and needs one.
	 */
	MageReady(5823),

	/**
	 * A human standing pleased with himself.
	 *
	 * <p>{@code AnimationID.HUMAN_SMUG_IDLE = 14000}, 12 frames, human framemap. An
	 * {@code _IDLE} by its own name and self-contained — no held item anywhere in it.
	 * Used by the Gunnar cameo in place of {@link #Fishing}, which needs a rod.
	 */
	HumanSmugIdle(14000),

	/**
	 * A drunk human standing and swaying.
	 *
	 * <p>{@code AnimationID.DRUNK_PLAYER_READY = 2770}, 12 frames, human framemap, and
	 * the {@code stand} of {@code NpcID.FALADOR_MAN1} (3263, "Drunken man"). Used by the
	 * "Drunken peasant" in Varrock, who was authored lying in the road on
	 * {@link #HalfLayingDown} and now stays on his feet.
	 */
	DrunkPlayerReady(2770),

	/**
	 * A tramp standing, hunched but upright.
	 *
	 * <p>{@code AnimationID.VARROCK_TRAMP_READY = 6480}, 8 frames, human framemap — the
	 * {@code _READY} half of the pair whose other half is {@code VARROCK_TRAMP_WALK =
	 * 6479}, i.e. the game's own idea of how a Varrock tramp stands. Used by "Joe the
	 * tramp", who was authored curled up on the ground.
	 */
	VarrockTrampReady(6480),

	/**
	 * A human standing on edge, glancing about.
	 *
	 * <p>{@code AnimationID.NERVOUS_IDLE = 10680}, 12 frames, human framemap, and the
	 * {@code stand} of {@code Bandit} (12667). Used by "Damien" outside Edgeville, who
	 * was authored face-down on {@link #FallenManIdle}.
	 */
	NervousIdle(10680),

	// Actions
	Grabbing(551),
	Eat(829),
	RangeCook(896),
	Alching(713),
	FireCook(897),
	FurnaceSmelt(899),
	HerbloreMix(363),
	Fletching(1248),
	AnvilBang(898),
	Crying(860),
	Mining(1728),
	BuryOrPickingUp(827),
	LayingDown(838),
	HumanLook(2713),
	WateringCanPour(2293),
	Fishing(622),

	ChildStarJump(218),
	ChildPlay1(6484),
	ChildPlay2(6485),

	/**
	 * A human emote, despite the name.
	 *
	 * <p>{@code AnimationID.EMOTE_YES_LOOP = 189}, framemap 0. The predecessor's name for
	 * it says "child" and its framemap says "player", and those disagree: every child
	 * model in this dataset is rigged to framemap 2402, the small-humanoid rig the game
	 * calls {@code GNOME_*}. Nothing uses this constant any more — see
	 * {@link #GnomeChildWalk}, which is what the two records that did now carry. It is
	 * kept because the name is part of the vendored data format, not an implementation
	 * choice, and a future record on a genuinely player-shaped body may want it.
	 */
	ChildWalk(189),

	/**
	 * The other half of the same mistake.
	 *
	 * <p>{@code AnimationID.EMOTE_LAUGH_LOOP = 195}, framemap 0. See {@link #ChildWalk}.
	 */
	ChildIdle(195),

	// --- The child rig, framemap 2402 ---------------------------------------
	//
	// Not from the predecessor plugin's table. Their "ChildIdle"/"ChildWalk" name two
	// human emotes on framemap 0, and every child model in the dataset is rigged to
	// 2402 instead — the rig the cache's gameval names call GNOME_*, shared by gnomes,
	// children and the game's other small humanoids. The two below are that rig's own
	// stand and walk, so a child-modelled record can be given the pair the game itself
	// gives every NPC built out of those models.

	/**
	 * The standing pose of every small-humanoid body in the game.
	 *
	 * <p>{@code AnimationID.GNOME_READY = 2331}, framemap 2402. It is the {@code stand}
	 * of {@code Street urchin} (3539), {@code Henja} (3306) and {@code Boy} (3994) —
	 * the three NPCs whose model lists the "Child" and "Liam" records copy exactly — and
	 * of some two thousand other NPCs on the same rig. Used by both of those records in
	 * place of {@link #ChildIdle}.
	 */
	GnomeChildReady(2331),

	/**
	 * The matching walk.
	 *
	 * <p>{@code AnimationID.GNOME_WALK = 12042}, framemap 2402, and the {@code walk} of
	 * the same three NPCs. Used by "Child" in the Grand Exchange, who wanders, in place
	 * of {@link #ChildWalk}.
	 */
	GnomeChildWalk(12042),

	SuzieIdle(10060),
	LectorIdle(5875),
	LectorWalk(5876),

	FallenManDead(6280),
	FallenManIdle(6282),

	// Non-human
	FireIdle(475),
	CatLunge(319),
	CatSit(2134),
	CatSleep(2159),
	RatIdle(2704),
	RatBanging(2706),
	BeeIdle(0),
	PuffinIdle(5873),
	PuffinWalk(5872),

	RiftGuardianIdle(7307),
	RiftGuardianWalk(7306),
	RiftGuardianSit(9397),

	CowIdle(180),
	CowWalk(229),

	TanglerootIdle(7312),
	TanglerootWalk(7313),

	TrollIdle(286),
	TrollWalk(283),

	DwarfLean(6206),
	DwarfWalk(98),
	DwarfMining(99),
	DwarfMining2(4021),
	DwarfIdle(101),
	DwarfSmith(4021),
	DwarfSit(2337),
	DwarfHandsBehindBack(2151),
	DrunkenDwarfIdle(900),
	DrunkenDwarfWalk(104),

	ChickenIdle(5386),
	ChickenWalk(5385),

	GoblinPull(3387),
	GoblinChill(6837),
	GoblinIdle(6203),
	GoblinIdl2(6835),
	GoblinIdle3(6834),
	GoblinWalk(6202),
	GoblinExcitedWalk(6193),

	PigeonIdle(4133),

	MagicBoxIdle(5221),
	StandingWithBook(1350),
	WalkingWithBook(10170),

	KittenSit(2694),

	/**
	 * The cat walk — despite the name, which is the predecessor's and is part of the
	 * data format.
	 *
	 * <p>{@code AnimationID.CAT_WALK = 314}, and the whole "Kitten" block below it is
	 * the same: {@code KittenIdle} is {@code CAT_READY}, {@code KittenLunge} is
	 * {@code CAT_ATTACK}. There is one cat rig and one set of cat animations; a kitten
	 * is a smaller model on it. So this is the correct move animation for a full-grown
	 * cat too, which is why "Nightfire" — {@code Hellcat}, models 13409 and 13405, whose
	 * own NPC definition pairs {@code stand = CAT_READY} with {@code walk = CAT_WALK} —
	 * uses it rather than being made stationary for want of a "CatWalk" name.
	 */
	KittenWalk(314),
	KittenLunge(315),
	KittenDip(316),
	KittenIdle(317),
	KittenSleep(2159),

	SheepDogIdle(2268),

	DogIdle(4777),
	DogWalk(4773),

	SquirrelIdle(3211),
	SquirrelWalk(3210),

	SwanIdle(3242),
	SwanWalk(3241),

	PigletWalk(2165),
	PigletIdle(2166),

	CrabIdle(3424),
	CrabWalk(3426),

	GoatIdle(5339),
	GoatWalk(5334),

	RaccoonIdle(3213),
	RaccoonWalk(3214),

	CrowIdle(6784),
	CrowWalk(6784),

	GoblinFishIdle(6061),
	GoblinFishWalk(6062),

	MonkeyIdle(222),
	MonkeyWalk(219),

	GhostIdle(5538),
	GhostWalk(5539),

	PenguinIdle(5668),
	PenguinWalk(5666),

	WerewolfIdle(6539),
	WerewolfWalk(6541),

	MoleIdle(3309),
	MoleWalk(3313),

	WheatFieldIdle(6627),

	FrontalGrab(897),
	ChestRub(190),
	Swinging(3475);

	private static final Map<String, LivelyAnimation> BY_NAME = new HashMap<>();

	static
	{
		for (LivelyAnimation a : values())
		{
			BY_NAME.put(a.name(), a);
		}
	}

	private final int id;

	LivelyAnimation(int id)
	{
		this.id = id;
	}

	/**
	 * Resolves a dataset animation name to an enum constant.
	 *
	 * @param name the name as stored in the region JSON, may be null
	 * @return the matching constant, or {@code null} if the name is missing,
	 * blank or unknown. Never throws.
	 */
	@Nullable
	public static LivelyAnimation fromName(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}

		String trimmed = name.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}

		return BY_NAME.get(trimmed);
	}

	public int getId()
	{
		return id;
	}
}
