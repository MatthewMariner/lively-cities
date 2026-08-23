package com.matthewmariner.livelycities;

import net.runelite.api.Animation;

/**
 * A short looping animation, real enough for {@code AnimationController} to walk
 * frames through.
 *
 * <p>The render core's earlier tests only ever asked <i>which</i> animation ids
 * were requested, so {@link FakeClient} could return null and
 * {@code AnimationController.tick} would return immediately. That is no longer
 * enough: the whole animation-smoothing claim is that the frame counter advances
 * between game ticks and survives everything the plugin does in between, and a
 * null animation makes {@code tick} a no-op — a fixture too uniform to
 * distinguish a working switch from a broken one.
 *
 * <p>The numbers are chosen against {@code AnimationController}'s real
 * arithmetic (disassembled from 1.12.36): the non-Maya path accumulates
 * {@code elapsedTicks} and advances while {@code elapsedTicks > frameLengths[frame]},
 * and {@code loop()} rewinds by {@code getFrameStep()} and resets to 0 if that
 * leaves the frame outside {@code getDuration()}. So a frame length of 3 client
 * ticks means one {@code tick(4)} advances exactly one frame, and a frame step
 * equal to the frame count makes the loop land back on frame 0.
 *
 * <p>All ten {@link Animation} methods are implemented rather than inheriting a
 * throwing stub: at ten methods a stub layer would be more code than the honest
 * answers.
 */
final class FakeAnimation implements Animation
{
	static final int FRAMES = 8;
	static final int FRAME_LENGTH_CLIENT_TICKS = 3;

	/** Client ticks that advance the frame counter by exactly one. */
	static final int CLIENT_TICKS_PER_FRAME = FRAME_LENGTH_CLIENT_TICKS + 1;

	private final int id;
	private final int[] frameLengths;

	private int restartMode;

	FakeAnimation(int id)
	{
		this.id = id;
		this.frameLengths = new int[FRAMES];
		for (int i = 0; i < FRAMES; i++)
		{
			frameLengths[i] = FRAME_LENGTH_CLIENT_TICKS;
		}
	}

	@Override
	public int getId()
	{
		return id;
	}

	@Override
	public boolean isMayaAnim()
	{
		return false;
	}

	@Override
	public int getNumFrames()
	{
		return FRAMES;
	}

	@Override
	public int getRestartMode()
	{
		return restartMode;
	}

	@Override
	public void setRestartMode(int restartMode)
	{
		this.restartMode = restartMode;
	}

	@Override
	public int getDuration()
	{
		return FRAMES;
	}

	@Override
	public int getFrameStep()
	{
		return FRAMES;
	}

	@Override
	public int[] getFrameLengths()
	{
		return frameLengths;
	}

	@Override
	public int getLeftHandItem()
	{
		return -1;
	}

	@Override
	public int getRightHandItem()
	{
		return -1;
	}
}
