package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The six cameos as authored content: who they are, where they stand, what they
 * wear, which way they face, and the promises the record text makes.
 *
 * <h2>Why any of this is a test rather than a comment</h2>
 *
 * <p>These six are the only player-shaped named humans in the dataset, they stand
 * in the most player-dense location in the game, and the predecessor plugin's own
 * content rule — adopted verbatim into this project's — is <i>"we do not add 'fake
 * players'"</i>. Every property this file pins is one that, if it drifted, would
 * turn six affectionate in-jokes into exactly the thing that rule forbids: the
 * opt-in default, the theme that confines them to one city, the honesty of the
 * examine text, and the fact that nothing derives more of them.
 *
 * <h2>The tiles, and how confident this is about them</h2>
 *
 * <p><b>Not very, and the code is arranged so that it does not have to be.</b> The
 * six tiles were chosen off the Grand Exchange's wiki map (centre {@code (3165,
 * 3490)}), north-west of that centre and out of the trade lane, in an arc. Nobody
 * has stood on them in game. Authored entities get no collision validation in this
 * plugin — only echoes do — so the mitigation is twofold and both halves are
 * deliberate:
 *
 * <ol>
 *   <li>{@code EntityScene.groundIsUsable} makes a cameo's tile pass
 *       {@link StandableGround} before it may spawn, on stricter terms than an echo:
 *       {@code UNKNOWN} is refused, because unlike an echo a cameo has no authored
 *       wander box to fall back to. A cameo on a bank booth therefore does not
 *       render at all, rather than rendering inside the booth.</li>
 *   <li>The arc sits in the same region and within a handful of tiles of the three
 *       vendored entities that were placed by hand and are known to work —
 *       "Richard" at {@code (3169, 3489)} and the brazier at {@code (3169, 3488)}
 *       — so it is at least inside the same courtyard rather than somewhere derived
 *       from arithmetic alone.</li>
 * </ol>
 *
 * <p><b>They still need an in-game eyeball.</b> If any of the six turns out to be
 * standing in a wall, the collision gate will have hidden it and the fix is a tile
 * edit in {@code 12598.json} plus the matching constant in this file. That is the
 * honest state of it: this test pins what was intended, not that the intention was
 * right about the floor.
 */
public class CameoPlacementTest
{
	/** The Grand Exchange, per {@code City.GRAND_EXCHANGE}. */
	private static final int GRAND_EXCHANGE_REGION = 12598;

	/** The GE's wiki map centre — what "north-west side" is measured from. */
	private static final WorldPoint GE_CENTRE = new WorldPoint(3165, 3490, 0);

	/**
	 * The closest two cameos may stand: two tiles, Chebyshev — the same figure
	 * {@link CitizenEcho#MIN_SEPARATION_TILES} uses, and for the same reason. Citizen
	 * models are roughly a tile wide, so two on adjacent tiles interpenetrate and
	 * read as one clipped body. A group posing together has to read as six people.
	 *
	 * <p>Note this is a rule about <i>these six</i> and not about authored content in
	 * general: 44 pairs of vendored entities are closer than this to each other and
	 * that is fine, because a human put them there on purpose. Nobody has looked at
	 * these, so they get held to the derived-content standard.
	 */
	private static final int MIN_SEPARATION_TILES = 2;

