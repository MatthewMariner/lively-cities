package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link CrowdDensity#CROWDED} through the scene: what the dial actually does to
 * the client's registered-object list.
 *
 * <p>{@link CitizenEchoTest} covers the derivation offline. This covers the five
 * things that can only go wrong once the wrappers exist — the opt-in, the placement
 * gate, the object cap, the per-citizen hide, and teardown — because every one of
 * them is a claim about {@code EntityScene}'s passes rather than about
 * {@code CitizenEcho}'s arithmetic.
 *
 * <p>Regions 12852 and 12853 again, for the reason {@code EntitySceneTest} gives:
 * they are Varrock, and they are where the shipped dataset is densest.
 */
public class CrowdedSceneTest
{
	private static final int VARROCK_SOUTH = 12852;
	private static final int VARROCK_NORTH = 12853;

	/** Piscatoris' one region file: 9272, x 2304..2367, y 3584..3647. */
	private static final int PISCATORIS_HARBOUR = 9272;

	/**
	 * The region immediately south of it, y 3520..3583. No city claims it and no
	 * region file ships for it, which is exactly why echoes could hide there.
	 */
	private static final int UNCLAIMED_SOUTH_OF_PISCATORIS = 9271;

	private static final WorldPoint PLAYER = new WorldPoint(3225, 3360, 0);

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private EntityScene scene;

	@Before
	public void setUp()
	{
		// A warm NPC archive: two of the tests below spawn the real dataset, which
		// includes one citizen dressed from an npcAppearanceId. An unregistered id
		// throws, exactly as the real client does, so without this that citizen
		// silently fails to spawn and every crowd count here comes out one short.
		client = new FakeClient().withShippedNpcAppearances();
		regions = new FakeRegions();
		config = new FakeConfig();
		scene = new EntityScene(client, regions, config, config.overrides());
	}

	// --- The opt-in -----------------------------------------------------------

