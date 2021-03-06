package com.osm2xp.gui.views.panels.xplane;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.gui.Activator;
import com.osm2xp.gui.dialogs.FacadeSetEditorDialog;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

/**
 * FacadeSetPanel.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class FacadeSetPanel extends Osm2xpPanel {

//	private Combo comboFacade;
//	private Label lblFacadeSet;
//	private GridData gridFacade;
	private Spinner spinnerLod;
	private Button btnLod;
	private Button btnSlopedRoofs;
	private Button btnHardBuildings;
	private ListViewer facadeListViewer;
	private List<String> facadeSets;
	private Spinner spinnerRenderLevel;

	public FacadeSetPanel(final Composite parent, final int style) {
		super(parent, style);
	}

	@Override
	protected void initLayout() {
		final GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.marginHeight = 15;
		gridLayout.horizontalSpacing = 15;
		gridLayout.verticalSpacing = 15;
		setLayout(gridLayout);
		GridDataFactory.fillDefaults().applyTo(this);
		
//		btnSlopedRoofs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false,
//				true, 1, 1));
//		btnHardBuildings.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true,
//				false, 1, 1));
	} 

	@Override
	protected void initComponents() {
		facadeListViewer = new ListViewer(this, SWT.BORDER);
		PixelConverter converter = new PixelConverter(facadeListViewer.getControl());
		GridDataFactory.fillDefaults().hint(converter.convertWidthInCharsToPixels(100),SWT.DEFAULT).span(1,4).applyTo(facadeListViewer.getControl());
		facadeListViewer.setContentProvider(new ArrayContentProvider());
		facadeListViewer.setLabelProvider(new LabelProvider());
		
		Button addButton = new Button(this, SWT.PUSH);
		addButton.setImage(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/add.png").createImage());
		addButton.setToolTipText("Add facade set by descriptor");
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doAddFacade();
			}
		});
		Button addFolderButton = new Button(this, SWT.PUSH);
		addFolderButton.setImage(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/add_folder.png").createImage());
		addFolderButton.setToolTipText("Add folder with facades");
		addFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doAddFacadeFolder();
			}
		});
		Button editButton = new Button(this, SWT.PUSH);
		editButton.setImage(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/edit.gif").createImage());
		editButton.setToolTipText("Edit facade set");
		editButton.setEnabled(false);
		editButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doEditFacadeSet();
			}
		});
		Button removeButton = new Button(this, SWT.PUSH);
		removeButton.setImage(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/remove.gif").createImage());
		removeButton.setToolTipText("Remove selected facade set");
		removeButton.setEnabled(false);
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRemoveSelected();
			}
		});
		facadeListViewer.addSelectionChangedListener(event -> {
			boolean hasSelection = !event.getSelection().isEmpty();
			removeButton.setEnabled(hasSelection);
			editButton.setEnabled(hasSelection);
		});
		facadeListViewer.addDoubleClickListener(e -> {
			if (facadeListViewer.getSelection().isEmpty()) {
				doAddFacade();
			} else {
				doEditFacadeSet();
			}
		});
		
//		lblFacadeSet = new Label(this, SWT.NONE);
//		lblFacadeSet.setText("Facade set : ");
//		comboFacade = new Combo(this, SWT.READ_ONLY);
//		gridFacade = new GridData(SWT.LEFT, SWT.CENTER, true, true, 1, 1);
//		gridFacade.minimumWidth = 200;
		
		Group optionsGroup = new Group(this, SWT.NONE);
		optionsGroup.setText("Options");
		optionsGroup.setLayoutData(GridDataFactory.fillDefaults().span(2,1). create());
		optionsGroup.setLayout(new GridLayout(2,false));
		
		btnLod = new Button(optionsGroup, SWT.CHECK);
		GridDataFactory.swtDefaults().span(2,1).applyTo(btnLod);
		btnLod.setText("Restrict Facade LOD");
		
		
		final Label lblLod = new Label(optionsGroup, SWT.NONE);
		lblLod.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1,
				1));
		lblLod.setText("L.O.D :");

		spinnerLod = new Spinner(optionsGroup, SWT.BORDER);
		spinnerLod.setMaximum(100000);
		spinnerLod.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, true,
				1, 1));
		
		boolean restrict = XPlaneOptionsProvider.getOptions().isRestrictFacadeLod();
		enableComponents(restrict, lblLod, spinnerLod);
		spinnerLod.setEnabled(restrict);
		lblLod.setEnabled(restrict);
		
		btnLod.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				enableComponents(btnLod.getSelection(), lblLod, spinnerLod);
			}
			
		});
		
		final Label lblRenderLevel = new Label(optionsGroup, SWT.NONE);
		lblRenderLevel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1,
				1));
		lblRenderLevel.setText("Render level for facades :");

		spinnerRenderLevel = new Spinner(optionsGroup, SWT.BORDER);
		spinnerRenderLevel.setMinimum(1);
		spinnerRenderLevel.setMaximum(6);
		spinnerRenderLevel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, true,
				1, 1));
		
//		String[] items = null;
//		try {
//			items = FilesUtils.listFacadesSets().toArray(new String[] {});
//			comboFacade.setItems(items);
//		} catch (Osm2xpBusinessException e) {
//			Osm2xpLogger.error("Error getting facades sets list", e);
//		}
//
//		if (XPlaneOptionsProvider.getOptions().getFacadeSet() != null) {
//			comboFacade
//					.setText(XPlaneOptionsProvider.getOptions().getFacadeSet());
//		}
		btnSlopedRoofs = new Button(optionsGroup, SWT.CHECK);
		btnSlopedRoofs.setText("Sloped roofs");
		GridDataFactory.swtDefaults().span(2,1).applyTo(btnSlopedRoofs);
		btnHardBuildings = new Button(optionsGroup, SWT.CHECK);
		btnHardBuildings.setText("Hard buildings");
		GridDataFactory.swtDefaults().span(2,1).applyTo(btnHardBuildings);
		
		loadSets();
	}
	
	protected void doRemoveSelected() {
		ISelection selection = facadeListViewer.getSelection();
		if (!selection.isEmpty()) {
			Object firstElement = ((StructuredSelection) selection).getFirstElement();
			facadeSets.remove(firstElement);
			persistFacadeSets();
			refreshViewer();
			if (!facadeSets.isEmpty()) {
				facadeListViewer.setSelection(new StructuredSelection(facadeSets.get(0)));
			} else {
				facadeListViewer.setSelection(new StructuredSelection());
			}
		}
	}
	
	protected void doEditFacadeSet() {
		ISelection selection = facadeListViewer.getSelection();
		if (!selection.isEmpty()) {
			Object firstElement = ((StructuredSelection) selection).getFirstElement();
			FacadeSetEditorDialog.editFacadeSet(firstElement.toString());
		}
	}

	protected void refreshViewer() {
		facadeListViewer.setInput(facadeSets.toArray());
	}

	protected void persistFacadeSets() {
		String facadesStr = facadeSets.stream().collect(Collectors.joining(File.pathSeparator));
		XPlaneOptionsProvider.getOptions().setFacadeSets(facadesStr);
//		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
//		node.put(FacadeSetManager.FACADE_SETS_PROP, facadesStr);
//		try {
//			node.flush();
//		} catch (BackingStoreException e) {
//			Activator.log(e);
//		}
	}

	protected void loadSets() {
		String setsStr = XPlaneOptionsProvider.getOptions().getFacadeSets();
		if (StringUtils.isEmpty(setsStr)) {
			setsStr = XPlaneOptionsProvider.getDefaultFacadeSets();
			XPlaneOptionsProvider.getOptions().setFacadeSets(setsStr);
		}
		String[] facades = setsStr.split(File.pathSeparator);
		facadeSets = new ArrayList<>();
		facadeSets.addAll(Arrays.asList(facades).stream().filter(str -> !str.trim().isEmpty()).collect(Collectors.toList()));
		refreshViewer();
	}

	protected void doAddFacadeFolder() {
		DirectoryDialog dialog = new DirectoryDialog(getShell());
		String result = dialog.open();
		if (result != null) {
			facadeSets.add(result);
			persistFacadeSets();
			refreshViewer();
		}
	}

	protected void doAddFacade() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setFilterExtensions(new String[] {"osm2xpFacadeSetDescriptor.xml"});
		String result = dialog.open();
		if (result != null) {
			facadeSets.add(result);
			persistFacadeSets();
			refreshViewer();
		}
	}

	@Override
	protected void bindComponents() {
		bindComponent(btnLod, XPlaneOptionsProvider.getOptions(), "restrictFacadeLod");
		bindComponent(spinnerLod, XPlaneOptionsProvider.getOptions(), "facadeLod");
		bindComponent(spinnerRenderLevel, XPlaneOptionsProvider.getOptions(), "facadeRenderLevel");
		bindComponent(btnSlopedRoofs, XPlaneOptionsProvider.getOptions(),
				"generateSlopedRoofs");
		bindComponent(btnHardBuildings, XPlaneOptionsProvider.getOptions(),
				"hardBuildings");
	}

	@Override
	protected void addComponentsListeners() {
	}
}
