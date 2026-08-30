package com.matthewmariner.livelycities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which records in the vendored dataset are ours, what they are made of, and the fact
 * that {@code NOTICE} says both correctly.
 *
 * <h2>Why this is a test and not a paragraph</h2>
 *
 * <p>{@code src/main/resources/RegionData/} is somebody else's work, redistributed
 * under BSD-2. Adding records to it is a modification, and the licence's whole point
 * is that a reader can tell what was changed. {@code NOTICE} item 8 makes claims a
 * reader has no way to check for themselves: that exactly thirty-three records were
 * authored by this project in the 2026-08-29 top-up, that they are the ones whose
 * {@code uuid} begins {@value #MARKER}, and that every one of them wears a
 * {@code modelIds} array copied whole out of a record already in the dataset with
 * that record's palette re-dealt.
 *
 * <p>All of those rot the moment somebody adds a thirty-fourth citizen, and none of
 * them would fail anything. So this reads the table out of {@code NOTICE} itself and
 * checks it against the shipped files: the region ids, the counts, the names, and —
 * record by record — the kit. A disclosure that has drifted from the data is a red
 * build rather than a quiet inaccuracy in a licence file.
 *
 * <h2>Why the uuid and not a field</h2>
 *
 * <p>The cameos are marked with {@code "cameo": true}, but that flag means "opt-in
 * content behind a checkbox" — {@code EntityScene} and {@link CitizenEcho} both read
 * it — so putting it on an ordinary townsperson who is on by default would be a lie
 * with behaviour attached. A new field would change the wire format for the sake of
 * bookkeeping. The uuid is already required, already unique, and is the one thing
 * about a record that never changes ({@code EntityDefinition.stableHash}), so a
 * reserved prefix costs nothing and is greppable from outside the build.
 *
 * <h2>Both rosters, not just the citizen one</h2>
 *
 * <p>This class reads the region files itself rather than going through
 * {@link ShippedCitizens}, which is deliberately citizens-only (see its javadoc).
 * Every count here is a claim about <i>records this project authored</i>, and a
 * marked record parked in {@code sceneryRoster} would be invisible to a
 * citizens-only reader — including to the "exactly thirty-three" count that is the
 * headline of the disclosure. It is read from both rosters and the scenery half is
 * then asserted empty, so "there are none" is a measured fact rather than a
 * consequence of not having looked.
 *
 * <h2>Requires a fresh test run</h2>
 *
 * <p>{@code NOTICE} is declared an input of the {@code test} task in
 * {@code build.gradle} for this class's sake. Without that declaration, editing
 * {@code NOTICE} and running {@code ./gradlew test} reports nothing and exits 0 —
 * the task is up to date, because none of its inputs appeared to change, and the
 * disclosure check never runs. If that declaration is ever removed, this class is
 * only honest under {@code ./gradlew clean test} or {@code --rerun-tasks}.
 */
public class AuthoredRecordsTest
{
	/** The uuid prefix every record this project authored in the top-up carries. */
	private static final String MARKER = "add1";

	/** What {@code NOTICE} item 8 claims, and what the region files have to show. */
	private static final int AUTHORED = 33;

	/**
	 * The disclosure's own table, parsed back out of {@code NOTICE}.
	 *
	 * <p>Lines look like {@code 10290   2   Ardougne monastery      Anselm, Brother
	 * Edwy} — region id, count, a place label, then the names. The label is prose and
	 * is not checked; the other three are. Every row is one line, which is why the
	 * Draynor row runs long rather than wrapping: a continuation line is one more thing
	 * for this pattern to get subtly wrong, and getting it wrong here means silently
	 * checking twelve rows instead of thirteen.
	 */
	private static final Pattern ROW = Pattern.compile(
		"^ {7}(\\d{5}) {3}(\\d+) {3}\\S.*?(?: {2,})([A-Z][^\\n]*)$",
		Pattern.MULTILINE);

	@Test
	public void everyRecordThisProjectAuthoredIsIdentifiableFromTheDataAlone()
	{
		TreeMap<Integer, List<String>> ours = ourRecordsByRegion();

		int authored = 0;
		for (List<String> names : ours.values())
		{
			authored += names.size();
		}

		assertEquals("records carrying the '" + MARKER + "' uuid marker", AUTHORED, authored);
		assertEquals("region files they were added to", 13, ours.size());

		int upstream = 0;
		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (!citizen.uuid.startsWith(MARKER))
			{
				upstream++;
			}
		}

		// The other half of the claim, and the half that would rot silently: the
		// marker is only a marker if nothing else wears it. 109 is the roster as it
		// stood before the top-up, cameos included.
		assertEquals("citizens that predate the top-up and must not carry the marker",
			109, upstream);
		assertEquals("and the two halves are the whole roster",
			142, authored + upstream);
	}

	/**
	 * Nothing this project authored hides in {@code sceneryRoster}.
	 *
	 * <p>Not a hypothetical tidiness rule. Every other assertion in this class, and
	 * the {@code grep} {@code NOTICE} hands the reader, is about citizens; a marked
	 * record in the other roster would be authored content this project ships and
	 * discloses nowhere, and it would leave "exactly thirty-three" true of the
	 * citizen roster while being false of the dataset. It would also be silent
	 * behaviourally — scenery has no name, no examine text and no remarks, and
	 * {@link CitizenEcho} refuses to derive from it — so nothing else would notice.
	 */
	@Test
	public void everyAuthoredRecordIsACitizenAndNotAPieceOfScenery()
	{
		List<String> inTheWrongRoster = new ArrayList<>();
		int marked = 0;

		for (Record record : shippedRecords())
		{
			if (!record.isOurs())
			{
				continue;
			}

			marked++;
			if (!"citizenRoster".equals(record.rosterKey))
			{
				inTheWrongRoster.add(record.describe());
			}
		}

		assertTrue("records this project authored that ship in sceneryRoster: "
			+ inTheWrongRoster, inTheWrongRoster.isEmpty());

		// Counted over both rosters, so a record moved from one to the other changes
		// this number even though it changes neither roster's own total by itself.
		assertEquals("marked records across the whole dataset, both rosters",
			AUTHORED, marked);
	}

	/**
	 * An authored townsperson must not be flagged as a cameo.
	 *
	 * <p>{@code "cameo": true} is a behavioural flag — it gates a record behind the
	 * {@code cameos} checkbox and stops {@link CitizenEcho} deriving from it — and
	 * {@code NOTICE} tells a reader it is what identifies the six likenesses. Putting
	 * it on one of these thirty-three would both hide an ordinary citizen behind a
	 * checkbox nobody expects and make that sentence in the licence file wrong.
	 */
	@Test
	public void noAuthoredTownspersonIsFlaggedAsACameoOrDressedFromAnNpc()
	{
		List<String> offenders = new ArrayList<>();

		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (!citizen.uuid.startsWith(MARKER))
			{
				continue;
			}

			if (citizen.cameo)
			{
				offenders.add(citizen.name + " is flagged as a cameo");
			}

			// Not a rule about what is possible, but about what this pass did: every
			// one of the thirty-three wears raw modelIds copied from a shipped record,
			// which is what NOTICE item 8 claims and what keeps the distinct-model-id
			// figure at 324.
			if (citizen.npcAppearanceId != 0)
			{
				offenders.add(citizen.name + " is dressed from NPC "
					+ citizen.npcAppearanceId + " rather than from copied modelIds");
			}
		}

		assertTrue("authored townsfolk must be ordinary citizens: " + offenders,
			offenders.isEmpty());
	}

	/**
	 * <b>The rule the whole top-up rests on, enforced rather than trusted.</b>
	 *
	 * <p>{@code NOTICE} item 8 says it in one sentence: every one of the thirty-three
	 * reuses a {@code modelIds} array copied whole out of a record already in this
	 * dataset, with that record's own {@code modelRecolorFind} kept as-is and its
	 * {@code modelRecolorReplace} rotated so the copy is not simply its donor standing
	 * somewhere else. That is what keeps the distinct-model-id figure at 324 — the
	 * number that decides how much of this plugin a cache renumbering can break — and
	 * it is what stops the dataset acquiring twins.
	 *
	 * <p>Until this test it was held by nothing but the discipline of whoever wrote
	 * the records. A review on 2026-08-29 made three edits to prove it, and <b>all
	 * three survived the whole suite</b>:
	 *
	 * <ol>
	 *   <li>setting one new record's {@code modelRecolorReplace} to its donor's exact
	 *       array, making it a pixel-for-pixel twin of a shipped citizen;</li>
	 *   <li>reversing one new record's {@code modelIds} order — a hand-assembled kit
	 *       that appears nowhere in the dataset, with the distinct-id count unmoved
	 *       because reversing an array changes no member of it;</li>
	 *   <li>swapping one model id for another already in the dataset, producing a
	 *       combination that exists in no record and is therefore a body nobody has
	 *       ever seen rendered.</li>
	 * </ol>
	 *
	 * <p>The three assertions below are one per mutation, in that order.
	 *
	 * <p><b>Why "a rotation" and not "some other palette".</b> The re-deal is not a
	 * loose "make it look different" — it is {@link CitizenEcho#redeal}, the exact
	 * operation the {@code Crowded} derivation uses on the same arrays, and the
	 * rotation has to be one {@link CitizenEcho#distinctDeals} would offer, which is
	 * how a rotation that comes out as the identity (a palette of one repeated colour)
	 * is excluded. Stating the rule in the code's own vocabulary rather than
	 * re-implementing it means this test cannot drift from what the plugin does, and
	 * it means the authored records and the derived ones are provably the same kind of
	 * thing: an existing citizen's wardrobe, dealt round.
	 */
	@Test
	public void everyAuthoredKitIsAWholeCopyOfAShippedKitWithThePaletteReDealt()
	{
		List<Record> all = shippedRecords();

		List<String> noDonor = new ArrayList<>();
		List<String> notReDealt = new ArrayList<>();
		List<String> donorIsNotACitizen = new ArrayList<>();
		TreeSet<String> repaletted = new TreeSet<>();
		TreeSet<String> legsLifted = new TreeSet<>();
		int checked = 0;

		for (Record ours : all)
		{
			if (!ours.isOurs())
			{
				continue;
			}

			checked++;

			List<Record> donors = new ArrayList<>();
			for (Record candidate : all)
			{
				if (candidate.isOurs())
				{
					// A record from this pass is not evidence that a kit predates it.
					continue;
				}

				if (Arrays.equals(candidate.modelIds, ours.modelIds)
					&& Arrays.equals(candidate.recolorFind, ours.recolorFind))
				{
					donors.add(candidate);
				}
			}

			if (donors.isEmpty())
			{
				noDonor.add(ours.describe() + " wears modelIds/modelRecolorFind that appear "
					+ "in no record predating the top-up: " + Arrays.toString(ours.modelIds));
				continue;
			}

			Record reDealtFrom = null;
			Lift lifted = null;
			for (Record donor : donors)
			{
				Lift slots = reDealOf(
					ours.recolorReplace, donor.recolorReplace, ours.recolorFind);
				if (slots != null)
				{
					reDealtFrom = donor;
					lifted = slots;
					if (slots.total() == 0)
					{
						break;
					}
				}
			}

			if (lifted != null && lifted.face > 0)
			{
				repaletted.add(ours.name);
			}
			if (lifted != null && lifted.legs > 0)
			{
				legsLifted.add(ours.name);
			}

			if (reDealtFrom == null)
			{
				List<String> tried = new ArrayList<>();
				for (Record donor : donors)
				{
					tried.add(donor.describe() + " "
						+ Arrays.toString(shorts(donor.recolorReplace)));
				}
				notReDealt.add(ours.describe() + " carries "
					+ Arrays.toString(shorts(ours.recolorReplace))
					+ ", which is not a distinct re-deal of any donor's palette: " + tried);
				continue;
			}

			// True of every donor this pass actually used, and worth pinning: scenery
			// carries no name, no examine text and no remarks, so a citizen wearing a
			// market stall's kit would be a body the dataset has no other example of.
			if (!"citizenRoster".equals(reDealtFrom.rosterKey))
			{
				donorIsNotACitizen.add(ours.describe() + " copies " + reDealtFrom.describe());
			}
		}

		assertTrue("authored kits that are not a whole copy of an existing kit — a "
			+ "hand-assembled modelIds array is a body nobody has seen rendered, and a "
			+ "new combination is new cache-breakage surface: " + noDonor, noDonor.isEmpty());
		assertTrue("authored kits whose palette is their donor's, not a re-deal of it — "
			+ "which is a pixel-for-pixel twin of a shipped citizen: " + notReDealt,
			notReDealt.isEmpty());
		assertTrue("authored kits copied from scenery: " + donorIsNotACitizen,
			donorIsNotACitizen.isEmpty());

		// The exception is bounded and named, because an unbounded one would let the
		// rule above be satisfied by any palette at all: "a re-deal with some slots
		// changed" is not a claim. These are the six the 2026-08-30 repalette lifted the
		// face colour off; every other authored record is a rotation and nothing else.
		assertEquals("authored records whose donor's palette had to be repaletted "
				+ "because no rotation of it could hold the face colour: " + repaletted,
			new TreeSet<>(Arrays.asList(
				"Aldous", "Brother Edwy", "Hesper", "Marta", "Sela", "Wynn")),
			repaletted);

		// The second exception, added 2026-08-31 and bounded the same way: one slot, the
		// legs slot, moved off a colour BodySlotLintTest now refuses there. See
		// everyLegsLiftIsOneSlotMovedOffTheFleshGamutAndNothingElse for its shape and for
		// why a whole-wardrobe re-rotation was not taken instead.
		assertEquals("authored records whose legs slot had to be lifted off the flesh "
				+ "gamut the rotation dealt onto it: " + legsLifted,
			new TreeSet<>(Arrays.asList(
				"Berta", "Brother Edwy", "Marlow", "Nessa", "Osric", "Tarik", "Tobias")),
			legsLifted);

		// The sample guard this file's siblings all carry: the three rules above are
		// inside the loop, so a scan that found no marked records would pass having
		// asked nothing at all.
		assertEquals("every authored record has to have been put through the rule",
			AUTHORED, checked);
	}

	/**
	 * <b>The six records a rotation could not produce, and why no rotation could.</b>
	 *
	 * <p>The rule above allows one exception, and this test is the evidence for it: for
	 * each of the six, <i>every</i> rotation of the donor's palette lands
	 * {@link CitizenEcho#PLAYER_SKIN_BASE} — the colour the client paints a player's
	 * face — on a {@code find} slot the donor did not aim at skin. That is not a
	 * property of which rotation was picked; it is a property of the donor. Each of
	 * these six donors carries the face colour on a slot aimed at an arm, a hand or a
	 * head, and <b>none of them has a {@code find = 4550} slot at all</b>, so there is
	 * nowhere in the array for the face colour to stay. Rotating such a palette moves
	 * the face somewhere by construction.
	 *
	 * <p>Asserting this is what stops the exception being a licence to hand-author a
	 * palette: if somebody later repalettes a record whose donor <i>could</i> have been
	 * rotated safely, this goes red.
	 */
	@Test
	public void noRotationOfThoseSixDonorsCouldHaveHeldTheFaceColourInPlace()
	{
		List<Record> all = shippedRecords();
		int checked = 0;

		for (Record ours : all)
		{
			if (!ours.isOurs())
			{
				continue;
			}

			for (Record donor : all)
			{
				if (donor.isOurs()
					|| !Arrays.equals(donor.modelIds, ours.modelIds)
					|| !Arrays.equals(donor.recolorFind, ours.recolorFind))
				{
					continue;
				}

				Lift lift = reDealOf(ours.recolorReplace, donor.recolorReplace,
					ours.recolorFind);
				if (lift == null || lift.face == 0)
				{
					// A legs lift is a different exception with a different justification
					// — see everyLegsLiftIsOneSlotMovedOffTheFleshGamutAndNothingElse. It
					// is deliberately not claimed to be forced, so it must not be counted
					// here: doing so would make this test assert an impossibility that is
					// not true and quietly widen what "forced" means.
					continue;
				}

				checked++;
				short[] theirs = shorts(donor.recolorReplace);
				for (int deal = 1; deal < theirs.length; deal++)
				{
					short[] dealt = CitizenEcho.redeal(theirs, deal);
					if (Arrays.equals(dealt, theirs))
					{
						continue;
					}

					assertTrue(ours.describe() + " could have been rotation " + deal
							+ " of " + donor.describe() + " without touching a colour, so "
							+ "repaletting it was not forced: " + Arrays.toString(dealt),
						landsTheFaceColourOnANonSkinSlot(dealt, ours.recolorFind));
				}
			}
		}

		assertEquals("repaletted records the impossibility was proved for", 6, checked);
	}

	/**
	 * <b>The seven legs lifts, and the honest reason each one is a lift rather than a
	 * different rotation.</b>
	 *
	 * <p>{@link BodySlotLintTest#noLegsSlotIsPaintedAColourFromTheFleshGamut} refuses any
	 * legs slot painted a colour inside {@link CitizenEcho#isFlesh}'s gamut. Seven of the
	 * thirty-three authored records inherited such a colour from their donor's rotation,
	 * so seven had to move — and the question this test answers is <b>what "move" was
	 * allowed to mean</b>.
	 *
	 * <h2>What a lift is allowed to be</h2>
	 *
	 * <p>Exactly one slot, it has to be the {@link BodySlots#LEGS_BASE} slot, the colour
	 * the rotation would have dealt there has to be inside the gamut, and the colour put
	 * there has to be outside it. Nothing else about the palette may move. That is
	 * asserted below record by record, which is what stops "a re-deal with a slot lifted"
	 * becoming a licence to hand-author a wardrobe.
	 *
	 * <h2>Why not simply pick a rotation that needed no lift</h2>
	 *
	 * <p>{@link #noRotationOfThoseSixDonorsCouldHaveHeldTheFaceColourInPlace} makes
	 * exactly that demand of the face-colour exception, and it is a fair demand to make
	 * of this one too. The answer is that it was available six times out of seven and was
	 * taken none of them, for two different reasons, both counted below rather than
	 * asserted away:
	 *
	 * <ul>
	 *   <li><b>"Brother Edwy" had no clean rotation at all.</b> His donor, the "Assistant
	 *       Apothecary", carries {@link CitizenEcho#PLAYER_SKIN_BASE} on its cuff slot and
	 *       has no {@code find = 4550} slot to hold it, so every rotation moves the face
	 *       somewhere it may not go — and four of the five also deal a tan onto the legs.
	 *       He is forced twice over, which is why he is the one record in both sets.</li>
	 *   <li><b>"Tobias", "Marlow" and "Tarik" had one clean rotation each and it was
	 *       already taken.</b> The gardener palette admits exactly one, and Tobias and
	 *       Marlow are both built from it, so giving it to both would make them
	 *       pixel-identical. The Ali palette likewise admits exactly one, and "Fahd" is
	 *       already wearing it — in Al Kharid, the same city as Tarik, which is a twin
	 *       standing where somebody can see both halves. Trading a bare leg for a
	 *       duplicate is not a repair.</li>
	 *   <li><b>"Osric", "Berta" and "Nessa" had a clean rotation and it was not taken.</b>
	 *       No twin would have resulted. The reason is scope and nothing grander: a
	 *       rotation repaints every slot in the record — the face, the hair, the tunic —
	 *       to fix the trousers, and these three ship today with faces and hair somebody
	 *       looked at. A lift changes the one slot that is wrong. This is the pass's
	 *       judgement rather than a rule, so it is written down as one.</li>
	 * </ul>
	 */
	@Test
	public void everyLegsLiftIsOneSlotMovedOffTheFleshGamutAndNothingElse()
	{
		List<Record> all = shippedRecords();
		TreeSet<String> lifted = new TreeSet<>();
		TreeSet<String> hadACleanRotationAvailable = new TreeSet<>();

		for (Record ours : all)
		{
			if (!ours.isOurs())
			{
				continue;
			}

			for (Record donor : all)
			{
				if (donor.isOurs()
					|| !Arrays.equals(donor.modelIds, ours.modelIds)
					|| !Arrays.equals(donor.recolorFind, ours.recolorFind))
				{
					continue;
				}

				Lift lift = reDealOf(ours.recolorReplace, donor.recolorReplace, ours.recolorFind);
				if (lift == null || lift.legs == 0)
				{
					continue;
				}

				lifted.add(ours.name);
				assertEquals(ours.describe() + " lifts more than the one slot the legs rule "
					+ "forces, which is hand-authoring rather than a re-deal", 1, lift.legs);

				if (cleanRotationsOf(donor.recolorReplace, ours.recolorFind) > 0)
				{
					hadACleanRotationAvailable.add(ours.name);
				}

				// The shape of the lift itself: the slot has to be the legs slot, and the
				// move has to be out of the gamut rather than merely to a different colour.
				short[] mine = shorts(ours.recolorReplace);
				short[] theirs = shorts(donor.recolorReplace);
				int rotationsThatWouldHaveDealtFleshOntoTheLegs = 0;
				for (int deal = 1; deal < theirs.length; deal++)
				{
					short[] dealt = CitizenEcho.redeal(theirs, deal);
					for (int i = 0; i < dealt.length; i++)
					{
						if (dealt[i] == mine[i]
							|| (ours.recolorFind[i] & 0xFFFF) != BodySlots.LEGS_BASE
							|| !CitizenEcho.isFlesh(dealt[i]))
						{
							continue;
						}

						rotationsThatWouldHaveDealtFleshOntoTheLegs++;
						assertFalse(ours.describe() + " lifts its legs slot onto " + mine[i]
								+ ", which is inside the gamut it was lifted out of",
							CitizenEcho.isFlesh(mine[i]));
					}
				}
				assertTrue(ours.describe() + " is marked as a legs lift but no rotation of "
						+ donor.describe() + " deals a flesh colour onto its legs slot",
					rotationsThatWouldHaveDealtFleshOntoTheLegs > 0);
			}
		}

		assertEquals("authored records carrying a legs lift", new TreeSet<>(Arrays.asList(
			"Berta", "Brother Edwy", "Marlow", "Nessa", "Osric", "Tarik", "Tobias")), lifted);

		// The count that keeps the javadoc above honest. Six of the seven could have been
		// re-rotated instead; the one that could not is the one that is also a face lift.
		assertEquals("legs lifts that had an untouched rotation available instead",
			new TreeSet<>(Arrays.asList("Berta", "Marlow", "Nessa", "Osric", "Tarik", "Tobias")),
			hadACleanRotationAvailable);
	}

	/**
	 * The licence file's table has to be the dataset's table.
	 *
	 * <p>Region by region, count by count, name by name. This is the assertion the
	 * whole class exists for: {@code NOTICE} is the document a licensee reads, and
	 * nothing else in this build would notice it going stale.
	 *
	 * <p><b>A row's count is checked against more than its own name list.</b> It used
	 * to be checked against that alone, which left a hole one duplicate wide: a row
	 * reading {@code 10290   3   Ardougne monastery   Anselm, Brother Edwy, Brother
	 * Edwy} has a count of three and three names, so it passed, and the name
	 * comparison below is between sets, so the duplicate collapsed and matched the two
	 * records that actually ship. The disclosure would have overstated what this
	 * project added and nothing would have said so. Names within a row are now
	 * asserted distinct, and the row counts are asserted to sum to the thirty-three
	 * the prose above the table claims.
	 */
	@Test
	public void theNoticeTableMatchesWhatWasActuallyAdded() throws IOException
	{
		String notice = new String(
			Files.readAllBytes(new File("NOTICE").toPath()), StandardCharsets.UTF_8);

		assertTrue("NOTICE has to state the marker, or a reader cannot find these records",
			notice.contains("beginning \"" + MARKER + "\""));

		TreeMap<Integer, List<String>> declared = new TreeMap<>();
		int declaredTotal = 0;
		Matcher matcher = ROW.matcher(notice);
		while (matcher.find())
		{
			int region = Integer.parseInt(matcher.group(1));
			int count = Integer.parseInt(matcher.group(2));
			List<String> names = new ArrayList<>();
			for (String name : matcher.group(3).split(","))
			{
				String trimmed = name.trim().replaceAll("\\s+", " ");
				if (!trimmed.isEmpty())
				{
					names.add(trimmed);
				}
			}

			assertEquals("NOTICE row for region " + region + " names " + names.size()
				+ " citizen(s) but claims " + count, count, names.size());
			assertEquals("NOTICE row for region " + region + " lists a name twice, which "
					+ "makes its count larger than the number of records it names: " + names,
				names.size(), new TreeSet<>(names).size());
			assertFalse("region " + region + " is listed twice in NOTICE",
				declared.containsKey(region));
			declared.put(region, names);
			declaredTotal += count;
		}

		assertEquals("the parser has to find the whole table — a regex that matched "
				+ "nothing would pass while checking nothing", 13, declared.size());
		assertEquals("the row counts have to sum to the total NOTICE claims in prose",
			AUTHORED, declaredTotal);

		TreeMap<Integer, List<String>> actual = ourRecordsByRegion();
		assertEquals("the regions NOTICE lists have to be the regions that changed",
			actual.keySet(), declared.keySet());

		for (Integer region : actual.keySet())
		{
			assertEquals("citizens NOTICE lists for " + region,
				new TreeSet<>(declared.get(region)), new TreeSet<>(actual.get(region)));
		}
	}

	/**
	 * @return whether {@code candidate} is {@code donor}'s palette dealt round by a
	 * rotation that actually changes it — which excludes the identity, and excludes a
	 * rotation that lands back on the palette it started from because every colour in
	 * it is the same
	 *
	 * <p><b>This used to ask {@link CitizenEcho#distinctDeals} for the rotations, and
	 * it deliberately does not any more.</b> Sharing that method made the authoring
	 * rule and the derivation rule the same rule by construction, which read as a
	 * strength right up until the derivation acquired a rule the authoring does not
	 * have: {@code distinctDeals} now refuses any rotation that would move a colour
	 * across the flesh boundary (see
	 * {@link CitizenEcho#keepsEachColourOnItsOwnSideOfTheSkin}), and 33 of the
	 * authored top-up records were hand-rotated by exactly such a rotation, so every
	 * one of them failed this test the moment the flesh rule landed.
	 *
	 * <p>They are not twins of their donors, which is the only thing this test claims,
	 * so the answer is to state the claim in its own terms rather than to borrow one
	 * that has grown a second meaning. The rotation is still {@link CitizenEcho#redeal}
	 * — the same operation, on the same arrays — and "distinct" is still the same
	 * exclusion. What is dropped is the flesh rule, which is a rule about <b>what this
	 * plugin may derive</b> and not about what a human may author: a Demon Butler with
	 * a red face and a shrouded figure with a white one are content decisions, and no
	 * test gets a vote on them.
	 *
	 * <p><b>That said, this is a finding and not a formality.</b> The hand-rotated
	 * palettes cross the boundary for the same reason a derived one would, and some of
	 * them therefore land a garment colour on a face, or a skin tone on a garment, on a
	 * citizen who ships at every density rather than only at {@code CROWDED}.
	 * <b>24 of the 33</b> are rotations the flesh rule would refuse — not all 33, which
	 * is what this paragraph claimed until the 2026-08-30 pass. The other nine happen to
	 * be class-preserving rotations and would have passed the coupled form unchanged,
	 * which matters because <b>"Wynn" in Catherby was one of the nine</b> and was the
	 * worst record in the set: he wore {@link CitizenEcho#PLAYER_SKIN_BASE} on
	 * {@code find = 25238}, the legs base, so his trousers were painted the exact colour
	 * the game paints faces. Neither form of this assertion could ever have caught him,
	 * because both ask whether a palette <i>is</i> a rotation and he is one.
	 *
	 * <p>The photographs the top-up shipped are worth naming rather than summarising.
	 * The Varrock "Gardener" is authored {@code [3507, 16026, 37394, 5572]} against the
	 * {@code find} slots {@code [4550, 8741, 25238, 123]} — a tan face, a dark
	 * yellow-green tunic, dark blue legs. "Tobias" in Falador is that palette rotated by
	 * two: a <b>very dark blue face</b>, and mid tan on <i>both</i> the torso and the
	 * legs. "Marlow" in Draynor is rotated by one: a <b>dark yellow-green face</b>, a
	 * dark blue torso and mid tan legs. So it is not only that one wears the green on
	 * his face and the other the blue — each of them also wears a complexion where a
	 * garment goes, which is the half of the fault the reader is actually looking at in
	 * the photograph.
	 *
	 * <p>Six records were worse still and were repaletted on 2026-08-30 — see
	 * {@link BodySlotLintTest} for the rule that now holds them and
	 * {@link #noRotationOfThoseSixDonorsCouldHaveHeldTheFaceColourInPlace} for why a
	 * different rotation was not available.
	 *
	 * <p><b>Tobias and Marlow were left out of that pass and should not have been.</b>
	 * The reasoning written here was that they wear flesh-<i>class</i> tans rather than
	 * the face colour itself, so no categorical rule reached them and repaletting them
	 * would be a taste judgement. That was defensible in the abstract and was falsified
	 * by observation on 2026-08-31: the owner, playing at {@code FULL}, reported figures
	 * that still looked like they had no trousers. Marlow's {@code 5572} decodes to
	 * hue 5, saturation 3, lightness 68 against the skin base's hue 4, saturation 3,
	 * lightness 70 — one hue rung and two lightness rungs from the colour the client
	 * paints a face. It is not a tan near skin; on a pair of legs it is skin.
	 *
	 * <p>The categorical rule that does reach them exists and is
	 * {@link BodySlotLintTest#noLegsSlotIsPaintedAColourFromTheFleshGamut}: a legs slot
	 * may not be painted a flesh-gamut colour at all. Both are now legs lifts — see
	 * {@link #everyLegsLiftIsOneSlotMovedOffTheFleshGamutAndNothingElse} — and their
	 * faces, which this paragraph describes correctly and which nothing in this pass
	 * touched, are still what they were.
	 */
	private static boolean isADistinctReDealOf(int[] candidate, int[] donor)
	{
		if (candidate.length != donor.length || donor.length < 2)
		{
			return false;
		}

		short[] mine = shorts(candidate);
		short[] theirs = shorts(donor);
		for (int deal = 1; deal < theirs.length; deal++)
		{
			short[] dealt = CitizenEcho.redeal(theirs, deal);
			if (!Arrays.equals(dealt, theirs) && Arrays.equals(dealt, mine))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * How much of {@code candidate} is not simply {@code donor}'s palette dealt round.
	 *
	 * @return {@code null} if {@code candidate} is not a distinct rotation of
	 * {@code donor} at all; otherwise the rotation with the fewest lifted slots, counted
	 * by <i>which</i> of the two permitted exceptions each one is
	 *
	 * <p><b>Two exceptions, both deliberately narrow, and counted apart.</b> A slot may
	 * not differ from the rotation for any reason a reader has to take on trust — only
	 * for one of these, and only ever <i>away</i> from the thing that is refused:
	 *
	 * <ul>
	 *   <li><b>A face lift.</b> The rotation would land
	 *       {@link CitizenEcho#PLAYER_SKIN_BASE} on a {@code find} the donor did not aim
	 *       at skin, and the candidate paints something that is not the face colour. Six
	 *       records, 2026-08-30, and
	 *       {@link #noRotationOfThoseSixDonorsCouldHaveHeldTheFaceColourInPlace} proves
	 *       each was forced.</li>
	 *   <li><b>A legs lift.</b> The rotation would land a colour inside
	 *       {@link CitizenEcho#isFlesh}'s gamut on the {@link BodySlots#LEGS_BASE} slot,
	 *       and the candidate paints something outside it. Seven records, 2026-08-31, and
	 *       {@link #everyLegsLiftIsOneSlotMovedOffTheFleshGamutAndNothingElse} pins its
	 *       shape.</li>
	 * </ul>
	 *
	 * <p>Counted apart because the two claims are not the same strength. A face lift is
	 * <i>forced</i> — no rotation of those donors could have avoided it. A legs lift is
	 * forced only once in its seven cases, and saying so is the whole point of keeping
	 * the two numbers separate rather than adding them up.
	 */
	private static Lift reDealOf(int[] candidate, int[] donor, int[] find)
	{
		if (candidate.length != donor.length || donor.length < 2 || find.length != donor.length)
		{
			return null;
		}

		short[] mine = shorts(candidate);
		short[] theirs = shorts(donor);
		Lift best = null;

		for (int deal = 1; deal < theirs.length; deal++)
		{
			short[] dealt = CitizenEcho.redeal(theirs, deal);
			if (Arrays.equals(dealt, theirs) || Arrays.equals(mine, theirs))
			{
				// The identity rotation, or a candidate that is its donor's twin.
				continue;
			}

			int face = 0;
			int legs = 0;
			boolean usable = true;
			for (int i = 0; i < dealt.length; i++)
			{
				if (dealt[i] == mine[i])
				{
					continue;
				}

				if ((dealt[i] & 0xFFFF) == CitizenEcho.PLAYER_SKIN_BASE
					&& (find[i] & 0xFFFF) != CitizenEcho.PLAYER_SKIN_BASE
					&& (mine[i] & 0xFFFF) != CitizenEcho.PLAYER_SKIN_BASE)
				{
					face++;
				}
				else if ((find[i] & 0xFFFF) == BodySlots.LEGS_BASE
					&& CitizenEcho.isFlesh(dealt[i])
					&& !CitizenEcho.isFlesh(mine[i]))
				{
					legs++;
				}
				else
				{
					usable = false;
					break;
				}
			}

			if (usable && (best == null || face + legs < best.total()))
			{
				best = new Lift(face, legs);
			}
		}

		return best;
	}

	/**
	 * How many distinct rotations of {@code donor} land nothing where it may not go —
	 * no {@link CitizenEcho#PLAYER_SKIN_BASE} on a {@code find} aimed at anything but
	 * skin, and nothing inside {@link CitizenEcho#isFlesh}'s gamut on the
	 * {@link BodySlots#LEGS_BASE} slot.
	 *
	 * <p>Used only to record, rather than to require, whether a record that took a legs
	 * lift had an untouched rotation available to it instead.
	 */
	private static int cleanRotationsOf(int[] donor, int[] find)
	{
		short[] theirs = shorts(donor);
		TreeSet<String> seen = new TreeSet<>();
		int clean = 0;

		for (int deal = 1; deal < theirs.length; deal++)
		{
			short[] dealt = CitizenEcho.redeal(theirs, deal);
			if (Arrays.equals(dealt, theirs) || !seen.add(Arrays.toString(dealt)))
			{
				continue;
			}

			boolean ok = true;
			for (int i = 0; i < dealt.length && ok; i++)
			{
				ok = !((dealt[i] & 0xFFFF) == CitizenEcho.PLAYER_SKIN_BASE
						&& (find[i] & 0xFFFF) != CitizenEcho.PLAYER_SKIN_BASE)
					&& !((find[i] & 0xFFFF) == BodySlots.LEGS_BASE && CitizenEcho.isFlesh(dealt[i]));
			}

			if (ok)
			{
				clean++;
			}
		}

		return clean;
	}

	/** The two kinds of lifted slot a re-deal may carry, counted apart. @see #reDealOf */
	private static final class Lift
	{
		private final int face;
		private final int legs;

		Lift(int face, int legs)
		{
			this.face = face;
			this.legs = legs;
		}

		int total()
		{
			return face + legs;
		}
	}

	/**
	 * @return whether this dealt palette puts the game's own face colour on a
	 * {@code find} slot the author did not aim at skin
	 */
	private static boolean landsTheFaceColourOnANonSkinSlot(short[] dealt, int[] find)
	{
		for (int i = 0; i < dealt.length; i++)
		{
			if ((dealt[i] & 0xFFFF) == CitizenEcho.PLAYER_SKIN_BASE
				&& (find[i] & 0xFFFF) != CitizenEcho.PLAYER_SKIN_BASE)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The recolour values as the render path sees them.
	 *
	 * <p>{@code EntityDefinition} narrows these to {@code short} because that is what
	 * {@code ModelData#recolor} takes, and several shipped palettes hold values above
	 * {@code Short.MAX_VALUE}. The narrowing is one-to-one over the range the dataset
	 * uses (every value is below 65536), so comparing after it neither merges two
	 * distinct colours nor splits one — and it is the comparison that matches what
	 * actually renders.
	 */
	private static short[] shorts(int[] values)
	{
		short[] out = new short[values.length];
		for (int i = 0; i < values.length; i++)
		{
			out[i] = (short) values[i];
		}
		return out;
	}

	/**
	 * @return every marked record, grouped by the file it ships in, read straight
	 * from the JSON rather than from a validated {@link EntityDefinition} — the
	 * disclosure is about what is written in the files, not about what survived
	 * validation
	 */
	private static TreeMap<Integer, List<String>> ourRecordsByRegion()
	{
		TreeMap<Integer, List<String>> ours = new TreeMap<>();
		for (Record record : shippedRecords())
		{
			if (record.isOurs())
			{
				ours.computeIfAbsent(record.fileRegionId, k -> new ArrayList<>())
					.add(record.name);
			}
		}
		return ours;
	}

	/**
	 * Every record in every shipped region file, both rosters, exactly as authored.
	 *
	 * <p>Read here rather than through {@link ShippedCitizens} or
	 * {@link ShippedModelIds} because this class needs three things neither of them
	 * carries together: the uuid (which roster a record is in is decided by the
	 * marker), the raw {@code modelIds}, and both recolour arrays.
	 */
	private static List<Record> shippedRecords()
	{
		List<Record> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			String resource = RegionDataLoader.DEFAULT_RESOURCE_PREFIX + regionId + ".json";
			InputStream in = AuthoredRecordsTest.class.getClassLoader()
				.getResourceAsStream(resource);
			if (in == null)
			{
				throw new IllegalStateException("missing " + resource);
			}

			try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				JsonObject root = TestGson.injected().fromJson(reader, JsonObject.class);
				collect(root, "citizenRoster", regionId, out);
				collect(root, "sceneryRoster", regionId, out);
			}
			catch (IOException e)
			{
				throw new IllegalStateException("could not read " + resource, e);
			}
		}

		if (out.isEmpty())
		{
			throw new IllegalStateException("no records found in the shipped dataset");
		}

		return out;
	}

	private static void collect(JsonObject root, String rosterKey, int regionId, List<Record> into)
	{
		JsonElement roster = root.get(rosterKey);
		if (roster == null || !roster.isJsonArray())
		{
			return;
		}

		for (JsonElement element : roster.getAsJsonArray())
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject record = element.getAsJsonObject();
			into.add(new Record(
				regionId,
				rosterKey,
				string(record, "uuid"),
				string(record, "name"),
				ints(record, "modelIds"),
				ints(record, "modelRecolorFind"),
				ints(record, "modelRecolorReplace")));
		}
	}

	private static String string(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static int[] ints(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		if (value == null || !value.isJsonArray())
		{
			return new int[0];
		}

		JsonArray array = value.getAsJsonArray();
		int[] out = new int[array.size()];
		for (int i = 0; i < array.size(); i++)
		{
			out[i] = array.get(i).getAsInt();
		}
		return out;
	}

	/** One roster record, from either roster, exactly as it is written in the file. */
	private static final class Record
	{
		final int fileRegionId;
		final String rosterKey;
		final String uuid;
		final String name;
		final int[] modelIds;
		final int[] recolorFind;
		final int[] recolorReplace;

		Record(
			int fileRegionId,
			String rosterKey,
			String uuid,
			String name,
			int[] modelIds,
			int[] recolorFind,
			int[] recolorReplace)
		{
			this.fileRegionId = fileRegionId;
			this.rosterKey = rosterKey;
			this.uuid = uuid;
			this.name = name;
			this.modelIds = modelIds;
			this.recolorFind = recolorFind;
			this.recolorReplace = recolorReplace;
		}

		boolean isOurs()
		{
			return uuid != null && uuid.startsWith(MARKER);
		}

		String describe()
		{
			return "'" + (name == null ? rosterKey : name) + "' in " + fileRegionId + ".json";
		}
	}
}
