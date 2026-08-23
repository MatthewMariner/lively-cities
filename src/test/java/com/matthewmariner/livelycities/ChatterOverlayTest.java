package com.matthewmariner.livelycities;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The one overlay that draws overhead text, and the two bug classes it is shaped
 * to make impossible.
 *
 * <p>The predecessor's text bugs were orphaned bubbles and text drifting off its
 * NPC. Both came from a renderer with its own list of what to draw and its own
 * idea of where, so both are tested here as claims about the loop: it walks the
 * live entity set, it asks the client whether each entity is still registered, and
 * it re-projects the object's own position every frame.
 *
 * <p><b>The projection is a seam, and it has to be.</b>
 * {@code Perspective.getCanvasTextLocation} reads the live camera —
 * {@code get3dZoom}, {@code getCameraX/Y/Z}, the rasteriser clip fields — and this
 * repo has no mocking framework and does not use reflection. So
 * {@link RecordingOverlay} overrides it. The one test that does <i>not</i> override
 * it is {@link #aProjectionThatFailsDrawsNothingRatherThanThrowing()}, which runs
 * the real {@code Perspective} call against {@link FakeClient} — whose
 * {@code getWorldView(int)} returns null, exactly as the real client's does for a
 * view that has gone — and so exercises the real null path rather than a
 * simulation of it.
 */
public class ChatterOverlayTest
{
	private static final int VARROCK_NORTH = 12853;
	private static final WorldPoint PLAYER = new WorldPoint(3220, 3420, 0);

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private EntityScene scene;
	private FakeWorldView view;
	private Graphics2D graphics;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		regions = new FakeRegions();
		config = new FakeConfig();
		scene = new EntityScene(client, regions, config, config.overrides());
		view = FakeWorldView.around(PLAYER, VARROCK_NORTH);

		// A real Graphics2D, off a 1x1 image: getFontMetrics() has to work for the
		// real Perspective call in the projection test, and a stub Graphics2D would
		// be four hundred throwing methods for no gain.
		graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
	}

	@Test
	public void oneLineIsDrawnForEachTalkingCitizenAndNoneForTheSilentOnes()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		EntityDefinition alsoTalker = regions.talker(VARROCK_NORTH, 3221, 3421, "Lovely weather.");
		EntityDefinition silent = regions.citizen(VARROCK_NORTH, 3222, 3421, 0);
		spawn(talker, alsoTalker, silent);

		RecordingOverlay overlay = new RecordingOverlay();

		// Nobody is talking yet: the overlay must draw nothing rather than draw a
		// blank line per citizen.
		overlay.render(graphics);
		assertTrue("a scene where nobody is talking draws nothing", overlay.drawn.isEmpty());

		say(talker, "Busy today.");
		say(alsoTalker, "Lovely weather.");
		overlay.render(graphics);

		assertEquals("one line per talking citizen and no more", 2, overlay.drawn.size());
		assertTrue(overlay.drawn.contains("Busy today."));
		assertTrue(overlay.drawn.contains("Lovely weather."));
	}

	/**
	 * The hard off switch, upstream issue #35, on the frame path.
	 *
	 * <p>Checked here as well as in {@link CitizenChatter} so that unticking the box
	 * empties the screen on the same frame rather than on the next game tick.
	 */
	@Test
	public void theHardOffSwitchStopsTheOverlayDrawingAnything()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);
		say(talker, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);
		assertEquals("the fixture has to draw something first", 1, overlay.drawn.size());

		config.setOverheadText(false);
		overlay.drawn.clear();
		overlay.projections = 0;
		overlay.render(graphics);

		assertTrue("nothing may be drawn with overhead chatter off", overlay.drawn.isEmpty());
		assertEquals("and nothing may even be projected — the check is the first line",
			0, overlay.projections);
	}

	/**
	 * A citizen the client no longer has registered contributes nothing, on the very
	 * next frame.
	 *
	 * <p>The remark is deliberately left set on the wrapper here. In the running
	 * plugin {@code despawn()} clears it, and {@code CitizenChatterTest} pins that;
	 * this test unregisters the object <i>behind</i> the wrapper's back so that the
	 * only thing standing between a stale remark and a bubble drawn over empty
	 * ground is the overlay's own {@code isActive()} check. Without it, "the text
	 * vanishes" would be true only because something else remembered to clear it.
	 */
	@Test
	public void aDespawnedCitizensTextVanishesOnTheNextFrame()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);
		say(talker, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);
		assertEquals(1, overlay.drawn.size());

		// The visibility pass takes it off the screen — through the config, which is
		// the real path — and then the remark is put back by hand.
		config.disable(City.VARROCK);
		scene.updateVisibility(PLAYER, view);
		assertEquals("the fixture has to actually despawn it", 0, scene.countActive());
		say(talker, "Busy today.");

		overlay.drawn.clear();
		overlay.render(graphics);

		assertTrue("text must never be drawn for a citizen the client is not rendering",
			overlay.drawn.isEmpty());
	}

	/**
	 * The position comes from the object, per frame, not from the authored tile.
	 *
	 * <p>This is the drifting-text half. A wandering citizen's authored tile is
	 * where it started, not where it is; projecting from the definition would leave
	 * the text standing still while its citizen walked away from it.
	 */
	@Test
	public void theTextFollowsTheObjectRatherThanTheAuthoredTile()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);
		say(talker, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(1, overlay.projectedAt.size());
		LocalPoint expected = scene.wrapperFor(talker).getRenderLocation();
		assertNotNull("the fixture has to be placed", expected);
		assertEquals("the overlay must project the object's own live position",
			expected.getX(), overlay.projectedAt.get(0).getX());
		assertEquals(expected.getY(), overlay.projectedAt.get(0).getY());
	}

	/**
	 * A projection that fails draws nothing and does not throw — using the real
	 * {@code Perspective} call, not a stand-in for it.
	 *
	 * <p>Behind the camera, off the edge of the viewport, or on a world view the
	 * client no longer has: all three happen constantly and none is an error. This
	 * runs sixty times a second per talking citizen, so a throw here would be sixty
	 * stack traces a second <i>and</i> would abandon every citizen after the
	 * offender on every one of them.
	 */
	@Test
	public void aProjectionThatFailsDrawsNothingRatherThanThrowing()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);
		say(talker, "Busy today.");

		// No override of textLocation: this really does call
		// Perspective.getCanvasTextLocation, which asks the client for the world
		// view by id and returns null when there is none.
		DrawRecordingOverlay overlay = new DrawRecordingOverlay();

		assertNull("the real projection has to fail for this fixture, or the test proves nothing",
			overlay.textLocation(graphics, scene.wrapperFor(talker).getRenderLocation(), "Busy today."));

		overlay.render(graphics);

		assertTrue("a failed projection draws nothing", overlay.drawn.isEmpty());
	}

	/**
	 * A citizen with no model yet — a cold model cache — has no position to project,
	 * and that must not be a null dereference on the frame path.
	 */
	@Test
	public void aCitizenWithNoObjectYetIsSkipped()
	{
		client.setCacheCold(true);
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		regions.file(VARROCK_NORTH, talker);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals("a cold cache must not have spawned anything", 0, scene.countActive());

		say(talker, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertTrue(overlay.drawn.isEmpty());
	}

	// --- helpers ------------------------------------------------------------

	private void spawn(EntityDefinition... entities)
	{
		regions.file(VARROCK_NORTH, entities);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertTrue("the fixture has to actually spawn", scene.countActive() > 0);
	}

	/**
	 * Puts a specific remark on a specific citizen.
	 *
	 * <p>Set directly rather than by running the chatter until it happens: these
	 * tests are about the frame path, and driving them through the cadence would make
	 * every one of them also a test of the cadence.
	 */
	private void say(EntityDefinition definition, String expected)
	{
		CitizenRemarks remarks = scene.wrapperFor(definition).getRemarks();
		assertNotNull(remarks);
		remarks.say(0, Integer.MAX_VALUE);
		assertEquals("the fixture's citizen has exactly one thing to say, so this is deterministic",
			expected, remarks.text());
	}

	/**
	 * The overlay with both seams recorded and the projection replaced by a fixed
	 * point, so the loop's decisions are observable without a camera.
	 */
	private final class RecordingOverlay extends ChatterOverlay
	{
		private final List<String> drawn = new ArrayList<>();
		private final List<LocalPoint> projectedAt = new ArrayList<>();
		private int projections;

		private RecordingOverlay()
		{
			super(null, client, scene, config);
		}

		@Override
		@Nullable
		Point textLocation(Graphics2D graphics, LocalPoint at, String text)
		{
			projections++;
			projectedAt.add(at);
			return new Point(100, 200);
		}

		@Override
		void drawRemark(Graphics2D graphics, Point at, String text)
		{
			drawn.add(text);
		}
	}

	/**
	 * Only the draw is recorded; the projection is the real one. Used by the single
	 * test that wants {@code Perspective} to actually run.
	 */
	private final class DrawRecordingOverlay extends ChatterOverlay
	{
		private final List<String> drawn = new ArrayList<>();

		private DrawRecordingOverlay()
		{
			super(null, client, scene, config);
		}

		@Override
		void drawRemark(Graphics2D graphics, Point at, String text)
		{
			drawn.add(text);
		}
	}
}
