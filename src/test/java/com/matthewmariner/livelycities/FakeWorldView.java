package com.matthewmariner.livelycities;

import net.runelite.api.Constants;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * The eight {@link WorldView} methods the render core and
 * {@code LocalPoint.fromWorld} need: the loaded map regions, the plane, and the
 * scene rectangle.
 *
 * <p>{@link #around} places the scene the way the client does — a
 * {@link Constants#SCENE_SIZE}-tile square with the player's chunk in the middle
 * — so an entity within the cull radius of the player resolves to a LocalPoint
 * for the same reason it does in game, rather than because the fake is
 * permissive.
 */
final class FakeWorldView extends StubWorldView
{
	private int[] mapRegions;
	private int plane;
	private int baseX;
	private int baseY;
	private boolean instance;

	private FakeWorldView(int[] mapRegions, int plane, int baseX, int baseY)
	{
		this.mapRegions = mapRegions;
		this.plane = plane;
		this.baseX = baseX;
		this.baseY = baseY;
	}

	/**
	 * A scene centred on the player's chunk, covering the given map regions.
	 */
	static FakeWorldView around(WorldPoint player, int... mapRegions)
	{
		int baseX = chunkAlignedBase(player.getX());
		int baseY = chunkAlignedBase(player.getY());
		return new FakeWorldView(mapRegions, player.getPlane(), baseX, baseY);
	}

	private static int chunkAlignedBase(int coordinate)
	{
		// The client's own arithmetic: the player's chunk sits six chunks in.
		return ((coordinate / Constants.CHUNK_SIZE) - 6) * Constants.CHUNK_SIZE;
	}

	void setMapRegions(int... regions)
	{
		mapRegions = regions;
	}

	void setInstance(boolean instance)
	{
		this.instance = instance;
	}

	void setPlane(int plane)
	{
		this.plane = plane;
	}

	@Override
	public int[] getMapRegions()
	{
		return mapRegions;
	}

	@Override
	public boolean isInstance()
	{
		return instance;
	}

	@Override
	public int getPlane()
	{
		return plane;
	}

	@Override
	public int getBaseX()
	{
		return baseX;
	}

	@Override
	public int getBaseY()
	{
		return baseY;
	}

	@Override
	public int getSizeX()
	{
		return Constants.SCENE_SIZE;
	}

	@Override
	public int getSizeY()
	{
		return Constants.SCENE_SIZE;
	}

	@Override
	public int getId()
	{
		return WorldView.TOPLEVEL;
	}

	@Override
	public boolean isTopLevel()
	{
		return true;
	}
}