	/**
	 * The six, exactly as they should appear in {@code 12598.json}.
	 *
	 * <p>Written out here rather than read from the file and compared to itself: a
	 * test that derived its expectations from the data would pass for any data. The
	 * NPC ids are the load-bearing half — every one was confirmed against
	 * {@code javap -p -constants net.runelite.api.gameval.NpcID} on the 1.12.36 jar,
	 * and the name in the comment is the constant's own.
	 *
	 * <h2>The pose rule, learned the hard way</h2>
	 *
	 * <p>These figures never move, so every one of them gets a <b>pose</b> and never an
	 * <b>action</b>. An action animation was authored around the item it uses:
	 * {@code Alching} assumes a staff, {@code Fishing} assumes a rod. Give it to a
	 * figure whose composition has no such model and it plays as a mime — bent double
	 * at the waist with the hands working at nothing, which is exactly how two of these
	 * six read on video. The test for "is this a pose" is either of two checkable
	 * things: the gameval name ends in {@code _READY} or {@code _IDLE}, or the game
	 * itself installs the id as some NPC's standing animation. The rule was stated in
	 * this file before it was broken in this file — the note on Rob rejects
	 * {@code HUMAN_UNARMEDBLOCK} for being a combat-cycle frame rather than a
	 * {@code _READY} — and then Cazh and Gunnar were given actions anyway. It is
	 * executed rather than recited now: see {@link #POSE_EVIDENCE}.
	 *
	 * <h2>The half of it that was missing</h2>
	 *
	 * <p>"Is this a pose" was never the whole question. It is "is this a pose <b>on this
	 * body's skeleton</b>", and by the letter of the rule above {@code DwarfIdle}
	 * qualifies — {@code DWARF_READY}, the standing animation of 288 NPCs — while being
	 * frame data for a rig no cameo has. That is not hypothetical: Sludgellama stood
	 * here on a Braindeath Island pose for as long as the rule said only the first half.
	 * {@link #everyCameoPoseDrivesTheSkeletonOfTheBodyItIsWornOn()} is the second half.
	 */
	private static final Cameo[] EXPECTED = {
		// 1798 = NpcID.WHITE_KNIGHT. Full plate and a shield; the gear carries
		// "imposing, defensive stance" because no loopable human block pose exists —
		// HUMAN_UNARMEDBLOCK (424) is a combat-cycle frame, not a _READY one, and
		// looping it would read as flinching on repeat.
		new Cameo("Rob", "0ca20006-8c47-4e29-9b05-7d16a4f9302e", 3158, 3494, 1798, 1664, "HumanIdle"),

		// 512 = NpcID.YOUNG_DARK_WIZARD. MageReady is TOL_MAGE_READY01 (5823), the
		// standing animation the game gives 'Transmute' The Alchemist (3592) and
		// 'Currency' The Alchemist (3594) — two robed humans holding nothing. It
		// replaces Alching (HUMAN_CASTHIGHLVLALCHEMY, 713), which is a cast: a wizard
		// with no staff performing one is the mime described above.
		new Cameo("Cazh", "0ca20001-9f4e-4b17-8d63-1e5a7c2b40d1", 3160, 3495, 512, 1792, "MageReady"),

		// 526 = NpcID.ROGUE, whose own stand/walk are HUMAN_READY (808) and HUMAN_WALK_F
		// (819) — both framemap 0. NervousIdle is NERVOUS_IDLE (10680), framemap 0, and
		// the stand of NpcID 12667 "Bandit": a man on edge, glancing about. "Shifty" is
		// the brief and a bandit's own standing pose is as close as the human rig gets.
		//
		// It replaces LectorIdle (5875 = BRAIN_BROTHER_TRANQUILITY_SHIFTY_READY), which
		// is on framemap 1304 and was kept there on a justification that turned out to
		// be false. 1304 is not "a human framemap": all 83 NPCs whose own stand and walk
		// are on it are the Braindeath Island / Rum Deal family, their models sit in a
		// 21391-21482 band plus one outlier at 32942, and not one of those models is
		// shared with the Rogue. Brother Tranquility (550) is human in fiction and his
		// models — 21412 to 21468 — are on the Braindeath rig like the rest of them. The
		// two rigs are not interchangeable either: framemap 0 has 245 transform groups
		// and a highest label of 217, framemap 1304 has 81 and 90.
		//
		// No crouch was adopted: GoblinIdle (6203) really is a squat but is goblin-
		// skeleton frame data, and HUMAN_CRATE_SQUAT (9406) is prop-specific and not a
		// _READY pose. HumanLook (2713 = READY_PLAYING_CARDS) is framemap 0 but "holding
		// cards" is not the brief.
		new Cameo("Sludgellama", "0ca20004-5e2f-4c81-9d38-4a7b0916e5c2", 3162, 3496, 526, 1856, "NervousIdle"),

		// 7987 = NpcID.CORSCURS_LORD_MARSHAL, "Lord Marshal Brogan" — full Shayzien
		// plate (models 34272-34284), so imposing without any bare skin, and sharing
		// no model at all with Rob's White Knight. He replaces NpcID.BARBARIAN (3256),
		// which is bare-chested in red shorts and read as naked on video.
		// ArmsCrossedReady is RD_KNIGHT_CROSSED_ARMS (2256) and is this same NPC's own
		// standing animation, so the pose and the body were authored for each other.
		new Cameo("Peter", "0ca20003-7d16-4a9c-8b45-2f80e6c31a97", 3164, 3495, 7987, 1984, "ArmsCrossedReady"),

		// 3680 = NpcID.MISC_SAILOR. HumanSmugIdle is HUMAN_SMUG_IDLE (14000) — an
		// _IDLE by its own name, with nothing held in it. It replaces Fishing
		// (HUMAN_FISHING_CASTING, 622), a rod cast this sailor has no rod for: on
		// video he was bent at the waist working an invisible line at crotch height.
		new Cameo("Gunnar", "0ca20002-3b8d-4f52-9a71-6c0e4d19b8f3", 3161, 3493, 3680, 1728, "HumanSmugIdle"),

		// 4214 = NpcID.HOBBES_THE_BUTLER, the closest thing to a tailored jacket in
		// the named constants. HumanLeanReady is HUMAN_LEAN_READY (916) — the one
		// animation this project added to LivelyAnimation, because the brief was
		// "leaning casually" and 1.12.36 does have a loopable human lean even though
		// the predecessor's table does not.
		new Cameo("MrCream", "0ca20005-1a6b-4d73-8f92-b30c58e2417d", 3163, 3493, 4214, 1920, "HumanLeanReady"),
	};

	// --- Identity ------------------------------------------------------------

	/**
	 * The dataset holds exactly these six cameos, with exactly these bodies, poses
	 * and facings.
	 */
	@Test
	public void theSixCameosAreAuthoredExactlyAsIntended()
	{
		List<EntityDefinition> cameos = shippedCameos();
		assertEquals("the number of cameos in the dataset", EXPECTED.length, cameos.size());

		for (Cameo expected : EXPECTED)
		{
			EntityDefinition actual = byName(cameos, expected.name);
			assertNotNull("no cameo named " + expected.name, actual);

			assertEquals(expected.name + " uuid", expected.uuid, actual.getUuid().toString());
			assertEquals(expected.name + " x", expected.x, actual.getWorldLocation().getX());
			assertEquals(expected.name + " y", expected.y, actual.getWorldLocation().getY());
			assertEquals(expected.name + " plane", 0, actual.getWorldLocation().getPlane());
			assertEquals(expected.name + " npcAppearanceId", expected.npcId, actual.getNpcAppearanceId());
			assertEquals(expected.name + " orientation", expected.orientation, actual.getOrientation());
			assertNotNull(expected.name + " idle animation", actual.getIdleAnimation());
			assertEquals(expected.name + " idle animation",
				expected.animation, actual.getIdleAnimation().name());

			assertEquals(expected.name + " is a StationaryCitizen",
				EntityType.StationaryCitizen, actual.getType());
			assertNull(expected.name + " must have no wander box — they are posing, not pacing",
				actual.getWanderBox());
			assertNull(expected.name + " must not have a move animation", actual.getMoveAnimation());
			assertEquals(expected.name + " is dressed from an NPC, so it carries no raw model ids",
				0, actual.getModelIds().length);
			assertTrue(expected.name + " must be flagged as a cameo", actual.isCameo());
			assertFalse(expected.name + " is authored, not derived", actual.isEcho());
		}
	}

