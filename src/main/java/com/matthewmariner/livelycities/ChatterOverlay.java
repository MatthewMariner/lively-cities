package com.matthewmariner.livelycities;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * The one overlay that draws citizens' overhead text.
 *
 * <p><b>One overlay, and it derives everything from the live entity set every
 * frame.</b> That is the structural fix for two of the predecessor's bug classes
 * at once — text floating with nobody under it, and text drifting off the citizen
 * it belonged to. Both were symptoms of the same thing: a renderer with its own
 * list of what to draw and its own idea of where. There is no such list here.
 * Each frame this walks {@link EntityScene#inScopeEntities()}, skips anything the
 * client does not have registered, asks the entity what it is saying, and projects
 * the position the client is about to draw the model at. An entity that despawned
 * one frame ago is simply not in the loop's output, so its text is gone on the
 * next frame rather than when something remembers to remove it.
 *
 * <p><b>{@code Actor.setOverheadText} is not available and could not be.</b> A
 * {@code RuneLiteObject} is not an {@code Actor} — it has no overhead text, no
 * health bar and no native menu — so screen-space drawing is the only option, and
 * it is what the hub-merged precedents do.
 *
 * <p><b>Projection is allowed to fail, and failing must draw nothing.</b>
 * {@code Perspective.getCanvasTextLocation} returns null for a citizen behind the
 * camera, off the edge of the viewport, or on a world view the client no longer
 * has — all three of which happen constantly and none of which is an error. Every
 * one of them is a {@code continue}. This runs once per frame per talking citizen,
 * so a throw here would be a stack trace sixty times a second <i>and</i> would
 * abandon every citizen after the offender on every one of them, which is the same
 * reasoning behind the containment in {@link EntityScene}.
 *
 * <p><b>The hard off switch is checked here as well as in
 * {@link CitizenChatter}.</b> Not belt-and-braces: the chatter clears its state on
 * its next game tick, up to 600ms away, and a toggle that visibly lags the click
 * reads as a toggle that did not work. Checking it here is what makes unticking the
 * box silence the screen on the same frame.
 *
 * <p><b>Kept minimal, because it runs every frame.</b> No sorting, no allocation
 * per entity beyond the {@code LocalPoint} the client's own API hands back, and no
 * work at all for the common case of a citizen with nothing to say — which is 75
 * of the 109 shipped citizens, and most of the remaining 34 most of the time.
 */
class ChatterOverlay extends Overlay
{
	/**
	 * How far above the citizen's tile the text sits, in local units
	 * ({@link Perspective#LOCAL_TILE_SIZE} = 128 per tile).
	 *
	 * <p>A constant rather than the model's own height. {@code Model} in 1.12.36
	 * exposes {@code getBottomY()} and an {@code AABB}, neither of which is a
	 * documented "how tall is this" and both of which need a live, lit model to mean
	 * anything; {@code Actor.getLogicalHeight()}, which is what RuneLite's own actor
	 * overlays use, does not exist on a {@code RuneLiteObject}. Every citizen in the
	 * dataset is a human-scale single-tile model, so one number that clears a head
	 * is both honest and right. Scenery never talks.
	 */
	static final int TEXT_HEIGHT = 220;

	private final Client client;
	private final EntityScene scene;
	private final LivelyCitiesConfig config;

	@Inject
	ChatterOverlay(LivelyCitiesPlugin plugin, Client client, EntityScene scene, LivelyCitiesConfig config)
	{
		super(plugin);
		this.client = client;
		this.scene = scene;
		this.config = config;

		// ABOVE_SCENE so the text sits over the world but under the interface — the
		// same layer RuneLite's own world-space text overlays use. DYNAMIC because
		// there is no box to drag: the position comes from the citizens.
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);

		// Below everything that is actually telling the user something. This is
		// ambience; it must never end up drawn over a warning.
		setPriority(Overlay.PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.overheadText())
		{
			// The hard off switch, upstream issue #35. Checked first so that "off"
			// costs one field read per frame.
			return null;
		}

		final List<LivelyEntity> entities = scene.inScopeEntities();
		for (int i = 0; i < entities.size(); i++)
		{
			LivelyEntity entity = entities.get(i);

			CitizenRemarks remarks = entity.getRemarks();
			if (remarks == null || !remarks.isTalking())
			{
				continue;
			}

			// Asked of the client, not of local bookkeeping: this is what makes a
			// despawned citizen's text disappear on the next frame rather than on
			// the next tick.
			if (!entity.isActive())
			{
				continue;
			}

			LocalPoint at = entity.getRenderLocation();
			if (at == null)
			{
				continue;
			}

			String text = remarks.text();
			if (text == null)
			{
				// isTalking() and text() are the same field; this is here so the
				// null-check the compiler wants is honest rather than an assertion
				// about ordering.
				continue;
			}

			Point on = textLocation(graphics, at, text);
			if (on == null)
			{
				// Behind the camera, off the viewport, or on a world view that has
				// gone. Not an error; draw nothing.
				continue;
			}

			drawRemark(graphics, on, text);
		}

		return null;
	}

	/**
	 * Projects a citizen's position to the screen.
	 *
	 * <p>Package-private and non-final so a test can stand in for it — the real
	 * implementation reads the live camera through
	 * {@code Perspective.localToCanvas}, and this repo has no mocking framework and
	 * does not use reflection. Everything the tests care about — which citizens are
	 * considered, and what happens when this returns null — is on this side of the
	 * seam.
	 *
	 * @return the canvas point, or {@code null} if the citizen cannot be drawn
	 */
	@Nullable
	Point textLocation(Graphics2D graphics, LocalPoint at, String text)
	{
		return Perspective.getCanvasTextLocation(client, graphics, at, text, TEXT_HEIGHT);
	}

	/**
	 * Draws one remark. Package-private and non-final for the same reason as
	 * {@link #textLocation}.
	 */
	void drawRemark(Graphics2D graphics, Point at, String text)
	{
		// The same colour as the menu targets, so overhead text is one more place
		// that says "this is not a real NPC" — see CitizenLabel.
		OverlayUtil.renderTextLocation(graphics, at, text, CitizenLabel.FAKE_COLOUR);
	}
}
