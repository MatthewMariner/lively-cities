package com.matthewmariner.livelycities;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link NpcAppearance}: given a client's answer about an NPC id, what does the
 * appearance come out as, and which answers are refused?
 *
 * <p>The four fixture shapes here exist because the real
 * {@code NPCComposition}'s three array accessors are each a bare {@code getfield}
 * in 1.12.36 (see {@link NpcAppearance}'s javadoc), so {@code null} is a value the
 * client really hands out and a fake that only ever produced arrays would leave
 * every null check untestable. {@link FakeNpcComposition} makes each one settable
 * separately for that reason.
 */
public class NpcAppearanceTest
{
	private static final int WHITE_KNIGHT = 1798;
	private static final String LABEL = "Rob@3158,3494,0";

	private FakeClient client;

	@Before
	public void setUp()
	{
		client = new FakeClient();
	}

	@Test
	public void aCompositionsModelsAndPaletteBothComeThrough()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.recoloured(
			"White Knight",
			new int[]{217, 305, 246},
			new short[]{10, 20},
			new short[]{11, 21}));

		NpcAppearance appearance = NpcAppearance.resolve(client, WHITE_KNIGHT, LABEL);

		assertNotNull(appearance);
		assertEquals(WHITE_KNIGHT, appearance.getNpcId());
		assertEquals("White Knight", appearance.getNpcName());
		assertArrayEquals(new int[]{217, 305, 246}, appearance.getModelIds());
		assertArrayEquals(new short[]{10, 20}, appearance.getRecolorFind());
		assertArrayEquals(new short[]{11, 21}, appearance.getRecolorReplace());
	}

	/**
	 * Most NPCs declare no recolours at all, and the client answers that with two
	 * {@code null}s rather than two empty arrays. An appearance with an empty palette
	 * has to be a working appearance, not a refused one — otherwise the mechanism
	 * would only work for NPCs that happen to be recoloured.
	 */
	@Test
	public void anNpcWithNoPaletteStillResolves()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", 217, 305));

		NpcAppearance appearance = NpcAppearance.resolve(client, WHITE_KNIGHT, LABEL);

		assertNotNull(appearance);
		assertEquals(0, appearance.getRecolorFind().length);
		assertEquals(0, appearance.getRecolorReplace().length);
		assertArrayEquals(new int[]{217, 305}, appearance.getModelIds());
	}

	/**
	 * Lopsided recolour arrays are truncated to the matched pairs, exactly as
	 * {@code EntityDefinition.recolorPairCount} does for an authored record — because
	 * the alternative is an {@code ArrayIndexOutOfBoundsException} inside the render
	 * loop, and the two halves are indexed together in {@code LivelyEntity.assemble}.
	 *
	 * <p>Asserted in both directions: a longer {@code find} and a longer
	 * {@code replace} are different array accesses, and a fixture that only tried one
	 * would leave the other's bound unchecked.
	 */
	@Test
	public void lopsidedRecolourArraysAreTruncatedToTheMatchedPairs()
	{
		client.withNpc(1, FakeNpcComposition.recoloured(
			"Longer find", new int[]{217}, new short[]{10, 20, 30}, new short[]{11}));
		client.withNpc(2, FakeNpcComposition.recoloured(
			"Longer replace", new int[]{217}, new short[]{10}, new short[]{11, 21, 31}));

		NpcAppearance longerFind = NpcAppearance.resolve(client, 1, LABEL);
		assertNotNull(longerFind);
		assertArrayEquals(new short[]{10}, longerFind.getRecolorFind());
		assertArrayEquals(new short[]{11}, longerFind.getRecolorReplace());

		NpcAppearance longerReplace = NpcAppearance.resolve(client, 2, LABEL);
		assertNotNull(longerReplace);
		assertArrayEquals(new short[]{10}, longerReplace.getRecolorFind());
		assertArrayEquals(new short[]{11}, longerReplace.getRecolorReplace());
	}

	/**
	 * One half of the palette present and the other {@code null} is the same
	 * lopsidedness with a harder edge — a {@code .length} on the null one would be an
	 * NPE inside a render pass.
	 */
	@Test
	public void oneHalfOfThePaletteBeingNullYieldsNoPaletteRatherThanAThrow()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.recoloured(
			"Half a palette", new int[]{217}, new short[]{10, 20}, null));

		NpcAppearance appearance = NpcAppearance.resolve(client, WHITE_KNIGHT, LABEL);

		assertNotNull(appearance);
		assertEquals(0, appearance.getRecolorFind().length);
		assertEquals(0, appearance.getRecolorReplace().length);
	}

	/**
	 * The ordinary "this id no longer exists" case, and the reason
	 * {@code NpcAppearance} has a try/catch at all: 1.12.36 throws rather than
	 * returning null for an id whose archive entry is missing. See
	 * {@code FakeClient.getNpcDefinition}.
	 */
	@Test
	public void anIdThatThrowsResolvesToNullRatherThanPropagating()
	{
		assertNull(NpcAppearance.resolve(client, 999999, LABEL));
		assertTrue("the client still has to have been asked, or this proves nothing",
			client.npcDefinitionsRequested().contains(999999));
	}

	/** The other way of not getting an answer: a null composition. */
	@Test
	public void anIdThatResolvesToNoCompositionResolvesToNull()
	{
		client.setNullNpcComposition(WHITE_KNIGHT);
		assertNull(NpcAppearance.resolve(client, WHITE_KNIGHT, LABEL));
	}

	/**
	 * A composition that resolves but has nothing to draw. Three shapes of it, all
	 * refused, because "the lookup worked" is not the same question as "there is a
	 * body" — and an audit that only checked the lookup would report a green id for
	 * an invisible citizen.
	 */
	@Test
	public void aCompositionWithNothingToDrawIsRefused()
	{
		client.withNpc(1, FakeNpcComposition.withoutModels("Null models", null));
		client.withNpc(2, FakeNpcComposition.withoutModels("Empty models", new int[0]));
		client.withNpc(3, FakeNpcComposition.withoutModels("Only empty slots", new int[]{-1, 0}));

		assertNull("a null getModels() is not an empty array", NpcAppearance.resolve(client, 1, LABEL));
		assertNull(NpcAppearance.resolve(client, 2, LABEL));
		assertNull("every slot empty is nothing to draw", NpcAppearance.resolve(client, 3, LABEL));
	}

	/**
	 * A composition with <i>some</i> empty slots keeps the rest. That is not the
	 * "partial model" this plugin refuses: a {@code -1} in a composition's array is
	 * the client's own "this equipment slot is empty", whereas a partial build is a
	 * part that failed to <i>load</i> — which {@code LivelyEntity.loadParts} still
	 * refuses wholesale.
	 */
	@Test
	public void emptySlotsAreDroppedAndTheRealModelsKept()
	{
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("Gappy", 217, -1, 305, 0, 246));

		NpcAppearance appearance = NpcAppearance.resolve(client, WHITE_KNIGHT, LABEL);

		assertNotNull(appearance);
		assertArrayEquals(new int[]{217, 305, 246}, appearance.getModelIds());
	}

	/**
	 * A non-positive id never reaches the client. Not reachable through
	 * {@code EntityDefinition}, which drops one at the validation gate — asserted
	 * here so the guard cannot be removed on the grounds that nothing exercises it.
	 */
	@Test
	public void aNonPositiveIdIsRefusedWithoutAskingTheClient()
	{
		assertNull(NpcAppearance.resolve(client, 0, LABEL));
		assertNull(NpcAppearance.resolve(client, -1798, LABEL));
		assertTrue("the client must not be asked about a sentinel",
			client.npcDefinitionsRequested().isEmpty());
	}

	/**
	 * The models array is not the composition's own. The client caches compositions
	 * ({@code pl.cy}), so handing out the live array would let a recolour or a future
	 * in-place edit reach every other user of that NPC — the same reasoning behind
	 * {@code LivelyEntity.assemble} always calling {@code mergeModels} before
	 * touching colours.
	 */
	@Test
	public void theModelsArrayIsACopyOfTheCompositionsOwn()
	{
		int[] compositionModels = {217, 305};
		client.withNpc(WHITE_KNIGHT, FakeNpcComposition.of("White Knight", compositionModels));

		NpcAppearance appearance = NpcAppearance.resolve(client, WHITE_KNIGHT, LABEL);

		assertNotNull(appearance);
		appearance.getModelIds()[0] = 999;
		assertEquals("the composition's own array must not have been written to",
			217, compositionModels[0]);
	}
}
