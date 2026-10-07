package com.osm2xp.gui.views.panels.flightGear;

import java.util.List;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.gui.Activator;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.utils.ui.UiUtil;

/**
 * FlightGear airfields options panel: apt.dat generation, BTG patching,
 * skipping of airfields already present in the downloaded terrain and the
 * excluded-ICAO list.
 *
 * @author osm2xp
 */
public class FlightGearAirfieldsPanel extends Osm2xpPanel {

	private ListViewer icaoListViewer;
	private List<String> ignoredICAOList;
	private Button createAirfieldsCheck;
	private Button patchBtgCheck;
	private Button cutBtgCheck;
	private Button ignoreExistingCheck;
	private Section aptConfigSection;

	public FlightGearAirfieldsPanel(Composite parent) {
		super(parent, SWT.NONE);
	}

	@Override
	protected void initComponents() {
		setLayout(new GridLayout(1, false));
		FormToolkit toolkit = new FormToolkit(getDisplay());
		toolkit.adapt(this);

		createAirfieldsCheck = toolkit.createButton(this, "Generate airfields (apt.dat)", SWT.CHECK);
		GridDataFactory.swtDefaults().applyTo(createAirfieldsCheck);

		aptConfigSection = toolkit.createSection(this, Section.TITLE_BAR);
		aptConfigSection.setText("Airfield generation properties");
		Composite con = toolkit.createComposite(aptConfigSection);
		aptConfigSection.setClient(con);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(aptConfigSection);
		con.setLayout(new GridLayout(2, false));

		Label btgHint = toolkit.createLabel(con,
				"Patching the BTG and baking the airfield into it is experimental.", SWT.WRAP);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(btgHint);
		Label btgHint2 = toolkit.createLabel(con,
				"The scenery terrain can have 'sawtooth' and other quirks after it.", SWT.WRAP);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(btgHint2);

		Composite leftComposite = toolkit.createComposite(con);
		GridDataFactory.fillDefaults().grab(false, true).applyTo(leftComposite);
		leftComposite.setLayout(new GridLayout(1, false));

		patchBtgCheck = toolkit.createButton(leftComposite, "Patch BTG terrain", SWT.CHECK);
		patchBtgCheck.setToolTipText(
				"Bake runways, taxiways, aprons and the grass skirt into the terrain BTG tiles");
		GridDataFactory.swtDefaults().applyTo(patchBtgCheck);

		cutBtgCheck = toolkit.createButton(leftComposite, "Cut intersecting terrain (experimental)", SWT.CHECK);
		cutBtgCheck.setToolTipText(
				"Cut a hole in the terrain and fill it with a flat airfield plate connected to the "
						+ "surrounding terrain by a skirt, instead of overlaying the plate on top");
		GridDataFactory.swtDefaults().applyTo(cutBtgCheck);

		ignoreExistingCheck = toolkit.createButton(leftComposite,
				"Ignore existing (already present in downloaded BTGs)", SWT.CHECK);
		ignoreExistingCheck.setToolTipText(
				"Scan the scenery path for airport terrain tiles (e.g. UNNT.btg / UNNT.btg.gz) and skip "
						+ "airfields that are already present");
		GridDataFactory.swtDefaults().applyTo(ignoreExistingCheck);

		Composite rightComposite = toolkit.createComposite(con, SWT.NONE);
		rightComposite.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, true).applyTo(rightComposite);
		createExclusionList(rightComposite, toolkit);

