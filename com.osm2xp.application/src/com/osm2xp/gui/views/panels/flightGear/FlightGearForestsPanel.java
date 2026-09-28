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
 * FlightGear forests (terrain vegetation) options panel.
 *
 * @author osm2xp
 */
public class FlightGearForestsPanel extends Osm2xpPanel {

	private Button btnGenerateForests;
	private Spinner spinnerSpacing;

	public FlightGearForestsPanel(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected void initComponents() {
		btnGenerateForests = new Button(this, SWT.CHECK);
		btnGenerateForests.setText("Generate forests as terrain vegetation");
		btnGenerateForests.setToolTipText(
				"Scatter trees for OSM forest areas into the terrain BTG, using the native FlightGear vegetation points");

		addLabel("Mean tree spacing, m");
		spinnerSpacing = addSpinner(1, 5000);
		spinnerSpacing.setToolTipText(
				"Average distance between scattered trees; smaller values add more trees (and larger tiles)");
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
		btnGenerateForests.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
	}

	@Override
	protected void bindComponents() {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		bindComponent(btnGenerateForests, options, "generateForests");
		bindSpinnerToDouble(spinnerSpacing, options, "forestSpacingM", 1);
	}
}
