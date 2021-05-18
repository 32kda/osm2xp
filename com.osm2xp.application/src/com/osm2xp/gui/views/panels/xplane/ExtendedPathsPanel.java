package com.osm2xp.gui.views.panels.xplane;

import org.eclipse.core.databinding.beans.PojoProperties;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.forms.widgets.FormToolkit;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneAirfieldOptions;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

public class ExtendedPathsPanel extends Osm2xpPanel {

	public ExtendedPathsPanel(Composite parent) {
		super(parent, SWT.NONE);
		setLayout(new GridLayout(2, false));
		FormToolkit toolkit = new FormToolkit(parent.getDisplay());
		toolkit.adapt(this);
		Group group = new Group(this, SWT.NONE);
		group.setText("Type of road network");
		group.setToolTipText("Use regular or EU road network definitions?");
		GridDataFactory.fillDefaults().applyTo(group);
		group.setLayout(new GridLayout(2, false));
		
		Button btnRegular = new Button(group, SWT.RADIO);
		btnRegular.setText("Worldwide(roards.net)");
		btnRegular.setSelection(!XPlaneOptionsProvider.getOptions().isUseEUNetwork());
		GridDataFactory.fillDefaults().applyTo(btnRegular);
		Button btnEU = new Button(group, SWT.RADIO);
		btnEU.setText("European (roads_eu.net; X-Plane 11 only!)");
		btnEU.setSelection(XPlaneOptionsProvider.getOptions().isUseEUNetwork());
		GridDataFactory.fillDefaults().applyTo(btnEU);
		
		SelectionAdapter netTypeListener = new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				XPlaneOptionsProvider.getOptions().setUseEUNetwork(btnEU.getSelection());
			}
			
		};
		btnRegular.addSelectionListener(netTypeListener);
		btnEU.addSelectionListener(netTypeListener);
		
		GridDataFactory.fillDefaults().grab(true, true).span(2,1).applyTo(group);
		
		Group generalSettingsGrp = new Group(this, SWT.NONE);
		generalSettingsGrp.setLayout(new GridLayout(2, false));	
		generalSettingsGrp.setText("General options");
		Button btnGenerateRoads = new Button(generalSettingsGrp, SWT.CHECK);
		btnGenerateRoads.setText("Generate Roads");
		GridDataFactory.fillDefaults().applyTo(btnGenerateRoads);
		Button btnGenerateRail = new Button(generalSettingsGrp, SWT.CHECK);
		btnGenerateRail.setText("Generate Railways");
		GridDataFactory.fillDefaults().applyTo(btnGenerateRail);
		Button btnGeneratePower = new Button(generalSettingsGrp, SWT.CHECK);
		btnGeneratePower.setText("Generate Powerlines");
		GridDataFactory.fillDefaults().applyTo(btnGeneratePower);
		
		Button btnGenerateBridges = new Button(generalSettingsGrp, SWT.CHECK);
		btnGenerateBridges.setText("Generate bridges");
		btnGenerateBridges.setToolTipText("Generate bridges for roads and railways.");
		GridDataFactory.fillDefaults().applyTo(btnGenerateBridges);

		bindingContext.bindValue(WidgetProperties.selection().observe(btnGenerateRoads),		
				PojoProperties.value("generateRoads").observe(XPlaneOptionsProvider.getOptions()));
		bindingContext.bindValue(WidgetProperties.selection().observe(btnGenerateRail),		
				PojoProperties.value("generateRailways").observe(XPlaneOptionsProvider.getOptions()));
		bindingContext.bindValue(WidgetProperties.selection().observe(btnGeneratePower),		
				PojoProperties.value("generatePowerlines").observe(XPlaneOptionsProvider.getOptions()));
		bindingContext.bindValue(WidgetProperties.selection().observe(btnGenerateBridges),		
				PojoProperties.value("generateBridges").observe(XPlaneOptionsProvider.getOptions()));
		
		GridDataFactory.fillDefaults().grab(true, true).span(2,1).applyTo(generalSettingsGrp);
		
		Group roadsGrp = new Group(this, SWT.NONE);
		roadsGrp.setLayout(new GridLayout(2, false));
		roadsGrp.setText("OSM road types to generate");
		createCheckboxAndBind(roadsGrp,toolkit, "Motorway", "generateHighwayMotorway", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Trunk", "generateHighwayTrunk", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Primary", "generateHighwayPrimary", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Secondary", "generateHighwaySecondary", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Tertiary", "generateHighwayTertiary", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Living", "generateHighwayLiving", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Residential", "generateHighwayResidential", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Pedestrian", "generateHighwayPedestrian", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Construction", "generateHighwayConstruction", XPlaneOptionsProvider.getOptions());
		createCheckboxAndBind(roadsGrp,toolkit, "Unclassified", "generateHighwayUnclassified", XPlaneOptionsProvider.getOptions());
		
	}
	
	private Control createCheckboxAndBind(Composite parent, FormToolkit toolkit, String title, String property, Object bean) {
		Button button = toolkit.createButton(parent, title, SWT.CHECK);
		bindComponent(button, bean, property);
		return button;
	}

	@Override
	protected void initComponents() {
		// TODO Auto-generated method stub

	}

}
