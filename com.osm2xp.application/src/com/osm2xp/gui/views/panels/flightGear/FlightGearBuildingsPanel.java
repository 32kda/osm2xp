package com.osm2xp.gui.views.panels.flightGear;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;

import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

/**
 * FlightGear buildings (OSMBuildings shader / BUILDING_LIST) options panel.
 *
 * @author osm2xp
 */
public class FlightGearBuildingsPanel extends Osm2xpPanel {

	private Button btnGenerateBuildings;
	private Button btnProbeElevation;
	private Button btnAllowNeighbours;
	private Spinner spinnerSmallMinSide;
	private Spinner spinnerMediumMinSide;
	private Spinner spinnerLargeMinSide;
	private Spinner spinnerSmallMaxLevels;
	private Spinner spinnerMediumMaxLevels;
	private Spinner spinnerLargeMaxLevels;
	private Spinner spinnerAreaDeviation;
	private Spinner spinnerDistDeviation;
	private Spinner spinnerTextureRadius;
	private Spinner spinnerFlatRatio;
	private Spinner spinnerGabledRatio;
	private Spinner spinnerHippedRatio;

	public FlightGearBuildingsPanel(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected void initComponents() {
		btnGenerateBuildings = new Button(this, SWT.CHECK);
		btnGenerateBuildings.setText("Generate buildings (OSMBuildings shader)");
		btnGenerateBuildings.setToolTipText(
				"Write buildings as a BUILDING_LIST rendered by the FlightGear OSMBuildings shader");

		btnProbeElevation = new Button(this, SWT.CHECK);
		btnProbeElevation.setText("Place buildings at terrain elevation");
		btnProbeElevation.setToolTipText(
				"Probe the downloaded terrain tiles and place each building at the real ground elevation");

		btnAllowNeighbours = new Button(this, SWT.CHECK);
		btnAllowNeighbours.setText("Allow buildings to share walls with neighbours");
		btnAllowNeighbours.setToolTipText("Group adjacent buildings so that touching walls are not rendered twice");

		addLabel("Small building min side, m");
		spinnerSmallMinSide = addSpinner(1, 1000);
		addLabel("Medium building min side, m");
		spinnerMediumMinSide = addSpinner(1, 1000);
		addLabel("Large building min side, m");
		spinnerLargeMinSide = addSpinner(1, 1000);
		addLabel("Small building max levels");
		spinnerSmallMaxLevels = addSpinner(1, 100);
		addLabel("Medium building max levels");
		spinnerMediumMaxLevels = addSpinner(1, 200);
		addLabel("Large building max levels");
		spinnerLargeMaxLevels = addSpinner(1, 500);
		addLabel("Area deviation (footprint / bounding rect)");
		spinnerAreaDeviation = addSpinner(1, 1000);
		addLabel("Distance deviation");
		spinnerDistDeviation = addSpinner(1, 1000);
		addLabel("Texture group radius, m");
		spinnerTextureRadius = addSpinner(0, 10000);
		addLabel("Flat roof ratio");
		spinnerFlatRatio = addSpinner(0, 1000);
		addLabel("Gabled roof ratio");
		spinnerGabledRatio = addSpinner(0, 1000);
		addLabel("Hipped roof ratio");
		spinnerHippedRatio = addSpinner(0, 1000);
	}

	private void addLabel(String text) {
		Label label = new Label(this, SWT.NONE);
		label.setText(text);
	}

	private Spinner addSpinner(int min, int max) {
		Spinner spinner = new Spinner(this, SWT.BORDER);
		spinner.setMinimum(min);
		spinner.setMaximum(max);
		return spinner;
	}

	@Override
	protected void initLayout() {
		GridLayout layout = new GridLayout(2, false);
		layout.horizontalSpacing = 15;
		layout.verticalSpacing = 6;
		setLayout(layout);
		btnGenerateBuildings.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		btnProbeElevation.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		btnAllowNeighbours.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
	}

	@Override
	protected void bindComponents() {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		bindComponent(btnGenerateBuildings, options, "generateBuildings");
		bindComponent(btnProbeElevation, options, "generateBuildingsElevation");
		bindComponent(btnAllowNeighbours, options, "buildingListAllowNeighbours");
		bindSpinnerToDouble(spinnerSmallMinSide, options, "buildingListSmallMinSide", 1);
		bindSpinnerToDouble(spinnerMediumMinSide, options, "buildingListMediumMinSide", 1);
		bindSpinnerToDouble(spinnerLargeMinSide, options, "buildingListLargeMinSide", 1);
		bindComponent(spinnerSmallMaxLevels, options, "buildingListSmallMaxLevels");
		bindComponent(spinnerMediumMaxLevels, options, "buildingListMediumMaxLevels");
		bindComponent(spinnerLargeMaxLevels, options, "buildingListLargeMaxLevels");
		bindSpinnerToDouble(spinnerAreaDeviation, options, "buildingListAreaDeviation", 2);
		bindSpinnerToDouble(spinnerDistDeviation, options, "buildingListDistDeviation", 2);
		bindComponent(spinnerTextureRadius, options, "buildingTextureGroupRadius");
		bindSpinnerToDouble(spinnerFlatRatio, options, "roofShapeFlatRatio", 2);
		bindSpinnerToDouble(spinnerGabledRatio, options, "roofShapeGabledRatio", 2);
		bindSpinnerToDouble(spinnerHippedRatio, options, "roofShapeHippedRatio", 2);
	}
}
