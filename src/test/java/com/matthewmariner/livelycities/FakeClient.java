package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Animation;
import net.runelite.api.GameState;
import net.runelite.api.ModelData;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;

/**
 * A client that keeps the registered-object list and the model cache in fields.
 *
 * <p>This is what makes the lifecycle testable without a game running. In
 * 1.12.36 (verified by disassembling the API jar) {@code RuneLiteObject} is not
 * a black box: {@code isActive()} is exactly
 * {@code client.isRuneLiteObjectRegistered(this)} and {@code setActive(b)} is
 * exactly {@code client.registerRuneLiteObject(this)} /
 * {@code removeRuneLiteObject(this)}. So a {@link Set} standing in for the
 * client's list turns "did teardown actually deactivate anything?" into an
 * assertion, and the objects under test are real {@code RuneLiteObject}s
 * running their real code.
 *
 * <p>{@link #peakRegistered()} is a high-water mark rather than a final count,
 * because "the cap is never exceeded" is a claim about the middle of a pass, not
 * about its end.
 *
 * <p>Everything the render core does not call is inherited from
 * {@link StubClient} and throws.
 */
final class FakeClient extends StubClient
{
	/** Stands in for the client's registered-object list. */
	private final Set<RuneLiteObjectController> registered = new LinkedHashSet<>();

	/** Model ids that resolve to nothing, whatever the cache is doing. */
	private final Set<Integer> unloadable = new HashSet<>();

	/** Model ids whose load blows up rather than returning null. */
	private final Set<Integer> throwing = new HashSet<>();

	/** Animation ids the entities asked for, in order. */
	private final List<Integer> animationsLoaded = new ArrayList<>();

	/**
	 * Animation ids that resolve to nothing.
	 *
	 * <p>The real {@code loadAnimation} returns null when the sequence it finds
	 * has no frame lengths and is not a Maya animation — a cold cache, or an id
	 * with no frames at all. Both look the same from here, which is the point:
	 * the plugin cannot tell them apart either, and that is why it retries.
	 */
	private final Set<Integer> unloadableAnimations = new HashSet<>();

	/** True while the model cache is pretending to be cold: everything misses. */
	private boolean cacheCold;

	/** Accepts the register call and does nothing with it. */
	private boolean refuseRegistration;

	private FakeRuneLiteObject lastObject;
	private int peakRegistered;
	private int registerCalls;
	private int removeCalls;
	private int loadModelDataCalls;
	private int createObjectCalls;
	private int mergeCalls;
	private int lastMergePartCount;
	private FakeModelData lastMerged;

	@Override
	public RuneLiteObject createRuneLiteObject()
	{
		createObjectCalls++;
		lastObject = new FakeRuneLiteObject(this);
		return lastObject;
	}

	/**
	 * @return the most recently created object, for the tests that need to ask it
	 * what was done to it. The scene tests deal in counts and never touch this.
	 */
	FakeRuneLiteObject lastObject()
	{
		return lastObject;
	}

	@Override
	public void registerRuneLiteObject(RuneLiteObjectController controller)
	{
		registerCalls++;

		if (refuseRegistration)
		{
			return;
		}

		registered.add(controller);
		peakRegistered = Math.max(peakRegistered, registered.size());
	}

	@Override
	public void removeRuneLiteObject(RuneLiteObjectController controller)
	{
		removeCalls++;
		registered.remove(controller);
	}

	@Override
	public boolean isRuneLiteObjectRegistered(RuneLiteObjectController controller)
	{
		return registered.contains(controller);
	}

	@Override
	public ModelData loadModelData(int id)
	{
		loadModelDataCalls++;

		if (throwing.contains(id))
		{
			throw new IllegalStateException("model " + id + " blew up on the way out of the cache");
		}

		if (cacheCold || unloadable.contains(id))
		{
			return null;
		}

		return new FakeModelData();
	}

	@Override
	public ModelData mergeModels(ModelData[] parts, int count)
	{
		mergeCalls++;
		lastMergePartCount = count;
		lastMerged = new FakeModelData();
		return lastMerged;
	}

	@Override
	public Animation loadAnimation(int id)
	{
		animationsLoaded.add(id);

		if (cacheCold || unloadableAnimations.contains(id))
		{
			return null;
		}

		// A real (if short) animation, not null: AnimationController.tick returns
		// immediately when its animation is null, so a null here would make every
		// assertion about frames advancing pass for the wrong reason.
		return new FakeAnimation(id);
	}

