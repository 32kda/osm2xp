package com.osm2xp.gui.views.panels.xplane;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.PojoObservables;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.osm2xp.constants.Osm2xpConstants;
import com.osm2xp.constants.Perspectives;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;

/**
 * SceneryExclusionsPanel.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class SceneryExclusionsPanel extends Composite {

	public SceneryExclusionsPanel(final Composite parent, final int style) {
		super(parent, style);
		DataBindingContext bindingContext = new DataBindingContext();
		GridLayout gridLayout = new GridLayout(2, true);
		gridLayout.horizontalSpacing = 15;
		gridLayout.verticalSpacing = 15;
		setLayout(gridLayout);
		Button btnCheckExcludeLin = new Button(this, SWT.CHECK);
		btnCheckExcludeLin.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnCheckExcludeLin.setText("Exclude Lines");
		bindingContext.bindValue(SWTObservables
				.observeSelection(btnCheckExcludeLin), PojoObservables
				.observeValue(XplaneOptionsHelper.getOptions(), "excludeLin"));

		Button btnCheckExcludeFor = new Button(this, SWT.CHECK);
		btnCheckExcludeFor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnCheckExcludeFor.setText("Exclude Forests");
		bindingContext.bindValue(SWTObservables
				.observeSelection(btnCheckExcludeFor), PojoObservables
				.observeValue(XplaneOptionsHelper.getOptions(), "excludeFor"));

		Button btnCheckExcludeObj = new Button(this, SWT.CHECK);
		btnCheckExcludeObj.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnCheckExcludeObj.setText("Exclude Objects");
		bindingContext.bindValue(SWTObservables
				.observeSelection(btnCheckExcludeObj), PojoObservables
				.observeValue(XplaneOptionsHelper.getOptions(), "excludeObj"));

		Button btnCheckExcludeStr = new Button(this, SWT.CHECK);
		btnCheckExcludeStr.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnCheckExcludeStr.setText("Exclude Strings");
		bindingContext.bindValue(SWTObservables
				.observeSelection(btnCheckExcludeStr), PojoObservables
				.observeValue(XplaneOptionsHelper.getOptions(), "excludeStr"));

		Button btnCheckExcludeFac = new Button(this, SWT.CHECK);
		btnCheckExcludeFac.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnCheckExcludeFac.setText("Exclude Facades");
		bindingContext.bindValue(SWTObservables
				.observeSelection(btnCheckExcludeFac), PojoObservables
				.observeValue(XplaneOptionsHelper.getOptions(), "excludeFac"));

		Button btnCheckExcludeNet = new Button(this, SWT.CHECK);
		btnCheckExcludeNet.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				false, false, 1, 1));
		btnCheckExcludeNet.setText("Exclude Network");
		bindingContext.bindValue(SWTObservables
				.observeSelection(btnCheckExcludeNet), PojoObservables
				.observeValue(XplaneOptionsHelper.getOptions(), "excludeNet"));

		if (GuiOptionsHelper.getOptions().getOutputFormat()
				.equals(Perspectives.PERSPECTIVE_XPLANE10)) {
			Button btnCheckExcludeBch = new Button(this, SWT.CHECK);
			btnCheckExcludeBch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
					false, false, 1, 1));
			btnCheckExcludeBch.setText("Exclude Bch");
			bindingContext.bindValue(
					SWTObservables.observeSelection(btnCheckExcludeBch),
					PojoObservables.observeValue(
							XplaneOptionsHelper.getOptions(), "excludeBch"));

			Button btnCheckExcludePol = new Button(this, SWT.CHECK);
			btnCheckExcludePol.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
					false, false, 1, 1));
			btnCheckExcludePol.setText("Exclude Pol");
			bindingContext.bindValue(
					SWTObservables.observeSelection(btnCheckExcludePol),
					PojoObservables.observeValue(
							XplaneOptionsHelper.getOptions(), "excludePol"));
		}

	}
}
