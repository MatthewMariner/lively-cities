package com.matthewmariner.livelycities;

import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Stands in for {@link LivelyCitiesPlugin}'s game-tick handler on a real
 * {@code EventBus}: runs the visibility pass at the default priority and nothing else.
 *
 * <p><b>Its name is the fixture.</b> {@code EventBus} orders subscribers by
 * {@code comparingDouble(Subscriber::getPriority).reversed().thenComparing(s ->
 * s.object.getClass().getName())} — verified in the 1.12.36 bytecode — so equal
 * priorities fall back to <i>alphabetical order of the subscriber's class name</i>.
 *
 * <p>That tiebreak is why this is a named top-level class instead of an anonymous one
 * inside the test. An anonymous subscriber is {@code …livelycities.FrameTimingsTest$3},
 * and {@code F} sorts before {@code L}, so it would run before
 * {@link LivelyCitiesDevReportsPlugin} whatever the priority said — making the
 * ordering test pass on alphabetical luck rather than on the annotation under test.
 *
 * <p>Production sorts the other way, and that is the whole point:
 * {@code LivelyCitiesDevReportsPlugin} sorts <i>before</i> {@code LivelyCitiesPlugin}
 * ({@code D} before {@code P}), so at equal priority the reporter would run before the
 * pass it measures and every report would omit the tick that triggered it.
 * {@code TickPassRunner} reproduces that relationship — {@code T} after {@code L} — so
 * only the reporter's negative priority can put it last, and deleting that priority
 * turns the test red the way it should.
 */
public final class TickPassRunner
{
	private final EntityScene scene;
	private final net.runelite.api.coords.WorldPoint player;
	private final net.runelite.api.WorldView view;

	TickPassRunner(EntityScene scene, net.runelite.api.coords.WorldPoint player,
		net.runelite.api.WorldView view)
	{
		this.scene = scene;
		this.player = player;
		this.view = view;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		scene.updateVisibility(player, view);
	}
}
