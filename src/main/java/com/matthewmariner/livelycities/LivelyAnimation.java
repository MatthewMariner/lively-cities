/*
 * Copyright (c) 2026, Matthew Mariner
 * All rights reserved.
 *
 * The animation name -> id table below is derived from the "Citizens" RuneLite
 * plugin (BSD 2-Clause, see NOTICE), because the region dataset this plugin
 * loads stores animations by those exact names.
 *
 * One constant is not theirs: HumanLeanReady, added here for the cameo records
 * this project authored. See its javadoc.
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
	ChildWalk(189),
	ChildIdle(195),

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
