package com.osm2xp.gui.views.panels.flightGear;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

/**
 * FlightGear 3D objects options panel: rule-based objects, building-size models
 * and the special objects (chimneys, cooling towers, silos, storage tanks).
 *
 * @author osm2xp
 */
public class FlightGearObjectsPanel extends Osm2xpPanel {

	private Button btnGenerateObjects;
	private Button btnGenerateBuildings3D;
	private Button btnGenerateChimneys;
	private Button btnGenerateCoolingTowers;
	private Button btnGenerateSilos;
	private Button btnGenerateStorageTanks;

	public FlightGearObjectsPanel(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected void initComponents() {
		btnGenerateObjects = new Button(this, SWT.CHECK);
		btnGenerateObjects.setText("Generate 3D objects (by rules)");
		btnGenerateObjects.setToolTipText("Insert 3D objects based on the rules specified on this tab");

		btnGenerateBuildings3D = new Button(this, SWT.CHECK);
		btnGenerateBuildings3D.setText("Generate buildings by size");
		btnGenerateBuildings3D.setToolTipText(
				"Choose a 3D model with the closest footprint size from the bundled objects folder");

		btnGenerateChimneys = new Button(this, SWT.CHECK);
		btnGenerateChimneys.setText("Generate chimneys");
		btnGenerateChimneys.setToolTipText("Generate chimneys by selecting the best-fit model");

		btnGenerateCoolingTowers = new Button(this, SWT.CHECK);
		btnGenerateCoolingTowers.setText("Generate cooling towers");
		btnGenerateCoolingTowers.setToolTipText("Generate cooling towers by selecting the best-fit model");

		btnGenerateSilos = new Button(this, SWT.CHECK);
		btnGenerateSilos.setText("Generate silos");
		btnGenerateSilos.setToolTipText("Generate silos (building/man_made=silo) by diameter");

		btnGenerateStorageTanks = new Button(this, SWT.CHECK);
		btnGenerateStorageTanks.setText("Generate tanks/gasometers");
		btnGenerateStorageTanks.setToolTipText(
				"Generate storage tanks and gasometers (tanks, fuel/oil tanks, gasometers) by diameter");
	}

	@Override
	protected void initLayout() {
		GridLayout layout = new GridLayout(1, false);
		layout.horizontalSpacing = 15;
		layout.verticalSpacing = 6;
		setLayout(layout);
		btnGenerateObjects.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
	}

	@Override
	protected void bindComponents() {
		bindComponent(btnGenerateObjects, FlightGearOptionsProvider.getOptions(), "generateObjects");
		bindComponent(btnGenerateBuildings3D, FlightGearOptionsProvider.getOptions(), "generateBuildings3D");
		bindComponent(btnGenerateChimneys, FlightGearOptionsProvider.getOptions(), "generateChimneys");
		bindComponent(btnGenerateCoolingTowers, FlightGearOptionsProvider.getOptions(), "generateCoolingTowers");
		bindComponent(btnGenerateSilos, FlightGearOptionsProvider.getOptions(), "generateSilos");
		bindComponent(btnGenerateStorageTanks, FlightGearOptionsProvider.getOptions(), "generateStorageTanks");
	}
}