		createAirfieldsCheck.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				UiUtil.setEnabledRecursive(aptConfigSection, createAirfieldsCheck.getSelection());
			}
		});
		patchBtgCheck.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				cutBtgCheck.setEnabled(patchBtgCheck.getSelection());
			}
		});
	}

	@Override
	protected void initLayout() {
		UiUtil.setEnabledRecursive(aptConfigSection, createAirfieldsCheck.getSelection());
		cutBtgCheck.setEnabled(patchBtgCheck.getSelection());
	}

	@Override
	protected void bindComponents() {
		bindComponent(createAirfieldsCheck, FlightGearOptionsProvider.getOptions(), "generateAirfields");
		bindComponent(patchBtgCheck, FlightGearOptionsProvider.getOptions(), "generateAirfieldsBtg");
		bindComponent(cutBtgCheck, FlightGearOptionsProvider.getOptions(), "generateAirfieldsBtgCut");
		bindComponent(ignoreExistingCheck, FlightGearOptionsProvider.getOptions(), "ignoreExistingAirfields");
	}

	private void createExclusionList(Composite parent, FormToolkit toolkit) {
		ignoredICAOList = FlightGearOptionsProvider.getOptions().getIgnoredAirfields();

		Label lbl = toolkit.createLabel(parent, "Exclude following airports from generation");
		GridDataFactory.swtDefaults().span(2, 1).applyTo(lbl);
		icaoListViewer = new ListViewer(parent, SWT.BORDER);
		PixelConverter converter = new PixelConverter(icaoListViewer.getControl());
		GridDataFactory.fillDefaults()
				.hint(converter.convertWidthInCharsToPixels(10), converter.convertWidthInCharsToPixels(30))
				.span(1, 2).applyTo(icaoListViewer.getControl());
		icaoListViewer.setContentProvider(new ArrayContentProvider());
		icaoListViewer.setLabelProvider(new LabelProvider());

		Button addButton = new Button(parent, SWT.PUSH);
		addButton.setImage(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/add.png")
				.createImage());
		addButton.setToolTipText("Add excluded airfield by ICAO code");
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doAddICAO();
			}
		});
		GridDataFactory.swtDefaults().align(SWT.LEFT, SWT.TOP).applyTo(addButton);

		Button removeButton = new Button(parent, SWT.PUSH);
		removeButton.setImage(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/remove.gif")
				.createImage());
		removeButton.setToolTipText("Remove selected airfield");
		removeButton.setEnabled(false);
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRemoveSelected();
			}
		});
		GridDataFactory.swtDefaults().align(SWT.LEFT, SWT.TOP).applyTo(removeButton);
		icaoListViewer.addSelectionChangedListener(event -> removeButton.setEnabled(!event.getSelection().isEmpty()));
		icaoListViewer.addDoubleClickListener(e -> {
			if (icaoListViewer.getSelection().isEmpty()) {
				doAddICAO();
			}
		});
		refreshViewer();
	}

	protected void doRemoveSelected() {
		ISelection selection = icaoListViewer.getSelection();
		if (selection instanceof StructuredSelection && !selection.isEmpty()) {
			String selected = ((StructuredSelection) selection).getFirstElement().toString();
			ignoredICAOList.remove(selected);
			refreshViewer();
		}
		FlightGearOptionsProvider.getOptions().setIgnoredAirfields(ignoredICAOList);
	}

	private void refreshViewer() {
		icaoListViewer.setInput(ignoredICAOList);
	}

	private void doAddICAO() {
		InputDialog dialog = new InputDialog(getShell(), "Enter ICAO code",
				"Enter Airfield ICAO code, which should be ignored during generation", "", this::checkICAO);
		if (dialog.open() == Window.OK) {
			String value = dialog.getValue().toUpperCase().trim();
			ignoredICAOList.add(value);
			refreshViewer();
		}
		FlightGearOptionsProvider.getOptions().setIgnoredAirfields(ignoredICAOList);
	}

	private String checkICAO(String input) {
		input = input.toLowerCase().trim();
		if (input.length() != 4) {
			return "ICAO code should be 4 characters long";
		}
		for (int i = 0; i < input.length(); i++) {
			if (!Character.isLetterOrDigit(input.charAt(i))) {
				return "Character '" + input.charAt(i) + "' is not allowed in ICAO code";
			}
		}
		return null;
	}
}
