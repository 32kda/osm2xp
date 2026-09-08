package com.osm2xp.gui.views.panels.xplane;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.osm2xp.generation.options.XPlaneOptionsProvider;

/**
 * DebugOptionsPanel
 * 
 * @author 32kda
 * 
 */
public class DebugOptionsPanel extends Composite {

	@SuppressWarnings("unchecked")
	public DebugOptionsPanel(final Composite parent, final int style) {
		super(parent, style);
		DataBindingContext bindingContext = new DataBindingContext();
		GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.verticalSpacing = 15;
		gridLayout.horizontalSpacing = 15;
		gridLayout.marginHeight = 15;
		setLayout(gridLayout);
		
		Button btnGenerateDebugImg = new Button(this, SWT.CHECK);
		btnGenerateDebugImg.setLayoutData(createLayoutData());
		btnGenerateDebugImg.setText("Generate Debug images");
		btnGenerateDebugImg.setToolTipText("Generate 2048x2048 image for each tile, using first encountered object as top-left corner. "
				+ "Generated buildings/objects etc. are marked on it using scale 1px = 1m");
		bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateDebugImg),		
				PojoProperties.value("generateDebugImg").observe(XPlaneOptionsProvider.getOptions()));
		
		Button btnDeleteSrcFiles = new Button(this, SWT.CHECK);
		btnDeleteSrcFiles.setLayoutData(createLayoutData());
		btnDeleteSrcFiles.setText("Delete generated txt files when done");
		btnDeleteSrcFiles.setToolTipText("Delete generated .txt files after packing them. Switch this off for debug/");
		bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnDeleteSrcFiles),		
				PojoProperties.value("deleteSrc").observe(XPlaneOptionsProvider.getOptions()));

		
		Button btnGenerateComments = new Button(this, SWT.CHECK);
		btnGenerateComments.setLayoutData(createLayoutData());
		btnGenerateComments.setText("Generate DSF comments");
		btnGenerateComments.setToolTipText("Generate comments in created DSF files");
		bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnGenerateComments),		
				PojoProperties.value("generateComments").observe(XPlaneOptionsProvider.getOptions()));
	}

	protected GridData createLayoutData() {
		return new GridData(SWT.LEFT, SWT.TOP,
				false, false, 1, 1);
	}

}
