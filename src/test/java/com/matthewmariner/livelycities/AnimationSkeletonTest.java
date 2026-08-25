package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A citizen may not walk on a different skeleton from the one it stands on.
 *
 * <h2>The defect this exists for</h2>
 *
 * <p>A human playing the plugin and recording video found ten citizens whose
 * {@code idleAnimation} and {@code moveAnimation} had been taken from different
 * creatures: goblins that stood like goblins and walked like men, a penguin with a
 * human gait, five dwarves the same, a bee swarm on {@code HumanWalk}, a cat on
 * {@code HumanWalk} — and, in the other direction, a man at a furnace who walked
 * like a stray dog. An offline sweep against the 1.12.36 cache found three more
 * nobody had reported: another man on {@code DogWalk} and two women on
 * {@code DrunkenDwarfWalk}.
 *
 * <p>Thirteen of the fourteen never move — only "Bees!" in Varrock carries a wander
 * box — so in all but one case the wrong gait was latent rather than visible. That is
 * precisely why it needs a test and not just a fix: the
 * dataset's whole claim on anyone's attention is that it is trustworthy, and a
 * latent wrong is only ever one wander box away from a visible one.
 *
 * <h2>Two rules, and why the second one had to be added</h2>
 *
 * <p>The first rule here is the one the dataset can ask about itself: the two
 * animations on one record must drive the same skeleton as each other. Every one of
 * the fourteen fails that, and no correct record does — a figure with one body cannot
 * have two rigs.
 *
 * <p>It has a blind spot exactly one record wide, and it is not hypothetical.
 * {@code if (idle == null || move == null) continue;} skips every figure carrying only
 * an idle — which is all six cameos, deliberately, because they are posed statues. So
 * the guard built to catch wrong-skeleton animations was skipping precisely the figures
 * whose poses had been hand-picked, and three records were on the wrong rig behind it:
 * "Sludgellama" posed on the Braindeath Island skeleton while wearing a Rogue's body,
 * and "Child" and "Liam" playing human emotes on gnome-rigged child models.
 *
 * <p>The second rule closes it by asking the question a reviewer actually wants
 * answered — "does this animation belong to this body?" — which needs one fact per
 * appearance rather than per record. {@link ModelSkeletons} carries them: which
 * framemap each shipped model id and each {@code npcAppearanceId} was seen rigged to.
 * That is an inference off NPC compositions rather than something readable out of a
 * model file, and its limits are written down there.
 *
 * <p>The skeleton numbers on both sides come from tables transcribed from the cache
 * rather than read at test time; see {@link AnimationSkeletons} for why.
 */
public class AnimationSkeletonTest
{
	/**
	 * Citizens carrying <b>both</b> an idle and a move animation, i.e. the population
	 * this rule can say anything about.
	 *
	 * <p>Pinned, and load-bearing. The comparison below skips a record with only one
	 * animation, so a bug that made {@link EntityDefinition} drop move animations
	 * entirely would empty the sample and turn every assertion green. This is the
	 * number that stops that: 99 of the 135 shipped citizens have both.
	 */
	private static final int CITIZENS_WITH_BOTH_ANIMATIONS = 99;

	/**
	 * The one that would have caught all fourteen.
	 */
	@Test
	public void noCitizenWalksOnADifferentSkeletonFromTheOneItStandsOn()
	{
		List<String> violations = new ArrayList<>();
		int checked = 0;

		for (EntityDefinition citizen : shippedCitizens())
		{
			LivelyAnimation idle = citizen.getIdleAnimation();
			LivelyAnimation move = citizen.getMoveAnimation();
			if (idle == null || move == null)
			{
				continue;
			}

			checked++;

			Integer idleSkeleton = AnimationSkeletons.framemapOf(idle);
			Integer moveSkeleton = AnimationSkeletons.framemapOf(move);
			assertNotNull(idle + " has no framemap in the table", idleSkeleton);
			assertNotNull(move + " has no framemap in the table", moveSkeleton);

			if (!idleSkeleton.equals(moveSkeleton))
			{
				violations.add(citizen.label() + " in " + citizen.getRegionId() + ".json stands on "
					+ idle + " (framemap " + idleSkeleton + ") and walks on "
					+ move + " (framemap " + moveSkeleton + ")");
			}
		}

		assertEquals("citizens carrying both an idle and a move animation — if this drops, the "
				+ "sample shrank and the assertion below stopped being asked",
			CITIZENS_WITH_BOTH_ANIMATIONS, checked);

		assertTrue("citizen(s) whose two animations come from different creatures: " + violations,
			violations.isEmpty());
	}

