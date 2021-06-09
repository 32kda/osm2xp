package com.osm2xp.gui.views;

import org.eclipse.core.databinding.beans.PojoProperties;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.gui.views.panels.CheckBoxPanel;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.gui.views.panels.xplane.ExtendedPathsPanel;

public class XPlaneNetworkView extends AbstractOptionsView {
	
	public XPlaneNetworkView() {
		super("Roads/Network", "images/toolbarsIcons/road_32.png");
	}	

	@Override
	protected void createFormControls() {
		/**
		 * Generated items
		 */
		Section sectionGeneratedItems = createSection("Generated items", true);
		Osm2xpPanel scGeneratedItemsPanel = new CheckBoxPanel(sectionGeneratedItems) {
			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {
				
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
				
				Button btnGenerateRoads = new Button(this, SWT.CHECK);
				btnGenerateRoads.setText("Generate Roads");
				GridDataFactory.fillDefaults().applyTo(btnGenerateRoads);
				Button btnGenerateRail = new Button(this, SWT.CHECK);
				btnGenerateRail.setText("Generate Railways");
				GridDataFactory.fillDefaults().applyTo(btnGenerateRail);
				Button btnGeneratePower = new Button(this, SWT.CHECK);
				btnGeneratePower.setText("Generate Powerlines");
				GridDataFactory.fillDefaults().applyTo(btnGeneratePower);
				
				Button btnGenerateBridges = new Button(this, SWT.CHECK);
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
			}
			
		};
				
		toolkit.adapt(scGeneratedItemsPanel, true, true);
		sectionGeneratedItems.setClient(scGeneratedItemsPanel);
		
		Section sectionRoadProperties = createSection("Types for roads/railways/powerlines", true);
		ExtendedPathsPanel extendedPathsPanel = new ExtendedPathsPanel(sectionRoadProperties);		
		
		toolkit.adapt(extendedPathsPanel, true, true);
		sectionRoadProperties.setClient(extendedPathsPanel);
	}
}
