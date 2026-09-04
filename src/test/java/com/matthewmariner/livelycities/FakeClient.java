package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 *
 * <p>{@link #getNpcDefinition(int)} is modelled on the real one's <i>failure</i>
 * shape rather than on convenience — an unknown id throws. See that method.
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

	/**
	 * NPC compositions this client knows about, keyed by NPC id.
	 *
	 * <p>Empty by default, and an id that is <b>not</b> in here <i>throws</i> rather
	 * than returning null — see {@link #getNpcDefinition(int)}. That is the real
	 * 1.12.36 behaviour and it is the whole reason this is a map with a default
	 * rather than a "return null unless told otherwise" field: a permissive fake
	 * would make {@code NpcAppearance}'s try/catch dead code that no test could tell
	 * from an empty method body.
	 */
	private final Map<Integer, net.runelite.api.NPCComposition> npcCompositions = new HashMap<>();

	/** NPC ids the composition lookup returns null for, rather than throwing. */
	private final Set<Integer> nullNpcCompositions = new HashSet<>();

	/** Every NPC id the composition lookup was asked about, in order. */
	private final List<Integer> npcDefinitionsRequested = new ArrayList<>();

	/** True while the model cache is pretending to be cold: everything misses. */
	private boolean cacheCold;

	/** Accepts the register call and does nothing with it. */
	private boolean refuseRegistration;

	/** Throws out of {@code removeRuneLiteObject}, leaving the object registered. */
	private boolean throwFromRemoval;

	/** Throws out of {@code isRuneLiteObjectRegistered}. */
	private boolean throwFromRegistrationCheck;

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
		// Counted before the throw: the client really was asked, and the whole point of
		// this failure mode is that asking is not the same as it having worked.
		removeCalls++;

		if (throwFromRemoval)
		{
			throw new IllegalStateException("the client will not let go of this object");
		}

		registered.remove(controller);
	}

	@Override
	public boolean isRuneLiteObjectRegistered(RuneLiteObjectController controller)
	{
		if (throwFromRegistrationCheck)
		{
			throw new IllegalStateException("the client will not say whether it has this object");
		}

		return registered.contains(controller);
	}

	/**
	 * {@code setActive(false)} blows up and the object stays registered — the state in
	 * which forgetting a wrapper leaks the object it was holding.
	 *
	 * <p>No realistic in-client trigger for this is known; it is here because the teardown
	 * promise is unconditional, and a promise nothing can falsify is not one.
	 */
	FakeClient refusingDeactivation()
	{
		throwFromRemoval = true;
		return this;
	}

	/**
	 * The client comes to its senses and starts letting go of objects again — so a
	 * wrapper kept back by an earlier teardown can be asked a second time, which is the
	 * only reason keeping it is worth anything.
	 */
	FakeClient acceptingDeactivation()
	{
		throwFromRemoval = false;
		return this;
	}

	/** The client will not even say whether it still has an object. */
	FakeClient withThrowingRegistrationChecks()
	{
		throwFromRegistrationCheck = true;
		return this;
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

	/**
	 * {@code Client.getNpcDefinition(int)} as 1.12.36 actually behaves.
	 *
	 * <p>Three outcomes, and each is one the plugin has to cope with:
	 * <ul>
	 *   <li><b>Known id</b> — the composition that was registered.</li>
	 *   <li><b>Explicitly null</b> — see {@link #setNullNpcComposition}. The real
	 *       accessor is a cache read, so a null is possible even though it is not the
	 *       usual failure.</li>
	 *   <li><b>Anything else, including while the cache is cold</b> — throws. That is
	 *       the real path for an id whose archive entry is absent:
	 *       {@code client.kz(id)} → {@code oh.ae(id, ..)} loads the bytes with
	 *       {@code va.bb(9, id, ..)}, which returns null, and the {@code pl}
	 *       constructor then blows up inside {@code oh.ae}'s own
	 *       {@code catch (RuntimeException)} and is rethrown wrapped.</li>
	 * </ul>
	 */
	@Override
	public net.runelite.api.NPCComposition getNpcDefinition(int id)
	{
		npcDefinitionsRequested.add(id);

		if (nullNpcCompositions.contains(id))
		{
			return null;
		}

		net.runelite.api.NPCComposition composition = cacheCold ? null : npcCompositions.get(id);
		if (composition == null)
		{
			throw new IllegalStateException("no NPC composition for id " + id);
		}

		return composition;
	}

	FakeClient withNpc(int id, net.runelite.api.NPCComposition composition)
	{
		npcCompositions.put(id, composition);
		return this;
	}

	/**
	 * Registers a working composition for every {@code npcAppearanceId} the shipped
	 * dataset references — a warm NPC archive.
	 *
	 * <p>Needed by the tests that spawn the <b>real</b> {@code RegionData/*.json}
	 * rather than a {@link FakeRegions} fixture. {@link #getNpcDefinition(int)} throws
	 * for an id nobody registered, which is the real 1.12.36 behaviour, so without
	 * this those tests model a client whose NPC archive is missing every id the
	 * dataset names: "Rufus" in Varrock would silently fail to spawn and a crowd count
	 * would come out one short for a reason that has nothing to do with what was being
	 * measured. It cost an afternoon once; hence this method rather than a per-test
	 * {@code withNpc} call.
	 *
	 * <p>Reads the ids straight out of the dataset, so an eighth one added to the JSON
	 * is covered without touching this.
	 */
	FakeClient withShippedNpcAppearances()
	{
		for (int id : ShippedModelIds.distinctNpcAppearanceIds())
		{
			withNpc(id, FakeNpcComposition.of("shipped npc " + id, 900_000 + id));
		}
		return this;
	}

	/** The id resolves to a null composition instead of throwing. */
	FakeClient setNullNpcComposition(int id)
	{
		nullNpcCompositions.add(id);
		return this;
	}

	/** Every NPC id the composition lookup was asked about, in order. */
	List<Integer> npcDefinitionsRequested()
	{
		return npcDefinitionsRequested;
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

	// --- The interaction surface. --------------------------------------------
	//
	// Four methods, and the list is deliberately this short: every other way of
	// reaching the game — menuAction(..) above all — stays on StubClient and throws.
	// That is how CitizenMenuTest can assert "the click handler sends nothing to the
	// server" as a fact about the code rather than as an intention. A permissive
	// client would have made the claim unprovable.

	private final FakeMenu menu = new FakeMenu();

	private final List<String> chatMessages = new ArrayList<>();

	private boolean widgetSelected;

	@Nullable
	private net.runelite.api.Point mouseCanvasPosition;

	@Override
	public net.runelite.api.Menu getMenu()
	{
		return menu;
	}

	FakeMenu menu()
	{
		return menu;
	}

	/**
	 * The {@code MenuOpened} the client would post for the menu as it stands now.
	 *
	 * <p>A snapshot, exactly like the real event: {@code Menu.createMenuEntry}
	 * mutates the live menu, so entries added by a handler do not appear in the array
	 * the handler was given.
	 */
	net.runelite.api.events.MenuOpened menuOpened()
	{
		net.runelite.api.events.MenuOpened event = new net.runelite.api.events.MenuOpened();
		event.setMenuEntries(menu.getMenuEntries());
		return event;
	}

	/**
	 * "Is a widget in target mode?" — the client's own javadoc. True while an item
	 * or a spell is on the cursor, which is the state
	 * {@link CitizenMenu#onMenuOpened} must produce no clickbox in.
	 */
	@Override
	public boolean isWidgetSelected()
	{
		return widgetSelected;
	}

	/**
	 * {@code Client.setWidgetSelected} is a real API method, so this implements it
	 * rather than shadowing it under another name — the same treatment as
	 * {@code setGameState} above. This plugin never calls it; the tests do, to put
	 * an item on the cursor.
	 */
	@Override
	public void setWidgetSelected(boolean widgetSelected)
	{
		this.widgetSelected = widgetSelected;
	}

	@Override
	@Nullable
	public net.runelite.api.Point getMouseCanvasPosition()
	{
		return mouseCanvasPosition;
	}

	void setMouseCanvasPosition(@Nullable net.runelite.api.Point position)
	{
		this.mouseCanvasPosition = position;
	}

	// --- The interface layout, for the minimap guard. -------------------------
	//
	// Three methods, and they are the three RuneLite's own OverlayOrigin.MINIMAP
	// asks (verified in client-1.12.36's bytecode): is the client resized, which
	// toplevel interface is loaded, and what is at this component id.
	//
	// The widget map starts EMPTY and getWidget returns null for anything not in
	// it, which is the real behaviour for a component in an interface that is not
	// loaded — getWidget(group, child) returns null once either array index is out
	// of range. That default is what lets a test say "the layout the client is in
	// is not the one this id belongs to" by simply not registering it.

	private final Map<Integer, net.runelite.api.widgets.Widget> widgets = new HashMap<>();

	private boolean resized;

	private int topLevelInterfaceId = net.runelite.api.gameval.InterfaceID.TOPLEVEL;

	@Override
	public boolean isResized()
	{
		return resized;
	}

	@Override
	public int getTopLevelInterfaceId()
	{
		return topLevelInterfaceId;
	}

	@Override
	@Nullable
	public net.runelite.api.widgets.Widget getWidget(int componentId)
	{
		return widgets.get(componentId);
	}

	/** Puts one widget at one packed {@code gameval.InterfaceID} component id. */
	FakeClient withWidget(int componentId, net.runelite.api.widgets.Widget widget)
	{
		widgets.put(componentId, widget);
		return this;
	}

	/**
	 * Switches the client into one of the resizable layouts.
	 *
	 * @param topLevelInterfaceId {@code InterfaceID.TOPLEVEL_OSRS_STRETCH} or
	 *                            {@code InterfaceID.TOPLEVEL_PRE_EOC} — the two the
	 *                            resizable branch has to tell apart
	 */
	FakeClient resizedWith(int topLevelInterfaceId)
	{
		this.resized = true;
		this.topLevelInterfaceId = topLevelInterfaceId;
		return this;
	}

	/**
	 * The local chat buffer. Returns null rather than a {@code MessageNode} because
	 * nothing in this plugin reads the result — it prints a line and forgets it.
	 */
	@Override
	@Nullable
	public net.runelite.api.MessageNode addChatMessage(
		net.runelite.api.ChatMessageType type, String name, String message, String sender)
	{
		chatMessages.add(message);
		return null;
	}

	List<String> chatMessages()
	{
		return chatMessages;
	}
}
