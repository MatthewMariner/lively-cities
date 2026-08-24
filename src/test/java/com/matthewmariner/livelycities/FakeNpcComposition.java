package com.matthewmariner.livelycities;

import javax.annotation.Nullable;

/**
 * One NPC's appearance as fields: a name, a model array, and the two recolour
 * arrays.
 *
 * <p>Every one of the four is independently settable and independently
 * {@code null}able, because those are the four different shapes the real thing
 * comes in and {@code NpcAppearance} answers each one differently. In the injected
 * client (1.12.36) {@code pl.getModels()}, {@code pl.getColorToReplace()} and
 * {@code pl.getColorToReplaceWith()} are all a bare {@code getfield} on an array
 * field, so {@code null} is the honest value for a composition whose cache entry
 * did not carry that opcode — a fake that returned empty arrays instead would make
 * the null checks in {@code NpcAppearance} untestable.
 *
 * <p>Everything else is inherited from {@link StubNpcComposition} and throws.
 */
final class FakeNpcComposition extends StubNpcComposition
{
	@Nullable
	private final String name;

	@Nullable
	private final int[] models;

	@Nullable
	private final short[] colorToReplace;

	@Nullable
	private final short[] colorToReplaceWith;

	private FakeNpcComposition(
		@Nullable String name,
		@Nullable int[] models,
		@Nullable short[] colorToReplace,
		@Nullable short[] colorToReplaceWith)
	{
		this.name = name;
		this.models = models;
		this.colorToReplace = colorToReplace;
		this.colorToReplaceWith = colorToReplaceWith;
	}

	/** A plain NPC: some models, no recolours declared (both arrays null). */
	static FakeNpcComposition of(String name, int... models)
	{
		return new FakeNpcComposition(name, models, null, null);
	}

	/** An NPC with a palette. */
	static FakeNpcComposition recoloured(
		String name, int[] models, short[] colorToReplace, short[] colorToReplaceWith)
	{
		return new FakeNpcComposition(name, models, colorToReplace, colorToReplaceWith);
	}

	/**
	 * A composition that resolves but has nothing to draw — the second of the two
	 * failure shapes {@code NpcAppearance} has to skip. Distinct from the id that
	 * throws, and it has to be: a fake that could only model the throw would leave
	 * the {@code getModels()} branch green for the wrong reason.
	 */
	static FakeNpcComposition withoutModels(String name, @Nullable int[] models)
	{
		return new FakeNpcComposition(name, models, null, null);
	}

	@Override
	@Nullable
	public String getName()
	{
		return name;
	}

	@Override
	@Nullable
	public int[] getModels()
	{
		return models;
	}

	@Override
	@Nullable
	public short[] getColorToReplace()
	{
		return colorToReplace;
	}

	@Override
	@Nullable
	public short[] getColorToReplaceWith()
	{
		return colorToReplaceWith;
	}
}
