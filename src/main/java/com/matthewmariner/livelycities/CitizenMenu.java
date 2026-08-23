package com.matthewmariner.livelycities;

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
 *       standing behind it projects underneath it — see {@link #isWorldClick}.</li>
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
	 * <p><b>Honest limit:</b> a minimap right-click also carries "Walk here", so a
	 * citizen whose projected outline reaches under the minimap could still be
	 * offered there. That is a corner of the screen citizens are almost never drawn
	 * in, and the alternative — {@code WorldView.getSelectedSceneTile()} — is
	 * documented as "the last right clicked tile", which is a different question and
	 * one this could not verify without a live client.
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