	/**
	 * The whole promise of "purely additive and opt-in": {@link CrowdDensity#FULL}
	 * has to still be exactly the authored set.
	 *
	 * <p>The echo wrappers exist at FULL — {@code EntityScene} builds them whatever
	 * the dial says, so that they inherit eviction and teardown for free — so this
	 * asserts on what the client is holding, not on what is in scope. Those are the
	 * two halves that a "gate it at build time instead" version would conflate.
	 */
	@Test
	public void fullShowsEveryAuthoredCitizenAndNotOneEcho()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 6, 6));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);

		assertEquals("every authored citizen", 6, scene.countActiveAuthored());
		assertEquals("and nothing else at all", 0, scene.countActiveEchoes());
		assertEquals(6, client.registeredCount());

		assertEquals("the echoes are built and in scope, they are simply not wanted",
			12, scene.getEchoInScopeCount());
	}

	@Test
	public void crowdedKeepsEveryAuthoredCitizenAndAddsTheEchoesOnTop()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 6, 6));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		int authoredAtFull = client.registeredCount();

		config.setCrowdDensity(CrowdDensity.CROWDED);
		VisibilityPasses.settle(scene, PLAYER, view);

		assertEquals("not one authored citizen may be lost on the way up",
			authoredAtFull, scene.countActiveAuthored());
		assertEquals(12, scene.countActiveEchoes());
		assertEquals(18, client.registeredCount());
	}

	/**
	 * Turning the dial back down has to deactivate the echoes on the click, not
	 * merely stop new ones appearing — the same requirement the city checkboxes have,
	 * answered by the same "what is not wanted is despawned" rule.
	 */
	@Test
	public void turningTheDialBackDownDeactivatesTheEchoesAndLeavesTheCitizens()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 4, 3));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals(8, scene.countActiveEchoes());

		config.setCrowdDensity(CrowdDensity.FULL);
		scene.onSettingsChanged(PLAYER, view);

		assertEquals("the echoes go away on the click", 0, scene.countActiveEchoes());
		assertEquals("and the citizens stay", 4, scene.countActiveAuthored());
		assertEquals("with their wrappers still cached, ready to come back",
			1, scene.getCachedRegionCount());
	}

	/**
	 * Thinning still thins at the levels below FULL, and it never admits an echo —
	 * SPARSE plus echoes would be a dial that means two contradictory things at once.
	 */
	@Test
	public void noLevelBelowFullEverAdmitsAnEcho()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 8, 6));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);

		for (CrowdDensity density : CrowdDensity.values())
		{
			config.setCrowdDensity(density);
			scene.onSettingsChanged(PLAYER, view);

			// The dial takes effect on the click; the crowd it admits still arrives
			// three models a pass, so give it the passes before counting.
			VisibilityPasses.settle(scene, PLAYER, view);

			if (density == CrowdDensity.CROWDED)
			{
				assertTrue(density + " must add echoes", scene.countActiveEchoes() > 0);
			}
			else
			{
				assertEquals(density + " must add none", 0, scene.countActiveEchoes());
			}
		}
	}

	// --- Placement ------------------------------------------------------------

	/**
	 * The gate that makes the feature safe: an echo whose tile the collision map
	 * refuses never spawns, and nothing goes looking for somewhere else to put it.
	 *
	 * <p>The fixture blocks exactly one echo's tile, so the assertion is a count and
	 * not a "nothing spawned" — a version that refused every echo the moment any tile
	 * was blocked would pass a coarser test.
	 */
	@Test
	public void anEchoOnBlockedGroundIsSkippedAndNotMovedSomewhereElse()
	{
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);
		regions.file(VARROCK_SOUTH, source);
		config.setCrowdDensity(CrowdDensity.CROWDED);

		// The region holds this one citizen, so deriving from a one-entry roster is
		// deriving exactly what the scene derived.
		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(Collections.singletonList(source));
		assertEquals(2, echoes.size());
		WorldPoint doomed = echoes.get(0).getWorldLocation();
		WorldPoint survivor = echoes.get(1).getWorldLocation();

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		view.block(doomed);

		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);

		assertEquals("the authored citizen is untouched", 1, scene.countActiveAuthored());
		assertEquals("one echo standing, one skipped", 1, scene.countActiveEchoes());

		LivelyEntity blocked = scene.wrapperForUuid(echoes.get(0).getUuid());
		LivelyEntity allowed = scene.wrapperForUuid(echoes.get(1).getUuid());
		assertNotNull(blocked);
		assertNotNull(allowed);

		assertFalse("the blocked echo must not be on screen", blocked.isActive());
		assertTrue(allowed.isActive());
		assertEquals("and it must still be standing on the tile it was derived onto — "
				+ "skipped, not nudged onto a neighbour",
			doomed, blocked.getDefinition().getWorldLocation());
		assertEquals(survivor, allowed.getDefinition().getWorldLocation());
		assertFalse("a skipped echo is not a broken one; it comes back if the ground does",
			blocked.isBroken());
	}

	/**
	 * Every echo the scene actually spawns stands on ground the collision map
	 * approved — asserted by re-asking the map about each one afterwards rather than
	 * by trusting the pass that put it there.
	 */
	@Test
	public void everyEchoOnScreenIsStandingOnGroundTheCollisionMapApproved()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3214, 3346, 8, 6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);

		// A wall through the middle of the crowd, so some echoes are refused and the
		// test is about the ones that were not.
		for (int y = 3340; y < 3370; y++)
		{
			view.block(new WorldPoint(3220, y, 0));
		}

		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);

		int checked = 0;
		for (LivelyEntity entity : scene.inScopeEntities())
		{
			if (!entity.getDefinition().isEcho() || !entity.isActive())
			{
				continue;
			}

			checked++;
			assertEquals("active echo at " + entity.getDefinition().getWorldLocation()
					+ " is standing on ground the collision map refuses",
				StandableGround.Verdict.STANDABLE,
				StandableGround.verdict(view, entity.getDefinition().getWorldLocation()));
		}

		assertTrue("the fixture has to leave some echoes standing", checked > 0);
		assertTrue("and the wall has to actually refuse some, or this proves nothing",
			checked < scene.getEchoInScopeCount());
	}

	// --- The object cap -------------------------------------------------------

	/**
	 * The cap sheds echoes before it sheds anybody a human placed.
	 *
	 * <p>The fixture is deliberately hostile to a plain nearest-first comparator: the
	 * echoes sit two tiles from their sources and the authored citizens are spread
	 * out, so by distance alone a great many echoes come ahead of the furthest
	 * authored citizens and would displace them. Sorting authored ahead of derived is
	 * what makes that impossible.
	 */
	@Test
	public void theCapShedsEchoesBeforeAuthoredCitizens()
	{
		int cap = RenderPolicy.MAX_ACTIVE_OBJECTS;
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3212, 3344, 40, 6));
		config.setCullRadius(RenderPolicy.MAX_CULL_RADIUS);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		int authoredAtFull = client.registeredCount();
		assertEquals("all forty authored citizens fit under the cap on their own",
			40, authoredAtFull);

		config.setCrowdDensity(CrowdDensity.CROWDED);
		VisibilityPasses.settle(scene, PLAYER, view);

		assertEquals("forty citizens and eighty echoes cannot all fit, so the cap binds",
			cap, client.registeredCount());
		assertEquals("and every authored citizen keeps its place",
			authoredAtFull, scene.countActiveAuthored());
		assertEquals(cap - authoredAtFull, scene.countActiveEchoes());
		assertEquals("the client must never hold more than the cap, mid-pass included",
			cap, client.peakRegistered());
	}

	/**
	 * The same claim against the real dataset, in the place it actually happens.
	 *
	 * <p>72 is the densest window <b>centred on an entity's own tile</b>, which is what
	 * this test can position itself on. It is not the figure the cap is sized against:
	 * a player may stand anywhere, and the densest window from an arbitrary tile holds
	 * <b>76</b> (see {@link RenderPolicy#MAX_ACTIVE_OBJECTS}). Both are correct answers
	 * to different questions, and conflating them is how "55/59/72/76" ended up in four
	 * places meaning three things. Either way {@code CROWDED} is over the cap of 80
	 * before it has spawned a dozen echoes — which is what the cap is for. Nothing is
	 * hardcoded except the spot: the authored count is measured by running the same
	 * pass at {@code FULL} first, so a data change moves both numbers together.
	 */
	@Test
	public void inTheDensestShippedNeighbourhoodTheCapIsSpentOnEchoesLast()
	{
		EntityScene real = new EntityScene(
			client, new RegionDataLoader(TestGson.injected()), config, config.overrides());

		// (3228, 3407) is the densest point in the dataset at the widest cull radius,
		// on the Varrock side of the wall; 12597 contributes from the north.
		WorldPoint spot = new WorldPoint(3228, 3407, 0);
		config.setCullRadius(RenderPolicy.MAX_CULL_RADIUS);

		FakeWorldView view = FakeWorldView.around(spot, VARROCK_NORTH, VARROCK_SOUTH, 12597);
		real.syncRegions(view);
		VisibilityPasses.settle(real, spot, view);

		int authoredAtFull = client.registeredCount();
		assertEquals("FULL admits no echoes anywhere", 0, real.countActiveEchoes());
		assertTrue("the authored crowd alone has to fit, or this tests the wrong thing: "
				+ authoredAtFull,
			authoredAtFull > 0 && authoredAtFull < RenderPolicy.MAX_ACTIVE_OBJECTS);

		config.setCrowdDensity(CrowdDensity.CROWDED);
		VisibilityPasses.settle(real, spot, view);

		assertEquals("CROWDED in Varrock hits the cap", RenderPolicy.MAX_ACTIVE_OBJECTS,
			client.registeredCount());
		assertEquals("and not one authored citizen is displaced by a derived one",
			authoredAtFull, real.countActiveAuthored());
		assertEquals(RenderPolicy.MAX_ACTIVE_OBJECTS - authoredAtFull, real.countActiveEchoes());
		assertEquals("the peak still never exceeds the cap",
			RenderPolicy.MAX_ACTIVE_OBJECTS, client.peakRegistered());

		assertEquals("teardown reports the whole capped crowd",
			RenderPolicy.MAX_ACTIVE_OBJECTS, real.shutdown());
		assertEquals("and leaves nothing behind", 0, client.registeredCount());
	}

	// --- Identity -------------------------------------------------------------

	/**
	 * Hiding a citizen hides that citizen, and hiding an echo hides that echo.
	 *
	 * <p>Both directions, because either one collapsing is a different bug: an echo
	 * that shared its source's uuid would vanish with it (and could never be hidden
	 * on its own), and an echo whose uuid was generated rather than derived would come
	 * back unhidden on the next region rebuild.
	 */
	@Test
	public void hidingASourceDoesNotHideItsEchoAndHidingAnEchoDoesNotHideItsSource()
	{
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);
		regions.file(VARROCK_SOUTH, source);
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, scene.countActiveAuthored());
		assertEquals(2, scene.countActiveEchoes());

		List<EntityDefinition> echoes = echoesInScope(scene);
		assertEquals(2, echoes.size());
		UUID firstEcho = echoes.get(0).getUuid();

		// Hide the source. Its echoes are somebody else as far as the setting is
		// concerned, and they stay.
		assertTrue(config.overrides().hide(source));
		scene.onSettingsChanged(PLAYER, view);
		assertEquals("hiding a citizen must not hide the strangers derived from it",
			2, scene.countActiveEchoes());
		assertEquals(0, scene.countActiveAuthored());

		// And the other way: unhide the source, hide one echo.
		config.overrides().unhideAll();
		assertTrue(config.overrides().hide(echoes.get(0)));
		scene.onSettingsChanged(PLAYER, view);
		assertEquals("hiding an echo must not hide the citizen it came from",
			1, scene.countActiveAuthored());
		assertEquals("nor its sibling", 1, scene.countActiveEchoes());

		LivelyEntity hidden = scene.wrapperForUuid(firstEcho);
		assertNotNull(hidden);
		assertFalse(hidden.isActive());
	}

	/**
	 * A hide on an echo survives the region cache being thrown away and rebuilt,
	 * which is the closest thing this test can get to a restart.
	 *
	 * <p>The setting is a string of uuids in the user's profile, so the only way this
	 * works is for the echo's uuid to be derived rather than generated. The assertion
	 * on {@code stored(..)} is the persistence half — the uuid really is written out —
	 * and the rebuild is the stability half.
	 */
	@Test
	public void aHiddenEchoStaysHiddenAcrossARebuildOfItsRegion()
	{
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);
		regions.file(VARROCK_SOUTH, source);
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);

		EntityDefinition echo = echoesInScope(scene).get(0);
		UUID echoUuid = echo.getUuid();
		assertTrue(config.overrides().hide(echo));

		String stored = config.stored(CitizenOverrides.HIDDEN_KEY);
		assertNotNull("the uuid has to actually reach the profile", stored);
		assertTrue(stored + " should hold " + echoUuid, stored.contains(echoUuid.toString()));

		// Throw the whole cache away and build it again, the way a logout or a long
		// walk away and back does.
		scene.shutdown();
		assertEquals(0, client.registeredCount());

		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);

		LivelyEntity rebuilt = scene.wrapperForUuid(echoUuid);
		assertNotNull("the same echo has to exist again, under the same uuid", rebuilt);
		assertFalse("and it has to still be hidden", rebuilt.isActive());
		assertEquals("while its sibling comes back", 1, scene.countActiveEchoes());
		assertEquals(1, scene.countActiveAuthored());
	}

	/**
	 * A rebuild produces the same echoes, field for field.
	 *
	 * <p>{@link CitizenEchoTest} asserts this of the derivation. This asserts it of
	 * the path that actually runs it: {@code ensureBuilt} rederives a region's echoes
	 * on every build, so this happens on every border crossing and every eviction,
	 * and an echo that moved or changed colour when its region came back would flicker
	 * exactly where the player is standing.
	 */
	@Test
	public void rebuildingARegionProducesTheSameEchoesInTheSamePlaces()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 6, 6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		Map<UUID, String> first = fingerprint(echoesInScope(scene));
		assertEquals(12, first.size());

		scene.shutdown();
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		Map<UUID, String> second = fingerprint(echoesInScope(scene));

		assertEquals("the same echoes, by uuid", first.keySet(), second.keySet());
		for (Map.Entry<UUID, String> entry : first.entrySet())
		{
			assertEquals("echo " + entry.getKey() + " changed on rebuild",
				entry.getValue(), second.get(entry.getKey()));
		}
	}

	// --- What an echo costs ---------------------------------------------------

	@Test
	public void echoesNeverJoinThePerFrameWalkerList()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);

		assertEquals("the authored wanderer walks", 1, scene.getWalkerCount());
		assertEquals("its echoes do not, so doubling the crowd costs the frame pass nothing",
			2, scene.countActiveEchoes());

		for (int tick = 0; tick < 50; tick++)
		{
			scene.onGameTick(PLAYER, view);
			scene.onFrame(view, 0.5f);
			assertEquals(1, scene.getWalkerCount());
		}
	}

	/**
	 * The sources here are citizens that <i>do</i> talk, which is the whole point:
	 * doubling the crowd must not double the overhead text, and the only way to tell
	 * that from a fixture coincidence is for the citizens being echoed to have lines
	 * of their own.
	 */
	@Test
	public void anEchoStaysSilentThroughHundredsOfTicksWhileItsSourceTalks()
	{
		List<EntityDefinition> talkers = new ArrayList<>();
		for (int i = 0; i < 6; i++)
		{
			talkers.add(regions.recolouredTalker(
				VARROCK_SOUTH, 3218 + i * 3, 3352, 6, "Busy today.", "Lovely weather."));
		}
		regions.file(VARROCK_SOUTH, talkers);
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);

		int everTalking = 0;
		for (int tick = 0; tick < 600; tick++)
		{
			scene.onGameTick(PLAYER, view);
			everTalking = Math.max(everTalking, scene.countTalking());

			for (LivelyEntity entity : scene.inScopeEntities())
			{
				if (!entity.getDefinition().isEcho())
				{
					continue;
				}

				assertNull("an echo has no authored line, so it can never be saying one",
					entity.getRemarks());
			}
		}

		assertEquals("the echoes are on screen throughout", 12, scene.countActiveEchoes());
		assertTrue("and their sources really do talk, or this proves nothing", everTalking > 0);
	}

	// --- Teardown -------------------------------------------------------------

	@Test
	public void teardownLeavesNothingRegisteredWithEchoesActive()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 6, 6));
		regions.file(VARROCK_NORTH, regions.recolouredCrowd(VARROCK_NORTH, 3225, 3394, 2, 6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		WorldPoint border = new WorldPoint(3225, 3390, 0);
		FakeWorldView view = FakeWorldView.around(border, VARROCK_SOUTH, VARROCK_NORTH);
		scene.syncRegions(view);
		scene.onGameTick(border, view);

		int active = client.registeredCount();
		assertTrue("the fixture has to have echoes on screen", scene.countActiveEchoes() > 0);

		assertEquals("shutdown reports everything it deactivated", active, scene.shutdown());
		assertEquals("and leaves nothing registered", 0, client.registeredCount());
		assertEquals("and drops every wrapper, echoes included", 0, scene.getCachedRegionCount());
		assertEquals(0, scene.getEchoInScopeCount());
	}

	@Test
	public void aSceneChangeDeactivatesTheEchoesItActivated()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 5, 6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals(10, scene.countActiveEchoes());

		WorldPoint elsewhere = new WorldPoint(3600, 3200, 0);
		FakeWorldView moved = FakeWorldView.around(elsewhere, RenderPolicy.regionIdOf(3600, 3200));
		scene.syncRegions(moved);

		assertEquals("a scene change must leave no echo registered either",
			0, client.registeredCount());
	}

	/**
	 * An echo is switched off by its own city's checkbox, like anything else standing
	 * in that city — and by the tile it stands on, not by the file it came from.
	 */
	@Test
	public void untickingACityDeactivatesItsEchoesToo()
	{
		regions.file(VARROCK_SOUTH, regions.recolouredCrowd(VARROCK_SOUTH, 3218, 3350, 4, 6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals(8, scene.countActiveEchoes());

		config.disableOnly(City.VARROCK);
		scene.onSettingsChanged(PLAYER, view);
		assertEquals(0, client.registeredCount());

		config.enable(City.VARROCK);
		scene.onSettingsChanged(PLAYER, view);
		assertEquals(4, scene.countActiveAuthored());
		assertEquals(8, scene.countActiveEchoes());
	}

	/**
	 * <b>Unticking a city takes its echoes with it even when an echo has stepped over
	 * a border into a region no city claims.</b>
	 *
	 * <p>Run against the real dataset, because the case only exists there and because
	 * it is the case that used to escape. {@link City#isEnabled} answers {@code true}
	 * for an unclaimed region on purpose — that is what lets a region file ship one
	 * commit before its checkbox — so an echo judged by its own tile went through that
	 * door: three of Piscatoris's echoes stand in region 9271, which no city claims and
	 * which ships no file, and unticking Piscatoris left them standing in the empty
	 * fields south of the village. They are judged by their source's city now, so they
	 * go when it goes and come back when it comes back.
	 */
	@Test
	public void untickingACityAlsoRemovesTheEchoesStandingInARegionNoCityClaims()
	{
		EntityScene real = new EntityScene(
			client, new RegionDataLoader(TestGson.injected()), config, config.overrides());

		// Piscatoris' harbour, and the unclaimed region immediately south of it. Both
		// have to be in the scene: scope membership is keyed on the entity's own tile,
		// so an echo standing in 9271 is only in scope while 9271 is loaded.
		WorldPoint spot = new WorldPoint(2337, 3585, 0);
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(spot, PISCATORIS_HARBOUR, UNCLAIMED_SOUTH_OF_PISCATORIS);
		real.syncRegions(view);
		VisibilityPasses.settle(real, spot, view);

		int strays = countActiveEchoesInUnclaimedRegions(real);
		assertTrue("the fixture depends on real echoes crossing into region "
				+ UNCLAIMED_SOUTH_OF_PISCATORIS + " — if the dataset moved, find the new case "
				+ "rather than deleting this test", strays > 0);
		assertTrue("and on their authored sources being on screen too",
			real.countActiveAuthored() > 0);
		assertNull("the region they stand in really is unclaimed",
			City.of(UNCLAIMED_SOUTH_OF_PISCATORIS));

		config.disableOnly(City.PISCATORIS);
		real.onSettingsChanged(spot, view);

		assertEquals("unticking Piscatoris has to take everything derived from Piscatoris "
				+ "with it, wherever it ended up standing", 0, client.registeredCount());

		config.enable(City.PISCATORIS);
		real.onSettingsChanged(spot, view);

		assertEquals("and ticking it again brings the same strays back",
			strays, countActiveEchoesInUnclaimedRegions(real));
	}

	/**
	 * The other half of that rule, which is <b>not</b> being changed: an
	 * <i>authored</i> entity in a region no city claims still fails open.
	 *
	 * <p>{@code CityTest.aRegionNoCityClaimsIsStillShown} asserts it of the lookup;
	 * this asserts it of the scene, with every checkbox in the plugin unticked, because
	 * the fix above works by asking a different question of an echo and it would have
	 * been just as easy to answer it wrongly for everybody.
	 */
	@Test
	public void anAuthoredCitizenInARegionNoCityClaimsIsStillShownWithEveryCheckboxOff()
	{
		// Keldagrim: no region file, no City constant, and no plans for either.
		int unclaimed = 11422;
		assertNull("the fixture has to be genuinely unmapped", City.of(unclaimed));

		WorldPoint tile = new WorldPoint(2860, 10150, 0);
		assertEquals(unclaimed, RenderPolicy.regionIdOf(tile.getX(), tile.getY()));

		regions.file(unclaimed, regions.recoloured(unclaimed, tile.getX(), tile.getY(), 6));
		config.setCrowdDensity(CrowdDensity.CROWDED).disable(City.values());

		FakeWorldView view = FakeWorldView.around(tile, unclaimed);
		scene.syncRegions(view);
		scene.onGameTick(tile, view);

		assertEquals("an unmapped region fails open for the citizen a human placed there",
			1, scene.countActiveAuthored());
		assertEquals("and for the echoes it seeds, which answer to the same nothing",
			2, scene.countActiveEchoes());
	}

	/** The cull radius is the echo's own, measured from where the echo stands. */
	@Test
	public void anEchoBeyondTheCullRadiusIsDeactivatedLikeAnythingElse()
	{
		regions.file(VARROCK_SOUTH, regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6));
		config.setCrowdDensity(CrowdDensity.CROWDED);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals(2, scene.countActiveEchoes());

		WorldPoint away = new WorldPoint(3225, 3355 - RenderPolicy.DEFAULT_CULL_RADIUS - 5, 0);
		scene.updateVisibility(away, view);
		assertEquals(0, client.registeredCount());
	}

	/**
	 * How many echoes are on screen whose <i>own</i> tile is in a region no city
	 * claims — the population that used to be unswitchable-off.
	 */
	private static int countActiveEchoesInUnclaimedRegions(EntityScene scene)
	{
		int n = 0;
		for (LivelyEntity entity : scene.inScopeEntities())
		{
			EntityDefinition definition = entity.getDefinition();
			if (definition.isEcho()
				&& entity.isActive()
				&& City.of(definition.getTileRegionId()) == null)
			{
				n++;
			}
		}
		return n;
	}

	private static List<EntityDefinition> echoesInScope(EntityScene scene)
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (LivelyEntity entity : scene.inScopeEntities())
		{
			if (entity.getDefinition().isEcho())
			{
				out.add(entity.getDefinition());
			}
		}
		return out;
	}

	/**
	 * Everything about an echo that a rebuild could plausibly change, as one string
	 * per uuid — so a diff names the field rather than saying "not equal".
	 */
	private static Map<UUID, String> fingerprint(List<EntityDefinition> echoes)
	{
		Map<UUID, String> out = new HashMap<>();
		for (EntityDefinition echo : echoes)
		{
			StringBuilder key = new StringBuilder();
			key.append("tile=").append(echo.getWorldLocation())
				.append(" facing=").append(echo.getOrientation())
				.append(" region=").append(echo.getTileRegionId())
				.append(" source=").append(echo.getEchoSourceUuid())
				.append(" authoredGround=").append(echo.isEchoOnAuthoredGround())
				.append(" find=");
			for (short colour : echo.getRecolorFind())
			{
				key.append(colour).append(',');
			}
			key.append(" replace=");
			for (short colour : echo.getRecolorReplace())
			{
				key.append(colour).append(',');
			}
			out.put(echo.getUuid(), key.toString());
		}
		return out;
	}
}
