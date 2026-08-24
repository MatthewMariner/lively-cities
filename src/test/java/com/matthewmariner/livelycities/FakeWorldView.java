package com.matthewmariner.livelycities;

import javax.annotation.Nullable;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * The {@link WorldView} methods the render core and {@code LocalPoint.fromWorld}
 * need: the loaded map regions, the plane, the scene rectangle, and the collision
 * map.
 *
 * <p>{@link #around} places the scene the way the client does — a
 * {@link Constants#SCENE_SIZE}-tile square with the player's chunk in the middle
 * — so an entity within the cull radius of the player resolves to a LocalPoint
 * for the same reason it does in game, rather than because the fake is
 * permissive.
 *
 * <p><b>Collision defaults to open ground and is allocated lazily.</b> Zero means
 * walkable in the client's own convention, so a test that does not care about
 * collision gets a scene every tile of which a citizen could stand on — and pays
 * nothing for it, because the four 104x104 arrays are only built if somebody asks.
 * {@link #block} is how a test says what is not walkable, and
 * {@link #withoutCollisionData} is how it says the client has not built the maps
 * yet.
 */
final class FakeWorldView extends StubWorldView
{
	private int[] mapRegions;
	private int plane;
	private int baseX;
	private int baseY;
	private boolean instance;

	/** Which world view this is. 0 is {@link WorldView#TOPLEVEL}. */
	private int id = WorldView.TOPLEVEL;

	/**
	 * One map per plane, matching the client's own {@code new gc[4]}, or
	 * {@code null} while the scene has not produced any.
	 */
	@Nullable
	private FakeCollisionData[] collisionMaps;

	private boolean collisionDataAvailable = true;

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
		return id;
	}

	/**
	 * {@code dz.isTopLevel()} in the injected client is compiled to
	 * {@code getId() == 0}, so this derives from the id rather than being a second
	 * settable flag that could disagree with it.
	 */
	@Override
	public boolean isTopLevel()
	{
		return id == WorldView.TOPLEVEL;
	}

	/**
	 * Makes this a {@code WorldEntity}'s view rather than the top-level one.
	 *
	 * <p>Only {@code StandableGroundTest} uses it, and for one reason: the client
	 * sizes a non-top-level collision map with an origin one tile to the south-west,
	 * so scene coordinates read the wrong tile there — see {@link StandableGround}.
	 */
	FakeWorldView asWorldEntityView()
	{
		this.id = 7;
		return this;
	}

	// --- Collision ------------------------------------------------------------

	@Override
	@Nullable
	public CollisionData[] getCollisionMaps()
	{
		if (!collisionDataAvailable)
		{
			return null;
		}

		return maps();
	}

	/**
	 * Marks a tile as one nobody can stand on, with the whole
	 * {@code BLOCK_MOVEMENT_FULL} mask — a wall, a counter, or open water.
	 *
	 * @throws IllegalArgumentException if the tile is outside this scene, so a
	 * fixture that thought it was blocking something and was not fails loudly
	 * instead of passing for the wrong reason
	 */
	FakeWorldView block(WorldPoint tile)
	{
		return setFlags(tile, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
	}

	/** ORs an arbitrary flag mask into one tile. */
	FakeWorldView setFlags(WorldPoint tile, int mask)
	{
		int sceneX = tile.getX() - baseX;
		int sceneY = tile.getY() - baseY;
		if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE)
		{
			throw new IllegalArgumentException(
				"tile " + tile + " is outside the fake scene at " + baseX + "," + baseY);
		}

		maps()[tile.getPlane()].set(sceneX, sceneY, mask);
		return this;
	}

	/** The client has not built any collision maps yet. */
	FakeWorldView withoutCollisionData()
	{
		collisionDataAvailable = false;
		return this;
	}

	/** The array exists but this plane's map does not — the client's own lazy state. */
	FakeWorldView withoutCollisionMapFor(int planeToDrop)
	{
		maps()[planeToDrop] = null;
		return this;
	}

	private FakeCollisionData[] maps()
	{
		if (collisionMaps == null)
		{
			// Four, because the client allocates `new gc[4]` — one per plane.
			collisionMaps = new FakeCollisionData[4];
			for (int i = 0; i < collisionMaps.length; i++)
			{
				collisionMaps[i] = new FakeCollisionData(Constants.SCENE_SIZE, Constants.SCENE_SIZE);
			}
		}
		return collisionMaps;
	}
}