	/**
	 * Six distinct people wearing six distinct bodies.
	 *
	 * <p>A copy-paste that gave two cameos the same NPC id would produce visual twins
	 * standing two tiles apart — the single most recognisable "this is a plugin, and a
	 * lazy one" tell, and the thing {@link CitizenEcho}'s whole palette re-deal exists
	 * to avoid for derived citizens.
	 */
	@Test
	public void everyCameoHasItsOwnNameUuidBodyAndFacing()
	{
		Set<String> names = new HashSet<>();
		Set<String> uuids = new HashSet<>();
		Set<Integer> npcIds = new HashSet<>();
		Set<Integer> orientations = new HashSet<>();

		for (EntityDefinition cameo : shippedCameos())
		{
			assertTrue("duplicate cameo name: " + cameo.getName(), names.add(cameo.getName()));
			assertTrue("duplicate cameo uuid: " + cameo.getUuid(), uuids.add(cameo.getUuid().toString()));
			assertTrue("two cameos share NPC body " + cameo.getNpcAppearanceId()
				+ " — they would be visual twins", npcIds.add(cameo.getNpcAppearanceId()));
			assertTrue("two cameos share orientation " + cameo.getOrientation()
				+ " — a posed group is not a rank", orientations.add(cameo.getOrientation()));
		}

		assertEquals(EXPECTED.length, names.size());
	}

	/**
	 * Every pose these six may stand in, and nothing else.
	 *
	 * <p>An allowlist rather than a denylist of actions, because the failure being
	 * guarded against is somebody reaching for whichever animation sounds most like
	 * the character — which is how Cazh got a high-alchemy cast and Gunnar got a rod
	 * cast. Adding a row here means adding the matching row to {@link #POSE_EVIDENCE},
	 * which is where the checking now has to be written down rather than asserted.
	 */
	private static final Set<LivelyAnimation> VERIFIED_POSES = EnumSet.of(
		LivelyAnimation.HumanIdle,
		LivelyAnimation.HumanLeanReady,
		LivelyAnimation.NervousIdle,
		LivelyAnimation.MageReady,
		LivelyAnimation.ArmsCrossedReady,
		LivelyAnimation.HumanSmugIdle
	);

	/**
	 * Why each row above was admitted, transcribed from the 1.12.36 cache.
	 *
	 * <p>The project's admission rule is "the gameval name ends in {@code _READY} or
	 * {@code _IDLE}, <b>or</b> the game itself installs the id as some NPC's standing
	 * animation". Both halves are cache facts, so the rule can be executed rather than
	 * recited — and it is, below. That turns "adding a row means having checked it" from
	 * an instruction in a comment into a row somebody has to write and a build that goes
	 * red if the row does not carry the property.
	 *
	 * <p>Only the two halves of the rule are recorded, not a general opinion: the count
	 * is how many NPC definitions in the cache name this id as their {@code stand}, and
	 * the name is the {@code gameval.AnimationID} constant.
	 */
	private static final Map<LivelyAnimation, PoseEvidence> POSE_EVIDENCE = poseEvidence();

	private static Map<LivelyAnimation, PoseEvidence> poseEvidence()
	{
		Map<LivelyAnimation, PoseEvidence> evidence = new EnumMap<>(LivelyAnimation.class);

		// Every ordinary human in the game, 5072 of them, starting with NpcID 13 "Piles".
		evidence.put(LivelyAnimation.HumanIdle, new PoseEvidence(808, "HUMAN_READY", 5072));

		// NpcID 14209 "Antos" and 14215 "Injured boy".
		evidence.put(LivelyAnimation.HumanLeanReady, new PoseEvidence(916, "HUMAN_LEAN_READY", 2));

		// NpcID 12667 "Bandit".
		evidence.put(LivelyAnimation.NervousIdle, new PoseEvidence(10680, "NERVOUS_IDLE", 1));

		// NpcID 3592 "'Transmute' The Alchemist", 3594 "'Currency' The Alchemist", 6157,
		// 6158. The name is the reason this row needs the second half of the rule: it
		// ends in "01", not in "_READY".
		evidence.put(LivelyAnimation.MageReady, new PoseEvidence(5823, "TOL_MAGE_READY01", 4));

		// NpcID 4680 "Lady Table", 4685 "Miss Cheevers", 4931 "Savant", 7377 "Captain
		// Kalt" and 7987 "Lord Marshal Brogan" — which is Peter's own body.
		evidence.put(LivelyAnimation.ArmsCrossedReady,
			new PoseEvidence(2256, "RD_KNIGHT_CROSSED_ARMS", 5));

		// No NPC stands on this one; it is admitted on its name alone.
		evidence.put(LivelyAnimation.HumanSmugIdle, new PoseEvidence(14000, "HUMAN_SMUG_IDLE", 0));

		return Collections.unmodifiableMap(evidence);
	}

