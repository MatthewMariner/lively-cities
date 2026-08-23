package com.matthewmariner.livelycities;

import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * The local player, as far as this plugin is concerned: a tile, and nothing else.
 *
 * <p>{@code getWorldLocation()} really is the only thing
 * {@link LivelyCitiesPlugin} asks a {@code Player} for, so everything else stays
 * on {@link StubPlayer} and throws. The tile is settable and may be set to
 * {@code null}, because "logged in but the location is not there yet" is a real
 * state the plugin has to survive rather than a hypothetical.
 */
final class FakePlayer extends StubPlayer
{
	@Nullable
	private WorldPoint location;

	FakePlayer(@Nullable WorldPoint location)
	{
		this.location = location;
	}

	void setWorldLocation(@Nullable WorldPoint location)
	{
		this.location = location;
	}

	@Override
	@Nullable
	public WorldPoint getWorldLocation()
	{
		return location;
	}
}
