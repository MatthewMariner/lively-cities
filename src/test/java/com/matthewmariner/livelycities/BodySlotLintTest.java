package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * <b>Nobody in this plugin is missing their trousers.</b>
 *
 * <h2>Why this is a rule</h2>
 *
 * <p>A human played the shipped plugin and photographed passers-by with no trousers in
 * Falador and Lumbridge, a gardener whose legs were not there, and figures in Draynor
 * he could not tell apart. Two separate faults produced that, and this test is about
 * the worse of them.
 *
 * <p>The palette fault ({@link CitizenEcho#isFlesh}) only reaches a user who moved the
 * crowd dial to {@code Crowded}, which {@code LivelyCitiesConfig} does not default to.
 * <b>This one renders for every user at every density</b>, because it is in the
 * authored records themselves: 47 of the 98 kit-built human citizens were shipped
 * without legs, without hands, or without anything on their feet. And "no legs" is not
 * an abstract quality problem — it is the exact sentence in the Reddit thread that got
 * the predecessor plugin disabled by the hub, quoted in {@link CitizenLabel}'s own
 * javadoc.
 *
 * <h2>What the repair was</h2>
 *
 * <p><b>One rule with its evidence, and then a tail.</b>
 *
 * <p>The rule is {@link BodySlots#ELDER_CHAOS_HOOD}, model {@code 54275}. It is a hood
 * — {@code -198..-156} in model space, head height — and two NPCs in the whole game
 * wear it. 26 of the 142 shipped citizens carried it, and in 20 of them it sat at the
 * array index where the kit it was copied from carries a trouser or a boot. The
 * clearest case is "Mike", the Lumbridge gardener: his kit is NPC 10758 "Gardener Jay
 * Jr." with <b>exactly one id changed</b> — index 6, {@code 28285} (legs) replaced by
 * {@code 54275}. "Sefton" and "Corliss" carry it twice, at the two positions where legs
 * and boots belong. So the rule is: where the hood is standing in for a body part, put
 * the body part back — {@code 54275 -> 28285} for a missing leg, {@code 54275 -> 185}
 * for a missing boot. That repaired 11 records outright and 9 more with one addition.
 *
 * <p>The tail is 27 records repaired one at a time by adding the part that is missing.
 * 26 of them have no hood in them at all; the twenty-seventh is "Alexander" in Draynor,
 * who carries one and still does — his was not standing in for anything, and what he
 * was short of was a hand. 24 of the 27 had lost only their footwear, and the shape of
 * the loss is consistent: the donor NPC kit carries a boot ({@code 181}, {@code 358},
 * {@code 361}) at an index where the record carries an arm model instead.
 *
 * <p><b>Every repair is additive and uses a part the dataset already shipped</b> —
 * {@link BodySlots#KIT_LEGS}, {@link BodySlots#KIT_HANDS}, {@link BodySlots#KIT_BOOT},
 * {@link BodySlots#KIT_TALL_BOOT} — so the distinct-model-id figure did not move.
 * {@code ModelIdAuditTest} and {@code CacheIdAuditTest} both still pin it at 324, which
 * is the check that matters: that figure is how much of this plugin one cache
 * renumbering can break.
 *
 * <p><b>The {@code npcAppearanceId} route was available and was not taken.</b> Dressing
 * a legless citizen from an NPC composition fixes the legs in one field — it is what
 * was done for "Rufus" when GitHub issue #1 reported him barefoot — but it costs the
 * citizen its ability to seed an echo ({@link CitizenEcho#echoesOfSource} refuses a
 * source whose colours come from a composition) and it moves the model-id count, since
 * the record's own {@code modelIds} stop being read. Additive repair reached all 47
 * without either cost.
 *
 * <h2>Green, not green-with-a-list</h2>
 *
 * <p>There is no accepted-fault list here, deliberately. {@link PlacementExceptions}
 * refuses that shape in as many words — <i>"not a place to quietly launder a confirmed
 * bug back to green"</i> — and the worked example in its javadoc is the Barrows
 * citizens, which were fixed in the data rather than excused. The same choice is made
 * here. The only records this test does not check are the ones the question does not
 * apply to, and each of those is counted below rather than skipped quietly.
 */
public class BodySlotLintTest
{
	/**
	 * <b>The rule.</b> Every citizen built out of a human kit is wearing legs, hands
	 * and something on its feet.
	 */
	@Test
	public void everyKitBuiltHumanCitizenHasLegsHandsAndFeet()
	{
		List<String> naked = new ArrayList<>();
		int checked = 0;

		for (EntityDefinition entity : shipped())
		{
			if (!isKitBuiltHuman(entity))
			{
				continue;
			}

			checked++;

			List<String> missing = missingSlots(entity);
			if (!missing.isEmpty())
			{
				naked.add(entity.label() + " in " + entity.getRegionId() + ".json has no "
					+ String.join(" and no ", missing) + " — " + Arrays.toString(entity.getModelIds()));
			}
		}

		assertTrue("citizen(s) shipped without part of a body: " + naked, naked.isEmpty());

		// The sample guard: the rule is inside the loop, so a predicate that had
		// stopped matching anything would pass this test having asked nothing at all.
		assertEquals("kit-built human citizens the rule was asked about", 98, checked);
	}

	/**
	 * <b>And nobody's trousers are painted the colour the game paints faces.</b>
	 *
	 * <h2>Why this is a second rule and not a clause of the first</h2>
	 *
	 * <p>The rule above proves every kit-built human has leg <i>geometry</i>. It says
	 * nothing about what colour that geometry is, and the two failures look identical in
	 * a screenshot: a citizen with no trouser model and a citizen whose trouser model is
	 * painted skin are both "a man with no trousers". The dataset shipped both, and only
	 * the first was being checked.
	 *
	 * <p>The worked example is <b>"Wynn" in Catherby</b>, authored in the 2026-08-29
	 * top-up. His record is {@code find [8741, 25238, 6798, 43072]} against
	 * {@code replace [8099, 4550, 322, 5532]}: slot 1 aims at {@link BodySlots#LEGS_BASE},
	 * the colour his two {@code 14395} leg models are authored in, and paints it
	 * {@link BodySlots#SKIN_BASE} — <b>the exact value the client substitutes for a
	 * player's face</b>. Not a tan near it; it. And unlike the palette fault in
	 * {@link CitizenEcho}, which only reaches somebody who moved the crowd dial to
	 * {@code Crowded}, this shipped at the default density to every user.
	 *
	 * <h2>Why the rule is about the {@code find} slot and not about geometry</h2>
	 *
	 * <p>A recolour pair is a statement of intent: {@code find = 25238} means "repaint
	 * whatever in this kit is trousers", because {@code 25238} is the colour the game's
	 * own leg models are authored in and nothing else is. So the record answers "what
	 * colour are this citizen's trousers" by itself, with no cache lookup and no
	 * threshold — which is the same property that makes {@link BodySlots} a table of
	 * measured facts rather than a heuristic.
	 *
	 * <p>The rule therefore reads: <b>a slot aimed at one of the four non-skin kit bases
	 * may not be painted the skin base.</b> It is categorical in both directions — there
	 * is no "how close to skin is too close" to argue about.
	 *
	 * <p><b>It used to be silent about every colour that was merely skin-<i>like</i>, and
	 * that silence was wrong.</b> This javadoc argued the silence was deliberate: that the
	 * seventeen shipped records painting the legs base a flesh-gamut colour were mostly
	 * dark leather browns, and that a rule catching them would be "a taste judgement
	 * wearing a lint's clothes". The owner, playing the shipped plugin at {@code FULL} on
	 * 2026-08-31, reported figures that still looked like they had no trousers. The base
	 * is one value in a gamut, and a colour one hue rung and two lightness rungs from it
	 * is functionally the same colour on a leg.
	 *
	 * <p>So this rule keeps its scope — four slots, one exact value — and a second one
	 * next to it takes the legs slot to the whole gamut. See
	 * {@link #noLegsSlotIsPaintedAColourFromTheFleshGamut}, which also says why the legs
	 * slot and not the other three.
	 */
	@Test
	public void noKitGarmentSlotIsPaintedTheColourTheGamePaintsFaces()
	{
		List<String> offenders = new ArrayList<>();
		int slotsAsked = 0;

		for (EntityDefinition entity : shipped())
		{
			short[] find = entity.getRecolorFind();
			short[] replace = entity.getRecolorReplace();

			for (int i = 0; i < find.length; i++)
			{
				if (!BodySlots.NON_SKIN_KIT_BASES.contains(find[i] & 0xFFFF))
				{
					continue;
				}

				slotsAsked++;
				if ((replace[i] & 0xFFFF) == BodySlots.SKIN_BASE)
				{
					offenders.add(entity.label() + " in " + entity.getRegionId()
						+ ".json paints its " + baseName(find[i] & 0xFFFF)
						+ " the player skin base");
				}
			}
		}

		assertTrue("citizen(s) wearing the game's own face colour where a garment goes: "
			+ offenders, offenders.isEmpty());

		// The sample guard: the rule is inside a loop over slots, so a dataset whose
		// records had stopped using the kit bases would pass this having asked nothing.
		assertEquals("kit-base recolour slots the rule was asked about", 195, slotsAsked);
	}

	/**
	 * <b>And no pair of trousers is painted any colour out of the flesh gamut, not just
	 * the one exact value.</b>
	 *
	 * <h2>Why the rule above was not enough</h2>
	 *
	 * <p>The rule above is about {@link BodySlots#SKIN_BASE}, one value. The owner played
	 * the shipped plugin at {@code FULL} on 2026-08-31 — no echoes, only authored records
	 * — and reported figures in Falador and Lumbridge that still looked like they had no
	 * trousers. He was right about the fault, and Falador holds three of the records that
	 * caused it. <b>Lumbridge holds none</b>, and neither do its other three region files:
	 * the nearest is "Marlow" in Draynor. That half of the report is unexplained by this
	 * rule and is written down here rather than rounded off, because "we fixed what he
	 * saw" and "we fixed everything of this kind" are different claims and only the
	 * second one is true. <b>Seventeen records painted the legs base a colour inside
	 * {@link CitizenEcho#isFlesh}'s gamut</b>, and the nearest of them is not a near miss:
	 * "Marlow" wore {@code 5572}, which decodes to hue 5, saturation 3, lightness 68,
	 * against the skin base's hue 4, saturation 3, lightness 70. One hue rung and two
	 * lightness rungs. On a leg that is not a tan resembling skin, it is skin.
	 *
	 * <p>So the rule for this one slot is the gamut rather than the value: <b>a
	 * {@code find} slot aimed at {@link BodySlots#LEGS_BASE} may not be painted a colour
	 * {@link CitizenEcho#isFlesh} calls flesh.</b> All seventeen were repainted on
	 * 2026-08-31 — ten of them upstream's, because an upstream record renders bare legs to
	 * every user at the default density exactly as one of ours does, and {@code NOTICE}
	 * discloses the repaint rather than the plugin shipping a defect it could name.
	 *
	 * <h2>The predicate is the plugin's own, not a second one</h2>
	 *
	 * <p>{@link CitizenEcho#isFlesh} is package-private and this test is in its package,
	 * so the lint and the {@code Crowded} derivation ask the same question of the same
	 * bits. That matters more here than it usually would: the derivation's re-deal rule
	 * ({@link CitizenEcho#keepsEachColourOnItsOwnSideOfTheSkin}) only guarantees that a
	 * deal keeps each colour on its own side of that boundary, so "no echo wears flesh on
	 * its legs" is true <i>because</i> no authored record does. A second copy of the gamut
	 * here could drift from the one the derivation uses and the two guarantees would come
	 * apart in silence. {@code CitizenEchoTest} asserts the derived half.
	 *
	 * <h2>Why the legs slot and not the other three garment slots</h2>
	 *
	 * <p>The same widening was considered for hair, torso and footwear and refused, and
	 * two of the three refusals are not a judgement at all:
	 *
	 * <ul>
	 *   <li><b>Hair and footwear are excluded by the game's own kit.</b> The hair base
	 *       {@code 6798} and the boots base {@code 4626} are <i>themselves</i> inside the
	 *       gamut — {@code CitizenEchoTest} asserts both, and {@link CitizenEcho#isFlesh}
	 *       explains why a dark leather boot really is the hue of a dark complexion. A
	 *       gamut rule on those slots would refuse the colour the game paints those parts
	 *       by default, along with every blond, auburn and tan-leather record in the
	 *       dataset. It would not be a strict rule, it would be an incoherent one.</li>
	 *   <li><b>The torso is coherent and is still refused.</b> The torso base {@code 8741}
	 *       is outside the gamut, so the rule would mean something there. It is not
	 *       adopted because a flesh-toned tunic is not the failure being guarded against:
	 *       the complaint that got this plugin's predecessor pulled is "why does he not
	 *       have any trousers", the legs are the part a missing garment reads as
	 *       nakedness on, and widening to the torso would force fourteen more repaints of
	 *       tan and leather jerkins nobody has photographed. That is a taste judgement,
	 *       and this rule is not one. (It was seventeen when this pass began. Three of
	 *       them moved anyway, as the side effect of keeping an authored record a rotation
	 *       of its donor — see {@code AuthoredRecordsTest}. That is a consequence of the
	 *       legs repaint rather than a decision about torsos, and it is the reason the
	 *       number below is 14.)</li>
	 * </ul>
	 *
	 * <p>The torso, hair and footwear counts are pinned in
	 * {@link #theOtherGarmentSlotsStillCarryTansAndTheCountIsPinned} so that the decision
	 * not to widen cannot quietly become a growing pile of them.
	 */
	@Test
	public void noLegsSlotIsPaintedAColourFromTheFleshGamut()
	{
		List<String> offenders = new ArrayList<>();
		int slotsAsked = 0;

		for (EntityDefinition entity : shipped())
		{
			short[] find = entity.getRecolorFind();
			short[] replace = entity.getRecolorReplace();

			for (int i = 0; i < find.length; i++)
			{
				if ((find[i] & 0xFFFF) != BodySlots.LEGS_BASE)
				{
					continue;
				}

				slotsAsked++;
				if (CitizenEcho.isFlesh(replace[i]))
				{
					int packed = replace[i] & 0xFFFF;
					offenders.add(entity.label() + " in " + entity.getRegionId()
						+ ".json paints its legs " + packed + " — hue " + (packed >>> 10)
						+ ", saturation " + ((packed >>> 7) & 0x07)
						+ ", lightness " + (packed & 0x7F));
				}
			}
		}

		assertTrue("citizen(s) whose trousers are painted a complexion: " + offenders,
			offenders.isEmpty());

		// The sample guard, same as the rule above carries: the check is inside a loop
		// over slots, so a dataset that had stopped aiming at the legs base would pass
		// this having asked nothing at all.
		assertEquals("legs-base recolour slots the rule was asked about", 69, slotsAsked);
	}

	/**
	 * The three garment slots the gamut rule is <b>not</b> applied to, counted rather
	 * than left implicit.
	 *
	 * <p>{@link #noLegsSlotIsPaintedAColourFromTheFleshGamut} says why each is excluded.
	 * This is the guard that stops the exclusion drifting: hair and footwear cannot be
	 * widened to without refusing the game's own base colours, but the torso could be,
	 * and the only thing standing between "seventeen tan jerkins nobody has complained
	 * about" and "the same fault as the trousers, one garment up" is somebody looking. A
	 * new one fails here and a human decides, which is the same shape as
	 * {@link #theSkinBaseOnANonGarmentSlotIsUpstreamsAloneAndIsNamed}.
	 */
	@Test
	public void theOtherGarmentSlotsStillCarryTansAndTheCountIsPinned()
	{
		int torso = 0;
		int hair = 0;
		int footwear = 0;

		for (EntityDefinition entity : shipped())
		{
			short[] find = entity.getRecolorFind();
			short[] replace = entity.getRecolorReplace();

			for (int i = 0; i < find.length; i++)
			{
				if (!CitizenEcho.isFlesh(replace[i]))
				{
					continue;
				}

				int base = find[i] & 0xFFFF;
				if (base == BodySlots.TORSO_BASE)
				{
					torso++;
				}
				else if (base == BodySlots.HAIR_BASE)
				{
					hair++;
				}
				else if (base == BodySlots.BOOT_BASE)
				{
					footwear++;
				}
			}
		}

		assertEquals("torso slots painted a flesh-gamut colour — tan and leather jerkins, "
			+ "left alone deliberately", 14, torso);
		assertEquals("hair slots — blond, auburn and brown, which is what the gamut is "
			+ "made of and why the rule cannot reach here", 12, hair);
		assertEquals("footwear slots — the boots base is itself inside the gamut", 1, footwear);

		// And the legs column of the same table, which is the rule.
		assertTrue("the hair base is inside the gamut, so a gamut rule on the hair slot "
			+ "would refuse the game's own hair colour", CitizenEcho.isFlesh((short) BodySlots.HAIR_BASE));
		assertTrue("and so is the boots base", CitizenEcho.isFlesh((short) BodySlots.BOOT_BASE));
		assertFalse("the torso base is outside it, so the rule would at least be coherent "
			+ "there — it is refused on evidence rather than on incoherence",
			CitizenEcho.isFlesh((short) BodySlots.TORSO_BASE));
		assertFalse("and the legs base is outside it, which is what makes the rule above "
			+ "a statement about the game's kit rather than about taste",
			CitizenEcho.isFlesh((short) BodySlots.LEGS_BASE));
	}

	/**
	 * The skin base <b>does</b> still land on six non-kit-base slots, and every one of
	 * them is upstream's and is right.
	 *
	 * <p>This is the counted-rather-than-skipped companion to the rule above, and it
	 * exists because "we fixed ours" is only an honest sentence if somebody has looked
	 * at the rest. Twelve shipped records paint {@link BodySlots#SKIN_BASE} onto a slot
	 * aimed at something other than skin. Six were this project's and were repaletted on
	 * 2026-08-30. <b>The other six were left alone, and not out of deference:</b> a
	 * read-only decode of the models each of those slots actually repaints shows they
	 * are painting skin onto skin.
	 *
	 * <ul>
	 *   <li>{@code 43072} is the base colour the kit's <b>arm and hand</b> models carry
	 *       on their cuffs — models {@code 176} and {@code 353} are a hand in
	 *       {@code 4550} below and a cuff in {@code 43072} above, and {@code 159} and
	 *       {@code 27139} are the same arrangement on a forearm. Painting it the skin
	 *       base extends the skin up the wrist, which is a rolled sleeve, not a fault.
	 *       Five of the six do this: "Forester" and "Nicholson", "Assistant Apothecary",
	 *       "Mary" and "Silver merchant".</li>
	 *   <li>{@code 27548} is the only colour on model {@code 405}, a close-fitting head
	 *       covering. "Thalindra" paints it the skin base, which reads as a bald head.
	 *       It is a look rather than a mistake, and it is not ours to restyle.</li>
	 * </ul>
	 *
	 * <p>The point of the count is that it cannot grow in silence. A seventh, or one of
	 * these six moving onto a garment base, fails here or in the rule above.
	 */
	@Test
	public void theSkinBaseOnANonGarmentSlotIsUpstreamsAloneAndIsNamed()
	{
		List<String> ours = new ArrayList<>();
		TreeSet<String> upstream = new TreeSet<>();

		for (EntityDefinition entity : shipped())
		{
			short[] find = entity.getRecolorFind();
			short[] replace = entity.getRecolorReplace();

			for (int i = 0; i < find.length; i++)
			{
				if ((replace[i] & 0xFFFF) != BodySlots.SKIN_BASE
					|| (find[i] & 0xFFFF) == BodySlots.SKIN_BASE)
				{
					continue;
				}

				if (entity.getUuid().toString().startsWith("add1"))
				{
					ours.add(entity.label() + " slot " + i + " find=" + (find[i] & 0xFFFF));
				}
				else
				{
					upstream.add(entity.getName());
				}
			}
		}

		assertTrue("records this project authored still painting the skin base onto a "
			+ "slot aimed at something else: " + ours, ours.isEmpty());
		assertEquals("upstream records that do, each of them painting an arm, a hand or "
				+ "a head — see this test's javadoc",
			new TreeSet<>(Arrays.asList("Assistant Apothecary", "Forester", "Mary",
				"Nicholson", "Silver merchant", "Thalindra")),
			upstream);
	}

	/**
	 * <b>The repair made two citizens identical, and that is the answer rather than an
	 * oversight.</b>
	 *
	 * <p>"Mike" in Lumbridge and the "Gardener" in Varrock now carry the same
	 * {@code modelIds}, the same {@code modelRecolorFind} and the same
	 * {@code modelRecolorReplace}. They did not before: Mike's index 6 held
	 * {@link BodySlots#ELDER_CHAOS_HOOD} where the Gardener's holds
	 * {@link BodySlots#KIT_LEGS}, and putting the legs back is what closed the gap.
	 *
	 * <p>Three reasons it is left:
	 *
	 * <ul>
	 *   <li><b>The difference was the bug.</b> Both are upstream's, both are gardeners,
	 *       both carry the watering can {@code 7368} and a {@code gardenerScript}. Mike
	 *       was authored as the same figure with one id mis-pasted. Restoring the leg
	 *       restored him to the gardener he was written as; keeping him different would
	 *       have meant preserving the paste.</li>
	 *   <li><b>Nobody can see both.</b> A twin is a problem because two identical people
	 *       standing near each other read as a rendering fault — which is what
	 *       {@link CitizenEcho#MIN_SEPARATION_TILES} is for, and what the "figures in
	 *       Draynor he could not tell apart" photograph was. These two are in different
	 *       cities, governed by different checkboxes, hundreds of tiles apart.</li>
	 *   <li><b>Choosing them a different trouser would be authoring.</b> The repair's
	 *       whole discipline is that it puts back the part the donor kit carries, and
	 *       the donor kit carries {@code 28285}. Picking a different leg to avoid a
	 *       resemblance nobody can observe would be the first hand-authored appearance
	 *       decision in the pass.</li>
	 * </ul>
	 *
	 * <p>What this test pins is that the resemblance stays where it is harmless. It does
	 * <b>not</b> claim the dataset has no other identical pairs — it has several,
	 * upstream's, including two in one region file — only that this one, which this
	 * project created, is two cities apart.
	 */
	@Test
	public void theOneTwinTheRepairCreatedIsTwoCitiesApart()
	{
		EntityDefinition mike = null;
		EntityDefinition gardener = null;
		for (EntityDefinition entity : shipped())
		{
			if ("Mike".equals(entity.getName()))
			{
				mike = entity;
			}
			else if ("Gardener".equals(entity.getName()))
			{
				gardener = entity;
			}
		}

		assertTrue("both gardeners have to still be in the dataset",
			mike != null && gardener != null);
		assertTrue("the repair made them pixel-identical, which is the fact being pinned",
			Arrays.equals(mike.getModelIds(), gardener.getModelIds())
				&& Arrays.equals(mike.getRecolorFind(), gardener.getRecolorFind())
				&& Arrays.equals(mike.getRecolorReplace(), gardener.getRecolorReplace()));
		assertTrue("and the leg that closed the gap is the donor kit's own",
			contains(mike.getModelIds(), BodySlots.KIT_LEGS));

		City mikeCity = City.of(mike.getCityRegionId());
		City gardenerCity = City.of(gardener.getCityRegionId());
		assertFalse("a twin only matters where both halves can be seen at once, so these "
				+ "two have to stay in different cities: " + mikeCity + " and " + gardenerCity,
			mikeCity == null || mikeCity == gardenerCity);
	}

	/**
	 * The kit bases this table names are the ones the plugin itself recolours against.
	 *
	 * <p>{@link BodySlots#SKIN_BASE} and {@link CitizenEcho#PLAYER_SKIN_BASE} are the
	 * same fact written down in two places — a test table and shipped code — and the
	 * two would otherwise be free to drift. The rule above and the derivation's re-deal
	 * rule would then be about different colours while reading as though they were about
	 * the same one, which is the worst kind of disagreement: silent and plausible.
	 */
	@Test
	public void theKitBaseColoursAreTheOnesThePluginItselfUses()
	{
		assertEquals("the lint and the derivation have to mean the same colour",
			CitizenEcho.PLAYER_SKIN_BASE, BodySlots.SKIN_BASE);
		assertFalse("the skin base is not a garment base",
			BodySlots.NON_SKIN_KIT_BASES.contains(BodySlots.SKIN_BASE));
		assertEquals("hair, torso, legs and boots", 4, BodySlots.NON_SKIN_KIT_BASES.size());
	}

	private static String baseName(int base)
	{
		if (base == BodySlots.HAIR_BASE)
		{
			return "hair";
		}
		if (base == BodySlots.TORSO_BASE)
		{
			return "torso";
		}
		if (base == BodySlots.LEGS_BASE)
		{
			return "legs";
		}
		return "footwear";
	}

	/**
	 * Who the rule is <b>not</b> asked about, counted rather than skipped quietly.
	 *
	 * <p>Every exclusion below is a case where "which of your models is the boot" has
	 * no answer, rather than a case where the answer is inconvenient. The five numbers
	 * have to add up to the whole shipped roster, which is what stops an exclusion
	 * quietly widening to cover a real fault.
	 */
	@Test
	public void everyRecordTheRuleSkipsIsSkippedForAStatedReason()
	{
		int scenery = 0;
		int dressedFromAnNpc = 0;
		int notOnTheHumanRig = 0;
		int wholeFigure = 0;
		int checked = 0;

		for (EntityDefinition entity : shipped())
		{
			if (!entity.getType().isCitizen())
			{
				// A market stall has no legs and is not missing any.
				scenery++;
			}
			else if (entity.getNpcAppearanceId() != 0)
			{
				// Its body comes from a composition at spawn time, so its modelIds are
				// not what renders — LivelyEntity.loadParts sources the parts from the
				// NPC instead. There is no kit here to be missing a part of.
				dressedFromAnNpc++;
			}
			else if (!ModelSkeletons.impliedRig(entity).contains(0))
			{
				// A cat, a rat, a penguin, a swan, a dwarf, a goblin, a gnome child.
				// The three slots are measured off the human skeleton and mean nothing
				// on any other one.
				notOnTheHumanRig++;
			}
			else if (isWholeFigure(entity))
			{
				// One mesh from crown to floor — an entire NPC rather than a kit. See
				// BodySlots.WHOLE_FIGURE.
				wholeFigure++;
			}
			else
			{
				checked++;
			}
		}

		assertEquals("scenery", 42, scenery);
		assertEquals("citizens dressed from an NPC composition", 7, dressedFromAnNpc);
		assertEquals("citizens that are not on the human skeleton", 32, notOnTheHumanRig);
		assertEquals("citizens that are one whole-body mesh rather than a kit", 5, wholeFigure);
		assertEquals("leaving the citizens the rule is asked about", 98, checked);
		assertEquals("and the five have to be the whole shipped dataset",
			184, scenery + dressedFromAnNpc + notOnTheHumanRig + wholeFigure + checked);
	}

	/**
	 * <b>The one rule that did most of the repair, and the evidence for it.</b>
	 *
	 * <p>Model {@code 54275} is not a leg and never was. What this pins is that it is
	 * no longer <i>doing a leg's job</i> anywhere: the six citizens that still carry it
	 * all have real legs, hands and boots of their own, so it is a spare hood on a head
	 * rather than the only thing between a citizen and a missing slot.
	 *
	 * <p>It is deliberately <b>not</b> removed from those six. Every occurrence in the
	 * dataset is the same paste, so deleting them all would be the tidier change — and
	 * it would take {@code 54275} out of the dataset altogether and drop the
	 * distinct-model-id figure from 324 to 323, which is pinned twice and is not this
	 * pass's to move. A cosmetic hood is a smaller problem than a moved invariant, and
	 * it is written down here rather than left for somebody to rediscover.
	 */
	@Test
	public void theElderChaosHoodIsNoLongerStandingInForALegOrABoot()
	{
		assertFalse("the hood is head geometry, not a body slot",
			BodySlots.LEGS.contains(BodySlots.ELDER_CHAOS_HOOD)
				|| BodySlots.FEET.contains(BodySlots.ELDER_CHAOS_HOOD)
				|| BodySlots.HANDS.contains(BodySlots.ELDER_CHAOS_HOOD));
		assertTrue(BodySlots.NO_SLOT.contains(BodySlots.ELDER_CHAOS_HOOD));

		List<String> carriers = new ArrayList<>();
		for (EntityDefinition entity : shipped())
		{
			if (!contains(entity.getModelIds(), BodySlots.ELDER_CHAOS_HOOD))
			{
				continue;
			}

			carriers.add(entity.label());
			assertTrue(entity.label() + " still needs the hood to look dressed",
				missingSlots(entity).isEmpty());
		}

		assertEquals("records still carrying the hood, down from 26: " + carriers,
			6, carriers.size());
	}

	/**
	 * The table covers the dataset exactly, and the six sets partition it.
	 *
	 * <p>This is the guard that makes the rule above unable to go green by accident. A
	 * model id with no row in {@link BodySlots} would otherwise count as "not legs" in
	 * silence, so a new record could ship a leg the table has never heard of and the
	 * lint would report it as legless — or, worse, an id could be quietly moved into
	 * {@link BodySlots#LEGS} to make a fault go away and nothing would notice.
	 */
	@Test
	public void everyShippedModelIdIsClassifiedAndTheSetsPartitionTheDataset()
	{
		Set<Integer> shippedIds = new TreeSet<>();
		for (EntityDefinition entity : shipped())
		{
			for (int modelId : entity.getModelIds())
			{
				shippedIds.add(modelId);
			}
		}

		assertEquals("the distinct-model-id figure this table is written against",
			324, shippedIds.size());
		assertEquals("every shipped model id has a row and the table has no row for "
				+ "anything else", shippedIds, BodySlots.classified());

		// The four kinds are mutually exclusive: a prop is not a body part, a whole
		// figure is not a kit part, and a body part either fills a slot or does not.
		Set<Integer> fillsASlot = new LinkedHashSet<>(BodySlots.LEGS);
		fillsASlot.addAll(BodySlots.FEET);
		fillsASlot.addAll(BodySlots.HANDS);

		assertEquals("model ids that fill at least one slot", 143, fillsASlot.size());
		assertEquals("body geometry that fills none of the three", 143, BodySlots.NO_SLOT.size());
		assertEquals("held props", 27, BodySlots.HELD_PROP.size());
		assertEquals("whole-body meshes", 11, BodySlots.WHOLE_FIGURE.size());
		assertEquals("and the four kinds have to be the 324",
			324, fillsASlot.size() + BodySlots.NO_SLOT.size()
				+ BodySlots.HELD_PROP.size() + BodySlots.WHOLE_FIGURE.size());

		assertTrue("a model cannot both fill a slot and fill none",
			disjoint(fillsASlot, BodySlots.NO_SLOT));
		assertTrue("a held prop is not body geometry",
			disjoint(BodySlots.HELD_PROP, fillsASlot) && disjoint(BodySlots.HELD_PROP, BodySlots.NO_SLOT));
		assertTrue("a whole figure is not a kit part",
			disjoint(BodySlots.WHOLE_FIGURE, fillsASlot)
				&& disjoint(BodySlots.WHOLE_FIGURE, BodySlots.NO_SLOT)
				&& disjoint(BodySlots.WHOLE_FIGURE, BodySlots.HELD_PROP));
	}

	/**
	 * <b>Held props are not body geometry</b>, stated as its own rule because getting
	 * it wrong is what made the first attempt at this repair measure the wrong thing.
	 *
	 * <p>"Mike" carries a watering can that sits in the leg band. If it counted, he
	 * would have a small tidy gap instead of no legs, and the fix would have been
	 * argued about in units rather than in slots.
	 */
	@Test
	public void aHeldPropNeverCountsAsABodyPart()
	{
		int wateringCan = 7368;
		assertTrue("the watering can is a prop", BodySlots.HELD_PROP.contains(wateringCan));

		EntityDefinition mike = null;
		for (EntityDefinition entity : shipped())
		{
			if ("Mike".equals(entity.getName()))
			{
				mike = entity;
			}
		}

		assertTrue("Mike has to still be in Lumbridge carrying his can",
			mike != null && contains(mike.getModelIds(), wateringCan));
		assertTrue("and he has to have legs now", missingSlots(mike).isEmpty());
		assertTrue("which came from the hood he was wearing on his shin",
			contains(mike.getModelIds(), BodySlots.KIT_LEGS));
		assertFalse("the hood is gone from his kit",
			contains(mike.getModelIds(), BodySlots.ELDER_CHAOS_HOOD));
	}

	// --- the rule, in one place -----------------------------------------------

	/**
	 * @return the slots this record's kit does not fill, in head-to-toe order. Empty
	 * for a citizen the rule has nothing against.
	 */
	private static List<String> missingSlots(EntityDefinition entity)
	{
		List<String> missing = new ArrayList<>();
		if (!fills(entity, BodySlots.LEGS))
		{
			missing.add("legs");
		}
		if (!fills(entity, BodySlots.HANDS))
		{
			missing.add("hands");
		}
		if (!fills(entity, BodySlots.FEET))
		{
			missing.add("footwear");
		}
		return missing;
	}

	private static boolean fills(EntityDefinition entity, Set<Integer> slot)
	{
		for (int modelId : entity.getModelIds())
		{
			if (slot.contains(modelId))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isKitBuiltHuman(EntityDefinition entity)
	{
		return entity.getType().isCitizen()
			&& entity.getNpcAppearanceId() == 0
			&& ModelSkeletons.impliedRig(entity).contains(0)
			&& !isWholeFigure(entity);
	}

	private static boolean isWholeFigure(EntityDefinition entity)
	{
		for (int modelId : entity.getModelIds())
		{
			if (BodySlots.WHOLE_FIGURE.contains(modelId))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean contains(int[] modelIds, int wanted)
	{
		for (int modelId : modelIds)
		{
			if (modelId == wanted)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean disjoint(Set<Integer> a, Set<Integer> b)
	{
		for (Integer value : a)
		{
			if (b.contains(value))
			{
				return false;
			}
		}
		return true;
	}

	/** Every shipped entity, through the real validation gate. */
	private static List<EntityDefinition> shipped()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<EntityDefinition> out = new ArrayList<>();
		for (int regionId : ShippedRegions.ids())
		{
			out.addAll(loader.loadRegion(regionId).getEntities());
		}

		assertEquals("the whole shipped roster", 184, out.size());
		return out;
	}
}