	void setUnloadableAnimations(int... ids)
	{
		for (int id : ids)
		{
			unloadableAnimations.add(id);
		}
	}

	void clearUnloadableAnimations()
	{
		unloadableAnimations.clear();
	}

	/**
	 * {@code RuneLiteObject.setLocation} runs
	 * {@code Perspective.getTileHeight(client, ..)}, which asks the client for
	 * the world view by id and returns height 0 when there is none. Null is
	 * therefore the honest answer for a scene that only exists as numbers, and
	 * it keeps the real {@code setLocation} on its real code path.
	 */
	@Override
	public WorldView getWorldView(int id)
	{
		return null;
	}

	// --- The four the plugin's own event handlers read. -----------------------
	// The render core never touches these; LivelyCitiesPluginLifecycleTest drives
	// all four. They are fields rather than throwing stubs because every one of
	// them is a thing the plugin has to cope with changing under it: the game
	// cycle is the interpolation clock, and the other three are each null or
	// wrong at some point in a real login.

	/**
	 * The client's 20ms clock. An {@code int} on purpose — it is one in the API,
	 * and the plugin's fraction arithmetic has to survive it wrapping.
	 */
	private int gameCycle;

	private GameState gameState = GameState.LOGGED_IN;

	@Nullable
	private Player localPlayer;

	@Nullable
	private WorldView topLevelWorldView;

	@Override
	public int getGameCycle()
	{
		return gameCycle;
	}

	@Override
	public GameState getGameState()
	{
		return gameState;
	}

	@Override
	@Nullable
	public Player getLocalPlayer()
	{
		return localPlayer;
	}

	@Override
	@Nullable
	public WorldView getTopLevelWorldView()
	{
		return topLevelWorldView;
	}

	void setGameCycle(int gameCycle)
	{
		this.gameCycle = gameCycle;
	}

	/** @return the cycle after advancing it, so a test can read the clock it set */
	int advanceGameCycle(int clientTicks)
	{
		gameCycle += clientTicks;
		return gameCycle;
	}

	/**
	 * {@code Client.setGameState} is a real API method, so this implements it
	 * rather than shadowing it under another name. The plugin never calls it; the
	 * tests do, to move the client between states.
	 */
	@Override
	public void setGameState(GameState gameState)
	{
		this.gameState = gameState;
	}

	void setLocalPlayer(@Nullable Player localPlayer)
	{
		this.localPlayer = localPlayer;
	}

	void setTopLevelWorldView(@Nullable WorldView topLevelWorldView)
	{
		this.topLevelWorldView = topLevelWorldView;
	}

	int registeredCount()
	{
		return registered.size();
	}

	/**
	 * How many times the client was asked to register / unregister an object,
	 * rather than how many are registered now.
	 *
	 * <p>The difference matters for the per-frame path.
	 * {@code RuneLiteObject.setLocation} silently deactivates and reactivates the
	 * object whenever the point's world view differs from the object's, so a
	 * mismatched world view would churn the registered-object list once per frame
	 * per citizen while leaving the <i>count</i> looking perfectly stable.
	 */
	int registerCalls()
	{
		return registerCalls;
	}

	int removeCalls()
	{
		return removeCalls;
	}

	int peakRegistered()
	{
		return peakRegistered;
	}

	int loadModelDataCalls()
	{
		return loadModelDataCalls;
	}

	int createObjectCalls()
	{
		return createObjectCalls;
	}

	int mergeCalls()
	{
		return mergeCalls;
	}

	int lastMergePartCount()
	{
		return lastMergePartCount;
	}

	FakeModelData lastMerged()
	{
		return lastMerged;
	}

	List<Integer> animationsLoaded()
	{
		return animationsLoaded;
	}

	void setCacheCold(boolean cold)
	{
		cacheCold = cold;
	}

	void setUnloadable(int... ids)
	{
		for (int id : ids)
		{
			unloadable.add(id);
		}
	}

	void setThrowing(int... ids)
	{
		for (int id : ids)
		{
			throwing.add(id);
		}
	}

	void refuseRegistration()
	{
		refuseRegistration = true;
	}

	void resetCounters()
	{
		loadModelDataCalls = 0;
		createObjectCalls = 0;
		mergeCalls = 0;
		registerCalls = 0;
		removeCalls = 0;
	}
}
