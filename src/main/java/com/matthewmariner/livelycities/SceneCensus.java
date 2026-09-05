package com.matthewmariner.livelycities;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One reading of what the scene is actually doing, taken on the client thread and
 * safe to look at from anywhere afterwards.
 *
 * <p><b>Why a snapshot and not five getters.</b> {@link EntityScene} already answers
 * every one of these questions individually, and the side panel wants all of them at
 * once, once a game tick. Asking five times means five walks of the wrapper cache and
 * five rounds of {@code RuneLiteObject.isActive()} calls into the client — for one
 * screenful of numbers that have to agree with each other. Worse, they would be five
 * readings taken at five moments: a panel that said "34 active" beside a per-city
 * breakdown adding to 33 is a panel nobody trusts again.
 *
 * <p><b>Why it is immutable and client-free.</b> It crosses a thread boundary. The
 * numbers are read where the client can be read and drawn on Swing's event dispatch
 * thread, so anything the panel could still dereference has to be a value rather than
 * a live view — the opposite decision from {@link EntityScene#inScopeEntities()},
 * which is deliberately a live view because its readers are already on the client
 * thread and need to see the set as it is <i>now</i>.
 *
 * <p>{@link #getActiveByCity()} holds only cities with at least one active figure, so
 * a reader has to treat a missing key as zero — see {@link #activeIn(City)}, which is
 * the accessor to use.
 */
final class SceneCensus
{
	/** What a caller with no scene at all should show: zeroes, not nulls. */
	static final SceneCensus EMPTY = new SceneCensus(0, 0, 0, 0, new EnumMap<>(City.class));

	private final int active;
	private final int inScope;
	private final int walking;
	private final int talking;
	private final Map<City, Integer> activeByCity;

	SceneCensus(int active, int inScope, int walking, int talking, EnumMap<City, Integer> activeByCity)
	{
		this.active = active;
		this.inScope = inScope;
		this.walking = walking;
		this.talking = talking;
		this.activeByCity = Collections.unmodifiableMap(activeByCity);
	}

	/**
	 * @return how many objects the client currently has registered for this plugin —
	 * the figure a player can count on screen, near enough
	 */
	int getActive()
	{
		return active;
	}

	/** @return how many definitions sit in a region the loaded scene covers */
	int getInScope()
	{
		return inScope;
	}

	/** @return how many active citizens the per-frame pass is interpolating */
	int getWalking()
	{
		return walking;
	}

	/** @return how many citizens have a remark on screen right now */
	int getTalking()
	{
		return talking;
	}

	/**
	 * @return the per-city breakdown, unmodifiable, holding only the cities with at
	 * least one active figure
	 */
	Map<City, Integer> getActiveByCity()
	{
		return activeByCity;
	}

	/** @return how many of {@link #getActive()} belong to one city, zero if none do */
	int activeIn(City city)
	{
		Integer n = activeByCity.get(city);
		return n == null ? 0 : n;
	}

	@Override
	public String toString()
	{
		return "SceneCensus{" + active + " active, " + inScope + " in scope, "
			+ walking + " walking, " + talking + " talking, " + activeByCity + '}';
	}
}