	/**
	 * Shipped records whose appearance the cache can put a rig on, i.e. the population
	 * the rule below can say anything about.
	 *
	 * <p>Pinned for the same reason {@link #CITIZENS_WITH_BOTH_ANIMATIONS} is: the loop
	 * skips a record whose models nothing in the cache is built out of, so a bug that
	 * emptied {@link ModelSkeletons} would turn every assertion green.
	 *
	 * <p>The arithmetic: 181 shipped records, 14 of them carrying no animation at all,
	 * leaves 167 this rule could apply to. 138 of those have a body the cache can put a
	 * rig on and 29 do not — see {@link #RECORDS_WITH_NO_RIG_EVIDENCE}.
	 */
	private static final int RECORDS_WITH_A_TRACEABLE_RIG = 138;

	/**
	 * The other side of that split, pinned so it cannot quietly grow.
	 *
	 * <p>These are records whose {@code modelIds} appear in no NPC composition anywhere
	 * in the cache — braziers, market stalls, planters, a wheat field. They are object
	 * geometry, nothing rigs them, and the rule below has nothing to say about them. It
	 * is a counted, named outcome rather than a silent {@code continue}: a citizen
	 * landing in this bucket would mean somebody had authored a person out of scenery
	 * models, and that should be visible.
	 */
	private static final int RECORDS_WITH_NO_RIG_EVIDENCE = 29;

	/**
	 * An animation has to drive the skeleton the record's own body is rigged to.
	 *
	 * <p>The rule that reaches the six cameos. Each of them carries an
	 * {@code npcAppearanceId} and no move animation, so the pair rule above skips all
	 * six; this one compares the single pose against the framemap of the NPC body it is
	 * worn on, and it is the check that would have caught {@code LectorIdle} (framemap
	 * 1304) posed on a Rogue (framemap 0) on the day it was written.
	 *
	 * <p>Scenery is in scope, not just citizens. A scenery record can carry an
	 * {@code idleAnimation} and {@link LivelyEntity} installs a controller for it exactly
	 * as it does for a citizen — and one did: an "Inactive spirit pool" model, rigged to
	 * framemap 1944, playing {@code HUMAN_READY}.
	 */
	@Test
	public void noRecordPlaysAnAnimationOnASkeletonItsOwnBodyIsNotRiggedTo()
	{
		List<String> violations = new ArrayList<>();
		List<String> noEvidence = new ArrayList<>();
		int checked = 0;

		for (EntityDefinition entity : shippedEntities())
		{
			List<LivelyAnimation> animations = new ArrayList<>();
			if (entity.getIdleAnimation() != null)
			{
				animations.add(entity.getIdleAnimation());
			}
			if (entity.getMoveAnimation() != null)
			{
				animations.add(entity.getMoveAnimation());
			}

			if (animations.isEmpty())
			{
				continue;
			}

			Set<Integer> rig = ModelSkeletons.impliedRig(entity);
			if (rig.isEmpty())
			{
				noEvidence.add(entity.label() + " in " + entity.getRegionId() + ".json");
				continue;
			}

			checked++;

			for (LivelyAnimation animation : animations)
			{
				Integer framemap = AnimationSkeletons.framemapOf(animation);
				assertNotNull(animation + " has no framemap in the table", framemap);

				if (!rig.contains(framemap))
				{
					violations.add(entity.label() + " in " + entity.getRegionId() + ".json plays "
						+ animation + " (framemap " + framemap + ") on a body rigged to " + rig);
				}
			}
		}

		// The rule first, then the two guards on the sample. That order is deliberate:
		// a record joining the sample and being wrong should fail with what is wrong
		// with it, not with a count that moved.
		assertTrue("record(s) whose animation drives a skeleton their own body does not have: "
			+ violations, violations.isEmpty());

		assertEquals("records carrying an animation and a body the cache can put a rig on — "
				+ "if this drops, the sample shrank and the rule above stopped being asked",
			RECORDS_WITH_A_TRACEABLE_RIG, checked);
		assertEquals("records animated on models no NPC is built out of, so nothing can be "
				+ "inferred: " + noEvidence, RECORDS_WITH_NO_RIG_EVIDENCE, noEvidence.size());
	}

