package com.osm2xp.gui.views.panels.flightGear;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.gui.views.panels.Osm2xpPanel;

/**
 * FlightGear simulator options panel: the local FlightGear installation paths
 * used as a terrain source and for the {@code fgelev} elevation utility.
 *
 * @author osm2xp
 */
public class FlightGearSimPanel extends Osm2xpPanel {

	private Text sceneryPathText;
	private Text fgelevPathText;

	public FlightGearSimPanel(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected void initComponents() {
		Composite pathsComposite = new Composite(this, SWT.NONE);
		pathsComposite.setLayout(new GridLayout(3, false));
		pathsComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		sceneryPathText = createPathRow(pathsComposite, "FlightGear scenery path:", true);
		sceneryPathText.setToolTipText(
				"Root of an existing FlightGear scenery installation (contains Terrain/, Objects/, ...). "
						+ "Terrain tiles are copied from here before downloading them.");

		fgelevPathText = createPathRow(pathsComposite, "fgelev executable:", false);
		fgelevPathText.setToolTipText(
				"Path to the fgelev utility shipped with FlightGear. Only needed for terrain formats that "
						+ "cannot be read in-process (the future WS3.0 VPB/OSGB terrain).");

		addHint("Terrain tiles are cached under the source_tiles folder in the working folder.");
		addHint("If that folder is not writable, the cache falls back to the folder containing the "
				+ "input file, then to the OS temp folder.");
		addHint("Missing tiles are downloaded from the configured TerraSync mirrors.");
	}

	private void addHint(String text) {
		Label label = new Label(this, SWT.WRAP);
		label.setText(text);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
	}

	private Text createPathRow(Composite parent, String labelText, boolean directory) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		Text text = new Text(parent, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		Button browse = new Button(parent, SWT.PUSH);
		browse.setText("Browse...");
		browse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String value;
				if (directory) {
					value = new DirectoryDialog(getShell()).open();
				} else {
					value = new FileDialog(getShell(), SWT.OPEN).open();
				}
				if (value != null) {
					text.setText(value);
				}
			}
		});
		return text;
	}

	@Override
	protected void initLayout() {
		GridLayout layout = new GridLayout(2, false);
		layout.horizontalSpacing = 15;
		layout.verticalSpacing = 6;
		setLayout(layout);
	}

	@Override
	protected void bindComponents() {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		bindComponent(sceneryPathText, options, "flightGearSceneryPath");
		bindComponent(fgelevPathText, options, "fgelevPath");
	}
}
