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
				group.setText("Use regular or EU road network definitions?");
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
		Osm2xpPanel scRoadsPanel = new Osm2xpPanel(sectionRoadProperties, SWT.BORDER) {
			
			@Override
			protected void initComponents() {
				GridLayout gridLayout = new GridLayout(4, false);
				gridLayout.verticalSpacing = 15;
				gridLayout.horizontalSpacing = 15;
				gridLayout.marginHeight = 15;
				setLayout(gridLayout);
				
				toolkit.createLabel(this,"City, 3+ lanes highway road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text city3LaneHighwayRoadType = new Text(this, SWT.BORDER);
				city3LaneHighwayRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(city3LaneHighwayRoadType, XPlaneOptionsProvider.getOptions(), "city3LaneHighwayRoadType");
				
				toolkit.createLabel(this,"Country, 3+ lanes highway road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text country3LaneHighwayRoadType = new Text(this, SWT.BORDER);
				country3LaneHighwayRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(country3LaneHighwayRoadType, XPlaneOptionsProvider.getOptions(), "country3LaneHighwayRoadType");
				
				toolkit.createLabel(this,"City, 2 lanes highway road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text city2LaneHighwayRoadType = new Text(this, SWT.BORDER);
				city2LaneHighwayRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(city2LaneHighwayRoadType, XPlaneOptionsProvider.getOptions(), "city2LaneHighwayRoadType");
				
				toolkit.createLabel(this,"Country, 2 lanes highway road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text country2LaneHighwayRoadType = new Text(this, SWT.BORDER);
				country2LaneHighwayRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(country2LaneHighwayRoadType, XPlaneOptionsProvider.getOptions(), "country2LaneHighwayRoadType");
				
				toolkit.createLabel(this,"City, 2 lanes, default road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text cityRoadType = new Text(this, SWT.BORDER);
				cityRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(cityRoadType, XPlaneOptionsProvider.getOptions(), "cityRoadType");
				
				toolkit.createLabel(this,"Country, 2 lanes, default road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text countryRoadType = new Text(this, SWT.BORDER);
				countryRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(countryRoadType, XPlaneOptionsProvider.getOptions(), "countryRoadType");
				
				toolkit.createLabel(this,"One lane road type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text oneLaneRoadType = new Text(this, SWT.BORDER);
				oneLaneRoadType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(oneLaneRoadType, XPlaneOptionsProvider.getOptions(), "oneLaneRoadType");
				
				toolkit.createLabel(this,"Railway type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text railwayType = new Text(this, SWT.BORDER);
				railwayType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(railwayType, XPlaneOptionsProvider.getOptions(), "railwayType");
				
				toolkit.createLabel(this,"Powerline type").setLayoutData(GridDataFactory.swtDefaults().create());
				Text powerlineType = new Text(this, SWT.BORDER);
				powerlineType.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(powerlineType, XPlaneOptionsProvider.getOptions(), "powerlineType");
			}
			
		};
		
		toolkit.adapt(scRoadsPanel, true, true);
		sectionRoadProperties.setClient(scRoadsPanel);
	}
}
