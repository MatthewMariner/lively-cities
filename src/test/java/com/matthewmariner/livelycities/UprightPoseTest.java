package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Nobody in this plugin lies collapsed in a public street.
 *
 * <h2>Why this is a rule and not a preference</h2>
 *
 * <p>Four vendored citizens rendered <i>exactly as authored</i> — nothing was broken
 * about them. What they depicted was the problem: a peasant lying flat in the road
 * in Varrock, a tramp curled up on the ground in Draynor market beside child NPCs,
 * a man weeping in the street, and a man face-down outside Edgeville. The brief for
 * this plugin is ambient city life; on video those four read as bugs, and one of
 * them reads worse than a bug.
 *
 * <p>They were re-posed rather than deleted, because the characters are good — a
 * drunk, a tramp, a man having a bad day, someone who came back from the Wilderness
 * the hard way all belong in a city. Only the pose changed; examine text and
 * remarks are untouched, and {@code NOTICE} records the change against the vendored
 * data as the licence requires.
 *
 * <p>This test is what stops the change being undone by accident, and — more
 * usefully — what stops the next authoring pass reaching for the same animations. A
 * dataset that is going to grow needs the rule written down where it executes.
 */
public class UprightPoseTest
{
	/**
	 * Poses that put a person on the ground or visibly in distress.
	 *
	 * <p>Named individually rather than inferred, because there is no property of an
	 * animation that says "this is undignified" — it is an editorial judgement and it
	 * belongs in a list a human can argue with. Every one of these is still in
	 * {@link LivelyAnimation}: the ids remain correct and a future authored scene —
	 * a hospital, a battlefield, the aftermath of something — may well want them. It
	 * is a <i>street</i> they may not be used in.
	 */
	private static final Set<LivelyAnimation> NOT_FOR_A_PUBLIC_STREET = EnumSet.of(
		LivelyAnimation.HalfLayingDown,  // WOUNDED_SITTING (1147)
		LivelyAnimation.LayingDown,      // HUMAN_UNCONSCIOUS (838)
		LivelyAnimation.CurledUp,        // MYQ3_CITIZEN_HUDDLED (4712)
		LivelyAnimation.FallenManDead,   // DREAM_CYRISUS_UNCONSCIOUS (6280)
		LivelyAnimation.FallenManIdle,   // DREAM_CYRISUS_BARELY_CONSCIOUS (6282)
		LivelyAnimation.Crying           // EMOTE_CRY (860)
	);

	/**
	 * The rule, over every citizen in the dataset.
	 */
	@Test
	public void noCitizenIsPosedCollapsedOrDistressed()
	{
		List<String> violations = new ArrayList<>();

		for (EntityDefinition entity : shipped())
		{
			if (entity.getType() == null || !entity.getType().isCitizen())
			{
				continue;
			}

			LivelyAnimation pose = entity.getIdleAnimation();
			if (pose != null && NOT_FOR_A_PUBLIC_STREET.contains(pose))
			{
				violations.add(entity.label() + " in " + entity.getRegionId() + ".json is posed "
					+ pose + " — \"" + entity.getExamineText() + "\"");
			}
		}

		assertTrue("citizen(s) depicted on the ground or in distress in a public street: "
			+ violations, violations.isEmpty());

		assertEquals("the list has to keep its teeth — six poses, not an empty set",
			6, NOT_FOR_A_PUBLIC_STREET.size());
	}

	/**
	 * The four, by name and by pose.
	 *
	 * <p>The sweep above stays green if somebody "fixes" a prone citizen by deleting
	 * it, or by giving all four the same blank {@code HumanIdle}. This says what each
	 * one was actually given and why it is that one, so a regression names the person.
	 */
	@Test
	public void theFourRePosedCitizensAreStandingAndStillThemselves()
	{
		// DRUNK_PLAYER_READY (2770) — the standing animation the game gives its own
		// "Drunken man", NpcID.FALADOR_MAN1. He sways instead of lying in the road.
		assertPose(12597, "Drunken peasant", LivelyAnimation.DrunkPlayerReady, "He had a long night.");

		// VARROCK_TRAMP_READY (6480) — the game's own idea of how a Varrock tramp
		// stands, and Joe is Varrock's tramp. Hunched, but on his feet.
		assertPose(12853, "Joe the tramp", LivelyAnimation.VarrockTrampReady, "He's had better days.");

		// EMOTE_SLAP_HEAD (4275) — upright, legible from across a street, and exactly
		// what the examine text already said about him. Replaces EMOTE_CRY.
		assertPose(12853, "Pryce", LivelyAnimation.SlapHead, "He's not having a good day.");

		// NERVOUS_IDLE (10680) — the standing animation of NPC 12667 "Bandit". A man
		// who came back from the Wilderness, still checking over his shoulder,
		// rather than a body on the ground outside the bank.
		assertPose(12342, "Damien", LivelyAnimation.NervousIdle, "A victim of the wilderness.");

		for (LivelyAnimation replaced : EnumSet.of(LivelyAnimation.HalfLayingDown,
			LivelyAnimation.CurledUp, LivelyAnimation.Crying, LivelyAnimation.FallenManIdle))
		{
			assertTrue(replaced + " was one of the four replaced poses and has to stay on the list",
				NOT_FOR_A_PUBLIC_STREET.contains(replaced));
		}
	}

	/**
	 * The one exemption, stated out loud.
	 *
	 * <p>The rule above is scoped to citizens, and that is not an oversight: a scenery
	 * record in {@code 12342.json} at 3090,3499 — the tile beside Damien — carries
	 * {@code FallenManIdle} on a single-model prop. It is not a person, it has no
	 * examine text, and nothing offline can say what model 2384 depicts. Rather than
	 * quietly excluding scenery, the exemption is pinned at exactly one record, so a
	 * second one cannot appear without somebody deciding it should.
	 */
	@Test
	public void exactlyOneSceneryRecordUsesOneOfThesePosesAndItIsTheKnownOne()
	{
		List<String> sceneryUsing = new ArrayList<>();

		for (EntityDefinition entity : shipped())
		{
			if (entity.getType() != EntityType.Scenery)
			{
				continue;
			}
			LivelyAnimation pose = entity.getIdleAnimation();
			if (pose != null && NOT_FOR_A_PUBLIC_STREET.contains(pose))
			{
				sceneryUsing.add(entity.getRegionId() + " " + entity.label() + " " + pose);
			}
		}

		assertEquals("scenery records using a ground pose: " + sceneryUsing, 1, sceneryUsing.size());
		assertEquals("12342 Scenery@3090,3499,0 FallenManIdle", sceneryUsing.get(0));
	}

	// --- fixtures ------------------------------------------------------------

	private static void assertPose(int regionId, String name, LivelyAnimation pose, String examine)
	{
		EntityDefinition citizen = null;
		for (EntityDefinition candidate : shipped())
		{
			if (candidate.getRegionId() == regionId && name.equals(candidate.getName()))
			{
				assertNull("two citizens named " + name + " in " + regionId + ".json", citizen);
				citizen = candidate;
			}
		}

		assertNotNull(name + " is no longer in " + regionId + ".json", citizen);
		assertEquals(name + " pose", pose, citizen.getIdleAnimation());
		assertEquals(name + " keeps the examine text it was authored with",
			examine, citizen.getExamineText());
	}

	private static List<EntityDefinition> shipped()
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
