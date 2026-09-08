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
import org.eclipse.ui.forms.widgets.TableWrapData;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.gui.views.panels.CheckBoxPanel;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.gui.views.panels.xplane.ForestsRulesPanel;

/**
 * XplaneForestsView.
 * 
 * @author Benjamin Blanchet
 * 
 */

public class XplaneForestsView extends AbstractOptionsView implements IContextProvider {

	public XplaneForestsView() {
		super("Forests options","images/toolbarsIcons/leaf_32.png");
	}
	
	@Override
	protected void createFormControls() {
		
		Section sectionGeneratedItems = createSection("Generated items", true);
		Osm2xpPanel scGeneratedItemsPanel = new CheckBoxPanel(sectionGeneratedItems) {
			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {
				Button btnGenerateForests = new Button(this, SWT.CHECK);
				btnGenerateForests.setText("Generate Forests");
				GridDataFactory.fillDefaults().applyTo(btnGenerateForests);
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateForests),
						PojoProperties.value("generateFor").observe(XPlaneOptionsProvider.getOptions()));
			}
		};
		toolkit.adapt(scGeneratedItemsPanel, true, true);
		sectionGeneratedItems.setClient(scGeneratedItemsPanel);
		/**
		 * Forests Rules
		 */

		Section sectionForestRules = toolkit.createSection(form.getBody(),
				Section.TWISTIE | Section.EXPANDED | Section.TITLE_BAR);
		sectionForestRules.setLayoutData(new TableWrapData(
				TableWrapData.FILL_GRAB, TableWrapData.TOP, 1, 1));
		sectionForestRules.setText("Forests rules");
		ForestsRulesPanel forestsRulesPanel = new ForestsRulesPanel(
				sectionForestRules, SWT.BORDER);
		toolkit.adapt(forestsRulesPanel, true, true);
		sectionForestRules.setClient(forestsRulesPanel);
	}

	@Override
	public int getContextChangeMask() {
		return 0;
	}

	@Override
	public IContext getContext(Object target) {
		return HelpSystem.getContext("com.osm2xp.forestsHelpContext");
	}

	@Override
	public String getSearchExpression(Object target) {
		return "forest";
	}
}