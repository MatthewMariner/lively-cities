package com.matthewmariner.livelycities;

import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Runs enough visibility passes for a fixture's crowd to finish arriving.
 *
 * <p><b>Why this exists.</b> {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} caps a
 * single pass at three model builds, so "spawn five citizens and look at them" is no
 * longer one pass — it is two. That is the point of the cap and not an inconvenience:
 * the measured 53.73ms visibility spike was forty models built inside one game tick.
 * Twenty-four tests in this suite were written against the old behaviour, and every one
 * of them is about something else — teardown, eviction, city checkboxes, the crowd cap,
 * clickboxes. This is how they say "a few game ticks pass" without each one growing its
 * own loop.
 *
 * <p><b>What it is not.</b> It is not a way to make the budget invisible. It changes
 * <i>when</i> a citizen appears and never <i>whether</i> — so a test that used it to
 * paper over an entity that never spawns would still be red, because the assertion
 * after it is unchanged. Any test that is <i>about</i> the budget must not call this:
 * it has to drive the passes itself and count them, or it is asserting about the helper
 * instead of about the cap. {@code EntitySceneTest}'s budget block and
 * {@code FrameTimingsTest}'s do exactly that.
 *
 * <p><b>A fixed count rather than "until it stops changing."</b> Converging on a stable
 * active count sounds tidier and is a trap: a pass whose three builds all failed leaves
 * the count unchanged too, so a convergence loop would stop early on precisely the
 * fixture where stopping early is wrong, and would report a half-built crowd as a
 * settled one. The count below is derived from the two constants that bound the wait,
 * so it cannot rot if either moves.
 */
final class VisibilityPasses
{
	/**
	 * How many passes a full cap's worth of models needs: {@code ceil(80 / 3) + 1} = 28.
	 *
	 * <p>The ceiling covers the crowd, and the extra pass is the one that has nothing
	 * left to build — so a fixture that is genuinely finished is finished with a pass to
	 * spare, and this never depends on the division coming out even.
	 */
	static final int PASSES_TO_SETTLE =
		1 + (RenderPolicy.MAX_ACTIVE_OBJECTS + RenderPolicy.MAX_MODEL_BUILDS_PER_PASS - 1)
			/ RenderPolicy.MAX_MODEL_BUILDS_PER_PASS;

	private VisibilityPasses()
	{
	}

	/**
	 * Runs {@link #PASSES_TO_SETTLE} visibility passes, which is enough for every model
	 * a full crowd needs to have been built.
	 *
	 * <p>{@code updateVisibility} rather than {@code onGameTick}: this stands in for the
	 * passage of ticks as far as <i>spawning</i> is concerned, and a caller that also
	 * wanted its wanderers to have walked twenty-eight tiles would be asking for
	 * something else.
	 */
	static void settle(EntityScene scene, WorldPoint player, WorldView view)
	{
		for (int i = 0; i < PASSES_TO_SETTLE; i++)
		{
			scene.updateVisibility(player, view);
		}
	}

	/**
	 * The same wait, driven through whatever a caller uses to advance a tick — the
	 * plugin's own {@code GameTick} handler, for the tests that go through the plugin
	 * rather than straight at the scene.
	 */
	static void settle(Runnable oneTick)
	{
		for (int i = 0; i < PASSES_TO_SETTLE; i++)
		{
			oneTick.run();
		}
	}
}
