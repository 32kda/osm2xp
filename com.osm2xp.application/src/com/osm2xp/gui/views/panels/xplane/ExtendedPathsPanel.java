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
import com.osm2xp.gui.views.panels.Osm2xpPanel;

public class ExtendedPathsPanel extends Osm2xpPanel {

	public ExtendedPathsPanel(Composite parent) {
		super(parent, SWT.NONE);
		setLayout(new GridLayout(2, false));
		FormToolkit toolkit = new FormToolkit(parent.getDisplay());
		toolkit.adapt(this);
		
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
		// Do nothing

	}

}
