package com.osm2xp.gui.views.panels.flightGear;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

/**
 * FlightGear power lines options panel.
 *
 * @author osm2xp
 */
public class FlightGearPowerLinesPanel extends Osm2xpPanel {

	private Button btnGeneratePowerLines;

	public FlightGearPowerLinesPanel(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected void initComponents() {
		btnGeneratePowerLines = new Button(this, SWT.CHECK);
		btnGeneratePowerLines.setText("Generate power lines");
		btnGeneratePowerLines.setToolTipText(
				"Place shared FlightGear pylon models at the nodes of power=line / power=minor_line ways");

		Label hint = new Label(this, SWT.WRAP);
		hint.setText("Pylon model is chosen from the line tags (material, design, cables, height) "
				+ "and the maximum distance between nodes, mirroring OSM2City.");
	}

	@Override
	protected void initLayout() {
		GridLayout layout = new GridLayout(1, false);
		layout.horizontalSpacing = 15;
		layout.verticalSpacing = 6;
		setLayout(layout);
		btnGeneratePowerLines.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
	}

	@Override
	protected void bindComponents() {
		bindComponent(btnGeneratePowerLines, FlightGearOptionsProvider.getOptions(), "generatePowerLines");
	}
}
