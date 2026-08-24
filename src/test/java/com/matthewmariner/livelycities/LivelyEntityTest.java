package com.matthewmariner.livelycities;

import com.matthewmariner.livelycities.data.EntityRecord;
import com.matthewmariner.livelycities.data.PointRecord;
import java.util.List;
import net.runelite.api.AnimationController;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The wrapper's own rules: what gets cached, what gets latched, and what gets
 * retried. {@link EntitySceneTest} drives the same code through the scene; this
 * one asserts on a single entity so a failure names the rule.
 */
public class LivelyEntityTest
{
	private static final int REGION = 12852;
	private static final WorldPoint PLAYER = new WorldPoint(3225, 3360, 0);

	/** Not a multiple of 256, so it can never be mistaken for a travel facing. */
	private static final int BASE_ORIENTATION = 500;

	private FakeClient client;
	private FakeRegions definitions;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		definitions = new FakeRegions();
		view = FakeWorldView.around(PLAYER, REGION);
	}

	private LivelyEntity entity(int... modelIds)
	{
		return new LivelyEntity(client, definitions.citizen(REGION, 3225, 3358, 0, modelIds));
	}

	@Test
	public void aCompleteModelSpawnsAndIsBuiltOnlyOnce()
	{
		LivelyEntity entity = entity(217, 218);

		assertTrue(entity.spawn(view));
		assertTrue(entity.isActive());
		assertEquals(1, client.registeredCount());
		assertEquals(2, client.loadModelDataCalls());
		assertEquals("both parts must reach the merge", 2, client.lastMergePartCount());

		// Out and back in: an activate, not a rebuild.
		assertTrue(entity.despawn());
		assertEquals(0, client.registeredCount());
		client.resetCounters();
		assertTrue(entity.spawn(view));
		assertEquals("the model is cached", 0, client.loadModelDataCalls());
		assertEquals(1, client.registeredCount());
	}

	@Test
	public void aPartialModelIsNotSpawnedAndNotCached()
	{
		LivelyEntity entity = entity(217, 218, 219);
		client.setUnloadable(219);

		assertFalse("two thirds of a citizen is not a citizen", entity.spawn(view));
		assertFalse(entity.isActive());
		assertEquals("nothing may be merged from a partial resolve", 0, client.mergeCalls());
		assertFalse("a missing part is transient, not structural", entity.isBroken());

		// The missing part turns up. Because nothing was cached, what gets built
		// is the whole model, not the two parts that happened to load first.
		FakeClient warm = new FakeClient();
		LivelyEntity healed = new LivelyEntity(warm, entity.getDefinition());
		assertTrue(healed.spawn(view));
		assertEquals(3, warm.lastMergePartCount());
	}

	@Test
	public void aColdCacheIsRetriedUpToTheBudgetAndThenLeftAlone()
	{
		LivelyEntity entity = entity(217);
		client.setCacheCold(true);

		// The first attempt is immediate.
		assertFalse(entity.spawn(view));
		assertEquals(1, client.loadModelDataCalls());

		// The retries are spaced out rather than fired on the next tick: the
		// cache being waited on warms up over seconds.
		for (int pass = 1; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			assertFalse(entity.spawn(view));
		}
		assertEquals("a retry must not be spent on the very next pass", 1, client.loadModelDataCalls());

		for (int attempt = 2; attempt <= LivelyEntity.MAX_MODEL_ATTEMPTS; attempt++)
		{
			passesUntilRetry(entity);
			assertEquals("one client call per attempt", attempt, client.loadModelDataCalls());
		}

		// Budget spent: the client is not asked again however long we wait, and
		// the entity is still not broken — a cold cache is not a verdict on the
		// model id.
		passesUntilRetry(entity);
		passesUntilRetry(entity);
		assertEquals(LivelyEntity.MAX_MODEL_ATTEMPTS, client.loadModelDataCalls());
		assertFalse(entity.isBroken());

		// A scene load hands the budget back, and by then the cache is warm.
		client.setCacheCold(false);
		entity.onScopeEntered();
		assertTrue(entity.spawn(view));
		assertTrue(entity.isActive());
	}

	/** Drives exactly enough failed passes to spend the next spaced-out retry. */
	private void passesUntilRetry(LivelyEntity entity)
	{
		for (int pass = 0; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			assertFalse(entity.spawn(view));
		}
	}

	// --- npcAppearanceId: the body comes from a composition -------------------

	private static final int WHITE_KNIGHT = 1798;

	private LivelyEntity npcDressed(int npcId)
	{
		return new LivelyEntity(client, definitions.npcDressed(REGION, 3225, 3358, npcId));
	}

	/**
	 * The whole mechanism, end to end: the composition's model ids are the ones
	 * loaded, and its palette is the one applied.
	 */
	@Test
	public void anNpcDressedEntityBuildsTheCompositionsModelsAndWearsItsPalette()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.recoloured(
			"White Knight",
			new int[]{217, 305, 246},
			new short[]{(short) 54397, 20},
			new short[]{(short) 33694, 21}));

		LivelyEntity entity = npcDressed(WHITE_KNIGHT);

		assertTrue(entity.spawn(view));
		assertEquals("one composition lookup", 1, client.npcDefinitionsRequested().size());
		assertEquals("three model parts, from the NPC", 3, client.loadModelDataCalls());
		assertEquals(3, client.lastMergePartCount());

		List<String> calls = client.lastMerged().calls();
		assertTrue("the NPC's palette has to be applied: " + calls,
			calls.contains("recolor " + (short) 54397 + "->" + (short) 33694));
		assertTrue("and the second pair too: " + calls, calls.contains("recolor 20->21"));
		assertTrue("cloneColors() must still come first, or the client's cached model is repainted",
			calls.indexOf("cloneColors") >= 0
				&& calls.indexOf("cloneColors") < calls.indexOf("recolor 20->21"));

		NpcAppearance appearance = entity.getAppearance();
		assertNotNull(appearance);
		assertEquals(WHITE_KNIGHT, appearance.getNpcId());
	}

	/**
	 * Precedence, at the only place it is observable.
	 *
	 * <p>{@code EntityDefinitionTest} asserts the record keeps both fields; this
	 * asserts which one is actually built. Written with a record rather than a
	 * {@link FakeRegions} fixture because the shipped data deliberately never carries
	 * both, so there is no fixture for it and there should not be.
	 */
	@Test
	public void whenARecordCarriesBothTheNpcAppearanceIsWhatGetsBuilt()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", 900, 901));

		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.uuid = "55555555-5555-4555-8555-555555555555";
		record.name = "Wearing both";
		PointRecord tile = new PointRecord();
		tile.x = 3225;
		tile.y = 3358;
		tile.plane = 0;
		record.worldLocation = tile;
		record.modelIds = new int[]{217, 305};
		record.modelRecolorFind = new int[]{1};
		record.modelRecolorReplace = new int[]{2};
		record.npcAppearanceId = WHITE_KNIGHT;

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		LivelyEntity entity = new LivelyEntity(client, definition);

		assertTrue(entity.spawn(view));
		assertEquals("the NPC's two models, not the record's two", 2, client.loadModelDataCalls());
		assertFalse("the record's own palette must not be applied to somebody else's model: "
				+ client.lastMerged().calls(),
			client.lastMerged().calls().contains("recolor 1->2"));
		assertFalse("nor may the record's colours be cloned for nothing",
			client.lastMerged().calls().contains("cloneColors"));
	}

	/**
	 * An NPC id that will not resolve is transient, not structural — the same verdict
	 * a missing model gets, and for the same reason: the composition comes out of the
	 * same cache archives, so a cold-cache miss must not cost the entity the session.
	 *
	 * <p>The budget is shared with the model loads rather than being a second one, so
	 * the total number of client calls a broken entity can make per scene load stays
	 * bounded at {@link LivelyEntity#MAX_MODEL_ATTEMPTS} however it is broken.
	 */
	@Test
	public void anUnresolvableNpcIdIsRetriedOnTheModelBudgetAndNeverLatched()
	{
		// Nothing registered for this id, so FakeClient throws — the real 1.12.36
		// behaviour for an id whose archive entry is gone.
		LivelyEntity entity = npcDressed(4242);

		assertFalse(entity.spawn(view));
		assertFalse("a composition miss is not a verdict on the entity", entity.isBroken());
		assertEquals("the first attempt is immediate", 1, client.npcDefinitionsRequested().size());
		assertEquals("and nothing may be built from nothing", 0, client.loadModelDataCalls());
		assertEquals(0, client.mergeCalls());

		for (int pass = 1; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			assertFalse(entity.spawn(view));
		}
		assertEquals("a retry must not be spent on the very next pass",
			1, client.npcDefinitionsRequested().size());

		for (int attempt = 2; attempt <= LivelyEntity.MAX_MODEL_ATTEMPTS; attempt++)
		{
			passesUntilRetry(entity);
			assertEquals("one composition lookup per attempt",
				attempt, client.npcDefinitionsRequested().size());
		}

		passesUntilRetry(entity);
		passesUntilRetry(entity);
		assertEquals("the budget bounds it",
			LivelyEntity.MAX_MODEL_ATTEMPTS, client.npcDefinitionsRequested().size());
		assertFalse(entity.isBroken());

		// A scene load hands the budget back, and by then the NPC is available.
		client.withNpc(4242, FakeNpcComposition.of("Late arrival", 217));
		entity.onScopeEntered();
		assertTrue(entity.spawn(view));
		assertNotNull(entity.getAppearance());
	}

	/**
	 * A composition that resolves to nothing drawable fails the same way, and it has
	 * to be tested separately from the throwing id: they are different branches in
	 * {@link NpcAppearance} and only one of them involves an exception.
	 */
	@Test
	public void aCompositionWithNoModelsDoesNotSpawnAndDoesNotHalfBuild()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.withoutModels("Bodyless", new int[0]));

		LivelyEntity entity = npcDressed(WHITE_KNIGHT);

		assertFalse(entity.spawn(view));
		assertFalse(entity.isActive());
		assertEquals("no part may be loaded from an empty composition", 0, client.loadModelDataCalls());
		assertEquals("and nothing may be merged", 0, client.mergeCalls());
		assertFalse(entity.isBroken());
		assertNull(entity.getAppearance());
	}

	/**
	 * The composition is asked for once and then kept.
	 *
	 * <p><b>Exercised through a partial model load, which is the only path that can
	 * see it.</b> Once the model is built, {@code trySpawn} never calls
	 * {@code loadParts()} again — so walking out of a region and back in cannot tell a
	 * cached appearance from an uncached one, and a test written that way is green
	 * whatever the cache does. (Mutation testing found exactly that: deleting the
	 * cache check left the whole suite green.) The reachable case is a composition that
	 * resolves while one of the models it names does not: the appearance is kept, the
	 * model is not, and the next retry has to re-ask the <i>model</i> cache without
	 * re-asking for the composition.
	 */
	@Test
	public void theCompositionIsResolvedOnceAndKeptAcrossAModelRetry()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", 217, 305, 246));
		client.setUnloadable(246);

		LivelyEntity entity = npcDressed(WHITE_KNIGHT);

		assertFalse("a part is missing, so nothing spawns", entity.spawn(view));
		assertEquals(1, client.npcDefinitionsRequested().size());
		assertNotNull("but the appearance resolved and must be kept", entity.getAppearance());
		assertEquals("all three ids came from the composition", 3, client.loadModelDataCalls());

		passesUntilRetry(entity);

		assertEquals("the composition must not be looked up again",
			1, client.npcDefinitionsRequested().size());
		assertEquals("while the model cache is asked again", 6, client.loadModelDataCalls());

		// And when the part turns up, it builds from the composition's full list.
		FakeClient warm = new FakeClient();
		warm.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", 217, 305, 246));
		LivelyEntity healed = new LivelyEntity(warm, entity.getDefinition());
		assertTrue(healed.spawn(view));
		assertEquals(3, warm.lastMergePartCount());
	}

	/**
	 * The built model is cached across a despawn/respawn, exactly as it is for an
	 * entity dressed from raw model ids — so an NPC-dressed citizen costs an activate
	 * rather than a rebuild when the player walks back.
	 */
	@Test
	public void anNpcDressedEntitysModelIsCachedAcrossADespawn()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", 217, 305));

		LivelyEntity entity = npcDressed(WHITE_KNIGHT);
		assertTrue(entity.spawn(view));

		assertTrue(entity.despawn());
		client.resetCounters();
		entity.onScopeEntered();
		assertTrue(entity.spawn(view));

		assertEquals("the model is cached", 0, client.loadModelDataCalls());
		assertEquals(0, client.mergeCalls());
	}

	/**
	 * A model part the composition names but the cache will not produce is still a
	 * partial build, and still refused.
	 *
	 * <p>Sourcing the ids from an NPC changes where the list came from, not what a
	 * missing part means — "no legs" is the failure that got the predecessor plugin
	 * disabled, and it must not come back through the new door.
	 */
	@Test
	public void aPartialBuildFromACompositionIsStillRefused()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", 217, 305, 246));
		client.setUnloadable(246);

		LivelyEntity entity = npcDressed(WHITE_KNIGHT);

		assertFalse("two thirds of a knight is not a knight", entity.spawn(view));
		assertEquals("nothing may be merged from a partial resolve", 0, client.mergeCalls());
		assertFalse(entity.isBroken());
	}

	@Test
	public void aStructuralFailureIsLatchedAndNeverRetried()
	{
		LivelyEntity entity = entity(217);
		client.setThrowing(217);

		assertFalse(entity.spawn(view));
		assertTrue("a throw is structural", entity.isBroken());
		assertFalse(entity.isWanted());

		client.resetCounters();
		assertFalse(entity.spawn(view));
		assertEquals("a broken entity must not touch the client again", 0, client.loadModelDataCalls());
	}

	@Test
	public void aRefusedActivationIsLatchedRatherThanWarnedEveryTick()
	{
		LivelyEntity entity = entity(217);
		client.refuseRegistration();

		assertFalse("setActive(true) did not take", entity.spawn(view));
		assertTrue("and that is not going to change next tick", entity.isBroken());
		assertFalse(entity.isWanted());

		client.resetCounters();
		assertFalse(entity.spawn(view));
		assertEquals("nothing may be rebuilt for a latched entity", 0, client.createObjectCalls());
		assertEquals(0, client.loadModelDataCalls());
	}

	@Test
	public void despawnIsIdempotentAndReportsWhetherItDidAnything()
	{
		LivelyEntity entity = entity(217);

		assertFalse("nothing to deactivate yet", entity.despawn());
		assertTrue(entity.spawn(view));
		assertTrue("this call deactivated something", entity.despawn());
		assertFalse("this one had nothing left to do", entity.despawn());
		assertEquals(0, client.registeredCount());
	}

	@Test
	public void recoloursCloneTheColoursBeforeTouchingThem()
	{
		// 54397 -> 33694 is a real pair from 12850.json; both wrap negative.
		LivelyEntity entity = new LivelyEntity(client, recoloured(54397, 33694));

		assertTrue(entity.spawn(view));

		List<String> calls = client.lastMerged().calls();
		assertNotNull(calls);
		int cloned = calls.indexOf("cloneColors");
		int recoloured = calls.indexOf("recolor " + (short) 54397 + "->" + (short) 33694);
		assertTrue("cloneColors() must happen: recolor() mutates the merge in place", cloned >= 0);
		assertTrue("the recolour must actually be applied", recoloured >= 0);
		assertTrue("cloneColors() must come first, or the client's cached model is repainted",
			cloned < recoloured);
	}

	@Test
	public void anEntityWithNoRecoloursDoesNotCloneColours()
	{
		LivelyEntity entity = entity(217);

		assertTrue(entity.spawn(view));
		assertFalse("no recolours, nothing to protect",
			client.lastMerged().calls().contains("cloneColors"));
	}

	@Test
	public void anEntityOutsideTheLoadedSceneIsNotAFailure()
	{
		// 200 tiles away: inside the region, outside the 104-tile scene.
		LivelyEntity entity = new LivelyEntity(client,
			definitions.citizen(REGION, 3225, 3560, 0, 217));

		assertFalse(entity.spawn(view));
		assertFalse("outside the scene is a wait, not a fault", entity.isBroken());
		assertEquals("and must not spend a model load", 0, client.loadModelDataCalls());
	}

	@Test
	public void theIdleAnimationIsAskedForByItsDatasetName()
	{
		LivelyEntity entity = new LivelyEntity(client, animated("Fletching"));

		assertTrue(entity.spawn(view));
		assertEquals(1, client.animationsLoaded().size());
		assertEquals(Integer.valueOf(LivelyAnimation.Fletching.getId()), client.animationsLoaded().get(0));
	}

	/**
	 * An animation the client will not give us must not be cached as a controller.
	 *
	 * <p>{@code new AnimationController(client, id)} calls
	 * {@code client.loadAnimation(id)} and hands the result — null or not —
	 * straight to {@code setAnimation}. A controller holding a null animation is
	 * inert forever: its {@code tick}, {@code loop} and {@code getPackedFrame} all
	 * return immediately. This class used to keep the first controller it built,
	 * so one cold-cache miss froze the citizen for the session.
	 *
	 * <p>The citizen still spawns while it waits. Standing in the right place
	 * holding still beats not being there, which is the same call the plugin makes
	 * for an animation name it does not recognise.
	 */
	@Test
	public void anAnimationThatWillNotLoadIsNotCachedAndIsRetried()
	{
		client.setUnloadableAnimations(LivelyAnimation.Fletching.getId());
		LivelyEntity entity = new LivelyEntity(client, animated("Fletching"));

		assertTrue("a citizen with no animation yet still belongs on its tile", entity.spawn(view));
		assertFalse("a missing animation is not a structural fault", entity.isBroken());
		assertNull("nothing may be installed while the animation is missing",
			entity.getInstalledController());
		assertEquals(1, client.animationsLoaded().size());

		// The cache warms up. The retry is spaced like the model one — a cold cache
		// warms over seconds, not ticks — so the next few passes are deliberately
		// silent, and then it asks again.
		client.clearUnloadableAnimations();
		for (int pass = 1; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			entity.retryMissingAnimation();
		}
		assertEquals("the retry is spaced, not per pass", 1, client.animationsLoaded().size());
		assertNull(entity.getInstalledController());

		entity.retryMissingAnimation();
		assertEquals("and then it asks the client again", 2, client.animationsLoaded().size());
		AnimationController installed = entity.getInstalledController();
		assertNotNull("and install what it got", installed);
		assertNotNull("which has to be a controller with a real animation", installed.getAnimation());
		assertEquals(LivelyAnimation.Fletching.getId(), installed.getAnimation().getId());

		// And having resolved once, it is cached like any other: no more asking.
		entity.retryMissingAnimation();
		assertEquals("a resolved animation is never re-requested", 2, client.animationsLoaded().size());
		assertSame(installed, entity.getInstalledController());
	}

	/**
	 * The retry is bounded, exactly like the model-data retry it mirrors.
	 *
	 * <p>An id with no frames at all — {@code LivelyAnimation.BeeIdle} is id 0 —
	 * never resolves however long the cache warms. Without a budget that would be
	 * one {@code loadAnimation} call and one log line per game tick, forever, which
	 * is the noise the model path already exists to avoid.
	 */
	@Test
	public void aMissingAnimationIsRetriedAtMostThreeTimesPerScopeEntry()
	{
		client.setUnloadableAnimations(LivelyAnimation.Fletching.getId());
		LivelyEntity entity = new LivelyEntity(client, animated("Fletching"));

		assertTrue(entity.spawn(view));
		assertEquals("the spawn spends the first attempt", 1, client.animationsLoaded().size());

		// Hammer it. The backoff swallows everything until the spacing is met, and
		// the budget stops it after three attempts however long we keep asking.
		for (int pass = 0; pass < LivelyEntity.RETRY_BACKOFF_PASSES * 10; pass++)
		{
			entity.retryMissingAnimation();
		}

		assertEquals("three attempts per scope entry, no more",
			LivelyEntity.MAX_ANIMATION_ATTEMPTS, client.animationsLoaded().size());
		assertNull(entity.getInstalledController());
		assertFalse("an unresolvable animation is still not a structural fault", entity.isBroken());

		// A scene load is the granularity at which a cold cache is worth re-testing.
		entity.onScopeEntered();
		entity.retryMissingAnimation();
		assertEquals("entering scope hands the budget back",
			LivelyEntity.MAX_ANIMATION_ATTEMPTS + 1, client.animationsLoaded().size());
	}

	/**
	 * Scenery, and any citizen whose animation name this build does not know, are
	 * static by design. Neither is a cache miss, so neither may cost a client call
	 * on every visibility pass forever.
	 */
	@Test
	public void anEntityWithNoAnimationAtAllIsNeverRetried()
	{
		LivelyEntity scenery = entity(217);
		assertTrue(scenery.spawn(view));

		LivelyEntity unknownName = new LivelyEntity(client, animated("PolishingTheBrasswork"));
		assertTrue(unknownName.spawn(view));

		for (int pass = 0; pass < 50; pass++)
		{
			scenery.retryMissingAnimation();
			unknownName.retryMissingAnimation();
		}

		assertEquals("a model with no animation has nothing to wait for",
			0, client.animationsLoaded().size());
	}

	/**
	 * A stationary citizen never asks for its move animation, so the animation
	 * cache is not warmed for something that will never play.
	 */
	@Test
	public void aStationaryCitizenNeverLoadsAMoveAnimation()
	{
		EntityRecord record = record();
		record.idleAnimation = "HumanIdle";
		record.moveAnimation = "HumanWalk";
		LivelyEntity entity = new LivelyEntity(client, definition(record));

		assertTrue(entity.spawn(view));
		entity.advanceTick();
		entity.advanceFrame(view, 0.5f);

		assertEquals("only the idle animation is ever needed", 1, client.animationsLoaded().size());
		assertEquals(Integer.valueOf(LivelyAnimation.HumanIdle.getId()), client.animationsLoaded().get(0));
	}

	/**
	 * The animation switch, and the thing that made the predecessor look like it
	 * had no smoothing at all.
	 *
	 * <p>An {@code AnimationController} <i>is</i> the playback position: handing a
	 * fresh one to the object, or calling {@code setAnimation} on the one it has,
	 * resets the frame counter. So the count of installs has to be the count of
	 * idle↔move switches. If it were the count of game ticks, every animation
	 * would restart 1.6 times a second and no amount of per-frame advancing would
	 * show.
	 */
	@Test
	public void theControllerIsInstalledOncePerSwitchAndNotOncePerTick()
	{
		LivelyEntity entity = wanderer();
		assertTrue(entity.spawn(view));

		FakeRuneLiteObject object = client.lastObject();
		assertEquals("spawning installs the idle animation", 1, object.animationControllerInstalls());

		CitizenWalk walk = entity.getWalk();
		assertNotNull(walk);

		// Stand still through a whole idle interval. Nothing should be reinstalled.
		for (int tick = 1; tick < CitizenWalk.IDLE_TICKS_BEFORE_NEW_DESTINATION; tick++)
		{
			entity.advanceTick();
			assertFalse(walk.isMoving());
		}
		assertEquals("standing still must not reinstall anything",
			1, object.animationControllerInstalls());

		// Walk. That is one switch, however many ticks the walk lasts.
		int ticksWalking = 0;
		while (!walk.isMoving() && ticksWalking < 200)
		{
			entity.advanceTick();
			ticksWalking++;
		}
		assertTrue("the fixture must get the citizen walking", walk.isMoving());
		assertEquals("starting to walk is one switch", 2, object.animationControllerInstalls());

		int installsWhileWalking = object.animationControllerInstalls();
		int stepsTaken = 0;
		while (walk.isMoving() && stepsTaken < 200)
		{
			entity.advanceTick();
			stepsTaken++;
			if (walk.isMoving())
			{
				assertEquals("every further step must keep the same controller",
					installsWhileWalking, object.animationControllerInstalls());
			}
		}

		assertFalse("the fixture must get the citizen to stop again", walk.isMoving());
		assertEquals("stopping is the second switch", 3, object.animationControllerInstalls());

		// Both animations were asked for exactly once each.
		assertEquals(2, client.animationsLoaded().size());
		assertEquals(Integer.valueOf(LivelyAnimation.HumanIdle.getId()), client.animationsLoaded().get(0));
		assertEquals(Integer.valueOf(LivelyAnimation.HumanWalk.getId()), client.animationsLoaded().get(1));
	}

	/**
	 * The smoothing claim, asserted on the frame counter itself.
	 *
	 * <p>The client advances the animation once per frame by calling
	 * {@code RuneLiteObject.tick(ticksSinceLastFrame)} — that call is simulated
	 * here, because it is the client's and not ours. What is being tested is that
	 * the plugin's own per-tick and per-frame work does not throw that progress
	 * away.
	 */
	@Test
	public void theAnimationFrameSurvivesEverythingThePluginDoesToTheObject()
	{
		LivelyEntity entity = wanderer();
		assertTrue(entity.spawn(view));

		FakeRuneLiteObject object = client.lastObject();
		AnimationController controller = entity.getInstalledController();
		assertNotNull("the idle animation should be driving the model", controller);

		// The client, advancing the animation between game ticks.
		for (int frame = 0; frame < 3; frame++)
		{
			object.tick(FakeAnimation.CLIENT_TICKS_PER_FRAME);
		}
		int frameBefore = controller.getFrame();
		assertTrue("the fixture animation has to actually advance", frameBefore > 0);

		// A game tick's worth of the plugin's work, while the citizen is idle.
		entity.advanceTick();
		entity.advanceFrame(view, 0f);
		entity.advanceFrame(view, 0.5f);
		entity.advanceFrame(view, 1f);

		assertSame("the same controller must still be driving the model",
			controller, entity.getInstalledController());
		assertEquals("nothing the plugin does per tick or per frame may reset the animation",
			frameBefore, controller.getFrame());
		assertEquals("and the plugin must never advance it either — that is the client's job, "
			+ "once per frame, and a second caller doubles every animation's speed",
			3, object.tickCalls());
	}

	@Test
	public void aWanderingCitizenFacesItsTravelDirectionAndTurnsBackWhenItStops()
	{
		LivelyEntity entity = wanderer();
		assertTrue(entity.spawn(view));

		FakeRuneLiteObject object = client.lastObject();
		assertEquals("it spawns facing the way it was authored facing",
			BASE_ORIENTATION, object.getOrientation());

		CitizenWalk walk = entity.getWalk();
		assertNotNull(walk);

		boolean sawTravelFacing = false;
		boolean sawResetToBase = false;

		for (int tick = 0; tick < 400; tick++)
		{
			entity.advanceTick();

			assertEquals("the object's orientation must be the walk's",
				walk.getOrientation(), object.getOrientation());

			if (walk.isMoving())
			{
				assertNotEquals("a moving citizen must not still be facing its base orientation",
					BASE_ORIENTATION, object.getOrientation());
				sawTravelFacing = true;
			}
			else if (sawTravelFacing)
			{
				assertEquals("stopping must turn it back to its base orientation",
					BASE_ORIENTATION, object.getOrientation());
				sawResetToBase = true;
			}
		}

		assertTrue("the fixture must get the citizen walking", sawTravelFacing);
		assertTrue("and must get it to stop again", sawResetToBase);
	}

	@Test
	public void nothingThatStandsStillDoesAnyPerTickOrPerFrameWork()
	{
		// A base orientation of 500 rather than the default 0: an entity whose
		// authored facing is 0 cannot tell "left alone" from "reset to zero".
		EntityRecord record = record();
		record.baseOrientation = BASE_ORIENTATION;
		LivelyEntity entity = new LivelyEntity(client, definition(record));
		assertTrue(entity.spawn(view));
		assertEquals(BASE_ORIENTATION, client.lastObject().getOrientation());

		FakeRuneLiteObject object = client.lastObject();
		int x = object.getX();
		int y = object.getY();
		int orientation = object.getOrientation();
		int installs = object.animationControllerInstalls();

		for (int tick = 0; tick < 50; tick++)
		{
			entity.advanceTick();
			entity.advanceFrame(view, tick / 50f);
		}

		assertEquals(x, object.getX());
		assertEquals(y, object.getY());
		assertEquals(orientation, object.getOrientation());
		assertEquals(installs, object.animationControllerInstalls());
	}

	/**
	 * A wanderer whose move animation name is not one the plugin knows keeps
	 * whatever it idles as, rather than freezing into a static model mid-step.
	 */
	@Test
	public void anUnknownMoveAnimationFallsBackToTheIdleOneRatherThanNothing()
	{
		EntityRecord record = wandererRecord();
		record.moveAnimation = "PolishingTheBrasswork";
		LivelyEntity entity = new LivelyEntity(client, definition(record));

		assertTrue(entity.spawn(view));
		FakeRuneLiteObject object = client.lastObject();
		CitizenWalk walk = entity.getWalk();
		assertNotNull(walk);

		for (int tick = 0; tick < 200 && !walk.isMoving(); tick++)
		{
			entity.advanceTick();
		}
		assertTrue(walk.isMoving());

		assertEquals("only the idle animation resolved, so only it is ever loaded",
			1, client.animationsLoaded().size());
		assertEquals("and the object keeps the controller it already had",
			1, object.animationControllerInstalls());
		assertNotNull("it must not have been reduced to a static model",
			entity.getInstalledController());
	}

	/**
	 * A citizen standing still is placed once per game tick, not once per frame.
	 *
	 * <p>Not micro-optimisation for its own sake: {@code setLocation} runs
	 * {@code Perspective.getTileHeight} against the live scene, this is a frame
	 * handler, and most citizens are idle most of the time. A walking one is a
	 * different matter — that is the whole point of the frame pass — so both
	 * branches are asserted.
	 */
	@Test
	public void anIdleWandererIsPlacedOncePerTickAndAWalkingOneOncePerFrame()
	{
		LivelyEntity entity = wanderer();
		assertTrue(entity.spawn(view));

		FakeRuneLiteObject object = client.lastObject();
		CitizenWalk walk = entity.getWalk();
		assertNotNull(walk);
		assertFalse("it starts idle", walk.isMoving());

		int afterSpawn = object.setLocationCalls();
		for (int frame = 0; frame < 40; frame++)
		{
			entity.advanceFrame(view, frame / 40f);
		}
		assertEquals("forty frames of standing still cost one placement",
			afterSpawn + 1, object.setLocationCalls());

		// One more tick of standing still costs one more placement, not forty.
		entity.advanceTick();
		assertFalse(walk.isMoving());
		for (int frame = 0; frame < 40; frame++)
		{
			entity.advanceFrame(view, frame / 40f);
		}
		assertEquals(afterSpawn + 2, object.setLocationCalls());

		// Now get it walking: every frame has to count.
		for (int tick = 0; tick < 200 && !walk.isMoving(); tick++)
		{
			entity.advanceTick();
		}
		assertTrue("the fixture must get the citizen walking", walk.isMoving());

		int beforeWalkingFrames = object.setLocationCalls();
		for (int frame = 0; frame < 40; frame++)
		{
			entity.advanceFrame(view, frame / 40f);
		}
		assertEquals("a walking citizen is re-placed on every single frame",
			beforeWalkingFrames + 40, object.setLocationCalls());
	}

	@Test
	public void aDeactivatedWandererDoesNoWork()
	{
		LivelyEntity entity = wanderer();
		assertTrue(entity.spawn(view));
		assertTrue(entity.despawn());

		FakeRuneLiteObject object = client.lastObject();
		int installs = object.animationControllerInstalls();
		CitizenWalk walk = entity.getWalk();
		assertNotNull(walk);
		WorldPoint tile = walk.currentTile();

		for (int tick = 0; tick < 100; tick++)
		{
			entity.advanceTick();
			entity.advanceFrame(view, 0.5f);
		}

		assertEquals("a citizen nobody can see does not walk", tile, walk.currentTile());
		assertEquals(installs, object.animationControllerInstalls());
		assertEquals(0, client.registeredCount());
	}

	/**
	 * A wandering citizen with a small box around its start tile, and a base
	 * orientation of 500 — a real value from region 12338, and not one of the
	 * eight travel orientations, so "turned back to base" is distinguishable from
	 * "never turned".
	 */
	private LivelyEntity wanderer()
	{
		return new LivelyEntity(client, definition(wandererRecord()));
	}

	private static EntityRecord wandererRecord()
	{
		EntityRecord record = record();
		record.entityType = "WanderingCitizen";
		record.baseOrientation = BASE_ORIENTATION;
		record.idleAnimation = "HumanIdle";
		record.moveAnimation = "HumanWalk";
		record.wanderBoxBL = point(3221, 3354, 0);
		record.wanderBoxTR = point(3229, 3362, 0);
		return record;
	}

	private static EntityDefinition recoloured(int find, int replace)
	{
		EntityRecord record = record();
		record.modelRecolorFind = new int[]{find};
		record.modelRecolorReplace = new int[]{replace};
		return definition(record);
	}

	private static EntityDefinition animated(String idleAnimation)
	{
		EntityRecord record = record();
		record.idleAnimation = idleAnimation;
		return definition(record);
	}

	private static EntityDefinition definition(EntityRecord record)
	{
		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		return definition;
	}

	private static EntityRecord record()
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.name = "Test subject";
		record.uuid = "55555555-5555-4555-8555-555555555555";
		record.worldLocation = point(3225, 3358, 0);
		record.modelIds = new int[]{217};
		return record;
	}

	private static PointRecord point(int x, int y, int plane)
	{
		PointRecord p = new PointRecord();
		p.x = x;
		p.y = y;
		p.plane = plane;
		return p;
	}
}
