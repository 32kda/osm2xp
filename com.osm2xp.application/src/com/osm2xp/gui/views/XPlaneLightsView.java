package com.osm2xp.gui.views;

import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.gui.views.panels.CheckBoxPanel;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.utils.ui.UiUtil;

/**
 * XPlaneLightsView.
 * 
 * @author Benjamin Blanchet, Dmitry Karpenko
 * 
 */
public class XPlaneLightsView extends AbstractOptionsView implements IContextProvider {
	
	private Button btnGenerateLights;

	public XPlaneLightsView() {
		super("Street lights options", "images/toolbarsIcons/light_32.png");
	}
	
	@Override
	protected void createFormControls() {
		/**
		 * lights
		 */
		
		Section sectionGeneratedItems = createSection("Generated items", true);
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		Osm2xpPanel scGeneratedItemsPanel = new CheckBoxPanel(sectionGeneratedItems) {

			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {
				btnGenerateLights = new Button(this, SWT.CHECK);
				btnGenerateLights.setText("Generate Street Lights (using objects/object strings)");
				GridDataFactory.fillDefaults().applyTo(btnGenerateLights);
				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateLights),
						PojoProperties.value("generateStreetLights").observe(options));
				
				toolkit.createLabel(this, "Choose this option if you want to generate separate lights for no-light roads.");
			}
			
			@Override
			protected void initLayout() {
				GridLayout gridLayout = new GridLayout(1, false);
				gridLayout.verticalSpacing = 15;
				gridLayout.horizontalSpacing = 15;
				gridLayout.marginHeight = 15;
				setLayout(gridLayout);
			}
		};
		toolkit.adapt(scGeneratedItemsPanel, true, true);
		sectionGeneratedItems.setClient(scGeneratedItemsPanel);

		Section sectionLights = createSection("Lights (by Object Strings) properties", true);
//		XplaneLightsRulesPanel lightsPanel = new XplaneLightsRulesPanel( //Old Light s props panel
//				sectionLights, SWT.BORDER);
		
		Osm2xpPanel lightsPanel = new Osm2xpPanel(sectionLights, SWT.BORDER) {
			
			@SuppressWarnings("unchecked")
			@Override
			protected void initComponents() {
				GridLayout gridLayout = new GridLayout(2, false);
				gridLayout.verticalSpacing = 15;
				gridLayout.horizontalSpacing = 15;
				gridLayout.marginHeight = 15;
				setLayout(gridLayout);
				
				toolkit.createLabel(this,"Light Object String (.str) path").setLayoutData(GridDataFactory.swtDefaults().create());
				Text objectPathText = new Text(this, SWT.BORDER);
				GridDataFactory.fillDefaults().grab(true,false).applyTo(objectPathText);
				bindComponent(objectPathText, options, "lightObjectString");
				
				Button btnGenerateHighwayLights = new Button(this, SWT.CHECK);
				btnGenerateHighwayLights.setText("Generate higway lights");
				btnGenerateHighwayLights.setToolTipText("Generate lights for highway roads (highway=motorway or highway=trunk)");
				GridDataFactory.fillDefaults().span(2,1).applyTo(btnGenerateHighwayLights);

				bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateHighwayLights),		
						PojoProperties.value("generateHighwayLights").observe(options));
				
				toolkit.createLabel(this,"Street Lights interval, m").setLayoutData(GridDataFactory.swtDefaults().create());
				Text lightsInterval = new Text(this, SWT.BORDER);
				lightsInterval.addVerifyListener(onlyDigitsVerifyListener);
				bindTextToInt(lightsInterval, options, "streetLightsInterval");
				
				toolkit.createLabel(this,"Road lane width, m").setLayoutData(GridDataFactory.swtDefaults().create());
				Spinner roadLaneWidth = new Spinner(this, SWT.BORDER);
				roadLaneWidth.setDigits(1);
				bindSpinnerToDouble(roadLaneWidth, GlobalOptionsProvider.getOptions(), "roadLaneWidth",1);
			}
		};
		
		toolkit.adapt(lightsPanel, true, true);
		sectionLights.setClient(lightsPanel);
		
		UiUtil.setEnabledRecursive(sectionLights, options.isGenerateStreetLights());
		btnGenerateLights.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				UiUtil.setEnabledRecursive(sectionLights, btnGenerateLights.getSelection());
			}
			
		});
		btnGenerateLights.setSelection(options.isGenerateStreetLights());
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