	/**
	 * A posed figure gets a pose, never an action.
	 *
	 * <p>Three things, and they are different mistakes. First, every cameo's animation
	 * is on the verified list. Second, every entry on that list carries its evidence and
	 * that evidence satisfies the admission rule — so the list cannot be widened by
	 * adding a name, only by writing down a claim about the cache that is checkable.
	 * Third, the rule itself still rejects the animation that started all this.
	 *
	 * <p><b>And one thing it honestly does not do.</b> The second half of the admission
	 * rule would let {@code Fishing} back in: {@code HUMAN_FISHING_CASTING} really is the
	 * {@code stand} of NpcID 11225 "D3ad1i F15her", a joke NPC posed permanently
	 * mid-cast — with a rod in its composition. That is the whole point and the whole
	 * limit at once: the animation was never the defect, the missing rod was, and no
	 * offline check can ask a composition whether it holds one. What keeps it out is the
	 * allowlist, which is why the allowlist is the primary rule and not a formality.
	 */
	@Test
	public void everyCameoIsPosedRatherThanMidAction()
	{
		for (EntityDefinition cameo : shippedCameos())
		{
			LivelyAnimation pose = cameo.getIdleAnimation();
			assertNotNull(cameo.getName() + " has no animation at all", pose);
			assertTrue(cameo.getName() + " is posed with " + pose + ", which is not on the verified "
					+ "pose list — an action animation without the item it was authored around "
					+ "plays as a mime",
				VERIFIED_POSES.contains(pose));
		}

		assertEquals("every verified pose needs its evidence row and nothing else may have one",
			VERIFIED_POSES, POSE_EVIDENCE.keySet());

		for (LivelyAnimation pose : VERIFIED_POSES)
		{
			PoseEvidence evidence = POSE_EVIDENCE.get(pose);
			assertEquals(pose + "'s recorded id has to be the id the enum actually carries",
				pose.getId(), evidence.animationId);
			assertTrue(pose + " is on the verified list but its evidence does not admit it: "
					+ evidence, evidence.isAPose());
		}

		assertFalse("the rule has to still reject the animation it was written after: Alching is "
				+ "HUMAN_CASTHIGHLVLALCHEMY, it is a cast that assumes a staff, and no NPC in the "
				+ "cache stands on it",
			new PoseEvidence(713, "HUMAN_CASTHIGHLVLALCHEMY", 0).isAPose());

		assertTrue("and the rule's known limit, stated rather than papered over: Fishing is "
				+ "admissible by the letter of it, and only the allowlist keeps it out",
			new PoseEvidence(622, "HUMAN_FISHING_CASTING", 1).isAPose());
	}

	/**
	 * A pose has to drive the skeleton of the body it is worn on.
	 *
	 * <p>This is the assertion that was missing, and its absence is not theoretical:
	 * "Sludgellama" stood at the Grand Exchange on {@code LectorIdle}, framemap 1304,
	 * wearing NpcID 526 "Rogue", framemap 0. It survived because {@code AnimationSkeletonTest}
	 * compares a citizen's idle against its move, and all six cameos have no move —
	 * they are posed statues, asserted as such a few methods up. Giving Rob the dwarf's
	 * standing pose used to leave the whole suite green; now it fails here and in
	 * {@code AnimationSkeletonTest}, twice over.
	 *
	 * <p>Both directions are checked, because they fail differently. Every animation on
	 * the verified list has to be on the human framemap — that is the rule for the
	 * <i>list</i>, and it is what stops a dwarf or goblin pose being admitted at all.
	 * And each cameo's own pose has to match the framemap of the NPC body <i>it</i>
	 * wears — that is the rule for the <i>record</i>, and it would still catch a
	 * non-human cameo body being paired with a human pose.
	 */
	@Test
	public void everyCameoPoseDrivesTheSkeletonOfTheBodyItIsWornOn()
	{
		for (LivelyAnimation pose : VERIFIED_POSES)
		{
			assertEquals(pose + " is on the cameo pose list, so it has to be on the human "
					+ "skeleton — every cameo body in the dataset is",
				(Integer) AnimationSkeletons.HUMAN, AnimationSkeletons.framemapOf(pose));
		}

		List<String> violations = new ArrayList<>();

		for (EntityDefinition cameo : shippedCameos())
		{
			assertEquals(cameo.getName() + " is dressed from an NPC, so its rig is that NPC's",
				0, cameo.getModelIds().length);

			Integer body = ModelSkeletons.framemapOfBody(cameo.getNpcAppearanceId());
			assertNotNull(cameo.getName() + " wears NpcID " + cameo.getNpcAppearanceId()
				+ ", which has no framemap recorded — look it up and add a row to "
				+ "ModelSkeletons", body);

			Integer pose = AnimationSkeletons.framemapOf(cameo.getIdleAnimation());
			assertNotNull(cameo.getIdleAnimation() + " has no framemap in the table", pose);

			if (!body.equals(pose))
			{
				violations.add(cameo.getName() + " wears NpcID " + cameo.getNpcAppearanceId()
					+ " (framemap " + body + ") and is posed " + cameo.getIdleAnimation()
					+ " (framemap " + pose + ")");
			}
		}

		assertTrue("cameo(s) posed on a skeleton their body does not have: " + violations,
			violations.isEmpty());
	}

