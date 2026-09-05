package com.matthewmariner.livelycities;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The sidebar: which places are populated, what is on screen right now, and the way
 * back from every "Hide" and "Mute" you have ever clicked.
 *
 * <p>The surface the plugin was missing. Nine identical checkboxes in a settings screen
 * can say whether Falador is on; they cannot say that Falador holds 26 citizens, that
 * eleven of them are in front of you, or that the reason the street looks thin is the
 * density dial two sections up. And they cannot offer the one thing the config screen
 * structurally cannot: a row per citizen you hid, with a way to bring back <em>that</em>
 * one. RuneLite config items are annotations on interface methods, fixed at compile
 * time, so a control keyed on a uuid out of a data file has never been possible there —
 * which is why the only undo the plugin shipped was "unhide all", a button that undoes
 * every decision you ever made in order to undo the one you regret.
 *
 * <h2>It decides nothing</h2>
 *
 * <p>Every number on it comes out of {@link PanelModel}, which is composed on the
 * client thread and is static, offline and under test. Every click leaves through
 * {@link LivelyCitiesPlugin} and lands in {@link ConfigWriter}, so the config remains
 * the source of truth and everything this panel does round-trips through the settings
 * screen. What is left here is layout and mouse handling, which is the part no test can
 * reach without a windowing system, and keeping it to that is deliberate — see
 * {@link SidePanel}.
 *
 * <h2>Two threads meet here and neither wanders</h2>
 *
 * <p>{@link #accept} is called from the client thread and hands straight to Swing.
 * Everything below it runs on the event dispatch thread and touches no client API at
 * all: a {@code Client} read off the client thread throws {@code IllegalStateException}
 * in a shipped client, and the numbers that need one have already been read by the time
 * they arrive here. Clicks go the other way and need no marshalling, because
 * {@code ConfigManager} is thread-safe and is what RuneLite's own settings panel writes
 * through from this very thread.
 *
 * <h2>Why it updates in place</h2>
 *
 * <p>A model arrives once a game tick while the panel is open. Rebuilding every
 * component each time — which is what a search-results panel should do — would rebuild
 * the row under the cursor 100 times a minute, losing its hover state and, on the tick
 * between the press and the release, the click itself. So the nine city cards are built
 * once and repainted from the model, and the override rows are rebuilt only when the
 * set of rows actually changes. {@link #signature} is what decides that.
 */
class LivelyCitiesPanel extends PluginPanel
{
	/**
	 * The colour of a heading, a live figure, and anything that is switched on.
	 *
	 * <p>Aliased rather than used directly so that "this panel has one accent" is a
	 * fact about the file rather than a habit — and so the accent can never drift into
	 * a hardcoded hex, which is the standing rule across this author's projects. Every
	 * colour below is a {@link ColorScheme} constant; there is no {@code new Color} in
	 * this file.
	 */
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;

	private static final Color BODY = ColorScheme.TEXT_COLOR;
	private static final Color SUBTLE = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color HELPER = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	private static final Color BACKDROP = ColorScheme.DARK_GRAY_COLOR;

	private final LivelyCitiesPlugin plugin;

	private final IconTextField search = new IconTextField();

	private final JLabel whereValue = new JLabel();
	private final JLabel activeValue = new JLabel();
	private final JLabel detailValue = new JLabel();

	private final Map<CrowdDensity, JLabel> densityChips = new LinkedHashMap<>();
	private final Map<City, CityCard> cityCards = new LinkedHashMap<>();

	private final JPanel cities = new JPanel();
	private final JPanel overrides = new JPanel();
	private final JLabel overridesHeader = new JLabel();
	private final JLabel overridesEmpty = new JLabel();

	/**
	 * The last model handed over, redrawn whenever the filter changes.
	 *
	 * <p>{@code volatile} because {@link #accept} writes it from the client thread and
	 * everything else reads it from Swing's. The model itself is immutable, so a
	 * published reference is a complete, self-consistent reading — which is the whole
	 * reason {@link PanelModel} is a value object rather than a view of the scene.
	 */
	private volatile PanelModel model;

	/**
	 * What {@link #overrides} was last built from — see {@link #signature}.
	 *
	 * <p><b>Null rather than the empty string, and that is a bug fix rather than a
	 * style.</b> The empty string is a legitimate signature: it is what an empty list
	 * produces. Starting there meant the very first draw of a fresh profile — nobody
	 * hidden, nobody muted — compared equal, skipped the rebuild, and left the section
	 * with no components in it at all. Not the empty-state sentence: nothing. Null cannot
	 * be produced by {@link #signature}, so the first draw always builds.
	 */
	@Nullable
	private String overridesSignature;

	private boolean overridesOpen = true;

	/**
	 * Whether this panel is the one currently open in the sidebar.
	 *
	 * <p>{@code volatile} for the same reason as {@link #model}, in the other
	 * direction: it is written from Swing (RuneLite calls {@code onActivate} there) and
	 * read from the client thread, which asks before doing the per-tick work of
	 * composing a model nobody is looking at.
	 */
	private volatile boolean open;

	@Inject
	LivelyCitiesPanel(LivelyCitiesPlugin plugin)
	{
		super(true);
		this.plugin = plugin;
		this.model = null;

		setBackground(BACKDROP);
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setLayout(new BorderLayout());

		final JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(BACKDROP);

		column.add(title());
		column.add(gap(8));
		column.add(nowCard());
		column.add(gap(8));
		column.add(heading("Crowd density"));
		column.add(gap(4));
		column.add(densityRow());
		column.add(gap(4));
		column.add(helper("Thins the roster the same way every time, so a street looks the "
			+ "same each time you walk down it. Crowded adds derived extras instead."));
		column.add(gap(8));
		column.add(searchField());
		column.add(gap(8));
		column.add(heading("Places"));
		column.add(gap(4));

		cities.setLayout(new BoxLayout(cities, BoxLayout.Y_AXIS));
		cities.setBackground(BACKDROP);
		for (City city : City.values())
		{
			final CityCard card = new CityCard(city);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			cityCards.put(city, card);
			cities.add(card);
			cities.add(gap(4));
		}
		column.add(cities);

		column.add(gap(8));
		column.add(overridesHeaderRow());
		column.add(gap(4));

		overrides.setLayout(new BoxLayout(overrides, BoxLayout.Y_AXIS));
		overrides.setBackground(BACKDROP);
		column.add(overrides);

		overridesEmpty.setFont(FontManager.getRunescapeSmallFont());
		overridesEmpty.setForeground(HELPER);

		for (Component child : column.getComponents())
		{
			// BoxLayout lays a column out by each child's X alignment, and the two
			// defaults disagree: JPanel is centred, JLabel is left. Mixed, the column
			// comes out ragged — cards indented by half their slack, headings not. One
			// loop here rather than a call per component, so a row added later cannot
			// forget.
			((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		add(column, BorderLayout.NORTH);
		redraw();
	}

	// --- the two ends of the seam --------------------------------------------

	/**
	 * A fresh reading from the client thread — see {@link SidePanel#refresh}.
	 *
	 * <p>Publishes the model and hops. Never draws on the calling thread: Swing
	 * components may only be touched from the event dispatch thread, and this is called
	 * from the game tick.
	 */
	void accept(PanelModel next)
	{
		model = next;
		SwingUtilities.invokeLater(this::redraw);
	}

	/** @return whether the sidebar is showing this panel — see {@link SidePanel#isOpen} */
	boolean isOpen()
	{
		return open;
	}

	/**
	 * Opened from the toolbar.
	 *
	 * <p>Redraws from whatever the last reading was, so the panel has content
	 * immediately rather than after the next game tick — up to 600ms of "which region
	 * am I in" being one tick stale is nothing; an empty panel for 600ms every time it
	 * is opened is the thing people notice.
	 */
	@Override
	public void onActivate()
	{
		open = true;
		redraw();
	}

	@Override
	public void onDeactivate()
	{
		open = false;
	}

	/**
	 * Called when the button leaves the toolbar, which RuneLite does not necessarily
	 * follow with an {@code onDeactivate}.
	 *
	 * <p>Left true, the plugin would go on composing a model once a game tick for a
	 * panel that is not in the sidebar any more — and after {@code shutDown()} there is
	 * no plugin to compose it. Same shape as the {@code processedGameTick} flag
	 * {@code shutDown} clears for the developer-only reporter, and for the same reason:
	 * a flag that outlives the thing it describes is a slow leak.
	 */
	void closed()
	{
		open = false;
	}

	// --- drawing --------------------------------------------------------------

	private void redraw()
	{
		final PanelModel current = model;
		final String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);

		if (current == null)
		{
			whereValue.setText("Starting up");
			whereValue.setForeground(HELPER);
			activeValue.setText("—");
			detailValue.setText("No reading yet");
			return;
		}

		drawNow(current);
		drawDensity(current);
		drawCities(current, query);
		drawOverrides(current, query);

		revalidate();
		repaint();
	}

	private void drawNow(PanelModel current)
	{
		if (!current.isInWorld())
		{
			whereValue.setText("Not in a world");
			whereValue.setForeground(HELPER);
			activeValue.setText("0");
			activeValue.setForeground(HELPER);
			detailValue.setText("Log in and the numbers here go live");
			return;
		}

		final City here = current.getHere();
		whereValue.setText(here == null ? "Region " + current.getRegionId() : here.getLabel());
		whereValue.setForeground(here == null ? SUBTLE : ACCENT);

		final SceneCensus census = current.getCensus();
		activeValue.setText(String.valueOf(census.getActive()));
		activeValue.setForeground(census.getActive() == 0 ? HELPER : ACCENT);

		if (here == null)
		{
			detailValue.setText("Nowhere this plugin has data for");
		}
		else
		{
			detailValue.setText(census.getWalking() + " walking · " + census.getTalking()
				+ " talking · " + census.getInScope() + " loaded");
		}
	}

	private void drawDensity(PanelModel current)
	{
		for (Map.Entry<CrowdDensity, JLabel> chip : densityChips.entrySet())
		{
			paintChip(chip.getValue(), chip.getKey() == current.getDensity());
		}
	}

	private void drawCities(PanelModel current, String query)
	{
		for (PanelModel.CityRow row : current.getCities())
		{
			final CityCard card = cityCards.get(row.getCity());
			if (card == null)
			{
				continue;
			}
			card.update(row);
			card.setVisible(matches(query, row.getCity().getLabel()));
		}
	}

	/**
	 * The hidden-and-muted list, rebuilt only when it is actually different.
	 *
	 * <p>The signature covers the filter as well as the rows, because a filtered list
	 * is a different list of components even when the model behind it has not moved.
	 */
	private void drawOverrides(PanelModel current, String query)
	{
		final List<PanelModel.OverrideRow> visible = new ArrayList<>();
		for (PanelModel.OverrideRow row : current.getOverrides())
		{
			if (matches(query, row.getDisplayName())
				|| (row.getCity() != null && matches(query, row.getCity().getLabel())))
			{
				visible.add(row);
			}
		}

		overridesHeader.setText((overridesOpen ? "▾  " : "▸  ")
			+ "Hidden and muted (" + current.getOverrides().size() + ")");

		final String next = signature(visible);
		if (!next.equals(overridesSignature))
		{
			overridesSignature = next;
			overrides.removeAll();

			if (visible.isEmpty())
			{
				overridesEmpty.setText(current.getOverrides().isEmpty()
					? "Nobody is hidden or muted."
					: "Nobody here matches that.");
				stack(overridesEmpty);
				stack(helper("Right-click any citizen for Hide and Mute. Whatever you "
					+ "choose turns up here, one row at a time, with the way back."));
			}
			else
			{
				for (PanelModel.OverrideRow row : visible)
				{
					stack(new OverrideCard(row));
				}
			}
		}

		overrides.setVisible(overridesOpen);
	}

	/**
	 * What {@link #drawOverrides} compares to decide whether to rebuild.
	 *
	 * <p>Both flags are in it, not just the uuid: a citizen who was hidden and is now
	 * hidden <i>and</i> muted is the same uuid on the same row with a second action on
	 * it, and a signature of uuids alone would leave that action undrawn until something
	 * else changed.
	 */
	/** Adds one component to the override list, spaced and aligned like every other. */
	private void stack(JComponent child)
	{
		child.setAlignmentX(Component.LEFT_ALIGNMENT);
		overrides.add(child);
		overrides.add(gap(4));
	}

	private static String signature(List<PanelModel.OverrideRow> rows)
	{
		final StringBuilder out = new StringBuilder();
		for (PanelModel.OverrideRow row : rows)
		{
			out.append(row.getUuid())
				.append(row.isHidden() ? 'h' : '-')
				.append(row.isMuted() ? 'm' : '-')
				.append(';');
		}
		return out.toString();
	}

	private static boolean matches(String query, String text)
	{
		return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
	}

	// --- construction helpers -------------------------------------------------

	private JPanel title()
	{
		final JPanel panel = row();

		final JLabel name = new JLabel("Lively Cities");
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(ACCENT);
		panel.add(name, BorderLayout.WEST);

		return panel;
	}

	private JPanel nowCard()
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(CARD);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			new EmptyBorder(6, 8, 6, 8)));

		final JPanel top = new JPanel(new BorderLayout());
		top.setBackground(CARD);

		whereValue.setFont(FontManager.getRunescapeBoldFont());
		whereValue.setForeground(ACCENT);
		top.add(whereValue, BorderLayout.WEST);

		activeValue.setFont(FontManager.getRunescapeBoldFont());
		activeValue.setForeground(ACCENT);
		top.add(activeValue, BorderLayout.EAST);
		panel.add(top);

		final JPanel bottom = new JPanel(new BorderLayout());
		bottom.setBackground(CARD);

		detailValue.setFont(FontManager.getRunescapeSmallFont());
		detailValue.setForeground(HELPER);
		bottom.add(detailValue, BorderLayout.WEST);

		final JLabel caption = new JLabel("on screen");
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(HELPER);
		bottom.add(caption, BorderLayout.EAST);
		panel.add(bottom);

		return constrain(panel);
	}

	private JPanel densityRow()
	{
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		panel.setBackground(BACKDROP);

		for (CrowdDensity density : CrowdDensity.values())
		{
			final JLabel chip = new JLabel(density.toString());
			chip.setFont(FontManager.getRunescapeSmallFont());
			chip.setBorder(new EmptyBorder(3, 7, 3, 7));
			chip.setOpaque(true);
			chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
			chip.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					plugin.setCrowdDensity(density);
				}
			});
			paintChip(chip, false);
			densityChips.put(density, chip);
			panel.add(chip);
		}

		return constrain(panel);
	}

	private static void paintChip(JLabel chip, boolean selected)
	{
		chip.setBackground(selected ? CARD_HOVER : CARD);
		chip.setForeground(selected ? ACCENT : SUBTLE);
	}

	private IconTextField searchField()
	{
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 28));
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.setBackground(CARD);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.addClearListener(this::redraw);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				redraw();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				redraw();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				redraw();
			}
		});
		return search;
	}

	private JPanel overridesHeaderRow()
	{
		final JPanel panel = row();
		panel.setCursor(new Cursor(Cursor.HAND_CURSOR));

		overridesHeader.setFont(FontManager.getRunescapeBoldFont());
		overridesHeader.setForeground(ACCENT);
		panel.add(overridesHeader, BorderLayout.WEST);

		panel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				overridesOpen = !overridesOpen;
				redraw();
			}
		});

		return panel;
	}

	private JPanel heading(String text)
	{
		final JPanel panel = row();

		final JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ACCENT);
		panel.add(label, BorderLayout.WEST);

		return panel;
	}

	/** A muted grey paragraph. Wrapped as HTML because Swing labels do not wrap. */
	private JLabel helper(String text)
	{
		final JLabel label = new JLabel("<html><body style='width:"
			+ (PluginPanel.PANEL_WIDTH - 40) + "px'>" + text + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(HELPER);
		label.setBorder(new EmptyBorder(0, 0, 0, 0));
		return label;
	}

	private JPanel row()
	{
		final JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(BACKDROP);
		return constrain(panel);
	}

	private static JPanel gap(int height)
	{
		final JPanel spacer = new JPanel();
		spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
		spacer.setBackground(BACKDROP);
		spacer.setPreferredSize(new Dimension(1, height));
		spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		spacer.setMinimumSize(new Dimension(1, height));
		return spacer;
	}

	/**
	 * Stops a {@code BoxLayout} row stretching to fill the column.
	 *
	 * <p>Called after the row's children are in place, because the cap is its preferred
	 * height and a row with nothing in it prefers to be nothing tall.
	 */
	private static JPanel constrain(JPanel panel)
	{
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	/**
	 * A row and every label on it, so a hover covers the strip rather than its margins.
	 *
	 * <p>Not called {@code paint}. {@link javax.swing.JComponent} already has a
	 * {@code paint(Graphics)}, and a same-named helper called from inside a
	 * {@code JPanel} subclass below resolves against the inherited one — which does not
	 * fail quietly, but does mean the name is taken.
	 */
	private static void repaintStrip(JPanel strip, Color colour)
	{
		strip.setBackground(colour);
		for (Component child : strip.getComponents())
		{
			child.setBackground(colour);
			if (child instanceof JPanel)
			{
				repaintStrip((JPanel) child, colour);
			}
		}
	}

	// --- the two kinds of card ------------------------------------------------

	/**
	 * One place: its name, what the dataset holds, what is on screen, and its checkbox.
	 *
	 * <p>Built once and repainted, never rebuilt — see the class javadoc. Clicking
	 * anywhere on it toggles the city, which is the same write the config screen's
	 * checkbox makes.
	 */
	private final class CityCard extends JPanel
	{
		private final City city;
		private final JLabel name = new JLabel();
		private final JLabel state = new JLabel();
		private final JLabel counts = new JLabel();

		private boolean enabled = true;

		private CityCard(City city)
		{
			super(new BorderLayout());
			this.city = city;

			setBackground(CARD);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
				new EmptyBorder(5, 8, 5, 8)));
			setCursor(new Cursor(Cursor.HAND_CURSOR));

			name.setFont(FontManager.getRunescapeBoldFont());
			add(name, BorderLayout.WEST);

			state.setFont(FontManager.getRunescapeSmallFont());
			add(state, BorderLayout.EAST);

			counts.setFont(FontManager.getRunescapeSmallFont());
			counts.setForeground(HELPER);
			add(counts, BorderLayout.SOUTH);

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					plugin.setCityEnabled(CityCard.this.city, !enabled);
				}

				@Override
				public void mouseEntered(MouseEvent event)
				{
					repaintStrip(CityCard.this, CARD_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					repaintStrip(CityCard.this, CARD);
				}
			});
		}

		private void update(PanelModel.CityRow row)
		{
			enabled = row.isEnabled();

			name.setText(row.getCity().getLabel());
			name.setForeground(enabled ? ACCENT : HELPER);

			state.setText(enabled ? "on" : "off");
			state.setForeground(enabled ? SUBTLE : HELPER);

			// The live figure is shown whenever there is one, and not only for the city
			// the player is standing in. A loaded scene covers up to nine regions, so
			// standing in Varrock really can mean the Grand Exchange has figures up —
			// and a card that hid that would be hiding the one thing only a panel can
			// show.
			final StringBuilder text = new StringBuilder();
			text.append(row.getCitizens()).append(row.getCitizens() == 1 ? " citizen" : " citizens");
			if (row.getActive() > 0)
			{
				text.append(" · ").append(row.getActive()).append(" on screen");
			}
			if (row.isHere())
			{
				text.append(" · you are here");
			}
			counts.setText(text.toString());
			counts.setForeground(row.isHere() || row.getActive() > 0 ? BODY : HELPER);

			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		}
	}

	/**
	 * One citizen you overrode, with the restore for whichever overrides apply.
	 *
	 * <p>Rebuilt rather than repainted, because the set of rows is what changes and a
	 * pool of reusable rows would be a cache keyed on a list that is usually empty.
	 */
	private final class OverrideCard extends JPanel
	{
		private OverrideCard(PanelModel.OverrideRow row)
		{
			super(new BorderLayout());

			setBackground(CARD);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
				new EmptyBorder(5, 8, 5, 8)));

			final JLabel name = new JLabel(row.getDisplayName());
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(ACCENT);
			add(name, BorderLayout.WEST);

			final StringBuilder subtitle = new StringBuilder();
			subtitle.append(row.getCity() == null ? "no longer in the dataset" : row.getCity().getLabel());
			subtitle.append(" · ");
			if (row.isHidden() && row.isMuted())
			{
				subtitle.append("hidden and muted");
			}
			else
			{
				subtitle.append(row.isHidden() ? "hidden" : "muted");
			}

			final JLabel where = new JLabel(subtitle.toString());
			where.setFont(FontManager.getRunescapeSmallFont());
			where.setForeground(HELPER);
			add(where, BorderLayout.SOUTH);

			final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			actions.setBackground(CARD);
			if (row.isHidden())
			{
				actions.add(action("show", () -> plugin.unhide(row.getUuid())));
			}
			if (row.isMuted())
			{
				actions.add(action("unmute", () -> plugin.unmute(row.getUuid())));
			}
			add(actions, BorderLayout.EAST);

			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		}

		private JLabel action(String text, Runnable onClick)
		{
			final JLabel label = new JLabel(text);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(SUBTLE);
			label.setBorder(new EmptyBorder(2, 5, 2, 5));
			label.setOpaque(true);
			label.setBackground(CARD_HOVER);
			label.setCursor(new Cursor(Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					onClick.run();
				}

				@Override
				public void mouseEntered(MouseEvent event)
				{
					label.setForeground(ACCENT);
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					label.setForeground(SUBTLE);
				}
			});
			return label;
		}
	}

}
