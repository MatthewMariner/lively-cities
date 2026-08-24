package com.matthewmariner.livelycities;

import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;

/**
 * One existing NPC's appearance — its model parts and its recolour pairs — read
 * off the client so a record can wear it instead of listing raw {@code modelIds}.
 *
 * <p><b>Why this is the better mechanism, and why it is not a retrofit.</b> A raw
 * model id is an unnamed number that a game update can renumber underneath the
 * dataset; that is exactly what killed the predecessor plugin (see
 * {@code README.md}). An NPC id is a generated constant in
 * {@code net.runelite.api.gameval.NpcID}, reviewable by anyone with the jar, and
 * one indirection further from the geometry an artist reworks. So new authored
 * content prefers this, and the vendored entities keep their {@code modelIds}
 * unchanged with one exception — "Rufus", whose authored array had no footwear model
 * in it at all (GitHub issue #1), so there was nothing worth keeping.
 * {@code EntityRecord.npcAppearanceId} says which wins when a record carries both.
 *
 * <p><b>The accessors, verified against 1.12.36 rather than guessed.</b>
 * {@code javap net.runelite.api.Client} declares
 * {@code public abstract net.runelite.api.NPCComposition getNpcDefinition(int);}
 * and {@code javap net.runelite.api.NPCComposition} declares exactly three
 * accessors this class needs — {@code public abstract int[] getModels();},
 * {@code public abstract short[] getColorToReplace();} and
 * {@code public abstract short[] getColorToReplaceWith();} (plus
 * {@code getName()}, used only for the log line). In the injected client
 * ({@code injected-client-1.12.36.jar}) the composition is class {@code pl}, and
 * all three of those accessors compile to a bare {@code getfield} + {@code areturn}
 * on {@code cf:[I}, {@code dt:[S} and {@code dr:[S} respectively — <b>so every one
 * of them can be {@code null}</b>, because those fields are only assigned for a
 * composition whose cache entry carried that opcode. Nothing here treats a null as
 * an empty array.
 *
 * <p><b>Two ways of not resolving, and both are handled.</b>
 * {@code client.getNpcDefinition(id)} is {@code client.kz(id)} → {@code oh.ae(id, ..)},
 * which asserts {@code isClientThread()} and then looks the id up in
 * {@code pl.cy} (a cache) or loads it from archive 9. When the archive has no
 * entry, {@code va.bb(..)} returns {@code null} and the {@code pl} constructor is
 * handed a {@code null} buffer — inside a {@code catch (RuntimeException)} that
 * rethrows as the client's own wrapper. So an id that does not resolve
 * <b>throws</b>, rather than returning {@code null}, and a composition that does
 * resolve may still have no models. Both come back from {@link #resolve} as
 * {@code null}, and the caller ({@link LivelyEntity#loadParts()}) treats that
 * exactly like a model-cache miss: the entity does not spawn, one {@code warn} is
 * written, and it is retried on the same bounded, backed-off budget. Never a
 * partial build, never a throw out of here, and never a silent fall back to the
 * record's own {@code modelIds} — a different body in the right place is worse than
 * no body.
 *
 * <p><b>Deliberately not cloned from the composition:</b> {@code getWidthScale()} /
 * {@code getHeightScale()} and {@code getSize()}. The scale pair is a 128-based
 * multiplier in the client's own units and the dataset's {@code scale} field is
 * tile fractions with an inverted sign convention (see
 * {@code LivelyEntity.assemble}); mapping between them is arithmetic this could not
 * verify without a live client, and getting it wrong silently is a citizen at the
 * wrong height. The seven NPCs this currently sources are ordinary human-sized
 * townsfolk, so identity scale is the right answer for them — a future
 * non-human-sized NPC would render at default size, which is a known limitation
 * rather than a surprise. {@code transform()} is likewise not called: it reads live
 * varbit state to pick between a multi-form NPC's shapes, and a multi-form NPC
 * whose base composition has no models simply fails the {@code getModels()} check
 * above and is skipped with a warn naming it.
 *
 * <p><b>Client thread only</b> — {@code getNpcDefinition} asserts it.
 */
@Slf4j
final class NpcAppearance
{
	private final int npcId;
	private final String npcName;
	private final int[] modelIds;
	private final short[] recolorFind;
	private final short[] recolorReplace;

	private NpcAppearance(
		int npcId,
		@Nullable String npcName,
		int[] modelIds,
		short[] recolorFind,
		short[] recolorReplace)
	{
		this.npcId = npcId;
		this.npcName = npcName;
		this.modelIds = modelIds;
		this.recolorFind = recolorFind;
		this.recolorReplace = recolorReplace;
	}

