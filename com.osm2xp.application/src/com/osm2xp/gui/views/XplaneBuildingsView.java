package com.osm2xp.gui.views;

import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.gui.views.panels.CheckBoxPanel;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.gui.views.panels.xplane.BuildingsHeightPanel;
import com.osm2xp.gui.views.panels.xplane.FacadeSetPanel;
import com.osm2xp.gui.views.panels.xplane.FacadesExclusionsPanel;
import com.osm2xp.gui.views.panels.xplane.FacadesRulesPanel;
import com.osm2xp.gui.views.panels.xplane.ObjectByPolyPanel;

/**
 * XplaneBuildingsView.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class XplaneBuildingsView extends AbstractOptionsView implements IContextProvider {

	public XplaneBuildingsView() {
		super("Building facades options", "images/toolbarsIcons/house_32.png");
	}

	@Override
	protected void createFormControls() {
		
		Section sectionGeneratedItems = createSection("Generated items", true);
		Osm2xpPanel scGeneratedItemsPanel = new CheckBoxPanel(sectionGeneratedItems) {
			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {				
				
				Button btnGenerateBuildings = new Button(this, SWT.CHECK);
				btnGenerateBuildings.setText("Generate Buildings(facades)");
				btnGenerateBuildings.setToolTipText("Generate buildings using X-Plane Facades feature. It allows to generate simple building model based on almost every contour on map.");
				GridDataFactory.fillDefaults().applyTo(btnGenerateBuildings);
				Button btnGenerateObjBuildings = new Button(this, SWT.CHECK);
				btnGenerateObjBuildings.setToolTipText("Generate buildings by choosing a model with closest size  from objects/<building type> folder");
				btnGenerateObjBuildings.setText("Generate Buildings(objects)");
				Button btnGenerateFence = new Button(this, SWT.CHECK);
				btnGenerateFence.setText("Generate Fence");
				GridDataFactory.fillDefaults().applyTo(btnGenerateFence);				
				Button btnGenerateTanks = new Button(this, SWT.CHECK);
				btnGenerateTanks.setText("Generate Tanks/Gasometers");
				btnGenerateTanks.setToolTipText("Generate Tanks/Gasometers using special facade");
				GridDataFactory.fillDefaults().applyTo(btnGenerateTanks);

				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateBuildings),		
						PojoProperties.value("generateBuildings").observe(XPlaneOptionsProvider.getOptions()));
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateObjBuildings),		
						PojoProperties.value("generateObjBuildings").observe(XPlaneOptionsProvider.getOptions()));
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateFence),		
						PojoProperties.value("generateFence").observe(XPlaneOptionsProvider.getOptions()));
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateTanks),		
						PojoProperties.value("generateTanks").observe(XPlaneOptionsProvider.getOptions()));
			}
			
		};
				
		toolkit.adapt(scGeneratedItemsPanel, true, true);
		sectionGeneratedItems.setClient(scGeneratedItemsPanel);
		
		/**
		 * Facade set
		 */

		Section sectionFacadeSets =  createSection("Facade sets", true);
		Osm2xpPanel facadeSetPanel = new FacadeSetPanel(sectionFacadeSets,
				SWT.BORDER);

		// display error if there is no facade set found
//		if (!new File(Osm2xpConstants.FACADES_SETS_PATH).exists()
//				|| (new File(Osm2xpConstants.FACADES_SETS_PATH).listFiles().length < 0)) {
//			form.setMessage("No facades sets found", IMessageProvider.ERROR);
//		}
		toolkit.adapt(facadeSetPanel, true, true);
		sectionFacadeSets.setClient(facadeSetPanel);

		/**
		 * Building height
		 */
		Section sectionBuildingHeight = createSection("Buildings height settings", false);
		BuildingsHeightPanel buildingsHeightPanel3 = new BuildingsHeightPanel(
				sectionBuildingHeight, SWT.BORDER);
		toolkit.adapt(buildingsHeightPanel3, true, true);
		sectionBuildingHeight.setClient(buildingsHeightPanel3);

		/**
		 * Facade exclusions
		 */

		Section sectionFacadeExclusions = createSection("Facade exclusions", false);
		FacadesExclusionsPanel facadesExclusionsPanel = new FacadesExclusionsPanel(
				sectionFacadeExclusions, SWT.BORDER);
		toolkit.adapt(facadesExclusionsPanel, true, true);
		sectionFacadeExclusions.setClient(facadesExclusionsPanel);

		/**
		 * Facade rules
		 */

		Section sectionFacadeRules = createSection("Facade rules", false);
		FacadesRulesPanel facadesRulesPanel = new FacadesRulesPanel(
				sectionFacadeRules, SWT.BORDER);
		toolkit.adapt(facadesRulesPanel, true, true);
		sectionFacadeRules.setClient(facadesRulesPanel);
		
		Section sectionBuildingObjectOptions = createSection("Object by polygon selection options",true);
		ObjectByPolyPanel panel = new ObjectByPolyPanel(sectionBuildingObjectOptions, SWT.BORDER);
		toolkit.adapt(panel, true, true);
		sectionBuildingObjectOptions.setClient(panel);
	}

	@Override
	public int getContextChangeMask() {
		return 0;
	}

	@Override
	public IContext getContext(Object target) {
		return HelpSystem.getContext("com.osm2xp.buildingsHelpContext");
	}

	@Override
	public String getSearchExpression(Object target) {
		return "buildings";
	}
}
