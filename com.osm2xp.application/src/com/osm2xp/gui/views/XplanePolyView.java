package com.osm2xp.gui.views;

import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.gui.views.panels.CheckBoxPanel;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.gui.views.panels.xplane.XplanePolyRulesPanel;

/**
 * Xplane Dpared poly configuration view
 * 
 * @author 32kda
 * 
 */
public class XplanePolyView extends AbstractOptionsView {
	
	public static final String ID = "com.osm2xp.viewPolysTab";

	public XplanePolyView() {
		super("Draped poly options", "images/toolbarsIcons/polygon_32.png");
	}
	
	@Override
	protected void createFormControls() {
		
		Section sectionGeneratedItems = createSection("Generated items", true);
		Osm2xpPanel scGeneratedItemsPanel = new CheckBoxPanel(sectionGeneratedItems) {
			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {
				Button btnGeneratePolys = new Button(this, SWT.CHECK);
				btnGeneratePolys.setText("Generate Draped Polygons");
				btnGeneratePolys.setToolTipText("Generate draped polygons - for items like parking areas and other paved surfaces");
				GridDataFactory.fillDefaults().applyTo(btnGeneratePolys);
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGeneratePolys),		
						PojoProperties.value("generatePolys").observe(XPlaneOptionsProvider.getOptions()));
			}
		};
		toolkit.adapt(scGeneratedItemsPanel, true, true);
		sectionGeneratedItems.setClient(scGeneratedItemsPanel);
		
		/**
		 * Poly rules
		 */

		Section sectionRules = toolkit.createSection(form.getBody(),
				Section.TWISTIE | Section.EXPANDED | Section.TITLE_BAR);
		sectionRules.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB, TableWrapData.FILL_GRAB));
		sectionRules.setText("Draped poly rules");
		XplanePolyRulesPanel rulesPanel = new XplanePolyRulesPanel(sectionRules, SWT.BORDER);
		toolkit.adapt(rulesPanel, true, true);
		sectionRules.setClient(rulesPanel);
	}

}
