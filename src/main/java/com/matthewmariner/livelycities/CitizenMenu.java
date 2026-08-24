package com.matthewmariner.livelycities;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * The right-click menu for a fake citizen: Examine, Hide, Mute — and nothing that
 * could ever reach the server.
 *
 * <p><b>Purely cosmetic and local: this class uses {@code RuneLiteObject}s only,
 * adds RUNELITE-type menu entries only, and sends nothing to the server.</b> That
 * is the hub's own accepted formula, and it is enforced here rather than
 * promised:
 * <ul>
 *   <li>Every entry is created with {@link MenuAction#RUNELITE} and
 *       {@code setDeprioritized(true)}. RUNELITE actions are handled inside the
 *       client's plugin layer and never packed into a packet.</li>
 *   <li>{@link #onMenuOptionClicked} checks the action type <i>and</i> the option
 *       text <i>and</i> the identifier it stamped on its own entry, then calls
 *       {@code consume()} before doing anything, so the click never reaches the
 *       client's own menu dispatch.</li>
 *   <li>The three actions do exactly three things: print a line into the local
 *       chat buffer, and write a uuid into this plugin's own config. Neither is a
 *       game action. {@code Client.menuAction(..)} — the one API on this surface
 *       that <i>does</i> perform a real action — is never called, and
 *       {@code StubClient} throws on it, so a regression that reached for it is a
 *       red test rather than a shipped packet.</li>
 * </ul>
 *
 * <p><b>Never compete with a real NPC for a click</b> — upstream issue #14, 49
 * upvotes, "can't right-click through them" and "misclicks when using an item".
 * Three things, and the first is structural:
 *
 * <ol>
 *   <li><b>The entries only exist inside an opened right-click menu.</b> They are
 *       created in {@link MenuOpened}, which fires after the client has built the
 *       menu for that click, so they are not present when the client resolves a
 *       <i>left</i> click at all. There is nothing to misclick.</li>
 *   <li><b>They go in at index 0, which is the bottom of the menu.</b>
 *       {@code MenuOpened.getFirstEntry()} returns {@code menuEntries[length - 1]}
 *       — verified in the 1.12.36 bytecode — so the array is rendered last-first
 *       and index 0 is the last row. {@code Menu.createMenuEntry(0)} inserts
 *       there; the same bytecode shows a non-negative index is used directly and a
 *       negative one is folded to {@code count + idx + 1}, which is why {@code -1}
 *       (the conventional call) would put us at the <i>top</i>. On top of that
 *       every entry is {@code setDeprioritized(true)}, whose contract is
 *       "sorted in the menu to be below the other menu entries".</li>
 *   <li><b>No clickbox is computed at all while an item or a spell is on the
 *       cursor.</b> {@code Client.isWidgetSelected()} — "is a widget in target
 *       mode?" — is checked before anything else, so mid-"use item on" there is no
 *       hit test, no entries, and nothing of ours in the menu. This is the
 *       specific reported complaint, and the guard is placed so that it also costs
 *       nothing.</li>
 *   <li><b>Nothing is added to an interface's menu either.</b> The clickbox is in
 *       canvas space and the inventory is drawn over the viewport, so a citizen
 *       standing behind it projects underneath it — see {@link #isWorldClick} and
 *       {@link #isOverMinimap}.</li>
 * </ol>
 *
 * <p><b>What {@code setDrawFrontTilesFirst} turned out to be, since the plan
 * flagged it as a possible third lever.</b> It is not one. Its own javadoc in
 * 1.12.36 says it expands the pre-drawn tile rectangle by a full tile in the
 * direction the object faces; the flag is passed to the scene's add-entity call as
 * its last argument, alongside a click tag of {@code -1L} — the client registers
 * our objects for <i>drawing</i> with no click tag at all, which is precisely why
 * this class has to compute a clickbox itself. So the flag is tile draw order and
 * cannot affect click competition. It is left at its default {@code false}: the
 * citizens are single-tile human models at the default radius of 60, which the
 * same javadoc calls the value that "works well for models the size of a single
 * tile", and a wandering citizen changes facing every step, so setting it would
 * churn the pre-drawn tile set once a tick for no visual gain.
 *
 * <p><b>The clickbox is computed on right-click, not per tick.</b> The plan named
 * the per-object per-tick convex hull as the known frame-time cost centre, and the
 * accepted three-part pattern is "clickbox → menu → click". Doing the first part
 * lazily costs nothing per frame and nothing per tick: {@link MenuOpened} fires
 * once per right-click, and at most
 * {@link #CLICKBOX_RADIUS_TILES}-worth of citizens are tested on it. A per-tick
 * clickbox would only be needed to put an entry on a <i>left</i> click, which this
 * plugin deliberately never does.
 *
 * <p><b>Client thread, and no {@code invokeLater}.</b> {@code MenuOpened} and
 * {@code MenuOptionClicked} are both posted from the client's own menu code, and
 * {@code Menu.createMenuEntry} asserts {@code isClientThread()} (also verified in
 * the bytecode). Deferring the {@code MenuOpened} handler through
 * {@link net.runelite.client.callback.ClientThread} would be actively wrong, not
 * merely unnecessary: the entries would be added after the menu had been built and
 * drawn.
 */
@Slf4j
@Singleton
class CitizenMenu
{
	/**
	 * How far from the player a citizen may be and still get a right-click menu:
	 * 15 tiles.
	 *
	 * <p>The figure the performance budget names for clickboxes. It is deliberately
	 * tighter than the default 25-tile render distance — a citizen twenty tiles away
	 * is scenery, and the whole point of the distance gate is that the hit test
	 * never walks the far half of a crowd.
	 */
	static final int CLICKBOX_RADIUS_TILES = 15;

	static final String OPTION_EXAMINE = "Examine";
	static final String OPTION_HIDE = "Hide";
	static final String OPTION_MUTE = "Mute";

	/**
	 * The minimap panel, in each of the three toplevel layouts a desktop client can
	 * be in: fixed (interface 548), resizable-classic (161) and resizable-modern
	 * (164).
	 *
	 * <p><b>Not invented — this is RuneLite's own answer to "where is the minimap".</b>
	 * {@code net.runelite.client.ui.overlay.OverlayOrigin.MINIMAP} in client-1.12.36
	 * resolves exactly these three component ids, in exactly the order
	 * {@link #minimapPanel()} does (verified by disassembling
	 * {@code OverlayOrigin$3.getWidget} and {@code OverlayOrigin.getComponent}). They
	 * are the containers RuneLite positions its own {@code CANVAS_TOP_RIGHT} overlays
	 * against, so they are laid out and drawn whenever the minimap is on screen —
	 * which is what makes {@code getBounds()} on them mean something.
	 *
	 * <p>The container rather than the {@code MINIMAP} child inside it, deliberately:
	 * it covers the compass and the orb column as well as the map itself, and a
	 * citizen projecting under any of those is equally unreachable. A wider rectangle
	 * costs nothing here and is one fewer guess about which child owns the "Walk here"
	 * option.
	 */
	private static final int FIXED_MINIMAP_PANEL = InterfaceID.Toplevel.MAPCONTAINER;
	private static final int RESIZABLE_MINIMAP_PANEL = InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER;
	private static final int RESIZABLE_MODERN_MINIMAP_PANEL = InterfaceID.ToplevelPreEoc.MAP_CONTAINER;

	private final Client client;
	private final EntityScene scene;
	private final CitizenOverrides overrides;

	/**
	 * The citizen the last opened menu was built for.
	 *
	 * <p>A uuid does not fit in {@code MenuEntry.setIdentifier(int)}, and there is
	 * exactly one open menu at a time, so the entity is remembered rather than
	 * encoded. {@link #token} is what makes that safe: it is stamped on the entries
	 * as their identifier and checked on the click, so a stale field can never be
	 * acted on by somebody else's RUNELITE entry.
	 */
	@Nullable
	private LivelyEntity target;

	/**
	 * Stamped on this menu's entries as their identifier, and bumped for every menu.
	 * Starts at 1 so a click carrying the default identifier of 0 can never match.
	 */
	private int token;

	@Inject
	CitizenMenu(Client client, EntityScene scene, CitizenOverrides overrides)
	{
		this.client = client;
		this.scene = scene;
		this.overrides = overrides;
	}

	/**
	 * Adds this plugin's entries, if the right-click landed on one of our citizens.
	 *
	 * <p>Client thread only, and synchronously — see the class javadoc.
	 */
	void onMenuOpened(MenuOpened event)
	{
		// Whatever happens, the previous menu's target is stale from here on.
		target = null;

		if (client.isWidgetSelected())
		{
			// An item or a spell is on the cursor. No hit test, no clickbox, no
			// entries — the misclick complaint from issue #14, and the reason this
			// is the first line rather than a filter further down.
			return;
		}

		if (!isWorldClick(event))
		{
			// The right-click was on an interface, not on the world. Our clickbox is
			// in canvas space and the inventory is drawn on top of the viewport, so a
			// citizen standing behind the inventory really does project underneath
			// it — and without this, right-clicking a rune would offer to examine
			// him. Nothing is computed and nothing is added.
			return;
		}

		final Player local = client.getLocalPlayer();
		final WorldPoint playerLocation = local == null ? null : local.getWorldLocation();
		final WorldView worldView = client.getTopLevelWorldView();
		final Point mouse = client.getMouseCanvasPosition();
		if (playerLocation == null || worldView == null || mouse == null)
		{
			return;
		}

		if (isOverMinimap(mouse))
		{
			// The one interface that also carries a "Walk here", so the check above
			// waved it through — GitHub issue #2. Same rule, answered geometrically
			// because the menu itself cannot answer it. Still before any clickbox.
			return;
		}

		LivelyEntity hit = findUnderMouse(playerLocation, worldView, mouse);
		if (hit == null)
		{
			return;
		}

		target = hit;
		token++;
		addEntries(hit);
	}

	/**
	 * Handles a click on one of our entries, and consumes it.
	 *
	 * <p>Anything that is not ours falls through untouched — in particular a
	 * RUNELITE entry with the same option text belonging to another plugin, which is
	 * what the identifier and target checks are for.
	 */
	void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.RUNELITE)
		{
			return;
		}

		final String option = event.getMenuOption();
		if (!OPTION_EXAMINE.equals(option) && !OPTION_HIDE.equals(option) && !OPTION_MUTE.equals(option))
		{
			return;
		}

		final LivelyEntity entity = target;
		if (entity == null || event.getId() != token)
		{
			return;
		}

		final EntityDefinition definition = entity.getDefinition();
		if (!CitizenLabel.menuTarget(definition).equals(event.getMenuTarget()))
		{
			// Same type, same option text, same identifier, different target: not
			// ours, and acting on it would be acting on somebody else's entry.
			return;
		}

		// Consumed before anything else happens, so there is no path on which the
		// client's own menu dispatch sees this click.
		event.consume();

		switch (option)
		{
			case OPTION_EXAMINE:
				message(CitizenLabel.examineMessage(definition));
				break;

			case OPTION_HIDE:
				if (overrides.hide(definition))
				{
					message(CitizenLabel.hiddenMessage(definition, overrides.hiddenUuids().size()));
				}
				// The citizen is not despawned here. Writing the setting posts a
				// ConfigChanged, the plugin answers that with a visibility pass, and
				// the pass despawns whatever is no longer wanted — the same path a
				// city checkbox takes. One mechanism, already tested.
				target = null;
				break;

			case OPTION_MUTE:
				if (overrides.mute(definition))
				{
					message(CitizenLabel.mutedMessage(definition, overrides.mutedUuids().size()));
				}
				// Unlike Hide, this one does need a nudge: muting does not change
				// whether the citizen is visible, so the visibility pass has nothing
				// to do, and a remark already on screen would otherwise sit there for
				// the rest of its dwell.
				CitizenRemarks remarks = entity.getRemarks();
				if (remarks != null)
				{
					remarks.clear();
				}
				break;

			default:
				// Unreachable: the option was matched above.
				break;
		}
	}

	/**
	 * Drops the remembered target.
	 *
	 * <p>Called on every scene invalidation and on shutdown. A {@link LivelyEntity}
	 * held here after its region has been evicted would be a wrapper the scene has
	 * forgotten, kept alive — with its lit model — by this one field.
	 */
	void forget()
	{
		target = null;
	}

	/** @return the citizen the currently open menu belongs to, for tests */
	@Nullable
	LivelyEntity getTarget()
	{
		return target;
	}

	/**
	 * Whether this menu belongs to a click on the game world rather than on an
	 * interface.
	 *
	 * <p>Decided by looking for a {@link MenuAction#WALK} entry: the client adds
	 * "Walk here" to every viewport right-click — on the ground, on an NPC, on a
	 * scene object alike — and to no interface right-click, where the menu is
	 * "Cancel" plus the widget's own operations. So it is the client's own answer to
	 * "was this a click on the world", read off the menu it built, rather than a
	 * geometric guess about where the inventory happens to be.
	 *
	 * <p><b>It has exactly one exception, and it is handled next door.</b> A minimap
	 * right-click carries a "Walk here" too, so this returns true for one interface —
	 * see {@link #isOverMinimap}, and GitHub issue #2.
	 */
	private static boolean isWorldClick(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();
		if (entries == null)
		{
			return false;
		}

		for (MenuEntry entry : entries)
		{
			if (entry != null && entry.getType() == MenuAction.WALK)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Whether the right-click landed on the minimap panel — the one interface
	 * {@link #isWorldClick} cannot see. GitHub issue #2.
	 *
	 * <p><b>Why this has to be geometry.</b> The obvious fix would be to read it off
	 * the menu, and the menu does not carry it. Disassembling the injected 1.12.36
	 * client, there is exactly one producer of a "Walk here" option — the string lives
	 * in {@code kk.hm} and the only code that reads it builds the entry as
	 * {@code rp.fb("Walk here", "", 23, 0, mouseX - originX, mouseY - originY, 0,
	 * false, worldViewId, ..)}. Opcode 23 is {@link MenuAction#WALK}, the identifier is
	 * a literal {@code 0}, the target is empty, and {@code param0}/{@code param1} are a
	 * mouse position relative to whatever the caller's origin was. Every field is the
	 * same whichever of the three call sites built it, so there is nothing on the entry
	 * that says "this one came from the map". {@code MenuEntry.getWidget()} is not an
	 * answer either: the entry is built by that call, which takes no widget.
	 *
	 * <p><b>Why these ids and this order.</b> Straight out of
	 * {@code net.runelite.client.ui.overlay.OverlayOrigin.MINIMAP} in client-1.12.36 —
	 * RuneLite's own "where is the minimap" — rather than a rule invented here:
	 * {@code isResized()} picks fixed versus resizable, and inside resizable
	 * {@code getTopLevelInterfaceId() == 164} picks the modern layout from the classic
	 * one. {@code Client.getWidget(int)} decodes the packed component id through
	 * {@code WidgetUtil.componentToInterface}/{@code componentToId} (also verified in
	 * the bytecode) and returns {@code null} when that interface is not loaded, so
	 * asking about the wrong layout is a null rather than a wrong rectangle.
	 *
	 * <p><b>It fails open, on purpose.</b> No widget and a hidden widget both mean
	 * "cannot tell", and the answer to "cannot tell" is the previous behaviour — a
	 * spurious Examine option — rather than silently withholding the plugin's own menu
	 * from a chunk of the screen. The entries here are inert and local, so the failure
	 * this trades against is the milder one.
	 *
	 * <p><b>No null or emptiness check on the rectangle</b>, and that is deliberate
	 * rather than an omission. {@code lw.getBounds()} in 1.12.36 is literally
	 * {@code new Rectangle(cz, ca, getWidth(), getHeight())} — a fresh instance, never
	 * null — and {@link Rectangle#contains(int, int)} already answers "no" for any box
	 * with a zero or negative dimension. Either guard would be a branch no test could
	 * tell from an empty statement, which is the kind of code this project treats as
	 * worse than absent.
	 *
	 * <p><b>The case those guards would nominally cover is a panel the client has
	 * loaded and never laid out, and the geometry already answers it.</b> Two earlier
	 * revisions of this paragraph claimed a "phantom rectangle at canvas (0, 0)"
	 * instead; both were wrong, so the bytecode is written out. {@code lw}'s no-arg
	 * constructor initialises the two canvas coordinates {@code cz} and {@code ca} to
	 * <b>{@code -1}</b> ({@code iconst_m1}) and the raw width and height fields
	 * {@code cv} and {@code do} to {@code 0} ({@code iconst_0}). {@code getWidth()}
	 * reads {@code cv}, which is <i>not</i> the interface definition's width — that is a
	 * separate field {@code dd}, exposed separately as {@code getOriginalWidth()}, and
	 * the buffer decoders that fill {@code dd} in never touch {@code cv}. So an
	 * unpositioned panel reports {@code Rectangle(-1, -1, 0, 0)}: empty, and
	 * {@code contains} returns false for every point on the canvas, {@code (0, 0)}
	 * included. There is no window in which this guard suppresses a citizen it should
	 * have offered. The original reasoning — {@code getBounds()} is never null and
	 * {@code contains()} already says no to a degenerate box — was right all along.
	 *
	 * <p><b>What {@code isHidden()} is for, then.</b> Not that case, and it is not a
	 * layout signal at all: disassembling 1.12.36 it is self-hidden (the raw boolean
	 * field {@code cq}, which is the whole of {@code isSelfHidden()}) OR, with no
	 * parent, the widget's own interface not being the current toplevel, OR the parent
	 * being hidden. All three are visibility flags. What it covers is the case that
	 * does happen: a panel the client <i>has</i> laid out, so it owns a real rectangle
	 * over the minimap, and has since marked hidden — the whole toplevel swapped out
	 * from under it, or the panel switched off. Geometry alone would go on suppressing
	 * citizens under a minimap that is not on screen, so the flag earns its place; it
	 * just earns it for a positioned panel rather than an unpositioned one.
	 *
	 * <p><b>What is still not covered:</b> {@code InterfaceID.TOPLEVEL_OSM} (601), the
	 * mobile-style layout, whose minimap sits at a fourth component id.
	 * {@code OverlayOrigin.MINIMAP} does not handle it either, so a plugin's overlays
	 * do not snap to the minimap there and this guard does not fire there. Adding a
	 * fourth branch would be a check nothing could verify — see the class javadoc's
	 * rule about the clickbox seam.
	 */
	private boolean isOverMinimap(Point mouse)
	{
		final Widget panel = minimapPanel();
		if (panel == null || panel.isHidden())
		{
			return false;
		}

		final Rectangle bounds = panel.getBounds();
		return bounds.contains(mouse.getX(), mouse.getY());
	}

	/**
	 * The minimap panel widget for whichever toplevel layout is loaded, or
	 * {@code null} if none of the three resolves.
	 *
	 * <p>Private, unlike {@link #clickbox}, and that is the difference worth noting:
	 * the clickbox needs a test seam because it projects through the live camera, while
	 * this is three ordinary {@code Client} calls a fake can answer. The tests drive it
	 * through {@code FakeClient}'s widget map rather than by overriding it, so what they
	 * exercise is this method and not a stand-in for it.
	 */
	@Nullable
	private Widget minimapPanel()
	{
		if (!client.isResized())
		{
			return client.getWidget(FIXED_MINIMAP_PANEL);
		}

		return client.getTopLevelInterfaceId() == InterfaceID.TOPLEVEL_PRE_EOC
			? client.getWidget(RESIZABLE_MODERN_MINIMAP_PANEL)
			: client.getWidget(RESIZABLE_MINIMAP_PANEL);
	}

	/**
	 * The nearest active citizen whose clickbox contains the mouse.
	 *
	 * <p>Nearest rather than first: citizens overlap on screen in a crowd, and the
	 * one in front is the one the player is pointing at.
	 */
	@Nullable
	private LivelyEntity findUnderMouse(WorldPoint playerLocation, WorldView worldView, Point mouse)
	{
		final List<LivelyEntity> entities = scene.inScopeEntities();

		LivelyEntity best = null;
		int bestDistance = Integer.MAX_VALUE;

		for (int i = 0; i < entities.size(); i++)
		{
			LivelyEntity entity = entities.get(i);
			if (!entity.isActive())
			{
				continue;
			}

			int distance = RenderPolicy.tileDistance(
				playerLocation, entity.getDefinition().getWorldLocation());
			if (distance > CLICKBOX_RADIUS_TILES || distance >= bestDistance)
			{
				// Too far to be clickable, or further than something already hit —
				// either way, do not pay for its clickbox. This is the whole of the
				// distance gate, and it is placed before the projection rather than
				// after it on purpose.
				continue;
			}

			Shape clickbox = clickbox(entity, worldView);
			if (clickbox == null || !clickbox.contains(mouse.getX(), mouse.getY()))
			{
				continue;
			}

			best = entity;
			bestDistance = distance;
		}

		return best;
	}

	/**
	 * The on-screen clickable area of one citizen, or {@code null} if it has no
	 * model yet or is off screen.
	 *
	 * <p>Package-private and non-final so a test can stand in for it. There is no
	 * other way: {@code Perspective.getClickbox} projects through the live camera —
	 * {@code get3dZoom}, {@code getCameraX/Y/Z}, the rasteriser clip fields — and
	 * this repo has no mocking framework and does not use reflection, so the seam is
	 * the override. Everything that decides <i>whether</i> to call this, which is
	 * the part issue #14 is about, is on this side of the seam and is tested.
	 *
	 * <p>{@code Perspective.getClickbox} is marked {@code @ApiStatus.Internal} with
	 * a javadoc that says to use {@code TileObject#getClickbox()} instead. There is
	 * no such method to use: a {@code RuneLiteObject} is neither a {@code TileObject}
	 * nor an {@code Actor}, and the client gives it no click tag (see the class
	 * javadoc), so this is the only projection available and it is the one the
	 * hub-merged precedents use.
	 */
	@Nullable
	Shape clickbox(LivelyEntity entity, WorldView worldView)
	{
		final Model model = entity.getRenderedModel();
		final LocalPoint at = entity.getRenderLocation();
		if (model == null || at == null)
		{
			return null;
		}

		return Perspective.getClickbox(
			client,
			worldView,
			model,
			entity.getRenderOrientation(),
			at.getX(),
			at.getY(),
			entity.getRenderZ());
	}

	/**
	 * Prints one line into the local chat buffer.
	 *
	 * <p>Package-private and non-final for the same reason as
	 * {@link #clickbox}: it is the one client call the click handler makes, and a
	 * test wants to read what was said.
	 */
	void message(String text)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null);
	}

	/**
	 * Three entries, all RUNELITE, all deprioritised, all at the bottom of the menu.
	 *
	 * <p>Inserted Examine, Hide, Mute, which — because each insert goes in at index
	 * 0 and the array renders last-first — puts them on screen in that order with
	 * Mute at the very bottom.
	 *
	 * <p>Mute is only offered to a citizen that has something to say. An entry that
	 * cannot do anything is worse than a missing one: the user who clicks it learns
	 * that the plugin's menu lies.
	 */
	private void addEntries(LivelyEntity entity)
	{
		final EntityDefinition definition = entity.getDefinition();

		entry(OPTION_EXAMINE, definition);
		entry(OPTION_HIDE, definition);

		if (entity.getRemarks() != null)
		{
			entry(OPTION_MUTE, definition);
		}
	}

	/**
	 * One entry.
	 *
	 * <p>{@code client.getMenu().createMenuEntry(int)} and not
	 * {@code Client.createMenuEntry(int)} — the latter is {@code @Deprecated} in
	 * 1.12.36 in favour of exactly this.
	 */
	private void entry(String option, EntityDefinition definition)
	{
		MenuEntry created = client.getMenu().createMenuEntry(0)
			.setOption(option)
			.setTarget(CitizenLabel.menuTarget(definition))
			.setType(MenuAction.RUNELITE)
			.setIdentifier(token)
			.setDeprioritized(true);

		if (log.isDebugEnabled())
		{
			log.debug("added '{}' for {} (identifier {}, deprioritized {})",
				option, definition.label(), created.getIdentifier(), created.isDeprioritized());
		}
	}
}
