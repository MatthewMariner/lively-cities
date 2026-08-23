package com.matthewmariner.livelycities;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One citizen's remarks: the four ways of having nothing to say, the dwell, and
 * the seeded randomness.
 *
 * <p>{@link CitizenChatter} is the class that decides <i>who</i> talks; this is
 * the state it drives. They are tested apart because the interesting failures are
 * different: here it is "silence is silence however it was spelled in the JSON",
 * there it is the cadence and the cap.
 */
public class CitizenRemarksTest
{
	private final FakeRegions regions = new FakeRegions();

	/**
	 * All four spellings of silence, side by side.
	 *
	 * <p>All four are in the shipped data — 33 citizens carry remarks, 54 carry
	 * {@code "remarks": []}, 42 carry no field at all, and all 46 scenery records
	 * omit it — so this is a fixture matching reality rather than an invented edge
	 * case. Flattening them at the validation gate is what lets every later reader
	 * ask one question.
	 */
	@Test
	public void everyKindOfSilenceProducesNoRemarksAtAll()
	{
		assertNull("a citizen with no remarks field",
			CitizenRemarks.forDefinition(regions.citizen(12853, 3220, 3420, 0)));
		assertNull("a citizen with an empty remarks array",
			CitizenRemarks.forDefinition(regions.talker(12853, 3220, 3421)));
		assertNull("a citizen whose only remark is blank",
			CitizenRemarks.forDefinition(regions.talker(12853, 3220, 3422, "   ")));
		assertNull("scenery, which must never talk however the record is authored",
			CitizenRemarks.forDefinition(regions.scenery(12853, 3220, 3423, "I am a crate.")));

		assertNotNull("and a citizen with something to say does get one",
			CitizenRemarks.forDefinition(regions.talker(12853, 3220, 3424, "Busy today.")));
	}

	@Test
	public void blankRemarksAreDroppedAndTheRestKept()
	{
		EntityDefinition mixed = regions.talker(12853, 3220, 3420, "First.", "  ", null, "Second.");

		assertEquals(2, mixed.getRemarks().length);
		assertEquals("First.", mixed.getRemarks()[0]);
		assertEquals("Second.", mixed.getRemarks()[1]);
	}

	@Test
	public void aRemarkStaysUpForItsDwellAndThenGoesAway()
	{
		CitizenRemarks remarks = remarksFor("Busy today.");

		assertFalse("silent to start with", remarks.isTalking());

		remarks.say(100, 20);
		assertTrue(remarks.isTalking());
		assertEquals("Busy today.", remarks.text());

		assertFalse("one tick before the dwell is up, it is still there", remarks.expire(119));
		assertTrue(remarks.isTalking());

		assertTrue("and on the tick the dwell expires, it goes", remarks.expire(120));
		assertFalse(remarks.isTalking());
		assertNull(remarks.text());

		assertFalse("expiring nothing is not an event", remarks.expire(200));
	}

	@Test
	public void clearingStopsARemarkMidDwell()
	{
		CitizenRemarks remarks = remarksFor("Busy today.");
		remarks.say(100, 500);
		assertTrue(remarks.isTalking());

		remarks.clear();
		assertFalse("this is the path despawn() and the hard off switch take",
			remarks.isTalking());
	}

	/**
	 * The same citizen says the same things in the same order every session.
	 *
	 * <p>Seeded from the entity's identity, not the clock — the same rule
	 * {@link CitizenWalk} follows, and for the same reason: a street should read as
	 * a place rather than as a random generator, and a test cannot assert anything
	 * about a thousand ticks otherwise.
	 */
	@Test
	public void theRemarkOrderIsTheSameEverySession()
	{
		EntityDefinition definition = regions.talker(12853, 3220, 3420, "A", "B", "C", "D");

		StringBuilder first = new StringBuilder();
		StringBuilder second = new StringBuilder();
		transcribe(definition, first);
		transcribe(definition, second);

		assertEquals("two sessions, same citizen, same lines", first.toString(), second.toString());
		assertTrue("and it does use more than one of them: " + first,
			first.toString().chars().distinct().count() > 1);
	}

	/**
	 * Two citizens do not share a sequence, and neither shares the walk's.
	 *
	 * <p>The second half is why the seed is salted: {@link CitizenWalk} already
	 * seeds a {@link java.util.Random} from the bare {@code stableHash()}, so
	 * without the salt a citizen's remark rolls would line up with its destination
	 * rolls forever — a correlation nobody asked for and nobody could debug.
	 */
	@Test
	public void differentCitizensRollDifferently()
	{
		Set<String> transcripts = new HashSet<>();
		for (int i = 0; i < 8; i++)
		{
			StringBuilder out = new StringBuilder();
			transcribe(regions.talker(12853, 3220 + i, 3420, "A", "B", "C", "D"), out);
			transcripts.add(out.toString());
		}

		assertTrue("eight citizens should not all say the same sequence, got " + transcripts,
			transcripts.size() > 1);
	}

	/**
	 * The rolls are staggered per citizen, so a crowd does not produce a chorus
	 * every interval and then silence.
	 */
	@Test
	public void theDueTickIsStaggeredAcrossCitizens()
	{
		Set<Integer> dueTicks = new HashSet<>();
		for (int i = 0; i < 30; i++)
		{
			CitizenRemarks remarks = remarksFor(regions.talker(12853, 3220 + i, 3420, "A"));
			for (int tick = 1; tick <= 60; tick++)
			{
				if (remarks.dueAt(tick, 60))
				{
					dueTicks.add(tick);
					break;
				}
			}
		}

		assertTrue("thirty citizens on a 60-tick interval should not all be due on the same tick, got "
			+ dueTicks, dueTicks.size() > 1);
	}

	/**
	 * Whatever the interval, a citizen is due exactly once per interval — never
	 * never, and never twice.
	 */
	@Test
	public void aCitizenIsDueExactlyOncePerInterval()
	{
		CitizenRemarks remarks = remarksFor("Busy today.");

		for (int interval : new int[]{1, 7, 60, 120, 600})
		{
			int due = 0;
			for (int tick = 1; tick <= interval * 4; tick++)
			{
				if (remarks.dueAt(tick, interval))
				{
					due++;
				}
			}
			assertEquals("interval " + interval, 4, due);
		}
	}

	private CitizenRemarks remarksFor(String... remarks)
	{
		return remarksFor(regions.talker(12853, 3220, 3420, remarks));
	}

	private CitizenRemarks remarksFor(EntityDefinition definition)
	{
		CitizenRemarks remarks = CitizenRemarks.forDefinition(definition);
		assertNotNull("the fixture has to have something to say", remarks);
		return remarks;
	}

	/**
	 * Drives a fresh holder for the same definition through twenty remarks and
	 * writes down what it said. A fresh holder each time is the point: it is what a
	 * new session is.
	 */
	private void transcribe(EntityDefinition definition, StringBuilder out)
	{
		CitizenRemarks remarks = remarksFor(definition);
		int tick = 0;
		while (out.length() < 20)
		{
			tick += 10;
			if (!remarks.rolls(100))
			{
				continue;
			}
			remarks.say(tick, 1);
			out.append(remarks.text());
			remarks.expire(tick + 1);
		}
	}
}
