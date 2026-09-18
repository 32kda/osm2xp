package com.osm2xp.gui.views;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;

import com.osm2xp.gui.views.panels.flightGear.FlightGearObjectsPanel;
import com.osm2xp.gui.views.panels.flightGear.FlightGearObjectsRulesPanel;

/**
 * FlightGear3DObjectsView.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class FlightGear3DObjectsView extends AbstractOptionsView implements
		IContextProvider {

	public FlightGear3DObjectsView() {
		super("3D Objects options", "images/toolbarsIcons/objects_32.png");
	}

	@Override
	protected void createFormControls() {
		Section sectionGeneratedItems = createSection("Generated items", true);
		FlightGearObjectsPanel generatedItemsPanel = new FlightGearObjectsPanel(sectionGeneratedItems, SWT.BORDER);
		toolkit.adapt(generatedItemsPanel, true, true);
		sectionGeneratedItems.setClient(generatedItemsPanel);

		Section sectionObjectsRules = toolkit.createSection(form.getBody(),
				Section.TWISTIE | Section.EXPANDED | Section.TITLE_BAR);
		sectionObjectsRules.setLayoutData(new TableWrapData(
				TableWrapData.FILL_GRAB, TableWrapData.TOP, 1, 1));

		sectionObjectsRules.setText("objects rules");
		FlightGearObjectsRulesPanel objectsRulesPanel = new FlightGearObjectsRulesPanel(
				sectionObjectsRules, SWT.BORDER);
		toolkit.adapt(objectsRulesPanel, true, true);
		sectionObjectsRules.setClient(objectsRulesPanel);
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