	/**
	 * Reads one NPC's appearance.
	 *
	 * @param client the live client. Client thread only.
	 * @param npcId  the NPC id to clone
	 * @param label  the entity this is for, for the log line
	 * @return the appearance, or {@code null} if the id does not resolve, the
	 * composition has no models, or every model id it lists is unusable. Never
	 * throws. Writes nothing — the caller owns the warn, because only it knows
	 * whether this is the first attempt or a retry.
	 */
	@Nullable
	static NpcAppearance resolve(Client client, int npcId, String label)
	{
		if (npcId <= 0)
		{
			// Not reachable through EntityDefinition, which drops a non-positive id
			// at the validation gate. Here so a future caller cannot ask the client
			// about a sentinel.
			return null;
		}

		NPCComposition composition;
		try
		{
			composition = client.getNpcDefinition(npcId);
		}
		catch (RuntimeException e)
		{
			// The ordinary "this id no longer exists" case — see the class javadoc,
			// the client throws rather than returning null for a missing archive
			// entry. Debug, not warn: the caller decides what to say and how often.
			log.debug("{}: npcAppearanceId {} threw on lookup", label, npcId, e);
			return null;
		}

		if (composition == null)
		{
			log.debug("{}: npcAppearanceId {} resolved to no composition", label, npcId);
			return null;
		}

		int[] models = usableModelIds(composition.getModels(), label, npcId);
		if (models.length == 0)
		{
			log.debug("{}: npcAppearanceId {} ('{}') has no usable models",
				label, npcId, composition.getName());
			return null;
		}

		short[] find = composition.getColorToReplace();
		short[] replace = composition.getColorToReplaceWith();
		int pairs = Math.min(find == null ? 0 : find.length, replace == null ? 0 : replace.length);

		short[] keptFind = new short[pairs];
		short[] keptReplace = new short[pairs];
		for (int i = 0; i < pairs; i++)
		{
			keptFind[i] = find[i];
			keptReplace[i] = replace[i];
		}

		return new NpcAppearance(npcId, composition.getName(), models, keptFind, keptReplace);
	}

	/**
	 * Drops a non-positive model id, exactly as {@code EntityDefinition} does for an
	 * authored {@code modelIds} array — and for the same reason: the client would be
	 * asked about it and would answer null, which the caller would read as a cold
	 * cache and retry three times for nothing.
	 *
	 * <p>A composition with <i>some</i> usable models still resolves. That is not a
	 * partial build: what "partial" means in this plugin is a model part that failed
	 * to <i>load</i> ({@link LivelyEntity#loadParts()} refuses those wholesale), and
	 * a {@code -1} in a composition's model array is the client's own "this slot is
	 * empty" rather than a missing part.
	 */
	private static int[] usableModelIds(@Nullable int[] models, String label, int npcId)
	{
		if (models == null || models.length == 0)
		{
			return new int[0];
		}

		int[] kept = new int[models.length];
		int n = 0;
		for (int id : models)
		{
			if (id <= 0)
			{
				log.debug("{}: npcAppearanceId {} lists an empty model slot ({}), skipping it",
					label, npcId, id);
				continue;
			}
			kept[n++] = id;
		}

		if (n == models.length)
		{
			return models.clone();
		}

		int[] trimmed = new int[n];
		System.arraycopy(kept, 0, trimmed, 0, n);
		return trimmed;
	}

	int getNpcId()
	{
		return npcId;
	}

	/** @return the NPC's own name, for a log line. May be {@code null}. */
	@Nullable
	String getNpcName()
	{
		return npcName;
	}

	/**
	 * @return the model parts to build, never empty. The array itself, not a copy —
	 * the same call {@code EntityDefinition.getModelIds()} already makes, and its
	 * only reader iterates it.
	 */
	int[] getModelIds()
	{
		return modelIds;
	}

	/** @return the NPC's {@code getColorToReplace()}, truncated to matched pairs. */
	short[] getRecolorFind()
	{
		return recolorFind;
	}

	/** @return the NPC's {@code getColorToReplaceWith()}, truncated to matched pairs. */
	short[] getRecolorReplace()
	{
		return recolorReplace;
	}

	@Override
	public String toString()
	{
		return "NpcAppearance{" + npcId + " '" + npcName + "', "
			+ modelIds.length + " model(s), " + recolorFind.length + " recolour pair(s)}";
	}
}