	/**
	 * The body table has to actually distinguish the bodies, or the rule above is
	 * satisfied by a table of zeroes.
	 *
	 * <p>The mirror of {@link #theFramemapTableTellsTheCreaturesApart}, and it is not
	 * hypothetical for the same reason: 255 of the 340 covered model ids really are on
	 * framemap 0, so a table that rounded the rest off to human would look almost right
	 * and pass every record in the dataset. The counts and the four spot-checks below
	 * are what stops that.
	 */
	@Test
	public void theModelSkeletonTableTellsTheBodiesApart()
	{
		Set<Integer> framemaps = new TreeSet<>();
		for (Set<Integer> rigs : ModelSkeletons.all().values())
		{
			framemaps.addAll(rigs);
		}

		assertEquals("distinct framemaps across the model table", 28, framemaps.size());
		assertEquals("shipped model ids with a rig recorded", 340, ModelSkeletons.all().size());
		assertEquals("shipped model ids no NPC is built out of, so no rig can be inferred",
			36, ModelSkeletons.NO_NPC_EVIDENCE.size());
		assertEquals("npcAppearanceId bodies", 7, ModelSkeletons.bodies().size());

		// One per record that was actually found wrong by this table, so a flattening
		// fails with the name of the thing it would have stopped catching.
		assertEquals("the Rogue body Sludgellama wears",
			(Integer) AnimationSkeletons.HUMAN, ModelSkeletons.framemapOfBody(526));
		assertEquals("model 12735 is a Street urchin part, on the small-humanoid rig",
			Collections.singleton(2402), ModelSkeletons.framemapsOf(12735));
		assertEquals("model 56138 is a Henja/Boy part, on the same one",
			Collections.singleton(2402), ModelSkeletons.framemapsOf(56138));
		assertEquals("model 41886 is the Inactive spirit pool, which is on neither",
			Collections.singleton(1944), ModelSkeletons.framemapsOf(41886));
		assertEquals("and a brazier is on nothing at all — it is object geometry",
			Collections.<Integer>emptySet(), ModelSkeletons.framemapsOf(2260));
	}

	/**
	 * Every animation in the enum is classified.
	 *
	 * <p>Both directions, because both failures are silent. An enum constant with no
	 * table entry would make the comparison above throw rather than skip — which is
	 * why {@link AnimationSkeletons#framemapOf} returns null instead of a default — but
	 * only if some record happens to use it, and most of the table is unused by the
	 * shipped data. A table entry with no enum constant behind it would not compile,
	 * so the interesting direction is the first one: adding an animation without
	 * looking up its framemap must fail here, immediately, rather than years later when
	 * somebody first pairs it with a walk.
	 */
	@Test
	public void everyAnimationConstantHasAFramemap()
	{
		Set<LivelyAnimation> missing = EnumSet.noneOf(LivelyAnimation.class);
		for (LivelyAnimation animation : LivelyAnimation.values())
		{
			if (AnimationSkeletons.framemapOf(animation) == null)
			{
				missing.add(animation);
			}
		}

		assertTrue("animation(s) with no framemap recorded — look the id up in the cache and add "
			+ "a row to AnimationSkeletons: " + missing, missing.isEmpty());

		assertEquals("the table has to cover the whole enum and nothing else",
			LivelyAnimation.values().length, AnimationSkeletons.all().size());
	}

