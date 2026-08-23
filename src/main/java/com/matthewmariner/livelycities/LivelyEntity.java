package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * One {@link EntityDefinition} bound to one {@link RuneLiteObject}.
 *
 * <p><b>Every method here must run on the client thread.</b> All of it reaches
 * into live client state: {@code loadModelData} and {@code mergeModels} read the
 * model cache, {@code light} allocates against it, {@code setActive} adds to and
 * removes from the client's registered-object list, and
 * {@code RuneLiteObject.setLocation(LocalPoint, int)} calls
 * {@code Perspective.getTileHeight(client, ..)}, which reads the scene's tile
 * settings and tile heights to work out {@code z}. That last one is the
 * {@link RuneLiteObject} override, verified by disassembling 1.12.36 — the base
 * {@code RuneLiteObjectController.setLocation} really does nothing but
 * {@code setX}/{@code setY}/{@code setWorldView}/{@code setLevel}, so reading
 * only the base class makes the call look thread-safe when it is not. The client
 * does the {@code z} fix-up for us; we never set it ourselves.
 *
 * <p>The built model is cached for the lifetime of the wrapper, so walking in
 * and out of a region costs an activate/deactivate rather than a rebuild.
 *
 * <p>Two kinds of failure, kept apart on purpose, because caching the wrong one
 * loses an entity for the whole session:
 * <ul>
 *   <li><b>Structural</b> — the client refuses to create an object, the merge
 *       returns nothing, lighting returns nothing, {@code setActive(true)} does
 *       not take, or anything throws. Nothing about the next tick will be
 *       different, so the entity is marked {@link #broken} and never retried.
 *       That is what stops a bad model producing one warning per game tick,
 *       forever.</li>
 *   <li><b>Transient</b> — {@code loadModelData} returned null for some or all
 *       of the parts, or {@code loadAnimation} returned null for an animation the
 *       record asked for. On a cold cache both are routine and say nothing about
 *       the id: the field report behind this code had 84 distinct model ids
 *       missing right after login and present later, and animations come out of
 *       the same cache. So neither is latched; both are retried, at most
 *       {@link #MAX_MODEL_ATTEMPTS} / {@link #MAX_ANIMATION_ATTEMPTS} times per
 *       scene load ({@link #onScopeEntered()} hands back both budgets) and spaced
 *       by {@link #RETRY_BACKOFF_PASSES}, which lets a warm cache heal the entity
 *       without a per-tick retry storm.</li>
 * </ul>
 *
 * <p>The difference between the two is which way the entity fails while it waits.
 * A missing model means no citizen at all, so the spawn is deferred. A missing
 * animation means a citizen standing in the right place holding still, which is
 * better than an absent one — so it spawns, and the animation is picked up
 * whenever the cache produces it.
 *
 * <p>A partial resolve counts as a failure, not as a model. Spawning the parts
 * that did load is how a citizen ends up with no head or no boots — and since
 * the model is cached, it would have stayed that way for the session.
 *
 * <p><b>Movement is split across two clocks</b>, and which half does what is not
 * an implementation detail:
 * <ul>
 *   <li>{@link #advanceTick()} is called once per game tick. It steps the walk one
 *       tile, faces the citizen the way it is going, and switches between the
 *       idle and move animations. Walking speed therefore comes from the game's
 *       clock, not the frame rate.</li>
 *   <li>{@link #advanceFrame(WorldView, float)} is called once per frame. It slides
 *       the drawn position between the tile the citizen left and the tile it is
 *       heading for. Nothing else per frame — in particular <i>not</i>
 *       {@code RuneLiteObject.tick(..)}, which the client already calls once per
 *       frame for every registered object and which is what advances the
 *       animation.</li>
 * </ul>
 */
@Slf4j
final class LivelyEntity
{
	/**
	 * Lighting used by the source dataset's authoring tool. Changing these
	 * changes the look of all 175 entities, so they stay put.
	 */
	private static final int LIGHT_AMBIENT = 64;
	private static final int LIGHT_CONTRAST = 850;
	private static final int LIGHT_X = -30;
	private static final int LIGHT_Y = -50;
	private static final int LIGHT_Z = -30;

	/**
	 * Model units per tile. The dataset stores scale/translate as tile
	 * fractions.
	 */
	private static final int UNITS_PER_TILE = 128;

	/**
	 * How many times a model-data miss is retried before this entity gives up
	 * until the next scene load. Three, spaced by
	 * {@link #RETRY_BACKOFF_PASSES}, so a genuinely absent model costs three
	 * attempts and three log lines per scene load rather than one per tick.
	 */
	static final int MAX_MODEL_ATTEMPTS = 3;

	/**
	 * Visibility passes between retries. The cache this is waiting on warms up
	 * over seconds, not ticks: three attempts on three consecutive ticks would
	 * spend the whole budget inside two seconds of a cold login and then leave
	 * the entity waiting for a border crossing. At 600ms a tick this spreads the
	 * three attempts over roughly half a minute, which is the timescale the
	 * field report describes.
	 */
	static final int RETRY_BACKOFF_PASSES = 25;

	/**
	 * How many times an animation that would not load is asked for again before
	 * this entity settles for a static model until the next scene load.
	 *
	 * <p>Same shape and the same reasoning as {@link #MAX_MODEL_ATTEMPTS}, because
	 * it is the same failure. {@code client.loadAnimation(id)} — which is what
	 * {@code new AnimationController(client, id)} calls, verified in the 1.12.36
	 * bytecode — returns {@code null} when the sequence it resolves has no frame
	 * lengths and is not a Maya animation, and the sequence comes out of the
	 * client's own cache archive just like a model does. A controller built on a
	 * {@code null} animation is inert forever: {@code tick}, {@code loop} and
	 * {@code getPackedFrame} all return immediately, so the object draws its base
	 * model unanimated. Caching one — which this class used to do unconditionally —
	 * froze the citizen for the rest of the session on a single cold-cache miss.
	 *
	 * <p>Two causes, and the retry is what tells them apart in a log rather than
	 * in a guess: a cold cache (transient, and this fixes it), or an id that has
	 * no frames at all (permanent — {@code LivelyAnimation.BeeIdle} is id 0, which
	 * is a prime suspect). Either way three attempts per scene load, spaced by
	 * {@link #RETRY_BACKOFF_PASSES}, is the difference between one line in the log
	 * and one line per game tick forever.
	 */
	static final int MAX_ANIMATION_ATTEMPTS = 3;

	private final Client client;
	private final EntityDefinition definition;

	/** The walk, for a wandering citizen; {@code null} for everything else. */
	private final CitizenWalk walk;

	private RuneLiteObject object;
	private Model model;
	private boolean broken;

	/**
	 * The two animation controllers, built on first use and then kept.
	 *
	 * <p>Kept rather than rebuilt on every switch because an
	 * {@code AnimationController} <i>is</i> the animation's playback position:
	 * constructing one, or calling {@code setAnimation} on one, resets its frame
	 * to zero. Re-creating a controller every game tick is exactly what makes a
	 * walk cycle look like a stutter instead of a walk — the client advances the
	 * frames between game ticks, and then something throws that progress away
	 * 1.6 times a second. Holding both means a citizen also resumes its walk
	 * cycle mid-stride rather than restarting it on every step.
	 *
	 * <p><b>Only ever assigned a controller that actually has an animation.</b>
	 * {@link #looping} returns null on a failed load and these stay null, so the
	 * next call tries again — see {@link #MAX_ANIMATION_ATTEMPTS}. Caching a
	 * controller whose animation is null is caching a permanently inert object,
	 * which is what this class used to do.
	 */
	private AnimationController idleController;
	private AnimationController moveController;

	/** The controller currently handed to the object, for the identity compare. */
	private AnimationController installed;

	/**
	 * True once the object sits at a position that cannot change again before the
	 * next game tick — i.e. the citizen has stopped and the frame pass has already
	 * placed it there.
	 *
	 * <p>Cleared by every spawn, every despawn and every tick, so a citizen is
	 * always placed at least once per game tick and is skipped only while it is
	 * genuinely standing still. That is most citizens most of the time, and
	 * {@code setLocation} is not free — it runs
	 * {@code Perspective.getTileHeight} against the live scene.
	 */
	private boolean positionSettled;

	/** Model-load attempts spent since the last {@link #onScopeEntered()}. */
	private int modelAttempts;

	/** Visibility passes since the last attempt, for the retry spacing. */
	private int passesSinceAttempt;

	/**
	 * Animation loads that came back {@code null} since the last
	 * {@link #onScopeEntered()}. Counts misses only, so resolving the move
	 * animation after the idle one does not spend anything.
	 */
	private int animationMisses;

	/** Visibility passes since the last animation miss, for the retry spacing. */
	private int passesSinceAnimationMiss;

	/**
	 * Set each visibility pass by {@link EntityScene}: whether this entity
	 * should be active right now.
	 */
	private boolean wanted;

	LivelyEntity(Client client, EntityDefinition definition)
	{
		this.client = client;
		this.definition = definition;
		this.walk = CitizenWalk.forDefinition(definition);
	}

	EntityDefinition getDefinition()
	{
		return definition;
	}

	/**
	 * @return this entity's walk, or {@code null} if it stands still — which is
	 * every {@code Scenery}, every {@code StationaryCitizen}, every
	 * {@code ScriptedCitizen}, and any {@code WanderingCitizen} whose box did not
	 * validate
	 */
	@Nullable
	CitizenWalk getWalk()
	{
		return walk;
	}

	boolean isWanted()
	{
		return wanted;
	}

	void setWanted(boolean wanted)
	{
		this.wanted = wanted;
	}

	boolean isBroken()
	{
		return broken;
	}

	/**
	 * Latches this entity out of every later pass, without a log line — the
	 * caller has more context and does the logging. For {@link EntityScene} to
	 * use when the throw came from outside {@link #spawn} / {@link #despawn}.
	 */
	void markBroken()
	{
		broken = true;
		wanted = false;
	}

	/**
	 * Hands back the model-load budget. Called when this entity's region enters
	 * scope: a scene load is the right granularity at which to re-test a cold
	 * model cache, and together with {@link #RETRY_BACKOFF_PASSES} it is what
	 * bounds the retries to a handful per border crossing instead of one per
	 * tick.
	 */
	void onScopeEntered()
	{
		modelAttempts = 0;
		passesSinceAttempt = 0;
		animationMisses = 0;
		passesSinceAnimationMiss = 0;
	}

	/**
	 * @return whether the client currently has this object registered. Asks the
	 * client rather than trusting local bookkeeping, so teardown evidence is
	 * real.
	 */
	boolean isActive()
	{
		return object != null && object.isActive();
	}

	/**
	 * Builds (once) and activates the object. Client thread only.
	 *
	 * @param worldView the view whose scene the entity is being placed in
	 * @return true if the object is active when this returns
	 */
	boolean spawn(WorldView worldView)
	{
		try
		{
			return trySpawn(worldView);
		}
		catch (RuntimeException e)
		{
			// The visibility pass runs from an EventBus handler. Letting this
			// out abandons every entity after this one in the pass — including
			// the ones that were supposed to deactivate — and does it again on
			// the next tick, because nothing would have marked the offender.
			// One entity's bad luck costs that entity.
			broken = true;
			wanted = false;
			log.warn("{}: threw while spawning, not retrying", definition.label(), e);
			return false;
		}
	}

	/**
	 * Deactivates the object if it is active. Client thread only.
	 *
	 * @return true if this call actually deactivated something
	 */
	boolean despawn()
	{
		try
		{
			if (!isActive())
			{
				return false;
			}

			object.setActive(false);
			positionSettled = false;
			log.debug("despawned {} (region {})", definition.label(), definition.getRegionId());
			return true;
		}
		catch (RuntimeException e)
		{
			// Same reasoning as spawn(), and the stakes are higher: this runs
			// from teardown loops that must reach every other entity.
			broken = true;
			wanted = false;
			log.warn("{}: threw while despawning", definition.label(), e);
			return false;
		}
	}

	private boolean trySpawn(WorldView worldView)
	{
		if (broken)
		{
			return false;
		}

		if (isActive())
		{
			return true;
		}

		// A wanderer comes back where it left off, not where it was authored: it
		// does not walk while deactivated, so its walk still holds the tile it was
		// standing on when the player walked away from it.
		LocalPoint location = walk == null
			? LocalPoint.fromWorld(worldView, definition.getWorldLocation())
			: walk.localPoint(worldView, 1f);
		if (location == null)
		{
			// Outside the loaded scene, or the view moved plane between the
			// candidate check and here. Not an error.
			return false;
		}

		if (object == null)
		{
			object = client.createRuneLiteObject();
			if (object == null)
			{
				log.warn("{}: client refused to create a RuneLiteObject", definition.label());
				broken = true;
				return false;
			}
		}

		if (model == null)
		{
			List<ModelData> parts = loadParts();
			if (parts == null)
			{
				// Transient: nothing cached, nothing latched, try again later.
				return false;
			}

			model = assemble(parts);
			if (model == null)
			{
				broken = true;
				return false;
			}

			object.setModel(model);
			object.setOrientation(definition.getOrientation());
			select(idleControllerOrNull());
		}

		object.setLocation(location, definition.getPlane());
		object.setActive(true);

		if (!object.isActive())
		{
			// The client took the call and still does not have the object. That
			// is not going to change on the next tick, and leaving it unlatched
			// is a WARN line per entity per tick, forever.
			log.warn("{}: setActive(true) did not take, not retrying", definition.label());
			broken = true;
			wanted = false;
			return false;
		}

		// The frame pass owns the exact position from here on; make sure it takes
		// one look at a citizen that has just come back.
		positionSettled = false;

		log.debug("spawned {} (region {})", definition.label(), definition.getRegionId());
		return true;
	}

	/**
	 * One game tick of walking, for a wandering citizen: take a step, face the way
	 * it is going, and switch between the idle and move animations.
	 *
	 * <p>Client thread only. Does nothing for anything that stands still, and
	 * nothing while deactivated — a citizen the player cannot see does not need to
	 * have been walking, and freezing it means it is still inside its box when the
	 * player comes back.
	 */
	void advanceTick()
	{
		if (walk == null || broken || !isActive())
		{
			return;
		}

		walk.tick();

		// Idle ↔ move. select() compares controllers by identity, so a citizen
		// mid-walk re-selects the one it already has and the object is left
		// alone — which is what keeps the animation's frame counter intact.
		select(walk.isMoving() ? moveControllerOrNull() : idleControllerOrNull());

		// Facing the direction of travel while moving, back to the authored
		// baseOrientation once it stops. CitizenWalk decides which; this only
		// writes it.
		object.setOrientation(walk.getOrientation());

		// This tick may have moved the citizen — including onto the tile it was
		// heading for, which is the case where it stops — so the frame pass has to
		// take at least one look before it is allowed to skip it again.
		positionSettled = false;
	}

	/**
	 * One frame of visual interpolation, for a wandering citizen.
	 *
	 * <p>Client thread only. This is the part that has to happen per frame rather
	 * than per game tick: {@code RuneLiteObject} has no walk API at all, so
	 * without this a citizen jumps a whole tile every 600ms.
	 *
	 * <p>It deliberately does <b>not</b> touch the animation. The client calls
	 * {@code RuneLiteObject.tick(ticksSinceLastFrame)} once per frame for every
	 * registered object, which is what advances the {@code AnimationController};
	 * calling it here as well would run every animation at double speed.
	 *
	 * @param fraction how far through the current game tick this frame is, 0..1
	 */
	void advanceFrame(WorldView worldView, float fraction)
	{
		if (walk == null || broken || !isActive())
		{
			return;
		}

		if (positionSettled)
		{
			// Standing still and already placed. Most citizens, most frames.
			return;
		}

		LocalPoint location = walk.localPoint(worldView, fraction);
		if (location == null)
		{
			// Walked to a tile the client has not loaded. Leaving the object where
			// it is beats moving it somewhere that does not mean anything.
			return;
		}

		object.setLocation(location, definition.getPlane());
		positionSettled = !walk.isMoving();
	}

	/**
	 * @return the animation currently driving the model, or {@code null} for a
	 * static one. Only meaningful once the model has been built.
	 */
	@Nullable
	AnimationController getInstalledController()
	{
		return installed;
	}

	/**
	 * Hands the object a controller, but only when it is not the one it already
	 * has. The identity compare is the whole point — see
	 * {@link #idleController}.
	 */
	private void select(@Nullable AnimationController controller)
	{
		if (controller == installed)
		{
			return;
		}

		installed = controller;
		object.setAnimationController(controller);
	}

	@Nullable
	private AnimationController idleControllerOrNull()
	{
		LivelyAnimation animation = definition.getIdleAnimation();
		if (animation == null)
		{
			// A static model is a legitimate outcome — most scenery has no
			// animation at all, and an unknown animation name degrades to this.
			return null;
		}

		if (idleController == null)
		{
			idleController = looping(animation);
		}
		return idleController;
	}

	@Nullable
	private AnimationController moveControllerOrNull()
	{
		LivelyAnimation animation = definition.getMoveAnimation();
		if (animation == null)
		{
			// Every shipped wanderer has one, but a citizen with an unknown
			// moveAnimation name should keep whatever it idles as while walking
			// rather than freeze into a static model mid-step.
			return idleControllerOrNull();
		}

		if (moveController == null)
		{
			moveController = looping(animation);
		}
		return moveController;
	}

	/**
	 * Builds a looping controller, or returns {@code null} without caching
	 * anything if the client would not give us the animation.
	 *
	 * <p>Not {@code setAnimation(..)}: that is sugar for exactly this, and the
	 * looping half of the old API ({@code setShouldLoop}) is deprecated. An
	 * {@code AnimationController} defaults to {@code AnimationController::loop},
	 * but say so explicitly so a future default change cannot silently make every
	 * citizen freeze on their last frame.
	 *
	 * <p><b>The null check is the whole point.</b> The constructor swallows a
	 * failed load — it calls {@code client.loadAnimation(id)} and hands the result,
	 * null or not, straight to {@code setAnimation} — so the only way to tell is to
	 * ask the controller what animation it ended up with. See
	 * {@link #MAX_ANIMATION_ATTEMPTS} for why that is treated as transient and
	 * bounded rather than as a permanent verdict on the id.
	 */
	@Nullable
	private AnimationController looping(LivelyAnimation animation)
	{
		if (animationMisses >= MAX_ANIMATION_ATTEMPTS)
		{
			return null;
		}

		if (animationMisses > 0 && ++passesSinceAnimationMiss < RETRY_BACKOFF_PASSES)
		{
			return null;
		}

		AnimationController controller = new AnimationController(client, animation.getId());
		if (controller.getAnimation() == null)
		{
			animationMisses++;
			passesSinceAnimationMiss = 0;

			if (animationMisses == 1)
			{
				log.warn("{}: animation {} (id {}) did not load — spawning it static for now; "
						+ "a cold animation cache is the usual cause, so it will be retried up to "
						+ "{} time(s) per scene load. An id with no frames at all never resolves.",
					definition.label(), animation, animation.getId(), MAX_ANIMATION_ATTEMPTS);
			}
			else
			{
				log.debug("{}: animation {} (id {}) still not loading (attempt {} of {})",
					definition.label(), animation, animation.getId(),
					animationMisses, MAX_ANIMATION_ATTEMPTS);
			}

			return null;
		}

		controller.setOnFinished(AnimationController::loop);
		return controller;
	}

	/**
	 * Asks again for an animation that did not load, for an entity that is already
	 * on screen.
	 *
	 * <p>Called once per visibility pass by {@link EntityScene}. A wandering
	 * citizen would get its retry from {@link #advanceTick()} anyway, which
	 * re-selects every game tick; a {@code StationaryCitizen} would not, and there
	 * are plenty of those with idle animations. Without this, "it loaded the model
	 * but not the animation" would be permanent for everything that stands still —
	 * which is the same bug the {@code null} check in {@link #looping} fixes, one
	 * level up.
	 *
	 * <p>Costs a field compare and returns once the animation is installed, which
	 * is the case for every entity that is working. Client thread only.
	 */
	void retryMissingAnimation()
	{
		if (broken || model == null || installed != null || !isActive())
		{
			return;
		}

		if (definition.getIdleAnimation() == null && definition.getMoveAnimation() == null)
		{
			// Static by design: every Scenery record, and any citizen whose
			// animation name this build does not know. Not a cache miss, so not
			// something to retry.
			return;
		}

		select(walk != null && walk.isMoving() ? moveControllerOrNull() : idleControllerOrNull());
	}

	/**
	 * Loads every model this entity is made of.
	 *
	 * @return all of the requested parts, or {@code null} if even one did not
	 * load — never a partial list. Costs at most one of
	 * {@link #MAX_MODEL_ATTEMPTS} and returns {@code null} without touching the
	 * client while a retry is still backing off, or once the budget is gone.
	 */
	@Nullable
	private List<ModelData> loadParts()
	{
		int[] modelIds = definition.getModelIds();
		List<EntityDefinition.MergedObject> merged = definition.getMergedObjects();
		int requested = modelIds.length + merged.size();

		if (modelAttempts >= MAX_MODEL_ATTEMPTS)
		{
			return null;
		}

		if (modelAttempts > 0 && ++passesSinceAttempt < RETRY_BACKOFF_PASSES)
		{
			return null;
		}

		passesSinceAttempt = 0;
		modelAttempts++;

		List<ModelData> parts = new ArrayList<>(requested);
		StringBuilder missing = new StringBuilder();

		for (int modelId : modelIds)
		{
			ModelData part = client.loadModelData(modelId);
			if (part == null)
			{
				note(missing, modelId);
				continue;
			}
			parts.add(part);
		}

		for (EntityDefinition.MergedObject entry : merged)
		{
			ModelData part = client.loadModelData(entry.getObjectId());
			if (part == null)
			{
				note(missing, entry.getObjectId());
				continue;
			}

			for (int i = 0; i < entry.getRotations(); i++)
			{
				part.cloneVertices();
				part.rotateY90Ccw();
			}
			parts.add(part);
		}

		if (parts.size() == requested)
		{
			return parts;
		}

		// One line for the entity, not one per id: a cold cache misses whole
		// handfuls at once, and the per-id version was the noise this class
		// exists to avoid.
		if (modelAttempts == 1)
		{
			log.warn("{}: only {} of {} model part(s) loaded, missing id(s) {} — not spawning; "
					+ "a cold model cache is the usual cause, so it will be retried up to {} time(s) per scene load",
				definition.label(), parts.size(), requested, missing, MAX_MODEL_ATTEMPTS);
		}
		else
		{
			log.debug("{}: still only {} of {} model part(s), missing id(s) {} (attempt {} of {})",
				definition.label(), parts.size(), requested, missing, modelAttempts, MAX_MODEL_ATTEMPTS);
		}

		return null;
	}

	/**
	 * Merges, recolours, transforms and lights a complete set of parts.
	 *
	 * @return the lit model, or {@code null} for a structural failure — the
	 * caller latches those.
	 */
	@Nullable
	private Model assemble(List<ModelData> parts)
	{
		// Always merge, even for a single part: mergeModels returns a fresh
		// ModelData, and recolour/scale below mutate in place. Recolouring a
		// bare loadModelData result would corrupt the client's shared cache
		// entry for every other user of that model.
		ModelData combined = client.mergeModels(parts.toArray(new ModelData[0]), parts.size());
		if (combined == null)
		{
			log.warn("{}: mergeModels returned null for {} part(s), cannot spawn",
				definition.label(), parts.size());
			return null;
		}

		short[] find = definition.getRecolorFind();
		short[] replace = definition.getRecolorReplace();
		if (find.length > 0)
		{
			// ModelData.recolor's own javadoc says to call cloneColors() first.
			// "mergeModels hands back a fresh instance" is an observation about
			// an obfuscated constructor, not a contract: 21 shipped entities are
			// single-part models carrying recolours, so a single-part fast path
			// that shared faceColors would repaint every instance of that model
			// in the world, through the client's own cache.
			combined.cloneColors();
			for (int i = 0; i < find.length; i++)
			{
				combined.recolor(find[i], replace[i]);
			}
		}

		// Sign convention comes from the dataset's authoring tool: the stored
		// values are negated on the way into model space (model Y is up-negative).
		float[] scale = definition.getScale();
		if (scale != null)
		{
			combined.cloneVertices();
			combined.scale(
				-Math.round(scale[0] * UNITS_PER_TILE),
				-Math.round(scale[1] * UNITS_PER_TILE),
				-Math.round(scale[2] * UNITS_PER_TILE));
		}

		float[] translate = definition.getTranslate();
		if (translate != null)
		{
			combined.cloneVertices();
			combined.translate(
				-Math.round(translate[0] * UNITS_PER_TILE),
				-Math.round(translate[1] * UNITS_PER_TILE),
				-Math.round(translate[2] * UNITS_PER_TILE));
		}

		Model lit = combined.light(LIGHT_AMBIENT, LIGHT_CONTRAST, LIGHT_X, LIGHT_Y, LIGHT_Z);
		if (lit == null)
		{
			log.warn("{}: lighting produced no model, cannot spawn", definition.label());
			return null;
		}

		return lit;
	}

	private static void note(StringBuilder missing, int modelId)
	{
		if (missing.length() > 0)
		{
			missing.append(", ");
		}
		missing.append(modelId);
	}
}
