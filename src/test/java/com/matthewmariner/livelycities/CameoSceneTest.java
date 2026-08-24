package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the {@code cameos} checkbox actually does to the scene, and what still has
 * to keep working when it is on.
 *
 * <p>Driven through the real {@link EntityScene} against {@link FakeClient}, so
 * "it is not rendered" means the client genuinely has nothing registered rather
 * than a flag being false somewhere.
 *
 * <p><b>Every fixture here puts a cameo and an ordinary citizen in the same
 * scene.</b> That is not padding: a scene made only of cameos could not tell "the
 * checkbox switched the cameos off" from "the checkbox switched everything off",
 * which is the exact shape of fixture uniformity that made {@link EntitySceneTest}
 * necessary in the first place.
 */
public class CameoSceneTest
{
	/** The Grand Exchange, x 3136-3199, y 3456-3519. */
	private static final int GRAND_EXCHANGE = 12598;

	/** Region 12852 — Varrock south. A second city, so the two toggles can differ. */
	private static final int VARROCK_SOUTH = 12852;

	/** Inside the GE, within the cull radius of the fixtures below. */
	private static final WorldPoint PLAYER = new WorldPoint(3165, 3490, 0);

	/** NpcID.WHITE_KNIGHT, and it does not matter here beyond being a real body. */
	private static final int WHITE_KNIGHT = 1798;
	private static final int BARBARIAN = 3256;

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private EntityScene scene;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.recoloured(
			"White Knight", new int[]{217, 305}, new short[]{10, 20}, new short[]{11, 21}));
		client.withNpc(BARBARIAN, FakeNpcComposition.of("Barbarian", 400));

		regions = new FakeRegions();
		config = new FakeConfig();
		scene = new EntityScene(client, regions, config, config.overrides());
	}

	/** One cameo and one ordinary citizen, both in the GE and both in range. */
	private void oneCameoAndOneCitizen()
	{
		List<EntityDefinition> roster = new ArrayList<>();
		roster.add(regions.cameo(GRAND_EXCHANGE, 3160, 3495, WHITE_KNIGHT));
		roster.add(regions.citizen(GRAND_EXCHANGE, 3169, 3489, 0, 217));
		regions.file(GRAND_EXCHANGE, roster);
	}

	private FakeWorldView pass()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, GRAND_EXCHANGE);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		return view;
	}

	// --- The two toggles, and their four combinations ------------------------

	/**
	 * The default. A fresh install renders the townsperson and not the cameo.
	 */
	@Test
	public void withTheCameosSettingOffOnlyTheOrdinaryCitizenRenders()
	{
		oneCameoAndOneCitizen();

		pass();

		assertEquals("the ordinary citizen must still be there", 1, client.registeredCount());
		assertFalse("nothing may have asked the client for a cameo's body",
			client.npcDefinitionsRequested().contains(WHITE_KNIGHT));
	}

	@Test
	public void tickingTheCameosSettingBringsThemIn()
	{
		oneCameoAndOneCitizen();
		config.setCameos(true);

		pass();

		assertEquals("both the cameo and the citizen", 2, client.registeredCount());
		assertTrue("the cameo's body has to have come from its NPC id",
			client.npcDefinitionsRequested().contains(WHITE_KNIGHT));
	}

	/**
	 * The interaction, and it is an AND rather than an OR: unticking the Grand
	 * Exchange takes the cameos with it even though their own checkbox is on.
	 */
	@Test
	public void aCameoNeedsItsCityCheckboxTooNotJustItsOwn()
	{
		oneCameoAndOneCitizen();
		config.setCameos(true).disableOnly(City.GRAND_EXCHANGE);

		pass();

		assertEquals("unticking the city switches off the cameo and the citizen alike",
			0, client.registeredCount());
	}

	/**
	 * And the other way round, which is the half that matters for a reviewer: the
	 * Grand Exchange being on is not consent to player-shaped content.
	 */
	@Test
	public void theCityCheckboxBeingOnIsNotEnoughOnItsOwn()
	{
		oneCameoAndOneCitizen();
		config.setCameos(false).enable(City.GRAND_EXCHANGE);

		pass();

		assertEquals("the city is on, so its ordinary citizen renders", 1, client.registeredCount());

		LivelyEntity cameo = wrapperForCameo();
		assertNotNull(cameo);
		assertFalse("but the cameo does not", cameo.isActive());
	}

	/**
	 * Unticking the box takes them off the screen on the click, through the same
	 * "what is not wanted is despawned" rule the city checkboxes use — not on the
	 * next region crossing.
	 */
	@Test
	public void unticikingTheSettingDespawnsThemImmediately()
	{
		oneCameoAndOneCitizen();
		config.setCameos(true);

		FakeWorldView view = pass();
		assertEquals(2, client.registeredCount());

		config.setCameos(false);
		scene.onSettingsChanged(PLAYER, view);

		assertEquals("only the ordinary citizen is left", 1, client.registeredCount());

		// And back again, without a rebuild: the wrapper kept its model.
		client.resetCounters();
		config.setCameos(true);
		scene.onSettingsChanged(PLAYER, view);
		assertEquals(2, client.registeredCount());
		assertEquals("coming back is an activate, not a rebuild", 0, client.mergeCalls());
	}

	// --- The ground gate ------------------------------------------------------

	/**
	 * A cameo standing on ground the collision map calls blocked does not render.
	 *
	 * <p>The mitigation for the fact that nobody has stood on these six tiles in game
	 * — see {@code CameoPlacementTest}'s class javadoc. The ordinary citizen two
	 * tiles away is the control: authored non-cameo content is <b>not</b> collision
	 * gated, and this is what says the gate is about cameos rather than about the
	 * whole scene.
	 */
	@Test
	public void aCameoOnBlockedGroundIsSkippedWhileAnOrdinaryCitizenIsNot()
	{
		WorldPoint cameoTile = new WorldPoint(3160, 3495, 0);
		WorldPoint citizenTile = new WorldPoint(3169, 3489, 0);

		regions.file(GRAND_EXCHANGE, Arrays.asList(
			regions.cameo(GRAND_EXCHANGE, cameoTile.getX(), cameoTile.getY(), WHITE_KNIGHT),
			regions.citizen(GRAND_EXCHANGE, citizenTile.getX(), citizenTile.getY(), 0, 217)));
		config.setCameos(true);

		FakeWorldView view = FakeWorldView.around(PLAYER, GRAND_EXCHANGE);
		view.block(cameoTile);
		view.block(citizenTile);

		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("the bank booth keeps the cameo out; the vendored citizen is trusted",
			1, client.registeredCount());

		LivelyEntity cameo = wrapperForCameo();
		assertNotNull(cameo);
		assertFalse(cameo.isActive());
		assertFalse("a refused tile is not a broken entity — the scene may reload", cameo.isBroken());
	}

	/**
	 * The other side of the same coin, and the reason the test above is not green for
	 * the wrong reason: on open ground the cameo does render.
	 *
	 * <p>{@link FakeWorldView} defaults every tile to walkable, so without this pair
	 * a gate that refused every cameo unconditionally would look identical to a gate
	 * that worked.
	 */
	@Test
	public void aCameoOnOpenGroundRenders()
	{
		oneCameoAndOneCitizen();
		config.setCameos(true);

		pass();

		LivelyEntity cameo = wrapperForCameo();
		assertNotNull(cameo);
		assertTrue(cameo.isActive());
	}

	/**
	 * A collision map that has no answer is not a yes.
	 *
	 * <p>An echo may be admitted on {@code UNKNOWN} if its tile came from inside its
	 * source's authored wander box — a human vouched for that ground. A cameo has no
	 * wander box and nothing has vouched for its tile, so it waits. In practice it
	 * waits until the scene finishes building, which is one tick.
	 */
	@Test
	public void aCameoIsNotRenderedWhileTheCollisionMapHasNoAnswer()
	{
		oneCameoAndOneCitizen();
		config.setCameos(true);

		FakeWorldView view = FakeWorldView.around(PLAYER, GRAND_EXCHANGE).withoutCollisionData();
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("only the ordinary citizen, which needs no vouching", 1, client.registeredCount());

		// The scene finishes loading. Same wrapper, same tile, now admitted.
		FakeWorldView loaded = FakeWorldView.around(PLAYER, GRAND_EXCHANGE);
		scene.syncRegions(loaded);
		scene.updateVisibility(PLAYER, loaded);
		assertEquals(2, client.registeredCount());
	}

	// --- Hide and mute --------------------------------------------------------

	/**
	 * Hide works on a cameo like any citizen, and hides only that one.
	 */
	@Test
	public void hidingACameoHidesThatCameoAndNothingElse()
	{
		List<EntityDefinition> roster = Arrays.asList(
			regions.cameo(GRAND_EXCHANGE, 3160, 3495, WHITE_KNIGHT),
			regions.cameo(GRAND_EXCHANGE, 3164, 3495, BARBARIAN),
			regions.citizen(GRAND_EXCHANGE, 3169, 3489, 0, 217));
		regions.file(GRAND_EXCHANGE, roster);
		config.setCameos(true);

		FakeWorldView view = pass();
		assertEquals(3, client.registeredCount());

		assertTrue(config.overrides().hide(roster.get(0)));
		scene.onSettingsChanged(PLAYER, view);

		assertEquals("one cameo hidden, the other cameo and the citizen untouched",
			2, client.registeredCount());
		assertFalse(scene.wrapperForUuid(roster.get(0).getUuid()).isActive());
		assertTrue(scene.wrapperForUuid(roster.get(1).getUuid()).isActive());
	}

	/**
	 * Mute works on a cameo, which means a cameo has to have something to mute — all
	 * six shipped ones carry a remark, and {@link CitizenMenu} only offers Mute to a
	 * citizen with remarks.
	 */
	@Test
	public void aCameoCanBeMutedBecauseItHasSomethingToSay()
	{
		List<EntityDefinition> roster = Arrays.asList(
			regions.cameo(GRAND_EXCHANGE, 3160, 3495, WHITE_KNIGHT),
			regions.citizen(GRAND_EXCHANGE, 3169, 3489, 0, 217));
		regions.file(GRAND_EXCHANGE, roster);
		config.setCameos(true);

		pass();

		LivelyEntity cameo = scene.wrapperForUuid(roster.get(0).getUuid());
		assertNotNull(cameo);
		assertNotNull("a cameo has remarks, so CitizenMenu offers it Mute", cameo.getRemarks());

		assertTrue(config.overrides().mute(roster.get(0)));
		assertTrue(config.overrides().mutedUuids().contains(roster.get(0).getUuid()));

		// The control: the ordinary citizen in this fixture has none, so "cameos can
		// be muted" is a fact about cameos rather than about every wrapper.
		assertNull(scene.wrapperForUuid(roster.get(1).getUuid()).getRemarks());
	}

	// --- Interaction with the crowd dial -------------------------------------

	/**
	 * Turning the density up to Crowded must not add anybody near a cameo.
	 *
	 * <p>An echo is gated on the crowd density and on its source's city, and on
	 * nothing else — so an echo of a cameo would be an extra human body in the Grand
	 * Exchange for any Crowded user regardless of the {@code cameos} checkbox, which
	 * is the exact leak that checkbox exists to close. It would also be a seventh
	 * person in a posed group photo.
	 *
	 * <p><b>The cameo in this fixture is deliberately the kind that would otherwise
	 * seed two echoes</b> — {@link FakeRegions#cameoWithModelIds} carries raw model ids
	 * and a three-pair palette — so being a cameo is the only reason it seeds none.
	 * A cameo dressed from an NPC id would have been refused by two other rules first,
	 * and this test would have been green for the wrong reason. The ordinary
	 * {@code recoloured} citizen beside it is the control: it does seed echoes, so a
	 * derivation that had stopped working altogether would also show up.
	 */
	@Test
	public void aCameoSeedsNoEchoesEvenAtCrowdedDensity()
	{
		EntityDefinition cameo = regions.cameoWithModelIds(GRAND_EXCHANGE, 3160, 3495);
		EntityDefinition ordinary = regions.recoloured(GRAND_EXCHANGE, 3170, 3480, 3);

		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(Arrays.asList(cameo, ordinary));

		assertFalse("the fixture has to produce echoes at all, or this proves nothing",
			echoes.isEmpty());

		for (EntityDefinition echo : echoes)
		{
			assertEquals("every echo here must come from the ordinary citizen",
				ordinary.getUuid(), echo.getEchoSourceUuid());
			assertFalse("an echo is never a cameo", echo.isCameo());
		}
	}

	/**
	 * An NPC-dressed source seeds no echoes either, and for a different reason: its
	 * colours come from the composition, so a re-dealt palette would produce a
	 * pixel-for-pixel twin standing two tiles away.
	 *
	 * <p>Separate from the cameo rule because the two are separate record fields and
	 * separate branches. Nothing in the shipped data is NPC-dressed without also being
	 * a cameo, so this is the only place the rule is exercised at all — which is
	 * exactly why it is here.
	 */
	@Test
	public void anNpcDressedSourceSeedsNoEchoesBecauseAReDealWouldChangeNothing()
	{
		EntityDefinition dressed = regions.npcDressed(GRAND_EXCHANGE, 3160, 3495, WHITE_KNIGHT);
		EntityDefinition ordinary = regions.recoloured(GRAND_EXCHANGE, 3170, 3480, 3);

		assertEquals("the fixture must have a palette rich enough to seed two, or this "
				+ "test cannot tell the rule from the palette check",
			3, dressed.getRecolorFind().length);

		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(Arrays.asList(dressed, ordinary));

		assertFalse(echoes.isEmpty());
		for (EntityDefinition echo : echoes)
		{
			assertEquals("an NPC-dressed citizen must seed nothing",
				ordinary.getUuid(), echo.getEchoSourceUuid());
		}
	}

	/**
	 * The same claim through the scene, because {@code echoesOfRegion} being pure is
	 * only half of it: the scene is what builds the wrappers and applies the dial.
	 */
	@Test
	public void crowdedWithTheCameosSettingOffAddsNothingToTheGrandExchange()
	{
		regions.file(GRAND_EXCHANGE, Arrays.asList(
			regions.cameoWithModelIds(GRAND_EXCHANGE, 3160, 3495),
			regions.citizen(GRAND_EXCHANGE, 3169, 3489, 0, 217)));
		config.setCameos(false).setCrowdDensity(CrowdDensity.CROWDED);

		pass();

		assertEquals("no cameo, and nothing derived from one", 1, client.registeredCount());
		assertEquals(0, scene.countActiveEchoes());
		assertEquals("the cameo must not even have seeded a wrapper", 0, scene.getEchoInScopeCount());
	}

	// --- The flag is not the appearance mechanism ----------------------------

	/**
	 * An entity dressed from an NPC id but <b>not</b> flagged as a cameo is ordinary
	 * content, and the {@code cameos} checkbox has no opinion about it.
	 *
	 * <p>This is why the two are separate record fields. The appearance mechanism is
	 * meant to be the preferred way to dress any future entity — a market stall owner
	 * sourced from an NPC id must not silently become opt-in content that most users
	 * never see.
	 */
	@Test
	public void anNpcDressedCitizenThatIsNotACameoIgnoresTheCameosSetting()
	{
		regions.file(GRAND_EXCHANGE, Arrays.asList(
			regions.npcDressed(GRAND_EXCHANGE, 3160, 3495, WHITE_KNIGHT),
			regions.cameo(GRAND_EXCHANGE, 3164, 3495, BARBARIAN)));
		config.setCameos(false);

		pass();

		assertEquals("the NPC-dressed citizen renders; the cameo does not", 1, client.registeredCount());
		assertTrue("and it really did source its body from the NPC id",
			client.npcDefinitionsRequested().contains(WHITE_KNIGHT));
	}

	/**
	 * A cameo in another city obeys <i>that</i> city's checkbox.
	 *
	 * <p>Nothing in the shipped data does this — the placement lint refuses it, see
	 * {@code CameoPlacementTest} — but the scene's gate must not be a special case
	 * about one region either, or the lint would be the only thing holding the rule
	 * and a hand-edited profile could not be reasoned about.
	 */
	@Test
	public void theCameoGateIsNotSpecialCasedToTheGrandExchange()
	{
		WorldPoint varrock = new WorldPoint(3225, 3360, 0);
		regions.file(VARROCK_SOUTH, Arrays.asList(
			regions.cameo(VARROCK_SOUTH, 3225, 3358, WHITE_KNIGHT),
			regions.citizen(VARROCK_SOUTH, 3227, 3358, 0, 217)));
		config.setCameos(true);

		FakeWorldView view = FakeWorldView.around(varrock, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(varrock, view);
		assertEquals(2, client.registeredCount());

		config.disableOnly(City.VARROCK);
		scene.onSettingsChanged(varrock, view);
		assertEquals("Varrock's checkbox governs a cameo standing in Varrock",
			0, client.registeredCount());
	}

	private LivelyEntity wrapperForCameo()
	{
		for (LivelyEntity entity : scene.inScopeEntities())
		{
			if (entity.getDefinition().isCameo())
			{
				return entity;
			}
		}
		return null;
	}
}
