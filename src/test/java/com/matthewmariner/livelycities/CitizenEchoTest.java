package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The derivation: who seeds an echo, how many, where it stands, what it looks
 * like, and what it is called.
 *
 * <p>Everything here is offline — {@link CitizenEcho#echoesOfRegion} touches nothing
 * but its argument — so these run against the real shipped dataset as well as
 * against hand-built fixtures. The shipped-data tests are the ones that keep the
 * feature's headline claim honest: they recompute the echo count from the 27 shipped
 * region files rather than trusting the number written in a comment, and they are the
 * only tests that can see a citizen colliding with somebody else's echo — a fixture
 * holding one citizen has no other lineage to collide with.
 */
public class CitizenEchoTest
{
	private static final int VARROCK_SOUTH = 12852;

	// --- Who seeds an echo ----------------------------------------------------

	@Test
	public void aCitizenWithNoRecolourPaletteSeedsNothing()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("nothing to re-deal means no honest way to make it look different",
			echoesOf(regions.citizen(VARROCK_SOUTH, 3225, 3355, 0)).isEmpty());
	}

	@Test
	public void aCitizenWithASinglePairSeedsNothing()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("one slot cannot be re-dealt into a different slot",
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 1)).isEmpty());
	}

	@Test
	public void twoPairsSeedOneEchoAndThreeOrMoreSeedTwo()
	{
		FakeRegions regions = new FakeRegions();

		assertEquals("two pairs admit exactly one distinct re-deal",
			1, echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 2)).size());
		assertEquals(CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3230, 3355, 3)).size());
		assertEquals("and the cap holds however rich the palette gets",
			CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3235, 3355, 11)).size());
	}

	/**
	 * A palette whose {@code replace} values are all the same colour.
	 *
	 * <p>Three shipped citizens are like this ("Brother Keptic", "Dark wizard",
	 * "Ambatu"; a fourth, "Sister Palus", went with region 13622 in the nine-city
	 * cut). Every rotation of {@code [red, red]} is
	 * {@code [red, red]}, so an echo would be a pixel-for-pixel copy of its source
	 * standing two tiles away — which is the twins failure. Counting pairs alone
	 * would have let them through.
	 */
	@Test
	public void aPaletteThatCannotActuallyBeReDealtSeedsNothing()
	{
		assertEquals(0, CitizenEcho.distinctDeals(new short[]{4769, 4769}).length);
		assertEquals(0, CitizenEcho.distinctDeals(new short[]{10508, 10508, 10508}).length);

		// And a palette that repeats in a pattern offers fewer deals than rotations:
		// [a,b,a,b] has three rotations and one distinct result.
		assertEquals(1, CitizenEcho.distinctDeals(new short[]{7, 9, 7, 9}).length);
	}

	// --- The body rule --------------------------------------------------------

	/**
	 * <b>An echo's name says "nobody in particular". Its body has to agree.</b>
	 *
	 * <p>{@link EntityDefinition#echoOf} replaces five things and inherits five
	 * others, and until this rule only the sixth ({@code npcAppearanceId}) was checked
	 * at all. So a gardener's watering can, a butler's chair, a smith's anvil pose and
	 * a citizen with a bench welded into his model all walked into a figure called
	 * {@link CitizenEcho#ECHO_NAME}. The gardener is a photograph.
	 *
	 * <p>Three fixtures for the three ways a body can be doing something, plus the
	 * control that says a plain standing citizen with the same palette still seeds.
	 * The control is the assertion that keeps the other three honest: without it a
	 * gate that refused everything would pass all three.
	 */
	@Test
	public void aCitizenWhoseBodyIsDoingSomethingSeedsNothing()
	{
		FakeRegions regions = new FakeRegions();

		assertFalse("the control: an ordinary standing citizen with this palette seeds",
			echoesOf(regions.recolouredButBusy(
				VARROCK_SOUTH, 3225, 3355, LivelyAnimation.HumanIdle, null, null, 0)).isEmpty());

		assertTrue("sitting on a chair that will not come with it",
			echoesOf(regions.recolouredButBusy(
				VARROCK_SOUTH, 3235, 3355, LivelyAnimation.Sitting, null, null, 0)).isEmpty());
		assertTrue("miming a tool it was authored holding",
			echoesOf(regions.recolouredButBusy(
				VARROCK_SOUTH, 3245, 3355, LivelyAnimation.AnvilBang, null, null, 0)).isEmpty());
		assertTrue("a pose that is about a prop",
			echoesOf(regions.recolouredButBusy(
				VARROCK_SOUTH, 3255, 3355, LivelyAnimation.StandingWithBook, null, null, 0)).isEmpty());

		assertTrue("scenery welded into the body",
			echoesOf(regions.recolouredButBusy(
				VARROCK_SOUTH, 3265, 3355, LivelyAnimation.HumanIdle, null, null, 7719)).isEmpty());
		assertTrue("scaled to fit one piece of the world",
			echoesOf(regions.recolouredButBusy(VARROCK_SOUTH, 3275, 3355,
				LivelyAnimation.HumanIdle, new float[]{-0.5f, -0.5f, -0.5f}, null, 0)).isEmpty());
		assertTrue("nudged off its tile to line up with one piece of the world",
			echoesOf(regions.recolouredButBusy(VARROCK_SOUTH, 3285, 3355,
				LivelyAnimation.HumanIdle, null, new float[]{0f, 0f, -1f}, 0)).isEmpty());

		// A translate of {0,0,0} moves nothing, so it is not "doing something" and a
		// record may carry one. A scale has no such identity — see
		// isAnOrdinaryStandingBody — so there is deliberately no equivalent case for it.
		assertFalse("a translate that does not translate is not 'doing something'",
			echoesOf(regions.recolouredButBusy(VARROCK_SOUTH, 3295, 3355,
				LivelyAnimation.HumanIdle, null, new float[]{0f, 0f, 0f}, 0)).isEmpty());
		assertTrue("but any authored scale is, because {0,0,0} would collapse the figure "
				+ "to a point rather than leave it alone",
			echoesOf(regions.recolouredButBusy(VARROCK_SOUTH, 3305, 3355,
				LivelyAnimation.HumanIdle, new float[]{0f, 0f, 0f}, null, 0)).isEmpty());
	}

	/**
	 * The same rule over the shipped dataset: <b>no echo that ships inherits a body
	 * doing anything</b>.
	 *
	 * <p>Checked against the source rather than against the echo, because the echo is
	 * where the inheritance has already happened — asking the echo would be asking the
	 * code under test to mark its own work. Both are asked anyway, since
	 * {@code echoOf} is what actually copies the fields and a version of it that
	 * dropped the idle animation would satisfy the source check and change what
	 * renders.
	 */
	@Test
	public void noShippedEchoInheritsABodyThatIsDoingSomething()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		List<String> violations = new ArrayList<>();
		int checked = 0;

		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull(source);
			checked++;

			if (!CitizenEcho.isAnOrdinaryStandingBody(source))
			{
				violations.add(describe(echo) + " came from " + source.label()
					+ ", whose body is doing something");
			}

			if (!CitizenEcho.isAnOrdinaryStandingBody(echo))
			{
				violations.add(describe(echo) + " is itself doing something, idle="
					+ echo.getIdleAnimation());
			}
		}

		assertTrue("echo(es) with a body an anonymous passer-by cannot have: " + violations,
			violations.isEmpty());
		assertEquals("the whole shipped echo roster has to have been asked", 46, checked);
	}

	/**
	 * <b>And the rule bites on the figures that were photographed.</b>
	 *
	 * <p>A gate that refused nothing would satisfy the two tests above. Each name below
	 * is a shipped citizen that seeded echoes before this rule and seeds none now, and
	 * each is named for the clause that refuses it — a fisherman miming a rod, a smith
	 * swinging at an anvil, a woodcutter with an axe, a butler sitting on a chair, a
	 * dwarf nudged off his tile to stand at a workbench, and a chicken carrying a scale
	 * vector so that it is not rendered at human size.
	 *
	 * <p><b>{@code mergedObjects} has no name here, and that is a fact about the
	 * dataset rather than a gap.</b> The two shipped records that carry one — "Morten"
	 * and "Jofridr", each with a bench welded in — are also posed {@code Sitting}, so no
	 * shipped citizen is refused by the merged-object clause <i>alone</i> and this test
	 * cannot witness it. {@code aCitizenWhoseBodyIsDoingSomethingSeedsNothing} covers it
	 * on a fixture built for the purpose, which is what a fixture is for.
	 */
	@Test
	public void theBodyRuleRefusesTheFiguresItWasWrittenFor()
	{
		Set<String> refused = new TreeSet<>();
		for (EntityDefinition citizen : shippedEntities())
		{
			if (citizen.getType().isCitizen() && !CitizenEcho.isAnOrdinaryStandingBody(citizen))
			{
				refused.add(citizen.getName());
			}
		}

		for (String who : Arrays.asList(
			"Fisherman", "Smithing apprentice", "Benny", "Butler Jarvis", "Stonehand", "Chicken"))
		{
			assertTrue(who + " has to be refused for its body", refused.contains(who));
			assertTrue(who + " has to seed nothing", echoesOfShipped(who).isEmpty());
		}

		// Counted as well as named, so that widening the rule until it refuses
		// everything is a failure rather than a stronger pass.
		int citizens = 0;
		int refusedCount = 0;
		for (EntityDefinition citizen : shippedEntities())
		{
			if (!citizen.getType().isCitizen())
			{
				continue;
			}
			citizens++;
			if (!CitizenEcho.isAnOrdinaryStandingBody(citizen))
			{
				refusedCount++;
			}
		}

		assertEquals("the citizen roster", 142, citizens);
		assertEquals("citizens whose body is doing something", 48, refusedCount);
	}

	// --- The flesh rule -------------------------------------------------------

	/**
	 * <b>The guarantee, stated with no threshold in it: no deal this class issues
	 * ever moves a colour across the flesh boundary.</b>
	 *
	 * <p>This is what {@link CitizenEcho#keepsEachColourOnItsOwnSideOfTheSkin} is
	 * for and it is the whole of the "passers-by with no trousers" fix. {@code find}
	 * is handed to an echo verbatim and only {@code replace} is rotated, so slot
	 * {@code i} of an echo still recolours the body region its author aimed it at —
	 * and the only thing that can go wrong is the <i>kind</i> of colour that lands
	 * there. A skin tone dealt onto the legs slot is bare legs. A tunic colour dealt
	 * onto the skin slot is a blue face.
	 *
	 * <p><b>Deliberately not a count of anything.</b> "How many echoes look wrong"
	 * depends on a threshold for how wrong, on which faces a {@code find} value
	 * actually paints, and therefore on a model decoder — three things nobody has to
	 * agree about for this assertion to be checkable. Either a surviving deal moves a
	 * colour across the boundary or it does not.
	 *
	 * <p>Asserted twice over: once about every deal {@code distinctDeals} would issue
	 * for every shipped palette, and once about the 46 echoes actually derived, which
	 * is the population that ships. The second is not implied by the first — it also
	 * covers {@code echoesOfSource} handing {@code redeal} the deal it said it would.
	 */
	@Test
	public void noSurvivingDealEverMovesAColourAcrossTheFleshBoundary()
	{
		List<String> violations = new ArrayList<>();
		int dealsChecked = 0;

		for (EntityDefinition citizen : shippedEntities())
		{
			short[] replace = citizen.getRecolorReplace();
			if (replace.length < 2)
			{
				continue;
			}

			for (int deal : CitizenEcho.distinctDeals(replace))
			{
				short[] dealt = CitizenEcho.redeal(replace, deal);
				dealsChecked++;
				for (int slot = 0; slot < replace.length; slot++)
				{
					if (CitizenEcho.isFlesh(dealt[slot]) != CitizenEcho.isFlesh(replace[slot]))
					{
						violations.add(citizen.label() + " deal " + deal + " puts " + dealt[slot]
							+ " where " + replace[slot] + " was, slot " + slot);
					}
				}
			}
		}

		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		int echoesChecked = 0;
		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull(source);

			short[] was = source.getRecolorReplace();
			short[] now = echo.getRecolorReplace();
			assertEquals("an echo wears its source's palette, so the arrays are the same length",
				was.length, now.length);

			echoesChecked++;
			for (int slot = 0; slot < was.length; slot++)
			{
				if (CitizenEcho.isFlesh(now[slot]) != CitizenEcho.isFlesh(was[slot]))
				{
					violations.add(describe(echo) + " wears " + now[slot] + " in the slot "
						+ source.label() + " painted " + was[slot]);
				}
			}
		}

		assertTrue("colour(s) dealt across the flesh boundary: " + violations, violations.isEmpty());

		// The sample guards. Both loops are "look for a counterexample", so an empty
		// dataset would satisfy them having asked nothing.
		assertTrue("the shipped palettes have to offer some deals to check: " + dealsChecked,
			dealsChecked > 50);
		assertEquals("and the whole shipped echo roster has to have been asked",
			46, echoesChecked);
	}

	/**
	 * <b>And the rule actually bites</b> — on the dataset that was photographed, in
	 * the way the photograph shows.
	 *
	 * <p>A rule that refused nothing would satisfy the test above perfectly. So the
	 * refusals are counted, and one of them is worked through by hand.
	 *
	 * <p>"Mary" in Draynor (region 12852) is the worked example, because she is not a
	 * hypothetical and not even a near miss: <b>until 2026-08-30 her echo shipped, and
	 * it wore the game's own face colour on its legs.</b> Her {@code find} names the
	 * torso base ({@code 8741}), the legs base ({@code 25238}), the hair base
	 * ({@code 6798}) and {@code 43072}, which is the base the kit's arm and hand models
	 * carry on their cuffs. Her {@code replace} answers them with a grey, a dark brown,
	 * a red-brown and {@link CitizenEcho#PLAYER_SKIN_BASE} — and that last one is on the
	 * <i>arm</i> slot, where it means a bare forearm and is right.
	 *
	 * <p>Rotating by two moves it onto {@code 25238}. Two of her four colours are
	 * flesh-class ({@code 5532} is a dark leather brown), and they are the two that swap
	 * places, so the flesh-or-not rule saw a class-preserving rotation and allowed it.
	 * The third class — the face colour as its own kind — is what refuses it now, and
	 * refusing it is what takes her from one echo to none.
	 *
	 * <p>("Marta" in Falador used to stand here for the same purpose. She was repaletted
	 * on 2026-08-30, because unlike Mary she was <i>ours</i>: the top-up authored her by
	 * hand-rotating an upstream palette, which put {@code 4550} on her hair base at the
	 * default density rather than only at {@code CROWDED}. Her wardrobe is now four
	 * garment colours and she seeds echoes again, which is the right outcome and the
	 * reason she is no longer an example of this rule.)
	 */
	@Test
	public void theFleshRuleRefusesTheDealsThatProducedThePhotographs()
	{
		int refused = 0;
		int citizensLeftWithNothing = 0;
		EntityDefinition mary = null;

		for (EntityDefinition citizen : shippedEntities())
		{
			if (!citizen.getType().isCitizen())
			{
				// Scenery never seeds an echo, so its rotations are not a cost.
				continue;
			}

			short[] replace = citizen.getRecolorReplace();
			if (replace.length < 2)
			{
				continue;
			}

			if ("Mary".equals(citizen.getName()))
			{
				mary = citizen;
			}

			int distinctRotations = distinctRotationCount(replace);
			int allowed = CitizenEcho.distinctDeals(replace).length;
			refused += distinctRotations - allowed;
			if (distinctRotations > 0 && allowed == 0)
			{
				citizensLeftWithNothing++;
			}
		}

		assertEquals("distinct re-deals the palette rule refuses across the dataset — "
				+ "216 of the 342 the shipped citizen palettes admit",
			216, refused);
		assertEquals("citizens whose whole wardrobe is refused by it",
			56, citizensLeftWithNothing);

		assertNotNull("the worked example has to still be in the dataset", mary);
		assertArray("Mary's authored find slots",
			new short[]{8741, 25238, 6798, (short) 43072}, mary.getRecolorFind());
		assertArray("and the palette she answers them with",
			new short[]{322, 5532, 8099, 4550}, mary.getRecolorReplace());
		assertTrue("4550 is the game's base skin tone and has to read as flesh",
			CitizenEcho.isFlesh((short) 4550));
		assertTrue("and so does the dark leather brown that swaps places with it — which "
			+ "is why the flesh-or-not rule let this rotation through",
			CitizenEcho.isFlesh((short) 5532));

		// The rotation that shipped: 4550 off the arm slot and onto the legs base.
		short[] wasShipped = CitizenEcho.redeal(mary.getRecolorReplace(), 2);
		assertArray("the deal that produced the photograph",
			new short[]{8099, 4550, 322, 5532}, wasShipped);
		assertEquals("which paints the legs base", 25238, mary.getRecolorFind()[1] & 0xFFFF);

		assertEquals("so no rotation of her palette survives, and she seeds nothing",
			0, CitizenEcho.distinctDeals(mary.getRecolorReplace()).length);
		assertTrue("and she seeds nothing", echoesOf(mary).isEmpty());
	}

	/**
	 * The gamut itself, checked against the five colours it was derived from.
	 *
	 * <p>{@link CitizenEcho#isFlesh} says how the wedge was measured — 3,319
	 * {@code find = 4550} recolours decoded out of the 1.12.36 cache. What can be
	 * asserted inside this repository is the part of that reasoning the dataset
	 * carries with it: the five base colours of the game's own player kit, which are
	 * the {@code find} values the shipped records are authored against.
	 */
	@Test
	public void theFleshGamutSeparatesTheGamesOwnSkinBaseFromItsGarmentBases()
	{
		assertTrue("4550, the skin base, is the colour the wedge is centred on",
			CitizenEcho.isFlesh((short) 4550));
		assertFalse("8741, the torso base, is the hue a tunic is dyed in",
			CitizenEcho.isFlesh((short) 8741));
		assertFalse("25238, the legs base, is green and nowhere near",
			CitizenEcho.isFlesh((short) 25238));

		// Two the palette cannot separate, both of which fall the safe way — see
		// isFlesh. 4626 is the boots base and a dark leather boot really is the hue of
		// a dark complexion; 6798 is the hair base and dark brown hair likewise. Both
		// are therefore held still rather than dealt around. Neither costs anything in
		// the shipped data — 4626 is a replace value four times and 6798 never — but
		// the classification is what it is and is written down rather than special-cased.
		assertTrue("the boots base falls inside the wedge", CitizenEcho.isFlesh((short) 4626));
		assertTrue("and so does the hair base", CitizenEcho.isFlesh((short) 6798));

		// The two excluded saturation rungs, at the wedge's own hue.
		assertFalse("saturation 0 at hue 4 is grey, not tan",
			CitizenEcho.isFlesh((short) ((4 << 10) | (0 << 7) | 70)));
		assertFalse("saturation 7 at hue 4 is a costume orange",
			CitizenEcho.isFlesh((short) ((4 << 10) | (7 << 7) | 70)));
		assertTrue("but everything between them is in, at any lightness",
			CitizenEcho.isFlesh((short) ((4 << 10) | (1 << 7) | 0))
				&& CitizenEcho.isFlesh((short) ((4 << 10) | (6 << 7) | 127)));

		// The hue edges.
		assertFalse("hue 1 is red", CitizenEcho.isFlesh((short) ((1 << 10) | (3 << 7) | 70)));
		assertTrue(CitizenEcho.isFlesh((short) ((2 << 10) | (3 << 7) | 70)));
		assertTrue(CitizenEcho.isFlesh((short) ((7 << 10) | (3 << 7) | 70)));
		assertFalse("hue 8 is where the torso base lives",
			CitizenEcho.isFlesh((short) ((8 << 10) | (3 << 7) | 70)));
	}

	@Test
	public void sceneryNeverSeedsAnEcho()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("a second market stall two tiles from the first is not a livelier city",
			echoesOf(regions.scenery(VARROCK_SOUTH, 3225, 3355)).isEmpty());
	}

	@Test
	public void anEchoNeverSeedsAnEchoOfItsOwn()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> echoes =
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6));
		assertFalse(echoes.isEmpty());

		for (EntityDefinition echo : echoes)
		{
			assertTrue("a re-deal of a re-deal drifts away from the authored palette",
				echoesOf(echo).isEmpty());
		}
	}

	// --- Determinism ----------------------------------------------------------

	/**
	 * Two derivations from the same source have to agree on every field.
	 *
	 * <p>Field by field rather than "the lists are the same size": the region cache
	 * is rebuilt on every border crossing and every eviction, so this runs many
	 * times per session, and an echo that moved a tile or changed colour when its
	 * region was rebuilt would flicker exactly where a player is looking.
	 */
	@Test
	public void theSameSourceDerivesTheSameEchoesEveryTime()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 5);

		List<EntityDefinition> first = echoesOf(source);
		List<EntityDefinition> second = echoesOf(source);

		assertEquals(first.size(), second.size());
		assertFalse("the fixture has to actually produce echoes", first.isEmpty());

		for (int i = 0; i < first.size(); i++)
		{
			EntityDefinition a = first.get(i);
			EntityDefinition b = second.get(i);

			assertEquals("uuid", a.getUuid(), b.getUuid());
			assertEquals("tile", a.getWorldLocation(), b.getWorldLocation());
			assertEquals("facing", a.getOrientation(), b.getOrientation());
			assertEquals("name", a.getName(), b.getName());
			assertEquals("examine", a.getExamineText(), b.getExamineText());
			assertEquals("source", a.getEchoSourceUuid(), b.getEchoSourceUuid());
			assertEquals("region", a.getTileRegionId(), b.getTileRegionId());
			assertTrue("recolour find", Arrays.equals(a.getRecolorFind(), b.getRecolorFind()));
			assertTrue("recolour replace", Arrays.equals(a.getRecolorReplace(), b.getRecolorReplace()));
			assertEquals("stable hash", a.stableHash(), b.stableHash());
		}
	}

	/**
	 * <b>The same roster in a different order derives the same crowd.</b>
	 *
	 * <p>Once separation is judged across a whole region, two citizens can want the
	 * same tile and only the first one asked can have it — so whatever decides who is
	 * asked first decides where somebody stands. If that were the region file's own
	 * listing order, then re-exporting the dataset, or a loader that read the scenery
	 * roster before the citizen roster, would silently move people around. Sources are
	 * walked in uuid order instead, and this is what says so: the same nine citizens,
	 * listed forwards, backwards and rotated, produce the same echoes on the same tiles
	 * in the same order.
	 *
	 * <p>The fixture is the tight three-by-three block again, because in a crowd with
	 * room to spare every order gives the same answer and the test would pass on a
	 * coincidence.
	 */
	@Test
	public void theDerivationDoesNotDependOnTheOrderTheRosterIsListedIn()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> roster = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			roster.add(regions.recoloured(VARROCK_SOUTH, 3220 + (i % 3) * 3, 3350 + (i / 3) * 3, 6));
		}

		List<String> asListed = placements(CitizenEcho.echoesOfRegion(roster));

		List<EntityDefinition> backwards = new ArrayList<>(roster);
		Collections.reverse(backwards);

		List<EntityDefinition> rotated = new ArrayList<>(roster);
		Collections.rotate(rotated, 4);

		assertFalse("the fixture has to produce echoes", asListed.isEmpty());
		assertEquals("listing the roster backwards must not move anybody",
			asListed, placements(CitizenEcho.echoesOfRegion(backwards)));
		assertEquals("nor must rotating it",
			asListed, placements(CitizenEcho.echoesOfRegion(rotated)));
	}

	/**
	 * An anchor value for the uuid derivation, so it cannot change by accident.
	 *
	 * <p>Same job as {@code CrowdDensityTest.theHashIsPinned..}: the uuid is what
	 * {@link CitizenOverrides} writes into the user's profile when they hide an echo,
	 * so a "harmless" tweak to the derivation silently discards every hide anybody
	 * had set on an echo, and swaps which stranger is hidden. Changing this number
	 * deliberately is fine; changing it without noticing is what this catches.
	 */
	@Test
	public void theEchoUuidDerivationIsPinned()
	{
		UUID source = UUID.fromString("44444444-4444-4444-8444-444444444444");

		assertEquals(UUID.fromString("663c3dc2-2e38-d721-b7b9-44c9f279864d"),
			CitizenEcho.echoUuid(source, 0));
		assertEquals(UUID.fromString("5d01ffe4-1f40-7d93-9091-4d172f2dd35c"),
			CitizenEcho.echoUuid(source, 1));
	}

	@Test
	public void everyEchoIndexGetsItsOwnUuid()
	{
		UUID source = UUID.fromString("44444444-4444-4444-8444-444444444444");

		assertNotEquals(CitizenEcho.echoUuid(source, 0), CitizenEcho.echoUuid(source, 1));
		assertNotEquals("and neither may be the source's own",
			source, CitizenEcho.echoUuid(source, 0));
		assertNotEquals(source, CitizenEcho.echoUuid(source, 1));
	}

	// --- Not twins ------------------------------------------------------------

	@Test
	public void anEchoIsNeverWithinTheMinimumDistanceOfItsSourceOrOfItsSibling()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);
		List<EntityDefinition> echoes = echoesOf(source);
		assertEquals(2, echoes.size());

		for (EntityDefinition echo : echoes)
		{
			assertTrue("echo at " + echo.getWorldLocation() + " is too close to its source",
				RenderPolicy.tileDistance(source.getWorldLocation(), echo.getWorldLocation())
					>= CitizenEcho.MIN_SEPARATION_TILES);
		}

		assertTrue("two echoes of one citizen must not stand on top of each other",
			RenderPolicy.tileDistance(
				echoes.get(0).getWorldLocation(), echoes.get(1).getWorldLocation())
				>= CitizenEcho.MIN_SEPARATION_TILES);
	}

	/**
	 * <b>And never within the minimum distance of anybody else's citizen, or anybody
	 * else's echo.</b>
	 *
	 * <p>The test above is the old rule: an echo against its own source and its own
	 * sibling. This is the rule as it is actually written down — an echo against
	 * everything the plugin renders in its region — and the fixture is deliberately
	 * tight enough to tell them apart. A three-by-three block of citizens three tiles
	 * apart leaves no tile in its interior more than one tile from somebody, so every
	 * candidate ring runs straight through a neighbour: a derivation that could only
	 * see one lineage would put echoes on top of strangers here, exactly as it did
	 * across the shipped files, where it produced 41 such pairs.
	 *
	 * <p>The shortfall is asserted too. A crowd this tight cannot house two echoes per
	 * citizen, and if it could, the fixture would not be exercising contention at all
	 * — it would be nine separate one-citizen fixtures standing near each other.
	 */
	@Test
	public void anEchoKeepsItsDistanceFromTheWholeRegionAndNotJustItsOwnLineage()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> roster = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			roster.add(regions.recoloured(VARROCK_SOUTH, 3220 + (i % 3) * 3, 3350 + (i / 3) * 3, 6));
		}

		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(roster);
		assertFalse("the fixture has to produce echoes", echoes.isEmpty());

		List<EntityDefinition> rendered = new ArrayList<>(roster);
		rendered.addAll(echoes);

		for (int i = 0; i < rendered.size(); i++)
		{
			for (int j = i + 1; j < rendered.size(); j++)
			{
				EntityDefinition a = rendered.get(i);
				EntityDefinition b = rendered.get(j);
				if (!a.isEcho() && !b.isEcho())
				{
					// Authored against authored is the fixture's own business.
					continue;
				}

				assertTrue(describe(a) + " at " + a.getWorldLocation() + " and " + describe(b)
						+ " at " + b.getWorldLocation() + " are too close",
					RenderPolicy.tileDistance(a.getWorldLocation(), b.getWorldLocation())
						>= CitizenEcho.MIN_SEPARATION_TILES);
			}
		}

		assertTrue("a row this tight has to cost somebody an echo, or the fixture is not "
				+ "exercising contention: got " + echoes.size() + " of "
				+ roster.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			echoes.size() < roster.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN);
	}

	/**
	 * <b>A floor is not a gap, but a floor between two people is.</b> The claimed-tile
	 * set is keyed on the plane as well as the coordinates, so a crowd upstairs cannot
	 * take the tiles a crowd downstairs needs.
	 *
	 * <p>{@link RenderPolicy#tileDistance} ignores the plane — it is the render
	 * distance metric, where a citizen one storey up is still a citizen a few tiles
	 * away — so this is the one place the plane has to be put back. The fixture stacks
	 * two identical nine-citizen blocks one storey apart, both tight enough to be
	 * fighting over tiles within their own storey; if the storeys were pooled they
	 * would be fighting over the same ones and the total would drop.
	 */
	@Test
	public void aCrowdUpstairsDoesNotTakeTheTilesTheCrowdDownstairsNeeds()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> groundFloor = new ArrayList<>();
		List<EntityDefinition> upstairs = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			int x = 3220 + (i % 3) * 3;
			int y = 3350 + (i / 3) * 3;
			groundFloor.add(regions.recoloured(VARROCK_SOUTH, x, y, 0, 6));
			upstairs.add(regions.recoloured(VARROCK_SOUTH, x, y, 1, 6));
		}

		List<EntityDefinition> groundAlone = CitizenEcho.echoesOfRegion(groundFloor);
		List<EntityDefinition> upstairsAlone = CitizenEcho.echoesOfRegion(upstairs);
		assertFalse("the fixture has to produce echoes", groundAlone.isEmpty());
		assertTrue("and each storey has to be crowded enough to be short of tiles on its own, "
				+ "or pooling the two would cost nothing and this would pass either way",
			groundAlone.size() < groundFloor.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN
				&& upstairsAlone.size() < upstairs.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN);

		List<EntityDefinition> both = new ArrayList<>(groundFloor);
		both.addAll(upstairs);
		List<EntityDefinition> stacked = CitizenEcho.echoesOfRegion(both);

		assertEquals("the ground floor's echoes must be exactly where they were when it was "
				+ "the only storey in the region",
			placements(groundAlone), placements(onPlane(stacked, 0)));
		assertEquals("and so must the first floor's",
			placements(upstairsAlone), placements(onPlane(stacked, 1)));
		assertEquals("with nobody else anywhere",
			groundAlone.size() + upstairsAlone.size(), stacked.size());
	}

	/**
	 * Each echo as {@code uuid@tile facing n}, in the order it was derived — so a
	 * comparison of two derivations names the field that moved rather than saying
	 * "not equal".
	 */
	private static List<String> placements(List<EntityDefinition> echoes)
	{
		List<String> out = new ArrayList<>(echoes.size());
		for (EntityDefinition echo : echoes)
		{
			out.add(echo.getUuid() + "@" + echo.getWorldLocation() + " facing " + echo.getOrientation());
		}
		return out;
	}

	private static List<EntityDefinition> onPlane(List<EntityDefinition> entities, int plane)
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (EntityDefinition entity : entities)
		{
			if (entity.getPlane() == plane)
			{
				out.add(entity);
			}
		}
		return out;
	}

	/**
	 * The recolour is the source's own palette, re-dealt — not a new palette and not
	 * the same one.
	 *
	 * <p>Three separate claims, and each is a different way of getting it wrong: the
	 * {@code find} slots have to be untouched (a different find array would recolour
	 * parts of the model the author never meant to touch), the {@code replace} values
	 * have to be exactly the source's own multiset (anything else is an invented
	 * colour), and the mapping has to actually differ (or it is a twin).
	 */
	@Test
	public void anEchoWearsItsSourcesOwnColoursInADifferentOrder()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 4);
		List<EntityDefinition> echoes = echoesOf(source);
		assertEquals(2, echoes.size());

		short[] sourceFind = source.getRecolorFind();
		short[] sourceReplace = source.getRecolorReplace();

		for (EntityDefinition echo : echoes)
		{
			assertTrue("the slots being recoloured must be the source's own",
				Arrays.equals(sourceFind, echo.getRecolorFind()));

			short[] sorted = echo.getRecolorReplace().clone();
			short[] sourceSorted = sourceReplace.clone();
			Arrays.sort(sorted);
			Arrays.sort(sourceSorted);
			assertTrue("every colour must be one the source already wore",
				Arrays.equals(sourceSorted, sorted));

			assertFalse("but not in the same order, or it is the source standing next to itself",
				Arrays.equals(sourceReplace, echo.getRecolorReplace()));
		}

		assertFalse("and two echoes of one citizen must differ from each other too",
			Arrays.equals(echoes.get(0).getRecolorReplace(), echoes.get(1).getRecolorReplace()));
	}

	@Test
	public void reDealingRotatesThePaletteAndLeavesTheOriginalAlone()
	{
		short[] palette = {10, 20, 30};

		assertArray(new short[]{20, 30, 10}, CitizenEcho.redeal(palette, 1));
		assertArray(new short[]{30, 10, 20}, CitizenEcho.redeal(palette, 2));
		assertArray("the source's own array must not be touched", new short[]{10, 20, 30}, palette);
	}

	/**
	 * Two echoes of one citizen must not stand shoulder to shoulder facing the same
	 * way — the "twins" tell that survives a recolour. The facing comes from each
	 * echo's own uuid rather than from the source's, which is what makes them differ.
	 */
	@Test
	public void anEchoFacesOneOfTheEightCardinalDirections()
	{
		FakeRegions regions = new FakeRegions();
		Set<Integer> facings = new HashSet<>();

		for (int i = 0; i < 30; i++)
		{
			for (EntityDefinition echo :
				echoesOf(regions.recoloured(VARROCK_SOUTH, 3210 + i * 3, 3340, 6)))
			{
				int facing = echo.getOrientation();
				assertEquals("a facing off the eight looks like a rendering fault, not a person",
					0, facing % 256);
				assertTrue("orientation must be inside 0..2047", facing >= 0 && facing < 2048);
				facings.add(facing);
			}
		}

		assertTrue("the derivation has to actually spread across the eight, not pick one: " + facings,
			facings.size() >= 6);
	}

	// --- Identity -------------------------------------------------------------

	@Test
	public void anEchoCarriesNeitherItsSourcesNameNorItsExamineText()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 5);
		List<EntityDefinition> echoes = echoesOf(source);
		assertFalse(echoes.isEmpty());

		for (EntityDefinition echo : echoes)
		{
			assertEquals(CitizenEcho.ECHO_NAME, echo.getName());
			assertNotEquals(source.getName(), echo.getName());
			assertNotEquals(source.getExamineText(), echo.getExamineText());
			assertEquals(CitizenEcho.ECHO_EXAMINE_TEXT, echo.getExamineText());
			assertNotEquals("and its own identity is its own", source.getUuid(), echo.getUuid());
			assertEquals("while still knowing where it came from",
				source.getUuid(), echo.getEchoSourceUuid());
			assertTrue(echo.isEcho());
		}

		assertFalse("and the source is not an echo", source.isEcho());
		assertNull(source.getEchoSourceUuid());
	}

	/**
	 * Examine on an echo must not claim to be the source, and must say what the thing
	 * actually is.
	 *
	 * <p>Read through {@link CitizenLabel#examineMessage} rather than off the field,
	 * because the message is what a player sees and it is assembled from two halves —
	 * asserting only on {@code examineText} would miss a version that pasted the
	 * source's name into the coloured prefix.
	 */
	@Test
	public void examineOnAnEchoIsTruthfulAboutWhatItIs()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 5);
		EntityDefinition echo = echoesOf(source).get(0);

		String message = CitizenLabel.examineMessage(echo);

		assertFalse("it must not name the citizen it was derived from: " + message,
			message.contains(source.getName()));
		assertFalse("nor repeat that citizen's examine text: " + message,
			message.contains(source.getExamineText()));
		assertTrue("it has to name what it is: " + message, message.contains(CitizenEcho.ECHO_NAME));
		assertTrue("and say which setting put it there: " + message, message.contains("Crowded"));
		assertTrue("on top of the plugin's own disclaimer: " + message,
			message.contains(CitizenLabel.PLUGIN_NAME));
		assertTrue(message.contains("not a real NPC"));
	}

	// --- What an echo is not --------------------------------------------------

	@Test
	public void anEchoNeitherWandersNorHasAnythingToSay()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition wanderer = regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			6);
		assertNotNull("the source itself does wander", wanderer.getWanderBox());

		List<EntityDefinition> echoes = echoesOf(wanderer);
		assertEquals(2, echoes.size());

		for (EntityDefinition echo : echoes)
		{
			assertNull("an echo's ground is proved at one tile, so it stays on it",
				echo.getWanderBox());
			assertNull("and CitizenWalk therefore builds nothing for it",
				CitizenWalk.forDefinition(echo));
			assertEquals("a WanderingCitizen that stands still would be a lie in every log line",
				EntityType.StationaryCitizen, echo.getType());

			assertEquals("an echo has no authored line, so it has none at all",
				0, echo.getRemarks().length);
			assertNull("and therefore no remark object for the chatter to drive",
				CitizenRemarks.forDefinition(echo));
		}
	}

	/**
	 * An echo of a citizen that <i>does</i> have something to say still says nothing.
	 *
	 * <p>Separate from the test above, and it is the one that earns its place: making
	 * an echo carry its source's {@code remarks} array left the whole suite green
	 * until this fixture existed, because no other fixture had a source with both a
	 * palette and a line to speak. 24 of the 142 shipped citizens have both, so the
	 * gap was in the fixtures rather than in the dataset.
	 *
	 * <p>(That sentence used to read "34 of the 109", which was the count of citizens
	 * carrying <i>remarks</i> rather than the intersection it claimed to be — the real
	 * figure at the time was 14. Corrected in the top-up pass on 2026-08-29 rather than
	 * merely rescaled.)
	 */
	@Test
	public void anEchoOfATalkativeCitizenStillHasNothingToSay()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recolouredTalker(
			VARROCK_SOUTH, 3225, 3355, 5, "Busy today.", "Lovely weather.");
		assertEquals("the source itself has two lines", 2, source.getRemarks().length);
		assertNotNull(CitizenRemarks.forDefinition(source));

		List<EntityDefinition> echoes = echoesOf(source);
		assertEquals(2, echoes.size());

		for (EntityDefinition echo : echoes)
		{
			assertEquals("putting an authored line in a second mouth makes it ambient noise",
				0, echo.getRemarks().length);
			assertNull(CitizenRemarks.forDefinition(echo));
		}
	}

	@Test
	public void anEchoInheritsItsSourcesBodyAndIdleAnimationButNotItsWalkAnimation()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition wanderer = regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			4);
		EntityDefinition echo = echoesOf(wanderer).get(0);

		assertTrue("same body", Arrays.equals(wanderer.getModelIds(), echo.getModelIds()));
		assertEquals(wanderer.getIdleAnimation(), echo.getIdleAnimation());
		assertNull("nothing moves, so there is nothing to play a walk cycle for",
			echo.getMoveAnimation());
		assertEquals("and it stays in the file its source was filed under",
			wanderer.getRegionId(), echo.getRegionId());
	}

	// --- Placement ------------------------------------------------------------

	@Test
	public void anEchoOfAWandererStandsOnItsSourcesOwnAuthoredGround()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition wanderer = regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			6);
		EntityDefinition.WanderBox box = wanderer.getWanderBox();
		assertNotNull(box);

		for (EntityDefinition echo : echoesOf(wanderer))
		{
			WorldPoint tile = echo.getWorldLocation();
			assertTrue("a box tile is ground a human already decided a citizen could pace: " + tile,
				box.contains(tile.getX(), tile.getY()));
			assertEquals("and on the same storey", wanderer.getPlane(), echo.getPlane());
			assertTrue("so it is allowed through even with no collision map at all",
				echo.isEchoOnAuthoredGround());
		}
	}

	@Test
	public void anEchoOfAStationaryCitizenStandsOnADerivedOffsetAndSaysSo()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);

		for (EntityDefinition echo : echoesOf(source))
		{
			assertFalse("nobody has vouched for an offset, so the collision map must",
				echo.isEchoOnAuthoredGround());
			assertEquals(CitizenEcho.MIN_SEPARATION_TILES,
				RenderPolicy.tileDistance(source.getWorldLocation(), echo.getWorldLocation()));
			assertEquals(source.getPlane(), echo.getPlane());
		}
	}

	/**
	 * The placement gate: standable passes, blocked is skipped, and unknown falls
	 * back to whether a human vouched for the ground.
	 */
	@Test
	public void placementFollowsTheCollisionMapAndFallsBackToAuthoredGround()
	{
		FakeRegions regions = new FakeRegions();
		WorldPoint player = new WorldPoint(3225, 3360, 0);

		EntityDefinition offsetEcho =
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6)).get(0);
		EntityDefinition boxEcho = echoesOf(regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3235, 3355, 0),
			new WorldPoint(3228, 3348, 0),
			new WorldPoint(3242, 3362, 0),
			6)).get(0);

		FakeWorldView open = FakeWorldView.around(player, VARROCK_SOUTH);
		assertTrue(CitizenEcho.isPlaceable(open, offsetEcho));
		assertTrue(CitizenEcho.isPlaceable(open, boxEcho));

		FakeWorldView blocked = FakeWorldView.around(player, VARROCK_SOUTH);
		blocked.block(offsetEcho.getWorldLocation());
		blocked.block(boxEcho.getWorldLocation());
		assertFalse("a blocked tile is skipped, whatever it was derived from",
			CitizenEcho.isPlaceable(blocked, offsetEcho));
		assertFalse("authored ground does not override the collision map",
			CitizenEcho.isPlaceable(blocked, boxEcho));

		FakeWorldView unknown = FakeWorldView.around(player, VARROCK_SOUTH).withoutCollisionData();
		assertFalse("with no answer, an offset is not admitted",
			CitizenEcho.isPlaceable(unknown, offsetEcho));
		assertTrue("but a tile inside an authored wander box is",
			CitizenEcho.isPlaceable(unknown, boxEcho));
	}

	@Test
	public void anAuthoredEntityIsAlwaysPlaceableAndNeverConsultsTheCollisionMap()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition authored = regions.citizen(VARROCK_SOUTH, 3225, 3355, 0);

		// StubWorldView throws on every method the render core has never needed, so a
		// version that asked about an authored entity's tile would blow up here rather
		// than quietly cost a scene lookup per authored citizen per tick.
		assertTrue(CitizenEcho.isPlaceable(new StubWorldView(), authored));
		assertTrue(CitizenEcho.isPlaceable(null, authored));
	}

	// --- The shipped dataset --------------------------------------------------

	/**
	 * The headline claim, recomputed from the shipped files.
	 *
	 * <p>142 authored citizens and 46 echoes, i.e. 188 in total, 1.32×.
	 *
	 * <p><b>This test used to assert a ratio in the band {@code [1.8, 2.4]}</b>, and
	 * that band was the wrong shape of claim in two ways at once. It was a floor under
	 * a number nobody should want a floor under — "at least 1.8× as many people as a
	 * human placed" is a target the derivation can only hit by relaxing whatever is
	 * stopping it — and it was global, so a city could be four-fifths made-up people
	 * as long as the whole dataset averaged out. The flesh rule
	 * ({@link CitizenEcho#isFlesh}) took the real figure to 1.51× and the band went
	 * red, which is the band doing the only useful thing it ever did.
	 *
	 * <p>What replaces it is a <b>ceiling, per city</b>: no city's rendered crowd may
	 * be more than half derived. That is a claim about what a player standing in
	 * Draynor actually sees, it cannot be satisfied by deriving more, and it is the
	 * one the photographs were evidence against — "Draynor full of near-identical
	 * figures doing nothing" is a statement about Draynor's own mix, not about a
	 * dataset-wide average. Draynor, which is the city that was photographed, is also
	 * the worst in the shipped data: 7 echoes against 10 authored, i.e. 41%. Two cities
	 * — Ardougne and Lumbridge — are now 0%, which is worth knowing when the
	 * keep-or-retire question about {@code CROWDED} is next argued.
	 *
	 * <p>The floor that is kept is the honest one: {@code CROWDED} has to add
	 * <i>something</i>, or it is a setting that does nothing.
	 *
	 * <p><b>Why 44 and not fewer.</b> Every echo the 24 seeds ask for finds somewhere
	 * legal to stand. That was not true before the flesh rule — the "Mysterious Old
	 * Man" in Varrock got one echo instead of two, because every tile near him was
	 * within {@link CitizenEcho#MIN_SEPARATION_TILES} of somebody — and it is true now
	 * for the dullest of reasons: there are fewer echoes competing for the same
	 * ground. The placement rule is untouched.
	 *
	 * <p><b>Why the seed count is one short of the citizens that reach the palette
	 * check with a usable wardrobe.</b> "Rufus" in Varrock square used to seed two
	 * echoes and now seeds none: he is dressed from an {@code npcAppearanceId} (GitHub
	 * issue #1 — his authored {@code modelIds} carried no footwear), and a source whose
	 * colours come from a composition rather than from its own record has no palette to
	 * re-deal, so {@code CitizenEcho} refuses it. That is the price of the fix: two
	 * ambient bodies at {@code CROWDED}, against a citizen who is no longer barefoot.
	 */
	@Test
	public void theShippedRosterIsHalfAgainAsBigUnderCrowded()
	{
		int citizens = 0;
		Map<City, Integer> authoredPerCity = new EnumMap<>(City.class);
		for (EntityDefinition entity : shippedEntities())
		{
			if (entity.getType().isCitizen())
			{
				citizens++;
				authoredPerCity.merge(City.of(entity.getCityRegionId()), 1, Integer::sum);
			}
		}

		int echoes = 0;
		int fromBoxes = 0;
		Set<UUID> seeds = new HashSet<>();
		Map<City, Integer> echoesPerCity = new EnumMap<>(City.class);
		for (EntityDefinition echo : shippedEchoes())
		{
			echoes++;
			if (echo.isEchoOnAuthoredGround())
			{
				fromBoxes++;
			}
			seeds.add(echo.getEchoSourceUuid());
			echoesPerCity.merge(City.of(echo.getCityRegionId()), 1, Integer::sum);
		}

		assertEquals("the authored citizen roster", 142, citizens);
		assertEquals("citizens that seeded at least one echo", 24, seeds.size());
		assertEquals("echoes derived from them", 46, echoes);
		assertEquals("of which this many stand inside an authored wander box", 27, fromBoxes);
		assertEquals("the rest stand on a derived offset the collision map has to vouch for",
			19, echoes - fromBoxes);

		// The floor, stated as the two rosters rather than as `echoes >= 0`, which is
		// what this line used to say: `echoes` is a counter that is only ever
		// incremented, so the old form could not go red whatever the derivation did.
		int atFull = citizens;
		int atCrowded = citizens + echoes;
		assertTrue("CROWDED must never yield fewer citizens than FULL: " + atCrowded
				+ " against " + atFull,
			atCrowded > atFull);

		// The ceiling, per city, which is the claim that means something at 1.32×.
		// Grouped by the region whose checkbox governs the entity — an echo answers to
		// its source's city, not to the tile it stands on — so this is the mix a player
		// who has that one city ticked would be standing in.
		// A city for every entity counted, asserted where it can actually fail. This
		// used to be `echoesPerCity.containsKey(null)`, which is dead twice over: an
		// EnumMap returns false for a null key unconditionally, and `merge(null, ...)`
		// thirty lines above would have thrown a NullPointerException before this line
		// was reached. Counted in the same passes as the maps instead.
		int cityless = 0;
		for (EntityDefinition entity : shippedEntities())
		{
			if (entity.getType().isCitizen() && City.of(entity.getCityRegionId()) == null)
			{
				cityless++;
			}
		}
		for (EntityDefinition echo : shippedEchoes())
		{
			if (City.of(echo.getCityRegionId()) == null)
			{
				cityless++;
			}
		}
		assertEquals("every entity the mix is measured over has to belong to a city, or "
			+ "the shares below are measured against nothing", 0, cityless);

		String worst = "";
		double worstShare = 0;
		for (Map.Entry<City, Integer> city : echoesPerCity.entrySet())
		{
			int authored = authoredPerCity.getOrDefault(city.getKey(), 0);
			int derived = city.getValue();
			double share = derived / (double) (authored + derived);
			if (share > worstShare)
			{
				worstShare = share;
				worst = city.getKey() + ": " + derived + " derived against "
					+ authored + " authored";
			}
		}

		assertTrue("no city may be more than half made-up people; worst was " + worst
				+ " = " + String.format("%.1f%%", worstShare * 100),
			worstShare <= 0.5);

		// Pinned as well as bounded, so that a data change which moved it says so
		// instead of drifting quietly upwards inside the ceiling.
		assertEquals("the worst city's derived share", 0.4444, worstShare, 0.0001);

		// There used to be a `total/authored` assertion here pinning the ratio to four
		// decimal places. It could not fail: `citizens` and `echoes` are both pinned
		// literals a few lines up, so the ratio was arithmetic on two numbers this test
		// had already asserted, and the only way to move it was to break one of them
		// first. The ratio is prose — it belongs in CitizenEcho's javadoc, where it is —
		// and a test that restates a figure it has just pinned is a test that looks like
		// two guards and is one.
	}

	/**
	 * Why the other 118 citizens seed nothing, gate by gate, and the fact that the
	 * gates partition the roster.
	 *
	 * <p>{@link #theShippedRosterIsHalfAgainAsBigUnderCrowded()} pins the ones that do
	 * seed; every other citizen in the roster lands at exactly one of the gates below.
	 * The counts for them are written into {@link CitizenEcho}'s javadoc and into the
	 * comment beside each {@code return NONE} — "22 of the 87 shipped citizens that
	 * reach this line land here" and its siblings — and nothing was checking them, so
	 * they went on saying 49 of 128 after the dataset was cut to 109 citizens. Numbers
	 * that have to sum to a total is the cheapest possible guard, and this is it.
	 *
	 * <p>The number in the first line is the one the last assertion in this method
	 * makes, and it is written that way because the previous version was not: it said
	 * "the other 46" while the code below asserted 118, and survived a review because a
	 * figure in a sentence has nothing holding it.
	 *
	 * <p><b>The last {@code return NONE} covers two different facts and they are split
	 * here.</b> {@code distinctDeals} returns nothing both for a palette that replaces
	 * every slot with the same colour (1 citizen that reaches it — every rotation is the
	 * identity) and for one whose every rotation would carry a colour across the flesh
	 * boundary (40).
	 * Rolled into one number those would hide each other: the flesh rule could stop
	 * working entirely and the total would still add up as long as the identity cases
	 * absorbed the difference.
	 *
	 * <p>The order below is {@code echoesOfSource}'s own order, because the counts
	 * are only meaningful in it: a cameo is refused before its palette is ever
	 * looked at, so "citizens with fewer than two recolour pairs" and "citizens that
	 * reach the palette check with fewer than two" are different numbers.
	 */
	@Test
	public void everyCitizenTheDerivationRefusesIsRefusedAtExactlyOneGate()
	{
		int cameos = 0;
		int dressedFromAnNpc = 0;
		int bodyIsDoingSomething = 0;
		int reachThePaletteCheck = 0;
		int tooFewPairs = 0;
		int everyRedealIsTheSame = 0;
		int everyRedealCrossesTheSkin = 0;
		int seeds = 0;

		for (EntityDefinition citizen : shippedEntities())
		{
			if (!citizen.getType().isCitizen())
			{
				continue;
			}

			if (citizen.isCameo())
			{
				cameos++;
				continue;
			}

			if (citizen.getNpcAppearanceId() != 0)
			{
				dressedFromAnNpc++;
				continue;
			}

			if (!CitizenEcho.isAnOrdinaryStandingBody(citizen))
			{
				bodyIsDoingSomething++;
				continue;
			}

			reachThePaletteCheck++;

			if (citizen.getRecolorFind().length < 2 || citizen.getRecolorReplace().length < 2)
			{
				tooFewPairs++;
				continue;
			}

			if (CitizenEcho.distinctDeals(citizen.getRecolorReplace()).length == 0)
			{
				if (everyRotationIsTheIdentity(citizen.getRecolorReplace()))
				{
					everyRedealIsTheSame++;
				}
				else
				{
					everyRedealCrossesTheSkin++;
				}
				continue;
			}

			seeds++;
		}

		assertEquals("cameos, refused before anything else is asked", 6, cameos);
		assertEquals("dressed from a composition, so there is no palette to re-deal",
			1, dressedFromAnNpc);
		assertEquals("sitting on something, miming a tool, welded to a bench, scaled or "
				+ "nudged — a body an anonymous passer-by cannot have", 48, bodyIsDoingSomething);
		assertEquals("citizens that get as far as the palette check", 87, reachThePaletteCheck);
		assertEquals("of those, the ones with no second slot to deal into", 22, tooFewPairs);
		assertEquals("and the ones whose every re-deal is the deal it started with",
			1, everyRedealIsTheSame);
		assertEquals("and the ones whose every re-deal would move a colour across the "
				+ "flesh boundary", 40, everyRedealCrossesTheSkin);
		assertEquals("leaving the seeds", 24, seeds);

		assertEquals("the palette check sees everything the three gates above it let through",
			reachThePaletteCheck,
			tooFewPairs + everyRedealIsTheSame + everyRedealCrossesTheSkin + seeds);
		assertEquals("and the gates between them account for every citizen in the dataset "
				+ "exactly once — a set of counts that does not add up is the drift this "
				+ "test exists to catch",
			142, cameos + dressedFromAnNpc + bodyIsDoingSomething + reachThePaletteCheck);
		assertEquals("citizens that seed nothing", 22 + 1 + 40 + 48 + 6 + 1, 142 - seeds);
	}

	/**
	 * The two populations the ring of fallback tiles serves, counted from the shipped
	 * files.
	 *
	 * <p>{@code appendRingCandidates}'s javadoc names both — the citizens with no
	 * authored box at all, and the wanderers whose box cannot supply every echo they
	 * ask for — and both were stale. The second one cannot be counted from the record
	 * alone: whether a box can hold two well-separated echoes depends on who else is
	 * already standing in the region, so it is read back off the placement itself
	 * through {@link EntityDefinition#isEchoOnAuthoredGround()}.
	 */
	@Test
	public void theRingServesTheCitizensWithNoBoxAndTheWanderersWhoseBoxIsNotEnough()
	{
		int citizens = 0;
		int withNoBox = 0;
		for (EntityDefinition entity : shippedEntities())
		{
			if (!entity.getType().isCitizen())
			{
				continue;
			}

			citizens++;
			if (entity.getWanderBox() == null)
			{
				withNoBox++;
			}
		}

		assertEquals("the authored citizen roster", 142, citizens);
		assertEquals("citizens with no authored box, for whom the ring is the only source "
				+ "of candidate ground", 91, withNoBox);
		assertEquals("the rest carry one", 51, citizens - withNoBox);

		int toppedUpFromTheRing = 0;
		int echoesPushedOffABox = 0;
		List<String> where = new ArrayList<>();
		for (Map.Entry<Integer, List<EntityDefinition>> region : shippedRosters().entrySet())
		{
			List<EntityDefinition> roster = region.getValue();

			Map<UUID, EntityDefinition> byUuid = new HashMap<>();
			for (EntityDefinition entity : roster)
			{
				byUuid.put(entity.getUuid(), entity);
			}

			Map<UUID, Integer> fromTheBox = new HashMap<>();
			for (EntityDefinition echo : CitizenEcho.echoesOfRegion(roster))
			{
				if (echo.isEchoOnAuthoredGround())
				{
					fromTheBox.merge(echo.getEchoSourceUuid(), 1, Integer::sum);
					continue;
				}

				EntityDefinition source = byUuid.get(echo.getEchoSourceUuid());
				if (source != null && source.getWanderBox() != null)
				{
					echoesPushedOffABox++;
				}
			}

			for (EntityDefinition source : roster)
			{
				if (source.getWanderBox() == null || source.isCameo()
					|| source.getNpcAppearanceId() != 0
					|| !CitizenEcho.isAnOrdinaryStandingBody(source)
					|| source.getRecolorFind().length < 2
					|| source.getRecolorReplace().length < 2)
				{
					continue;
				}

				int[] deals = CitizenEcho.distinctDeals(source.getRecolorReplace());
				if (deals.length == 0)
				{
					continue;
				}

				int wanted = Math.min(deals.length, CitizenEcho.MAX_ECHOES_PER_CITIZEN);
				if (fromTheBox.getOrDefault(source.getUuid(), 0) < wanted)
				{
					toppedUpFromTheRing++;
					where.add(source.label() + " in " + region.getKey() + ".json");
				}
			}
		}

		assertEquals("wanderers whose own box could not supply every echo they asked for: "
				+ where, 2, toppedUpFromTheRing);

		// The same fact counted in echoes rather than in sources, because CitizenEcho's
		// "What that costs" paragraph quotes it in echoes and said "four" for as long as
		// nothing checked it. A wanderer that gets neither of its two echoes out of its
		// own box contributes two to this and one to the count above.
		assertEquals("echoes whose source carries a box but which stand on a ring offset "
				+ "instead, because no box tile was far enough from everything else",
			3, echoesPushedOffABox);
	}

	/**
	 * Every uuid in play — 142 authored citizens, 42 scenery records and 46 echoes —
	 * has to be distinct.
	 *
	 * <p>A collision would mean two entities the user cannot tell apart in the
	 * hidden/muted settings: hiding one would hide the other, permanently, and the
	 * derivation would be the reason. Asserted over the whole roster rather than
	 * argued from the mixer's properties.
	 */
	@Test
	public void everyDerivedUuidIsDistinctFromEveryOtherUuidInTheDataset()
	{
		Set<UUID> seen = new HashSet<>();
		List<String> clashes = new ArrayList<>();

		for (EntityDefinition entity : shippedEntities())
		{
			if (!seen.add(entity.getUuid()))
			{
				clashes.add("authored " + entity.label() + " " + entity.getUuid());
			}
		}

		for (EntityDefinition echo : shippedEchoes())
		{
			if (!seen.add(echo.getUuid()))
			{
				clashes.add("echo of " + echo.getEchoSourceUuid() + " " + echo.getUuid());
			}
		}

		assertTrue("uuid collision(s): " + clashes, clashes.isEmpty());
		assertEquals("184 authored entities plus 46 echoes", 230, seen.size());
	}

	/**
	 * <b>Nobody stands inside anybody.</b> Every pair of entities the plugin would
	 * render out of the shipped dataset, at the same storey, at least
	 * {@link CitizenEcho#MIN_SEPARATION_TILES} apart — authored citizens, scenery and
	 * echoes, all in one pot.
	 *
	 * <p>This is the widened form of a test that used to compare each echo against
	 * its own source and its own sibling and nothing else. That is not what the rule
	 * says, and the dataset proved it: judged one lineage at a time, the shipped files
	 * produced <b>41 pairs</b> closer than the minimum — 57 counted once per echo
	 * rather than once per pair — of which three were exact same-tile collisions and
	 * two of those were echo on echo. Twins standing in each other, produced by the
	 * rule against twins standing in each other. The fixture tests above cannot catch
	 * that class of fault at all, because a fixture with one citizen in it has no
	 * other lineage to collide with; only the real dataset does.
	 *
	 * <p><b>It walks the whole dataset rather than one region at a time</b>, so it is
	 * a wider net than {@link CitizenEcho#echoesOfRegion} casts: that method can only
	 * separate an echo from the region file it was derived with, and two entities
	 * either side of a region border are outside its reach. The shipped files have no
	 * such pair (they had none before this change either), so the net is empty rather
	 * than slack — and if a future region file ever creates one, this goes red and
	 * somebody has to decide what to do about it, which is the right outcome for a
	 * problem the derivation cannot see.
	 *
	 * <p><b>Authored against authored is excluded, and counted instead.</b> 42 pairs
	 * of hand-placed entities are closer than the minimum and eight of those share a
	 * tile exactly — a stall and its owner, two guards on a gate. A human chose those
	 * and this feature has no vote on them; what it must not do is add to them. The
	 * count is pinned so that "excluded" cannot quietly grow to cover a derived
	 * offender.
	 */
	@Test
	public void noTwoShippedRenderedEntitiesStandCloserThanTheMinimum()
	{
		List<EntityDefinition> rendered = new ArrayList<>(shippedEntities());
		int authoredCount = rendered.size();
		rendered.addAll(shippedEchoes());

		List<String> violations = new ArrayList<>();
		int authoredPairs = 0;
		int authoredCollisions = 0;

		for (int i = 0; i < rendered.size(); i++)
		{
			for (int j = i + 1; j < rendered.size(); j++)
			{
				EntityDefinition a = rendered.get(i);
				EntityDefinition b = rendered.get(j);

				if (a.getPlane() != b.getPlane())
				{
					// A different storey is not the same tile.
					continue;
				}

				int distance = RenderPolicy.tileDistance(a.getWorldLocation(), b.getWorldLocation());
				if (distance >= CitizenEcho.MIN_SEPARATION_TILES)
				{
					continue;
				}

				if (!a.isEcho() && !b.isEcho())
				{
					authoredPairs++;
					if (distance == 0)
					{
						authoredCollisions++;
					}
					continue;
				}

				violations.add(describe(a) + " and " + describe(b) + " are " + distance
					+ " tile(s) apart at " + a.getWorldLocation() + " / " + b.getWorldLocation());
			}
		}

		assertEquals("the fixture has to be the whole authored roster", 184, authoredCount);
		assertTrue("separation violation(s) involving a derived citizen: " + violations,
			violations.isEmpty());
		assertEquals("hand-placed entities closer than the minimum to each other, which is "
				+ "authored content and none of this feature's business", 42, authoredPairs);
		assertEquals("of which this many share a tile exactly", 8, authoredCollisions);
	}

	/**
	 * Every shipped echo wears its source's own colours, re-dealt.
	 *
	 * <p>The same three claims as the fixture test, over the 44 real echoes. What
	 * this adds is coverage of the dataset's awkward palettes — repeated colours,
	 * eleven-pair wardrobes — where a rotation can quietly come out as the identity.
	 */
	@Test
	public void everyShippedEchoWearsAReDealOfItsSourcesOwnPalette()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		List<String> violations = new ArrayList<>();
		Map<UUID, List<short[]>> palettesBySource = new HashMap<>();

		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull("every echo has to name a source in the same dataset", source);

			short[] sourceReplace = source.getRecolorReplace();
			short[] sourceSorted = sourceReplace.clone();
			Arrays.sort(sourceSorted);

			if (!Arrays.equals(source.getRecolorFind(), echo.getRecolorFind()))
			{
				violations.add(describe(echo) + " recolours different slots from " + source.label());
			}

			short[] sorted = echo.getRecolorReplace().clone();
			Arrays.sort(sorted);
			if (!Arrays.equals(sourceSorted, sorted))
			{
				violations.add(describe(echo) + " wears a colour " + source.label() + " never wore");
			}

			if (Arrays.equals(sourceReplace, echo.getRecolorReplace()))
			{
				violations.add(describe(echo) + " is dressed identically to " + source.label());
			}

			List<short[]> siblings =
				palettesBySource.computeIfAbsent(echo.getEchoSourceUuid(), k -> new ArrayList<>());
			for (short[] sibling : siblings)
			{
				if (Arrays.equals(sibling, echo.getRecolorReplace()))
				{
					violations.add(describe(echo) + " is dressed identically to its own sibling");
				}
			}
			siblings.add(echo.getRecolorReplace());
		}

		assertTrue("appearance violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * <b>Which checkbox switches an echo off is a question about its source, not about
	 * the tile it happens to stand on</b> — and the shipped dataset is what makes the
	 * difference visible.
	 *
	 * <p>{@link City#isEnabled} fails open for a region no city claims, on purpose, so
	 * that a region file can ship one commit before its checkbox does
	 * ({@code CityTest.aRegionNoCityClaimsIsStillShown}). Four shipped echoes used to
	 * stand a few tiles over a border in a region nobody claimed — three derived from
	 * Piscatoris citizens, one from a Camelot citizen — so judging an echo by its own
	 * tile sent all four through that door: unticking Piscatoris or Camelot left them
	 * standing in an empty village. Asked of the source's region instead, they answer
	 * to the checkbox that governs the citizen they are copies of.
	 *
	 * <p><b>Those four are gone, and the shipped data has no replacement for them.</b>
	 * The nine-city cut on 2026-08-24 removed both Piscatoris and Camelot, and all 44
	 * echoes the surviving 27 regions seed now stand in a region their own source's
	 * city claims. So the stray count below is asserted at zero and is no longer the
	 * interesting assertion in this method. The 33 citizens added on 2026-08-29 did not
	 * reintroduce the case: every one of them stands well inside its own region, so
	 * their echoes cannot reach a border.
	 *
	 * <p>What is still asserted over the real files, and is the claim that actually
	 * matters, is the rule itself: <b>every one of the 46 echoes takes its governing
	 * region from its source's tile, not from its own.</b> That is checked echo by
	 * echo below and cannot go green by there being nothing to check. The two
	 * behavioural halves the strays used to demonstrate are checked directly instead —
	 * that the fail-open door is still open for an unclaimed region, and that unticking
	 * a real city really does switch off an echo derived from it — so no claim was
	 * dropped, only the geometry that used to make them vivid.
	 * {@code CrowdedSceneTest.untickingACityAlsoRemovesTheEchoesStandingInARegionNoCityClaims}
	 * keeps the stray case alive on a hand-built fixture.
	 */
	@Test
	public void everyShippedEchoIsGovernedByItsSourcesCityRatherThanByTheTileItStandsOn()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		List<EntityDefinition> strays = new ArrayList<>();
		Map<String, Integer> strayRegions = new TreeMap<>();
		int checked = 0;

		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull(source);

			assertEquals("an echo answers to whatever governs the citizen it came from",
				source.getTileRegionId(), echo.getCityRegionId());
			checked++;

			if (City.of(echo.getTileRegionId()) != City.of(source.getTileRegionId()))
			{
				strays.add(echo);
				strayRegions.merge(
					echo.getTileRegionId() + " from " + City.of(echo.getCityRegionId()), 1, Integer::sum);
			}
		}

		// The sample guard, in the same spirit as the rest of this file: the rule above
		// is inside the loop, so an empty roster would pass it having asked nothing.
		assertEquals("the whole shipped echo roster has to have been asked", 46, checked);

		assertEquals("echoes standing in a region their source's city does not claim — the "
				+ "shipped data no longer contains this case at all: " + strayRegions,
			0, strays.size());

		// The two halves the four strays used to demonstrate, asserted directly so that
		// removing them cost no coverage. 13110 is the Lumber Yard's square: it ships no
		// file and no city claims it, because the same cut deleted it.
		int unclaimed = 13110;
		assertNull("the fixture has to be genuinely unclaimed", City.of(unclaimed));
		assertTrue("an unclaimed region still fails open, for the authored entities the "
				+ "rule exists for",
			City.isEnabled(unclaimed, new FakeConfig().disable(City.values())));

		EntityDefinition anyEcho = shippedEchoes().get(0);
		City owner = City.of(anyEcho.getCityRegionId());
		assertNotNull("an echo's source still has a city, or this proves nothing", owner);
		assertFalse("unticking " + owner + " has to switch off an echo derived from it",
			City.isEnabled(anyEcho.getCityRegionId(), new FakeConfig().disableOnly(owner)));
		assertTrue("and leaving it ticked has to leave the echo alone",
			City.isEnabled(anyEcho.getCityRegionId(), new FakeConfig()));
	}

	/** An echo never changes storey, and never leaves the file its source was filed under. */
	@Test
	public void everyShippedEchoStaysOnItsSourcesStoreyAndInItsSourcesFile()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		int checked = 0;
		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull(source);
			assertEquals("an echo never changes storey", source.getPlane(), echo.getPlane());
			assertEquals("nor the file it is discovered through",
				source.getRegionId(), echo.getRegionId());
			checked++;
		}

		assertEquals("the shipped echo roster", 46, checked);
	}

	private static String describe(EntityDefinition entity)
	{
		return (entity.isEcho() ? "echo of " + entity.getEchoSourceUuid() + " " : "authored ")
			+ entity.label();
	}

	/**
	 * Every echo the named shipped citizen seeds, derived with its whole region file
	 * around it — which is the only way the answer means anything, since placement is
	 * judged against everybody else in the file.
	 */
	private static List<EntityDefinition> echoesOfShipped(String name)
	{
		List<EntityDefinition> out = new ArrayList<>();
		boolean found = false;

		for (List<EntityDefinition> roster : shippedRosters().values())
		{
			UUID uuid = null;
			for (EntityDefinition entity : roster)
			{
				if (name.equals(entity.getName()))
				{
					uuid = entity.getUuid();
					found = true;
				}
			}

			if (uuid == null)
			{
				continue;
			}

			for (EntityDefinition echo : CitizenEcho.echoesOfRegion(roster))
			{
				if (uuid.equals(echo.getEchoSourceUuid()))
				{
					out.add(echo);
				}
			}
		}

		assertTrue("'" + name + "' has to still be in the dataset, or this proves nothing",
			found);
		return out;
	}

	/**
	 * How many <i>distinct</i> non-identity rotations a palette has, ignoring the
	 * flesh rule entirely.
	 *
	 * <p>Written out here rather than asked of {@link CitizenEcho} on purpose: it is
	 * the "before" figure the flesh rule is measured against, so computing it with the
	 * code under test would make the difference between the two numbers unfalsifiable.
	 * The arithmetic is small enough to state twice.
	 */
	private static int distinctRotationCount(short[] replace)
	{
		Set<List<Short>> seen = new HashSet<>();
		seen.add(boxed(replace));

		int distinct = 0;
		for (int k = 1; k < replace.length; k++)
		{
			if (seen.add(boxed(CitizenEcho.redeal(replace, k))))
			{
				distinct++;
			}
		}
		return distinct;
	}

	/** @return true if every rotation of this palette comes out as the palette itself */
	private static boolean everyRotationIsTheIdentity(short[] replace)
	{
		return distinctRotationCount(replace) == 0;
	}

	private static List<Short> boxed(short[] palette)
	{
		List<Short> out = new ArrayList<>(palette.length);
		for (short colour : palette)
		{
			out.add(colour);
		}
		return out;
	}

	private static void assertArray(short[] expected, short[] actual)
	{
		assertArray("", expected, actual);
	}

	private static void assertArray(String message, short[] expected, short[] actual)
	{
		assertTrue(message + " expected " + Arrays.toString(expected)
			+ " but was " + Arrays.toString(actual), Arrays.equals(expected, actual));
	}

	/**
	 * One citizen's echoes, derived as if it were the only thing in its region.
	 *
	 * <p>{@link CitizenEcho#echoesOfRegion} takes a whole region file, because
	 * separation is a claim about a tile and everything standing near it. A fixture
	 * with one citizen in it is a region with one citizen in it, and that is exactly
	 * what most of the tests above want: the derivation with nothing else in the way.
	 * The dataset tests below hand it real rosters instead.
	 */
	private static List<EntityDefinition> echoesOf(EntityDefinition source)
	{
		return CitizenEcho.echoesOfRegion(Collections.singletonList(source));
	}

	/**
	 * Every entity in the shipped dataset, through the real
	 * {@link EntityDefinition#fromRecord} — so wander boxes are the validated,
	 * clamped ones the game would use, which is what {@link CitizenEcho} places
	 * echoes inside.
	 */
	private static List<EntityDefinition> shippedEntities()
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (List<EntityDefinition> roster : shippedRosters().values())
		{
			out.addAll(roster);
		}

		assertEquals("the whole shipped roster", 184, out.size());
		return out;
	}

	/**
	 * Every echo the shipped dataset seeds, derived the way the scene derives them:
	 * one whole region file at a time.
	 */
	private static List<EntityDefinition> shippedEchoes()
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (List<EntityDefinition> roster : shippedRosters().values())
		{
			out.addAll(CitizenEcho.echoesOfRegion(roster));
		}
		return out;
	}

	/** The shipped rosters, keyed by region file, in ascending region order. */
	private static Map<Integer, List<EntityDefinition>> shippedRosters()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		Map<Integer, List<EntityDefinition>> out = new LinkedHashMap<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			out.put(regionId, region.getEntities());
		}

		return out;
	}
}
