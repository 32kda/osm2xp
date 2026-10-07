package com.osm2xp.gui.handlers.modes;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.osm2xp.controllers.BuildController;
import com.osm2xp.utils.ui.UiUtil;

public class ModeCommand extends AbstractHandler {
	
	private String perspectiveId;

	private String docFolder;

	private String modeId;

	public ModeCommand(String perspectiveId, String docFolder, String modeId) {
		super();
		this.perspectiveId = perspectiveId;
		this.modeId = modeId;
		this.docFolder = docFolder;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		UiUtil.switchPerspective(perspectiveId);
		BuildController.setGenerationMode(modeId);
		UiUtil.showCurrentMode();
		UiUtil.showCurrentModeInfo(false);
		return null;
	}

}