	/**
	 * The table has to actually distinguish the creatures, or the rule above is
	 * satisfied by a table of zeroes.
	 *
	 * <p>This is the fake-test guard, and it is not hypothetical: a table that mapped
	 * every animation to {@link AnimationSkeletons#HUMAN} would pass the comparison for
	 * all 99 records including the fourteen broken ones, because every pair would agree
	 * on 0. So the six skeletons the fourteen fixes actually turned on are named here
	 * individually, asserted distinct from the human one and from each other, and the
	 * distinct-framemap count across the whole table is pinned on top.
	 */
	@Test
	public void theFramemapTableTellsTheCreaturesApart()
	{
		assertEquals("HumanWalk is the player skeleton",
			(Integer) AnimationSkeletons.HUMAN, AnimationSkeletons.framemapOf(LivelyAnimation.HumanWalk));
		assertEquals("HumanIdle is the same skeleton as HumanWalk",
			(Integer) AnimationSkeletons.HUMAN, AnimationSkeletons.framemapOf(LivelyAnimation.HumanIdle));

		assertEquals((Integer) AnimationSkeletons.DWARF, AnimationSkeletons.framemapOf(LivelyAnimation.DwarfWalk));
		assertEquals((Integer) AnimationSkeletons.GOBLIN, AnimationSkeletons.framemapOf(LivelyAnimation.GoblinWalk));
		assertEquals((Integer) AnimationSkeletons.PENGUIN, AnimationSkeletons.framemapOf(LivelyAnimation.PenguinWalk));
		assertEquals((Integer) AnimationSkeletons.CAT, AnimationSkeletons.framemapOf(LivelyAnimation.KittenWalk));
		assertEquals((Integer) AnimationSkeletons.SWARM, AnimationSkeletons.framemapOf(LivelyAnimation.BeeIdle));
		assertEquals((Integer) AnimationSkeletons.DOG, AnimationSkeletons.framemapOf(LivelyAnimation.DogWalk));

		Set<Integer> named = new TreeSet<>();
		named.add(AnimationSkeletons.HUMAN);
		named.add(AnimationSkeletons.DWARF);
		named.add(AnimationSkeletons.GOBLIN);
		named.add(AnimationSkeletons.PENGUIN);
		named.add(AnimationSkeletons.CAT);
		named.add(AnimationSkeletons.SWARM);
		named.add(AnimationSkeletons.DOG);
		assertEquals("the seven named skeletons must be seven different numbers", 7, named.size());

		// And the whole table, so a wholesale flattening cannot hide behind the seven
		// spot-checks above.
		assertEquals("distinct framemaps across the animation table",
			33, new TreeSet<>(AnimationSkeletons.all().values()).size());
	}

	/**
	 * The corrected pairs, named one by one.
	 *
	 * <p>The sweep above says "no citizen disagrees with itself", which stays true if
	 * somebody fixes a mismatch by making the citizen stationary or by copying the
	 * wrong animation onto both fields. This says what each of the fourteen was
	 * actually given, so a regression that re-broke one of them fails with that
	 * citizen's name in the message rather than as an anonymous list entry.
	 *
	 * <p><b>"Its own species' gait" is nine of the fourteen, not fourteen.</b> The
	 * method is named for the skeleton, not the animation, because that is all the
	 * fourteen have in common. Nine got the exact {@code walk} of the NPC whose model
	 * list the record copies. Grimefang and Sludgenose got {@code GoblinWalk} (6202)
	 * where NPC 3028 "Goblin" — which owns their exact models — walks on 6180; both are
	 * framemap 1415, so the rig is right and the animation is a different goblin walk.
	 * Forester, Thalindra and the Dark wizard have no NPC owning their exact models at
	 * all, so {@code HumanWalk} was a judgement made on the rig: every NPC sharing any
	 * of Forester's or the Dark wizard's models is on framemap 0, and Thalindra is 671
	 * of 672. {@code NOTICE} carries the same three-way split, because a licensee reads
	 * that and not this.
	 */
	@Test
	public void theFourteenCorrectedCitizensCarryAGaitFromTheirOwnSkeleton()
	{
		assertGait(12594, "Grimefang", LivelyAnimation.GoblinChill, LivelyAnimation.GoblinWalk);
		assertGait(12594, "Sludgenose", LivelyAnimation.GoblinIdle3, LivelyAnimation.GoblinWalk);
		assertGait(12596, "Skipper", LivelyAnimation.PenguinIdle, LivelyAnimation.PenguinWalk);
		assertGait(12853, "Bees!", LivelyAnimation.BeeIdle, LivelyAnimation.BeeIdle);
		assertGait(12853, "Nightfire", LivelyAnimation.CatSit, LivelyAnimation.KittenWalk);
		assertGait(12853, "Dofur", LivelyAnimation.DwarfSit, LivelyAnimation.DwarfWalk);
		assertGait(12853, "Simon", LivelyAnimation.DwarfSit, LivelyAnimation.DwarfWalk);
		assertGait(12853, "Rifur", LivelyAnimation.DwarfSmith, LivelyAnimation.DwarfWalk);
		assertGait(12853, "Dorgud", LivelyAnimation.DwarfIdle, LivelyAnimation.DwarfWalk);
		assertGait(14936, "Draug", LivelyAnimation.DwarfMining, LivelyAnimation.DwarfWalk);
		assertGait(12850, "Zack", LivelyAnimation.FurnaceSmelt, LivelyAnimation.HumanWalk);
		assertGait(11061, "Forester", LivelyAnimation.Woodcutting, LivelyAnimation.HumanWalk);
		assertGait(12850, "Thalindra", LivelyAnimation.Sitting, LivelyAnimation.HumanWalk);
		assertGait(12850, "Dark wizard", LivelyAnimation.Sitting, LivelyAnimation.HumanWalk);
	}

