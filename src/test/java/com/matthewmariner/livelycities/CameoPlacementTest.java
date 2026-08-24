package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	 */
	private static final Cameo[] EXPECTED = {
		// 1798 = NpcID.WHITE_KNIGHT. Full plate and a shield; the gear carries
		// "imposing, defensive stance" because no loopable human block pose exists —
		// HUMAN_UNARMEDBLOCK (424) is a combat-cycle frame, not a _READY one, and
		// looping it would read as flinching on repeat.
		new Cameo("Rob", "0ca20006-8c47-4e29-9b05-7d16a4f9302e", 3158, 3494, 1798, 1664, "HumanIdle"),

		// 512 = NpcID.YOUNG_DARK_WIZARD. Alching is HUMAN_CASTHIGHLVLALCHEMY (713) —
		// verified by gameval name, and it is the one-hand-forward cast, i.e. the
		// closest thing to a spell pose in the table.
		new Cameo("Cazh", "0ca20001-9f4e-4b17-8d63-1e5a7c2b40d1", 3160, 3495, 512, 1792, "Alching"),

		// 526 = NpcID.ROGUE. LectorIdle is 5875 =
		// BRAIN_BROTHER_TRANQUILITY_SHIFTY_READY: a human-skeleton, loopable, shifty
		// standing pose. Chosen over HumanLook (2713 = READY_PLAYING_CARDS) because
		// "shifty" is the brief and "holding cards" is not. No crouch was adopted:
		// GoblinIdle (6203) really is a squat but is goblin-skeleton frame data, and
		// HUMAN_CRATE_SQUAT (9406) is prop-specific and not a _READY pose.
		new Cameo("Sludgellama", "0ca20004-5e2f-4c81-9d38-4a7b0916e5c2", 3162, 3496, 526, 1856, "LectorIdle"),

		// 3256 = NpcID.BARBARIAN. Flex is EMOTE_FLEX (8917) and loops.
		new Cameo("Peter", "0ca20003-7d16-4a9c-8b45-2f80e6c31a97", 3164, 3495, 3256, 1984, "Flex"),

		// 3680 = NpcID.MISC_SAILOR. Fishing is HUMAN_FISHING_CASTING (622) — a rod
		// cast, which is the honest sailor-adjacent option; nothing leans on a wheel.
		new Cameo("Gunnar", "0ca20002-3b8d-4f52-9a71-6c0e4d19b8f3", 3161, 3493, 3680, 1728, "Fishing"),

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
