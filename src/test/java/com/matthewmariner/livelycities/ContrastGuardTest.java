package com.matthewmariner.livelycities;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Every label a user is meant to read clears a real contrast ratio against whatever is
 * actually painted behind it.
 *
 * <p><b>Why this exists.</b> This panel had exactly one raw {@code MEDIUM_GRAY_COLOR} —
 * RGB(77,77,77) — behind its {@code HELPER} alias, and that one line coloured every
 * detail line, greyed-out city name, "off" state and empty-state paragraph on the panel.
 * Against this panel's card background, RGB(30,30,30), that is a contrast ratio under
 * 2:1 — WCAG's floor for body text is 4.5:1. Every test in
 * {@code LivelyCitiesPanelTest} that asserted a label's text passed the whole time,
 * because an unreadable label still returns its string from {@code getText()}. This repo
 * ships {@code noRowOnThePanelIsPinnedToZeroHeight} for the exact same shape of gap —
 * existence is not visibility, and visibility is not legibility.
 *
 * <p><b>Computed, not hardcoded.</b> The ratio comes out of the two {@code Color}s a
 * label actually holds when this walks the tree — its own foreground, and the background
 * of the nearest ancestor that actually paints one — via the WCAG 2 relative-luminance
 * formula, so a future colour swap that reintroduces the bug fails on the numbers.
 *
 * <p><b>Two bars, structurally chosen.</b> Ordinary body text is held to WCAG SC 1.4.3's
 * 4.5:1. The two labels that are themselves opaque, self-painted chips — a density chip
 * and an override row's "show"/"unmute" action — are held to SC 1.4.11's 3:1 instead:
 * a chip's state is carried twice, in the fill as well as the glyph, which is the
 * redundancy 1.4.11 exists for. Which bar applies is read off {@link JLabel#isOpaque()}
 * rather than off which label this happens to be.
 */
public class ContrastGuardTest
{
	private static final WorldPoint IN_VARROCK = new WorldPoint(3225, 3360, 0);

	/** WCAG 2 SC 1.4.3 (text contrast, AA): the bar for a plain label on its panel. */
	private static final double TEXT_MIN_CONTRAST = 4.5;

	/**
	 * WCAG 2 SC 1.4.11 (non-text/UI-component contrast, AA): the bar for a label that
	 * paints its own background — a density chip or an override row's action label.
	 * Its selected/rest state is already carried by the fill, so the glyph itself is
	 * held to the UI-component bar rather than the paragraph one.
	 */
	private static final double CHIP_MIN_CONTRAST = 3.0;

	private final FakeConfig config = new FakeConfig();
	private final CitizenDirectory directory =
		new CitizenDirectory(new RegionDataLoader(TestGson.injected()));

	/**
	 * Before any game tick has ever posted — the panel's very first paint, with
	 * {@code model} still null. This is the "Starting up" / "No reading yet" branch, and
	 * the density chips and headings that are built once in the constructor rather than
	 * from a model.
	 */
	@Test
	public void noLabelBeforeTheFirstReadingIsTooCloseToItsBackground()
	{
		assertReadable(new LivelyCitiesPanel(plugin(config)));
	}

	/**
	 * The login-screen reading: nine real city cards, on and off, an empty
	 * overrides section, and both helper paragraphs.
	 */
	@Test
	public void noLabelOnALoggedOutPanelIsTooCloseToItsBackground()
	{
		config.disableOnly(City.FALADOR);
		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));

		panel.onActivate();
		drain();

		assertReadable(panel);
	}

	/**
	 * A live, in-world reading with somebody hidden and somebody else muted, so the
	 * override cards' subtitle and both restore actions are on screen alongside the
	 * "on screen" figures the now-card reports.
	 */
	@Test
	public void noLabelWithLiveDataAndOverridesIsTooCloseToItsBackground()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition hidden = regions.citizen(12852, 3225, 3360, 0);
		EntityDefinition mutedAndHidden = regions.talker(12852, 3226, 3360, "Busy today.");

		config.overrides().hide(hidden);
		config.overrides().hide(mutedAndHidden);
		config.overrides().mute(mutedAndHidden);

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));

		java.util.EnumMap<City, Integer> active = new java.util.EnumMap<>(City.class);
		active.put(City.VARROCK, 5);

		panel.accept(PanelModel.of(IN_VARROCK, new SceneCensus(5, 12, 3, 1, active), config,
			uuids(hidden, mutedAndHidden), uuids(mutedAndHidden),
			new CitizenDirectory(regions)));
		drain();

		assertReadable(panel);
	}

	// --- the guard itself --------------------------------------------------------

	private static void assertReadable(Container root)
	{
		List<String> failures = new ArrayList<>();

		for (Component component : all(root))
		{
			if (!(component instanceof JLabel) || !component.isVisible())
			{
				continue;
			}

			JLabel label = (JLabel) component;
			String text = label.getText();
			if (text == null || text.isEmpty())
			{
				continue;
			}

			Color background = effectiveBackground(label);
			double ratio = contrast(label.getForeground(), background);
			double minimum = label.isOpaque() ? CHIP_MIN_CONTRAST : TEXT_MIN_CONTRAST;

			if (ratio < minimum)
			{
				failures.add(String.format(
					"\"%s\" reads %.2f:1 (needs %.1f:1) — foreground %s on background %s",
					text, ratio, minimum, label.getForeground(), background));
			}
		}

		assertTrue("label(s) too close to their background:\n" + String.join("\n", failures),
			failures.isEmpty());
	}

	/**
	 * @return the colour actually painted behind {@code component} — its own, if it is
	 * opaque and paints one itself, otherwise the nearest ancestor's. A plain
	 * {@code JLabel} is not opaque by default and paints nothing, so what a user sees
	 * behind it is whichever {@code JPanel} it sits on, and every panel in this file sets
	 * its own background explicitly.
	 */
	private static Color effectiveBackground(Component component)
	{
		for (Component at = component; at != null; at = at.getParent())
		{
			if (at instanceof JComponent && ((JComponent) at).isOpaque())
			{
				return at.getBackground();
			}
		}

		throw new AssertionError("no opaque ancestor carries a background for " + component);
	}

	private static double contrast(Color foreground, Color background)
	{
		double lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
		double darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
		return (lighter + 0.05) / (darker + 0.05);
	}

	/** The WCAG 2 relative-luminance formula, straight off the spec. */
	private static double relativeLuminance(Color colour)
	{
		return 0.2126 * channel(colour.getRed())
			+ 0.7152 * channel(colour.getGreen())
			+ 0.0722 * channel(colour.getBlue());
	}

	private static double channel(int value)
	{
		double normalised = value / 255.0;
		return normalised <= 0.03928
			? normalised / 12.92
			: Math.pow((normalised + 0.055) / 1.055, 2.4);
	}

	// --- walking and driving the panel, trimmed from LivelyCitiesPanelTest -----------

	private LivelyCitiesPlugin plugin(FakeConfig config)
	{
		LivelyCitiesPlugin plugin = new LivelyCitiesPlugin();
		plugin.config = config;
		plugin.configWriter = config.writer();
		plugin.overrides = config.overrides();
		plugin.directory = directory;
		return plugin;
	}

	private static Set<UUID> uuids(EntityDefinition... definitions)
	{
		Set<UUID> out = new java.util.LinkedHashSet<>();
		for (EntityDefinition definition : definitions)
		{
			out.add(definition.getUuid());
		}
		return out;
	}

	private static void collect(Container root, List<Component> into)
	{
		for (Component child : root.getComponents())
		{
			into.add(child);
			if (child instanceof Container)
			{
				collect((Container) child, into);
			}
		}
	}

	private static List<Component> all(Container root)
	{
		List<Component> found = new ArrayList<>();
		collect(root, found);
		return found;
	}

	/** {@link LivelyCitiesPanel#accept} hops to Swing; this waits for that hop to land. */
	private static void drain()
	{
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception e)
		{
			throw new AssertionError("the Swing queue never drained", e);
		}
	}
}
