package com.osm2xp.gui.views.panels.xplane;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.gui.NumberVerifyListener;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

public class AdvancedNetworkPanel extends Osm2xpPanel {

	public AdvancedNetworkPanel(Composite parent) {
		super(parent, SWT.NONE);
		setLayout(new GridLayout(2, false));
		FormToolkit toolkit = new FormToolkit(parent.getDisplay());
		toolkit.adapt(this);
		
		Group bridgeGrp = new Group(this, SWT.NONE);
		bridgeGrp.setText("Bridge generation settings");
		bridgeGrp.setLayout(new GridLayout(2, false));
		Label roadRampLenLbl = toolkit.createLabel(bridgeGrp, "Road bridge ramp lenth");
		roadRampLenLbl.setToolTipText("Controls, how long, in meters, should be a ramp near the bridge");
		GridDataFactory.swtDefaults().applyTo(roadRampLenLbl);
		
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		Text roadRampLenTxt = new Text(bridgeGrp, SWT.BORDER);
		GridDataFactory.swtDefaults().applyTo(roadRampLenTxt);
		roadRampLenTxt.addVerifyListener(new NumberVerifyListener());
		bindTextToInt(roadRampLenTxt, options, "roadBridgeRampLen");
		
		Label railRampLenLbl = toolkit.createLabel(bridgeGrp, "Railway bridge ramp lуngth");
		railRampLenLbl.setToolTipText("Controls how long, in meters, should be a ramp near the bridge");
		GridDataFactory.swtDefaults().applyTo(railRampLenLbl);
		
		Text railRampLenTxt = new Text(bridgeGrp, SWT.BORDER);
		GridDataFactory.swtDefaults().applyTo(railRampLenTxt);
		railRampLenTxt.addVerifyListener(new NumberVerifyListener());
		bindTextToInt(railRampLenTxt, options,"railBridgeRampLen");
		
	}

	@Override
	protected void initComponents() {
		// TODO Auto-generated method stub
		
	}
	
	

}