	/**
	 * Nobody at the Grand Exchange is wearing skin as a costume.
	 *
	 * <p>{@code NpcID.BARBARIAN} is bare-chested in red shorts. It was chosen for
	 * being the most muscular human body in the named constants, which it may well be,
	 * and on video it reads as a naked man standing in the busiest bank in the game.
	 * There is no offline way to ask a composition whether it is dressed — models are
	 * raw geometry — so what is checkable is the narrower thing: the id that was wrong
	 * is not back, and the id that replaced it is the armoured one it was replaced
	 * with, compared against gameval rather than against a copy of itself.
	 */
	@Test
	public void peterIsDressed()
	{
		EntityDefinition peter = byName(shippedCameos(), "Peter");
		assertNotNull("the cameo this test is about is no longer in the dataset", peter);

		assertEquals("Peter wears Lord Marshal Brogan's Shayzien plate",
			NpcID.CORSCURS_LORD_MARSHAL, peter.getNpcAppearanceId());
		assertNotEquals("NpcID.BARBARIAN is bare-chested and cannot come back",
			NpcID.BARBARIAN, peter.getNpcAppearanceId());
	}

	// --- Placement -----------------------------------------------------------

	/**
	 * All six stand in the Grand Exchange's own region, north-west of its centre, and
	 * close enough to it to be inside the compound rather than out on the Varrock
	 * road.
	 */
	@Test
	public void everyCameoStandsNorthWestOfTheGrandExchangeCentre()
	{
		List<String> violations = new ArrayList<>();

		for (EntityDefinition cameo : shippedCameos())
		{
			WorldPoint tile = cameo.getWorldLocation();

			if (cameo.getTileRegionId() != GRAND_EXCHANGE_REGION)
			{
				violations.add(cameo.getName() + " stands in region " + cameo.getTileRegionId());
			}

			if (tile.getX() > GE_CENTRE.getX() || tile.getY() < GE_CENTRE.getY())
			{
				violations.add(cameo.getName() + " at " + tile.getX() + "," + tile.getY()
					+ " is not north-west of the GE centre");
			}

			int distance = RenderPolicy.tileDistance(GE_CENTRE, tile);
			if (distance > 10)
			{
				violations.add(cameo.getName() + " is " + distance
					+ " tiles from the GE centre, which is outside the compound");
			}
		}

		assertTrue("cameo placement violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * The GE checkbox really is the one that governs them.
	 *
	 * <p>Not a restatement of the region assertion above: {@code City} owns the
	 * region-to-checkbox mapping and fails <i>open</i> for a region no city claims, so
	 * "they are in 12598" and "unticking Grand Exchange switches them off" are two
	 * different facts and only the second one matters to a user.
	 */
	@Test
	public void theGrandExchangeCheckboxIsTheOneThatGovernsThem()
	{
		FakeConfig config = new FakeConfig();

		for (EntityDefinition cameo : shippedCameos())
		{
			assertEquals("the city claiming " + cameo.getName() + "'s tile",
				City.GRAND_EXCHANGE, City.of(cameo.getCityRegionId()));

			config.enable(City.GRAND_EXCHANGE);
			assertTrue(City.isEnabled(cameo.getCityRegionId(), config));

			config.disableOnly(City.GRAND_EXCHANGE);
			assertFalse(cameo.getName() + " must go away with the Grand Exchange checkbox",
				City.isEnabled(cameo.getCityRegionId(), config));
		}
	}

	/**
	 * No two of them interpenetrate, and none of them stands on top of one of the
	 * three vendored entities already in the file.
	 */
	@Test
	public void theGroupIsSpacedFarEnoughApartToReadAsSixPeople()
	{
		List<EntityDefinition> cameos = shippedCameos();
		List<String> violations = new ArrayList<>();

		for (int i = 0; i < cameos.size(); i++)
		{
			for (int j = i + 1; j < cameos.size(); j++)
			{
				EntityDefinition a = cameos.get(i);
				EntityDefinition b = cameos.get(j);
				int distance = RenderPolicy.tileDistance(a.getWorldLocation(), b.getWorldLocation());
				if (distance < MIN_SEPARATION_TILES)
				{
					violations.add(a.getName() + " and " + b.getName() + " are " + distance
						+ " tile(s) apart and would clip through each other");
				}
			}
		}

		for (EntityDefinition cameo : cameos)
		{
			for (EntityDefinition other : shippedRegion())
			{
				if (other.isCameo())
				{
					continue;
				}
				int distance = RenderPolicy.tileDistance(
					cameo.getWorldLocation(), other.getWorldLocation());
				if (distance < MIN_SEPARATION_TILES)
				{
					violations.add(cameo.getName() + " is " + distance + " tile(s) from the vendored "
						+ other.label());
				}
			}
		}

		assertTrue("cameo spacing violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * They face the courtyard, not each other's backs and not all north.
	 *
	 * <p>Measured rather than eyeballed. The client's orientation convention is
	 * {@code 0 = south, 512 = west, 1024 = north, 1536 = east} (read off
	 * {@code CitizenWalk.STEP_ORIENTATION}, which maps a step of {@code dx=0, dy=-1}
	 * to 0 and {@code dx=+1, dy=0} to 1536), so an orientation's facing vector is
	 * {@code (-sin θ, -cos θ)} with {@code θ = 2π · o / 2048}. The dot product of
	 * that with the unit vector towards the GE centre has to be strongly positive:
	 * 0.9 is a cone of about 26 degrees, so a cameo that faced north — which is what
	 * an unset {@code baseOrientation} would mean — comes out at roughly -1 and fails
	 * hard.
	 */
	@Test
	public void everyCameoFacesTheCourtyardRatherThanAwayFromTheViewer()
	{
		List<String> violations = new ArrayList<>();

		for (EntityDefinition cameo : shippedCameos())
		{
			WorldPoint tile = cameo.getWorldLocation();
			double toCentreX = GE_CENTRE.getX() - tile.getX();
			double toCentreY = GE_CENTRE.getY() - tile.getY();
			double length = Math.hypot(toCentreX, toCentreY);
			assertTrue("a cameo standing on the GE centre has no direction to face", length > 0);

			double theta = 2 * Math.PI * cameo.getOrientation() / 2048.0;
			double facingX = -Math.sin(theta);
			double facingY = -Math.cos(theta);

			double alignment = (facingX * toCentreX + facingY * toCentreY) / length;
			if (alignment < 0.9)
			{
				violations.add(String.format("%s faces %d, which is %.2f aligned with the courtyard "
						+ "(needs 0.90)", cameo.getName(), cameo.getOrientation(), alignment));
			}
		}

		assertTrue("cameo facing violation(s): " + violations, violations.isEmpty());
	}

	// --- Honesty -------------------------------------------------------------

	/**
	 * The record text has to be affectionate without claiming to be the person.
	 *
	 * <p>Three requirements, and each has a failure mode with a name. Every cameo
	 * needs an {@code examineText}, or {@code CitizenLabel.examineMessage} prints the
	 * plugin's disclaimer and nothing else and the player learns nothing. That text
	 * has to say outright that it is a likeness and not a player, because "Rob" over
	 * a White Knight model at the Grand Exchange is otherwise indistinguishable from
	 * an account name. And nothing may claim the person is <i>present</i> — the
	 * difference between a tribute and an impersonation.
	 */
	@Test
	public void everyCameoExamineTextSaysItIsALikenessAndNotAPlayer()
	{
		List<String> violations = new ArrayList<>();

		// Phrasings that would turn a tribute into a claim about a real account.
		List<String> forbidden = Arrays.asList(
			"is here", "is online", "logged in", "my account", "his account", "her account",
			"real player", "an actual player");

		for (EntityDefinition cameo : shippedCameos())
		{
			String examine = cameo.getExamineText();
			if (examine == null || examine.trim().isEmpty())
			{
				violations.add(cameo.getName() + " has no examine text");
				continue;
			}

			String lower = examine.toLowerCase();

			if (!lower.contains("likeness") && !lower.contains("cameo"))
			{
				violations.add(cameo.getName() + " does not say what it is: '" + examine + "'");
			}

			if (!lower.contains("not a player"))
			{
				violations.add(cameo.getName() + " does not disclaim being a player: '" + examine + "'");
			}

			for (String phrase : forbidden)
			{
				if (lower.contains(phrase))
				{
					violations.add(cameo.getName() + " implies the person is present ('" + phrase
						+ "'): '" + examine + "'");
				}
			}

			// The plugin's own disclaimer still has to be the last word, whatever the
			// authored text says.
			String message = CitizenLabel.examineMessage(cameo);
			if (!message.contains("not a real NPC"))
			{
				violations.add(cameo.getName() + " loses the plugin disclaimer: " + message);
			}
			if (!message.contains(cameo.getName()))
			{
				violations.add(cameo.getName() + " is not named in its own examine line: " + message);
			}
		}

		assertTrue("cameo text violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * Each has one short line, in character.
	 *
	 * <p>Cameos are the one part of the dataset where every citizen talks, so the cap
	 * matters: six characters shouting at once in the busiest room in the game is the
	 * overhead-text spam that was the predecessor's loudest complaint. One line each
	 * keeps them inside the same cadence and the same
	 * {@code maxConcurrentRemarks} cap as everybody else.
	 */
	@Test
	public void everyCameoHasExactlyOneShortRemark()
	{
		List<String> violations = new ArrayList<>();

		for (EntityDefinition cameo : shippedCameos())
		{
			String[] remarks = cameo.getRemarks();
			if (remarks.length != 1)
			{
				violations.add(cameo.getName() + " has " + remarks.length + " remark(s), expected 1");
				continue;
			}

			if (remarks[0].length() > 40)
			{
				violations.add(cameo.getName() + "'s remark is " + remarks[0].length()
					+ " characters — overhead text, not dialogue: '" + remarks[0] + "'");
			}

			assertNotNull("a cameo with a remark must have something to mute",
				CitizenRemarks.forDefinition(cameo));
		}

		assertTrue("cameo remark violation(s): " + violations, violations.isEmpty());
	}

	// --- The opt-in ----------------------------------------------------------

	/**
	 * The default, and it is the whole reason the feature is allowed to exist.
	 *
	 * <p>Asserted on the interface's own {@code default} method rather than on a
	 * fake, because that method <i>is</i> the shipped default: a fresh install and a
	 * hub reviewer both read exactly this value.
	 */
	@Test
	public void theCameosSettingIsOffByDefault()
	{
		LivelyCitiesConfig shipped = new LivelyCitiesConfig()
		{
		};

		assertFalse("cameos must be opt-in — player-shaped content at the Grand Exchange is "
				+ "exactly what got the predecessor plugin disabled",
			shipped.cameos());

		assertFalse("and the test fake must inherit that default rather than opting in for "
				+ "convenience", new FakeConfig().cameos());
	}

	/**
	 * The setting has to explain itself where the user reads it.
	 *
	 * <p>A checkbox whose description said only "adds six cameos" would leave a
	 * reviewer to work out that this is the player-shaped content their own rules are
	 * about. The description is therefore load-bearing text, and this is what stops it
	 * being trimmed to a label.
	 */
	@Test
	public void theCameosSettingDescriptionSaysWhatItAddsAndWhyItIsOff()
	{
		String lower = LivelyCitiesConfig.CAMEOS_DESCRIPTION.toLowerCase();

		assertTrue("it must say where they are", lower.contains("grand exchange"));
		assertTrue("it must say how many", lower.contains("six"));
		assertTrue("it must say what they look like", lower.contains("player-shaped"));
		assertTrue("it must say being off is the deliberate default",
			lower.contains("switched off by default"));
		assertTrue("it must say whose likenesses they are", lower.contains("friend"));
		assertTrue("it must say the city checkbox applies too", lower.contains("grand exchange checkbox"));

		// Long enough to have said all of that, and buried in nothing: this is the
		// text the user reads on the checkbox itself.
		assertTrue("the description must not be trimmed to a tooltip",
			LivelyCitiesConfig.CAMEOS_DESCRIPTION.length() > 300);

		assertTrue("the label itself must not bury it — it is one row in a list of checkboxes",
			LivelyCitiesConfig.CAMEOS_NAME.toLowerCase().contains("cameo"));
		assertTrue("and the label should say it is off, because a reviewer reads labels first",
			LivelyCitiesConfig.CAMEOS_NAME.toLowerCase().contains("off by default"));
	}

	/**
	 * What the description says they are dressed as has to be what they are dressed as.
	 *
	 * <p>The check above pins the phrases that make the text honest about the
	 * <i>feature</i>, and it went on passing for a description that said "a barbarian"
	 * after Peter had stopped being one — because it never looked at the costume list.
	 * That list is the concrete half of the promise: the abstract half ("player-shaped",
	 * "off by default") is what a reviewer skims, and the costume list is what a user
	 * checks against what they can see standing at the bank.
	 *
	 * <p>Derived from the roster rather than restated. {@link #COSTUMES} maps an NPC
	 * body to the word the description uses for it, including bodies that are no longer
	 * worn — so a retired costume is asserted <i>absent</i> by the same table that
	 * asserts the current ones present, and "barbarian" cannot come back into the string
	 * any more than {@code NpcID.BARBARIAN} can come back into the data.
	 */
	@Test
	public void theCameosSettingDescriptionNamesTheCostumesTheCameosActuallyWear()
	{
		String lower = LivelyCitiesConfig.CAMEOS_DESCRIPTION.toLowerCase();

		Set<Integer> worn = new HashSet<>();
		for (EntityDefinition cameo : shippedCameos())
		{
			worn.add(cameo.getNpcAppearanceId());
		}
		assertEquals("one body per cameo", EXPECTED.length, worn.size());

		List<String> violations = new ArrayList<>();

		for (Map.Entry<Integer, String> costume : COSTUMES.entrySet())
		{
			boolean mentioned = lower.contains(costume.getValue());
			if (worn.contains(costume.getKey()) && !mentioned)
			{
				violations.add("a cameo wears NpcID " + costume.getKey()
					+ " but the description never says \"" + costume.getValue() + "\"");
			}
			if (!worn.contains(costume.getKey()) && mentioned)
			{
				violations.add("the description still says \"" + costume.getValue()
					+ "\", but no cameo wears NpcID " + costume.getKey() + " any more");
			}
		}

		for (int body : worn)
		{
			if (!COSTUMES.containsKey(body))
			{
				violations.add("NpcID " + body + " is worn by a cameo and has no word in the "
					+ "costume table — add one, and put it in the description");
			}
		}

		assertTrue("cameo costume violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * The word the settings description uses for each cameo body, and for the one it
	 * used to use.
	 *
	 * <p>Retired entries stay. {@code NpcID.BARBARIAN} is here precisely because it is
	 * not worn: the assertion above reads this table in both directions, so leaving a
	 * dead costume in the string fails as loudly as leaving a live one out.
	 */
	private static final Map<Integer, String> COSTUMES = costumes();

	private static Map<Integer, String> costumes()
	{
		Map<Integer, String> words = new TreeMap<>();
		words.put(NpcID.YOUNG_DARK_WIZARD, "wizard");        // Cazh
		words.put(NpcID.MISC_SAILOR, "sailor");              // Gunnar
		words.put(NpcID.CORSCURS_LORD_MARSHAL, "soldier");   // Peter
		words.put(NpcID.ROGUE, "rogue");                     // Sludgellama
		words.put(NpcID.HOBBES_THE_BUTLER, "butler");        // MrCream
		words.put(NpcID.WHITE_KNIGHT, "white knight");       // Rob
		words.put(NpcID.BARBARIAN, "barbarian");             // retired — see the javadoc
		return Collections.unmodifiableMap(words);
	}

	// --- The lint ------------------------------------------------------------

	/**
	 * Every cameo in the data is tagged {@link Theme#CAMEO} in {@link EntityTheme},
	 * and nothing else is.
	 *
	 * <p>This is the join between the dataset and the placement lint, and without it
	 * the lint would be honest about six citizens and blind to a seventh. Both
	 * directions are checked: an untagged cameo would be {@link Theme#GENERIC}, which
	 * is compatible with every region, so it could be copied into Varrock square
	 * unnoticed — and a tagged non-cameo would be a citizen that mysteriously may only
	 * stand at the Grand Exchange.
	 */
	@Test
	public void theCameoThemeTableAndTheDatasetsOwnFlagAgreeExactly()
	{
		Set<String> flaggedInData = new TreeSet<>();
		Set<String> taggedAsCameo = new TreeSet<>();

		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (citizen.cameo)
			{
				flaggedInData.add(citizen.uuid);
			}
			if (EntityTheme.themeOf(citizen.uuid) == Theme.CAMEO)
			{
				taggedAsCameo.add(citizen.uuid);
			}
		}

		assertEquals("the dataset's cameo count", EXPECTED.length, flaggedInData.size());
		assertEquals("every cameo has to be themed CAMEO and nothing else may be",
			flaggedInData, taggedAsCameo);
	}

	/**
	 * The poison property {@link Theme#CAMEO} depends on: it is mapped to exactly one
	 * city.
	 *
	 * <p>Mapped to none and it would behave like {@link Theme#UNIQUE_BOSS} — flagged
	 * everywhere, including at home, and the shipped dataset would fail its own lint.
	 * Mapped to two or more and a cameo becomes copy-pasteable into a second city,
	 * which is how six in-jokes become the "fake players" the content rules forbid.
	 * The mirror of {@code PlacementLintTest.noCityIsEverMappedToTheUniqueBossTheme}.
	 */
	@Test
	public void theCameoThemeMapsToExactlyOneCityAndItIsTheGrandExchange()
	{
		List<String> cities = new ArrayList<>();
		for (City city : City.values())
		{
			if (CityTheme.of(city) == Theme.CAMEO)
			{
				cities.add(city.getLabel());
			}
		}

		assertEquals("Theme.CAMEO must be compatible with exactly one city: " + cities,
			1, cities.size());
		assertEquals(City.GRAND_EXCHANGE.getLabel(), cities.get(0));

		// And the rule it buys, stated directly rather than left to the lint.
		assertTrue("a cameo belongs at the Grand Exchange",
			PlacementCompatibility.isCompatible(Theme.CAMEO, CityTheme.of(City.GRAND_EXCHANGE)));
		assertFalse("a cameo does not belong in Varrock square",
			PlacementCompatibility.isCompatible(Theme.CAMEO, CityTheme.of(City.VARROCK)));
		assertTrue("and an ordinary townsperson is still welcome at the Grand Exchange — "
				+ "Richard the cook is GENERIC and lives in the same file",
			PlacementCompatibility.isCompatible(Theme.GENERIC, CityTheme.of(City.GRAND_EXCHANGE)));
	}

	/**
	 * A human-readable roll call, printed for review, backed by an assertion so it
	 * cannot drift from what the tests above checked.
	 */
	@Test
	public void printsTheCameoRosterForHumanReview()
	{
		TreeMap<String, String> rows = new TreeMap<>();

		System.out.println("Lively Cities cameos — region " + GRAND_EXCHANGE_REGION
			+ " (Grand Exchange), behind the `cameos` setting, default off");
		System.out.println("name          tile          npc    facing  animation        remark");

		for (EntityDefinition cameo : shippedCameos())
		{
			String row = String.format("%-13s %d,%-8d %-6d %-7d %-16s %s",
				cameo.getName(),
				cameo.getWorldLocation().getX(),
				cameo.getWorldLocation().getY(),
				cameo.getNpcAppearanceId(),
				cameo.getOrientation(),
				cameo.getIdleAnimation() == null ? "-" : cameo.getIdleAnimation().name(),
				cameo.getRemarks().length == 0 ? "-" : cameo.getRemarks()[0]);
			rows.put(cameo.getName(), row);
		}

		for (String row : rows.values())
		{
			System.out.println(row);
		}

		assertEquals("every cameo needs a row", EXPECTED.length, rows.size());
	}

	// --- fixtures ------------------------------------------------------------

	private static List<EntityDefinition> shippedRegion()
	{
		RegionDefinition region = new RegionDataLoader(TestGson.injected())
			.loadRegion(GRAND_EXCHANGE_REGION);
		assertNotNull("region " + GRAND_EXCHANGE_REGION + " failed to load", region);
		return region.getEntities();
	}

	/**
	 * The cameos, read out of the whole shipped dataset rather than out of the one
	 * file they are expected to be in — so a cameo that got copied into another region
	 * turns up here and fails the placement assertions, instead of being invisible to
	 * this file.
	 */
	private static List<EntityDefinition> shippedCameos()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<EntityDefinition> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			for (EntityDefinition entity : region.getEntities())
			{
				if (entity.isCameo())
				{
					out.add(entity);
				}
			}
		}

		return out;
	}

	private static EntityDefinition byName(List<EntityDefinition> entities, String name)
	{
		for (EntityDefinition entity : entities)
		{
			if (name.equals(entity.getName()))
			{
				return entity;
			}
		}
		return null;
	}

	/**
	 * The two cache facts the pose admission rule is written in terms of.
	 *
	 * <p>{@link #standOfNpcCount} is how many NPC definitions in the 1.12.36 cache name
	 * {@link #animationId} as their {@code stand}. Zero is a legitimate value and is not
	 * a failure on its own — {@code HUMAN_SMUG_IDLE} is admitted on its name.
	 */
	private static final class PoseEvidence
	{
		final int animationId;
		final String gamevalName;
		final int standOfNpcCount;

		PoseEvidence(int animationId, String gamevalName, int standOfNpcCount)
		{
			this.animationId = animationId;
			this.gamevalName = gamevalName;
			this.standOfNpcCount = standOfNpcCount;
		}

		/** The admission rule itself, executable. */
		boolean isAPose()
		{
			return gamevalName.endsWith("_READY")
				|| gamevalName.endsWith("_IDLE")
				|| standOfNpcCount > 0;
		}

		@Override
		public String toString()
		{
			return gamevalName + " (" + animationId + "), stand of " + standOfNpcCount + " NPC(s)";
		}
	}

	/** One expected cameo, written out by hand. */
	private static final class Cameo
	{
		final String name;
		final String uuid;
		final int x;
		final int y;
		final int npcId;
		final int orientation;
		final String animation;

		Cameo(String name, String uuid, int x, int y, int npcId, int orientation, String animation)
		{
			this.name = name;
			this.uuid = uuid;
			this.x = x;
			this.y = y;
			this.npcId = npcId;
			this.orientation = orientation;
			this.animation = animation;
		}
	}
}
