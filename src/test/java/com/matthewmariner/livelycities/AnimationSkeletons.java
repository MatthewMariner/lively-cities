package com.matthewmariner.livelycities;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Which framemap — the game's word for a skeleton — every {@link LivelyAnimation}
 * constant is authored against.
 *
 * <h2>What a framemap is, and why it decides whether a citizen looks broken</h2>
 *
 * <p>An animation is a list of frames, and every frame names the framemap whose
 * transform groups it drives. A dwarf model is rigged to {@link #DWARF}, a goblin
 * to {@link #GOBLIN}, a player-shaped human to {@link #HUMAN}. Play a human walk on
 * a goblin and the frames address groups that model does not have: the result is
 * not "slightly off", it is a figure that slides, twists or comes apart. That is
 * exactly the defect a human found on video in ten citizens whose
 * {@code idleAnimation} and {@code moveAnimation} had been taken from different
 * creatures.
 *
 * <h2>Where these numbers come from, and why they are transcribed rather than read</h2>
 *
 * <p>Each one is the framemap id carried in the first two bytes of every frame file
 * the animation references, in the 1.12.36 game cache — checked across <i>all</i>
 * frames of each animation rather than just the first, and every animation in this
 * table came back with exactly one.
 *
 * <p>They are written out here by hand, in the same spirit as
 * {@code LivelyAnimationTest}'s copy of RuneLite's smoothing denylist: the numbers
 * belong to somebody else's file, so they are pinned where a reviewer can see them
 * rather than re-derived while the suite runs. Reading the real game cache during a
 * test would make pass/fail depend on whose machine it is — the precise failure mode
 * {@code CacheIdAudit} exists to own instead, against a live client.
 *
 * <h2>Scope</h2>
 *
 * <p>This says which skeleton an animation drives. It cannot say which skeleton a
 * {@code modelIds} array is rigged to — nothing offline can — so the guard built on
 * it compares a citizen's two animations against <i>each other</i>, which is a
 * question the dataset can answer about itself.
 */
final class AnimationSkeletons
{
	/** The cat framemap. */
	static final int CAT = 280;

	/** The dog framemap. */
	static final int DOG = 1105;

	/** The dwarf framemap. */
	static final int DWARF = 297;

	/** The goblin framemap. */
	static final int GOBLIN = 1415;

	/** The human framemap. */
	static final int HUMAN = 0;

	/** The penguin framemap. */
	static final int PENGUIN = 1310;

	/** The swarm framemap. */
	static final int SWARM = 344;

	private static final Map<LivelyAnimation, Integer> BY_ANIMATION =
		new EnumMap<>(LivelyAnimation.class);

	static
	{
		// framemap 0 — human
		put(LivelyAnimation.Flex, HUMAN); // EMOTE_FLEX
		put(LivelyAnimation.Think, HUMAN); // EMOTE_THINK
		put(LivelyAnimation.Yawn, HUMAN); // EMOTE_YAWN
		put(LivelyAnimation.SlapHead, HUMAN); // EMOTE_SLAP_HEAD
		put(LivelyAnimation.HumanIdle, HUMAN); // HUMAN_READY
		put(LivelyAnimation.HumanWalk, HUMAN); // HUMAN_WALK_F
		put(LivelyAnimation.Woodcutting, HUMAN); // HUMAN_WOODCUTTING_INFERNAL_AXE
		put(LivelyAnimation.HalfLayingDown, HUMAN); // WOUNDED_SITTING
		put(LivelyAnimation.Sitting, HUMAN); // CHAIR_SIT_READY_THRONE_4
		put(LivelyAnimation.ChurchSitting, HUMAN); // ROMEO_JULIET_PEW_READY
		put(LivelyAnimation.CurledUp, HUMAN); // MYQ3_CITIZEN_HUDDLED
		put(LivelyAnimation.HumanWithStickIdle, HUMAN); // HUMAN_STAFFREADY
		put(LivelyAnimation.HumanWithStickWalk, HUMAN); // WALK_WALKINGSTICK
		put(LivelyAnimation.HumanLeanReady, HUMAN); // HUMAN_LEAN_READY
		put(LivelyAnimation.ArmsCrossedReady, HUMAN); // RD_KNIGHT_CROSSED_ARMS
		put(LivelyAnimation.MageReady, HUMAN); // TOL_MAGE_READY01
		put(LivelyAnimation.HumanSmugIdle, HUMAN); // HUMAN_SMUG_IDLE
		put(LivelyAnimation.DrunkPlayerReady, HUMAN); // DRUNK_PLAYER_READY
		put(LivelyAnimation.VarrockTrampReady, HUMAN); // VARROCK_TRAMP_READY
		put(LivelyAnimation.NervousIdle, HUMAN); // NERVOUS_IDLE
		put(LivelyAnimation.Grabbing, HUMAN); // HUMAN_OPENDOORR
		put(LivelyAnimation.Eat, HUMAN); // HUMAN_EAT
		put(LivelyAnimation.RangeCook, HUMAN); // HUMAN_COOKING
		put(LivelyAnimation.Alching, HUMAN); // HUMAN_CASTHIGHLVLALCHEMY
		put(LivelyAnimation.FireCook, HUMAN); // HUMAN_FIRECOOKING
		put(LivelyAnimation.FurnaceSmelt, HUMAN); // HUMAN_FURNACE
		put(LivelyAnimation.HerbloreMix, HUMAN); // HUMAN_HERBING_VIAL
		put(LivelyAnimation.Fletching, HUMAN); // HUMAN_FLETCHING
		put(LivelyAnimation.AnvilBang, HUMAN); // HUMAN_SMITHING
		put(LivelyAnimation.Crying, HUMAN); // EMOTE_CRY
		put(LivelyAnimation.Mining, HUMAN); // HUMAN_FARMING
		put(LivelyAnimation.BuryOrPickingUp, HUMAN); // HUMAN_PICKUPFLOOR
		put(LivelyAnimation.LayingDown, HUMAN); // HUMAN_UNCONSCIOUS
		put(LivelyAnimation.HumanLook, HUMAN); // READY_PLAYING_CARDS
		put(LivelyAnimation.WateringCanPour, HUMAN); // FARMING_WATERING
		put(LivelyAnimation.Fishing, HUMAN); // HUMAN_FISHING_CASTING
		put(LivelyAnimation.ChildWalk, HUMAN); // EMOTE_YES_LOOP
		put(LivelyAnimation.ChildIdle, HUMAN); // EMOTE_LAUGH_LOOP
		put(LivelyAnimation.SuzieIdle, HUMAN); // HUMAN_CUTE_READY
		put(LivelyAnimation.FallenManDead, HUMAN); // DREAM_CYRISUS_UNCONSCIOUS
		put(LivelyAnimation.FallenManIdle, HUMAN); // DREAM_CYRISUS_BARELY_CONSCIOUS
		put(LivelyAnimation.StandingWithBook, HUMAN); // HUMAN_READBOOK
		put(LivelyAnimation.WalkingWithBook, HUMAN); // ANCIENT_AXE_WALK
		put(LivelyAnimation.FrontalGrab, HUMAN); // HUMAN_FIRECOOKING
		put(LivelyAnimation.ChestRub, HUMAN); // EMOTE_NO_LOOP
		put(LivelyAnimation.Swinging, HUMAN); // _100_JUBBLY_ROOT_CHOPPING

		// framemap 121
		put(LivelyAnimation.MoleIdle, 121); // MOLE_READY
		put(LivelyAnimation.MoleWalk, 121); // MOLE_WALK

		// framemap 272
		put(LivelyAnimation.CowIdle, 272); // MONKEY_COW_IDLE
		put(LivelyAnimation.CowWalk, 272); // MONKEY_COW_WALK

		// framemap 280 — cat
		put(LivelyAnimation.CatLunge, CAT); // CAT_POUNCE
		put(LivelyAnimation.CatSit, CAT); // CAT_ON_STOOL_READY
		put(LivelyAnimation.CatSleep, CAT); // CAT_SLEEP_READY
		put(LivelyAnimation.KittenSit, CAT); // TWOCATS_CAT_SITS
		put(LivelyAnimation.KittenWalk, CAT); // CAT_WALK
		put(LivelyAnimation.KittenLunge, CAT); // CAT_ATTACK
		put(LivelyAnimation.KittenDip, CAT); // CAT_BLOCK
		put(LivelyAnimation.KittenIdle, CAT); // CAT_READY
		put(LivelyAnimation.KittenSleep, CAT); // CAT_SLEEP_READY

		// framemap 289
		put(LivelyAnimation.SheepDogIdle, 289); // SITTINGDOG_READY

		// framemap 297 — dwarf
		put(LivelyAnimation.DwarfLean, DWARF); // SLICE_DWARF_2_READY
		put(LivelyAnimation.DwarfWalk, DWARF); // DWARF_WALK
		put(LivelyAnimation.DwarfMining, DWARF); // DWARF_ATTACK
		put(LivelyAnimation.DwarfMining2, DWARF); // ROYAL_DWARF_MINING
		put(LivelyAnimation.DwarfIdle, DWARF); // DWARF_READY
		put(LivelyAnimation.DwarfSmith, DWARF); // ROYAL_DWARF_MINING
		put(LivelyAnimation.DwarfSit, DWARF); // DWARF_READY_SITTING_AND_DRINKING
		put(LivelyAnimation.DwarfHandsBehindBack, DWARF); // DWARF_DIRECTOR_TABLE_READY
		put(LivelyAnimation.DrunkenDwarfIdle, DWARF); // DWARF_DRUNKREADY
		put(LivelyAnimation.DrunkenDwarfWalk, DWARF); // DWARF_DRUNKWALK

		// framemap 320
		put(LivelyAnimation.MonkeyIdle, 320); // MONKEY_READY
		put(LivelyAnimation.MonkeyWalk, 320); // MONKEY_WALK

		// framemap 325
		put(LivelyAnimation.SquirrelIdle, 325); // SQUIRREL_READY
		put(LivelyAnimation.SquirrelWalk, 325); // SQUIRREL_WALK

		// framemap 326
		put(LivelyAnimation.RatIdle, 326); // MOUSE_READY
		put(LivelyAnimation.RatBanging, 326); // MOUSE_DEFEND

		// framemap 330
		put(LivelyAnimation.RaccoonIdle, 330); // FAI_RACOON_READY
		put(LivelyAnimation.RaccoonWalk, 330); // FAI_RACOON_WALK

		// framemap 343
		put(LivelyAnimation.SwanIdle, 343); // SWAN_READY
		put(LivelyAnimation.SwanWalk, 343); // SWAN_WALK

		// framemap 344 — swarm
		put(LivelyAnimation.BeeIdle, SWARM); // SWARM_WALK

		// framemap 461
		put(LivelyAnimation.TrollIdle, 461); // TROLL_READY
		put(LivelyAnimation.TrollWalk, 461); // TROLL_WALK

		// framemap 559
		put(LivelyAnimation.CrabIdle, 559); // CRAB_READY
		put(LivelyAnimation.CrabWalk, 559); // CRAB_WALK

		// framemap 728
		put(LivelyAnimation.FireIdle, 728); // FIRE

		// framemap 790
		put(LivelyAnimation.PigletWalk, 790); // FARMING_PIG_WALK
		put(LivelyAnimation.PigletIdle, 790); // FARMING_PIG_READY

		// framemap 1105 — dog
		put(LivelyAnimation.DogIdle, DOG); // STRAYDOG_READY
		put(LivelyAnimation.DogWalk, DOG); // STRAYDOG_WALK

		// framemap 1228
		put(LivelyAnimation.MagicBoxIdle, 1228); // HUNTING_IMP_CAUGHT_IN_TRAP

		// framemap 1238
		put(LivelyAnimation.PigeonIdle, 1238); // WASHING_LINE_PIGEON

		// framemap 1247
		put(LivelyAnimation.GoatIdle, 1247); // GOAT_UPDATE_READY
		put(LivelyAnimation.GoatWalk, 1247); // SHEEP_UPDATE_WALK

		// framemap 1255
		put(LivelyAnimation.ChickenIdle, 1255); // LORE_CHICKEN_READY
		put(LivelyAnimation.ChickenWalk, 1255); // LORE_CHICKEN_WALK

		// framemap 1290
		put(LivelyAnimation.GhostIdle, 1290); // GHOST_UPDATE_TENDRILL_READY
		put(LivelyAnimation.GhostWalk, 1290); // GHOST_UPDATE_TENDRILL_WALK

		// framemap 1304
		put(LivelyAnimation.LectorIdle, 1304); // BRAIN_BROTHER_TRANQUILITY_SHIFTY_READY
		put(LivelyAnimation.LectorWalk, 1304); // BRAIN_HUMAN_BRAIN_MONK_WALK

		// framemap 1310 — penguin
		put(LivelyAnimation.PenguinIdle, PENGUIN); // PENG_GENTOO_READY
		put(LivelyAnimation.PenguinWalk, PENGUIN); // PENG_GENTOO_WALK

		// framemap 1359
		put(LivelyAnimation.PuffinIdle, 1359); // BRAIN_PUFFIN_READY
		put(LivelyAnimation.PuffinWalk, 1359); // BRAIN_PUFFIN_WALK

		// framemap 1385
		put(LivelyAnimation.GoblinFishIdle, 1385); // DORGESH_GOBLIN_FISH_WALK
		put(LivelyAnimation.GoblinFishWalk, 1385); // DORGESH_GOBLIN_FISH_READY

		// framemap 1415 — goblin
		put(LivelyAnimation.GoblinPull, GOBLIN); // _100_GOB_SIT
		put(LivelyAnimation.GoblinChill, GOBLIN); // QIP_OBSERVATORY_HUCK_FIN_GOBLIN_READY_TIMER
		put(LivelyAnimation.GoblinIdle, GOBLIN); // SLICE_SURFACE_GOBLIN_SQUAT_GENERALS_READY
		put(LivelyAnimation.GoblinIdl2, GOBLIN); // QIP_OBSERVATORY_GOBLIN_CHATTER_TWO
		put(LivelyAnimation.GoblinIdle3, GOBLIN); // QIP_OBSERVATORY_GOBLIN_CHATTER_ONE
		put(LivelyAnimation.GoblinWalk, GOBLIN); // SLICE_SURFACE_GOBLIN_SQUAT_GENERALS_WALK
		put(LivelyAnimation.GoblinExcitedWalk, GOBLIN); // SLICE_SURFACE_GOBLIN_RUNNING_SCARED

		// framemap 1490
		put(LivelyAnimation.WerewolfIdle, 1490); // WEREWOLF_UPDATE_READY
		put(LivelyAnimation.WerewolfWalk, 1490); // WEREWOLF_UPDATE_WALK

		// framemap 1493
		put(LivelyAnimation.WheatFieldIdle, 1493); // II_MAGIC_WHEAT_M

		// framemap 1534
		put(LivelyAnimation.CrowIdle, 1534); // CROW_UPDATE_FLY
		put(LivelyAnimation.CrowWalk, 1534); // CROW_UPDATE_FLY

		// framemap 1653
		put(LivelyAnimation.TanglerootIdle, 1653); // TANGLEROOT_IDLE
		put(LivelyAnimation.TanglerootWalk, 1653); // TANGLEROOT_WALK

		// framemap 1655
		put(LivelyAnimation.RiftGuardianIdle, 1655); // RIFT_GUARDIAN_IDLE
		put(LivelyAnimation.RiftGuardianWalk, 1655); // RIFT_GUARDIAN_WALK
		put(LivelyAnimation.RiftGuardianSit, 1655); // RIFT_GUARDIAN_SIT

		// framemap 2402
		put(LivelyAnimation.ChildStarJump, 2402); // GNOME_CL8
		put(LivelyAnimation.ChildPlay1, 2402); // FAI_VARROCK_CHILD_READY
		put(LivelyAnimation.ChildPlay2, 2402); // FAI_VARROCK_CHILD_READY_OFFSET
		put(LivelyAnimation.GnomeChildReady, 2402); // GNOME_READY
		put(LivelyAnimation.GnomeChildWalk, 2402); // GNOME_WALK
	}

	private AnimationSkeletons()
	{
	}

	private static void put(LivelyAnimation animation, int framemap)
	{
		Integer previous = BY_ANIMATION.put(animation, framemap);
		if (previous != null)
		{
			throw new IllegalStateException("duplicate framemap entry for " + animation);
		}
	}

	/**
	 * @return the framemap {@code animation} drives, or {@code null} if this table has
	 * no entry for it — which {@link AnimationSkeletonTest} treats as a failure rather
	 * than as a pass, so a new enum constant cannot slip through unclassified
	 */
	static Integer framemapOf(LivelyAnimation animation)
	{
		return BY_ANIMATION.get(animation);
	}

	static Map<LivelyAnimation, Integer> all()
	{
		return Collections.unmodifiableMap(BY_ANIMATION);
	}
}
