package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LivelyAnimationTest
{
	@Test
	public void resolvesNamesToTheirGameIds()
	{
		// Deliberately spread across the table rather than three neighbours, so
		// a truncated or reordered enum shows up.
		assertEquals(1248, LivelyAnimation.fromName("Fletching").getId());
		assertEquals(808, LivelyAnimation.fromName("HumanIdle").getId());
		assertEquals(10060, LivelyAnimation.fromName("SuzieIdle").getId());
		assertEquals(99, LivelyAnimation.fromName("DwarfMining").getId());
		assertEquals(6539, LivelyAnimation.fromName("WerewolfIdle").getId());
	}

	/**
	 * The one constant this project added rather than inheriting.
	 *
	 * <p>{@code HumanLeanReady} is 916, which
	 * {@code javap -p -constants net.runelite.api.gameval.AnimationID} on the 1.12.36
	 * jar names {@code HUMAN_LEAN_READY} — the pose rather than
	 * {@code HUMAN_LEAN = 915}, which is the transition into it. Pinned here because
	 * it is the only entry in the table with no upstream provenance (see
	 * {@code NOTICE}) and because a {@code _READY} id is the difference between a
	 * loopable idle and an animation that plays once and stops.
	 */
	@Test
	public void theOneLocallyAddedAnimationIsTheHumanLeanReadyPose()
	{
		assertNotNull(LivelyAnimation.fromName("HumanLeanReady"));
		assertEquals("gameval AnimationID.HUMAN_LEAN_READY",
			916, LivelyAnimation.fromName("HumanLeanReady").getId());
		assertNull("HUMAN_LEAN (915) is the transition, and is deliberately not in the table",
			byId(915));
	}

	private static LivelyAnimation byId(int id)
	{
		for (LivelyAnimation animation : LivelyAnimation.values())
		{
			if (animation.getId() == id)
			{
				return animation;
			}
		}
		return null;
	}

	@Test
	public void unknownAndEmptyNamesResolveToNullInsteadOfThrowing()
	{
		assertNull(LivelyAnimation.fromName(null));
		assertNull(LivelyAnimation.fromName(""));
		assertNull(LivelyAnimation.fromName("   "));
		assertNull(LivelyAnimation.fromName("PolishingTheBrasswork"));
		// Case matters: the dataset uses the exact constant names.
		assertNull(LivelyAnimation.fromName("fletching"));
	}

	@Test
	public void tolerateSurroundingWhitespace()
	{
		assertEquals(LivelyAnimation.Fletching, LivelyAnimation.fromName("  Fletching  "));
	}

	/**
	 * The load-bearing one. The dataset stores animations by name, so a name it
	 * uses that the enum does not carry means a citizen renders frozen — and
	 * nothing else in the build would notice.
	 */
	@Test
	public void everyAnimationNameInTheShippedDatasetResolves()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());

		List<String> missing = new ArrayList<>();
		TreeSet<String> seen = new TreeSet<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);

			for (EntityDefinition entity : region.getEntities())
			{
				// A definition only carries a resolved animation, so an
				// unresolved name would have shown up as null here. Cross-check
				// against the raw name via the enum directly.
				if (entity.getIdleAnimation() != null)
				{
					seen.add(entity.getIdleAnimation().name());
				}
				if (entity.getMoveAnimation() != null)
				{
					seen.add(entity.getMoveAnimation().name());
				}
			}
		}

		for (String name : ShippedAnimationNames.all())
		{
			if (LivelyAnimation.fromName(name) == null)
			{
				missing.add(name);
			}
		}

		assertTrue("animation names used by the dataset but absent from the enum: " + missing,
			missing.isEmpty());

		// Sanity: 63 distinct idleAnimation names and 28 distinct moveAnimation
		// names across the 45 files, overlapping on HumanIdle and BeeIdle ->
		// 89 distinct. A drop here means definitions stopped resolving names.
		//
		// It was 81 before the six cameos, which brought in Alching, Flex and
		// HumanLeanReady as idle poses; the other three (Fishing, Think, HumanIdle —
		// and LectorIdle) were already in use elsewhere in the dataset. It went 84 ->
		// 89 when the video pass corrected fourteen wrong gaits and re-posed seven
		// figures: the moves gained GoblinWalk, PenguinWalk and KittenWalk (+3, and
		// none of HumanWalk, DogWalk or DrunkenDwarfWalk left the dataset, they are
		// each still right for somebody); the idles gained DrunkPlayerReady,
		// VarrockTrampReady, NervousIdle, SlapHead, MageReady, ArmsCrossedReady and
		// HumanSmugIdle (+7) and lost HalfLayingDown, CurledUp, Crying, Alching and
		// Flex, which nothing else used (-5). FallenManIdle stayed: the scenery record
		// beside Damien still uses it.
		//
		// It stayed at 89 through the skeleton pass, which is arithmetic rather than
		// luck: the idles lost ChildIdle and gained GnomeChildReady (0), the moves lost
		// ChildWalk and gained GnomeChildWalk (0), and Sludgellama moving from LectorIdle
		// to NervousIdle moved neither — "Lector Argus" in 12852 still uses the first
		// and "Damien" in 12342 already used the second. Dropping HumanIdle off one
		// scenery record left 58 other users of it.
		assertEquals("distinct animation names in the dataset", 89, ShippedAnimationNames.all().size());
		assertEquals("distinct animations resolved onto definitions", 89, seen.size());
	}

	/**
	 * The 23 animation ids RuneLite's core <b>Animation Smoothing</b> plugin
	 * refuses to interpolate, copied out of the 1.12.36 bytecode.
	 *
	 * <p>{@code AnimationSmoothingPlugin.isAnimationInterpolatable(int)} is a
	 * {@code lookupswitch} over exactly these, returning {@code false} for them and
	 * {@code true} for everything else — i.e. a <b>denylist</b>. That matters
	 * because it is the opposite of a whitelist, and the difference decides whether
	 * this plugin's citizens can be smoothed at all.
	 */
	private static final int[] SMOOTHING_DENYLIST = {
		244, 367, 1051, 3558, 4519, 4652, 5530, 5531, 5583, 5857, 6495, 6566,
		6818, 7898, 8266, 8267, 8270, 8271, 8499, 8977, 9450, 9493, 11291,
	};

	/**
	 * Whether the citizens can be smoothed at all — and the answer is yes, subject
	 * to the user having the plugin on.
	 *
	 * <p>The mechanism, disassembled from 1.12.36 rather than assumed:
	 * {@code AnimationController.getPackedFrame()} asks
	 * {@code client.getAnimationInterpolationFilter()}, and only packs the
	 * interpolation bits when that filter is non-null <i>and</i> returns true for
	 * the animation's id. The only thing in RuneLite that installs such a filter is
	 * the core Animation Smoothing plugin, which is {@code enabledByDefault =
	 * false}; the filter it installs is the denylist above. So:
	 *
	 * <ul>
	 *   <li>Animation Smoothing off — the filter is null, and nothing at all is
	 *       interpolated, ours included. Not something this plugin can change, and
	 *       not something it should: it is the same for every real NPC.</li>
	 *   <li>Animation Smoothing on — every id is interpolated unless it is on the
	 *       denylist, and this test is what says none of ours is.</li>
	 * </ul>
	 *
	 * <p>It is pinned as a test rather than written in a comment because the
	 * denylist is somebody else's file: an id added to it upstream that collides
	 * with one of ours would silently stop that citizen being smoothed, and this is
	 * the only place that would notice.
	 */
	@Test
	public void noShippedAnimationIsOnTheSmoothingDenylist()
	{
		List<String> clashes = new ArrayList<>();

		for (LivelyAnimation animation : LivelyAnimation.values())
		{
			for (int denied : SMOOTHING_DENYLIST)
			{
				if (animation.getId() == denied)
				{
					clashes.add(animation.name() + " (id " + denied + ")");
				}
			}
		}

		assertTrue("animation(s) RuneLite's Animation Smoothing plugin will not interpolate: "
			+ clashes, clashes.isEmpty());

		// The denylist itself, so a copy that lost entries cannot make this pass
		// by being empty.
		assertEquals("the 1.12.36 denylist has 23 entries", 23, SMOOTHING_DENYLIST.length);
	}
}