	/**
	 * A printed roster of every animation pairing the dataset uses, backed by an
	 * assertion so it cannot drift from what was checked.
	 */
	@Test
	public void printsTheAnimationPairingsForHumanReview()
	{
		TreeMap<String, Integer> pairings = new TreeMap<>();

		for (EntityDefinition citizen : shippedCitizens())
		{
			LivelyAnimation idle = citizen.getIdleAnimation();
			LivelyAnimation move = citizen.getMoveAnimation();
			if (idle == null || move == null)
			{
				continue;
			}
			String key = String.format("%-22s %-22s framemap %d",
				idle.name(), move.name(), AnimationSkeletons.framemapOf(idle));
			pairings.merge(key, 1, Integer::sum);
		}

		System.out.println("Lively Cities animation pairings — idle, move, shared framemap, citizens");
		int total = 0;
		for (java.util.Map.Entry<String, Integer> row : pairings.entrySet())
		{
			System.out.println(row.getKey() + "   " + row.getValue());
			total += row.getValue();
		}

		assertEquals("the printed rows have to account for every checked citizen",
			CITIZENS_WITH_BOTH_ANIMATIONS, total);
	}

	// --- fixtures ------------------------------------------------------------

	private static void assertGait(int regionId, String name, LivelyAnimation idle, LivelyAnimation move)
	{
		EntityDefinition citizen = null;
		for (EntityDefinition candidate : shippedCitizens())
		{
			if (candidate.getRegionId() == regionId && name.equals(candidate.getName()))
			{
				assertNull("two citizens named " + name + " in " + regionId
					+ ".json would make this assertion ambiguous", citizen);
				citizen = candidate;
			}
		}

		assertNotNull(name + " is no longer in " + regionId + ".json", citizen);
		assertEquals(name + " idle animation", idle, citizen.getIdleAnimation());
		assertEquals(name + " move animation", move, citizen.getMoveAnimation());
		assertEquals(name + " must stand and walk on one skeleton",
			AnimationSkeletons.framemapOf(idle), AnimationSkeletons.framemapOf(move));
	}

	private static List<EntityDefinition> shippedCitizens()
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (EntityDefinition entity : shippedEntities())
		{
			if (entity.getType() != null && entity.getType().isCitizen())
			{
				out.add(entity);
			}
		}
		return out;
	}

	/** Every shipped record, scenery included — see the body-rig rule for why. */
	private static List<EntityDefinition> shippedEntities()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<EntityDefinition> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			out.addAll(region.getEntities());
		}

		return out;
	}
}
