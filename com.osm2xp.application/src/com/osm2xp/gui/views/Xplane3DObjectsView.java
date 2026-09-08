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
import com.osm2xp.gui.views.panels.xplane.ObjectOptionsPanel;
import com.osm2xp.gui.views.panels.xplane.XplaneObjectsRulesPanel;

/**
 * Xplane3DObjectsView.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class Xplane3DObjectsView extends AbstractOptionsView implements IContextProvider {

	public Xplane3DObjectsView() {
		super("3D Objects options", "images/toolbarsIcons/objects_32.png");
	}
	
	@Override
	protected void createFormControls() {
		
		Section sectionGeneratedItems = createSection("Generated items", true);
		Osm2xpPanel scGeneratedItemsPanel = new CheckBoxPanel(sectionGeneratedItems) {
			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {				
				Button btnGenerateObjects = new Button(this, SWT.CHECK);
				btnGenerateObjects.setText("Generate 3D objects");
				btnGenerateObjects.setToolTipText("Insert 3D objects into scenery based on the rules specified on the 3D Objects tab");
				GridDataFactory.fillDefaults().applyTo(btnGenerateObjects);
				Button btnGenerateChimneys = new Button(this, SWT.CHECK);
				btnGenerateChimneys.setText("Generate Chimneys");
				btnGenerateChimneys.setToolTipText("Generate Chimneys by selecting best-fit model");
				GridDataFactory.fillDefaults().applyTo(btnGenerateChimneys);				
				Button btnGenerateCoolingTowers = new Button(this, SWT.CHECK);
				btnGenerateCoolingTowers.setText("Generate Cooling Towers");
				btnGenerateCoolingTowers.setToolTipText("Generate Cooling Towers by selecting best-fit model");
				GridDataFactory.fillDefaults().applyTo(btnGenerateCoolingTowers);

				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateObjects),		
						PojoProperties.value("generateObj").observe(XPlaneOptionsProvider.getOptions()));
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateChimneys),		
						PojoProperties.value("generateChimneys").observe(XPlaneOptionsProvider.getOptions()));
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateCoolingTowers),		
						PojoProperties.value("generateCoolingTowers").observe(XPlaneOptionsProvider.getOptions()));
			}
			
		};
		toolkit.adapt(scGeneratedItemsPanel, true, true);
		sectionGeneratedItems.setClient(scGeneratedItemsPanel);
		
		/**
		 * objects rules
		 */

		Section sectionObjectsRules = createSection("Object generation rules",true);
		XplaneObjectsRulesPanel objectsRulesPanel = new XplaneObjectsRulesPanel(
				sectionObjectsRules, SWT.BORDER);
		toolkit.adapt(objectsRulesPanel, true, true);
		sectionObjectsRules.setClient(objectsRulesPanel);
		
		Section sectionGlobalObjectOptions = createSection("Global object placement options", true);
		ObjectOptionsPanel objectOptionsPanel = new ObjectOptionsPanel(sectionGlobalObjectOptions, SWT.BORDER);
		toolkit.adapt(objectOptionsPanel, true, true);
		sectionGlobalObjectOptions.setClient(objectOptionsPanel);
		
		
	}

	@Override
	public int getContextChangeMask() {
		return 0;
	}

	@Override
	public IContext getContext(Object target) {
		return HelpSystem.getContext("com.osm2xp.objectsHelpContext");
	}

	@Override
	public String getSearchExpression(Object target) {
		return "object";
	}

}
