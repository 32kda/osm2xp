package com.osm2xp.gui.views.panels.generic;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import math.geom2d.Point2D;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPartSite;

import com.osm2xp.gui.views.LastFilesView;
import com.osm2xp.gui.views.panels.Osm2xpPanel;
import com.osm2xp.utils.helpers.GuiOptionsHelper;

/**
 * SceneryFilePanel.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class SceneryFilePanel extends Osm2xpPanel {

	private static final String[] FILTER_NAMES = { "OSM files (*.osm,*.pbf;*.shp)" };
	private static final String[] FILTER_EXTS = { "*.osm;*.pbf;*.shp" };
	private Text textInputSceneName;
	private Button btnGenerateAllTiles;
	private Spinner spinnerLatitude;
	private Spinner spinnerLongitude;
	private Button btnBrowse;
	private Label lblSceneName;
	private GridData gridInputSceneName;
	private Group grpCoordinates;
	private GridData gridCoordinates;
	private IWorkbenchPartSite partSite;
	private ISelectionListener selectionListener = (part, selection) -> {
		if (selection instanceof StructuredSelection &&
				!selection.isEmpty()) {
			Object firstElement = ((StructuredSelection) selection).getFirstElement();
			if (firstElement instanceof String) {
				refreshCurrentFilePath((String) firstElement);
			}
			
		}
	};
	private Text filenameText;

	public SceneryFilePanel(final Composite parent, int style, IWorkbenchPartSite partSite) {
		super(parent, SWT.BORDER);
		this.partSite = partSite;
		partSite.getWorkbenchWindow().getSelectionService().addSelectionListener(selectionListener);
	}

//	private boolean isValidPbf(File file) {
//		if (!file.isFile()) {
//			return false;
//		}
//		String name = file.getName();
//		for (String ext: FILTER_EXTS) {
//			if (name.endsWith(ext)) {
//				return true;
//			}
//		}
//		return false;
//	}

	/**
	 * Construct scene name
	 * 
	 * @return String the scene name
	 */
	private String computeSceneName() {
		StringBuilder sceneName = new StringBuilder();
		// clean file extension
		File file = new File(GuiOptionsHelper.getOptions().getCurrentFilePath());
		String fileName = file.getName().substring(0,
				file.getName().indexOf("."));
		sceneName.append(fileName);
		if (GuiOptionsHelper.getOptions().isAppendHour()) {
			DateFormat dateFormat = new SimpleDateFormat("_dd_MM_yy_HH'_'mm");
			sceneName.append(dateFormat.format(new Date()));
		}
		return sceneName.toString();

	}

	public void refreshCurrentFilePath(String path) {
		if (!filenameText.isDisposed()) {
			filenameText.setText(path);	
		}
//		filenameText.setText(GuiOptionsHelper.getOptions()
//				.getCurrentFilePath());
//		String sceneName = computeSceneName();
//		GuiOptionsHelper.setSceneName(sceneName);
//		textInputSceneName.setText(sceneName);
	}

	@Override
	protected void initLayout() {
		GridLayout gridLayout = new GridLayout(3, false);
		gridLayout.verticalSpacing = 15;
		gridLayout.horizontalSpacing = 15;
		setLayout(gridLayout);
	}

	@Override
	protected void initComponents() {

		Label labelInputSceneFile = new Label(this, SWT.NONE);
		labelInputSceneFile.setText("Scene file:");
		
		filenameText = new Text(this, SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true,false).applyTo(filenameText);
		String currentFilePath = GuiOptionsHelper.getOptions().getCurrentFilePath();
		filenameText.setText(StringUtils.stripToEmpty(currentFilePath).trim());
		
		filenameText.addModifyListener(event -> {
			String text = filenameText.getText().trim();
			if (new File(text).isFile()) {
				fileSelected(text);
			} else {
				GuiOptionsHelper.getOptions().setCurrentFilePath(null);
			}
		});
		
		btnBrowse = new Button(this, SWT.NONE);
		btnBrowse.setText("Browse");
		// scene name label
		lblSceneName = new Label(this, SWT.NONE);
		lblSceneName.setText("Scene name :");
		lblSceneName.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false,
				true, 1, 1));
		// grid for scene name
		gridInputSceneName = new GridData(SWT.LEFT, SWT.CENTER, true, true, 2,
				1);
		gridInputSceneName.widthHint = 300;
		// scene text edit
		textInputSceneName = new Text(this, SWT.BORDER);
		textInputSceneName.setLayoutData(gridInputSceneName);
		
		
		SelectionListener tileSettingAdapter = new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				Point2D result = null;
				if (!btnGenerateAllTiles.getSelection()) {
					result = new Point2D(spinnerLongitude.getSelection(),
							spinnerLatitude.getSelection());
				}
				GuiOptionsHelper.setSelectedCoordinates(result);
			}
			
		};
		
		Button btnExclusionsFromFile = new Button(this, SWT.CHECK);
		btnExclusionsFromFile.setText("Use exclusion coordinates from OSM file");
		btnExclusionsFromFile.setToolTipText("Use bounding box from PBF/OSM file as exclusion zone instead of excluding whole tile. Can be useful when generating zones smaller than 1 tile");
		btnExclusionsFromFile.setSelection(GuiOptionsHelper.isUseExclusionsFromPBF());
		btnExclusionsFromFile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				GuiOptionsHelper.setUseExclusionsFromPBF(btnExclusionsFromFile.getSelection());
			}
		});
		btnExclusionsFromFile.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER,
				false, true, 3, 1));
		
		btnGenerateAllTiles = new Button(this, SWT.CHECK);
		btnGenerateAllTiles.setSelection(true);
		btnGenerateAllTiles.setText("Generate all tiles");
		btnGenerateAllTiles.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER,
				false, true, 3, 1));
		btnGenerateAllTiles.addSelectionListener(tileSettingAdapter);
		grpCoordinates = new Group(this, SWT.NONE);
		gridCoordinates = new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1);
		gridCoordinates.widthHint = 294;
		grpCoordinates.setLayout(new GridLayout(7, false));
		grpCoordinates.setLayoutData(gridCoordinates);
		grpCoordinates.setText("Coordinates");
		grpCoordinates.setVisible(false);
		Label lblLatitude = new Label(grpCoordinates, SWT.NONE);
		lblLatitude.setText("Latitude:");
		new Label(grpCoordinates, SWT.NONE);
		spinnerLatitude = new Spinner(grpCoordinates, SWT.BORDER);
		spinnerLatitude.setMaximum(200);
		spinnerLatitude.setMinimum(-200);
		spinnerLatitude.addSelectionListener(tileSettingAdapter);
		Label lblLongitude = new Label(grpCoordinates, SWT.NONE);
		lblLongitude.setText("Longitude:");
		spinnerLongitude = new Spinner(grpCoordinates, SWT.BORDER);
		spinnerLongitude.setMaximum(200);
		spinnerLongitude.setMinimum(-200);
		spinnerLongitude.addSelectionListener(tileSettingAdapter);
		new Label(grpCoordinates, SWT.NONE);
		new Label(grpCoordinates, SWT.NONE);

	}

	@Override
	protected void bindComponents() {
		// TODO Auto-generated method stub

	}

	protected void fileSelected(String fileName) {
		GuiOptionsHelper.getOptions().setCurrentFilePath(fileName);
		String sceneName = computeSceneName();
		GuiOptionsHelper.setSceneName(sceneName);
		textInputSceneName.setText(sceneName);
		GuiOptionsHelper.addUsedFile(fileName);
		if (fileName.toUpperCase().contains(".SHP")) {
			GuiOptionsHelper.askShapeFileNature(getShell());
		}
	}

	@Override
	protected void addComponentsListeners() {
		btnBrowse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dlg = new FileDialog(getParent().getShell(),
						SWT.OPEN);
				dlg.setFilterNames(FILTER_NAMES);
				dlg.setFilterExtensions(FILTER_EXTS);
				String fileName = dlg.open();
				if (fileName != null) {
					filenameText.setText(fileName);
				}
			}
		});

		textInputSceneName.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				GuiOptionsHelper.setSceneName(textInputSceneName.getText());
			}
		});
		btnGenerateAllTiles.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				grpCoordinates.setVisible(btnGenerateAllTiles.getSelection());
			}
		});

	}
	
	@Override
	public void dispose() {
		partSite.getWorkbenchWindow().getSelectionService().removeSelectionListener(selectionListener);
		super.dispose();
	}

}
